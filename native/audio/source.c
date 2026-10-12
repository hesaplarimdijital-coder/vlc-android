#include "source.h"

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/audio_fifo.h>
#include <libavutil/channel_layout.h>
#include <libavutil/dict.h>
#include <libavutil/opt.h>
#include <libswresample/swresample.h>

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* decoding errors in a row before a read gives up: a damaged frame is skipped, a damaged file is not */
#define MAX_DECODE_ERRORS 32

struct NagSource {
    AVFormatContext *ic;
    AVCodecContext *dec;
    SwrContext *swr;
    AVAudioFifo *fifo;
    AVPacket *packet;
    AVFrame *frame;
    int stream;
    int channels;      /* of the frames read */
    int64_t skip_until; /* samples (at the file's rate) to drop after a seek; -1 none */
    int64_t next_pts;   /* the sample position of the next decoded frame, when the decoder gives no pts */
    int eof_sent;      /* the decoder was told the input ended */
    int drained;       /* nothing more will come */
    NagSourceInfo info;
    /* S-908: DSD over PCM, without a decoder */
    int dop;
    int dop_planar;    /* the packets hold each channel's bytes in a run */
    int dop_lsbf;      /* the bits of each byte oldest last: reversed for DoP */
    int dop_marker;    /* 0x05 or 0xFA, alternating per frame */
    uint8_t dop_carry[2];
    int dop_carried[2];
    int64_t dop_position; /* frames before the next packet, when its timestamp is unknown */
};

static int is_dsd(enum AVCodecID id)
{
    return id == AV_CODEC_ID_DSD_LSBF || id == AV_CODEC_ID_DSD_MSBF || id == AV_CODEC_ID_DSD_LSBF_PLANAR || id == AV_CODEC_ID_DSD_MSBF_PLANAR;
}

static void fail(char *error, size_t size, const char *what, int ret)
{
    char reason[AV_ERROR_MAX_STRING_SIZE] = {0};
    if (ret < 0)
        av_strerror(ret, reason, sizeof(reason));
    if (error && size)
        snprintf(error, size, "%s%s%s", what, ret < 0 ? ": " : "", ret < 0 ? reason : "");
}

/* "-6.54 dB" → -6.54; "0.987654" → 0.987654; NAN when absent or unreadable */
static double tag_number(const AVDictionary *dict, const char *key)
{
    const AVDictionaryEntry *e = av_dict_get(dict, key, NULL, 0);
    if (!e || !e->value)
        return NAN;
    char *end = NULL;
    double v = strtod(e->value, &end);
    return end == e->value ? NAN : v;
}

static double first_tag(const AVFormatContext *ic, const AVStream *st, const char *key)
{
    double v = tag_number(st->metadata, key);
    return isnan(v) ? tag_number(ic->metadata, key) : v;
}

static void read_info(NagSource *s)
{
    const AVStream *st = s->ic->streams[s->stream];
    const AVCodecParameters *par = st->codecpar;
    NagSourceInfo *info = &s->info;
    memset(info, 0, sizeof(*info));
    info->rate = par->sample_rate;
    info->channels = par->ch_layout.nb_channels;
    if (is_dsd(par->codec_id))
        info->dsd_rate = par->sample_rate * 8; /* FFmpeg counts a DSD stream's rate in bytes */
    if (s->dop) {
        /* DoP: 24-bit words, two DSD bytes of each channel per frame */
        info->rate = par->sample_rate / 2;
        info->bits = 24;
        info->is_float = 0;
        info->dop = 1;
    } else {
        info->is_float = av_sample_fmt_is_planar(s->dec->sample_fmt) ? (s->dec->sample_fmt == AV_SAMPLE_FMT_FLTP || s->dec->sample_fmt == AV_SAMPLE_FMT_DBLP)
                                                                     : (s->dec->sample_fmt == AV_SAMPLE_FMT_FLT || s->dec->sample_fmt == AV_SAMPLE_FMT_DBL);
        if (par->bits_per_raw_sample > 0)
            info->bits = par->bits_per_raw_sample;
        else if (!info->is_float)
            info->bits = av_get_bytes_per_sample(s->dec->sample_fmt) * 8;
    }
    /* FLAC and ALAC say their depth in bits_per_raw_sample; a 24-bit file decoded to s32 keeps 24 */
    if (st->duration > 0 && st->time_base.den > 0)
        info->duration_ms = av_rescale_q(st->duration, st->time_base, (AVRational){1, 1000});
    else if (s->ic->duration > 0)
        info->duration_ms = s->ic->duration / (AV_TIME_BASE / 1000);
    info->bitrate = par->bit_rate > 0 ? par->bit_rate : s->ic->bit_rate;
    snprintf(info->codec, sizeof(info->codec), "%s", avcodec_get_name(par->codec_id));
    snprintf(info->container, sizeof(info->container), "%s", s->ic->iformat ? s->ic->iformat->name : "");
    info->track_gain_db = first_tag(s->ic, st, "REPLAYGAIN_TRACK_GAIN");
    info->track_peak = first_tag(s->ic, st, "REPLAYGAIN_TRACK_PEAK");
    info->album_gain_db = first_tag(s->ic, st, "REPLAYGAIN_ALBUM_GAIN");
    info->album_peak = first_tag(s->ic, st, "REPLAYGAIN_ALBUM_PEAK");
    /* Opus writes R128_TRACK_GAIN in Q7.8 dB relative to -23 LUFS; as ReplayGain means -18 LUFS, add 5 dB */
    if (isnan(info->track_gain_db)) {
        double r128 = first_tag(s->ic, st, "R128_TRACK_GAIN");
        if (!isnan(r128))
            info->track_gain_db = r128 / 256.0 + 5.0;
    }
    if (isnan(info->album_gain_db)) {
        double r128 = first_tag(s->ic, st, "R128_ALBUM_GAIN");
        if (!isnan(r128))
            info->album_gain_db = r128 / 256.0 + 5.0;
    }
}

