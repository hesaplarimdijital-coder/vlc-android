/*
 * Nağmedar Ses Motoru: the converter (S-1010). A file, or a CUE sheet's part of it, read by the
 * engine's source (FFmpeg, libopenmpt; 64-bit float frames at the file's rate), resampled with
 * swresample where the codec asks for another rate, and encoded anew:
 *
 *   Opus  libopus, 20 ms packets into FFmpeg's Ogg muxer. The packets' timestamps start at
 *         minus the encoder's lookahead (the OpusHead's pre-skip), and the last ones are as
 *         long as the audio is, so the last granule position trims the padding (RFC 7845).
 *   MP3   LAME writes the stream itself: its first frame is the Xing/LAME frame, filled in at
 *         the end with the encoder delay and the padding (what makes a decoder gapless).
 *   AAC   FFmpeg's own encoder into its M4A muxer (ipod), which writes the priming and the
 *         padding into the edit list.
 *
 * The output is a descriptor (a content document's on Android): the muxers write through an
 * AVIOContext over it, LAME's bytes go to it directly. The tags and the front cover follow
 * with TagLib (nag_tags_copy); a source with ReplayGain gets its track gain measured again on
 * the output (nag_loudness_scan_file), the album's kept.
 */
#include "nagaudio.h"
#include "source.h"

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/audio_fifo.h>
#include <libavutil/channel_layout.h>
#include <libavutil/intreadwrite.h>
#include <libavutil/mem.h>
#include <libavutil/opt.h>
#include <libswresample/swresample.h>
#include <lame/lame.h>
#include <opus/opus.h>

#include <errno.h>
#include <limits.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

/* frames read from the source at a time */
#define READ_FRAMES 4096
/* Opus: 20 ms packets at 48 kHz */
#define OPUS_RATE 48000
#define OPUS_FRAME 960
#define OPUS_MAX_PACKET 4000
/* frames kept back in the fifo until the end, so that the resampler's excess can be cut off there */
#define FIFO_RESERVE 2048

typedef struct Conv {
    const NagConvertOptions *opt;
    int out_fd;
    const char *out_name;
    char *error;
    size_t error_size;
    nag_convert_progress_fn progress;
    void *opaque;

    NagSource *src;
    int in_rate;
    int rate;
    int channels;
    SwrContext *swr;
    AVAudioFifo *fifo; /* float frames at the output's rate, interleaved */
    int64_t produced;  /* frames into the fifo */
    int64_t encoded;   /* frames out of the fifo into the encoder */

    /* Opus */
    OpusEncoder *opus;
    int pre_skip;
    int64_t opus_packets;
    /* AAC */
    AVCodecContext *aac;
    AVFrame *aac_frame;
    /* Opus and AAC: the muxer and its I/O */
    AVFormatContext *oc;
    AVIOContext *io;
    AVPacket *packet;
    /* MP3 */
    lame_t lame;
    unsigned char *mp3_buffer;
    int mp3_buffer_size;
} Conv;

static int failed(Conv *c, int ret, const char *what)
{
    if (ret == NAG_CONVERT_CANCELLED) {
        snprintf(c->error, c->error_size, "cancelled");
        return ret;
    }
    if (ret < 0 && ret != -1) {
        char text[AV_ERROR_MAX_STRING_SIZE] = {0};
        av_strerror(ret, text, sizeof(text));
        snprintf(c->error, c->error_size, "%s: %s", what, text);
    } else {
        snprintf(c->error, c->error_size, "%s", what);
    }
    return ret < 0 ? ret : -1;
}

/* ----------------------------------------------------------------- output I/O */

static int write_all(int fd, const uint8_t *data, size_t size)
{
    while (size > 0) {
        ssize_t n = write(fd, data, size);
        if (n < 0) {
            if (errno == EINTR)
                continue;
            return AVERROR(errno);
        }
        if (n == 0)
            return AVERROR(EIO);
        data += n;
        size -= (size_t)n;
    }
    return 0;
}

static int io_write(void *opaque, const uint8_t *buf, int size)
{
    int ret = write_all(*(int *)opaque, buf, (size_t)size);
    return ret < 0 ? ret : size;
}

