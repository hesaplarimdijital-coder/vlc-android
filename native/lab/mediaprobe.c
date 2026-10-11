// S-802: what a media file holds, for the library's technical badges.
//
// libavformat reads the container and the codec parameters (the HEVC parser fills in the
// profile and the pixel format without a decoder; the audio decoders tell Atmos and DTS:X apart
// through the profile). Dolby Vision comes from the configuration record and, for HEVC, from the
// first RPU, which libdovi parses for the profile and FEL/MEL. HDR10+ is found in the first video
// packets: ST 2094-40 user data in SEI messages, or HDR10+ packet side data.
#include "mediaprobe.h"
#include "dovi_capi.h"

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/avstring.h>
#include <libavutil/bprint.h>
#include <libavutil/channel_layout.h>
#include <libavutil/dovi_meta.h>
#include <libavutil/mastering_display_metadata.h>
#include <libavutil/pixdesc.h>

#include <stdlib.h>
#include <string.h>

/* video packets read after the stream info for RPUs, enhancement layers and HDR10+ SEI */
#define SCAN_VIDEO_PACKETS 12
#define SCAN_MAX_BYTES (48 * 1024 * 1024)

#define NAL_SEI_PREFIX 39
#define NAL_UNSPEC62 62
#define NAL_UNSPEC63 63

typedef struct VideoScan {
    int packets;
    int rpu;            /* an RPU was parsed */
    int rpu_profile;    /* profile libdovi guessed from it */
    char el_type[8];    /* "FEL" / "MEL" (profile 7) */
    int el_nals;        /* enhancement-layer NAL units in the base-layer track */
    int hdr10plus;
    /* HDR10 static metadata from SEI, for streams whose container does not carry it (MPEG-TS) */
    double max_luminance, min_luminance;
    unsigned max_cll, max_fall;
} VideoScan;

static int nal_type(const uint8_t *nal) { return (nal[0] >> 1) & 0x3F; }
static int nal_layer(const uint8_t *nal) { return ((nal[0] & 1) << 5) | (nal[1] >> 3); }

/* RBSP of a NAL unit payload, without emulation prevention bytes; at most [cap] bytes */
static size_t unescape(const uint8_t *src, size_t size, uint8_t *dst, size_t cap)
{
    size_t n = 0;
    for (size_t i = 0; i < size && n < cap; i++) {
        if (i + 2 < size && src[i] == 0 && src[i + 1] == 0 && src[i + 2] == 3) {
            dst[n++] = 0;
            if (n < cap)
                dst[n++] = 0;
            i += 2;
            continue;
        }
        dst[n++] = src[i];
    }
    return n;
}

static uint32_t be(const uint8_t *p, int bytes)
{
    uint32_t v = 0;
    for (int i = 0; i < bytes; i++)
        v = (v << 8) | p[i];
    return v;
}

/*
 * The SEI messages of a prefix SEI NAL unit (header included): HDR10+ (ST 2094-40 user data),
 * mastering display colour volume and content light level.
 */
static void scan_sei(VideoScan *v, const uint8_t *nal, size_t size)
{
    uint8_t rbsp[2048];
    size_t n = unescape(nal + 2, size - 2, rbsp, sizeof(rbsp));
    size_t i = 0;
    while (i + 2 <= n) {
        unsigned type = 0, len = 0;
        while (i < n && rbsp[i] == 0xFF)
            type += rbsp[i++];
        if (i >= n)
            break;
        type += rbsp[i++];
        while (i < n && rbsp[i] == 0xFF)
            len += rbsp[i++];
        if (i >= n)
            break;
        len += rbsp[i++];
        if (len > n - i)
            break;
        const uint8_t *p = rbsp + i;
        if (type == 4 && len >= 6 && p[0] == 0xB5 && p[1] == 0x00 && p[2] == 0x3C && p[3] == 0x00 && p[4] == 0x01 && p[5] == 0x04) {
            /* user_data_registered_itu_t_t35: USA, Samsung, oriented code 1, application 4 */
            v->hdr10plus = 1;
        } else if (type == 137 && len >= 24 && v->max_luminance == 0) {
            /* mastering_display_colour_volume: luminance in 0.0001 cd/m2 after the primaries */
            v->max_luminance = be(p + 16, 4) / 10000.0;
            v->min_luminance = be(p + 20, 4) / 10000.0;
        } else if (type == 144 && len >= 4 && v->max_cll == 0) {
            v->max_cll = be(p, 2);
            v->max_fall = be(p + 2, 2);
        }
        i += len;
    }
}

