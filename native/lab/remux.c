// S-1206: the lossless remux engine; S-1209: its verification. See remux.h.
//
// The source is demuxed with libavformat and muxed into Matroska packet by packet, nothing
// decoded: timestamps, durations, key frames and side data go through as read, the streams in
// the order the plan names them, with the flags, language and title the plan sets. Both files
// are file descriptors behind a custom AVIOContext (seekable: the muxer writes its seek head
// and cues at the end), so a document from the storage framework works without a path.
// While the video stream is copied its content is hashed (SHA-256 over every NAL unit, or
// over every packet for codecs without NAL units); the verification reads the output back and
// hashes it the same way, so a remux that changed one video bit cannot pass.
#define _FILE_OFFSET_BITS 64
#include "remux.h"
#include "dvconvert.h"
#include "dovi_capi.h"

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavformat/avio.h>
#include <libavutil/avstring.h>
#include <libavutil/dovi_meta.h>
#include <libavutil/mathematics.h>
#include <libavutil/mem.h>
#include <libavutil/sha.h>
#include <libavutil/time.h>

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <unistd.h>

/* ---- file descriptor IO ------------------------------------------------------------------ */

typedef struct FdIo {
    int fd;
} FdIo;

static int fd_read(void *opaque, uint8_t *buf, int size)
{
    FdIo *io = opaque;
    ssize_t n;
    do
        n = read(io->fd, buf, (size_t)size);
    while (n < 0 && errno == EINTR);
    if (n == 0)
        return AVERROR_EOF;
    return n < 0 ? AVERROR(errno) : (int)n;
}

static int fd_write(void *opaque, const uint8_t *buf, int size)
{
    FdIo *io = opaque;
    int done = 0;
    while (done < size) {
        ssize_t n = write(io->fd, buf + done, (size_t)(size - done));
        if (n < 0 && errno == EINTR)
            continue;
        if (n <= 0)
            return n < 0 ? AVERROR(errno) : AVERROR(EIO);
        done += (int)n;
    }
    return done;
}

static int64_t fd_seek(void *opaque, int64_t offset, int whence)
{
    FdIo *io = opaque;
    if (whence & AVSEEK_SIZE) {
        struct stat st;
        if (fstat(io->fd, &st) < 0)
            return AVERROR(errno);
        return st.st_size;
    }
    whence &= ~AVSEEK_FORCE;
    off_t pos = lseek(io->fd, (off_t)offset, whence);
    return pos < 0 ? AVERROR(errno) : (int64_t)pos;
}

/* A seekable context over [fd]; [write] for the output; NULL for a descriptor that cannot seek (a pipe). Freed with fd_io_close. */
static AVIOContext *fd_io_open(int fd, int write)
{
    if (lseek(fd, 0, SEEK_CUR) < 0)
        return NULL;
    int size = write ? 1 << 20 : 1 << 18;
    uint8_t *buffer = av_malloc((size_t)size);
    FdIo *io = av_mallocz(sizeof(*io));
    if (!buffer || !io) {
        av_free(buffer);
        av_free(io);
        return NULL;
    }
    io->fd = fd;
    AVIOContext *ctx = avio_alloc_context(buffer, size, write, io, write ? NULL : fd_read, write ? fd_write : NULL, fd_seek);
    if (!ctx) {
        av_free(buffer);
        av_free(io);
        return NULL;
    }
    ctx->seekable = AVIO_SEEKABLE_NORMAL;
    return ctx;
}

static void fd_io_close(AVIOContext **ctx)
{
    if (!*ctx)
        return;
    if ((*ctx)->write_flag)
        avio_flush(*ctx);
    av_free((*ctx)->opaque);
    av_freep(&(*ctx)->buffer);
    avio_context_free(ctx);
}

/* ---- the plan ----------------------------------------------------------------------------- */

static int plan_flag(const char *value, int *out)
{
    if (!strcmp(value, "0") || !strcmp(value, "1")) {
        *out = value[0] - '0';
        return 0;
    }
    return -1;
}

