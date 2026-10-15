#include "source.h"

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/audio_fifo.h>
#include <libavutil/channel_layout.h>
#include <libavutil/dict.h>
#include <libavutil/opt.h>
#include <libswresample/swresample.h>
#include <libopenmpt/libopenmpt.h>

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* decoding errors in a row before a read gives up: a damaged frame is skipped, a damaged file is not */
#define MAX_DECODE_ERRORS 32

/* S-911: a tracker module is rendered at this rate, this many frames at a time: as ffmpeg's
 * libopenmpt demuxer renders it, so that the render is the same (libopenmpt's volume ramps go
 * by the size of the reads) */
#define MOD_RATE 48000
#define MOD_CHUNK 256
/* a module is read whole: a file larger than this is not one */
#define MOD_MAX_BYTES (64 << 20)

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
    int64_t fifo_end;   /* the sample position (in the stream's own count) just past the last frame pushed into the fifo */
    /* The stream's first timestamp in samples: its count runs from there (MP3's trimmed encoder
     * delay, 1105 samples, say), while the file's frames are counted from 0. */
    int64_t pts_offset;
    /* S-808: the part of the file read, in its frames: [part_start, part_end); part_end -1 for the end */
    int64_t part_start;
    int64_t part_end;
    int64_t file_duration_ms; /* the whole file's, as read_info found it */
    /* S-1010: where an MP4's AAC ends, in the stream's own count, as its iTunSMPB or edit list
     * says: the encoder's padding after it left out (FFmpeg's demuxer trims the priming, not
     * that); -1 when the file says neither */
    int64_t stream_end;
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
    /* S-911: a tracker module, rendered by libopenmpt instead of decoded by FFmpeg */
    openmpt_module *mod;
    float *mod_buffer;    /* MOD_CHUNK frames of what it renders, mono or interleaved stereo */
    int64_t mod_position; /* the frame the next render starts at, in the module's own count */
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

