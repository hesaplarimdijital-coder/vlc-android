#include "scope.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

int nag_scope_tap_init(NagScopeTap *tap)
{
    memset(tap, 0, sizeof(*tap));
    tap->data = calloc((size_t)NAG_SCOPE_CAPACITY * 2, sizeof(float));
    if (!tap->data)
        return -1;
    atomic_init(&tap->written, 0);
    atomic_init(&tap->peak[0], 0.0);
    atomic_init(&tap->peak[1], 0.0);
    return 0;
}

void nag_scope_tap_free(NagScopeTap *tap)
{
    free(tap->data);
    tap->data = NULL;
}

static inline void max_double(_Atomic double *target, double value)
{
    double seen = atomic_load(target);
    while (value > seen && !atomic_compare_exchange_weak(target, &seen, value))
        ;
}

void nag_scope_tap_write(NagScopeTap *tap, const double *frames, int n, int channels)
{
    if (!tap->data || n <= 0 || channels <= 0)
        return;
    uint64_t pos = atomic_load_explicit(&tap->written, memory_order_relaxed);
    double peak_l = 0.0, peak_r = 0.0;
    const int right = channels > 1 ? 1 : 0;
    for (int i = 0; i < n; i++) {
        const double l = frames[(size_t)i * channels];
        const double r = frames[(size_t)i * channels + right];
        float *slot = tap->data + (size_t)((pos + (uint64_t)i) % NAG_SCOPE_CAPACITY) * 2;
        slot[0] = (float)l;
        slot[1] = (float)r;
        const double al = fabs(l), ar = fabs(r);
        if (al > peak_l)
            peak_l = al;
        if (ar > peak_r)
            peak_r = ar;
    }
    atomic_store_explicit(&tap->written, pos + (uint64_t)n, memory_order_release);
    max_double(&tap->peak[0], peak_l);
    max_double(&tap->peak[1], peak_r);
}

void nag_scope_tap_read(NagScopeTap *tap, float *dst, int n, uint64_t delay, uint64_t *written)
{
    const uint64_t total = atomic_load_explicit(&tap->written, memory_order_acquire);
    if (written)
        *written = total;
    /* the newest frame heard; what the ring no longer holds (a delay past it) is not read */
    if (delay > NAG_SCOPE_CAPACITY - (uint64_t)n - 8192)
        delay = NAG_SCOPE_CAPACITY - (uint64_t)n - 8192;
    const uint64_t end = total > delay ? total - delay : 0;
    for (int i = 0; i < n; i++) {
        const uint64_t frame = end - (uint64_t)n + (uint64_t)i; /* wraps below 0 for the frames before the first */
        if (end < (uint64_t)n - (uint64_t)i) {
            dst[2 * i] = dst[2 * i + 1] = 0.0f;
            continue;
        }
        const float *slot = tap->data + (size_t)(frame % NAG_SCOPE_CAPACITY) * 2;
        dst[2 * i] = slot[0];
        dst[2 * i + 1] = slot[1];
    }
}

void nag_scope_tap_take_peaks(NagScopeTap *tap, double peak[2])
{
    peak[0] = atomic_exchange(&tap->peak[0], 0.0);
    peak[1] = atomic_exchange(&tap->peak[1], 0.0);
}

int nag_scope_fft_size(int rate)
{
    /* 2048 at 44.1 and 48 kHz: 23 Hz a bin, 43 ms a window; as long a window at other rates */
    double want = rate > 0 ? rate * (2048.0 / 48000.0) : 2048.0;
    int size = NAG_SCOPE_FFT_MIN;
    while (size < NAG_SCOPE_FFT_MAX && size * 1.5 < want)
        size *= 2;
    return size;
}

double nag_scope_db(double linear)
{
    if (!(linear > 0.0))
        return NAG_SCOPE_FLOOR_DB;
    const double db = 20.0 * log10(linear);
    return db < NAG_SCOPE_FLOOR_DB ? NAG_SCOPE_FLOOR_DB : db;
}

void nag_scope_analyser_free(NagScopeAnalyser *a)
{
    free(a->window);
    free(a->cos_table);
    free(a->sin_table);
    free(a->re);
    free(a->im);
    memset(a, 0, sizeof(*a));
}