static int open_swr(NagSource *s, char *error, size_t error_size)
{
    AVChannelLayout out_layout;
    if (s->channels == s->dec->ch_layout.nb_channels) {
        av_channel_layout_copy(&out_layout, &s->dec->ch_layout);
    } else {
        av_channel_layout_default(&out_layout, s->channels);
    }
    int ret = swr_alloc_set_opts2(&s->swr, &out_layout, AV_SAMPLE_FMT_DBL, s->dec->sample_rate,
                                  &s->dec->ch_layout, s->dec->sample_fmt, s->dec->sample_rate, 0, NULL);
    if (ret < 0) {
        av_channel_layout_uninit(&out_layout);
        fail(error, error_size, "swresample", ret);
        return ret;
    }
    /* a mono file on a stereo output: the same samples on both sides, not 3 dB down */
    if (s->dec->ch_layout.nb_channels == 1 && s->channels == 2) {
        const double matrix[2] = {1.0, 1.0};
        swr_set_matrix(s->swr, matrix, 1);
    } else if (s->dec->ch_layout.nb_channels > s->channels) {
        /* a downmix that cannot clip: the coefficients of each output sum to one at most */
        av_opt_set_double(s->swr, "rematrix_maxval", 1.0, 0);
    }
    av_channel_layout_uninit(&out_layout);
    if ((ret = swr_init(s->swr)) < 0) {
        fail(error, error_size, "swresample init", ret);
        return ret;
    }
    return 0;
}

int nag_source_open(NagSource **out, const char *url, int channels, char *error, size_t error_size)
{
    return nag_source_open_ex(out, url, channels, 0, error, error_size);
}

