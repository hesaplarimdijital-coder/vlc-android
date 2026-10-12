// nagaudio: the engine from the command line, for the host tests (S-901, S-1405) and for a look
// at a file.
//
//   nagaudio info FILE                      the source as the engine reads it, JSON (the library's
//                                           probe, S-803: with the composer)
//   nagaudio wave FILE [--points N]         the waveform: each of N buckets' peak and RMS (S-1001)
//   nagaudio scan FILE... [--album]         EBU R128 loudness, true peak and ReplayGain 2.0 gain
//                                           of each file (and of the album with --album), JSON
//   nagaudio tag FILE [--track-gain DB] [--track-peak P] [--album-gain DB] [--album-peak P]
//                     [--track-lufs L] [--album-lufs L]
//                                           writes the ReplayGain tags into FILE (S-907)
//   nagaudio decode FILE [FILE2 ...] [-o OUT] [-f s16|s24|s32|f32|f64] [--seek MS]
//                                           [--seek-after FRAMES] [--volume V] [--no-dither] [--md5]
//                                           [--crossfade MS [--linear]]
//                                           [--preamp DB] [--auto-preamp] [--graphic DB,DB,...]
//                                           [--peq TYPE:HZ:DB:Q;...] [--profile TYPE:HZ:DB:Q;...]
//                                           [--profile-preamp DB] [--limiter DB] [--limiter-release MS]
//                                           [--limiter-lookahead MS] [--bypass]
//                                           [--replaygain off|track|album] [--rg-preamp DB]
//                                           [--rg-untagged DB] [--rg-no-clip]
//                                           [--tempo T] [--pitch SEMITONES] [--balance B] [--mono]
//                                           [--dop] [--min-format FORMAT] [--max-format FORMAT]
//                                           [--ab-after FRAMES]
//           plays FILE (then FILE2 ... gapless, as a queue) through the engine's render into
//           OUT (raw samples; "-" for stdout; none: nothing written) and prints a JSON summary
//           with the events seen and, with --md5, the MD5 of the samples (what ffmpeg -f md5
//           prints for the same raw format). --seek MS starts at MS; with --seek-after N the
//           start is 0 and the seek comes while playing, after N frames rendered. The DSP
//           options (S-906) set the chain: --graphic takes the 20 bands' dB in order, a band
//           of --peq/--profile is pk, ls, hs, lp or hp with its Hz, dB and Q. The summary
//           carries the clip meter, the chain's latency and the signal path.
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

static int info(const char *path)
{
    char *json = nag_probe_json(path);
    if (!json) {
        fprintf(stderr, "nagaudio: %s: does not open\n", path);
        return 1;
    }
    printf("%s\n", json);
    free(json);
    return 0;
}

/* S-1001: the waveform's buckets, peaks and RMS */
static int wave(int argc, char **argv)
{
    const char *path = NULL;
    int points = 1000;
    for (int i = 0; i < argc; i++) {
        if (strcmp(argv[i], "--points") == 0 && i + 1 < argc) points = atoi(argv[++i]);
        else path = argv[i];
    }
    if (!path || points <= 0) {
        fprintf(stderr, "nagaudio wave: a file and a positive --points are needed\n");
        return 2;
    }
    float *buckets = malloc(sizeof(float) * 2 * (size_t)points);
    char error[256] = {0};
    int filled = buckets ? nag_waveform(path, points, buckets, NULL, error, sizeof(error)) : -1;
    if (filled < 0) {
        fprintf(stderr, "nagaudio: %s: %s\n", path, error);
        free(buckets);
        return 1;
    }
    printf("{\"points\":%d,\"filled\":%d,\"peak\":[", points, filled);
    for (int b = 0; b < points; b++)
        printf("%s%.7f", b ? "," : "", buckets[2 * b]);
    printf("],\"rms\":[");
    for (int b = 0; b < points; b++)
        printf("%s%.7f", b ? "," : "", buckets[2 * b + 1]);
    printf("]}\n");
    free(buckets);
    return 0;
}

static void print_loudness(const NagLoudnessResult *r)
{
    if (r->loudness_lufs > -HUGE_VAL)
        printf("{\"loudness_lufs\":%.3f,", r->loudness_lufs);
    else
        printf("{\"loudness_lufs\":null,");
    printf("\"true_peak\":%.9f,\"sample_peak\":%.9f,\"gain_db\":%.3f,\"frames\":%lld,\"rate\":%d}", r->true_peak, r->sample_peak, r->gain_db,
           (long long)r->frames, r->rate);
}

