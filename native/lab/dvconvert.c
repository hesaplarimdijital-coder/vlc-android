// SP-03: Dolby Vision profile 7 -> 8.1 remux with libavformat and libdovi.
//
// Every packet of the HEVC track is rewritten NAL by NAL: enhancement-layer units
// (UNSPEC63 or nuh_layer_id > 0) are dropped, RPUs (UNSPEC62) are converted with
// libdovi mode 2 (profile 8.1, mapping curves set to no-op) and everything else of
// the base layer is copied bit for bit. Timestamps come from the source container,
// so no frame rate guessing; the output stream carries a profile 8.1 configuration
// record (Matroska BlockAdditionMapping, or dvvC in MP4).
#include "dvconvert.h"
#include "dovi_capi.h"

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/avstring.h>
#include <libavutil/dovi_meta.h>
#include <libavutil/mathematics.h>
#include <libavutil/mem.h>
#include <libavutil/time.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>

#define NAL_UNSPEC62 62
#define NAL_UNSPEC63 63

typedef struct ByteBuf {
    uint8_t *data;
    size_t size, cap;
} ByteBuf;

static int buf_reserve(ByteBuf *b, size_t extra)
{
    if (b->size + extra <= b->cap)
        return 0;
    size_t cap = b->cap ? b->cap : 65536;
    while (cap < b->size + extra)
        cap *= 2;
    uint8_t *p = av_realloc(b->data, cap + AV_INPUT_BUFFER_PADDING_SIZE);
    if (!p)
        return AVERROR(ENOMEM);
    b->data = p;
    b->cap = cap;
    return 0;
}

static int buf_append(ByteBuf *b, const uint8_t *src, size_t n)
{
    int ret = buf_reserve(b, n);
    if (ret < 0)
        return ret;
    memcpy(b->data + b->size, src, n);
    b->size += n;
    return 0;
}

static int buf_put_be(ByteBuf *b, uint32_t v, int bytes)
{
    uint8_t tmp[4];
    for (int i = 0; i < bytes; i++)
        tmp[i] = (uint8_t)(v >> (8 * (bytes - 1 - i)));
    return buf_append(b, tmp, bytes);
}

static int nal_type(const uint8_t *nal) { return (nal[0] >> 1) & 0x3F; }
static int nal_layer(const uint8_t *nal) { return ((nal[0] & 1) << 5) | (nal[1] >> 3); }

/* ---- decoding timestamps ------------------------------------------------------------------
 * Matroska stores presentation times only. Without an HEVC decoder libavformat cannot learn the
 * reorder depth and hands out dts = pts, which goes backwards at every B-frame. The converter
 * therefore derives dts itself, as FFmpeg's muxer once did: the smallest of the last depth + 1
 * presentation times, primed with times one frame apart before the first one. The depth comes
 * from the sequence parameter set (sps_max_num_reorder_pics of the highest sub-layer). */

#define MAX_REORDER 16

typedef struct Bits {
    const uint8_t *data;
    size_t size, pos; /* pos in bits */
    int overrun;
} Bits;

static unsigned bits_read(Bits *b, int n)
{
    unsigned v = 0;
    while (n-- > 0) {
        if (b->pos >= b->size * 8) {
            b->overrun = 1;
            return 0;
        }
        v = (v << 1) | ((b->data[b->pos >> 3] >> (7 - (b->pos & 7))) & 1);
        b->pos++;
    }
    return v;
}

static void bits_skip(Bits *b, size_t n)
{
    b->pos += n;
    if (b->pos > b->size * 8)
        b->overrun = 1;
}

static unsigned bits_ue(Bits *b)
{
    int zeros = 0;
    while (!b->overrun && bits_read(b, 1) == 0 && zeros < 31)
        zeros++;
    return ((1u << zeros) - 1) + bits_read(b, zeros);
}