int nag_source_open_ex(NagSource **out, const char *url, int channels, int flags, char *error, size_t error_size)
{
    *out = NULL;
    NagSource *s = calloc(1, sizeof(*s));
    if (!s) {
        fail(error, error_size, "out of memory", 0);
        return AVERROR(ENOMEM);
    }
    s->skip_until = -1;
    s->next_pts = 0;
    AVDictionary *opts = NULL;
    av_dict_set(&opts, "probesize", "4000000", 0);
    int ret = avformat_open_input(&s->ic, url, NULL, &opts);
    av_dict_free(&opts);
    if (ret < 0) {
        fail(error, error_size, "open", ret);
        goto fail;
    }
    if ((ret = avformat_find_stream_info(s->ic, NULL)) < 0) {
        fail(error, error_size, "stream info", ret);
        goto fail;
    }
    const AVCodec *codec = NULL;
    ret = av_find_best_stream(s->ic, AVMEDIA_TYPE_AUDIO, -1, -1, &codec, 0);
    if (ret < 0 || !codec) {
        fail(error, error_size, "no audio stream", ret < 0 ? ret : AVERROR_DECODER_NOT_FOUND);
        if (ret >= 0)
            ret = AVERROR_DECODER_NOT_FOUND;
        goto fail;
    }
    s->stream = ret;
    AVStream *st = s->ic->streams[s->stream];
    /* the other streams (a cover picture, a video) are never read */
    for (unsigned i = 0; i < s->ic->nb_streams; i++)
        if ((int)i != s->stream)
            s->ic->streams[i]->discard = AVDISCARD_ALL;
    if ((flags & NAG_SOURCE_DOP) && is_dsd(st->codecpar->codec_id) && st->codecpar->ch_layout.nb_channels == 2 && (channels == 0 || channels == 2)) {
        /* S-908: the DSD bits as they are, under DoP markers; no decoder, no mix */
        s->dop = 1;
        s->dop_planar = st->codecpar->codec_id == AV_CODEC_ID_DSD_LSBF_PLANAR || st->codecpar->codec_id == AV_CODEC_ID_DSD_MSBF_PLANAR;
        s->dop_lsbf = st->codecpar->codec_id == AV_CODEC_ID_DSD_LSBF || st->codecpar->codec_id == AV_CODEC_ID_DSD_LSBF_PLANAR;
        s->dop_marker = 0x05;
        s->channels = 2;
        s->fifo = av_audio_fifo_alloc(AV_SAMPLE_FMT_DBL, 2, 1 << 16);
        s->packet = av_packet_alloc();
        if (!s->fifo || !s->packet) {
            ret = AVERROR(ENOMEM);
            fail(error, error_size, "out of memory", ret);
            goto fail;
        }
        read_info(s);
        *out = s;
        return 0;
    }
    s->dec = avcodec_alloc_context3(codec);
    if (!s->dec) {
        ret = AVERROR(ENOMEM);
        fail(error, error_size, "decoder", ret);
        goto fail;
    }
    if ((ret = avcodec_parameters_to_context(s->dec, st->codecpar)) < 0) {
        fail(error, error_size, "decoder parameters", ret);
        goto fail;
    }
    s->dec->pkt_timebase = st->time_base;
    s->dec->thread_count = 1;
    if ((ret = avcodec_open2(s->dec, codec, NULL)) < 0) {
        fail(error, error_size, "decoder open", ret);
        goto fail;
    }
    if (s->dec->sample_rate <= 0 || s->dec->ch_layout.nb_channels <= 0) {
        ret = AVERROR_INVALIDDATA;
        fail(error, error_size, "no sample rate or channels", ret);
        goto fail;
    }
    s->channels = channels > 0 ? channels : s->dec->ch_layout.nb_channels;
    if ((ret = open_swr(s, error, error_size)) < 0)
        goto fail;
    s->fifo = av_audio_fifo_alloc(AV_SAMPLE_FMT_DBL, s->channels, 1 << 16);
    s->packet = av_packet_alloc();
    s->frame = av_frame_alloc();
    if (!s->fifo || !s->packet || !s->frame) {
        ret = AVERROR(ENOMEM);
        fail(error, error_size, "out of memory", ret);
        goto fail;
    }
    read_info(s);
    *out = s;
    return 0;
fail:
    nag_source_close(s);
    return ret;
}

void nag_source_close(NagSource *s)
{
    if (!s)
        return;
    av_frame_free(&s->frame);
    av_packet_free(&s->packet);
    if (s->fifo)
        av_audio_fifo_free(s->fifo);
    swr_free(&s->swr);
    avcodec_free_context(&s->dec);
    avformat_close_input(&s->ic);
    free(s);
}

const NagSourceInfo *nag_source_info(const NagSource *s)
{
    return &s->info;
}

int nag_source_channels(const NagSource *s)
{
    return s->channels;
}

int nag_source_channel_weights(const NagSource *s, double *weights, int max)
{
    AVChannelLayout layout;
    if (s->dec && s->channels == s->dec->ch_layout.nb_channels)
        av_channel_layout_copy(&layout, &s->dec->ch_layout);
    else
        av_channel_layout_default(&layout, s->channels);
    for (int i = 0; i < s->channels && i < max; i++) {
        enum AVChannel ch = av_channel_layout_channel_from_index(&layout, (unsigned)i);
        switch (ch) {
        case AV_CHAN_LOW_FREQUENCY:
        case AV_CHAN_LOW_FREQUENCY_2:
            weights[i] = 0.0;
            break;
        case AV_CHAN_SIDE_LEFT:
        case AV_CHAN_SIDE_RIGHT:
        case AV_CHAN_BACK_LEFT:
        case AV_CHAN_BACK_RIGHT:
            weights[i] = 1.41;
            break;
        default:
            weights[i] = 1.0;
            break;
        }
    }
    av_channel_layout_uninit(&layout);
    return s->channels;
}