static int scan(int argc, char **argv)
{
    int album = 0;
    const char *files[64];
    int n_files = 0;
    for (int i = 0; i < argc; i++) {
        if (strcmp(argv[i], "--album") == 0) album = 1;
        else if (n_files < 64) files[n_files++] = argv[i];
    }
    if (!n_files) {
        fprintf(stderr, "nagaudio scan: a file is needed\n");
        return 2;
    }
    NagLoudnessScan *acc = album ? nag_loudness_scan_new() : NULL;
    printf("{\"tracks\":[");
    int failed = 0;
    for (int i = 0; i < n_files; i++) {
        NagLoudnessResult r;
        char error[256] = {0};
        if (nag_loudness_scan_file(acc, files[i], &r, NULL, error, sizeof(error)) < 0) {
            fprintf(stderr, "nagaudio: %s: %s\n", files[i], error);
            failed++;
            printf("%snull", i ? "," : "");
            continue;
        }
        printf("%s", i ? "," : "");
        print_loudness(&r);
    }
    printf("]");
    if (acc) {
        NagLoudnessResult r;
        printf(",\"album\":");
        if (nag_loudness_scan_result(acc, &r) == 0)
            print_loudness(&r);
        else
            printf("null");
        nag_loudness_scan_free(acc);
    }
    printf("}\n");
    return failed ? 1 : 0;
}