int remux_plan_parse(const char *text, RemuxPlan *plan, char *err, size_t err_size)
{
    memset(plan, 0, sizeof(*plan));
    plan->chapters = plan->attachments = plan->metadata = 1;
    err[0] = 0;
    const char *line = text;
    int line_no = 0;
    while (line && *line) {
        line_no++;
        const char *nl = strchr(line, '\n');
        size_t len = nl ? (size_t)(nl - line) : strlen(line);
        char buf[1024];
        if (len >= sizeof(buf)) {
            snprintf(err, err_size, "line %d too long", line_no);
            return AVERROR_INVALIDDATA;
        }
        memcpy(buf, line, len);
        buf[len] = 0;
        line = nl ? nl + 1 : NULL;
        if (!buf[0] || buf[0] == '#')
            continue;
        char *save = NULL;
        char *key = strtok_r(buf, "\t", &save);
        char *value = strtok_r(NULL, "\t", &save);
        if (!key || !value) {
            snprintf(err, err_size, "line %d: no value", line_no);
            return AVERROR_INVALIDDATA;
        }
        if (!strcmp(key, "dv")) {
            if (!strcmp(value, "keep"))
                plan->dv_convert = 0;
            else if (!strcmp(value, "convert81"))
                plan->dv_convert = 1;
            else
                goto bad;
        } else if (!strcmp(key, "chapters")) {
            if (plan_flag(value, &plan->chapters) < 0)
                goto bad;
        } else if (!strcmp(key, "attachments")) {
            if (plan_flag(value, &plan->attachments) < 0)
                goto bad;
        } else if (!strcmp(key, "metadata")) {
            if (plan_flag(value, &plan->metadata) < 0)
                goto bad;
        } else if (!strcmp(key, "stream")) {
            if (plan->count >= REMUX_MAX_STREAMS) {
                snprintf(err, err_size, "line %d: more than %d streams", line_no, REMUX_MAX_STREAMS);
                return AVERROR_INVALIDDATA;
            }
            RemuxStreamPlan *sp = &plan->streams[plan->count];
            memset(sp, 0, sizeof(*sp));
            sp->set_default = sp->set_forced = -1;
            if (strlen(value) >= sizeof(sp->ref) || !strchr("vas", value[0]) || value[1] != ':' || !value[2])
                goto bad;
            for (const char *d = value + 2; *d; d++)
                if (*d < '0' || *d > '9')
                    goto bad;
            av_strlcpy(sp->ref, value, sizeof(sp->ref));
            char *field;
            while ((field = strtok_r(NULL, "\t", &save)) != NULL) {
                char *eq = strchr(field, '=');
                if (!eq)
                    goto bad;
                *eq = 0;
                const char *v = eq + 1;
                if (!strcmp(field, "default")) {
                    if (plan_flag(v, &sp->set_default) < 0)
                        goto bad;
                } else if (!strcmp(field, "forced")) {
                    if (plan_flag(v, &sp->set_forced) < 0)
                        goto bad;
                } else if (!strcmp(field, "lang")) {
                    if (strlen(v) >= sizeof(sp->language))
                        goto bad;
                    sp->set_language = 1;
                    av_strlcpy(sp->language, v, sizeof(sp->language));
                } else if (!strcmp(field, "title")) {
                    sp->set_title = 1;
                    av_strlcpy(sp->title, v, sizeof(sp->title));
                } else {
                    goto bad;
                }
            }
            plan->count++;
        } else {
            goto bad;
        }
        continue;
    bad:
        snprintf(err, err_size, "line %d: bad %s", line_no, key);
        return AVERROR_INVALIDDATA;
    }
    return 0;
}

/* The source stream a reference names, or -1. */
static int resolve_ref(const AVFormatContext *ic, const char *ref)
{
    enum AVMediaType type = ref[0] == 'v' ? AVMEDIA_TYPE_VIDEO : ref[0] == 'a' ? AVMEDIA_TYPE_AUDIO : AVMEDIA_TYPE_SUBTITLE;
    int wanted = atoi(ref + 2), seen = 0;
    for (unsigned i = 0; i < ic->nb_streams; i++) {
        const AVStream *st = ic->streams[i];
        if (st->codecpar->codec_type != type)
            continue;
        if (type == AVMEDIA_TYPE_VIDEO && (st->disposition & AV_DISPOSITION_ATTACHED_PIC))
            continue;
        if (seen++ == wanted)
            return (int)i;
    }
    return -1;
}

/* ---- the video hash ----------------------------------------------------------------------- */

static const char *const SCOPE_NAMES[] = {"packets", "nal_units", "base_layer_nal_units", "av1_obus"};

const char *remux_hash_scope_name(int scope)
{
    return scope >= 0 && scope <= REMUX_HASH_AV1_OBUS ? SCOPE_NAMES[scope] : "unknown";
}

int remux_hash_scope_of(const char *name)
{
    for (int i = 0; i <= REMUX_HASH_AV1_OBUS; i++)
        if (name && !strcmp(name, SCOPE_NAMES[i]))
            return i;
    return -1;
}

typedef struct Hasher {
    struct AVSHA *sha;
    int scope;
    int length_size;
    int64_t packets, units;
} Hasher;

static int hasher_init(Hasher *h, int scope, int length_size)
{
    memset(h, 0, sizeof(*h));
    h->sha = av_sha_alloc();
    if (!h->sha)
        return AVERROR(ENOMEM);
    h->scope = scope;
    h->length_size = length_size;
    return av_sha_init(h->sha, 256);
}

static void hasher_unit(Hasher *h, const uint8_t *data, size_t size)
{
    uint8_t head[4] = {(uint8_t)(size >> 24), (uint8_t)(size >> 16), (uint8_t)(size >> 8), (uint8_t)size};
    av_sha_update(h->sha, head, 4);
    av_sha_update(h->sha, data, size);
    h->units++;
}

static int hasher_nal(void *opaque, const uint8_t *nal, size_t size)
{
    Hasher *h = opaque;
    if (size < 2)
        return 0;
    if (h->scope == REMUX_HASH_BASE_LAYER) {
        int type = dvc_nal_type(nal);
        if (type == DVC_NAL_RPU || type == DVC_NAL_EL || dvc_nal_layer(nal) > 0)
            return 0;
    }
    hasher_unit(h, nal, size);
    return 0;
}

/* The AV1 OBUs of a packet, those Matroska keeps (as libavformat's av1_filter_obus); 0 when the packet does not parse. */
static int hasher_obus(Hasher *h, const uint8_t *data, size_t size)
{
    const uint8_t *p = data, *end = data + size;
    while (p < end) {
        int type = (p[0] >> 3) & 0xF, ext = (p[0] >> 2) & 1, has_size = (p[0] >> 1) & 1;
        size_t head = 1 + (size_t)ext;
        if (end - p < (ptrdiff_t)head)
            return 0;
        uint64_t obu_size = (uint64_t)(end - p - head);
        if (has_size) {
            obu_size = 0;
            size_t i = 0;
            for (; i < 8 && head + i < (size_t)(end - p); i++) {
                obu_size |= (uint64_t)(p[head + i] & 0x7F) << (7 * i);
                if (!(p[head + i] & 0x80))
                    break;
            }
            if (i == 8 || head + i >= (size_t)(end - p))
                return 0;
            head += i + 1;
        }
        if (obu_size > (uint64_t)(end - p - head))
            return 0;
        size_t len = head + (size_t)obu_size;
        if (type != 2 && type != 7 && type != 8 && type != 15) /* temporal delimiter, redundant frame header, tile list, padding */
            hasher_unit(h, p, len);
        p += len;
    }
    return 1;
}

