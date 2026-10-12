/*
 * Nağmedar Ses Motoru: tempo and pitch (S-909). See stretch.h.
 */
#include "stretch.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

#define TABLE_PHASES 256
#define TAPS_HALF_BASE 16
#define TAPS_HALF_MAX 64 /* 16 / 0.25: a pitch two octaves up */
#define KAISER_BETA 8.0
#define OVERLAP_MS 8.0
#define SEQ_MS_SLOW 125.0 /* the sequence at tempo 0.5 and under */
#define SEQ_MS_FAST 50.0  /* at tempo 2 and over */
#define SEEK_MS_SLOW 25.0
#define SEEK_MS_FAST 15.0
#define CHUNK 1024
#define OUT_REQUEST_MAX 4096

static double bessel_i0(double x)
{
    double sum = 1.0, term = 1.0;
    double y = x * x / 4.0;
    for (int k = 1; k < 64; k++) {
        term *= y / ((double)k * k);
        sum += term;
        if (term < sum * 1e-17)
            break;
    }
    return sum;
}

int nag_stretch_init(NagStretch *st, int channels)
{
    memset(st, 0, sizeof *st);
    if (channels < 1 || channels > NAG_STRETCH_CHANNELS_MAX)
        return -1;
    st->channels = channels;
    st->tempo = 1.0;
    st->ratio = 1.0;
    st->taps_half = TAPS_HALF_BASE;
    st->cutoff = 1.0;
    /* input: the longest sequence, seek and skip (tempo 4) at the highest rate, and a chunk */
    double longest_s = (SEQ_MS_SLOW + SEEK_MS_SLOW + NAG_TEMPO_MAX * SEQ_MS_SLOW) / 1000.0;
    st->in_cap = (int)(longest_s * NAG_STRETCH_RATE_MAX) + CHUNK;
    st->out_cap = OUT_REQUEST_MAX + (int)(SEQ_MS_SLOW / 1000.0 * NAG_STRETCH_RATE_MAX) + CHUNK;
    st->hist_cap = (int)(OUT_REQUEST_MAX * NAG_TEMPO_MAX) + 4 * TAPS_HALF_MAX + 2 * CHUNK;
    int overlap_max = (int)(OVERLAP_MS / 1000.0 * NAG_STRETCH_RATE_MAX) + 1;
    st->table_len = TAPS_HALF_MAX * TABLE_PHASES + 2;
    st->in = malloc(sizeof(double) * (size_t)st->in_cap * channels);
    st->out = malloc(sizeof(double) * (size_t)st->out_cap * channels);
    st->hist = malloc(sizeof(double) * (size_t)st->hist_cap * channels);
    st->mid = malloc(sizeof(double) * (size_t)overlap_max * channels);
    st->sinc_table = malloc(sizeof(double) * (size_t)st->table_len);
    st->window_table = malloc(sizeof(double) * (TABLE_PHASES + 2));
    if (!st->in || !st->out || !st->hist || !st->mid || !st->sinc_table || !st->window_table) {
        nag_stretch_free(st);
        return -1;
    }
    for (int i = 0; i < st->table_len; i++) {
        double x = (double)i / TABLE_PHASES;
        st->sinc_table[i] = i == 0 ? 1.0 : sin(M_PI * x) / (M_PI * x);
    }
    double i0 = bessel_i0(KAISER_BETA);
    for (int i = 0; i <= TABLE_PHASES + 1; i++) {
        double x = (double)i / TABLE_PHASES;
        st->window_table[i] = x >= 1.0 ? 0.0 : bessel_i0(KAISER_BETA * sqrt(1.0 - x * x)) / i0;
    }
    return 0;
}

void nag_stretch_free(NagStretch *st)
{
    free(st->in);
    free(st->out);
    free(st->hist);
    free(st->mid);
    free(st->sinc_table);
    free(st->window_table);
    memset(st, 0, sizeof *st);
}

void nag_stretch_reset(NagStretch *st)
{
    st->in_count = 0;
    st->mid_valid = 0;
    st->out_count = 0;
    st->out_read = 0;
    st->hist_count = 0;
    st->pos = 0;
    st->skip_fract = 0;
    st->ended = 0;
}

static double lerp_by_tempo(double tempo, double slow, double fast)
{
    double t = (tempo - 0.5) / 1.5;
    if (t < 0)
        t = 0;
    if (t > 1)
        t = 1;
    return slow + (fast - slow) * t;
}