/* sps_max_num_reorder_pics of an SPS NAL unit (header included), or -1 */
static int sps_reorder_depth(const uint8_t *nal, size_t size)
{
    uint8_t rbsp[512];
    size_t n = 0;
    for (size_t i = 2; i < size && n < sizeof(rbsp); i++) {
        if (i + 2 < size && nal[i] == 0 && nal[i + 1] == 0 && nal[i + 2] == 3) {
            rbsp[n++] = 0;
            if (n < sizeof(rbsp))
                rbsp[n++] = 0;
            i += 2; /* skip the emulation prevention byte */
            continue;
        }
        rbsp[n++] = nal[i];
    }
    Bits b = {rbsp, n, 0, 0};
    bits_skip(&b, 4); /* sps_video_parameter_set_id */
    int max_sub_layers_minus1 = (int)bits_read(&b, 3);
    bits_skip(&b, 1);
    bits_skip(&b, 96); /* general profile, tier, level */
    int sub_profile[8] = {0}, sub_level[8] = {0};
    for (int i = 0; i < max_sub_layers_minus1; i++) {
        sub_profile[i] = (int)bits_read(&b, 1);
        sub_level[i] = (int)bits_read(&b, 1);
    }
    if (max_sub_layers_minus1 > 0)
        bits_skip(&b, 2 * (size_t)(8 - max_sub_layers_minus1));
    for (int i = 0; i < max_sub_layers_minus1; i++)
        bits_skip(&b, (sub_profile[i] ? 88 : 0) + (sub_level[i] ? 8 : 0));
    bits_ue(&b); /* sps_seq_parameter_set_id */
    if (bits_ue(&b) == 3) /* chroma_format_idc */
        bits_skip(&b, 1);
    bits_ue(&b); /* width */
    bits_ue(&b); /* height */
    if (bits_read(&b, 1)) /* conformance window */
        for (int i = 0; i < 4; i++)
            bits_ue(&b);
    bits_ue(&b); /* bit_depth_luma_minus8 */
    bits_ue(&b); /* bit_depth_chroma_minus8 */
    bits_ue(&b); /* log2_max_pic_order_cnt_lsb_minus4 */
    int all_layers = (int)bits_read(&b, 1);
    unsigned reorder = 0;
    for (int i = all_layers ? 0 : max_sub_layers_minus1; i <= max_sub_layers_minus1; i++) {
        bits_ue(&b); /* sps_max_dec_pic_buffering_minus1 */
        reorder = bits_ue(&b);
        bits_ue(&b); /* sps_max_latency_increase_plus1 */
    }
    return b.overrun || reorder > MAX_REORDER ? -1 : (int)reorder;
}

/* reorder depth from the base layer's SPS in hvcC or Annex B extradata, or -1 */
static int extradata_reorder_depth(const uint8_t *ex, int size)
{
    if (!ex || size < 4)
        return -1;
    if (size >= 23 && ex[0] == 1) {
        const uint8_t *p = ex + 23, *end = ex + size;
        for (int a = 0; a < ex[22] && end - p >= 3; a++) {
            int type = p[0] & 0x3F, count = (p[1] << 8) | p[2];
            p += 3;
            for (int i = 0; i < count && end - p >= 2; i++) {
                int len = (p[0] << 8) | p[1];
                p += 2;
                if (len > end - p)
                    return -1;
                if (type == 33 && len > 2 && nal_layer(p) == 0)
                    return sps_reorder_depth(p, (size_t)len);
                p += len;
            }
        }
        return -1;
    }
    for (int i = 0; i + 3 < size; i++) {
        if (ex[i] == 0 && ex[i + 1] == 0 && ex[i + 2] == 1) {
            const uint8_t *nal = ex + i + 3;
            int len = size - (i + 3);
            for (int j = i + 3; j + 2 < size; j++)
                if (ex[j] == 0 && ex[j + 1] == 0 && (ex[j + 2] == 1 || (j + 3 < size && ex[j + 2] == 0 && ex[j + 3] == 1))) {
                    len = j - (i + 3);
                    break;
                }
            if (len > 2 && nal_type(nal) == 33 && nal_layer(nal) == 0)
                return sps_reorder_depth(nal, (size_t)len);
        }
    }
    return -1;
}

typedef struct DtsGen {
    int depth;
    int primed;
    int64_t buf[MAX_REORDER + 1];
    int64_t last;
} DtsGen;

static int64_t dts_next(DtsGen *g, int64_t pts, int64_t duration)
{
    if (!g->primed) {
        for (int i = 1; i <= g->depth; i++)
            g->buf[i] = pts + (i - g->depth - 1) * duration;
        g->primed = 1;
        g->last = INT64_MIN;
    }
    g->buf[0] = pts;
    for (int i = 0; i < g->depth && g->buf[i] > g->buf[i + 1]; i++) {
        int64_t t = g->buf[i];
        g->buf[i] = g->buf[i + 1];
        g->buf[i + 1] = t;
    }
    int64_t dts = g->buf[0];
    if (g->last != INT64_MIN && dts <= g->last)
        dts = g->last + 1; /* a deeper reorder than the SPS says: keep dts increasing */
    g->last = dts;
    return dts;
}