/* The sample position of [frame], from its timestamp when it has one; the running count otherwise. */
static int64_t frame_position(NagSource *s, const AVFrame *frame)
{
    int64_t pts = frame->pts != AV_NOPTS_VALUE ? frame->pts : frame->best_effort_timestamp;
    if (pts == AV_NOPTS_VALUE)
        return s->next_pts;
    const AVStream *st = s->ic->streams[s->stream];
    return av_rescale_q(pts, st->time_base, (AVRational){1, s->dec->sample_rate});
}

/* Converts a decoded frame into the fifo, minus what a seek asked to drop. */
static int push_frame(NagSource *s, AVFrame *frame)
{
    int64_t position = frame_position(s, frame);
    int samples = frame->nb_samples;
    int offset = 0;
    if (s->skip_until >= 0) {
        if (position + samples <= s->skip_until) {
            s->next_pts = position + samples;
            return 0;
        }
        if (position < s->skip_until)
            offset = (int)(s->skip_until - position);
        s->skip_until = -1;
    }
    s->next_pts = position + samples;
    int out_samples = samples - offset;
    if (out_samples <= 0)
        return 0;
    uint8_t *out = NULL;
    int ret = av_samples_alloc(&out, NULL, s->channels, out_samples, AV_SAMPLE_FMT_DBL, 0);
    if (ret < 0)
        return ret;
    const uint8_t *in[AV_NUM_DATA_POINTERS] = {0};
    int planar = av_sample_fmt_is_planar(frame->format);
    int bytes = av_get_bytes_per_sample(frame->format);
    int in_channels = frame->ch_layout.nb_channels;
    for (int c = 0; c < (planar ? in_channels : 1); c++)
        in[c] = frame->extended_data[c] + (planar ? offset * bytes : offset * bytes * in_channels);
    ret = swr_convert(s->swr, &out, out_samples, in, out_samples);
    if (ret > 0)
        ret = av_audio_fifo_write(s->fifo, (void **)&out, ret);
    av_freep(&out);
    return ret < 0 ? ret : 0;
}

static const uint8_t bit_reverse[256] = {
#define R2(n) n, n + 2 * 64, n + 1 * 64, n + 3 * 64
#define R4(n) R2(n), R2(n + 2 * 16), R2(n + 1 * 16), R2(n + 3 * 16)
#define R6(n) R4(n), R4(n + 2 * 4), R4(n + 1 * 4), R4(n + 3 * 4)
    R6(0), R6(2), R6(1), R6(3)
#undef R2
#undef R4
#undef R6
};

/* A packet of DSD bytes as DoP frames into the fifo: two bytes of each channel under a marker. */
static int push_dop_packet(NagSource *s, const AVPacket *pkt)
{
    const AVStream *st = s->ic->streams[s->stream];
    int per_channel = pkt->size / 2;
    if (per_channel <= 0)
        return 0;
    /* where this packet starts, in DoP frames: its timestamp counts bytes per channel */
    int64_t position = s->dop_position;
    if (pkt->pts != AV_NOPTS_VALUE)
        position = av_rescale_q(pkt->pts, st->time_base, (AVRational){1, st->codecpar->sample_rate}) / 2;
    int frames_max = (per_channel + 1) / 2 + 1;
    double *out = malloc(sizeof(double) * 2 * (size_t)frames_max);
    if (!out)
        return AVERROR(ENOMEM);
    int frames = 0;
    for (int i = 0; i < per_channel; i++) {
        uint8_t bytes[2];
        for (int c = 0; c < 2; c++) {
            uint8_t b = s->dop_planar ? pkt->data[(size_t)c * per_channel + i] : pkt->data[(size_t)i * 2 + c];
            bytes[c] = s->dop_lsbf ? bit_reverse[b] : b;
        }
        if (!s->dop_carried[0]) {
            s->dop_carry[0] = bytes[0];
            s->dop_carry[1] = bytes[1];
            s->dop_carried[0] = 1;
            continue;
        }
        for (int c = 0; c < 2; c++) {
            int32_t word = (s->dop_marker << 16) | (s->dop_carry[c] << 8) | bytes[c];
            if (word & 0x800000)
                word -= 0x1000000; /* the 24-bit word, signed */
            out[(size_t)frames * 2 + c] = word / 8388608.0;
        }
        s->dop_carried[0] = 0;
        s->dop_marker = s->dop_marker == 0x05 ? 0xFA : 0x05;
        frames++;
    }
    int offset = 0;
    if (s->skip_until >= 0) {
        if (position + frames <= s->skip_until) {
            s->dop_position = position + frames;
            free(out);
            return 0;
        }
        if (position < s->skip_until)
            offset = (int)(s->skip_until - position);
        s->skip_until = -1;
    }
    s->dop_position = position + frames;
    int ret = 0;
    if (frames - offset > 0) {
        double *planes[1] = {out + (size_t)offset * 2};
        ret = av_audio_fifo_write(s->fifo, (void **)planes, frames - offset);
    }
    free(out);
    return ret < 0 ? ret : 0;
}

