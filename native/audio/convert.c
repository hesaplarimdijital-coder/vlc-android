/*
 * Nağmedar Ses Motoru: the converter (S-1010). A file, or a CUE sheet's part of it, read by the
 * engine's source (FFmpeg, libopenmpt; 64-bit float frames at the file's rate), resampled with
 * swresample where the codec asks for another rate, and encoded anew:
 *
 *   Opus  libopus, 20 ms packets into FFmpeg's Ogg muxer. The packets' timestamps start at
 *         minus the encoder's lookahead (the OpusHead's pre-skip), and the last ones are as
 *         long as the audio is, so the last granule position trims the padding (RFC 7845).
 *         More than two channels: libopus' surround encoder, mapping family 1 (Vorbis channel
 *         order, the coupled streams its table names).
 *   MP3   LAME writes the stream itself: its first frame is the Xing/LAME frame, filled in at
 *         the end with the encoder delay and the padding (what makes a decoder gapless).
 *   AAC   FFmpeg's own encoder into its M4A muxer (ipod), which writes the priming and the
 *         padding into the edit list. Up to 7.1, in the channel configurations MPEG-4 names
 *         (1 to 6, and 12 for 7.1).
 *
 * The channels stay as the source's up to 7.1 (a layout the codec has: what the source lacks
 * of it is silence), or go to stereo by ITU-R BS.775's matrix when asked for, and always for MP3.
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
#include <opus/opus_multistream.h>

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
#define OPUS_MAX_PACKET 8192
/* the most channels any output has (7.1) */
#define MAX_CH 8
/* a libopus request on the encoder in use: the single one (mono, stereo) or the surround one */
#define OPUS_CTL(c, request) ((c)->opus_ms ? opus_multistream_encoder_ctl((c)->opus_ms, request) : opus_encoder_ctl((c)->opus, request))
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
    int src_channels;   /* of the frames the source gives */
    int channels;       /* of the output */
    int mixing;         /* the frames are mixed by [matrix] before they are encoded */
    double matrix[MAX_CH * MAX_CH]; /* output x source gains (stride MAX_CH) */
    int lfe_channels;   /* LFE channels among the output's */
    uint64_t aac_mask;  /* the output's layout as FFmpeg's channel mask */
    SwrContext *swr;
    AVAudioFifo *fifo; /* float frames at the output's rate, interleaved */
    int64_t produced;  /* frames into the fifo */
    int64_t encoded;   /* frames out of the fifo into the encoder */

    /* Opus */
    OpusEncoder *opus;
    OpusMSEncoder *opus_ms; /* more than two channels */
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

/* ------------------------------------------------------------------ channels */

/* what a channel is for, whatever the layout calls it */
enum {
    ROLE_L, ROLE_R, ROLE_C, ROLE_LFE, ROLE_SL, ROLE_SR, ROLE_BL, ROLE_BR, ROLE_BC, ROLE_FLC, ROLE_FRC, ROLE_COUNT
};

static int role_of(int id)
{
    switch (id) {
    case AV_CHAN_FRONT_LEFT: return ROLE_L;
    case AV_CHAN_FRONT_RIGHT: return ROLE_R;
    case AV_CHAN_FRONT_CENTER: return ROLE_C;
    case AV_CHAN_LOW_FREQUENCY: return ROLE_LFE;
    case AV_CHAN_SIDE_LEFT: return ROLE_SL;
    case AV_CHAN_SIDE_RIGHT: return ROLE_SR;
    case AV_CHAN_BACK_LEFT: return ROLE_BL;
    case AV_CHAN_BACK_RIGHT: return ROLE_BR;
    case AV_CHAN_BACK_CENTER: return ROLE_BC;
    case AV_CHAN_FRONT_LEFT_OF_CENTER: return ROLE_FLC;
    case AV_CHAN_FRONT_RIGHT_OF_CENTER: return ROLE_FRC;
    default: return -1;
    }
}

/* A layout a codec writes: its channels in the order the codec takes them. */
typedef struct Layout {
    int count;
    signed char roles[MAX_CH];
    uint64_t mask; /* FFmpeg's mask of the same channels (the AAC encoder's layouts) */
} Layout;

/*
 * Opus' mapping family 1 (RFC 7845 section 5.1.1.2): Vorbis' channel order. Mono, stereo, 3.0,
 * quad, 5.0, 5.1, 6.1 and 7.1; the "rear" channels of the smaller ones are the surrounds.
 */