static int64_t io_seek(void *opaque, int64_t offset, int whence)
{
    int fd = *(int *)opaque;
    if (whence == AVSEEK_SIZE) {
        off_t here = lseek(fd, 0, SEEK_CUR);
        off_t end = lseek(fd, 0, SEEK_END);
        if (here >= 0)
            lseek(fd, here, SEEK_SET);
        return end < 0 ? AVERROR(errno) : end;
    }
    off_t at = lseek(fd, (off_t)offset, whence & ~AVSEEK_FORCE);
    return at < 0 ? AVERROR(errno) : at;
}

/* The muxer [format] writing to the output descriptor, with one audio stream. */
static int open_muxer(Conv *c, const char *format, AVStream **stream)
{
    int ret = avformat_alloc_output_context2(&c->oc, NULL, format, c->out_name);
    if (ret < 0 || !c->oc)
        return failed(c, ret < 0 ? ret : AVERROR_MUXER_NOT_FOUND, "muxer");
    const int size = 1 << 16;
    unsigned char *buffer = av_malloc(size);
    if (!buffer)
        return failed(c, AVERROR(ENOMEM), "out of memory");
    c->io = avio_alloc_context(buffer, size, 1, &c->out_fd, NULL, io_write, io_seek);
    if (!c->io) {
        av_free(buffer);
        return failed(c, AVERROR(ENOMEM), "out of memory");
    }
    c->oc->pb = c->io;
    c->oc->flags |= AVFMT_FLAG_CUSTOM_IO;
    *stream = avformat_new_stream(c->oc, NULL);
    if (!*stream)
        return failed(c, AVERROR(ENOMEM), "out of memory");
    c->packet = av_packet_alloc();
    if (!c->packet)
        return failed(c, AVERROR(ENOMEM), "out of memory");
    return 0;
}

/* --------------------------------------------------------------------- Opus */

static int opus_open(Conv *c)
{
    int err = 0;
    c->opus = opus_encoder_create(OPUS_RATE, c->channels, OPUS_APPLICATION_AUDIO, &err);
    if (err != OPUS_OK || !c->opus) {
        snprintf(c->error, c->error_size, "libopus: %s", opus_strerror(err));
        return -1;
    }
    opus_encoder_ctl(c->opus, OPUS_SET_BITRATE(c->opt->bitrate > 0 ? c->opt->bitrate : 128000));
    opus_encoder_ctl(c->opus, OPUS_SET_VBR(1));
    opus_encoder_ctl(c->opus, OPUS_SET_VBR_CONSTRAINT(0));
    opus_encoder_ctl(c->opus, OPUS_SET_COMPLEXITY(10));
    opus_encoder_ctl(c->opus, OPUS_SET_SIGNAL(OPUS_SIGNAL_MUSIC));
    opus_int32 lookahead = 0;
    opus_encoder_ctl(c->opus, OPUS_GET_LOOKAHEAD(&lookahead));
    c->pre_skip = (int)lookahead;

    AVStream *st;
    int ret = open_muxer(c, "ogg", &st);
    if (ret < 0)
        return ret;
    /* RFC 7845's identification header: mapping family 0 (mono or stereo) */
    uint8_t *head = av_mallocz(19 + AV_INPUT_BUFFER_PADDING_SIZE);
    if (!head)
        return failed(c, AVERROR(ENOMEM), "out of memory");
    memcpy(head, "OpusHead", 8);
    head[8] = 1;
    head[9] = (uint8_t)c->channels;
    AV_WL16(head + 10, c->pre_skip);
    AV_WL32(head + 12, c->in_rate);
    AV_WL16(head + 16, 0);
    head[18] = 0;
    st->codecpar->codec_type = AVMEDIA_TYPE_AUDIO;
    st->codecpar->codec_id = AV_CODEC_ID_OPUS;
    st->codecpar->sample_rate = OPUS_RATE;
    av_channel_layout_default(&st->codecpar->ch_layout, c->channels);
    st->codecpar->extradata = head;
    st->codecpar->extradata_size = 19;
    st->codecpar->initial_padding = c->pre_skip;
    st->codecpar->bit_rate = c->opt->bitrate;
    st->time_base = (AVRational){1, OPUS_RATE};
    if ((ret = avformat_write_header(c->oc, NULL)) < 0)
        return failed(c, ret, "Ogg header");
    return 0;
}

