// S-1202: a file's bitrate over time, from its packets.
//
// libavformat demuxes the whole file (or its first seconds) without decoding; every packet's
// bytes go to the second its timestamp falls in, for all streams together and for the video
// alone. From the seconds come the mean, the highest second and the highest five seconds,
// which tell whether a USB 2.0 port or a network can feed the player.
#include "bitrate.h"

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/bprint.h>
#include <libavutil/mathematics.h>

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

/* a film is hours, not days: the seconds counted at most */
#define MAX_SECONDS (24 * 3600)
#define PEAK_WINDOW 5

typedef struct Seconds {
    uint64_t *bytes;
    int count;      /* seconds in use: the last with bytes + 1 */
    int capacity;
} Seconds;

typedef struct StreamSum {
    uint64_t bytes;
    uint64_t second_bytes; /* the second being counted */
    int second;
    uint64_t peak_second_bytes;
} StreamSum;

static int grow(Seconds *s, int second)
{
    if (second < s->capacity)
        return 0;
    int capacity = s->capacity ? s->capacity : 1024;
    while (capacity <= second)
        capacity *= 2;
    uint64_t *bytes = realloc(s->bytes, capacity * sizeof(*bytes));
    if (!bytes)
        return -1;
    memset(bytes + s->capacity, 0, (capacity - s->capacity) * sizeof(*bytes));
    s->bytes = bytes;
    s->capacity = capacity;
    return 0;
}

static int add(Seconds *s, int second, int size)
{
    if (grow(s, second) < 0)
        return -1;
    s->bytes[second] += (uint64_t)size;
    if (second >= s->count)
        s->count = second + 1;
    return 0;
}

static void stream_add(StreamSum *st, int second, int size)
{
    st->bytes += (uint64_t)size;
    if (second != st->second) {
        if (st->second_bytes > st->peak_second_bytes)
            st->peak_second_bytes = st->second_bytes;
        st->second_bytes = 0;
        st->second = second;
    }
    st->second_bytes += (uint64_t)size;
}

static void stream_finish(StreamSum *st)
{
    if (st->second_bytes > st->peak_second_bytes)
        st->peak_second_bytes = st->second_bytes;
}

static long long kbps(uint64_t bytes, double seconds)
{
    if (seconds <= 0)
        return 0;
    return (long long)(bytes * 8.0 / 1000.0 / seconds + 0.5);
}

static long long peak_window(const Seconds *s, int window)
{
    if (s->count <= 0)
        return 0;
    if (window > s->count)
        window = s->count;
    uint64_t sum = 0, best = 0;
    for (int i = 0; i < s->count; i++) {
        sum += s->bytes[i];
        if (i >= window)
            sum -= s->bytes[i - window];
        if (i >= window - 1 && sum > best)
            best = sum;
    }
    return kbps(best, window);
}

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

static void put_bool(AVBPrint *bp, int *first, const char *name, int value)
{
    key(bp, first, name);
    av_bprintf(bp, "%s", value ? "true" : "false");
}

static void put_seconds(AVBPrint *bp, int *first, const char *name, const Seconds *s)
{
    key(bp, first, name);
    av_bprint_chars(bp, '[', 1);
    for (int i = 0; i < s->count; i++)
        av_bprintf(bp, "%s%lld", i ? "," : "", kbps(s->bytes[i], 1));
    av_bprint_chars(bp, ']', 1);
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
    put_int(&bp, &first, "version", BITRATE_VERSION);
    put_text(&bp, &first, "error", text);
    av_bprint_chars(&bp, '}', 1);
    return finish(&bp);
}

static const char *type_name(enum AVMediaType type)
{
    switch (type) {
    case AVMEDIA_TYPE_VIDEO: return "video";
    case AVMEDIA_TYPE_AUDIO: return "audio";
    case AVMEDIA_TYPE_SUBTITLE: return "subtitle";
    case AVMEDIA_TYPE_ATTACHMENT: return "attachment";
    default: return "other";
    }
}

/* The second a packet falls in, from its timestamp; the stream's last when it has none. */
static int second_of(const AVStream *st, const AVPacket *pkt, int last)
{
    int64_t ts = pkt->dts != AV_NOPTS_VALUE ? pkt->dts : pkt->pts;
    if (ts == AV_NOPTS_VALUE)
        return last;
    if (st->start_time != AV_NOPTS_VALUE)
        ts -= st->start_time;
    int64_t seconds = av_rescale_q(ts, st->time_base, (AVRational){1, 1});
    if (seconds < 0)
        return 0;
    if (seconds >= MAX_SECONDS)
        return MAX_SECONDS - 1;
    return (int)seconds;
}