typedef struct Ctx {
    DvcStats *stats;
    int length_size; /* NAL length field size in the source packets, 0 = Annex B */
} Ctx;

static void note_profile(DvcStats *s, const DoviRpuOpaque *rpu, int output)
{
    const DoviRpuDataHeaderPrefix *h = dovi_rpu_get_header(rpu);
    if (!h)
        return;
    if (output) {
        s->output_profile = h->guessed_profile;
    } else {
        if (!s->input_profile)
            s->input_profile = h->guessed_profile;
        if (h->el_type && !s->input_el_type[0])
            av_strlcpy(s->input_el_type, h->el_type, sizeof(s->input_el_type));
    }
    dovi_rpu_free_header(h);
}

/* Appends [nal] to [out] in the source framing, converted, or drops it. */
static int handle_nal(Ctx *c, const uint8_t *nal, size_t size, ByteBuf *out)
{
    if (size < 2)
        return 0;
    int type = nal_type(nal), layer = nal_layer(nal);
    if (type == NAL_UNSPEC63 || layer > 0) {
        c->stats->el_nals_dropped++;
        c->stats->el_bytes_dropped += (int64_t)size;
        return 0;
    }
    const uint8_t *data = nal;
    size_t len = size;
    const DoviData *converted = NULL;
    DoviRpuOpaque *rpu = NULL;
    if (type == NAL_UNSPEC62) {
        rpu = dovi_parse_unspec62_nalu(nal, size);
        const char *err = rpu ? dovi_rpu_get_error(rpu) : "dovi_parse_unspec62_nalu returned NULL";
        if (!err) {
            if (!c->stats->rpus_converted && !c->stats->rpus_failed) {
                note_profile(c->stats, rpu, 0);
                if (c->stats->input_profile == 5) {
                    /* the base layer is IPTPQc2, not HDR10: a profile 8.1 label would show wrong colours */
                    snprintf(c->stats->error, sizeof(c->stats->error),
                             "profile 5 cannot become 8.1: its base layer is not HDR10-compatible");
                    dovi_rpu_free(rpu);
                    return AVERROR_PATCHWELCOME;
                }
            }
            if (dovi_convert_rpu_with_mode(rpu, 2) == 0)
                converted = dovi_write_unspec62_nalu(rpu);
            err = converted && converted->data ? NULL : dovi_rpu_get_error(rpu);
        }
        if (!err) {
            if (!c->stats->rpus_converted)
                note_profile(c->stats, rpu, 1);
            data = converted->data;
            len = converted->len;
            c->stats->rpus_converted++;
        } else {
            /* a frame without RPU falls back to the HDR10 base layer; an unconverted
             * profile 7 RPU under a profile 8 record would not */
            c->stats->rpus_failed++;
            if (!c->stats->first_rpu_error[0])
                av_strlcpy(c->stats->first_rpu_error, err ? err : "unknown error", sizeof(c->stats->first_rpu_error));
            if (converted)
                dovi_data_free(converted);
            dovi_rpu_free(rpu);
            return 0;
        }
    }
    int ret = c->length_size ? buf_put_be(out, (uint32_t)len, c->length_size)
                             : buf_append(out, (const uint8_t *)"\0\0\0\1", 4);
    if (ret >= 0)
        ret = buf_append(out, data, len);
    if (converted)
        dovi_data_free(converted);
    if (rpu)
        dovi_rpu_free(rpu);
    return ret;
}