/* One packet of [frames] frames (960, zero-padded at the end), lasting [duration] of them. */
static int opus_packet(Conv *c, const float *pcm, int duration)
{
    uint8_t data[OPUS_MAX_PACKET];
    opus_int32 size = opus_encode_float(c->opus, pcm, OPUS_FRAME, data, sizeof(data));
    if (size < 0) {
        snprintf(c->error, c->error_size, "libopus: %s", opus_strerror(size));
        return -1;
    }
    int ret = av_new_packet(c->packet, size);
    if (ret < 0)
        return failed(c, ret, "out of memory");
    memcpy(c->packet->data, data, (size_t)size);
    /* the encoder's output runs pre_skip behind its input */
    c->packet->pts = c->packet->dts = c->opus_packets * OPUS_FRAME - c->pre_skip;
    c->packet->duration = duration;
    c->packet->stream_index = 0;
    c->opus_packets++;
    ret = av_write_frame(c->oc, c->packet);
    av_packet_unref(c->packet);
    return ret < 0 ? failed(c, ret, "Ogg write") : 0;
}

/*
 * The last packets: what is left in the fifo, zero-padded, then silence until the lookahead is
 * through; each lasts as much of the audio (plus the pre-skip) as it carries.
 */
static int opus_finish(Conv *c, float *pcm)
{
    const int64_t needed = c->encoded + av_audio_fifo_size(c->fifo) + c->pre_skip;
    while (c->opus_packets * OPUS_FRAME < needed) {
        memset(pcm, 0, sizeof(float) * OPUS_FRAME * c->channels);
        int got = av_audio_fifo_read(c->fifo, (void **)&pcm, OPUS_FRAME);
        if (got > 0)
            c->encoded += got;
        int64_t left = needed - c->opus_packets * OPUS_FRAME;
        int ret = opus_packet(c, pcm, left < OPUS_FRAME ? (int)left : OPUS_FRAME);
        if (ret < 0)
            return ret;
    }
    int ret = av_write_trailer(c->oc);
    return ret < 0 ? failed(c, ret, "Ogg trailer") : 0;
}

/* ---------------------------------------------------------------------- MP3 */

static const int mp3_rates[] = {48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000};

static int mp3_open(Conv *c)
{
    c->lame = lame_init();
    if (!c->lame)
        return failed(c, -1, "LAME: out of memory");
    lame_set_num_channels(c->lame, c->channels);
    lame_set_in_samplerate(c->lame, c->rate);
    lame_set_out_samplerate(c->lame, c->rate);
    lame_set_mode(c->lame, c->channels == 1 ? MONO : JOINT_STEREO);
    if (c->opt->bitrate > 0) {
        lame_set_VBR(c->lame, vbr_off);
        lame_set_brate(c->lame, c->opt->bitrate / 1000);
    } else {
        lame_set_VBR(c->lame, vbr_default);
        int q = c->opt->vbr_quality;
        lame_set_VBR_quality(c->lame, (float)(q < 0 ? 0 : q > 9 ? 9 : q));
    }
    /* the Xing/LAME frame first, filled in at the end; the tags are TagLib's */
    lame_set_bWriteVbrTag(c->lame, 1);
    lame_set_write_id3tag_automatic(c->lame, 0);
    if (lame_init_params(c->lame) < 0)
        return failed(c, -1, "LAME: these settings are not MP3's");
    c->mp3_buffer_size = READ_FRAMES * 2 + 7200;
    c->mp3_buffer = malloc((size_t)c->mp3_buffer_size);
    return c->mp3_buffer ? 0 : failed(c, AVERROR(ENOMEM), "out of memory");
}

static int mp3_encode(Conv *c, float *pcm, int frames)
{
    /* LAME takes -1..1: what the resampler carried past full scale is clipped here */
    for (int i = 0; i < frames * c->channels; i++)
        pcm[i] = pcm[i] > 1.0f ? 1.0f : pcm[i] < -1.0f ? -1.0f : pcm[i];
    int bytes = c->channels == 1
        ? lame_encode_buffer_ieee_float(c->lame, pcm, NULL, frames, c->mp3_buffer, c->mp3_buffer_size)
        : lame_encode_buffer_interleaved_ieee_float(c->lame, pcm, frames, c->mp3_buffer, c->mp3_buffer_size);
    if (bytes < 0)
        return failed(c, -1, "LAME: encoding failed");
    int ret = write_all(c->out_fd, c->mp3_buffer, (size_t)bytes);
    return ret < 0 ? failed(c, ret, "write") : 0;
}

