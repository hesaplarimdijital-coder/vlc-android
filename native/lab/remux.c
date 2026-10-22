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
// S-1207: an encode line adds a compatibility track: the audio stream it names is decoded on the
// way (its packets are still copied untouched when the plan copies it too), resampled and
// downmixed with libswresample where the codec needs it, and encoded into E-AC-3, AC-3 or LPCM;
// its timestamps count the samples from the source's first one, a gap becomes silence.
#define _FILE_OFFSET_BITS 64
#include "remux.h"
#include "dvconvert.h"
#include "dovi_capi.h"

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavformat/avio.h>
#include <libavutil/audio_fifo.h>
#include <libavutil/avstring.h>
#include <libavutil/channel_layout.h>
#include <libavutil/dovi_meta.h>
#include <libavutil/mathematics.h>
#include <libavutil/mem.h>
#include <libavutil/opt.h>
#include <libavutil/samplefmt.h>
#include <libavutil/sha.h>
#include <libavutil/time.h>
#include <libswresample/swresample.h>

#include <errno.h>
#include <fcntl.h>
#include <math.h>
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

/* A whole number from [min] to [max], or -1. */
static int plan_number(const char *value, int min, int max)
{
    char *end = NULL;
    long n = strtol(value, &end, 10);
    return end != value && !*end && n >= min && n <= max ? (int)n : -1;
}

static const struct {
    const char *name;
    int encode;
} ENCODE_NAMES[] = {
    {"eac3", REMUX_ENCODE_EAC3}, {"ac3", REMUX_ENCODE_AC3}, {"lpcm", REMUX_ENCODE_LPCM},
    {"pcm_s16le", REMUX_ENCODE_PCM_S16}, {"pcm_s24le", REMUX_ENCODE_PCM_S24},
};