void nag_stretch_configure(NagStretch *st, int rate, double tempo, double pitch_semitones)
{
    if (!(tempo >= NAG_TEMPO_MIN))
        tempo = NAG_TEMPO_MIN;
    if (tempo > NAG_TEMPO_MAX)
        tempo = NAG_TEMPO_MAX;
    if (!(pitch_semitones >= -NAG_PITCH_MAX_SEMITONES))
        pitch_semitones = -NAG_PITCH_MAX_SEMITONES;
    if (pitch_semitones > NAG_PITCH_MAX_SEMITONES)
        pitch_semitones = NAG_PITCH_MAX_SEMITONES;
    if (rate > NAG_STRETCH_RATE_MAX || rate <= 0) {
        /* beyond the stage's reach: it stays out of the way */
        st->rate = rate;
        st->tempo = tempo;
        st->pitch = pitch_semitones;
        st->active = 0;
        return;
    }
    int rate_changed = rate != st->rate;
    st->rate = rate;
    st->tempo = tempo;
    st->pitch = pitch_semitones;
    st->active = tempo != 1.0 || pitch_semitones != 0.0;
    /* the pitch's share: stretched by r, then resampled by r */
    double r = pow(2.0, pitch_semitones / 12.0);
    st->ratio = r;
    st->wsola_tempo = tempo / r;
    double wt = st->wsola_tempo;
    int seq = (int)(lerp_by_tempo(wt, SEQ_MS_SLOW, SEQ_MS_FAST) / 1000.0 * rate);
    int seek = (int)(lerp_by_tempo(wt, SEEK_MS_SLOW, SEEK_MS_FAST) / 1000.0 * rate);
    int overlap = (int)(OVERLAP_MS / 1000.0 * rate);
    if (overlap < 16)
        overlap = 16;
    if (seq < 3 * overlap)
        seq = 3 * overlap;
    if (seek < 1)
        seek = 1;
    if (rate_changed || seq != st->seq || seek != st->seek || overlap != st->overlap) {
        st->seq = seq;
        st->seek = seek;
        st->overlap = overlap;
        if (rate_changed)
            nag_stretch_reset(st);
        else
            st->mid_valid = 0; /* a different overlap: the tail kept no longer fits */
    }
    st->skip = wt * (seq - overlap);
    double cutoff = r > 1.0 ? 1.0 / r : 1.0;
    st->cutoff = cutoff;
    int half = (int)ceil(TAPS_HALF_BASE / cutoff);
    if (half > TAPS_HALF_MAX)
        half = TAPS_HALF_MAX;
    st->taps_half = half;
}

int nag_stretch_latency(const NagStretch *st)
{
    return st->in_count + st->out_count - st->out_read + (st->hist_count - (int)st->pos);
}

/* ------------------------------------------------------------------- WSOLA */

/* How well [overlap] frames of [in] at [offset] continue the tail kept: normalised correlation. */
static double correlation(const NagStretch *st, int offset)
{
    const int C = st->channels;
    const double *a = st->mid;
    const double *b = st->in + (size_t)offset * C;
    double corr = 0, norm = 0;
    const int n = st->overlap * C;
    for (int i = 0; i < n; i++) {
        corr += a[i] * b[i];
        norm += b[i] * b[i];
    }
    if (norm <= 0)
        return corr > 0 ? 0 : -1e300;
    return corr / sqrt(norm);
}

static int best_offset(const NagStretch *st)
{
    int best = 0;
    double best_corr = -1e300;
    /* coarse, every fourth position, then around the winner */
    for (int k = 0; k < st->seek; k += 4) {
        double c = correlation(st, k);
        if (c > best_corr) {
            best_corr = c;
            best = k;
        }
    }
    int lo = best - 3 > 0 ? best - 3 : 0;
    int hi = best + 3 < st->seek ? best + 3 : st->seek - 1;
    for (int k = lo; k <= hi; k++) {
        if (k % 4 == 0)
            continue;
        double c = correlation(st, k);
        if (c > best_corr) {
            best_corr = c;
            best = k;
        }
    }
    return best;
}

static void out_append(NagStretch *st, const double *frames, int n)
{
    const int C = st->channels;
    /* the fifo's read part is reclaimed first */
    if (st->out_read > 0) {
        memmove(st->out, st->out + (size_t)st->out_read * C, sizeof(double) * (size_t)(st->out_count - st->out_read) * C);
        st->out_count -= st->out_read;
        st->out_read = 0;
    }
    if (n > st->out_cap - st->out_count)
        n = st->out_cap - st->out_count; /* cannot happen with the sizes chosen */
    memcpy(st->out + (size_t)st->out_count * C, frames, sizeof(double) * (size_t)n * C);
    st->out_count += n;
}

static void in_consume(NagStretch *st, int n)
{
    const int C = st->channels;
    if (n >= st->in_count) {
        st->in_count = 0;
        return;
    }
    memmove(st->in, st->in + (size_t)n * C, sizeof(double) * (size_t)(st->in_count - n) * C);
    st->in_count -= n;
}

