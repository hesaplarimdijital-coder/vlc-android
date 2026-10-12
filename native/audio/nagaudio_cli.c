// nagaudio: the engine from the command line, for the host tests (S-901, S-1405) and for a look
// at a file.
//
//   nagaudio info FILE                      the source as the engine reads it, JSON
//   nagaudio decode FILE [FILE2 ...] [-o OUT] [-f s16|s24|s32|f32|f64] [--seek MS]
//                                           [--seek-after FRAMES] [--volume V] [--no-dither] [--md5]
//           plays FILE (then FILE2 ... gapless, as a queue) through the engine's render into
//           OUT (raw samples; "-" for stdout; none: nothing written) and prints a JSON summary
//           with the events seen and, with --md5, the MD5 of the samples (what ffmpeg -f md5
//           prints for the same raw format). --seek MS starts at MS; with --seek-after N the
//           start is 0 and the seek comes while playing, after N frames rendered.
#include "nagaudio.h"
#include "source.h"

#include <libavutil/md5.h>
#include <libavutil/mem.h>

#include <math.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

typedef struct Seen {
    pthread_mutex_t lock;
    char log[8192];
    int ended;
    int errors;
    int tracks;
    int64_t last_track;
} Seen;

static void on_event(void *opaque, const NagEvent *ev)
{
    Seen *s = opaque;
    if (!ev)
        return; /* the event thread is ending */
    pthread_mutex_lock(&s->lock);
    size_t len = strlen(s->log);
    const char *name = "?";
    switch (ev->type) {
    case NAG_EVENT_STATE: name = "state"; break;
    case NAG_EVENT_TRACK: name = "track"; s->tracks++; s->last_track = ev->arg; break;
    case NAG_EVENT_TRACK_ENDED: name = "track_ended"; break;
    case NAG_EVENT_FORMAT: name = "format"; break;
    case NAG_EVENT_ERROR: name = "error"; s->errors++; break;
    case NAG_EVENT_DEVICE_LOST: name = "device_lost"; break;
    case NAG_EVENT_QUEUE_EMPTY: name = "queue_empty"; s->ended = 1; break;
    }
    if (len + 200 < sizeof(s->log))
        snprintf(s->log + len, sizeof(s->log) - len, "%s%s:%lld%s%s", len ? " " : "", name, (long long)ev->arg, ev->text[0] ? ":" : "", ev->text);
    if (ev->type == NAG_EVENT_ERROR)
        fprintf(stderr, "nagaudio: error on track %lld: %s\n", (long long)ev->arg, ev->text);
    pthread_mutex_unlock(&s->lock);
}

static void print_info(const NagSourceInfo *i)
{
    printf("{\"codec\":\"%s\",\"container\":\"%s\",\"rate\":%d,\"channels\":%d,\"bits\":%d,\"float\":%s,\"duration_ms\":%lld,\"bitrate\":%lld",
           i->codec, i->container, i->rate, i->channels, i->bits, i->is_float ? "true" : "false", (long long)i->duration_ms,
           (long long)i->bitrate);
    if (!isnan(i->track_gain_db))
        printf(",\"track_gain_db\":%.2f", i->track_gain_db);
    if (!isnan(i->track_peak))
        printf(",\"track_peak\":%.6f", i->track_peak);
    if (!isnan(i->album_gain_db))
        printf(",\"album_gain_db\":%.2f", i->album_gain_db);
    if (!isnan(i->album_peak))
        printf(",\"album_peak\":%.6f", i->album_peak);
    printf("}\n");
}

static int info(const char *path)
{
    NagSource *src = NULL;
    char error[256] = {0};
    if (nag_source_open(&src, path, 0, error, sizeof(error)) < 0) {
        fprintf(stderr, "nagaudio: %s: %s\n", path, error);
        return 1;
    }
    print_info(nag_source_info(src));
    nag_source_close(src);
    return 0;
}

static int format_same(const NagFormat *a, const NagFormat *b)
{
    return a->rate == b->rate && a->channels == b->channels && a->sample_format == b->sample_format;
}

static NagSampleFormat parse_format(const char *name)
{
    if (strcmp(name, "s16") == 0) return NAG_FMT_S16;
    if (strcmp(name, "s24") == 0) return NAG_FMT_S24_3;
    if (strcmp(name, "s32") == 0) return NAG_FMT_S32;
    if (strcmp(name, "f32") == 0) return NAG_FMT_F32;
    if (strcmp(name, "f64") == 0) return NAG_FMT_F64;
    return NAG_FMT_NONE;
}