static int encode_of(const char *name)
{
    for (size_t i = 0; i < sizeof(ENCODE_NAMES) / sizeof(ENCODE_NAMES[0]); i++)
        if (!strcmp(name, ENCODE_NAMES[i].name))
            return ENCODE_NAMES[i].encode;
    return REMUX_ENCODE_NONE;
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
        } else if (!strcmp(key, "stream") || !strcmp(key, "encode")) {
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
            if (key[0] == 'e') {
                /* S-1207: an audio stream encoded again, beside the original */
                const char *codec = strtok_r(NULL, "\t", &save);
                if (value[0] != 'a' || !codec || (sp->encode = encode_of(codec)) == REMUX_ENCODE_NONE)
                    goto bad;
            }
            char *field;
            while ((field = strtok_r(NULL, "\t", &save)) != NULL) {
                char *eq = strchr(field, '=');
                if (!eq)
                    goto bad;
                *eq = 0;
                const char *v = eq + 1;
                if (sp->encode && !strcmp(field, "bitrate")) {
                    if ((sp->bitrate_kbps = plan_number(v, 32, 6144)) < 0)
                        goto bad;
                } else if (sp->encode && !strcmp(field, "channels")) {
                    if ((sp->channels = plan_number(v, 1, 8)) < 0)
                        goto bad;
                } else if (!strcmp(field, "default")) {
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

static void fail(char *error, size_t size, const char *what, int err)
{
    char msg[AV_ERROR_MAX_STRING_SIZE] = {0};
    av_strerror(err, msg, sizeof(msg));
    snprintf(error, size, "%s: %s", what, msg);
}

/* ---- the compatibility tracks (S-1207) ---------------------------------------------------- */

typedef struct AudioEncoder {
    int src;                      /* the source stream */
    int out;                      /* the output stream */
    char *error;                  /* the run's error text */
    size_t error_size;
    RemuxStreamStats *ss;
    AVCodecContext *dec, *enc;
    SwrContext *swr;
    AVChannelLayout in_layout;    /* the decoded frames swr is set up for */
    int in_rate, in_format;
    int out_rate;
    AVAudioFifo *fifo;            /* converted samples waiting for a whole encoder frame */
    AVFrame *decoded, *converted, *chunk, *silence;
    int converted_capacity;
    AVPacket *packet;
    int chunk_size;               /* samples per encoded frame */
    int fixed_size;               /* the encoder takes whole frames only (AC-3): the last one is padded */
    int64_t origin;               /* the first sample's timestamp, in 1/out_rate; AV_NOPTS_VALUE before it */
    int64_t emitted;              /* samples handed to the encoder */
    int64_t base;                 /* where the current input rate began, in 1/out_rate */
    int64_t fed;                  /* input samples fed since then */
    int64_t padded;               /* silence put into gaps, in output samples */
    int64_t packets_in;
    int64_t src_first, src_end;   /* the source's packets, in its time base */
    int64_t out_first, out_end;   /* the encoded packets, in 1/out_rate */
} AudioEncoder;

#define REMUX_MAX_GAP_SECONDS 600 /* a longer jump in the source's timestamps is not a gap to fill */

static int is_ac3(int encode)
{
    return encode == REMUX_ENCODE_EAC3 || encode == REMUX_ENCODE_AC3;
}

static int has_lfe(const AVChannelLayout *layout)
{
    return av_channel_layout_index_from_channel(layout, AV_CHAN_LOW_FREQUENCY) >= 0;
}

static int codec_takes_layout(const AVCodec *codec, const AVChannelLayout *layout)
{
    const AVChannelLayout *layouts = NULL;
    int count = 0;
    if (avcodec_get_supported_config(NULL, codec, AV_CODEC_CONFIG_CHANNEL_LAYOUT, 0, (const void **)&layouts, &count) < 0 || !layouts)
        return 0;
    for (int i = 0; i < count; i++)
        if (!av_channel_layout_compare(&layouts[i], layout))
            return 1;
    return 0;
}

/* The new stream's channels: E-AC-3 and AC-3 at most 5.1 (the source's own layout when the codec
 * takes it), LPCM up to 7.1 in WAV order, which is what Matroska's PCM means; fewer when asked. */
static void target_layout(const AVChannelLayout *src, const RemuxStreamPlan *sp, const AVCodec *codec, AVChannelLayout *dst)
{
    int n = src->nb_channels;
    if (sp->channels > 0 && sp->channels < n)
        n = sp->channels;
    if (is_ac3(sp->encode)) {
        if (n > 6)
            n = 6;
        if (n <= 2)
            av_channel_layout_default(dst, n);
        else if (n == src->nb_channels && codec_takes_layout(codec, src))
            av_channel_layout_copy(dst, src);
        else
            *dst = has_lfe(src) || n >= 6 ? (AVChannelLayout)AV_CHANNEL_LAYOUT_5POINT1 : (AVChannelLayout)AV_CHANNEL_LAYOUT_5POINT0;
        return;
    }
    av_channel_layout_default(dst, n > 8 ? 8 : n);
}

/* "5.1", "7.1", "2.0": the main channels and the LFE. */
static void layout_short(const AVChannelLayout *layout, char *buf, size_t size)
{
    int lfe = has_lfe(layout);
    snprintf(buf, size, "%d.%d", layout->nb_channels - lfe, lfe);
}

static const char *encode_label(int encode)
{
    switch (encode) {
    case REMUX_ENCODE_EAC3: return "E-AC-3";
    case REMUX_ENCODE_AC3: return "AC-3";
    default: return "LPCM";
    }
}

static int encoder_fail(AudioEncoder *e, const char *what, int err)
{
    char text[128];
    snprintf(text, sizeof(text), "%s (%s)", what, e->ss->ref);
    fail(e->error, e->error_size, text, err);
    return err;
}

static void encoder_free(AudioEncoder **pe)
{
    AudioEncoder *e = *pe;
    if (!e)
        return;
    avcodec_free_context(&e->dec);
    avcodec_free_context(&e->enc);
    swr_free(&e->swr);
    av_channel_layout_uninit(&e->in_layout);
    av_audio_fifo_free(e->fifo);
    av_frame_free(&e->decoded);
    av_frame_free(&e->converted);
    av_frame_free(&e->chunk);
    av_frame_free(&e->silence);
    av_packet_free(&e->packet);
    av_freep(pe);
}

/*
 * The decoder of [ist], the encoder the plan line asks for and the output stream [ost] set up
 * from it. TrueHD and DTS-HD are asked for their own downmix when fewer channels are wanted (the
 * presentation the mastering made); AC-3 and E-AC-3 sources are decoded without their dynamic
 * range compression, which the new stream would otherwise carry baked in.
 */
static int encoder_open(AudioEncoder **pe, AVFormatContext *oc, const AVStream *ist, AVStream *ost, int src,
                        const RemuxStreamPlan *sp, RemuxStreamStats *ss, char *error, size_t error_size)
{
    const AVCodecParameters *par = ist->codecpar;
    AudioEncoder *e = av_mallocz(sizeof(*e));
    if (!e)
        return AVERROR(ENOMEM);
    *pe = e;
    e->src = src;
    e->out = ost->index;
    e->error = error;
    e->error_size = error_size;
    e->ss = ss;
    e->origin = e->src_first = e->src_end = e->out_first = e->out_end = AV_NOPTS_VALUE;
    av_strlcpy(ss->source_codec, avcodec_get_name(par->codec_id), sizeof(ss->source_codec));
    ss->encoded = 1;

    AVChannelLayout source = {0}, target = {0};
    int ret = 0;
    if (par->ch_layout.nb_channels <= 0) {
        snprintf(error, error_size, "the channels of %s are unknown", sp->ref);
        return AVERROR_INVALIDDATA;
    }
    if (par->ch_layout.order == AV_CHANNEL_ORDER_UNSPEC)
        av_channel_layout_default(&source, par->ch_layout.nb_channels);
    else if ((ret = av_channel_layout_copy(&source, &par->ch_layout)) < 0)
        return ret;

    const AVCodec *decoder = avcodec_find_decoder(par->codec_id);
    enum AVCodecID encoder_id = sp->encode == REMUX_ENCODE_EAC3 ? AV_CODEC_ID_EAC3
                              : sp->encode == REMUX_ENCODE_AC3  ? AV_CODEC_ID_AC3
                              : sp->encode == REMUX_ENCODE_PCM_S16 ? AV_CODEC_ID_PCM_S16LE
                              : sp->encode == REMUX_ENCODE_PCM_S24 ? AV_CODEC_ID_PCM_S24LE
                              : par->bits_per_raw_sample > 0 && par->bits_per_raw_sample <= 16 ? AV_CODEC_ID_PCM_S16LE
                              : AV_CODEC_ID_PCM_S24LE;
    const AVCodec *encoder = avcodec_find_encoder(encoder_id);
    if (!decoder || !encoder) {
        snprintf(error, error_size, "no %s %s in this build", avcodec_get_name(decoder ? encoder_id : par->codec_id),
                 decoder ? "encoder" : "decoder");
        ret = AVERROR(ENOSYS);
        goto end;
    }
    target_layout(&source, sp, encoder, &target);

    if (!(e->dec = avcodec_alloc_context3(decoder)) || (ret = avcodec_parameters_to_context(e->dec, par)) < 0) {
        ret = ret < 0 ? ret : AVERROR(ENOMEM);
        goto end;
    }
    e->dec->pkt_timebase = ist->time_base;
    if (target.nb_channels < source.nb_channels) {
        int stereo = !av_channel_layout_compare(&target, &(AVChannelLayout)AV_CHANNEL_LAYOUT_STEREO);
        int surround = !av_channel_layout_compare(&target, &(AVChannelLayout)AV_CHANNEL_LAYOUT_5POINT1) ||
                       !av_channel_layout_compare(&target, &(AVChannelLayout)AV_CHANNEL_LAYOUT_5POINT0);
        int embedded = par->codec_id == AV_CODEC_ID_TRUEHD || par->codec_id == AV_CODEC_ID_MLP || par->codec_id == AV_CODEC_ID_DTS;
        int ac3 = par->codec_id == AV_CODEC_ID_AC3 || par->codec_id == AV_CODEC_ID_EAC3;
        if ((embedded && (stereo || surround)) || (ac3 && stereo))
            av_opt_set_chlayout(e->dec, "downmix", &target, AV_OPT_SEARCH_CHILDREN);
    }
    if (par->codec_id == AV_CODEC_ID_AC3 || par->codec_id == AV_CODEC_ID_EAC3)
        av_opt_set_double(e->dec, "drc_scale", 0.0, AV_OPT_SEARCH_CHILDREN);
    if ((ret = avcodec_open2(e->dec, decoder, NULL)) < 0) {
        encoder_fail(e, "open the decoder", ret);
        goto end;
    }

    if (!(e->enc = avcodec_alloc_context3(encoder))) {
        ret = AVERROR(ENOMEM);
        goto end;
    }
    const enum AVSampleFormat *formats = NULL;
    int nformats = 0;
    if (avcodec_get_supported_config(NULL, encoder, AV_CODEC_CONFIG_SAMPLE_FORMAT, 0, (const void **)&formats, &nformats) < 0 ||
        !formats || nformats < 1) {
        ret = AVERROR_BUG;
        goto end;
    }
    /* E-AC-3 and AC-3 are 48 kHz on every device; LPCM keeps the source's rate */
    e->out_rate = is_ac3(sp->encode) || par->sample_rate <= 0 ? 48000 : par->sample_rate;
    e->enc->sample_fmt = formats[0];
    e->enc->sample_rate = e->out_rate;
    e->enc->time_base = (AVRational){1, e->out_rate};
    if ((ret = av_channel_layout_copy(&e->enc->ch_layout, &target)) < 0)
        goto end;
    if (is_ac3(sp->encode)) {
        int n = target.nb_channels;
        int kbps = sp->bitrate_kbps ? sp->bitrate_kbps : n >= 5 ? 640 : n >= 3 ? 448 : n == 2 ? 256 : 128;
        /* AC-3's ceiling; E-AC-3's with frames of six blocks, the ones every decoder takes */
        int ceiling = sp->encode == REMUX_ENCODE_AC3 ? 640 : 1024;
        if (kbps > ceiling)
            kbps = ceiling;
        e->enc->bit_rate = (int64_t)kbps * 1000;
    } else {
        e->enc->bits_per_raw_sample = encoder_id == AV_CODEC_ID_PCM_S16LE ? 16 : 24;
    }
    if (oc->oformat->flags & AVFMT_GLOBALHEADER)
        e->enc->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    AVDictionary *options = NULL;
    /* -31 dBFS: the level as decoded, the player turns nothing down */
    if (is_ac3(sp->encode))
        av_dict_set(&options, "dialnorm", "-31", 0);
    ret = avcodec_open2(e->enc, encoder, &options);
    av_dict_free(&options);
    if (ret < 0) {
        encoder_fail(e, "open the encoder", ret);
        goto end;
    }
    e->fixed_size = !(encoder->capabilities & AV_CODEC_CAP_VARIABLE_FRAME_SIZE) && e->enc->frame_size > 0;
    e->chunk_size = e->enc->frame_size > 0 ? e->enc->frame_size : FFMAX(256, (int)av_rescale(1536, e->out_rate, 48000));
    e->fifo = av_audio_fifo_alloc(e->enc->sample_fmt, target.nb_channels, 2 * e->chunk_size);
    e->decoded = av_frame_alloc();
    e->converted = av_frame_alloc();
    e->chunk = av_frame_alloc();
    e->silence = av_frame_alloc();
    e->packet = av_packet_alloc();
    if (!e->fifo || !e->decoded || !e->converted || !e->chunk || !e->silence || !e->packet) {
        ret = AVERROR(ENOMEM);
        goto end;
    }
    e->chunk->format = e->enc->sample_fmt;
    e->chunk->sample_rate = e->out_rate;
    e->chunk->nb_samples = e->chunk_size;
    if ((ret = av_channel_layout_copy(&e->chunk->ch_layout, &target)) < 0 || (ret = av_frame_get_buffer(e->chunk, 0)) < 0)
        goto end;

    if ((ret = avcodec_parameters_from_context(ost->codecpar, e->enc)) < 0)
        goto end;
    ost->codecpar->codec_tag = 0;
    /* no CodecDelay element: E-AC-3 in Matroska as mkvmerge writes it, which every TV reads; the
     * encoder's priming is in the timestamps (encoder_drain) */
    ost->codecpar->initial_padding = 0;
    ost->time_base = e->enc->time_base;

    av_strlcpy(ss->codec, avcodec_get_name(encoder_id), sizeof(ss->codec));
    ss->channels = target.nb_channels;
    av_channel_layout_describe(&target, ss->layout, sizeof(ss->layout));
    ss->sample_rate = e->out_rate;
    ss->bit_rate = e->enc->bit_rate > 0 ? e->enc->bit_rate
                                        : (int64_t)e->out_rate * target.nb_channels * av_get_bits_per_sample(encoder_id);
end:
    av_channel_layout_uninit(&source);
    av_channel_layout_uninit(&target);
    return ret;
}

/* The default title of a new stream: "E-AC-3 5.1", "LPCM 7.1". */
static void encoder_title(const AudioEncoder *e, int encode, char *buf, size_t size)
{
    char channels[16];
    layout_short(&e->enc->ch_layout, channels, sizeof(channels));
    snprintf(buf, size, "%s %s", encode_label(encode), channels);
}

/* The encoded packets written; the encoder's priming before a source that starts at zero is cut
 * at zero, or Matroska would move every stream later by it. */
static int encoder_drain(AudioEncoder *e, AVFormatContext *oc)
{
    for (;;) {
        int ret = avcodec_receive_packet(e->enc, e->packet);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF)
            return 0;
        if (ret < 0)
            return encoder_fail(e, "encode", ret);
        AVPacket *p = e->packet;
        if (p->pts != AV_NOPTS_VALUE && p->pts < 0 && e->origin >= 0)
            p->pts = 0;
        p->dts = p->pts;
        if (p->pts != AV_NOPTS_VALUE) {
            if (e->out_first == AV_NOPTS_VALUE || p->pts < e->out_first)
                e->out_first = p->pts;
            if (e->out_end == AV_NOPTS_VALUE || p->pts + p->duration > e->out_end)
                e->out_end = p->pts + p->duration;
        }
        e->ss->packets++;
        e->ss->bytes += p->size;
        p->stream_index = e->out;
        p->pos = -1;
        av_packet_rescale_ts(p, e->enc->time_base, oc->streams[e->out]->time_base);
        if ((ret = av_interleaved_write_frame(oc, p)) < 0)
            return encoder_fail(e, "write", ret);
    }
}

/* Whole encoder frames from the queue; with [final] the rest too (padded for AC-3), then the encoder's own. */
static int encoder_pump(AudioEncoder *e, AVFormatContext *oc, int final)
{
    int ret, channels = e->enc->ch_layout.nb_channels;
    while (av_audio_fifo_size(e->fifo) >= e->chunk_size || (final && av_audio_fifo_size(e->fifo) > 0)) {
        int n = FFMIN(av_audio_fifo_size(e->fifo), e->chunk_size);
        int size = e->fixed_size ? e->chunk_size : n;
        e->chunk->nb_samples = e->chunk_size;
        if ((ret = av_frame_make_writable(e->chunk)) < 0)
            return ret;
        if (av_audio_fifo_read(e->fifo, (void **)e->chunk->extended_data, n) < n)
            return AVERROR_BUG;
        if (n < size)
            av_samples_set_silence(e->chunk->extended_data, n, size - n, channels, e->enc->sample_fmt);
        e->chunk->nb_samples = size;
        e->chunk->pts = e->origin + e->emitted;
        e->emitted += size;
        ret = avcodec_send_frame(e->enc, e->chunk);
        e->chunk->nb_samples = e->chunk_size;
        if (ret < 0)
            return encoder_fail(e, "encode", ret);
        if ((ret = encoder_drain(e, oc)) < 0)
            return ret;
    }
    if (final) {
        if ((ret = avcodec_send_frame(e->enc, NULL)) < 0 && ret != AVERROR_EOF)
            return encoder_fail(e, "encode", ret);
        return encoder_drain(e, oc);
    }
    return 0;
}

/* [count] input samples through the resampler into the queue; NULL and 0 drain the resampler. */
static int encoder_convert(AudioEncoder *e, const uint8_t **in, int count)
{
    for (;;) {
        int capacity = swr_get_out_samples(e->swr, count);
        if (capacity < 0)
            return capacity;
        if (capacity == 0)
            return 0;
        if (capacity > e->converted_capacity) {
            av_frame_unref(e->converted);
            e->converted->format = e->enc->sample_fmt;
            e->converted->sample_rate = e->out_rate;
            e->converted->nb_samples = capacity;
            int ret = av_channel_layout_copy(&e->converted->ch_layout, &e->enc->ch_layout);
            if (ret < 0 || (ret = av_frame_get_buffer(e->converted, 0)) < 0) {
                e->converted_capacity = 0;
                return ret;
            }
            e->converted_capacity = capacity;
        }
        int got = swr_convert(e->swr, e->converted->extended_data, capacity, in, count);
        if (got < 0)
            return encoder_fail(e, "resample", got);
        if (got > 0 && av_audio_fifo_write(e->fifo, (void **)e->converted->extended_data, got) < got)
            return AVERROR(ENOMEM);
        /* a drain goes on until the resampler is empty; input is taken whole in one call */
        if (in || got == 0)
            return 0;
    }
}

/* Where the next input sample falls, in 1/out_rate. */
static int64_t encoder_expected(const AudioEncoder *e)
{
    return e->base + (e->in_rate > 0 ? av_rescale(e->fed, e->out_rate, e->in_rate) : 0);
}

/* The resampler for the decoded frames' format, again when it changes (the rest of the old one kept). */
static int encoder_resampler(AudioEncoder *e, const AVFrame *f)
{
    AVChannelLayout layout = {0};
    int ret;
    if (f->ch_layout.order == AV_CHANNEL_ORDER_UNSPEC)
        av_channel_layout_default(&layout, f->ch_layout.nb_channels);
    else if ((ret = av_channel_layout_copy(&layout, &f->ch_layout)) < 0)
        return ret;
    if (e->swr && e->in_format == f->format && e->in_rate == f->sample_rate && !av_channel_layout_compare(&e->in_layout, &layout)) {
        av_channel_layout_uninit(&layout);
        return 0;
    }
    if (e->swr) {
        if ((ret = encoder_convert(e, NULL, 0)) < 0) {
            av_channel_layout_uninit(&layout);
            return ret;
        }
        swr_free(&e->swr);
    }
    if (e->in_rate && e->in_rate != f->sample_rate) {
        e->base = encoder_expected(e);
        e->fed = 0;
    }
    e->in_rate = f->sample_rate;
    e->in_format = f->format;
    av_channel_layout_uninit(&e->in_layout);
    e->in_layout = layout;
    ret = swr_alloc_set_opts2(&e->swr, &e->enc->ch_layout, e->enc->sample_fmt, e->out_rate, &e->in_layout, f->format,
                              f->sample_rate, 0, NULL);
    if (ret < 0)
        return encoder_fail(e, "resampler", ret);
    /* a downmix scaled so that no sum clips (integer output is scaled anyway); the LFE kept */
    if (e->in_layout.nb_channels > e->enc->ch_layout.nb_channels)
        av_opt_set_double(e->swr, "rematrix_maxval", 1.0, 0);
    if (e->enc->sample_fmt == AV_SAMPLE_FMT_S16)
        av_opt_set_int(e->swr, "dither_method", SWR_DITHER_TRIANGULAR, 0);
    if ((ret = swr_init(e->swr)) < 0)
        return encoder_fail(e, "resampler", ret);
    return 0;
}

/* [count] input samples of silence (a gap in the source), in slices through the resampler. */
static int encoder_silence(AudioEncoder *e, AVFormatContext *oc, int64_t count)
{
    const int slice = 4096;
    int ret;
    if (!e->silence->nb_samples || e->silence->format != e->in_format ||
        av_channel_layout_compare(&e->silence->ch_layout, &e->in_layout)) {
        av_frame_unref(e->silence);
        e->silence->format = e->in_format;
        e->silence->nb_samples = slice;
        if ((ret = av_channel_layout_copy(&e->silence->ch_layout, &e->in_layout)) < 0 || (ret = av_frame_get_buffer(e->silence, 0)) < 0)
            return ret;
        av_samples_set_silence(e->silence->extended_data, 0, slice, e->in_layout.nb_channels, e->in_format);
    }
    while (count > 0) {
        int n = count > slice ? slice : (int)count;
        if ((ret = encoder_convert(e, (const uint8_t **)e->silence->extended_data, n)) < 0)
            return ret;
        e->fed += n;
        count -= n;
        if ((ret = encoder_pump(e, oc, 0)) < 0)
            return ret;
    }
    return 0;
}

/* One decoded frame placed on the new stream's time line: a gap before it filled with silence, a
 * frame that goes back in time (beyond 20 ms) dropped. */
static int encoder_frame(AudioEncoder *e, AVFormatContext *oc, AVFrame *f)
{
    int64_t ts = f->pts != AV_NOPTS_VALUE ? f->pts : f->best_effort_timestamp;
    int64_t at = ts == AV_NOPTS_VALUE ? AV_NOPTS_VALUE : av_rescale_q(ts, e->dec->pkt_timebase, (AVRational){1, e->out_rate});
    int ret;
    if (e->origin == AV_NOPTS_VALUE) {
        if (at == AV_NOPTS_VALUE)
            at = e->src_first == AV_NOPTS_VALUE ? 0 : av_rescale_q(e->src_first, e->dec->pkt_timebase, (AVRational){1, e->out_rate});
        e->origin = e->base = at;
    }
    if ((ret = encoder_resampler(e, f)) < 0)
        return ret;
    if (at != AV_NOPTS_VALUE) {
        int64_t gap = at - encoder_expected(e), tolerance = e->out_rate / 50;
        if (gap > (int64_t)REMUX_MAX_GAP_SECONDS * e->out_rate) {
            snprintf(e->error, e->error_size, "the timestamps of %s jump by %.0f s", e->ss->ref, (double)gap / e->out_rate);
            return AVERROR_INVALIDDATA;
        }
        if (gap > tolerance) {
            e->padded += gap;
            if ((ret = encoder_silence(e, oc, av_rescale(gap, e->in_rate, e->out_rate))) < 0)
                return ret;
        } else if (gap < -tolerance) {
            e->ss->frames_dropped++;
            return 0;
        }
    }
    if ((ret = encoder_convert(e, (const uint8_t **)f->extended_data, f->nb_samples)) < 0)
        return ret;
    e->fed += f->nb_samples;
    return encoder_pump(e, oc, 0);
}

static int encoder_receive(AudioEncoder *e, AVFormatContext *oc)
{
    for (int errors = 0;;) {
        int ret = avcodec_receive_frame(e->dec, e->decoded);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF)
            return 0;
        if (ret == AVERROR(ENOMEM))
            return ret;
        if (ret < 0) {
            e->ss->decode_errors++;
            if (++errors > 100)
                return encoder_fail(e, "decode", ret);
            continue;
        }
        e->ss->frames_decoded++;
        ret = encoder_frame(e, oc, e->decoded);
        av_frame_unref(e->decoded);
        if (ret < 0)
            return ret;
    }
}

/* A packet of the source stream, before the copy (if any) takes it. */
static int encoder_packet(AudioEncoder *e, AVFormatContext *oc, const AVPacket *pkt)
{
    e->packets_in++;
    if (pkt->pts != AV_NOPTS_VALUE) {
        if (e->src_first == AV_NOPTS_VALUE || pkt->pts < e->src_first)
            e->src_first = pkt->pts;
        if (e->src_end == AV_NOPTS_VALUE || pkt->pts + pkt->duration > e->src_end)
            e->src_end = pkt->pts + pkt->duration;
    }
    int ret = avcodec_send_packet(e->dec, pkt);
    if (ret == AVERROR(ENOMEM))
        return ret;
    if (ret < 0) /* a damaged packet: its time becomes silence when the next frame comes */
        e->ss->decode_errors++;
    return encoder_receive(e, oc);
}

static void encoder_stats(AudioEncoder *e)
{
    RemuxStreamStats *ss = e->ss;
    AVRational src_tb = e->dec ? e->dec->pkt_timebase : (AVRational){1, 1};
    if (e->src_first != AV_NOPTS_VALUE)
        ss->source_seconds = (e->src_end - e->src_first) * av_q2d(src_tb);
    if (e->out_first != AV_NOPTS_VALUE && e->out_rate > 0) {
        ss->seconds = (double)(e->out_end - e->out_first) / e->out_rate;
        if (e->src_first != AV_NOPTS_VALUE)
            ss->start_offset_seconds = (double)e->out_first / e->out_rate - e->src_first * av_q2d(src_tb);
    }
    if (e->out_rate > 0)
        ss->padded_seconds = (double)e->padded / e->out_rate;
}

/* The end of the source: the decoder, the resampler and the encoder emptied; then the new
 * stream checked against its source: not empty, decodable, as long. */
static int encoder_finish(AudioEncoder *e, AVFormatContext *oc)
{
    int ret = avcodec_send_packet(e->dec, NULL);
    if (ret < 0 && ret != AVERROR_EOF)
        return encoder_fail(e, "decode", ret);
    if ((ret = encoder_receive(e, oc)) < 0)
        return ret;
    if (e->swr && (ret = encoder_convert(e, NULL, 0)) < 0)
        return ret;
    if ((ret = encoder_pump(e, oc, 1)) < 0)
        return ret;
    encoder_stats(e);
    RemuxStreamStats *ss = e->ss;
    if (ss->packets == 0 || ss->frames_decoded == 0) {
        snprintf(e->error, e->error_size, "no audio decoded from %s for its compatibility track", ss->ref);
        return AVERROR_INVALIDDATA;
    }
    if (ss->decode_errors * 20 > e->packets_in) {
        snprintf(e->error, e->error_size, "%lld of %lld packets of %s could not be decoded", (long long)ss->decode_errors,
                 (long long)e->packets_in, ss->ref);
        return AVERROR_INVALIDDATA;
    }
    if (e->src_first != AV_NOPTS_VALUE && fabs(ss->seconds - ss->source_seconds) > 0.5) {
        snprintf(e->error, e->error_size, "the compatibility track of %s lasts %.2f s, the source %.2f s", ss->ref, ss->seconds,
                 ss->source_seconds);
        return AVERROR_INVALIDDATA;
    }
    return 0;
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
    int *map = NULL, video = -1, ret, encode_default = 0;
    unsigned streams = 0;
    AudioEncoder *encoders[REMUX_MAX_STREAMS] = {0};
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
    /* the streams the plan can name: those known once the file is open (as the probe lists them); a stream the
     * demuxer adds while reading (a TS whose PMT names one more, a programme stream's subtitles that start
     * late) is in no plan and its packets are left out (remuxing.c of FFmpeg's examples does the same) */
    streams = ic->nb_streams;

    map = av_malloc_array(streams, sizeof(*map));
    if (!map) {
        ret = AVERROR(ENOMEM);
        goto end;
    }
    for (unsigned i = 0; i < streams; i++)
        map[i] = -1;
    for (int n = 0; n < plan->count; n++) {
        int src = resolve_ref(ic, plan->streams[n].ref);
        if (src < 0) {
            ret = AVERROR_STREAM_NOT_FOUND;
            snprintf(s->error, sizeof(s->error), "no stream %s in the source", plan->streams[n].ref);
            goto end;
        }
        if (plan->streams[n].encode) {
            /* an added stream: the source may also be copied, and encoded more than once */
            if (plan->streams[n].set_default == 1)
                encode_default = 1;
            continue;
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
        RemuxStreamStats *ss = &s->stream[s->streams++];
        av_strlcpy(ss->ref, sp->ref, sizeof(ss->ref));
        ss->source_index = src;
        ss->output_index = ost->index;
        if (sp->encode) {
            /* S-1207: the compatibility track, flagged and named as the plan says; of the source's
             * tags only the language (its statistics would be wrong) */
            if ((ret = encoder_open(&encoders[n], oc, ist, ost, src, sp, ss, s->error, sizeof(s->error))) < 0) {
                if (!s->error[0])
                    fail(s->error, sizeof(s->error), "compatibility track", ret);
                goto end;
            }
            ost->disposition = (ist->disposition & ~(AV_DISPOSITION_DEFAULT | AV_DISPOSITION_FORCED)) |
                               (sp->set_default == 1 ? AV_DISPOSITION_DEFAULT : 0) | (sp->set_forced == 1 ? AV_DISPOSITION_FORCED : 0);
            const AVDictionaryEntry *lang = av_dict_get(ist->metadata, "language", NULL, 0);
            if (sp->set_language)
                av_dict_set(&ost->metadata, "language", sp->language[0] ? sp->language : NULL, 0);
            else if (lang)
                av_dict_set(&ost->metadata, "language", lang->value, 0);
            char title[64];
            encoder_title(encoders[n], sp->encode, title, sizeof(title));
            av_dict_set(&ost->metadata, "title", sp->set_title ? (sp->title[0] ? sp->title : NULL) : title, 0);
            continue;
        }
        if ((ret = avcodec_parameters_copy(ost->codecpar, ist->codecpar)) < 0)
            goto end;
        ost->codecpar->codec_tag = 0;
        ost->time_base = ist->time_base;
        ost->disposition = ist->disposition;
        /* a compatibility track made default takes the flag from the audio the plan leaves as it was */
        if (sp->set_default < 0 && encode_default && ist->codecpar->codec_type == AVMEDIA_TYPE_AUDIO)
            ost->disposition &= ~AV_DISPOSITION_DEFAULT;
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
        av_strlcpy(ss->codec, avcodec_get_name(ist->codecpar->codec_id), sizeof(ss->codec));
    }
    if (plan->attachments) {
        /* fonts and other files, and the cover pictures (a video stream of one picture to the demuxer, never in the
         * plan): attachments again, after the streams */
        for (unsigned i = 0; i < streams; i++) {
            int attached = dvc_copy_attachment(oc, ic->streams[i]);
            if (attached < 0) {
                ret = attached;
                fail(s->error, sizeof(s->error), "attachment", ret);
                goto end;
            }
            s->attachments += attached;
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
        if ((unsigned)pkt->stream_index >= streams) {
            av_packet_unref(pkt);
            continue;
        }
        /* the compatibility tracks decode the packet before the copy takes it */
        for (int n = 0; n < plan->count; n++) {
            if (encoders[n] && encoders[n]->src == pkt->stream_index && (ret = encoder_packet(encoders[n], oc, pkt)) < 0) {
                if (!s->error[0])
                    fail(s->error, sizeof(s->error), "compatibility track", ret);
                av_packet_unref(pkt);
                goto end;
            }
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
    for (int n = 0; n < plan->count; n++) {
        if (encoders[n] && (ret = encoder_finish(encoders[n], oc)) < 0) {
            if (!s->error[0])
                fail(s->error, sizeof(s->error), "compatibility track", ret);
            goto end;
        }
    }
    if ((ret = av_write_trailer(oc)) < 0) {
        fail(s->error, sizeof(s->error), "write trailer", ret);
        goto end;
    }
    /* on the drive before the run counts as done: the verification is to read what the drive holds, and a USB
     * drive pulled out once the job is done must have the whole film */
    avio_flush(out_io);
    if (out_io->error < 0) {
        ret = out_io->error;
        fail(s->error, sizeof(s->error), "write", ret);
        goto end;
    }
    if (fsync(output_fd) < 0 && errno != EINVAL) {
        ret = AVERROR(errno);
        fail(s->error, sizeof(s->error), "sync output", ret);
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
    for (int n = 0; n < REMUX_MAX_STREAMS; n++) {
        if (encoders[n]) {
            encoder_stats(encoders[n]);
            encoder_free(&encoders[n]);
        }
    }
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
    /* read from the drive, not from the pages the writing left in memory: written out (the remux synced them,
     * this only makes sure) and dropped from the page cache, as far as the file system lets them go */
    fsync(output_fd);
    posix_fadvise(output_fd, 0, 0, POSIX_FADV_DONTNEED);
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
    size_t cap = 4096 + (size_t)s->streams * 768;
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
        o += (size_t)snprintf(json + o, cap - o, "%s{\"ref\":\"%s\",\"source_index\":%d,\"output_index\":%d,\"codec\":\"%s\",\"packets\":%lld,\"bytes\":%lld",
                              i ? "," : "", ss->ref, ss->source_index, ss->output_index, codec, (long long)ss->packets, (long long)ss->bytes);
        if (ss->encoded && o < cap) {
            char source_codec[80], layout[160];
            json_string(source_codec, sizeof(source_codec), ss->source_codec);
            json_string(layout, sizeof(layout), ss->layout);
            o += (size_t)snprintf(json + o, cap - o,
                                  ",\"encoded\":{\"source_codec\":\"%s\",\"channels\":%d,\"layout\":\"%s\",\"sample_rate\":%d,"
                                  "\"bit_rate\":%lld,\"frames_decoded\":%lld,\"decode_errors\":%lld,\"frames_dropped\":%lld,"
                                  "\"padded_seconds\":%.3f,\"source_seconds\":%.3f,\"seconds\":%.3f,\"start_offset_seconds\":%.3f}",
                                  source_codec, ss->channels, layout, ss->sample_rate, (long long)ss->bit_rate,
                                  (long long)ss->frames_decoded, (long long)ss->decode_errors, (long long)ss->frames_dropped,
                                  ss->padded_seconds, ss->source_seconds, ss->seconds, ss->start_offset_seconds);
        }
        if (o < cap)
            o += (size_t)snprintf(json + o, cap - o, "}");
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