static int mp3_finish(Conv *c)
{
    int bytes = lame_encode_flush(c->lame, c->mp3_buffer, c->mp3_buffer_size);
    if (bytes < 0)
        return failed(c, -1, "LAME: flush failed");
    int ret = write_all(c->out_fd, c->mp3_buffer, (size_t)bytes);
    if (ret < 0)
        return failed(c, ret, "write");
    /* the first frame: the Xing/LAME frame with the delay, the padding and the frame count */
    unsigned char tag[2880];
    size_t size = lame_get_lametag_frame(c->lame, tag, sizeof(tag));
    if (size > 0 && size <= sizeof(tag) && pwrite(c->out_fd, tag, size, 0) != (ssize_t)size)
        return failed(c, AVERROR(errno), "write");
    return 0;
}

/* ---------------------------------------------------------------------- AAC */

static const int aac_rates[] = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000};

static int aac_open(Conv *c)
{
    const AVCodec *codec = avcodec_find_encoder(AV_CODEC_ID_AAC);
    if (!codec)
        return failed(c, AVERROR_ENCODER_NOT_FOUND, "AAC encoder");
    c->aac = avcodec_alloc_context3(codec);
    if (!c->aac)
        return failed(c, AVERROR(ENOMEM), "out of memory");
    c->aac->sample_fmt = AV_SAMPLE_FMT_FLTP;
    c->aac->sample_rate = c->rate;
    av_channel_layout_default(&c->aac->ch_layout, c->channels);
    c->aac->bit_rate = c->opt->bitrate > 0 ? c->opt->bitrate : 256000;
    c->aac->time_base = (AVRational){1, c->rate};
    c->aac->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    int ret = avcodec_open2(c->aac, codec, NULL);
    if (ret < 0)
        return failed(c, ret, "AAC encoder");
    c->aac_frame = av_frame_alloc();
    if (!c->aac_frame)
        return failed(c, AVERROR(ENOMEM), "out of memory");
    c->aac_frame->format = AV_SAMPLE_FMT_FLTP;
    c->aac_frame->nb_samples = c->aac->frame_size;
    c->aac_frame->sample_rate = c->rate;
    av_channel_layout_copy(&c->aac_frame->ch_layout, &c->aac->ch_layout);
    if ((ret = av_frame_get_buffer(c->aac_frame, 0)) < 0)
        return failed(c, ret, "out of memory");

    AVStream *st;
    if ((ret = open_muxer(c, "ipod", &st)) < 0)
        return ret;
    if ((ret = avcodec_parameters_from_context(st->codecpar, c->aac)) < 0)
        return failed(c, ret, "M4A stream");
    st->time_base = (AVRational){1, c->rate};
    if ((ret = avformat_write_header(c->oc, NULL)) < 0)
        return failed(c, ret, "M4A header");
    return 0;
}

static int aac_drain(Conv *c)
{
    for (;;) {
        int ret = avcodec_receive_packet(c->aac, c->packet);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF)
            return 0;
        if (ret < 0)
            return failed(c, ret, "AAC encoding");
        c->packet->stream_index = 0;
        av_packet_rescale_ts(c->packet, c->aac->time_base, c->oc->streams[0]->time_base);
        ret = av_write_frame(c->oc, c->packet);
        av_packet_unref(c->packet);
        if (ret < 0)
            return failed(c, ret, "M4A write");
    }
}

/* [frames] interleaved frames (at most the frame size) as one planar frame; NULL ends the stream. */
static int aac_encode(Conv *c, const float *pcm, int frames)
{
    int ret;
    if (pcm) {
        if ((ret = av_frame_make_writable(c->aac_frame)) < 0)
            return failed(c, ret, "out of memory");
        c->aac_frame->nb_samples = frames;
        for (int ch = 0; ch < c->channels; ch++) {
            float *plane = (float *)c->aac_frame->extended_data[ch];
            for (int i = 0; i < frames; i++)
                plane[i] = pcm[i * c->channels + ch];
        }
        c->aac_frame->pts = c->encoded;
        ret = avcodec_send_frame(c->aac, c->aac_frame);
    } else {
        ret = avcodec_send_frame(c->aac, NULL);
    }
    if (ret < 0)
        return failed(c, ret, "AAC encoding");
    return aac_drain(c);
}

