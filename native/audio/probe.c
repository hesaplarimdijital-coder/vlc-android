/*
 * Nağmedar Ses Motoru: a file as the library keeps it (S-803) and its waveform (S-1001).
 */
#include "nagaudio.h"
#include "source.h"

#include <math.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct Text {
    char *s;
    size_t len, cap;
} Text;

static void add(Text *t, const char *fmt, ...)
{
    if (!t->s)
        return;
    va_list ap;
    for (;;) {
        size_t room = t->cap - t->len;
        va_start(ap, fmt);
        int n = vsnprintf(t->s + t->len, room, fmt, ap);
        va_end(ap);
        if (n < 0) {
            free(t->s);
            t->s = NULL;
            return;
        }
        if ((size_t)n < room) {
            t->len += (size_t)n;
            return;
        }
        char *bigger = realloc(t->s, t->cap * 2 + (size_t)n);
        if (!bigger) {
            free(t->s);
            t->s = NULL;
            return;
        }
        t->s = bigger;
        t->cap = t->cap * 2 + (size_t)n;
    }
}

/* [text] as a JSON string, quotes included */
static void add_quoted(Text *t, const char *text)
{
    add(t, "\"");
    for (const unsigned char *p = (const unsigned char *)text; *p; p++) {
        if (*p == '"' || *p == '\\')
            add(t, "\\%c", *p);
        else if (*p < 0x20)
            add(t, "\\u%04x", *p);
        else
            add(t, "%c", *p);
    }
    add(t, "\"");
}

char *nag_probe_json(const char *url)
{
    NagSource *src = NULL;
    char error[256] = {0};
    if (nag_source_open(&src, url, 0, error, sizeof(error)) < 0)
        return NULL;
    const NagSourceInfo *i = nag_source_info(src);
    Text t = {malloc(512), 0, 512};
    add(&t, "{\"codec\":");
    add_quoted(&t, i->codec);
    add(&t, ",\"container\":");
    add_quoted(&t, i->container);
    add(&t, ",\"rate\":%d,\"channels\":%d,\"bits\":%d,\"float\":%s,\"duration_ms\":%lld,\"bitrate\":%lld", i->rate, i->channels, i->bits,
        i->is_float ? "true" : "false", (long long)i->duration_ms, (long long)i->bitrate);
    if (!isnan(i->track_gain_db))
        add(&t, ",\"track_gain_db\":%.2f", i->track_gain_db);
    if (!isnan(i->track_peak))
        add(&t, ",\"track_peak\":%.6f", i->track_peak);
    if (!isnan(i->album_gain_db))
        add(&t, ",\"album_gain_db\":%.2f", i->album_gain_db);
    if (!isnan(i->album_peak))
        add(&t, ",\"album_peak\":%.6f", i->album_peak);
    if (i->dsd_rate)
        add(&t, ",\"dsd_rate\":%d", i->dsd_rate);
    if (i->composer[0]) {
        add(&t, ",\"composer\":");
        add_quoted(&t, i->composer);
    }
    add(&t, "}");
    nag_source_close(src);
    return t.s;
}

#define READ_FRAMES 4096

int nag_waveform(const char *url, int points, float *out, const int *cancel, char *error, size_t error_size)
{
    if (points <= 0 || !out) {
        snprintf(error, error_size, "no points");
        return -1;
    }
    memset(out, 0, sizeof(float) * 2 * (size_t)points);
    NagSource *src = NULL;
    int ret = nag_source_open(&src, url, 0, error, error_size);
    if (ret < 0)
        return ret;
    const NagSourceInfo *info = nag_source_info(src);
    const int channels = nag_source_channels(src);
    const int64_t total = info->duration_ms > 0 ? info->duration_ms * (int64_t)info->rate / 1000 : 0;
    if (total <= 0 || channels <= 0) {
        snprintf(error, error_size, "the length is not known");
        nag_source_close(src);
        return -1;
    }
    double *buf = malloc(sizeof(double) * READ_FRAMES * (size_t)channels);
    double *squares = calloc((size_t)points, sizeof(double));
    int64_t *samples = calloc((size_t)points, sizeof(int64_t));
    if (!buf || !squares || !samples) {
        snprintf(error, error_size, "out of memory");
        ret = -1;
        goto done;
    }
    int64_t frame = 0;
    for (;;) {
        if (cancel && __atomic_load_n(cancel, __ATOMIC_RELAXED)) {
            ret = -2;
            break;
        }
        int n = nag_source_read(src, buf, READ_FRAMES);
        if (n < 0) {
            snprintf(error, error_size, "decoding failed");
            ret = n;
            break;
        }
        if (n == 0) {
            ret = 0;
            break;
        }
        for (int i = 0; i < n; i++, frame++) {
            /* the frames past the length the file said go to the last bucket */
            int64_t b = frame * points / total;
            if (b >= points)
                b = points - 1;
            const double *f = buf + (size_t)i * channels;
            double peak = out[2 * b];
            for (int c = 0; c < channels; c++) {
                double a = fabs(f[c]);
                if (a > peak)
                    peak = a > 1.0 ? 1.0 : a;
                squares[b] += f[c] * f[c];
            }
            out[2 * b] = (float)peak;
            samples[b] += channels;
        }
    }
    if (ret == 0) {
        int filled = 0;
        for (int b = 0; b < points; b++) {
            if (!samples[b])
                continue;
            double rms = sqrt(squares[b] / (double)samples[b]);
            out[2 * b + 1] = (float)(rms > 1.0 ? 1.0 : rms);
            filled = b + 1;
        }
        ret = filled;
    }
done:
    free(buf);
    free(squares);
    free(samples);
    nag_source_close(src);
    return ret;
}