static void hasher_packet(Hasher *h, const uint8_t *data, int size)
{
    h->packets++;
    if (h->scope == REMUX_HASH_PACKETS) {
        hasher_unit(h, data, (size_t)size);
        return;
    }
    if (h->scope == REMUX_HASH_AV1_OBUS) {
        if (!hasher_obus(h, data, (size_t)size))
            hasher_unit(h, data, (size_t)size);
        return;
    }
    if (dvc_for_each_nal(data, (size_t)size, h->length_size, hasher_nal, h) < 0) {
        /* a packet whose length fields do not add up: hashed whole, so a copy of it still matches */
        hasher_unit(h, data, (size_t)size);
    }
}

static void hasher_final(Hasher *h, char hex[65])
{
    uint8_t digest[32];
    av_sha_final(h->sha, digest);
    for (int i = 0; i < 32; i++)
        snprintf(hex + 2 * i, 3, "%02x", digest[i]);
    av_freep(&h->sha);
}

static int scope_for(const AVCodecParameters *par, int dv_convert)
{
    if (dv_convert)
        return REMUX_HASH_BASE_LAYER;
    if (par->codec_id == AV_CODEC_ID_HEVC || par->codec_id == AV_CODEC_ID_H264)
        return REMUX_HASH_NAL_UNITS;
    if (par->codec_id == AV_CODEC_ID_AV1)
        return REMUX_HASH_AV1_OBUS;
    return REMUX_HASH_PACKETS;
}

/* ---- the remux ---------------------------------------------------------------------------- */

/* Whether /proc/self/fd resolves both descriptors to one path (false when it resolves neither). */
static int same_path(int a, int b)
{
    char link_a[64], link_b[64], path_a[4096], path_b[4096];
    snprintf(link_a, sizeof(link_a), "/proc/self/fd/%d", a);
    snprintf(link_b, sizeof(link_b), "/proc/self/fd/%d", b);
    ssize_t na = readlink(link_a, path_a, sizeof(path_a) - 1), nb = readlink(link_b, path_b, sizeof(path_b) - 1);
    if (na <= 0 || nb <= 0 || na != nb)
        return 0;
    path_a[na] = path_b[nb] = 0;
    return path_a[0] == '/' && strcmp(path_a, path_b) == 0;
}

static void fail(char *error, size_t size, const char *what, int err)
{
    char msg[AV_ERROR_MAX_STRING_SIZE] = {0};
    av_strerror(err, msg, sizeof(msg));
    snprintf(error, size, "%s: %s", what, msg);
}

/* The progress callback honoured: 0 to go on, AVERROR_EXIT to stop; waits as long as it asks. */
static int report(remux_progress_fn progress, void *opaque, volatile int *cancel, double done, double total, int64_t bytes,
                  double *paused_seconds)
{
    if (!progress)
        return cancel && *cancel ? AVERROR_EXIT : 0;
    for (;;) {
        int wait = progress(opaque, done, total, bytes);
        if (wait < 0)
            return AVERROR_EXIT;
        if (wait == 0)
            return cancel && *cancel ? AVERROR_EXIT : 0;
        /* in slices, so that a cancel during a long pause is seen */
        for (int left = wait; left > 0; left -= 500) {
            if (cancel && *cancel)
                return AVERROR_EXIT;
            int slice = left < 500 ? left : 500;
            av_usleep((unsigned)slice * 1000);
            *paused_seconds += slice / 1000.0;
        }
    }
}

/* Opens the demuxer over [fd]; on failure the error is in [error]. */
static int open_input(int fd, AVFormatContext **ic, AVIOContext **io, char *error, size_t error_size)
{
    *io = fd_io_open(fd, 0);
    if (!*io) {
        snprintf(error, error_size, "open input: the file cannot be read with seeking");
        return AVERROR(ESPIPE);
    }
    *ic = avformat_alloc_context();
    if (!*ic) {
        fail(error, error_size, "allocation", AVERROR(ENOMEM));
        return AVERROR(ENOMEM);
    }
    (*ic)->pb = *io;
    (*ic)->flags |= AVFMT_FLAG_CUSTOM_IO;
    int ret = avformat_open_input(ic, NULL, NULL, NULL);
    if (ret < 0) {
        fail(error, error_size, "open input", ret);
        return ret;
    }
    if ((ret = avformat_find_stream_info(*ic, NULL)) < 0) {
        fail(error, error_size, "read stream info", ret);
        return ret;
    }
    return 0;
}

static int first_video(const AVFormatContext *ic)
{
    for (unsigned i = 0; i < ic->nb_streams; i++)
        if (ic->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO && !(ic->streams[i]->disposition & AV_DISPOSITION_ATTACHED_PIC))
            return (int)i;
    return -1;
}