/* One sequence of input into the output fifo. */
static void wsola_sequence(NagStretch *st)
{
    const int C = st->channels;
    const int overlap = st->overlap, seq = st->seq;
    int offset = st->mid_valid ? best_offset(st) : 0;
    const double *s = st->in + (size_t)offset * C;
    if (st->mid_valid) {
        /* the tail kept fades into the sequence's head */
        double *mixed = st->out + (size_t)(st->out_count) * C; /* assembled in place after reclaiming */
        if (st->out_read > 0) {
            memmove(st->out, st->out + (size_t)st->out_read * C, sizeof(double) * (size_t)(st->out_count - st->out_read) * C);
            st->out_count -= st->out_read;
            st->out_read = 0;
            mixed = st->out + (size_t)st->out_count * C;
        }
        for (int i = 0; i < overlap; i++) {
            double t = (double)i / overlap;
            for (int c = 0; c < C; c++)
                mixed[(size_t)i * C + c] = st->mid[(size_t)i * C + c] * (1.0 - t) + s[(size_t)i * C + c] * t;
        }
        st->out_count += overlap;
        out_append(st, s + (size_t)overlap * C, seq - 2 * overlap);
    } else {
        out_append(st, s, seq - overlap);
    }
    memcpy(st->mid, s + (size_t)(seq - overlap) * C, sizeof(double) * (size_t)overlap * C);
    st->mid_valid = 1;
    st->skip_fract += st->skip;
    int n = (int)st->skip_fract;
    st->skip_fract -= n;
    in_consume(st, n);
}

/* Input needed before a sequence can be cut. */
static int wsola_need(const NagStretch *st)
{
    int skip = (int)ceil(st->skip) + st->overlap;
    int body = skip > st->seq ? skip : st->seq;
    return body + st->seek;
}

/* Pulls input until a sequence fits; -1 once the source ended, 0 when it is behind, 1 when ready. */
static int wsola_fill(NagStretch *st, nag_pull_fn pull, void *opaque)
{
    const int C = st->channels;
    int need = wsola_need(st);
    while (st->in_count < need) {
        int want = need - st->in_count;
        if (want > CHUNK)
            want = CHUNK;
        if (want > st->in_cap - st->in_count)
            want = st->in_cap - st->in_count;
        int got = pull(opaque, st->in + (size_t)st->in_count * C, want);
        if (got < 0) {
            st->ended = 1;
            return -1;
        }
        if (got == 0)
            return 0;
        st->in_count += got;
    }
    return 1;
}

/* The source ended: the tail kept and the input left go out as they are. */
static void wsola_flush(NagStretch *st)
{
    const int C = st->channels;
    if (st->mid_valid) {
        if (st->in_count >= st->overlap) {
            for (int i = 0; i < st->overlap; i++) {
                double t = (double)i / st->overlap;
                for (int c = 0; c < C; c++)
                    st->in[(size_t)i * C + c] = st->mid[(size_t)i * C + c] * (1.0 - t) + st->in[(size_t)i * C + c] * t;
            }
        } else {
            out_append(st, st->mid, st->overlap);
        }
        st->mid_valid = 0;
    }
    out_append(st, st->in, st->in_count);
    st->in_count = 0;
}

/* [frames] WSOLA output frames into [dst]; how many. */
static int wsola_pull(NagStretch *st, double *dst, int frames, nag_pull_fn pull, void *opaque)
{
    const int C = st->channels;
    while (st->out_count - st->out_read < frames && !st->ended) {
        int ready = wsola_fill(st, pull, opaque);
        if (ready < 0) {
            wsola_flush(st);
            break;
        }
        if (ready == 0)
            break;
        wsola_sequence(st);
    }
    int n = st->out_count - st->out_read;
    if (n > frames)
        n = frames;
    memcpy(dst, st->out + (size_t)st->out_read * C, sizeof(double) * (size_t)n * C);
    st->out_read += n;
    if (st->out_read == st->out_count)
        st->out_read = st->out_count = 0;
    return n;
}

/* --------------------------------------------------------------- resampler */

static inline double sinc_at(const NagStretch *st, double x)
{
    double p = fabs(x) * TABLE_PHASES;
    int i = (int)p;
    if (i + 1 >= st->table_len)
        return 0;
    double f = p - i;
    return st->sinc_table[i] + (st->sinc_table[i + 1] - st->sinc_table[i]) * f;
}

static inline double window_at(const NagStretch *st, double u) /* u = |x| / half in [0, 1] */
{
    double p = u * TABLE_PHASES;
    int i = (int)p;
    if (i >= TABLE_PHASES)
        return 0;
    double f = p - i;
    return st->window_table[i] + (st->window_table[i + 1] - st->window_table[i]) * f;
}