static int rewrite_packet(Ctx *c, const AVPacket *in, ByteBuf *out)
{
    const uint8_t *p = in->data, *end = in->data + in->size;
    out->size = 0;
    if (c->length_size) {
        while (end - p >= c->length_size) {
            uint32_t n = 0;
            for (int i = 0; i < c->length_size; i++)
                n = (n << 8) | p[i];
            p += c->length_size;
            if (n > (uint32_t)(end - p))
                return AVERROR_INVALIDDATA;
            int ret = handle_nal(c, p, n, out);
            if (ret < 0)
                return ret;
            p += n;
        }
        return 0;
    }
    /* Annex B: split on 00 00 01 / 00 00 00 01 */
    const uint8_t *start = NULL;
    while (p + 3 <= end) {
        if (p[0] == 0 && p[1] == 0 && p[2] == 1) {
            if (start) {
                const uint8_t *stop = p;
                while (stop > start && stop[-1] == 0)
                    stop--;
                int ret = handle_nal(c, start, stop - start, out);
                if (ret < 0)
                    return ret;
            }
            p += 3;
            start = p;
        } else {
            p++;
        }
    }
    if (start && start < end)
        return handle_nal(c, start, end - start, out);
    return 0;
}

/* hvcC without enhancement-layer parameter sets (layer id > 0, UNSPEC62/63 arrays). */
static int filter_hvcc(const uint8_t *src, int size, uint8_t **dst, int *dst_size)
{
    if (size < 23 || src[0] != 1)
        return 0; /* not hvcC: leave as is */
    ByteBuf b = {0};
    int ret = buf_append(&b, src, 22);
    if (ret < 0)
        return ret;
    int arrays = src[22], kept_arrays = 0;
    size_t count_pos = b.size;
    if ((ret = buf_append(&b, (const uint8_t *)"\0", 1)) < 0)
        goto fail;
    const uint8_t *p = src + 23, *end = src + size;
    for (int a = 0; a < arrays && end - p >= 3; a++) {
        uint8_t head = p[0];
        int type = head & 0x3F, n = (p[1] << 8) | p[2];
        p += 3;
        ByteBuf units = {0};
        int kept = 0;
        for (int i = 0; i < n && end - p >= 2; i++) {
            int len = (p[0] << 8) | p[1];
            p += 2;
            if (len > end - p) {
                av_free(units.data);
                ret = AVERROR_INVALIDDATA;
                goto fail;
            }
            if (len >= 2 && type != NAL_UNSPEC62 && type != NAL_UNSPEC63 && nal_layer(p) == 0) {
                if ((ret = buf_put_be(&units, len, 2)) < 0 || (ret = buf_append(&units, p, len)) < 0) {
                    av_free(units.data);
                    goto fail;
                }
                kept++;
            }
            p += len;
        }
        if (kept) {
            uint8_t hdr[3] = {head, (uint8_t)(kept >> 8), (uint8_t)kept};
            if ((ret = buf_append(&b, hdr, 3)) < 0 || (ret = buf_append(&b, units.data, units.size)) < 0) {
                av_free(units.data);
                goto fail;
            }
            kept_arrays++;
        }
        av_free(units.data);
    }
    b.data[count_pos] = (uint8_t)kept_arrays;
    *dst = b.data;
    *dst_size = (int)b.size;
    return 1;
fail:
    av_free(b.data);
    return ret;
}

static void fail(DvcStats *s, const char *what, int err)
{
    char msg[AV_ERROR_MAX_STRING_SIZE] = {0};
    av_strerror(err, msg, sizeof(msg));
    snprintf(s->error, sizeof(s->error), "%s: %s", what, msg);
}

static int dolby_level(const AVCodecParameters *par, AVRational fps)
{
    /* level from pixel rate when the source has no configuration record */
    double rate = (double)par->width * par->height * (fps.num && fps.den ? av_q2d(fps) : 24.0);
    static const double max_rate[] = {0, 22118400, 27648000, 49766400, 62208000, 124416000, 199065600,
                                      248832000, 398131200, 497664000, 995328000, 995328000, 1990656000, 3981312000};
    for (int level = 1; level < 14; level++)
        if (rate <= max_rate[level])
            return level;
    return 13;
}