static const Layout opus_layouts[] = {
    {1, {ROLE_C}, AV_CH_LAYOUT_MONO},
    {2, {ROLE_L, ROLE_R}, AV_CH_LAYOUT_STEREO},
    {3, {ROLE_L, ROLE_C, ROLE_R}, 0},
    {4, {ROLE_L, ROLE_R, ROLE_BL, ROLE_BR}, 0},
    {5, {ROLE_L, ROLE_C, ROLE_R, ROLE_BL, ROLE_BR}, 0},
    {6, {ROLE_L, ROLE_C, ROLE_R, ROLE_BL, ROLE_BR, ROLE_LFE}, 0},
    {7, {ROLE_L, ROLE_C, ROLE_R, ROLE_SL, ROLE_SR, ROLE_BC, ROLE_LFE}, 0},
    {8, {ROLE_L, ROLE_C, ROLE_R, ROLE_SL, ROLE_SR, ROLE_BL, ROLE_BR, ROLE_LFE}, 0},
};

/*
 * The layouts of FFmpeg's AAC encoder that MPEG-4's channel configurations 1 to 6 and 12 name (the
 * others need a program config element, which fewer players read), in FFmpeg's channel order:
 * mono, stereo, 3.0, 4.0 (a back centre), 5.0, 5.1 and 7.1.
 */
static const Layout aac_layouts[] = {
    {1, {ROLE_C}, AV_CH_LAYOUT_MONO},
    {2, {ROLE_L, ROLE_R}, AV_CH_LAYOUT_STEREO},
    {3, {ROLE_L, ROLE_R, ROLE_C}, AV_CH_LAYOUT_SURROUND},
    {4, {ROLE_L, ROLE_R, ROLE_C, ROLE_BC}, AV_CH_LAYOUT_4POINT0},
    {5, {ROLE_L, ROLE_R, ROLE_C, ROLE_BL, ROLE_BR}, AV_CH_LAYOUT_5POINT0_BACK},
    {6, {ROLE_L, ROLE_R, ROLE_C, ROLE_LFE, ROLE_BL, ROLE_BR}, AV_CH_LAYOUT_5POINT1_BACK},
    {8, {ROLE_L, ROLE_R, ROLE_C, ROLE_LFE, ROLE_BL, ROLE_BR, ROLE_SL, ROLE_SR}, AV_CH_LAYOUT_7POINT1},
};

typedef struct ChannelPlan {
    int channels;                   /* of the output */
    double matrix[MAX_CH * MAX_CH]; /* [output * MAX_CH + source] */
    int lfe;                        /* LFE channels among the output's */
    uint64_t mask;                  /* the layout's FFmpeg mask, 0 where the codec has no use for one */
    int identity;                   /* the output is the source as it is */
} ChannelPlan;

static int is_left(int role) { return role == ROLE_SL || role == ROLE_BL || role == ROLE_FLC; }
static int is_right(int role) { return role == ROLE_SR || role == ROLE_BR || role == ROLE_FRC; }

/*
 * The source's channels (by role) into [layout]: each to the slot of its own role, a surround to
 * the other kind of surround where the layout has the one only (side and back are the same
 * speakers to the listener), and, with [fold], a back centre into the left and right surrounds
 * at -3 dB. Slots without a channel stay silent. 1 when every channel found a place.
 */
static int fit_layout(const Layout *layout, const int *roles, int n, int fold, ChannelPlan *plan)
{
    int slot[MAX_CH], used[MAX_CH] = {0};
    memset(plan->matrix, 0, sizeof(plan->matrix));
    for (int i = 0; i < n; i++) {
        slot[i] = -1;
        for (int s = 0; s < layout->count; s++) {
            if (!used[s] && layout->roles[s] == roles[i]) {
                slot[i] = s;
                used[s] = 1;
                break;
            }
        }
    }
    for (int i = 0; i < n; i++) {
        if (slot[i] >= 0 || (!is_left(roles[i]) && !is_right(roles[i])))
            continue;
        const int left = is_left(roles[i]);
        for (int s = 0; s < layout->count; s++) {
            const int role = layout->roles[s];
            if (!used[s] && (left ? (role == ROLE_SL || role == ROLE_BL) : (role == ROLE_SR || role == ROLE_BR))) {
                slot[i] = s;
                used[s] = 1;
                break;
            }
        }
    }
    for (int i = 0; i < n; i++) {
        if (slot[i] >= 0) {
            plan->matrix[slot[i] * MAX_CH + i] = 1.0;
            continue;
        }
        if (roles[i] != ROLE_BC || !fold)
            return 0;
        int l = -1, r = -1;
        for (int s = 0; s < layout->count; s++) {
            const int role = layout->roles[s];
            if (!used[s] && l < 0 && (role == ROLE_SL || role == ROLE_BL))
                l = s;
            if (!used[s] && r < 0 && (role == ROLE_SR || role == ROLE_BR))
                r = s;
        }
        if (l < 0 || r < 0)
            return 0;
        used[l] = used[r] = 1;
        plan->matrix[l * MAX_CH + i] = plan->matrix[r * MAX_CH + i] = M_SQRT1_2;
    }
    plan->channels = layout->count;
    plan->mask = layout->mask;
    plan->lfe = 0;
    for (int s = 0; s < layout->count; s++)
        plan->lfe += layout->roles[s] == ROLE_LFE;
    return 1;
}