/* A text tag of the stream, else of the file; NULL when neither has it */
static const char *first_text(const AVFormatContext *ic, const AVStream *st, const char *key)
{
    const AVDictionaryEntry *e = av_dict_get(st->metadata, key, NULL, 0);
    if (!e || !e->value || !e->value[0])
        e = av_dict_get(ic->metadata, key, NULL, 0);
    return e && e->value && e->value[0] ? e->value : NULL;
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
    s->file_duration_ms = info->duration_ms;
    s->pts_offset = st->start_time != AV_NOPTS_VALUE && st->start_time > 0 ? av_rescale_q(st->start_time, st->time_base, (AVRational){1, info->rate}) : 0;
    info->bitrate = par->bit_rate > 0 ? par->bit_rate : s->ic->bit_rate;
    snprintf(info->codec, sizeof(info->codec), "%s", avcodec_get_name(par->codec_id));
    snprintf(info->container, sizeof(info->container), "%s", s->ic->iformat ? s->ic->iformat->name : "");
    const char *composer = first_text(s->ic, st, "composer");
    if (composer) {
        snprintf(info->composer, sizeof(info->composer), "%s", composer);
        if (strlen(composer) >= sizeof(info->composer)) {
            /* cut short: not in the middle of a character */
            size_t n = strlen(info->composer), i = n;
            while (i > 0 && ((unsigned char)info->composer[i - 1] & 0xC0) == 0x80)
                i--;
            if (i > 0) {
                unsigned char lead = (unsigned char)info->composer[i - 1];
                size_t need = lead >= 0xF0 ? 4 : lead >= 0xE0 ? 3 : lead >= 0xC0 ? 2 : 1;
                if (n - (i - 1) < need)
                    info->composer[i - 1] = 0;
            }
        }
    }
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

/* The converter from frames of [in_layout] in [in_fmt] to the fifo's doubles in s->channels, at the same rate. */
static int open_swr_from(NagSource *s, const AVChannelLayout *in_layout, enum AVSampleFormat in_fmt, int rate, char *error, size_t error_size)
{
    AVChannelLayout out_layout;
    if (s->channels == in_layout->nb_channels) {
        av_channel_layout_copy(&out_layout, in_layout);
    } else {
        av_channel_layout_default(&out_layout, s->channels);
    }
    int ret = swr_alloc_set_opts2(&s->swr, &out_layout, AV_SAMPLE_FMT_DBL, rate, in_layout, in_fmt, rate, 0, NULL);
    if (ret < 0) {
        av_channel_layout_uninit(&out_layout);
        fail(error, error_size, "swresample", ret);
        return ret;
    }
    /* a mono file on a stereo output: the same samples on both sides, not 3 dB down */
    if (in_layout->nb_channels == 1 && s->channels == 2) {
        const double matrix[2] = {1.0, 1.0};
        swr_set_matrix(s->swr, matrix, 1);
    } else if (in_layout->nb_channels > s->channels) {
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

static int open_swr(NagSource *s, char *error, size_t error_size)
{
    return open_swr_from(s, &s->dec->ch_layout, s->dec->sample_fmt, s->dec->sample_rate, error, error_size);
}

/*
 * S-911: a tracker module (MOD, XM, S3M, IT, MPTM, …), told from the file's first bytes and
 * read whole for libopenmpt, which renders it to float at MOD_RATE: stereo, or mono when the
 * frames asked for are mono. Returns 1 when [url] is a module and open, 0 when it is not one
 * (FFmpeg reads it then), negative when it is one that cannot be read.
 */
static int mod_open(NagSource *s, const char *url, int channels, char *error, size_t error_size)
{
    AVIOContext *pb = NULL;
    if (avio_open2(&pb, url, AVIO_FLAG_READ, NULL, NULL) < 0)
        return 0; /* FFmpeg says what is wrong with the file */
    int64_t size = avio_size(pb);
    if (size <= 0 || size > MOD_MAX_BYTES) {
        avio_closep(&pb);
        return 0;
    }
    /* the first bytes tell: with the recommended amount the answer is final */
    int64_t head = (int64_t)openmpt_probe_file_header_get_recommended_size();
    if (head > size)
        head = size;
    uint8_t *data = malloc((size_t)head);
    if (!data) {
        avio_closep(&pb);
        fail(error, error_size, "out of memory", AVERROR(ENOMEM));
        return AVERROR(ENOMEM);
    }
    int64_t at = 0;
    while (at < head) {
        int n = avio_read(pb, data + at, (int)(head - at));
        if (n <= 0)
            break;
        at += n;
    }
    int probe = at == head ? openmpt_probe_file_header(OPENMPT_PROBE_FILE_HEADER_FLAGS_DEFAULT, data, (size_t)head, (uint64_t)size,
                                                       NULL, NULL, NULL, NULL, NULL, NULL)
                           : OPENMPT_PROBE_FILE_HEADER_RESULT_FAILURE;
    if (probe != OPENMPT_PROBE_FILE_HEADER_RESULT_SUCCESS) {
        free(data);
        avio_closep(&pb);
        return 0;
    }
    uint8_t *whole = realloc(data, (size_t)size);
    if (!whole) {
        free(data);
        avio_closep(&pb);
        fail(error, error_size, "out of memory", AVERROR(ENOMEM));
        return AVERROR(ENOMEM);
    }
    data = whole;
    while (at < size) {
        int n = avio_read(pb, data + at, (int)FFMIN(size - at, 1 << 20));
        if (n <= 0)
            break;
        at += n;
    }
    avio_closep(&pb);
    if (at != size) {
        free(data);
        fail(error, error_size, "the module could not be read whole", AVERROR(EIO));
        return AVERROR(EIO);
    }
    int err = OPENMPT_ERROR_OK;
    const char *message = NULL;
    s->mod = openmpt_module_create_from_memory2(data, (size_t)size, NULL, NULL, NULL, NULL, &err, &message, NULL);
    free(data);
    if (!s->mod) {
        if (error && error_size)
            snprintf(error, error_size, "module: %s", message && message[0] ? message : "cannot be read");
        openmpt_free_string(message);
        return AVERROR_INVALIDDATA;
    }
    openmpt_free_string(message);
    openmpt_module_set_repeat_count(s->mod, 0); /* once through, its loops as written */
    s->channels = channels > 0 ? channels : 2;
    int rendered = s->channels == 1 ? 1 : 2;
    AVChannelLayout layout;
    av_channel_layout_default(&layout, rendered);
    int ret = open_swr_from(s, &layout, AV_SAMPLE_FMT_FLT, MOD_RATE, error, error_size);
    av_channel_layout_uninit(&layout);
    if (ret < 0)
        return ret;
    s->mod_buffer = malloc(sizeof(float) * MOD_CHUNK * (size_t)rendered);
    s->fifo = av_audio_fifo_alloc(AV_SAMPLE_FMT_DBL, s->channels, 1 << 16);
    if (!s->mod_buffer || !s->fifo) {
        fail(error, error_size, "out of memory", AVERROR(ENOMEM));
        return AVERROR(ENOMEM);
    }
    /* what the file is: the module is the "codec", its kind (mod, xm, it, …) the container */
    NagSourceInfo *info = &s->info;
    memset(info, 0, sizeof(*info));
    info->rate = MOD_RATE;
    info->channels = rendered;
    info->is_float = 1;
    info->duration_ms = llround(openmpt_module_get_duration_seconds(s->mod) * 1000.0);
    s->file_duration_ms = info->duration_ms;
    snprintf(info->codec, sizeof(info->codec), "tracker");
    const char *type = openmpt_module_get_metadata(s->mod, "type");
    snprintf(info->container, sizeof(info->container), "%s", type && type[0] ? type : "mod");
    openmpt_free_string(type);
    info->track_gain_db = info->track_peak = info->album_gain_db = info->album_peak = NAN;
    return 1;
}

/* Renders the next frames of the module into the fifo, minus what a seek asked to drop. */
static int mod_render(NagSource *s)
{
    int rendered = s->channels == 1 ? 1 : 2;
    size_t got = rendered == 1 ? openmpt_module_read_float_mono(s->mod, MOD_RATE, MOD_CHUNK, s->mod_buffer)
                               : openmpt_module_read_interleaved_float_stereo(s->mod, MOD_RATE, MOD_CHUNK, s->mod_buffer);
    if (got == 0) {
        s->drained = 1;
        return 0;
    }
    int64_t position = s->mod_position;
    s->mod_position += (int64_t)got;
    int offset = 0;
    if (s->skip_until >= 0) {
        if (position + (int64_t)got <= s->skip_until) {
            s->fifo_end = position + (int64_t)got;
            return 0;
        }
        if (position < s->skip_until)
            offset = (int)(s->skip_until - position);
        s->skip_until = -1;
    }
    s->fifo_end = position + (int64_t)got;
    int frames = (int)got - offset;
    uint8_t *out = NULL;
    int ret = av_samples_alloc(&out, NULL, s->channels, frames, AV_SAMPLE_FMT_DBL, 0);
    if (ret < 0)
        return ret;
    const uint8_t *in[1] = {(const uint8_t *)(s->mod_buffer + (size_t)offset * (size_t)rendered)};
    ret = swr_convert(s->swr, &out, frames, in, frames);
    if (ret > 0)
        ret = av_audio_fifo_write(s->fifo, (void **)&out, ret);
    av_freep(&out);
    return ret < 0 ? ret : 0;
}

/*
 * To frame [target] of the module: libopenmpt lands on the first row boundary at or after the
 * time asked, so it is asked for earlier times until it lands at or before the target; the
 * rest is dropped as it renders, and what follows starts on the frame asked.
 */
static int mod_seek(NagSource *s, int64_t target)
{
    double ask = (double)target / MOD_RATE;
    double at = openmpt_module_set_position_seconds(s->mod, ask);
    double step = 0.01;
    for (int i = 0; i < 24 && at * MOD_RATE > (double)target + 0.5 && ask > 0.0; i++) {
        ask -= step;
        if (ask < 0.0)
            ask = 0.0;
        step *= 2.0;
        at = openmpt_module_set_position_seconds(s->mod, ask);
    }
    int64_t landed = llround(at * MOD_RATE);
    if (landed < 0)
        landed = 0;
    av_audio_fifo_reset(s->fifo);
    s->drained = 0;
    s->mod_position = landed;
    s->skip_until = landed < target ? target : -1;
    s->fifo_end = landed;
    return 0;
}

/* An MP4 box's header at the position read: its type and where it ends; 0 when none fits before [end]. */
static int box_header(AVIOContext *pb, int64_t end, uint32_t *type, int64_t *box_end)
{
    int64_t start = avio_tell(pb);
    if (start < 0 || end - start < 8)
        return 0;
    uint64_t size = avio_rb32(pb);
    *type = avio_rl32(pb);
    int header = 8;
    if (size == 1) {
        size = avio_rb64(pb);
        header = 16;
    } else if (size == 0) {
        size = (uint64_t)(end - start);
    }
    if (size < (uint64_t)header || size > (uint64_t)(end - start) || pb->eof_reached)
        return 0;
    *box_end = start + (int64_t)size;
    return 1;
}

/* A trak's track id and its edit list: the one segment that plays media, or none (*segment -1). */
static void read_trak(AVIOContext *pb, int64_t end, int *track_id, int64_t *segment)
{
    uint32_t type;
    int64_t box_end;
    while (box_header(pb, end, &type, &box_end)) {
        if (type == MKTAG('t', 'k', 'h', 'd')) {
            int version = avio_r8(pb);
            avio_skip(pb, 3 + (version == 1 ? 16 : 8));
            *track_id = (int)avio_rb32(pb);
        } else if (type == MKTAG('e', 'd', 't', 's')) {
            read_trak(pb, box_end, track_id, segment);
        } else if (type == MKTAG('e', 'l', 's', 't')) {
            int version = avio_r8(pb);
            avio_skip(pb, 3);
            uint32_t count = avio_rb32(pb);
            int media = 0;
            int64_t duration = -1;
            for (uint32_t i = 0; i < count && !pb->eof_reached && avio_tell(pb) < box_end; i++) {
                int64_t d = version == 1 ? (int64_t)avio_rb64(pb) : (int64_t)avio_rb32(pb);
                int64_t t = version == 1 ? (int64_t)avio_rb64(pb) : (int64_t)(int32_t)avio_rb32(pb);
                avio_skip(pb, 4); /* the rate */
                if (t != -1) {
                    media++;
                    duration = d;
                }
            }
            /* one segment of media only: its length is the audio's; anything else is not ours to read */
            *segment = media == 1 && duration > 0 ? duration : -1;
        }
        if (avio_seek(pb, box_end, SEEK_SET) < 0)
            return;
    }
}

/*
 * S-1010: where an MP4's AAC track says its audio ends, in samples after the priming: the
 * iTunSMPB atom's sample count (iTunes), else the track's edit list (FFmpeg, Apple), its one
 * segment at the movie's timescale. -1 when the file says neither: the track then plays to its
 * last decoded sample, so a file with a damaged or short header loses nothing. The boxes are
 * read on a handle of their own; the demuxer's position is untouched.
 */
static int64_t mp4_audio_end(NagSource *s, const char *url, const AVStream *st)
{
    const AVDictionaryEntry *smpb = av_dict_get(s->ic->metadata, "iTunSMPB", NULL, 0);
    if (smpb && smpb->value) {
        unsigned priming = 0, remainder = 0;
        unsigned long long samples = 0;
        if (sscanf(smpb->value, "%*x %x %x %llx", &priming, &remainder, &samples) == 3 && samples > 0)
            return (int64_t)samples;
    }
    AVIOContext *pb = NULL;
    if (avio_open2(&pb, url, AVIO_FLAG_READ, NULL, NULL) < 0)
        return -1;
    const int64_t size = avio_size(pb);
    uint32_t movie_scale = 0, type;
    int64_t segment = -1, box_end;
    while (size > 0 && box_header(pb, size, &type, &box_end)) {
        if (type == MKTAG('m', 'o', 'o', 'v')) {
            const int64_t moov_end = box_end;
            while (box_header(pb, moov_end, &type, &box_end)) {
                if (type == MKTAG('m', 'v', 'h', 'd')) {
                    int version = avio_r8(pb);
                    avio_skip(pb, 3 + (version == 1 ? 16 : 8));
                    movie_scale = avio_rb32(pb);
                } else if (type == MKTAG('t', 'r', 'a', 'k')) {
                    int track_id = -1;
                    int64_t trak_segment = -1;
                    read_trak(pb, box_end, &track_id, &trak_segment);
                    if (track_id == st->id)
                        segment = trak_segment;
                }
                if (avio_seek(pb, box_end, SEEK_SET) < 0)
                    break;
            }
            break;
        }
        if (avio_seek(pb, box_end, SEEK_SET) < 0)
            break;
    }
    avio_closep(&pb);
    if (segment <= 0 || movie_scale == 0)
        return -1;
    return av_rescale(segment, s->info.rate, movie_scale);
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
    s->part_start = 0;
    s->part_end = -1;
    s->stream_end = -1;
    /* S-911: a tracker module is libopenmpt's; anything else FFmpeg's */
    int ret = mod_open(s, url, channels, error, error_size);
    if (ret != 0) {
        if (ret < 0)
            goto fail;
        *out = s;
        return 0;
    }
    AVDictionary *opts = NULL;
    av_dict_set(&opts, "probesize", "4000000", 0);
    ret = avformat_open_input(&s->ic, url, NULL, &opts);
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
    if (st->codecpar->codec_id == AV_CODEC_ID_AAC && s->ic->iformat && strncmp(s->ic->iformat->name, "mov", 3) == 0) {
        int64_t end = mp4_audio_end(s, url, st);
        if (end > 0)
            s->stream_end = end + s->pts_offset;
    }
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
    if (s->mod)
        openmpt_module_destroy(s->mod);
    free(s->mod_buffer);
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
            s->fifo_end = position + samples;
            return 0;
        }
        if (position < s->skip_until)
            offset = (int)(s->skip_until - position);
        s->skip_until = -1;
    }
    s->next_pts = position + samples;
    s->fifo_end = position + samples;
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
            s->fifo_end = position + frames;
            free(out);
            return 0;
        }
        if (position < s->skip_until)
            offset = (int)(s->skip_until - position);
        s->skip_until = -1;
    }
    s->dop_position = position + frames;
    s->fifo_end = position + frames;
    int ret = 0;
    if (frames - offset > 0) {
        double *planes[1] = {out + (size_t)offset * 2};
        ret = av_audio_fifo_write(s->fifo, (void **)planes, frames - offset);
    }
    free(out);
    return ret < 0 ? ret : 0;
}