int dvc_convert(const char *input, const char *output, double max_seconds, int mp4,
                volatile int *cancel, dvc_progress_fn progress, void *opaque, DvcStats *s)
{
    memset(s, 0, sizeof(*s));
    AVFormatContext *ic = NULL, *oc = NULL;
    AVPacket *pkt = av_packet_alloc(), *outpkt = av_packet_alloc();
    int *map = NULL, video = -1, ret;
    ByteBuf buf = {0};
    int64_t started = av_gettime_relative(), last_report = 0, start_pts = AV_NOPTS_VALUE;
    Ctx ctx = {.stats = s};

    if (!pkt || !outpkt) {
        ret = AVERROR(ENOMEM);
        fail(s, "allocation", ret);
        goto end;
    }
    if ((ret = avformat_open_input(&ic, input, NULL, NULL)) < 0) {
        fail(s, "open input", ret);
        goto end;
    }
    if ((ret = avformat_find_stream_info(ic, NULL)) < 0) {
        fail(s, "read stream info", ret);
        goto end;
    }
    for (unsigned i = 0; i < ic->nb_streams; i++)
        if (ic->streams[i]->codecpar->codec_id == AV_CODEC_ID_HEVC && video < 0)
            video = (int)i;
    if (video < 0) {
        ret = AVERROR_STREAM_NOT_FOUND;
        snprintf(s->error, sizeof(s->error), "no HEVC video track");
        goto end;
    }
    AVStream *ist_video = ic->streams[video];
    const AVPacketSideData *conf = av_packet_side_data_get(ist_video->codecpar->coded_side_data,
                                                           ist_video->codecpar->nb_coded_side_data, AV_PKT_DATA_DOVI_CONF);
    int level = 0;
    if (conf) {
        const AVDOVIDecoderConfigurationRecord *rec = (const AVDOVIDecoderConfigurationRecord *)conf->data;
        s->input_profile = rec->dv_profile;
        level = rec->dv_level;
        if (rec->dv_profile == 5) {
            ret = AVERROR_PATCHWELCOME;
            snprintf(s->error, sizeof(s->error), "profile 5 cannot become 8.1: its base layer is not HDR10-compatible");
            goto end;
        }
    }
    if (!level)
        level = dolby_level(ist_video->codecpar, ist_video->avg_frame_rate);
    const uint8_t *ex = ist_video->codecpar->extradata;
    ctx.length_size = (ist_video->codecpar->extradata_size >= 23 && ex[0] == 1) ? (ex[21] & 3) + 1 : 0;

    /* Matroska has no decoding timestamps (see dts_next) */
    int regen_dts = ic->iformat && strstr(ic->iformat->name, "matroska") != NULL;
    DtsGen dts_gen = {0};
    int64_t frame_duration = 1;
    if (regen_dts) {
        int depth = extradata_reorder_depth(ex, ist_video->codecpar->extradata_size);
        dts_gen.depth = depth >= 0 ? depth : 4; /* no SPS in the header: more than x265's and Blu-ray encoders' usual 2-3 */
        AVRational fps = ist_video->avg_frame_rate.num ? ist_video->avg_frame_rate : ist_video->r_frame_rate;
        if (fps.num && fps.den)
            frame_duration = FFMAX(1, av_rescale_q(1, av_inv_q(fps), ist_video->time_base));
    }

    if ((ret = avformat_alloc_output_context2(&oc, NULL, mp4 ? "mp4" : "matroska", output)) < 0) {
        fail(s, "create output", ret);
        goto end;
    }
    if (mp4)
        oc->strict_std_compliance = FF_COMPLIANCE_UNOFFICIAL; /* dvvC */
    map = av_calloc(ic->nb_streams, sizeof(*map));
    if (!map) {
        ret = AVERROR(ENOMEM);
        goto end;
    }
    for (unsigned i = 0; i < ic->nb_streams; i++) {
        AVStream *ist = ic->streams[i];
        enum AVMediaType type = ist->codecpar->codec_type;
        int keep = (int)i == video || type == AVMEDIA_TYPE_AUDIO;
        if (!mp4 && (type == AVMEDIA_TYPE_SUBTITLE || type == AVMEDIA_TYPE_ATTACHMENT))
            keep = 1;
        if (mp4 && type == AVMEDIA_TYPE_SUBTITLE && ist->codecpar->codec_id == AV_CODEC_ID_MOV_TEXT)
            keep = 1;
        if (!keep) {
            map[i] = -1;
            s->streams_skipped++;
            continue;
        }
        AVStream *ost = avformat_new_stream(oc, NULL);
        if (!ost) {
            ret = AVERROR(ENOMEM);
            goto end;
        }
        if ((ret = avcodec_parameters_copy(ost->codecpar, ist->codecpar)) < 0)
            goto end;
        ost->codecpar->codec_tag = 0;
        if (mp4 && (int)i == video)
            ost->codecpar->codec_tag = MKTAG('h', 'v', 'c', '1'); /* players and TVs expect hvc1 next to dvvC */
        ost->time_base = ist->time_base;
        ost->disposition = ist->disposition;
        av_dict_copy(&ost->metadata, ist->metadata, 0);
        if ((int)i == video) {
            uint8_t *hvcc = NULL;
            int hvcc_size = 0;
            int filtered = filter_hvcc(ist->codecpar->extradata, ist->codecpar->extradata_size, &hvcc, &hvcc_size);
            if (filtered < 0) {
                ret = filtered;
                fail(s, "hvcC", ret);
                goto end;
            }
            if (filtered) {
                av_freep(&ost->codecpar->extradata);
                ost->codecpar->extradata = hvcc;
                ost->codecpar->extradata_size = hvcc_size;
                memset(hvcc + hvcc_size, 0, AV_INPUT_BUFFER_PADDING_SIZE);
            }
            av_packet_side_data_remove(ost->codecpar->coded_side_data, &ost->codecpar->nb_coded_side_data, AV_PKT_DATA_DOVI_CONF);
            size_t dovi_size = 0;
            AVDOVIDecoderConfigurationRecord *dovi = av_dovi_alloc(&dovi_size);
            if (!dovi) {
                ret = AVERROR(ENOMEM);
                goto end;
            }
            dovi->dv_version_major = 1;
            dovi->dv_profile = 8;
            dovi->dv_level = (uint8_t)level;
            dovi->rpu_present_flag = 1;
            dovi->el_present_flag = 0;
            dovi->bl_present_flag = 1;
            dovi->dv_bl_signal_compatibility_id = 1; /* HDR10 */
            if (!av_packet_side_data_add(&ost->codecpar->coded_side_data, &ost->codecpar->nb_coded_side_data,
                                         AV_PKT_DATA_DOVI_CONF, dovi, dovi_size, 0)) {
                av_free(dovi);
                ret = AVERROR(ENOMEM);
                goto end;
            }
        }
        map[i] = ost->index;
        s->streams_copied++;
    }
    if (!mp4) {
        av_dict_copy(&oc->metadata, ic->metadata, 0);
        if (ic->nb_chapters) {
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
        }
    }
    if ((ret = avio_open(&oc->pb, output, AVIO_FLAG_WRITE)) < 0) {
        fail(s, "open output", ret);
        goto end;
    }
    if ((ret = avformat_write_header(oc, NULL)) < 0) {
        fail(s, "write header", ret);
        goto end;
    }

    double total = ic->duration > 0 ? ic->duration / (double)AV_TIME_BASE : 0;
    if (max_seconds > 0 && (total == 0 || max_seconds < total))
        total = max_seconds;
    int64_t frame_index = 0;
    while (!(cancel && *cancel)) {
        ret = av_read_frame(ic, pkt);
        if (ret == AVERROR_EOF) {
            ret = 0;
            break;
        }
        if (ret < 0) {
            fail(s, "read", ret);
            goto end;
        }
        int out_index = map[pkt->stream_index];
        if (out_index < 0) {
            av_packet_unref(pkt);
            continue;
        }
        AVStream *ist = ic->streams[pkt->stream_index], *ost = oc->streams[out_index];
        if (pkt->stream_index == video) {
            if (pkt->pts == AV_NOPTS_VALUE && pkt->dts == AV_NOPTS_VALUE) {
                /* raw Annex B input: number the frames */
                AVRational fps = ist->avg_frame_rate.num ? ist->avg_frame_rate : ist->r_frame_rate;
                if (!fps.num)
                    fps = (AVRational){24000, 1001};
                pkt->pts = pkt->dts = av_rescale_q(frame_index, av_inv_q(fps), ist->time_base);
                pkt->duration = av_rescale_q(1, av_inv_q(fps), ist->time_base);
            }
            if (regen_dts && pkt->pts != AV_NOPTS_VALUE)
                pkt->dts = dts_next(&dts_gen, pkt->pts, pkt->duration > 0 ? pkt->duration : frame_duration);
            frame_index++;
            s->video_packets++;
            int64_t ts = pkt->pts != AV_NOPTS_VALUE ? pkt->pts : pkt->dts;
            if (ts != AV_NOPTS_VALUE) {
                if (start_pts == AV_NOPTS_VALUE)
                    start_pts = ts;
                double t = (ts - start_pts) * av_q2d(ist->time_base);
                if (t > s->media_seconds)
                    s->media_seconds = t;
                if (max_seconds > 0 && t >= max_seconds) {
                    av_packet_unref(pkt);
                    break;
                }
            }
            if ((ret = rewrite_packet(&ctx, pkt, &buf)) < 0) {
                if (!s->error[0])
                    fail(s, "rewrite packet", ret);
                av_packet_unref(pkt);
                goto end;
            }
            if ((ret = av_new_packet(outpkt, (int)buf.size)) < 0)
                goto end;
            memcpy(outpkt->data, buf.data, buf.size);
            if ((ret = av_packet_copy_props(outpkt, pkt)) < 0)
                goto end;
            outpkt->stream_index = out_index;
            av_packet_rescale_ts(outpkt, ist->time_base, ost->time_base);
            av_packet_unref(pkt);
            ret = av_interleaved_write_frame(oc, outpkt);
        } else {
            pkt->stream_index = out_index;
            av_packet_rescale_ts(pkt, ist->time_base, ost->time_base);
            pkt->pos = -1;
            ret = av_interleaved_write_frame(oc, pkt);
        }
        if (ret < 0) {
            fail(s, "write", ret);
            goto end;
        }
        int64_t now = av_gettime_relative();
        if (progress && now - last_report > 500000) {
            last_report = now;
            progress(opaque, s->media_seconds, total, oc->pb ? avio_tell(oc->pb) : 0);
        }
    }
    if (cancel && *cancel) {
        snprintf(s->error, sizeof(s->error), "cancelled");
        ret = AVERROR_EXIT;
    }
    {
        int tret = av_write_trailer(oc);
        if (ret >= 0 && tret < 0) {
            ret = tret;
            fail(s, "write trailer", ret);
        }
    }

end:
    if (ic && ic->pb)
        s->bytes_in = avio_tell(ic->pb);
    if (oc && oc->pb) {
        s->bytes_out = avio_tell(oc->pb);
        avio_closep(&oc->pb);
    }
    avformat_free_context(oc);
    avformat_close_input(&ic);
    av_packet_free(&pkt);
    av_packet_free(&outpkt);
    av_free(map);
    av_free(buf.data);
    s->wall_seconds = (av_gettime_relative() - started) / 1e6;
    struct rusage usage;
    if (getrusage(RUSAGE_SELF, &usage) == 0)
        s->peak_rss_kb = usage.ru_maxrss;
    if (ret >= 0 && s->rpus_converted == 0 && !s->error[0])
        snprintf(s->error, sizeof(s->error), "no Dolby Vision RPU found: the output is the HDR10 base layer only");
    if (progress)
        progress(opaque, s->media_seconds, s->media_seconds, s->bytes_out);
    return ret;
}

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