int remux_run(int input_fd, int output_fd, const RemuxPlan *plan, volatile int *cancel,
              remux_progress_fn progress, void *opaque, RemuxStats *s)
{
    memset(s, 0, sizeof(*s));
    s->hash_scope = -1;
    AVFormatContext *ic = NULL, *oc = NULL;
    AVIOContext *in_io = NULL, *out_io = NULL;
    AVPacket *pkt = av_packet_alloc(), *outpkt = av_packet_alloc();
    int *map = NULL, video = -1, ret;
    DvcBuf buf = {0};
    DvcStats dv = {0};
    DvcRewriter rewriter = {.stats = &dv};
    Hasher hasher = {0};
    int64_t started = av_gettime_relative(), last_report = 0, start_pts = AV_NOPTS_VALUE;

    if (!pkt || !outpkt) {
        ret = AVERROR(ENOMEM);
        fail(s->error, sizeof(s->error), "allocation", ret);
        goto end;
    }
    if (plan->count == 0) {
        ret = AVERROR(EINVAL);
        snprintf(s->error, sizeof(s->error), "the plan names no stream");
        goto end;
    }
    /* never the source: by inode, and by the paths behind the descriptors (a FUSE mount and the file
     * system under it give different inodes for one file) */
    struct stat in_st, out_st;
    if (fstat(output_fd, &out_st) < 0) {
        ret = AVERROR(errno);
        fail(s->error, sizeof(s->error), "stat output", ret);
        goto end;
    }
    if (fstat(input_fd, &in_st) == 0 && in_st.st_dev == out_st.st_dev && in_st.st_ino == out_st.st_ino) {
        ret = AVERROR(EINVAL);
        av_strlcpy(s->refused, REMUX_REFUSED_SAME_FILE, sizeof(s->refused));
        snprintf(s->error, sizeof(s->error), "the output is the source itself");
        goto end;
    }
    if (same_path(input_fd, output_fd)) {
        ret = AVERROR(EINVAL);
        av_strlcpy(s->refused, REMUX_REFUSED_SAME_FILE, sizeof(s->refused));
        snprintf(s->error, sizeof(s->error), "the output is the source itself (same path)");
        goto end;
    }
    /* a pipe or a socket has no size to say it is empty: never written */
    if (!S_ISREG(out_st.st_mode)) {
        ret = AVERROR(EINVAL);
        av_strlcpy(s->refused, REMUX_REFUSED_OUTPUT_NOT_FILE, sizeof(s->refused));
        snprintf(s->error, sizeof(s->error), "the output is not a regular file");
        goto end;
    }
    /* only ever into an empty file: a document the picker just made, never one it "replaced" */
    if (out_st.st_size != 0) {
        ret = AVERROR(EEXIST);
        av_strlcpy(s->refused, REMUX_REFUSED_OUTPUT_NOT_EMPTY, sizeof(s->refused));
        snprintf(s->error, sizeof(s->error), "the output is not empty");
        goto end;
    }
    if (lseek(output_fd, 0, SEEK_CUR) < 0) {
        ret = AVERROR(ESPIPE);
        snprintf(s->error, sizeof(s->error), "open output: the file cannot be written with seeking");
        goto end;
    }
    if (ftruncate(output_fd, 0) < 0 || lseek(output_fd, 0, SEEK_SET) < 0) {
        ret = AVERROR(errno);
        fail(s->error, sizeof(s->error), "truncate output", ret);
        goto end;
    }
    if ((ret = open_input(input_fd, &ic, &in_io, s->error, sizeof(s->error))) < 0)
        goto end;
    s->source_seconds = ic->duration > 0 ? ic->duration / (double)AV_TIME_BASE : 0;

    map = av_malloc_array(ic->nb_streams, sizeof(*map));
    if (!map) {
        ret = AVERROR(ENOMEM);
        goto end;
    }
    for (unsigned i = 0; i < ic->nb_streams; i++)
        map[i] = -1;
    for (int n = 0; n < plan->count; n++) {
        int src = resolve_ref(ic, plan->streams[n].ref);
        if (src < 0) {
            ret = AVERROR_STREAM_NOT_FOUND;
            snprintf(s->error, sizeof(s->error), "no stream %s in the source", plan->streams[n].ref);
            goto end;
        }
        if (map[src] >= 0) {
            ret = AVERROR(EINVAL);
            snprintf(s->error, sizeof(s->error), "stream %s planned twice", plan->streams[n].ref);
            goto end;
        }
        map[src] = n;
        if (video < 0 && ic->streams[src]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO)
            video = src;
    }

    /* Dolby Vision */
    int dv_level = 0;
    if (plan->dv_convert) {
        if (video < 0 || ic->streams[video]->codecpar->codec_id != AV_CODEC_ID_HEVC) {
            ret = AVERROR_PATCHWELCOME;
            snprintf(s->error, sizeof(s->error), "Dolby Vision conversion needs an HEVC video stream");
            goto end;
        }
        const AVCodecParameters *par = ic->streams[video]->codecpar;
        const AVPacketSideData *conf = av_packet_side_data_get(par->coded_side_data, par->nb_coded_side_data, AV_PKT_DATA_DOVI_CONF);
        if (conf) {
            const AVDOVIDecoderConfigurationRecord *rec = (const AVDOVIDecoderConfigurationRecord *)conf->data;
            dv.input_profile = rec->dv_profile;
            dv_level = rec->dv_level;
            if (rec->dv_profile == 5) {
                ret = AVERROR_PATCHWELCOME;
                snprintf(s->error, sizeof(s->error), "profile 5 cannot become 8.1: its base layer is not HDR10-compatible");
                goto end;
            }
        }
        if (!dv_level)
            dv_level = dvc_dolby_level(par, ic->streams[video]->avg_frame_rate);
        rewriter.length_size = dvc_length_size(AV_CODEC_ID_HEVC, par->extradata, par->extradata_size);
    }

    /* the output */
    if ((ret = avformat_alloc_output_context2(&oc, NULL, "matroska", NULL)) < 0) {
        fail(s->error, sizeof(s->error), "create output", ret);
        goto end;
    }
    out_io = fd_io_open(output_fd, 1);
    if (!out_io) {
        ret = AVERROR(ENOMEM);
        fail(s->error, sizeof(s->error), "open output", ret);
        goto end;
    }
    oc->pb = out_io;
    oc->flags |= AVFMT_FLAG_CUSTOM_IO;
    /* the interleaving queue holds at most this much of a 100 Mbps film: about 25 MB */
    oc->max_interleave_delta = 2 * AV_TIME_BASE;
    for (int n = 0; n < plan->count; n++) {
        const RemuxStreamPlan *sp = &plan->streams[n];
        int src = resolve_ref(ic, sp->ref);
        AVStream *ist = ic->streams[src];
        AVStream *ost = avformat_new_stream(oc, NULL);
        if (!ost) {
            ret = AVERROR(ENOMEM);
            goto end;
        }
        if ((ret = avcodec_parameters_copy(ost->codecpar, ist->codecpar)) < 0)
            goto end;
        ost->codecpar->codec_tag = 0;
        ost->time_base = ist->time_base;
        ost->disposition = ist->disposition;
        if (sp->set_default >= 0)
            ost->disposition = (ost->disposition & ~AV_DISPOSITION_DEFAULT) | (sp->set_default ? AV_DISPOSITION_DEFAULT : 0);
        if (sp->set_forced >= 0)
            ost->disposition = (ost->disposition & ~AV_DISPOSITION_FORCED) | (sp->set_forced ? AV_DISPOSITION_FORCED : 0);
        av_dict_copy(&ost->metadata, ist->metadata, 0);
        if (sp->set_language)
            av_dict_set(&ost->metadata, "language", sp->language[0] ? sp->language : NULL, 0);
        if (sp->set_title)
            av_dict_set(&ost->metadata, "title", sp->title[0] ? sp->title : NULL, 0);
        if (src == video && plan->dv_convert) {
            uint8_t *hvcc = NULL;
            int hvcc_size = 0;
            int filtered = dvc_filter_hvcc(ist->codecpar->extradata, ist->codecpar->extradata_size, &hvcc, &hvcc_size);
            if (filtered < 0) {
                ret = filtered;
                fail(s->error, sizeof(s->error), "hvcC", ret);
                goto end;
            }
            if (filtered) {
                av_freep(&ost->codecpar->extradata);
                ost->codecpar->extradata = hvcc;
                ost->codecpar->extradata_size = hvcc_size;
                memset(hvcc + hvcc_size, 0, AV_INPUT_BUFFER_PADDING_SIZE);
            }
            if ((ret = dvc_profile81_record(ost->codecpar, dv_level)) < 0)
                goto end;
            /* mkvmerge's statistics of the stream no longer hold: the enhancement layer is gone */
            static const char *const stale[] = {"BPS", "NUMBER_OF_BYTES", "_STATISTICS_TAGS", "_STATISTICS_WRITING_APP", "_STATISTICS_WRITING_DATE_UTC"};
            for (size_t k = 0; k < sizeof(stale) / sizeof(stale[0]); k++)
                av_dict_set(&ost->metadata, stale[k], NULL, 0);
        }
        RemuxStreamStats *ss = &s->stream[s->streams++];
        av_strlcpy(ss->ref, sp->ref, sizeof(ss->ref));
        ss->source_index = src;
        ss->output_index = ost->index;
        av_strlcpy(ss->codec, avcodec_get_name(ist->codecpar->codec_id), sizeof(ss->codec));
    }
    if (plan->attachments) {
        for (unsigned i = 0; i < ic->nb_streams; i++) {
            AVStream *ist = ic->streams[i];
            if (ist->codecpar->codec_type != AVMEDIA_TYPE_ATTACHMENT)
                continue;
            AVStream *ost = avformat_new_stream(oc, NULL);
            if (!ost) {
                ret = AVERROR(ENOMEM);
                goto end;
            }
            if ((ret = avcodec_parameters_copy(ost->codecpar, ist->codecpar)) < 0)
                goto end;
            ost->codecpar->codec_tag = 0;
            ost->time_base = ist->time_base;
            ost->disposition = ist->disposition;
            av_dict_copy(&ost->metadata, ist->metadata, 0);
            s->attachments++;
        }
    }
    if (plan->metadata)
        av_dict_copy(&oc->metadata, ic->metadata, 0);
    if (plan->chapters && ic->nb_chapters) {
        oc->chapters = av_calloc(ic->nb_chapters, sizeof(*oc->chapters));
        if (!oc->chapters) {
            ret = AVERROR(ENOMEM);
            goto end;
        }
        for (unsigned i = 0; i < ic->nb_chapters; i++) {
            AVChapter *in = ic->chapters[i], *copy = av_mallocz(sizeof(*copy));
            if (!copy) {
                ret = AVERROR(ENOMEM);
                goto end;
            }
            copy->id = in->id;
            copy->time_base = in->time_base;
            copy->start = in->start;
            copy->end = in->end;
            av_dict_copy(&copy->metadata, in->metadata, 0);
            oc->chapters[oc->nb_chapters++] = copy;
        }
        s->chapters = (int)oc->nb_chapters;
    }

    /* the video stream: its hash, and its decoding timestamps when the source has none */
    DvcDtsGen dts_gen;
    int regen_dts = 0;
    int64_t frame_duration = 1;
    if (video >= 0) {
        const AVCodecParameters *par = ic->streams[video]->codecpar;
        s->video_codec_id = par->codec_id;
        s->hash_scope = scope_for(par, plan->dv_convert);
        if ((ret = hasher_init(&hasher, s->hash_scope, dvc_length_size(par->codec_id, par->extradata, par->extradata_size))) < 0)
            goto end;
        /* Matroska carries no decoding timestamps and the lab's FFmpeg has no decoder to derive them from */
        regen_dts = (par->codec_id == AV_CODEC_ID_HEVC || par->codec_id == AV_CODEC_ID_H264) && ic->iformat &&
                    strstr(ic->iformat->name, "matroska") != NULL;
        if (regen_dts) {
            int depth = dvc_reorder_depth(par->codec_id, par->extradata, par->extradata_size);
            dvc_dts_init(&dts_gen, depth >= 0 ? depth : DVC_FALLBACK_REORDER);
            AVStream *vs = ic->streams[video];
            AVRational fps = vs->avg_frame_rate.num ? vs->avg_frame_rate : vs->r_frame_rate;
            if (fps.num && fps.den)
                frame_duration = FFMAX(1, av_rescale_q(1, av_inv_q(fps), vs->time_base));
        }
    }

    if ((ret = avformat_write_header(oc, NULL)) < 0) {
        fail(s->error, sizeof(s->error), "write header", ret);
        goto end;
    }

    while (!(cancel && *cancel)) {
        ret = av_read_frame(ic, pkt);
        if (ret == AVERROR_EOF) {
            ret = 0;
            break;
        }
        if (ret < 0) {
            fail(s->error, sizeof(s->error), "read", ret);
            goto end;
        }
        int out_index = map[pkt->stream_index];
        if (out_index < 0) {
            av_packet_unref(pkt);
            continue;
        }
        AVStream *ist = ic->streams[pkt->stream_index], *ost = oc->streams[out_index];
        RemuxStreamStats *ss = &s->stream[out_index];
        ss->packets++;
        ss->bytes += pkt->size;
        AVPacket *write = pkt;
        if (pkt->stream_index == video) {
            if (pkt->pts == AV_NOPTS_VALUE && pkt->dts == AV_NOPTS_VALUE) {
                /* a raw Annex B stream: the frames numbered at its rate */
                AVRational fps = ist->avg_frame_rate.num ? ist->avg_frame_rate : ist->r_frame_rate;
                if (!fps.num)
                    fps = (AVRational){24000, 1001};
                pkt->pts = pkt->dts = av_rescale_q(ss->packets - 1, av_inv_q(fps), ist->time_base);
                pkt->duration = av_rescale_q(1, av_inv_q(fps), ist->time_base);
            }
            if (regen_dts && pkt->pts != AV_NOPTS_VALUE)
                pkt->dts = dvc_dts_next(&dts_gen, pkt->pts, pkt->duration > 0 ? pkt->duration : frame_duration);
            hasher_packet(&hasher, pkt->data, pkt->size);
            int64_t ts = pkt->pts != AV_NOPTS_VALUE ? pkt->pts : pkt->dts;
            if (ts != AV_NOPTS_VALUE) {
                if (start_pts == AV_NOPTS_VALUE)
                    start_pts = ts;
                double t = (ts - start_pts) * av_q2d(ist->time_base);
                if (t > s->media_seconds)
                    s->media_seconds = t;
            }
            if (plan->dv_convert) {
                if ((ret = dvc_rewrite_packet(&rewriter, pkt, &buf)) < 0) {
                    if (dv.error[0])
                        av_strlcpy(s->error, dv.error, sizeof(s->error));
                    else
                        fail(s->error, sizeof(s->error), "rewrite packet", ret);
                    av_packet_unref(pkt);
                    goto end;
                }
                if ((ret = av_new_packet(outpkt, (int)buf.size)) < 0)
                    goto end;
                memcpy(outpkt->data, buf.data, buf.size);
                if ((ret = av_packet_copy_props(outpkt, pkt)) < 0)
                    goto end;
                av_packet_unref(pkt);
                write = outpkt;
            }
        }
        write->stream_index = out_index;
        write->pos = -1;
        av_packet_rescale_ts(write, ist->time_base, ost->time_base);
        ret = av_interleaved_write_frame(oc, write);
        if (ret < 0) {
            fail(s->error, sizeof(s->error), "write", ret);
            goto end;
        }
        int64_t now = av_gettime_relative();
        if (now - last_report > 250000) {
            last_report = now;
            if ((ret = report(progress, opaque, cancel, s->media_seconds, s->source_seconds, out_io ? avio_tell(out_io) : 0, &s->paused_seconds)) < 0)
                break;
        }
    }
    if (ret == AVERROR_EXIT || (cancel && *cancel)) {
        s->cancelled = 1;
        ret = AVERROR_EXIT;
        snprintf(s->error, sizeof(s->error), "cancelled");
        goto end;
    }
    if ((ret = av_write_trailer(oc)) < 0) {
        fail(s->error, sizeof(s->error), "write trailer", ret);
        goto end;
    }
    if (plan->dv_convert && dv.rpus_converted == 0) {
        ret = AVERROR_INVALIDDATA;
        snprintf(s->error, sizeof(s->error), "no Dolby Vision RPU found: the output would be the HDR10 base layer only");
    }

end:
    /* a cancelled or failed remux gets no trailer: a partial file must not look complete; the app removes it */
    if (video >= 0 && hasher.sha) {
        s->video_packets = hasher.packets;
        s->video_units = hasher.units;
        hasher_final(&hasher, s->video_hash);
    }
    if (in_io)
        s->bytes_in = avio_tell(in_io);
    if (out_io) {
        avio_flush(out_io);
        s->bytes_out = avio_tell(out_io);
    }
    s->dv_input_profile = dv.input_profile;
    av_strlcpy(s->dv_input_el_type, dv.input_el_type, sizeof(s->dv_input_el_type));
    s->dv_output_profile = dv.output_profile;
    s->dv_rpus_converted = dv.rpus_converted;
    s->dv_rpus_failed = dv.rpus_failed;
    s->dv_el_nals_dropped = dv.el_nals_dropped;
    s->dv_el_bytes_dropped = dv.el_bytes_dropped;
    av_strlcpy(s->dv_first_rpu_error, dv.first_rpu_error, sizeof(s->dv_first_rpu_error));
    avformat_free_context(oc);
    avformat_close_input(&ic);
    fd_io_close(&in_io);
    fd_io_close(&out_io);
    av_packet_free(&pkt);
    av_packet_free(&outpkt);
    av_free(map);
    av_free(buf.data);
    s->wall_seconds = (av_gettime_relative() - started) / 1e6;
    struct rusage usage;
    if (getrusage(RUSAGE_SELF, &usage) == 0)
        s->peak_rss_kb = usage.ru_maxrss;
    return ret;
}