static int prepare(NagScopeAnalyser *a, int size)
{
    if (a->size == size)
        return 0;
    nag_scope_analyser_free(a);
    a->window = malloc(sizeof(float) * (size_t)size);
    a->cos_table = malloc(sizeof(float) * (size_t)size / 2);
    a->sin_table = malloc(sizeof(float) * (size_t)size / 2);
    a->re = malloc(sizeof(float) * (size_t)size);
    a->im = malloc(sizeof(float) * (size_t)size);
    if (!a->window || !a->cos_table || !a->sin_table || !a->re || !a->im) {
        nag_scope_analyser_free(a);
        return -1;
    }
    for (int i = 0; i < size; i++)
        a->window[i] = (float)(0.5 - 0.5 * cos(2.0 * M_PI * i / size)); /* periodic Hann: its mean is 1/2 */
    for (int i = 0; i < size / 2; i++) {
        a->cos_table[i] = (float)cos(2.0 * M_PI * i / size);
        a->sin_table[i] = (float)-sin(2.0 * M_PI * i / size);
    }
    a->size = size;
    return 0;
}

/* In place, radix 2, decimation in time: re/im of [a]'s length. */
static void fft(NagScopeAnalyser *a)
{
    const int n = a->size;
    float *re = a->re, *im = a->im;
    for (int i = 1, j = 0; i < n; i++) {
        int bit = n >> 1;
        for (; j & bit; bit >>= 1)
            j ^= bit;
        j ^= bit;
        if (i < j) {
            float t = re[i];
            re[i] = re[j];
            re[j] = t;
            t = im[i];
            im[i] = im[j];
            im[j] = t;
        }
    }
    for (int len = 2; len <= n; len <<= 1) {
        const int half = len >> 1, step = n / len;
        for (int start = 0; start < n; start += len) {
            for (int k = 0; k < half; k++) {
                const float wr = a->cos_table[k * step], wi = a->sin_table[k * step];
                const int p = start + k, q = p + half;
                const float tr = re[q] * wr - im[q] * wi;
                const float ti = re[q] * wi + im[q] * wr;
                re[q] = re[p] - tr;
                im[q] = im[p] - ti;
                re[p] += tr;
                im[p] += ti;
            }
        }
    }
}

int nag_scope_analyse(NagScopeAnalyser *a, const float *frames, int rate, int wave_points, NagScopeFrame *out)
{
    const int n = nag_scope_fft_size(rate);
    if (prepare(a, n) < 0)
        return -1;
    out->rate = rate;
    out->fft_size = n;
    /* the RMS of the window, each channel */
    double sum_l = 0.0, sum_r = 0.0;
    for (int i = 0; i < n; i++) {
        const double l = frames[2 * i], r = frames[2 * i + 1];
        sum_l += l * l;
        sum_r += r * r;
    }
    out->rms_db[0] = nag_scope_db(sqrt(sum_l / n));
    out->rms_db[1] = nag_scope_db(sqrt(sum_r / n));
    /* both channels in one transform: left the real part, right the imaginary */
    for (int i = 0; i < n; i++) {
        a->re[i] = frames[2 * i] * a->window[i];
        a->im[i] = frames[2 * i + 1] * a->window[i];
    }
    fft(a);
    /* a sine of amplitude A on bin k: |X_k| = A · n · mean(window) / 2, so A = |X_k| · 4 / n */
    const double scale = 4.0 / n;
    for (int k = 0; k < n / 2; k++) {
        const int m = k == 0 ? 0 : n - k;
        const double zr = a->re[k], zi = a->im[k], mr = a->re[m], mi = -a->im[m];
        /* left (Z_k + conj Z_{n-k}) / 2, right (Z_k - conj Z_{n-k}) / 2i */
        const double lr = (zr + mr) * 0.5, li = (zi + mi) * 0.5;
        const double dr = (zr - mr) * 0.5, di = (zi - mi) * 0.5;
        const double power = (lr * lr + li * li + dr * dr + di * di) * 0.5;
        out->spectrum_db[k] = (float)nag_scope_db(sqrt(power) * scale);
    }
    /* the oscilloscope: about 20 ms, from the last rising zero crossing that leaves room for it */
    if (wave_points > NAG_SCOPE_WAVE_MAX)
        wave_points = NAG_SCOPE_WAVE_MAX;
    if (wave_points < 0)
        wave_points = 0;
    int span = rate / 50;
    if (span > n / 2)
        span = n / 2;
    if (span < 1)
        span = 1;
    int start = n - span;
    for (int i = n - span; i > n - 2 * span && i > 0; i--) {
        const float before = frames[2 * (i - 1)] + frames[2 * (i - 1) + 1];
        const float at = frames[2 * i] + frames[2 * i + 1];
        if (before < 0.0f && at >= 0.0f) {
            start = i;
            break;
        }
    }
    out->wave_points = wave_points;
    for (int p = 0; p < wave_points; p++) {
        const int i = start + (int)((int64_t)p * span / wave_points);
        out->wave[0][p] = frames[2 * i];
        out->wave[1][p] = frames[2 * i + 1];
    }
    return 0;
}