/* ------------------------------------------------------------------- driver */

/* The same file behind two descriptors: the same device and inode, or the same path as /proc reads it. */
static int same_file(int a, const struct stat *sa, int b, const struct stat *sb)
{
    if (sa->st_dev == sb->st_dev && sa->st_ino == sb->st_ino)
        return 1;
    char link[64], pa[PATH_MAX], pb[PATH_MAX];
    snprintf(link, sizeof(link), "/proc/self/fd/%d", a);
    ssize_t na = readlink(link, pa, sizeof(pa) - 1);
    snprintf(link, sizeof(link), "/proc/self/fd/%d", b);
    ssize_t nb = readlink(link, pb, sizeof(pb) - 1);
    if (na <= 0 || nb <= 0 || pa[0] != '/')
        return 0;
    pa[na] = 0;
    pb[nb] = 0;
    return strcmp(pa, pb) == 0;
}

static int pick_rate(int asked, int source, const int *rates, int count, int fallback_44, int fallback_48)
{
    if (asked > 0) {
        for (int i = 0; i < count; i++)
            if (rates[i] == asked)
                return asked;
        return -1;
    }
    for (int i = 0; i < count; i++)
        if (rates[i] == source)
            return source;
    /* the highest rate of the same family (44.1 kHz's or 48 kHz's) below the source's */
    for (int i = 0; i < count; i++)
        if (rates[i] < source && (source % 11025 == 0) == (rates[i] % 11025 == 0))
            return rates[i];
    return source % 11025 == 0 ? fallback_44 : fallback_48;
}

static int frame_size(const Conv *c)
{
    switch (c->opt->codec) {
    case NAG_CONVERT_OPUS: return OPUS_FRAME;
    case NAG_CONVERT_AAC: return c->aac->frame_size;
    default: return 1152;
    }
}

static int encode(Conv *c, float *pcm, int frames)
{
    switch (c->opt->codec) {
    case NAG_CONVERT_OPUS: return opus_packet(c, pcm, OPUS_FRAME);
    case NAG_CONVERT_AAC: return aac_encode(c, pcm, frames);
    default: return mp3_encode(c, pcm, frames);
    }
}

/* Whole frames out of the fifo into the encoder, [keep] frames left behind. */
static int encode_fifo(Conv *c, float *pcm, int keep)
{
    const int size = frame_size(c);
    while (av_audio_fifo_size(c->fifo) >= size + keep) {
        int got = av_audio_fifo_read(c->fifo, (void **)&pcm, size);
        if (got < 0)
            return failed(c, got, "out of memory");
        int ret = encode(c, pcm, got);
        c->encoded += got;
        if (ret < 0)
            return ret;
    }
    return 0;
}

/* Frames at the source's rate (doubles) into the fifo at the output's (floats); NULL flushes. */
static int push(Conv *c, const double *in, int frames, float *scratch, int scratch_frames)
{
    int out;
    if (c->swr) {
        const uint8_t *src[1] = {(const uint8_t *)in};
        uint8_t *dst[1] = {(uint8_t *)scratch};
        out = swr_convert(c->swr, dst, scratch_frames, in ? src : NULL, in ? frames : 0);
        if (out < 0)
            return failed(c, out, "resampling");
    } else {
        if (!in)
            return 0;
        for (int i = 0; i < frames * c->channels; i++)
            scratch[i] = (float)in[i];
        out = frames;
    }
    if (out > 0) {
        void *planes[1] = {scratch};
        int ret = av_audio_fifo_write(c->fifo, planes, out);
        if (ret < 0)
            return failed(c, ret, "out of memory");
        c->produced += out;
    }
    return 0;
}

static void conv_close(Conv *c)
{
    if (c->oc) {
        avformat_free_context(c->oc);
        c->oc = NULL;
    }
    if (c->io) {
        av_freep(&c->io->buffer);
        avio_context_free(&c->io);
    }
    av_packet_free(&c->packet);
    av_frame_free(&c->aac_frame);
    avcodec_free_context(&c->aac);
    if (c->opus)
        opus_encoder_destroy(c->opus);
    if (c->lame)
        lame_close(c->lame);
    free(c->mp3_buffer);
    swr_free(&c->swr);
    if (c->fifo)
        av_audio_fifo_free(c->fifo);
    nag_source_close(c->src);
}