/* ---- the verification --------------------------------------------------------------------- */

typedef struct RpuCheck {
    RemuxVerification *v;
    int expected_profile;
    int found;
} RpuCheck;

static int check_rpu(void *opaque, const uint8_t *nal, size_t size)
{
    RpuCheck *c = opaque;
    if (size < 2 || dvc_nal_type(nal) != DVC_NAL_RPU)
        return 0;
    c->found++;
    c->v->rpus++;
    DoviRpuOpaque *rpu = dovi_parse_unspec62_nalu(nal, size);
    int bad = 1;
    if (rpu && !dovi_rpu_get_error(rpu)) {
        const DoviRpuDataHeaderPrefix *h = dovi_rpu_get_header(rpu);
        if (h) {
            bad = h->guessed_profile != c->expected_profile;
            dovi_rpu_free_header(h);
        }
    }
    if (rpu)
        dovi_rpu_free(rpu);
    if (bad)
        c->v->rpus_bad++;
    return 0;
}

int remux_verify(int output_fd, int scope, const char *expected_hash, int64_t expected_packets, int dv_profile,
                 volatile int *cancel, remux_progress_fn progress, void *opaque, RemuxVerification *v)
{
    memset(v, 0, sizeof(*v));
    v->hash_scope = scope;
    v->expected_packets = expected_packets;
    v->dv_expected_profile = dv_profile;
    av_strlcpy(v->expected_hash, expected_hash ? expected_hash : "", sizeof(v->expected_hash));
    AVFormatContext *ic = NULL;
    AVIOContext *io = NULL;
    AVPacket *pkt = av_packet_alloc();
    Hasher hasher = {0};
    int ret, video;
    int64_t last_report = 0, size = 0;
    double unused = 0;

    if (!pkt) {
        ret = AVERROR(ENOMEM);
        fail(v->error, sizeof(v->error), "allocation", ret);
        goto end;
    }
    if (scope < 0 || scope > REMUX_HASH_AV1_OBUS) {
        ret = AVERROR(EINVAL);
        snprintf(v->error, sizeof(v->error), "unknown hash scope");
        goto end;
    }
    if (lseek(output_fd, 0, SEEK_SET) < 0) {
        ret = AVERROR(errno);
        fail(v->error, sizeof(v->error), "seek output", ret);
        goto end;
    }
    if ((ret = open_input(output_fd, &ic, &io, v->error, sizeof(v->error))) < 0)
        goto end;
    size = avio_size(io);
    v->duration_seconds = ic->duration > 0 ? ic->duration / (double)AV_TIME_BASE : 0;
    video = first_video(ic);
    if (video < 0) {
        ret = AVERROR_STREAM_NOT_FOUND;
        snprintf(v->error, sizeof(v->error), "no video stream in the output");
        goto end;
    }
    const AVCodecParameters *par = ic->streams[video]->codecpar;
    int length_size = dvc_length_size(par->codec_id, par->extradata, par->extradata_size);
    if ((ret = hasher_init(&hasher, scope, length_size)) < 0)
        goto end;
    if (dv_profile > 0) {
        const AVPacketSideData *conf = av_packet_side_data_get(par->coded_side_data, par->nb_coded_side_data, AV_PKT_DATA_DOVI_CONF);
        if (conf) {
            const AVDOVIDecoderConfigurationRecord *rec = (const AVDOVIDecoderConfigurationRecord *)conf->data;
            v->dv_profile = rec->dv_profile;
            v->dv_compatibility = rec->dv_bl_signal_compatibility_id;
            v->dv_el_present = rec->el_present_flag;
        }
    }
    RpuCheck check = {.v = v, .expected_profile = dv_profile};
    while (!(cancel && *cancel)) {
        ret = av_read_frame(ic, pkt);
        if (ret == AVERROR_EOF) {
            ret = 0;
            break;
        }
        if (ret < 0) {
            fail(v->error, sizeof(v->error), "read", ret);
            goto end;
        }
        if (pkt->stream_index == video) {
            hasher_packet(&hasher, pkt->data, pkt->size);
            if (dv_profile > 0 && par->codec_id == AV_CODEC_ID_HEVC) {
                check.found = 0;
                dvc_for_each_nal(pkt->data, (size_t)pkt->size, length_size, check_rpu, &check);
                if (!check.found)
                    v->packets_without_rpu++;
            }
        }
        av_packet_unref(pkt);
        int64_t now = av_gettime_relative();
        if (now - last_report > 250000) {
            last_report = now;
            double done = size > 0 ? (double)avio_tell(io) / size : 0;
            if ((ret = report(progress, opaque, cancel, done * v->duration_seconds, v->duration_seconds, avio_tell(io), &unused)) < 0)
                break;
        }
    }
    if (ret == AVERROR_EXIT || (cancel && *cancel)) {
        v->cancelled = 1;
        ret = AVERROR_EXIT;
        snprintf(v->error, sizeof(v->error), "cancelled");
        goto end;
    }
    v->packets = hasher.packets;
    v->units = hasher.units;
    hasher_final(&hasher, v->hash);
    v->ok = expected_packets > 0 && !strcmp(v->hash, v->expected_hash) && v->packets == expected_packets;
    if (dv_profile > 0) {
        /* profile 8.1: single layer over an HDR10 base layer, an RPU of that profile in every frame */
        int record_ok = v->dv_profile == dv_profile && (dv_profile != 8 || (v->dv_compatibility == 1 && !v->dv_el_present));
        if (!record_ok || v->rpus == 0 || v->rpus_bad > 0 || v->packets_without_rpu > 0)
            v->ok = 0;
    }

end:
    if (hasher.sha)
        av_freep(&hasher.sha);
    avformat_close_input(&ic);
    fd_io_close(&io);
    av_packet_free(&pkt);
    return ret;
}