static void scan_nal(VideoScan *v, const uint8_t *nal, size_t size)
{
    if (size < 3)
        return;
    int type = nal_type(nal);
    if (type == NAL_UNSPEC63 || nal_layer(nal) > 0) {
        v->el_nals++;
    } else if (type == NAL_SEI_PREFIX) {
        scan_sei(v, nal, size);
    } else if (type == NAL_UNSPEC62 && !v->rpu) {
        DoviRpuOpaque *rpu = dovi_parse_unspec62_nalu(nal, size);
        if (rpu && !dovi_rpu_get_error(rpu)) {
            const DoviRpuDataHeaderPrefix *h = dovi_rpu_get_header(rpu);
            if (h) {
                v->rpu = 1;
                v->rpu_profile = h->guessed_profile;
                if (h->el_type)
                    av_strlcpy(v->el_type, h->el_type, sizeof(v->el_type));
                dovi_rpu_free_header(h);
            }
        }
        if (rpu)
            dovi_rpu_free(rpu);
    }
}

/* Every NAL unit of a packet: length-prefixed ([length_size] bytes) or Annex B (0). */
static void scan_packet(VideoScan *v, const uint8_t *data, int size, int length_size)
{
    const uint8_t *p = data, *end = data + size;
    if (length_size) {
        while (end - p >= length_size) {
            uint32_t n = 0;
            for (int i = 0; i < length_size; i++)
                n = (n << 8) | p[i];
            p += length_size;
            if (n > (uint32_t)(end - p))
                return;
            scan_nal(v, p, n);
            p += n;
        }
        return;
    }
    const uint8_t *start = NULL;
    while (p + 3 <= end) {
        if (p[0] == 0 && p[1] == 0 && p[2] == 1) {
            if (start) {
                const uint8_t *stop = p;
                while (stop > start && stop[-1] == 0)
                    stop--;
                scan_nal(v, start, (size_t)(stop - start));
            }
            p += 3;
            start = p;
        } else {
            p++;
        }
    }
    if (start && start < end)
        scan_nal(v, start, (size_t)(end - start));
}

/* ---- JSON ----------------------------------------------------------------------------------- */

static void json_text(AVBPrint *bp, const char *s)
{
    av_bprint_chars(bp, '"', 1);
    for (const unsigned char *p = (const unsigned char *)(s ? s : ""); *p; p++) {
        if (*p == '"' || *p == '\\')
            av_bprintf(bp, "\\%c", *p);
        else if (*p < 0x20)
            av_bprintf(bp, "\\u%04x", *p);
        else
            av_bprint_chars(bp, (char)*p, 1);
    }
    av_bprint_chars(bp, '"', 1);
}

static void key(AVBPrint *bp, int *first, const char *name)
{
    if (!*first)
        av_bprint_chars(bp, ',', 1);
    *first = 0;
    av_bprintf(bp, "\"%s\":", name);
}

static void put_text(AVBPrint *bp, int *first, const char *name, const char *value)
{
    key(bp, first, name);
    json_text(bp, value);
}

static void put_int(AVBPrint *bp, int *first, const char *name, long long value)
{
    key(bp, first, name);
    av_bprintf(bp, "%lld", value);
}

static void put_real(AVBPrint *bp, int *first, const char *name, double value)
{
    key(bp, first, name);
    av_bprintf(bp, "%.4f", value);
}

static void put_bool(AVBPrint *bp, int *first, const char *name, int value)
{
    key(bp, first, name);
    av_bprintf(bp, "%s", value ? "true" : "false");
}

static const char *tag(const AVDictionary *metadata, const char *name)
{
    const AVDictionaryEntry *e = av_dict_get(metadata, name, NULL, 0);
    return e ? e->value : "";
}