/* The source's frames encoded; 0, or negative with the error set. */
static int run(Conv *c)
{
    const NagConvertOptions *opt = c->opt;
    int ret;
    const NagSourceInfo *info = nag_source_info(c->src);
    c->in_rate = info->rate;
    c->channels = nag_source_channels(c->src);
    switch (opt->codec) {
    case NAG_CONVERT_OPUS:
        c->rate = OPUS_RATE;
        break;
    case NAG_CONVERT_MP3:
        c->rate = pick_rate(opt->rate, c->in_rate, mp3_rates, sizeof(mp3_rates) / sizeof(mp3_rates[0]), 44100, 48000);
        break;
    case NAG_CONVERT_AAC:
        c->rate = pick_rate(opt->rate, c->in_rate, aac_rates, sizeof(aac_rates) / sizeof(aac_rates[0]), 44100, 48000);
        break;
    default:
        return failed(c, -1, "unknown codec");
    }
    if (c->rate <= 0)
        return failed(c, -1, "the codec has no such sample rate");
    if (c->rate != c->in_rate) {
        AVChannelLayout layout;
        av_channel_layout_default(&layout, c->channels);
        ret = swr_alloc_set_opts2(&c->swr, &layout, AV_SAMPLE_FMT_FLT, c->rate, &layout, AV_SAMPLE_FMT_DBL, c->in_rate, 0, NULL);
        av_channel_layout_uninit(&layout);
        if (ret < 0 || (ret = swr_init(c->swr)) < 0)
            return failed(c, ret, "resampler");
    }
    c->fifo = av_audio_fifo_alloc(AV_SAMPLE_FMT_FLT, c->channels, 1 << 15);
    if (!c->fifo)
        return failed(c, AVERROR(ENOMEM), "out of memory");
    switch (opt->codec) {
    case NAG_CONVERT_OPUS: ret = opus_open(c); break;
    case NAG_CONVERT_MP3: ret = mp3_open(c); break;
    default: ret = aac_open(c); break;
    }
    if (ret < 0)
        return ret;

    /* buffers: the source's doubles, the resampler's output, one encoder frame */
    const int scratch_frames = (int)av_rescale_rnd(READ_FRAMES, c->rate, c->in_rate, AV_ROUND_UP) + 256;
    double *in = malloc(sizeof(double) * READ_FRAMES * c->channels);
    float *scratch = malloc(sizeof(float) * (size_t)scratch_frames * c->channels);
    float *pcm = malloc(sizeof(float) * 2048 * c->channels);
    if (!in || !scratch || !pcm) {
        free(in);
        free(scratch);
        free(pcm);
        return failed(c, AVERROR(ENOMEM), "out of memory");
    }
    const int64_t expected = info->duration_ms > 0 ? av_rescale(info->duration_ms, c->in_rate, 1000) : 0;
    int64_t read = 0;
    for (;;) {
        int n = nag_source_read(c->src, in, READ_FRAMES);
        if (n < 0) {
            ret = failed(c, n, "decoding");
            break;
        }
        if (n == 0)
            break;
        /* a damaged float source's NaN or infinity becomes silence: no encoder sees it */
        for (int i = 0; i < n * c->channels; i++)
            if (!isfinite(in[i]))
                in[i] = 0.0;
        read += n;
        if ((ret = push(c, in, n, scratch, scratch_frames)) < 0 || (ret = encode_fifo(c, pcm, FIFO_RESERVE)) < 0)
            break;
        double fraction = expected > 0 ? (double)read / (double)expected : 0.0;
        if (c->progress && c->progress(c->opaque, 0.95 * (fraction > 1.0 ? 1.0 : fraction))) {
            ret = NAG_CONVERT_CANCELLED;
            failed(c, ret, "cancelled");
            break;
        }
    }
    if (ret >= 0) {
        if (read == 0) {
            ret = failed(c, -1, "no audio");
        } else if ((ret = push(c, NULL, 0, scratch, scratch_frames)) >= 0) {
            /* exactly as many frames as the source's at the output's rate: the resampler's excess cut, a shortfall padded */
            const int64_t target = av_rescale_rnd(read, c->rate, c->in_rate, AV_ROUND_NEAR_INF);
            const int64_t have = c->produced;
            if (have > target) {
                /* the fifo has no cut at its end: what stays is read out and written back */
                int64_t keep = av_audio_fifo_size(c->fifo) - (have - target);
                int stay = keep > 0 ? (int)keep : 0;
                float *rest = stay > 0 ? malloc(sizeof(float) * (size_t)stay * c->channels) : NULL;
                if (stay > 0 && !rest) {
                    ret = failed(c, AVERROR(ENOMEM), "out of memory");
                } else {
                    if (stay > 0)
                        av_audio_fifo_read(c->fifo, (void **)&rest, stay);
                    av_audio_fifo_reset(c->fifo);
                    if (stay > 0)
                        av_audio_fifo_write(c->fifo, (void **)&rest, stay);
                    free(rest);
                }
            } else if (have < target) {
                int pad = (int)(target - have);
                float *zeros = calloc((size_t)pad * c->channels, sizeof(float));
                if (!zeros)
                    ret = failed(c, AVERROR(ENOMEM), "out of memory");
                else
                    av_audio_fifo_write(c->fifo, (void **)&zeros, pad);
                free(zeros);
            }
        }
    }
    if (ret >= 0) {
        switch (opt->codec) {
        case NAG_CONVERT_OPUS:
            if ((ret = encode_fifo(c, pcm, 0)) >= 0)
                ret = opus_finish(c, pcm);
            break;
        case NAG_CONVERT_MP3:
            if ((ret = encode_fifo(c, pcm, 0)) >= 0) {
                int left = av_audio_fifo_size(c->fifo);
                if (left > 0) {
                    av_audio_fifo_read(c->fifo, (void **)&pcm, left);
                    ret = mp3_encode(c, pcm, left);
                    c->encoded += left;
                }
                if (ret >= 0)
                    ret = mp3_finish(c);
            }
            break;
        default:
            if ((ret = encode_fifo(c, pcm, 0)) >= 0) {
                int left = av_audio_fifo_size(c->fifo);
                if (left > 0) {
                    av_audio_fifo_read(c->fifo, (void **)&pcm, left);
                    ret = aac_encode(c, pcm, left);
                    c->encoded += left;
                }
                if (ret >= 0 && (ret = aac_encode(c, NULL, 0)) >= 0) {
                    ret = av_write_trailer(c->oc);
                    if (ret < 0)
                        failed(c, ret, "M4A trailer");
                }
            }
            break;
        }
    }
    if (ret >= 0 && c->io)
        avio_flush(c->io);
    free(in);
    free(scratch);
    free(pcm);
    return ret < 0 ? ret : 0;
}