/*
 * ITU-R BS.775's downmix to stereo: the centre and the surrounds at -3 dB into both sides or
 * their own, the back centre at -6 dB into both, the LFE left out (the recommendation has no
 * place for it). Scaled down together when the louder side could pass full scale, so that no
 * downmix clips.
 */
static void fit_stereo(const int *roles, int n, ChannelPlan *plan)
{
    static const double left[ROLE_COUNT] = {1, 0, M_SQRT1_2, 0, M_SQRT1_2, 0, M_SQRT1_2, 0, 0.5, 1, 0};
    static const double right[ROLE_COUNT] = {0, 1, M_SQRT1_2, 0, 0, M_SQRT1_2, 0, M_SQRT1_2, 0.5, 0, 1};
    memset(plan->matrix, 0, sizeof(plan->matrix));
    double sum_left = 0, sum_right = 0;
    for (int i = 0; i < n; i++) {
        plan->matrix[0 * MAX_CH + i] = left[roles[i]];
        plan->matrix[1 * MAX_CH + i] = right[roles[i]];
        sum_left += left[roles[i]];
        sum_right += right[roles[i]];
    }
    const double peak = sum_left > sum_right ? sum_left : sum_right;
    if (peak > 1.0) {
        for (int i = 0; i < n; i++) {
            plan->matrix[0 * MAX_CH + i] /= peak;
            plan->matrix[1 * MAX_CH + i] /= peak;
        }
    }
    plan->channels = 2;
    plan->mask = AV_CH_LAYOUT_STEREO;
    plan->lfe = 0;
}

/* Whether the source's channels are ones the planner places (else FFmpeg mixes the source down to stereo). */
static int channels_known(const int *ids, int n)
{
    if (n < 1 || n > MAX_CH)
        return 0;
    if (n == 1)
        return 1;
    for (int i = 0; i < n; i++)
        if (role_of(ids[i]) < 0)
            return 0;
    return 1;
}

/*
 * The ids of the source's [count] channels as the planner takes them: a pair it has no roles for
 * (the matrix-encoded Lt/Rt pair a MOV's or M4A's channel layout calls a "downmix", say) is the
 * stereo pair every player plays it as, left and right.
 */
static void source_ids(const NagSource *src, int count, int *ids)
{
    nag_source_channel_ids(src, ids, MAX_CH);
    if (count == 2 && !channels_known(ids, count)) {
        ids[0] = AV_CHAN_FRONT_LEFT;
        ids[1] = AV_CHAN_FRONT_RIGHT;
    }
}

/*
 * The output's channels from the source's [ids]: mono stays mono; stereo when asked for ([stereo])
 * and always for MP3, by BS.775's matrix; otherwise the source's own layout in the codec's (the
 * smallest the codec has that holds every channel, silence for what the source lacks of it), and
 * the stereo downmix for what none holds. 0, or -1 when a channel has no known role.
 */
static int plan_channels(NagConvertCodec codec, int stereo, const int *ids, int n, ChannelPlan *plan)
{
    int roles[MAX_CH];
    memset(plan, 0, sizeof(*plan));
    if (!channels_known(ids, n))
        return -1;
    if (n == 1) {
        plan->channels = 1;
        plan->matrix[0] = 1.0;
        plan->mask = AV_CH_LAYOUT_MONO;
        plan->identity = 1;
        return 0;
    }
    for (int i = 0; i < n; i++)
        roles[i] = role_of(ids[i]);
    int found = 0;
    if (!stereo && codec != NAG_CONVERT_MP3) {
        const Layout *table = codec == NAG_CONVERT_OPUS ? opus_layouts : aac_layouts;
        const int count = codec == NAG_CONVERT_OPUS ? (int)(sizeof(opus_layouts) / sizeof(opus_layouts[0]))
                                                    : (int)(sizeof(aac_layouts) / sizeof(aac_layouts[0]));
        for (int fold = 0; fold <= 1 && !found; fold++)
            for (int l = 0; l < count && !found; l++)
                found = fit_layout(&table[l], roles, n, fold, plan);
    }
    if (!found)
        fit_stereo(roles, n, plan);
    plan->identity = plan->channels == n;
    for (int o = 0; o < plan->channels && plan->identity; o++)
        for (int i = 0; i < n; i++)
            if (plan->matrix[o * MAX_CH + i] != (o == i ? 1.0 : 0.0))
                plan->identity = 0;
    return 0;
}