static int decode(int argc, char **argv)
{
    const char *files[64];
    int n_files = 0;
    const char *out_path = NULL;
    NagSampleFormat format = NAG_FMT_NONE;
    int64_t seek_ms = -1, seek_after = -1;
    double volume = 1.0;
    int dither = 1, md5 = 0;
    for (int i = 0; i < argc; i++) {
        if (strcmp(argv[i], "-o") == 0 && i + 1 < argc) out_path = argv[++i];
        else if (strcmp(argv[i], "-f") == 0 && i + 1 < argc) {
            format = parse_format(argv[++i]);
            if (format == NAG_FMT_NONE) {
                fprintf(stderr, "nagaudio: unknown format %s\n", argv[i]);
                return 2;
            }
        } else if (strcmp(argv[i], "--seek") == 0 && i + 1 < argc) seek_ms = atoll(argv[++i]);
        else if (strcmp(argv[i], "--seek-after") == 0 && i + 1 < argc) seek_after = atoll(argv[++i]);
        else if (strcmp(argv[i], "--volume") == 0 && i + 1 < argc) volume = atof(argv[++i]);
        else if (strcmp(argv[i], "--no-dither") == 0) dither = 0;
        else if (strcmp(argv[i], "--md5") == 0) md5 = 1;
        else if (argv[i][0] == '-' && argv[i][1]) {
            fprintf(stderr, "nagaudio: unknown option %s\n", argv[i]);
            return 2;
        } else if (n_files < 64) files[n_files++] = argv[i];
    }
    if (n_files == 0) {
        fprintf(stderr, "nagaudio decode: a file is needed\n");
        return 2;
    }
    Seen seen = {PTHREAD_MUTEX_INITIALIZER, "", 0, 0, 0, -1};
    NagConfig cfg;
    nag_config_defaults(&cfg);
    cfg.dither = dither;
    if (format != NAG_FMT_NONE)
        cfg.fixed_format = format;
    NagAudio *e = nag_audio_new(&cfg, on_event, &seen);
    if (!e)
        return 1;
    nag_audio_set_sink(e, nag_sink_pull_new());
    nag_audio_set_volume(e, volume);
    if (nag_audio_open(e, 1, files[0], seek_ms > 0 && seek_after < 0 ? seek_ms : 0) < 0) {
        nag_audio_free(e);
        return 1;
    }
    /* the queue: file i plays as track i + 1; the next is set once the one before it plays */
    int next_file = 1;
    int64_t queued = -1;
    if (next_file < n_files) {
        queued = next_file + 1;
        nag_audio_set_next(e, queued, files[next_file++]);
    }
    nag_audio_play(e);
    FILE *out = NULL;
    if (out_path)
        out = strcmp(out_path, "-") == 0 ? stdout : fopen(out_path, "wb");
    struct AVMD5 *hash = md5 ? av_md5_alloc() : NULL;
    if (hash)
        av_md5_init(hash);
    const int frames = 1024;
    void *buf = NULL;
    size_t buf_size = 0;
    int64_t total = 0;
    int64_t last_track = -1;
    int idle_rounds = 0;
    NagFormat fmt = {0};
    while (nag_audio_state(e) != NAG_STATE_ENDED && nag_audio_state(e) != NAG_STATE_STOPPED) {
        NagFormat now = nag_audio_output_format(e);
        if (now.sample_format == NAG_FMT_NONE) {
            usleep(1000);
            if (++idle_rounds > 5000)
                break;
            continue;
        }
        if (!format_same(&now, &fmt)) {
            fmt = now;
            size_t need = (size_t)frames * fmt.channels * nag_format_bytes_per_sample(fmt.sample_format);
            if (need > buf_size) {
                free(buf);
                buf = malloc(need);
                buf_size = need;
            }
        }
        int n = nag_audio_render(e, buf, frames);
        /* the queued track plays: the file after it is queued */
        int64_t track = nag_audio_track_id(e);
        if (track != last_track) {
            last_track = track;
            if (track == queued && next_file < n_files) {
                queued = next_file + 1;
                nag_audio_set_next(e, queued, files[next_file++]);
            }
        }
        if (n > 0) {
            size_t bytes = (size_t)n * fmt.channels * nag_format_bytes_per_sample(fmt.sample_format);
            if (out)
                fwrite(buf, 1, bytes, out);
            if (hash)
                av_md5_update(hash, buf, bytes);
            total += n;
            idle_rounds = 0;
            if (seek_after >= 0 && total >= seek_after) {
                nag_audio_seek(e, seek_ms);
                seek_after = -1;
            }
        } else {
            /* the decoder has not caught up (or a seek is under way): give it a moment */
            usleep(500);
            if (++idle_rounds > 20000) {
                fprintf(stderr, "nagaudio: no frames for 10 seconds\n");
                break;
            }
        }
    }
    nag_audio_stop(e);
    nag_audio_free(e);
    if (out && out != stdout)
        fclose(out);
    else if (out)
        fflush(out);
    printf("{\"frames\":%lld,\"rate\":%d,\"channels\":%d,\"format\":\"%s\",\"tracks\":%d,\"errors\":%d", (long long)total, fmt.rate,
           fmt.channels, nag_format_name(fmt.sample_format), seen.tracks, seen.errors);
    if (hash) {
        uint8_t digest[16];
        av_md5_final(hash, digest);
        printf(",\"md5\":\"");
        for (int i = 0; i < 16; i++)
            printf("%02x", digest[i]);
        printf("\"");
        av_free(hash);
    }
    printf(",\"events\":\"%s\"}\n", seen.log);
    free(buf);
    return seen.errors ? 1 : 0;
}

int main(int argc, char **argv)
{
    if (argc >= 3 && strcmp(argv[1], "info") == 0)
        return info(argv[2]);
    if (argc >= 3 && strcmp(argv[1], "decode") == 0)
        return decode(argc - 2, argv + 2);
    if (argc >= 2 && strcmp(argv[1], "version") == 0) {
        printf("nagaudio %s\n", nag_audio_version());
        return 0;
    }
    fprintf(stderr, "usage: nagaudio info FILE | nagaudio decode FILE... [-o OUT] [-f FORMAT] [--seek MS] [--volume V] [--no-dither] [--md5]\n");
    return 2;
}