static const AVPacketSideData *side_data(const AVCodecParameters *par, enum AVPacketSideDataType type)
{
    return av_packet_side_data_get(par->coded_side_data, par->nb_coded_side_data, type);
}

/* hvcC: version 1, at least the 23-byte header */
static int is_hvcc(const AVCodecParameters *par)
{
    return par->extradata && par->extradata_size >= 23 && par->extradata[0] == 1;
}

static const char *hevc_profile(int idc)
{
    switch (idc) {
    case 1: return "Main";
    case 2: return "Main 10";
    case 3: return "Main Still Picture";
    case 4: return "Rext";
    default: return NULL;
    }
}

static void write_video(AVBPrint *bp, const AVStream *st, const VideoScan *scan)
{
    const AVCodecParameters *par = st->codecpar;
    int first = 1;
    av_bprint_chars(bp, '{', 1);
    put_text(bp, &first, "codec", avcodec_get_name(par->codec_id));
    const char *profile = avcodec_profile_name(par->codec_id, par->profile);
    if (!profile && par->codec_id == AV_CODEC_ID_HEVC && is_hvcc(par))
        profile = hevc_profile(par->extradata[1] & 0x1F);
    put_text(bp, &first, "profile", profile);
    put_int(bp, &first, "width", par->width);
    put_int(bp, &first, "height", par->height);
    AVRational rate = st->avg_frame_rate.num && st->avg_frame_rate.den ? st->avg_frame_rate : st->r_frame_rate;
    put_real(bp, &first, "fps", rate.num && rate.den ? av_q2d(rate) : 0);

    int depth = 0;
    const AVPixFmtDescriptor *desc = av_pix_fmt_desc_get((enum AVPixelFormat)par->format);
    if (desc)
        depth = desc->comp[0].depth;
    else if (par->codec_id == AV_CODEC_ID_HEVC && is_hvcc(par))
        depth = (par->extradata[17] & 7) + 8;
    else if (par->bits_per_raw_sample > 0)
        depth = par->bits_per_raw_sample;
    else if (profile && (par->codec_id == AV_CODEC_ID_HEVC || par->codec_id == AV_CODEC_ID_H264))
        depth = strstr(profile, "12") ? 12 : strstr(profile, "10") ? 10 : 8; /* "Main 10", "High 10", "High" */
    put_int(bp, &first, "bit_depth", depth);

    put_text(bp, &first, "transfer", av_color_transfer_name(par->color_trc));
    put_text(bp, &first, "primaries", av_color_primaries_name(par->color_primaries));
    put_text(bp, &first, "matrix", av_color_space_name(par->color_space));
    double max_lum = 0, min_lum = 0;
    const AVPacketSideData *sd = side_data(par, AV_PKT_DATA_MASTERING_DISPLAY_METADATA);
    if (sd && sd->size >= sizeof(AVMasteringDisplayMetadata)) {
        const AVMasteringDisplayMetadata *m = (const AVMasteringDisplayMetadata *)sd->data;
        if (m->has_luminance) {
            max_lum = av_q2d(m->max_luminance);
            min_lum = av_q2d(m->min_luminance);
        }
    }
    if (max_lum == 0) {
        max_lum = scan->max_luminance;
        min_lum = scan->min_luminance;
    }
    put_real(bp, &first, "max_luminance", max_lum);
    put_real(bp, &first, "min_luminance", min_lum);
    unsigned max_cll = scan->max_cll, max_fall = scan->max_fall;
    sd = side_data(par, AV_PKT_DATA_CONTENT_LIGHT_LEVEL);
    if (sd && sd->size >= sizeof(AVContentLightMetadata)) {
        const AVContentLightMetadata *l = (const AVContentLightMetadata *)sd->data;
        max_cll = l->MaxCLL;
        max_fall = l->MaxFALL;
    }
    put_int(bp, &first, "max_cll", max_cll);
    put_int(bp, &first, "max_fall", max_fall);
    put_bool(bp, &first, "hdr10plus", scan->hdr10plus || side_data(par, AV_PKT_DATA_DYNAMIC_HDR10_PLUS) != NULL);

    int dv_profile = 0, dv_level = 0, dv_compat = 0, el_present = scan->el_nals > 0;
    sd = side_data(par, AV_PKT_DATA_DOVI_CONF);
    if (sd && sd->size >= sizeof(AVDOVIDecoderConfigurationRecord)) {
        const AVDOVIDecoderConfigurationRecord *c = (const AVDOVIDecoderConfigurationRecord *)sd->data;
        dv_profile = c->dv_profile;
        dv_level = c->dv_level;
        dv_compat = c->dv_bl_signal_compatibility_id;
        el_present |= c->el_present_flag;
    }
    if (!dv_profile && scan->rpu)
        dv_profile = scan->rpu_profile;
    put_int(bp, &first, "dv_profile", dv_profile);
    put_int(bp, &first, "dv_level", dv_level);
    put_int(bp, &first, "dv_compatibility", dv_compat);
    put_text(bp, &first, "dv_el", dv_profile == 7 ? scan->el_type : "");
    put_bool(bp, &first, "dv_el_present", el_present);
    put_bool(bp, &first, "dv_rpu", scan->rpu);
    av_bprint_chars(bp, '}', 1);
}