static int tag(int argc, char **argv)
{
    const char *file = NULL;
    NagReplayGainTags t = {NAN, NAN, NAN, NAN, NAN, NAN};
    for (int i = 0; i < argc; i++) {
        if (strcmp(argv[i], "--track-gain") == 0 && i + 1 < argc) t.track_gain_db = atof(argv[++i]);
        else if (strcmp(argv[i], "--track-peak") == 0 && i + 1 < argc) t.track_peak = atof(argv[++i]);
        else if (strcmp(argv[i], "--album-gain") == 0 && i + 1 < argc) t.album_gain_db = atof(argv[++i]);
        else if (strcmp(argv[i], "--album-peak") == 0 && i + 1 < argc) t.album_peak = atof(argv[++i]);
        else if (strcmp(argv[i], "--track-lufs") == 0 && i + 1 < argc) t.track_lufs = atof(argv[++i]);
        else if (strcmp(argv[i], "--album-lufs") == 0 && i + 1 < argc) t.album_lufs = atof(argv[++i]);
        else file = argv[i];
    }
    if (!file) {
        fprintf(stderr, "nagaudio tag: a file is needed\n");
        return 2;
    }
    FILE *f = fopen(file, "r+b");
    if (!f) {
        perror(file);
        return 1;
    }
    char error[256] = {0};
    int ret = nag_tags_write_replaygain(fileno(f), file, &t, error, sizeof(error));
    fclose(f);
    if (ret < 0) {
        fprintf(stderr, "nagaudio: %s: %s\n", file, error);
        return 1;
    }
    printf("{\"written\":true}\n");
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

/* "TYPE:HZ:DB:Q;TYPE:HZ:DB:Q" into [bands]; the count, or -1 for a bad band. */
static int parse_bands(const char *text, NagEqBand *bands, int max)
{
    int n = 0;
    const char *p = text;
    while (*p && n < max) {
        char type[8] = {0};
        double hz = 0, db = 0, q = 1;
        int used = 0;
        if (sscanf(p, "%7[a-z]:%lf:%lf:%lf%n", type, &hz, &db, &q, &used) < 3)
            return -1;
        NagEqBand *b = &bands[n++];
        b->enabled = 1;
        b->freq = hz;
        b->gain_db = db;
        b->q = q;
        if (strcmp(type, "pk") == 0) b->type = NAG_EQ_PEAK;
        else if (strcmp(type, "ls") == 0) b->type = NAG_EQ_LOW_SHELF;
        else if (strcmp(type, "hs") == 0) b->type = NAG_EQ_HIGH_SHELF;
        else if (strcmp(type, "lp") == 0) b->type = NAG_EQ_LOW_PASS;
        else if (strcmp(type, "hp") == 0) b->type = NAG_EQ_HIGH_PASS;
        else return -1;
        p += used;
        if (*p == ';')
            p++;
    }
    return n;
}

static int decode(int argc, char **argv)
{
    const char *files[64];
    int n_files = 0;
    const char *out_path = NULL;
    NagSampleFormat format = NAG_FMT_NONE;
    int64_t seek_ms = -1, seek_after = -1;
    double volume = 1.0;
    int dither = 1, md5 = 0, crossfade_ms = 0, linear = 0, dsp_set = 0, rg_set = 0;
    NagDspParams dsp;
    nag_dsp_params_defaults(&dsp);
    NagReplayGain rg = {NAG_REPLAYGAIN_OFF, 0, 0, 1};
    double tempo = 1.0, pitch = 0.0, balance = 0.0;
    int mono = 0, dop = 0;
    NagSampleFormat min_format = NAG_FMT_NONE, max_format = NAG_FMT_S32;
    int64_t ab_after = -1; /* S-912: side B from this many frames on */
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
        else if (strcmp(argv[i], "--crossfade") == 0 && i + 1 < argc) crossfade_ms = atoi(argv[++i]);
        else if (strcmp(argv[i], "--linear") == 0) linear = 1;
        else if (strcmp(argv[i], "--md5") == 0) md5 = 1;
        else if (strcmp(argv[i], "--preamp") == 0 && i + 1 < argc) { dsp.preamp_db = atof(argv[++i]); dsp_set = 1; }
        else if (strcmp(argv[i], "--auto-preamp") == 0) { dsp.auto_preamp = 1; dsp_set = 1; }
        else if (strcmp(argv[i], "--graphic") == 0 && i + 1 < argc) {
            dsp.graphic_enabled = 1;
            dsp_set = 1;
            const char *p = argv[++i];
            for (int b = 0; b < NAG_GRAPHIC_BANDS && *p; b++) {
                dsp.graphic_db[b] = strtod(p, (char **)&p);
                if (*p == ',')
                    p++;
            }
        } else if (strcmp(argv[i], "--peq") == 0 && i + 1 < argc) {
            dsp.parametric_enabled = 1;
            dsp_set = 1;
            if (parse_bands(argv[++i], dsp.parametric, NAG_EQ_BANDS) < 0) {
                fprintf(stderr, "nagaudio: bad band in %s\n", argv[i]);
                return 2;
            }
        } else if (strcmp(argv[i], "--profile") == 0 && i + 1 < argc) {
            dsp.profile_enabled = 1;
            dsp_set = 1;
            snprintf(dsp.profile_name, sizeof(dsp.profile_name), "cli");
            if (parse_bands(argv[++i], dsp.profile, NAG_EQ_BANDS) < 0) {
                fprintf(stderr, "nagaudio: bad band in %s\n", argv[i]);
                return 2;
            }
        } else if (strcmp(argv[i], "--profile-preamp") == 0 && i + 1 < argc) { dsp.profile_enabled = 1; dsp.profile_preamp_db = atof(argv[++i]); dsp_set = 1; }
        else if (strcmp(argv[i], "--limiter") == 0 && i + 1 < argc) { dsp.limiter_enabled = 1; dsp.limiter_threshold_db = atof(argv[++i]); dsp_set = 1; }
        else if (strcmp(argv[i], "--limiter-release") == 0 && i + 1 < argc) { dsp.limiter_release_ms = atof(argv[++i]); dsp_set = 1; }
        else if (strcmp(argv[i], "--limiter-lookahead") == 0 && i + 1 < argc) { dsp.limiter_lookahead_ms = atof(argv[++i]); dsp_set = 1; }
        else if (strcmp(argv[i], "--bypass") == 0) { dsp.bypass = 1; dsp_set = 1; }
        else if (strcmp(argv[i], "--replaygain") == 0 && i + 1 < argc) {
            const char *mode = argv[++i];
            rg.mode = strcmp(mode, "track") == 0 ? NAG_REPLAYGAIN_TRACK : strcmp(mode, "album") == 0 ? NAG_REPLAYGAIN_ALBUM : NAG_REPLAYGAIN_OFF;
            rg_set = 1;
        } else if (strcmp(argv[i], "--rg-preamp") == 0 && i + 1 < argc) { rg.preamp_db = atof(argv[++i]); rg_set = 1; }
        else if (strcmp(argv[i], "--rg-untagged") == 0 && i + 1 < argc) { rg.untagged_db = atof(argv[++i]); rg_set = 1; }
        else if (strcmp(argv[i], "--rg-no-clip") == 0) { rg.prevent_clipping = 0; rg_set = 1; }
        else if (strcmp(argv[i], "--tempo") == 0 && i + 1 < argc) tempo = atof(argv[++i]);
        else if (strcmp(argv[i], "--pitch") == 0 && i + 1 < argc) pitch = atof(argv[++i]);
        else if (strcmp(argv[i], "--balance") == 0 && i + 1 < argc) balance = atof(argv[++i]);
        else if (strcmp(argv[i], "--mono") == 0) mono = 1;
        else if (strcmp(argv[i], "--dop") == 0) dop = 1;
        else if (strcmp(argv[i], "--min-format") == 0 && i + 1 < argc) min_format = parse_format(argv[++i]);
        else if (strcmp(argv[i], "--max-format") == 0 && i + 1 < argc) max_format = parse_format(argv[++i]);
        else if (strcmp(argv[i], "--ab-after") == 0 && i + 1 < argc) ab_after = atoll(argv[++i]);
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
    nag_audio_set_crossfade(e, crossfade_ms, linear ? NAG_CROSSFADE_LINEAR : NAG_CROSSFADE_EQUAL_POWER);
    if (dsp_set)
        nag_audio_set_dsp(e, &dsp);
    if (rg_set)
        nag_audio_set_replaygain(e, &rg);
    nag_audio_set_tempo(e, tempo);
    nag_audio_set_pitch(e, pitch);
    nag_audio_set_balance(e, balance);
    nag_audio_set_mono(e, mono);
    nag_audio_set_dsd_mode(e, dop ? NAG_DSD_DOP : NAG_DSD_PCM);
    if (min_format != NAG_FMT_NONE || max_format != NAG_FMT_S32)
        nag_audio_set_format_range(e, min_format, max_format);
    if (nag_audio_open(e, 1, files[0], seek_ms > 0 && seek_after < 0 ? seek_ms : 0) < 0) {
        nag_audio_free(e);
        return 1;
    }
    /* the queue: file i plays as track i + 1; the next is set once the one before it plays */
    int next_file = 1;
    int64_t queued = -1;
    if (next_file < n_files) {
        queued = next_file + 1;
        nag_audio_set_next(e, queued, files[next_file++], crossfade_ms > 0);
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
    char *signal_path = NULL;
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
                nag_audio_set_next(e, queued, files[next_file++], crossfade_ms > 0);
            }
        }
        if (n > 0) {
            size_t bytes = (size_t)n * fmt.channels * nag_format_bytes_per_sample(fmt.sample_format);
            if (!signal_path)
                signal_path = nag_audio_signal_path_json(e); /* as the first frames play */
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
            if (ab_after >= 0 && total >= ab_after) {
                nag_audio_set_ab_side(e, 1);
                ab_after = -1;
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
    NagDspMeter meter;
    nag_audio_dsp_meter(e, &meter, 0);
    int64_t dsp_latency = nag_audio_dsp_latency_frames(e);
    double dsp_level_db = nag_audio_dsp_level_db(e), ab_gain_db = nag_audio_ab_gain_db(e);
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
    printf(",\"meter\":{\"frames\":%lld,\"clipped\":%lld,\"peak\":%.9f,\"reduction_db\":%.4f},\"dsp_latency\":%lld",
           (long long)meter.frames, (long long)meter.clipped, meter.peak, meter.reduction_db, (long long)dsp_latency);
    printf(",\"dsp_level_db\":%.4f,\"ab_gain_db\":%.4f", dsp_level_db, ab_gain_db);
    printf(",\"signal_path\":%s", signal_path ? signal_path : "null");
    printf(",\"events\":\"%s\"}\n", seen.log);
    free(signal_path);
    free(buf);
    return seen.errors ? 1 : 0;
}

/* S-1003: the file's lyrics as JSON; "{}" when it has none */
static int lyrics(const char *file)
{
    FILE *f = fopen(file, "rb");
    if (!f) {
        perror(file);
        return 1;
    }
    char *json = nag_tags_read_lyrics(fileno(f), file);
    fclose(f);
    printf("%s\n", json ? json : "{}");
    free(json);
    return 0;
}

int main(int argc, char **argv)
{
    if (argc >= 3 && strcmp(argv[1], "info") == 0)
        return info(argv[2]);
    if (argc >= 3 && strcmp(argv[1], "decode") == 0)
        return decode(argc - 2, argv + 2);
    if (argc >= 3 && strcmp(argv[1], "scan") == 0)
        return scan(argc - 2, argv + 2);
    if (argc >= 3 && strcmp(argv[1], "tag") == 0)
        return tag(argc - 2, argv + 2);
    if (argc >= 3 && strcmp(argv[1], "wave") == 0)
        return wave(argc - 2, argv + 2);
    if (argc >= 3 && strcmp(argv[1], "lyrics") == 0)
        return lyrics(argv[2]);
    if (argc >= 2 && strcmp(argv[1], "version") == 0) {
        printf("nagaudio %s\n", nag_audio_version());
        return 0;
    }
    fprintf(stderr, "usage: nagaudio info FILE | decode FILE... [options] | scan FILE... [--album] | tag FILE [--track-gain DB ...] | wave FILE [--points N] | lyrics FILE | version\n");
    return 2;
}