/* Fills the fifo with at least [want] samples, or all that is left. */
static int fill(NagSource *s, int want)
{
    int errors = 0;
    if (s->dop) {
        while (!s->drained && av_audio_fifo_size(s->fifo) < want) {
            int ret = av_read_frame(s->ic, s->packet);
            if (ret == AVERROR_EOF) {
                s->drained = 1;
                break;
            }
            if (ret < 0)
                return ret;
            if (s->packet->stream_index == s->stream)
                ret = push_dop_packet(s, s->packet);
            av_packet_unref(s->packet);
            if (ret < 0)
                return ret;
        }
        return 0;
    }
    while (!s->drained && av_audio_fifo_size(s->fifo) < want) {
        int ret = avcodec_receive_frame(s->dec, s->frame);
        if (ret == 0) {
            ret = push_frame(s, s->frame);
            av_frame_unref(s->frame);
            if (ret < 0)
                return ret;
            continue;
        }
        if (ret == AVERROR_EOF) {
            s->drained = 1;
            break;
        }
        if (ret != AVERROR(EAGAIN))
            return ret;
        /* the decoder wants a packet */
        if (s->eof_sent)
            continue;
        ret = av_read_frame(s->ic, s->packet);
        if (ret == AVERROR_EOF) {
            s->eof_sent = 1;
            avcodec_send_packet(s->dec, NULL);
            continue;
        }
        if (ret < 0)
            return ret;
        if (s->packet->stream_index != s->stream) {
            av_packet_unref(s->packet);
            continue;
        }
        ret = avcodec_send_packet(s->dec, s->packet);
        av_packet_unref(s->packet);
        if (ret < 0 && ret != AVERROR(EAGAIN)) {
            if (ret == AVERROR_EOF)
                continue;
            /* a damaged packet: skipped, up to a point */
            if (++errors > MAX_DECODE_ERRORS)
                return ret;
        }
    }
    return 0;
}

int nag_source_read(NagSource *s, double *dst, int max_frames)
{
    if (max_frames <= 0)
        return 0;
    int ret = fill(s, max_frames);
    if (ret < 0)
        return ret;
    int have = av_audio_fifo_size(s->fifo);
    if (have <= 0)
        return 0;
    if (have > max_frames)
        have = max_frames;
    void *planes[1] = {dst};
    ret = av_audio_fifo_read(s->fifo, planes, have);
    return ret < 0 ? ret : have;
}

int nag_source_seek(NagSource *s, int64_t ms)
{
    if (ms < 0)
        ms = 0;
    AVStream *st = s->ic->streams[s->stream];
    int64_t ts = av_rescale_q(ms, (AVRational){1, 1000}, st->time_base);
    int ret = av_seek_frame(s->ic, s->stream, ts, AVSEEK_FLAG_BACKWARD);
    if (ret < 0) {
        /* a stream that cannot seek by time: back to the start */
        ret = av_seek_frame(s->ic, s->stream, 0, AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_BYTE);
        if (ret < 0)
            return ret;
    }
    if (s->dec)
        avcodec_flush_buffers(s->dec);
    av_audio_fifo_reset(s->fifo);
    s->eof_sent = 0;
    s->drained = 0;
    s->skip_until = av_rescale(ms, s->info.rate, 1000);
    s->next_pts = 0;
    s->dop_carried[0] = 0;
    s->dop_marker = 0x05;
    s->dop_position = 0;
    return 0;
}