/*
 * The bit rate for [channels]: [base] is the one asked for a stereo pair. Each full-range channel
 * past the pair adds 0.6 of the share a pair's channel has (surrounds carry less of their own),
 * the LFE 0.15: 5.1 gets about twice the pair's rate, 7.1 two and a half times. Mono and stereo
 * as asked.
 */
static int scaled_bitrate(int base, int channels, int lfe)
{
    if (channels <= 2)
        return base;
    const double pairs = (2.0 + 0.6 * (channels - lfe - 2) + 0.15 * lfe) / 2.0;
    const double rate = base * pairs;
    return rate > (double)INT_MAX ? INT_MAX : (int)rate;
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
    int streams = 1, coupled = c->channels == 2;
    unsigned char mapping[MAX_CH] = {0, 1};
    const int bitrate = scaled_bitrate(c->opt->bitrate > 0 ? c->opt->bitrate : 128000, c->channels, c->lfe_channels);
    if (c->channels <= 2) {
        c->opus = opus_encoder_create(OPUS_RATE, c->channels, OPUS_APPLICATION_AUDIO, &err);
        if (err != OPUS_OK || !c->opus) {
            snprintf(c->error, c->error_size, "libopus: %s", opus_strerror(err));
            return -1;
        }
    } else {
        /* mapping family 1: the surround encoder knows the streams, the coupled pairs and the mapping RFC 7845 names */
        c->opus_ms = opus_multistream_surround_encoder_create(OPUS_RATE, c->channels, 1, &streams, &coupled, mapping,
                                                              OPUS_APPLICATION_AUDIO, &err);
        if (err != OPUS_OK || !c->opus_ms) {
            snprintf(c->error, c->error_size, "libopus: %s", opus_strerror(err));
            return -1;
        }
    }
    OPUS_CTL(c, OPUS_SET_BITRATE(bitrate));
    OPUS_CTL(c, OPUS_SET_VBR(1));
    OPUS_CTL(c, OPUS_SET_VBR_CONSTRAINT(0));
    OPUS_CTL(c, OPUS_SET_COMPLEXITY(10));
    OPUS_CTL(c, OPUS_SET_SIGNAL(OPUS_SIGNAL_MUSIC));
    opus_int32 lookahead = 0;
    OPUS_CTL(c, OPUS_GET_LOOKAHEAD(&lookahead));
    c->pre_skip = (int)lookahead;

    AVStream *st;
    int ret = open_muxer(c, "ogg", &st);
    if (ret < 0)
        return ret;
    /* RFC 7845's identification header: mapping family 0 (mono or stereo), else family 1 with its channel mapping table */
    const int head_size = c->channels <= 2 ? 19 : 21 + c->channels;
    uint8_t *head = av_mallocz(head_size + AV_INPUT_BUFFER_PADDING_SIZE);
    if (!head)
        return failed(c, AVERROR(ENOMEM), "out of memory");
    memcpy(head, "OpusHead", 8);
    head[8] = 1;
    head[9] = (uint8_t)c->channels;
    AV_WL16(head + 10, c->pre_skip);
    AV_WL32(head + 12, c->in_rate);
    AV_WL16(head + 16, 0);
    head[18] = c->channels <= 2 ? 0 : 1;
    if (c->channels > 2) {
        head[19] = (uint8_t)streams;
        head[20] = (uint8_t)coupled;
        memcpy(head + 21, mapping, (size_t)c->channels);
    }
    st->codecpar->codec_type = AVMEDIA_TYPE_AUDIO;
    st->codecpar->codec_id = AV_CODEC_ID_OPUS;
    st->codecpar->sample_rate = OPUS_RATE;
    av_channel_layout_default(&st->codecpar->ch_layout, c->channels);
    st->codecpar->extradata = head;
    st->codecpar->extradata_size = head_size;
    st->codecpar->initial_padding = c->pre_skip;
    st->codecpar->bit_rate = bitrate;
    st->time_base = (AVRational){1, OPUS_RATE};
    if ((ret = avformat_write_header(c->oc, NULL)) < 0)
        return failed(c, ret, "Ogg header");
    return 0;
}