/* ---- JSON --------------------------------------------------------------------------------- */

static void json_string(char *dst, size_t cap, const char *src)
{
    size_t o = 0;
    for (; src && *src && o + 7 < cap; src++) {
        unsigned char ch = (unsigned char)*src;
        if (ch == '"' || ch == '\\') {
            dst[o++] = '\\';
            dst[o++] = (char)ch;
        } else if (ch < 0x20) {
            o += snprintf(dst + o, cap - o, "\\u%04x", ch);
        } else {
            dst[o++] = (char)ch;
        }
    }
    dst[o] = 0;
}

char *remux_stats_json(const RemuxStats *s)
{
    size_t cap = 4096 + (size_t)s->streams * 256;
    char *json = malloc(cap);
    if (!json)
        return NULL;
    char err[1100], rpu_err[600], el[16];
    json_string(err, sizeof(err), s->error);
    json_string(rpu_err, sizeof(rpu_err), s->dv_first_rpu_error);
    json_string(el, sizeof(el), s->dv_input_el_type);
    size_t o = (size_t)snprintf(json, cap, "{\"error\":\"%s\",\"refused\":\"%s\",\"cancelled\":%s,\"streams\":[", err, s->refused,
                                s->cancelled ? "true" : "false");
    for (int i = 0; i < s->streams && o < cap; i++) {
        const RemuxStreamStats *ss = &s->stream[i];
        char codec[80];
        json_string(codec, sizeof(codec), ss->codec);
        o += (size_t)snprintf(json + o, cap - o, "%s{\"ref\":\"%s\",\"source_index\":%d,\"output_index\":%d,\"codec\":\"%s\",\"packets\":%lld,\"bytes\":%lld}",
                              i ? "," : "", ss->ref, ss->source_index, ss->output_index, codec, (long long)ss->packets, (long long)ss->bytes);
    }
    if (o < cap)
        snprintf(json + o, cap - o,
                 "],\"attachments\":%d,\"chapters\":%d,\"video_codec\":\"%s\",\"video_packets\":%lld,\"video_units\":%lld,"
                 "\"hash_scope\":\"%s\",\"video_hash\":\"%s\",\"source_seconds\":%.3f,\"media_seconds\":%.3f,"
                 "\"bytes_in\":%lld,\"bytes_out\":%lld,\"wall_seconds\":%.3f,\"paused_seconds\":%.3f,\"peak_rss_kb\":%ld,"
                 "\"dv\":{\"input_profile\":%d,\"input_el_type\":\"%s\",\"output_profile\":%d,\"rpus_converted\":%lld,"
                 "\"rpus_failed\":%lld,\"el_nals_dropped\":%lld,\"el_bytes_dropped\":%lld,\"first_rpu_error\":\"%s\"}}",
                 s->attachments, s->chapters, s->video_codec_id ? avcodec_get_name(s->video_codec_id) : "",
                 (long long)s->video_packets, (long long)s->video_units, s->hash_scope >= 0 ? remux_hash_scope_name(s->hash_scope) : "",
                 s->video_hash, s->source_seconds, s->media_seconds, (long long)s->bytes_in, (long long)s->bytes_out,
                 s->wall_seconds, s->paused_seconds, s->peak_rss_kb, s->dv_input_profile, el, s->dv_output_profile,
                 (long long)s->dv_rpus_converted, (long long)s->dv_rpus_failed, (long long)s->dv_el_nals_dropped,
                 (long long)s->dv_el_bytes_dropped, rpu_err);
    return json;
}