static void hist_drop(NagStretch *st, int n)
{
    const int C = st->channels;
    if (n <= 0)
        return;
    if (n >= st->hist_count) {
        st->hist_count = 0;
    } else {
        memmove(st->hist, st->hist + (size_t)n * C, sizeof(double) * (size_t)(st->hist_count - n) * C);
        st->hist_count -= n;
    }
    st->pos -= n;
}

/* [frames] resampled frames into [dst], pulling WSOLA output; how many. */
static int resample_pull(NagStretch *st, double *dst, int frames, nag_pull_fn pull, void *opaque)
{
    const int C = st->channels;
    const int half = st->taps_half;
    const double c = st->cutoff;
    int produced = 0;
    while (produced < frames) {
        int idx = (int)floor(st->pos);
        int last_needed = idx + half; /* inclusive */
        if (last_needed >= st->hist_count) {
            if (st->ended && st->out_count == st->out_read && st->in_count == 0)
                break; /* nothing more will come */
            /* room: the history before the taps' reach is dropped */
            int keep_from = idx - half; /* the first tap's frame */
            if (keep_from > CHUNK)
                hist_drop(st, keep_from);
            int want = last_needed - st->hist_count + 1;
            if (want < CHUNK)
                want = CHUNK;
            if (want > st->hist_cap - st->hist_count)
                want = st->hist_cap - st->hist_count;
            if (want <= 0)
                break;
            int got = wsola_pull(st, st->hist + (size_t)st->hist_count * C, want, pull, opaque);
            if (got <= 0)
                break;
            st->hist_count += got;
            continue;
        }
        double frac = st->pos - idx;
        double acc[NAG_STRETCH_CHANNELS_MAX] = {0};
        double gain = 0;
        for (int k = -half + 1; k <= half; k++) {
            int j = idx + k;
            if (j < 0)
                continue;
            double x = k - frac;
            double w = c * sinc_at(st, c * x) * window_at(st, fabs(x) / half);
            gain += w;
            const double *src = st->hist + (size_t)j * C;
            for (int ch = 0; ch < C; ch++)
                acc[ch] += w * src[ch];
        }
        if (gain > 1e-9)
            for (int ch = 0; ch < C; ch++)
                acc[ch] /= gain;
        for (int ch = 0; ch < C; ch++)
            dst[(size_t)produced * C + ch] = acc[ch];
        produced++;
        st->pos += st->ratio;
    }
    return produced;
}

/* ------------------------------------------------------------------- entry */

int nag_stretch_pull(NagStretch *st, double *dst, int frames, nag_pull_fn pull, void *opaque)
{
    const int C = st->channels;
    if (frames <= 0)
        return 0;
    if (!st->active) {
        /* out of the way: what the stages still hold goes out first, in order, then the source itself */
        int produced = 0;
        int idx = (int)floor(st->pos);
        if (idx < 0)
            idx = 0;
        if (st->hist_count > idx) {
            int n = st->hist_count - idx;
            if (n > frames)
                n = frames;
            memcpy(dst, st->hist + (size_t)idx * C, sizeof(double) * (size_t)n * C);
            hist_drop(st, idx + n);
            st->pos = 0;
            produced += n;
        } else {
            st->hist_count = 0;
            st->pos = 0;
        }
        if (produced < frames && st->out_count > st->out_read) {
            int n = st->out_count - st->out_read;
            if (n > frames - produced)
                n = frames - produced;
            memcpy(dst + (size_t)produced * C, st->out + (size_t)st->out_read * C, sizeof(double) * (size_t)n * C);
            st->out_read += n;
            if (st->out_read == st->out_count)
                st->out_read = st->out_count = 0;
            produced += n;
        }
        if (produced < frames && st->mid_valid) {
            int n = st->overlap;
            if (n > frames - produced)
                return produced; /* next call */
            memcpy(dst + (size_t)produced * C, st->mid, sizeof(double) * (size_t)n * C);
            st->mid_valid = 0;
            produced += n;
        }
        if (produced < frames && st->in_count > 0) {
            int n = st->in_count;
            if (n > frames - produced)
                n = frames - produced;
            memcpy(dst + (size_t)produced * C, st->in, sizeof(double) * (size_t)n * C);
            in_consume(st, n);
            produced += n;
        }
        if (produced < frames) {
            int got = pull(opaque, dst + (size_t)produced * C, frames - produced);
            if (got > 0)
                produced += got;
        }
        return produced;
    }
    if (st->ratio == 1.0)
        return wsola_pull(st, dst, frames, pull, opaque);
    return resample_pull(st, dst, frames, pull, opaque);
}