/* One packet of [frames] frames (960, zero-padded at the end), lasting [duration] of them. */
static int opus_packet(Conv *c, const float *pcm, int duration)
{
    uint8_t data[OPUS_MAX_PACKET];
    opus_int32 size = c->opus_ms ? opus_multistream_encode_float(c->opus_ms, pcm, OPUS_FRAME, data, sizeof(data))
                                 : opus_encode_float(c->opus, pcm, OPUS_FRAME, data, sizeof(data));
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
    /* one of the encoder's own layouts (MPEG-4's channel configurations), else it asks for a program config element */
    int ret = av_channel_layout_from_mask(&c->aac->ch_layout, c->aac_mask);
    if (ret < 0)
        return failed(c, ret, "AAC channel layout");
    c->aac->bit_rate = scaled_bitrate(c->opt->bitrate > 0 ? c->opt->bitrate : 256000, c->channels, c->lfe_channels);
    c->aac->time_base = (AVRational){1, c->rate};
    c->aac->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    ret = avcodec_open2(c->aac, codec, NULL);
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
    /*
     * 7.1: FFmpeg's encoder writes the elements of MPEG-4's configuration 12 (centre, front pair,
     * side pair, rear pair, LFE) under 7, which a decoder that follows the standard reads as 7.1
     * wide (the front pair as centre-left and centre-right). The audio specific config's channel
     * configuration (4 bits after the object type and the rate index) is made 12.
     */
    if (c->channels == 8 && st->codecpar->extradata_size >= 2)
        st->codecpar->extradata[1] = (uint8_t)((st->codecpar->extradata[1] & 0x87) | (12 << 3));
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

/* [frames] source frames into the output's channels by the plan's matrix. */
static void mix(const Conv *c, const double *in, double *out, int frames)
{
    for (int f = 0; f < frames; f++) {
        const double *src = in + (size_t)f * c->src_channels;
        double *dst = out + (size_t)f * c->channels;
        for (int o = 0; o < c->channels; o++) {
            const double *row = c->matrix + o * MAX_CH;
            double sum = 0.0;
            for (int i = 0; i < c->src_channels; i++)
                sum += row[i] * src[i];
            dst[o] = sum;
        }
    }
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
    if (c->opus_ms)
        opus_multistream_encoder_destroy(c->opus_ms);
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
    c->src_channels = nag_source_channels(c->src);
    {
        int ids[MAX_CH] = {0};
        ChannelPlan plan;
        source_ids(c->src, c->src_channels, ids);
        if (plan_channels(opt->codec, opt->channels == NAG_CONVERT_STEREO, ids, c->src_channels, &plan) < 0)
            return failed(c, -1, "channels the converter does not place");
        c->channels = plan.channels;
        c->lfe_channels = plan.lfe;
        c->aac_mask = plan.mask;
        c->mixing = !plan.identity;
        memcpy(c->matrix, plan.matrix, sizeof(c->matrix));
    }
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
        AVChannelLayout layout = {0};
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
    double *in = malloc(sizeof(double) * READ_FRAMES * c->src_channels);
    double *mixed = c->mixing ? malloc(sizeof(double) * READ_FRAMES * c->channels) : NULL;
    float *scratch = malloc(sizeof(float) * (size_t)scratch_frames * c->channels);
    float *pcm = malloc(sizeof(float) * 2048 * c->channels);
    if (!in || (c->mixing && !mixed) || !scratch || !pcm) {
        free(in);
        free(mixed);
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
        for (int i = 0; i < n * c->src_channels; i++)
            if (!isfinite(in[i]))
                in[i] = 0.0;
        read += n;
        if (c->mixing)
            mix(c, in, mixed, n);
        if ((ret = push(c, c->mixing ? mixed : in, n, scratch, scratch_frames)) < 0 || (ret = encode_fifo(c, pcm, FIFO_RESERVE)) < 0)
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
    free(mixed);
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
    /* the channels as the file has them; a layout the planner cannot place (heights, more than eight) is mixed to stereo by FFmpeg */
    int ids[MAX_CH] = {0};
    const int count = nag_source_channels(c->src);
    if (count > MAX_CH || (source_ids(c->src, count, ids), !channels_known(ids, count))) {
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