char *media_bitrate(const char *input, double max_seconds, bitrate_progress progress, void *opaque, volatile int *cancel)
{
    AVFormatContext *ic = NULL;
    AVDictionary *opts = NULL;
    int ret;
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

    /* the streams known once the file is open, as the probe lists them; a stream the demuxer adds while reading
     * (a TS whose PMT names one more) is not counted */
    const unsigned streams = ic->nb_streams;
    int video = -1;
    for (unsigned i = 0; i < streams && video < 0; i++) {
        const AVStream *st = ic->streams[i];
        if (st->codecpar->codec_type == AVMEDIA_TYPE_VIDEO && !(st->disposition & AV_DISPOSITION_ATTACHED_PIC))
            video = (int)i;
    }

    Seconds total = {0}, video_seconds = {0};
    StreamSum *sums = calloc(streams ? streams : 1, sizeof(*sums));
    int *last = calloc(streams ? streams : 1, sizeof(*last));
    AVPacket *pkt = av_packet_alloc();
    int complete = 1, failed = sums == NULL || last == NULL || pkt == NULL;
    int64_t size = ic->pb ? avio_size(ic->pb) : 0;
    double reported = -1;
    int limit = max_seconds > 0 ? (int)(max_seconds + 0.5) : 0;

    while (!failed) {
        if (cancel && *cancel) {
            complete = 0;
            break;
        }
        if ((ret = av_read_frame(ic, pkt)) < 0) {
            if (ret != AVERROR_EOF)
                complete = 0;
            break;
        }
        unsigned index = (unsigned)pkt->stream_index;
        if (index < streams && pkt->size > 0) {
            const AVStream *st = ic->streams[index];
            int second = second_of(st, pkt, last[index]);
            last[index] = second;
            if (limit > 0 && second >= limit) {
                /* past the seconds asked for: a second more is read, so that every stream's packets of them are in */
                if (second > limit) {
                    av_packet_unref(pkt);
                    break;
                }
                av_packet_unref(pkt);
                continue;
            }
            if (add(&total, second, pkt->size) < 0)
                failed = 1;
            if ((int)index == video && add(&video_seconds, second, pkt->size) < 0)
                failed = 1;
            stream_add(&sums[index], second, pkt->size);
        }
        av_packet_unref(pkt);
        if (progress && size > 0 && ic->pb) {
            double fraction = (double)avio_tell(ic->pb) / (double)size;
            if (fraction - reported >= 0.005) {
                reported = fraction;
                if (progress(opaque, fraction < 1 ? fraction : 1)) {
                    complete = 0;
                    break;
                }
            }
        }
    }
    av_packet_free(&pkt);
    if (failed) {
        free(sums);
        free(last);
        free(total.bytes);
        free(video_seconds.bytes);
        avformat_close_input(&ic);
        return error_json("memory", AVERROR(ENOMEM));
    }
    for (unsigned i = 0; i < streams; i++)
        stream_finish(&sums[i]);

    uint64_t all_bytes = 0, video_bytes = 0;
    for (int i = 0; i < total.count; i++)
        all_bytes += total.bytes[i];
    for (int i = 0; i < video_seconds.count; i++)
        video_bytes += video_seconds.bytes[i];
    uint64_t peak_second = 0, video_peak_second = 0;
    for (int i = 0; i < total.count; i++)
        if (total.bytes[i] > peak_second)
            peak_second = total.bytes[i];
    for (int i = 0; i < video_seconds.count; i++)
        if (video_seconds.bytes[i] > video_peak_second)
            video_peak_second = video_seconds.bytes[i];

    AVBPrint bp;
    av_bprint_init(&bp, 0, AV_BPRINT_SIZE_UNLIMITED);
    int first = 1;
    av_bprint_chars(&bp, '{', 1);
    put_int(&bp, &first, "version", BITRATE_VERSION);
    put_text(&bp, &first, "error", "");
    put_bool(&bp, &first, "complete", complete);
    put_int(&bp, &first, "duration_ms", ic->duration != AV_NOPTS_VALUE ? ic->duration / 1000 : 0);
    put_int(&bp, &first, "size", size);
    put_int(&bp, &first, "seconds", total.count);
    put_seconds(&bp, &first, "total", &total);
    put_seconds(&bp, &first, "video", &video_seconds);
    put_int(&bp, &first, "average_kbps", kbps(all_bytes, total.count));
    put_int(&bp, &first, "peak_1s_kbps", kbps(peak_second, 1));
    put_int(&bp, &first, "peak_5s_kbps", peak_window(&total, PEAK_WINDOW));
    put_int(&bp, &first, "video_average_kbps", kbps(video_bytes, video_seconds.count));
    put_int(&bp, &first, "video_peak_1s_kbps", kbps(video_peak_second, 1));
    put_int(&bp, &first, "video_peak_5s_kbps", peak_window(&video_seconds, PEAK_WINDOW));
    key(&bp, &first, "streams");
    av_bprint_chars(&bp, '[', 1);
    for (unsigned i = 0; i < streams; i++) {
        const AVStream *st = ic->streams[i];
        const AVCodecDescriptor *codec = avcodec_descriptor_get(st->codecpar->codec_id);
        int sfirst = 1;
        if (i)
            av_bprint_chars(&bp, ',', 1);
        av_bprint_chars(&bp, '{', 1);
        put_int(&bp, &sfirst, "index", i);
        put_text(&bp, &sfirst, "type", type_name(st->codecpar->codec_type));
        put_text(&bp, &sfirst, "codec", codec ? codec->name : "");
        put_int(&bp, &sfirst, "bytes", (long long)sums[i].bytes);
        put_int(&bp, &sfirst, "average_kbps", kbps(sums[i].bytes, total.count));
        put_int(&bp, &sfirst, "peak_1s_kbps", kbps(sums[i].peak_second_bytes, 1));
        av_bprint_chars(&bp, '}', 1);
    }
    av_bprint_chars(&bp, ']', 1);
    av_bprint_chars(&bp, '}', 1);

    free(sums);
    free(last);
    free(total.bytes);
    free(video_seconds.bytes);
    avformat_close_input(&ic);
    return finish(&bp);
}