static void write_audio(AVBPrint *bp, const AVStream *st)
{
    const AVCodecParameters *par = st->codecpar;
    int first = 1;
    char layout[64] = "";
    if (av_channel_layout_describe(&par->ch_layout, layout, sizeof(layout)) < 0)
        layout[0] = 0;
    av_bprint_chars(bp, '{', 1);
    put_text(bp, &first, "codec", avcodec_get_name(par->codec_id));
    put_text(bp, &first, "profile", avcodec_profile_name(par->codec_id, par->profile));
    put_int(bp, &first, "channels", par->ch_layout.nb_channels);
    put_text(bp, &first, "layout", layout);
    put_int(bp, &first, "sample_rate", par->sample_rate);
    put_int(bp, &first, "bits", par->bits_per_raw_sample);
    put_int(bp, &first, "bitrate", par->bit_rate);
    put_text(bp, &first, "language", tag(st->metadata, "language"));
    put_text(bp, &first, "title", tag(st->metadata, "title"));
    put_bool(bp, &first, "default", st->disposition & AV_DISPOSITION_DEFAULT);
    put_bool(bp, &first, "forced", st->disposition & AV_DISPOSITION_FORCED);
    put_bool(bp, &first, "commentary", st->disposition & AV_DISPOSITION_COMMENT);
    av_bprint_chars(bp, '}', 1);
}

static void write_subtitle(AVBPrint *bp, const AVStream *st)
{
    int first = 1;
    av_bprint_chars(bp, '{', 1);
    put_text(bp, &first, "codec", avcodec_get_name(st->codecpar->codec_id));
    put_text(bp, &first, "language", tag(st->metadata, "language"));
    put_text(bp, &first, "title", tag(st->metadata, "title"));
    put_bool(bp, &first, "default", st->disposition & AV_DISPOSITION_DEFAULT);
    put_bool(bp, &first, "forced", st->disposition & AV_DISPOSITION_FORCED);
    put_bool(bp, &first, "hearing_impaired", st->disposition & AV_DISPOSITION_HEARING_IMPAIRED);
    av_bprint_chars(bp, '}', 1);
}

static char *finish(AVBPrint *bp)
{
    char *json = av_bprint_is_complete(bp) ? strdup(bp->str) : NULL;
    av_bprint_finalize(bp, NULL);
    return json;
}

static char *error_json(const char *what, int err)
{
    char msg[AV_ERROR_MAX_STRING_SIZE] = {0}, text[AV_ERROR_MAX_STRING_SIZE + 64];
    av_strerror(err, msg, sizeof(msg));
    snprintf(text, sizeof(text), "%s: %s", what, msg);
    AVBPrint bp;
    av_bprint_init(&bp, 0, AV_BPRINT_SIZE_UNLIMITED);
    int first = 1;
    av_bprint_chars(&bp, '{', 1);
    put_int(&bp, &first, "version", MEDIA_PROBE_VERSION);
    put_text(&bp, &first, "error", text);
    av_bprint_chars(&bp, '}', 1);
    return finish(&bp);
}