int nag_convert(int in_fd, const char *in_name, int out_fd, const char *out_name, const NagConvertOptions *options,
                nag_convert_progress_fn progress, void *opaque, NagConvertResult *result, char *error, size_t error_size)
{
    Conv conv = {0};
    Conv *c = &conv;
    c->opt = options;
    c->out_fd = out_fd;
    c->out_name = out_name;
    c->error = error;
    c->error_size = error_size;
    c->progress = progress;
    c->opaque = opaque;
    if (error_size)
        error[0] = 0;
    if (result) {
        memset(result, 0, sizeof(*result));
        result->lufs = NAN;
        result->gain_db = NAN;
    }
    if (!options || in_fd < 0 || out_fd < 0)
        return failed(c, -1, "no file");
    /*
     * The output is a new, empty file, never one that holds anything (the source above all):
     * nothing here truncates. A seekable file, not a pipe.
     */
    struct stat in_st, out_st;
    if (fstat(in_fd, &in_st) != 0 || fstat(out_fd, &out_st) != 0)
        return failed(c, AVERROR(errno), "no file");
    if (same_file(in_fd, &in_st, out_fd, &out_st))
        return failed(c, -1, "the output is the source");
    if (out_st.st_size > 0)
        return failed(c, -1, "the output is not an empty file");
    if (lseek(out_fd, 0, SEEK_SET) < 0)
        return failed(c, AVERROR(errno), "the destination cannot be written as a file");

    char url[64];
    snprintf(url, sizeof(url), "/proc/self/fd/%d", in_fd);
    int ret = nag_source_open(&c->src, url, 0, error, error_size);
    if (ret < 0)
        return ret;
    /* more than two channels are mixed to stereo */
    if (nag_source_channels(c->src) > 2) {
        nag_source_close(c->src);
        c->src = NULL;
        if ((ret = nag_source_open(&c->src, url, 2, error, error_size)) < 0)
            return ret;
    }
    if (options->from > 0 || options->to >= 0) {
        const int rate = nag_source_info(c->src)->rate;
        int64_t start = av_rescale(options->from, rate, NAG_CUE_FRAMES_PER_SECOND);
        int64_t end = options->to >= 0 ? av_rescale(options->to, rate, NAG_CUE_FRAMES_PER_SECOND) : -1;
        if ((ret = nag_source_set_part(c->src, start, end)) < 0) {
            conv_close(c);
            return failed(c, ret, "seeking to the part");
        }
    }
    ret = run(c);
    const int rate = c->rate, channels = c->channels;
    const int64_t frames = c->encoded;
    conv_close(c);
    if (ret < 0)
        return ret;

    /* the ReplayGain: measured again on the output when the source has any */
    NagReplayGainTags gain = {NAN, NAN, NAN, NAN, NAN, NAN};
    NagReplayGainTags source = {NAN, NAN, NAN, NAN, NAN, NAN};
    NagLoudnessResult measured;
    double lufs = NAN;
    if (nag_tags_read_replaygain(in_fd, in_name, &source) > 0) {
        if (progress && progress(opaque, 0.96))
            return failed(c, NAG_CONVERT_CANCELLED, "cancelled");
        char out_url[64], scan_error[128];
        snprintf(out_url, sizeof(out_url), "/proc/self/fd/%d", out_fd);
        if (nag_loudness_scan_file(NULL, out_url, &measured, NULL, scan_error, sizeof(scan_error)) == 0 && measured.loudness_lufs > -HUGE_VAL) {
            lufs = measured.loudness_lufs;
            gain.track_gain_db = measured.gain_db;
            gain.track_peak = measured.true_peak;
            gain.track_lufs = lufs;
        }
        /* the album's gain as the source's: a lossy copy is as loud within a tenth of a dB */
        if (!isnan(source.album_gain_db)) {
            gain.album_gain_db = source.album_gain_db;
            gain.album_lufs = -18.0 - source.album_gain_db;
            double peak = isnan(source.album_peak) ? NAN : source.album_peak;
            if (!isnan(gain.track_peak) && (isnan(peak) || gain.track_peak > peak))
                peak = gain.track_peak;
            gain.album_peak = peak;
        }
    }
    if (progress && progress(opaque, 0.98))
        return failed(c, NAG_CONVERT_CANCELLED, "cancelled");
    int written = 0, cover = 0;
    char tag_error[256] = {0};
    if (nag_tags_copy(in_fd, in_name, out_fd, out_name, options->tags, &gain, &written, &cover, tag_error, sizeof(tag_error)) < 0)
        return failed(c, -1, tag_error);
    if (result) {
        result->frames = frames;
        result->rate = rate;
        result->channels = channels;
        off_t end = lseek(out_fd, 0, SEEK_END);
        result->bytes = end > 0 ? (int64_t)end : 0;
        result->tags = written;
        result->cover = cover;
        result->lufs = lufs;
        result->gain_db = gain.track_gain_db;
    }
    if (progress)
        progress(opaque, 1.0);
    return 0;
}

static void number(char *out, size_t size, double value)
{
    if (isnan(value))
        snprintf(out, size, "null");
    else
        snprintf(out, size, "%.3f", value);
}

void nag_convert_result_json(const NagConvertResult *r, char *out, size_t size)
{
    char lufs[32], gain[32];
    number(lufs, sizeof(lufs), r->lufs);
    number(gain, sizeof(gain), r->gain_db);
    snprintf(out, size, "{\"frames\":%lld,\"rate\":%d,\"channels\":%d,\"bytes\":%lld,\"tags\":%d,\"cover\":%s,\"lufs\":%s,\"gain_db\":%s}",
             (long long)r->frames, r->rate, r->channels, (long long)r->bytes, r->tags, r->cover ? "true" : "false", lufs, gain);
}