char *remux_verification_json(const RemuxVerification *v)
{
    char err[1100];
    json_string(err, sizeof(err), v->error);
    size_t cap = 2048;
    char *json = malloc(cap);
    if (!json)
        return NULL;
    snprintf(json, cap,
             "{\"ok\":%s,\"error\":\"%s\",\"cancelled\":%s,\"hash_scope\":\"%s\",\"expected_hash\":\"%s\",\"hash\":\"%s\","
             "\"expected_packets\":%lld,\"packets\":%lld,\"units\":%lld,\"duration_seconds\":%.3f,"
             "\"dv_expected_profile\":%d,\"dv_profile\":%d,\"dv_compatibility\":%d,\"dv_el_present\":%d,"
             "\"rpus\":%lld,\"rpus_bad\":%lld,\"packets_without_rpu\":%lld}",
             v->ok ? "true" : "false", err, v->cancelled ? "true" : "false", remux_hash_scope_name(v->hash_scope),
             v->expected_hash, v->hash, (long long)v->expected_packets, (long long)v->packets, (long long)v->units,
             v->duration_seconds, v->dv_expected_profile, v->dv_profile, v->dv_compatibility, v->dv_el_present,
             (long long)v->rpus, (long long)v->rpus_bad, (long long)v->packets_without_rpu);
    return json;
}