char *dvc_stats_json(const DvcStats *s, const char *input, const char *output)
{
    char in[1024], out[1024], err[1100], rpu_err[600], el[16];
    json_string(in, sizeof(in), input);
    json_string(out, sizeof(out), output);
    json_string(err, sizeof(err), s->error);
    json_string(rpu_err, sizeof(rpu_err), s->first_rpu_error);
    json_string(el, sizeof(el), s->input_el_type);
    size_t cap = 4096;
    char *json = malloc(cap);
    if (!json)
        return NULL;
    snprintf(json, cap,
             "{\"input\":\"%s\",\"output\":\"%s\",\"input_profile\":%d,\"input_el_type\":\"%s\","
             "\"output_profile\":%d,\"video_packets\":%lld,\"rpus_converted\":%lld,\"rpus_failed\":%lld,"
             "\"el_nals_dropped\":%lld,\"el_bytes_dropped\":%lld,\"bytes_in\":%lld,\"bytes_out\":%lld,"
             "\"streams_copied\":%d,\"streams_skipped\":%d,\"media_seconds\":%.3f,\"wall_seconds\":%.3f,"
             "\"peak_rss_kb\":%ld,\"first_rpu_error\":\"%s\",\"error\":\"%s\"}",
             in, out, s->input_profile, el, s->output_profile, (long long)s->video_packets,
             (long long)s->rpus_converted, (long long)s->rpus_failed, (long long)s->el_nals_dropped,
             (long long)s->el_bytes_dropped, (long long)s->bytes_in, (long long)s->bytes_out, s->streams_copied,
             s->streams_skipped, s->media_seconds, s->wall_seconds, s->peak_rss_kb, rpu_err, err);
    return json;
}