char *media_probe(const char *input, volatile int *cancel)
{
    AVFormatContext *ic = NULL;
    AVDictionary *opts = NULL;
    int ret;
    /* enough for the stream info of a remux, without reading whole minutes of it */
    av_dict_set(&opts, "probesize", "20000000", 0);
    av_dict_set(&opts, "analyzeduration", "10000000", 0);
    ret = avformat_open_input(&ic, input, NULL, &opts);
    av_dict_free(&opts);
    if (ret < 0)
        return error_json("open", ret);
    if ((ret = avformat_find_stream_info(ic, NULL)) < 0) {
        avformat_close_input(&ic);
        return error_json("stream info", ret);
    }

    int video = -1;
    for (unsigned i = 0; i < ic->nb_streams && video < 0; i++) {
        const AVStream *st = ic->streams[i];
        if (st->codecpar->codec_type == AVMEDIA_TYPE_VIDEO && !(st->disposition & AV_DISPOSITION_ATTACHED_PIC))
            video = (int)i;
    }

    VideoScan scan = {0};
    if (video >= 0 && ic->streams[video]->codecpar->codec_id == AV_CODEC_ID_HEVC) {
        const AVCodecParameters *par = ic->streams[video]->codecpar;
        int length_size = is_hvcc(par) ? (par->extradata[21] & 3) + 1 : 0;
        AVPacket *pkt = av_packet_alloc();
        int64_t start = ic->pb ? avio_tell(ic->pb) : 0;
        while (pkt && scan.packets < SCAN_VIDEO_PACKETS && !(cancel && *cancel)) {
            if (av_read_frame(ic, pkt) < 0)
                break;
            if (pkt->stream_index == video) {
                scan.packets++;
                if (av_packet_get_side_data(pkt, AV_PKT_DATA_DYNAMIC_HDR10_PLUS, NULL))
                    scan.hdr10plus = 1;
                scan_packet(&scan, pkt->data, pkt->size, length_size);
            }
            av_packet_unref(pkt);
            if (ic->pb && avio_tell(ic->pb) - start > SCAN_MAX_BYTES)
                break;
        }
        av_packet_free(&pkt);
    }

    AVBPrint bp;
    av_bprint_init(&bp, 0, AV_BPRINT_SIZE_UNLIMITED);
    int first = 1;
    av_bprint_chars(&bp, '{', 1);
    put_int(&bp, &first, "version", MEDIA_PROBE_VERSION);
    put_text(&bp, &first, "error", "");
    put_text(&bp, &first, "format", ic->iformat ? ic->iformat->name : "");
    put_text(&bp, &first, "brand", tag(ic->metadata, "major_brand"));
    put_int(&bp, &first, "duration_ms", ic->duration != AV_NOPTS_VALUE ? ic->duration / 1000 : 0);
    put_int(&bp, &first, "size", ic->pb ? avio_size(ic->pb) : 0);
    put_int(&bp, &first, "bitrate", ic->bit_rate);
    put_int(&bp, &first, "chapters", ic->nb_chapters);
    key(&bp, &first, "video");
    if (video >= 0)
        write_video(&bp, ic->streams[video], &scan);
    else
        av_bprintf(&bp, "%s", "null");
    key(&bp, &first, "audio");
    av_bprint_chars(&bp, '[', 1);
    int n = 0;
    for (unsigned i = 0; i < ic->nb_streams; i++) {
        if (ic->streams[i]->codecpar->codec_type != AVMEDIA_TYPE_AUDIO)
            continue;
        if (n++)
            av_bprint_chars(&bp, ',', 1);
        write_audio(&bp, ic->streams[i]);
    }
    av_bprint_chars(&bp, ']', 1);
    key(&bp, &first, "subtitles");
    av_bprint_chars(&bp, '[', 1);
    n = 0;
    for (unsigned i = 0; i < ic->nb_streams; i++) {
        if (ic->streams[i]->codecpar->codec_type != AVMEDIA_TYPE_SUBTITLE)
            continue;
        if (n++)
            av_bprint_chars(&bp, ',', 1);
        write_subtitle(&bp, ic->streams[i]);
    }
    av_bprint_chars(&bp, ']', 1);
    av_bprint_chars(&bp, '}', 1);
    avformat_close_input(&ic);
    return finish(&bp);
}