/* Fills the fifo with at least [want] samples, or all that is left. */
/* Whether the fifo already reaches the part's end: nothing more needs decoding. */
static int part_filled(const NagSource *s)
{
    return s->part_end >= 0 && s->fifo_end >= s->part_end + s->pts_offset;
}

static int fill(NagSource *s, int want)
{
    int errors = 0;
    if (s->mod) {
        while (!s->drained && !part_filled(s) && av_audio_fifo_size(s->fifo) < want) {
            int ret = mod_render(s);
            if (ret < 0)
                return ret;
        }
        return 0;
    }
    if (s->dop) {
        while (!s->drained && !part_filled(s) && av_audio_fifo_size(s->fifo) < want) {
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
    while (!s->drained && !part_filled(s) && av_audio_fifo_size(s->fifo) < want) {
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
    const int64_t head = s->fifo_end - av_audio_fifo_size(s->fifo);
    if (s->part_end >= 0) {
        /* S-808: the part ends on its last frame exactly */
        int64_t left = s->part_end + s->pts_offset - head;
        if (left <= 0)
            return 0;
        if (have > left)
            have = (int)left;
    }
    if (s->stream_end >= 0) {
        int64_t left = s->stream_end - head;
        if (left <= 0)
            return 0;
        if (have > left)
            have = (int)left;
    }
    void *planes[1] = {dst};
    ret = av_audio_fifo_read(s->fifo, planes, have);
    return ret < 0 ? ret : have;
}

/*
 * To file frame [target], sample accurate: the demuxer to the key before it, the rest dropped.
 * The demuxer is asked for a little before the target, so that a decoder which leans on the
 * frames before (MP3's bit reservoir, a codec's priming) has them when the target comes.
 */
#define SEEK_PREROLL_MS 200

static int seek_frames(NagSource *s, int64_t target)
{
    if (target < 0)
        target = 0;
    if (s->mod)
        return mod_seek(s, target);
    AVStream *st = s->ic->streams[s->stream];
    int64_t preroll = av_rescale(SEEK_PREROLL_MS, s->info.rate, 1000);
    int64_t before = target > preroll ? target - preroll : 0;
    int64_t ts = av_rescale_q(before + s->pts_offset, (AVRational){1, s->info.rate}, st->time_base);
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
    s->skip_until = target + s->pts_offset;
    s->next_pts = 0;
    s->fifo_end = 0;
    s->dop_carried[0] = 0;
    s->dop_marker = 0x05;
    s->dop_position = 0;
    return 0;
}

int nag_source_seek(NagSource *s, int64_t ms)
{
    if (ms < 0)
        ms = 0;
    int64_t target = s->part_start + av_rescale(ms, s->info.rate, 1000);
    if (s->part_end >= 0 && target > s->part_end)
        target = s->part_end;
    return seek_frames(s, target);
}

int nag_source_set_part(NagSource *s, int64_t start, int64_t end)
{
    if (start < 0)
        start = 0;
    if (end >= 0 && end < start)
        end = start;
    s->part_start = start;
    s->part_end = end;
    /* the info tells the part's length */
    if (end >= 0)
        s->info.duration_ms = av_rescale(end - start, 1000, s->info.rate);
    else if (s->file_duration_ms > 0)
        s->info.duration_ms = s->file_duration_ms - av_rescale(start, 1000, s->info.rate);
    if (s->info.duration_ms < 0)
        s->info.duration_ms = 0;
    return start > 0 ? seek_frames(s, start) : 0;
}

void nag_source_part(const NagSource *s, int64_t *start, int64_t *end)
{
    *start = s->part_start;
    *end = s->part_end;
}
