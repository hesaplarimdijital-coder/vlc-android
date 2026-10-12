/*
 * Nağmedar Ses Motoru: the DSP chain (S-906).
 *
 * Everything runs in doubles. The EQs are cascades of RBJ biquads (transposed direct form
 * II); each band keeps its own memory in a fixed slot, so a band switched on or off, or a
 * gain moved, changes only the coefficients the next block uses. The limiter is a lookahead
 * peak limiter with a hard guarantee: the gain applied to a frame is the box-blurred sliding
 * minimum of the gains asked for over the lookahead window that frame belongs to, so no sample
 * ever passes the threshold, and the gain moves smoothly; a one-pole release lets it recover.
 */
#include "dsp.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

/* half-octave bandwidth: Q = sqrt(2^BW) / (2^BW - 1) with BW = 0.5 */
#define GRAPHIC_Q 2.871
#define GAIN_DB_MAX 30.0
#define Q_MIN 0.05
#define Q_MAX 50.0
#define RESPONSE_POINTS 120
/* the limiter's blur sum is re-added exactly this often, against drift */
#define BLUR_RECOUNT_FRAMES 65536

const double nag_graphic_frequencies[NAG_GRAPHIC_BANDS] = {
    25, 35.5, 50, 71, 100, 140, 200, 280, 400, 560, 800, 1120, 1600, 2240, 3150, 4500, 6300, 9000, 12500, 18000,
};

void nag_dsp_params_defaults(NagDspParams *p)
{
    memset(p, 0, sizeof *p);
    for (int i = 0; i < NAG_EQ_BANDS; i++) {
        p->parametric[i].type = NAG_EQ_PEAK;
        p->parametric[i].freq = 1000;
        p->parametric[i].q = 1.0;
        p->profile[i].type = NAG_EQ_PEAK;
        p->profile[i].freq = 1000;
        p->profile[i].q = 1.0;
    }
    p->limiter_threshold_db = -1.0;
    p->limiter_release_ms = 100;
    p->limiter_lookahead_ms = 5;
}

static int band_does_something(const NagEqBand *b)
{
    if (!b->enabled || !(b->freq > 0))
        return 0;
    if (b->type == NAG_EQ_LOW_PASS || b->type == NAG_EQ_HIGH_PASS)
        return 1;
    return b->gain_db != 0;
}

int nag_dsp_params_identity(const NagDspParams *p)
{
    if (p->bypass)
        return 1;
    if (p->preamp_db != 0 || p->limiter_enabled)
        return 0;
    if (p->graphic_enabled)
        for (int i = 0; i < NAG_GRAPHIC_BANDS; i++)
            if (p->graphic_db[i] != 0)
                return 0;
    if (p->parametric_enabled)
        for (int i = 0; i < NAG_EQ_BANDS; i++)
            if (band_does_something(&p->parametric[i]))
                return 0;
    if (p->profile_enabled) {
        if (p->profile_preamp_db != 0)
            return 0;
        for (int i = 0; i < NAG_EQ_BANDS; i++)
            if (band_does_something(&p->profile[i]))
                return 0;
    }
    return 1;
}

/* ------------------------------------------------------------------ biquads */

int nag_dsp_biquad(NagBiquad *out, const NagEqBand *band, int rate)
{
    if (!band_does_something(band) || rate <= 0)
        return 0;
    double freq = band->freq;
    if (freq >= rate * 0.49)
        return 0; /* above the output's reach */
    if (freq < 1.0)
        freq = 1.0;
    double gain_db = band->gain_db;
    if (gain_db > GAIN_DB_MAX)
        gain_db = GAIN_DB_MAX;
    if (gain_db < -GAIN_DB_MAX)
        gain_db = -GAIN_DB_MAX;
    double q = band->q;
    if (!(q >= Q_MIN))
        q = Q_MIN;
    if (q > Q_MAX)
        q = Q_MAX;
    const double A = pow(10.0, gain_db / 40.0);
    const double w0 = 2.0 * M_PI * freq / rate;
    const double cw = cos(w0), sw = sin(w0);
    const double alpha = sw / (2.0 * q);
    const double sqA2 = 2.0 * sqrt(A) * alpha;
    double b0, b1, b2, a0, a1, a2;
    switch (band->type) {
    case NAG_EQ_PEAK:
        b0 = 1 + alpha * A;
        b1 = -2 * cw;
        b2 = 1 - alpha * A;
        a0 = 1 + alpha / A;
        a1 = -2 * cw;
        a2 = 1 - alpha / A;
        break;
    case NAG_EQ_LOW_SHELF:
        b0 = A * ((A + 1) - (A - 1) * cw + sqA2);
        b1 = 2 * A * ((A - 1) - (A + 1) * cw);
        b2 = A * ((A + 1) - (A - 1) * cw - sqA2);
        a0 = (A + 1) + (A - 1) * cw + sqA2;
        a1 = -2 * ((A - 1) + (A + 1) * cw);
        a2 = (A + 1) + (A - 1) * cw - sqA2;
        break;
    case NAG_EQ_HIGH_SHELF:
        b0 = A * ((A + 1) + (A - 1) * cw + sqA2);
        b1 = -2 * A * ((A - 1) + (A + 1) * cw);
        b2 = A * ((A + 1) + (A - 1) * cw - sqA2);
        a0 = (A + 1) - (A - 1) * cw + sqA2;
        a1 = 2 * ((A - 1) - (A + 1) * cw);
        a2 = (A + 1) - (A - 1) * cw - sqA2;
        break;
    case NAG_EQ_LOW_PASS:
        b0 = (1 - cw) / 2;
        b1 = 1 - cw;
        b2 = (1 - cw) / 2;
        a0 = 1 + alpha;
        a1 = -2 * cw;
        a2 = 1 - alpha;
        break;
    case NAG_EQ_HIGH_PASS:
        b0 = (1 + cw) / 2;
        b1 = -(1 + cw);
        b2 = (1 + cw) / 2;
        a0 = 1 + alpha;
        a1 = -2 * cw;
        a2 = 1 - alpha;
        break;
    default:
        return 0;
    }
    out->b0 = b0 / a0;
    out->b1 = b1 / a0;
    out->b2 = b2 / a0;
    out->a1 = a1 / a0;
    out->a2 = a2 / a0;
    return 1;
}

static double biquad_power(const NagBiquad *q, double w)
{
    double c1 = cos(w), s1 = sin(w), c2 = cos(2 * w), s2 = sin(2 * w);
    double nr = q->b0 + q->b1 * c1 + q->b2 * c2, ni = q->b1 * s1 + q->b2 * s2;
    double dr = 1 + q->a1 * c1 + q->a2 * c2, di = q->a1 * s1 + q->a2 * s2;
    double d = dr * dr + di * di;
    if (d <= 0)
        return 1e-30;
    return (nr * nr + ni * ni) / d;
}

double nag_dsp_response_db(const NagDsp *d, double hz)
{
    if (d->rate <= 0)
        return 0;
    double w = 2.0 * M_PI * hz / d->rate;
    double db = 0;
    for (int k = 0; k < d->n_biquads; k++)
        db += 10.0 * log10(biquad_power(&d->coef[k], w));
    return db;
}

/* ------------------------------------------------------------------ limiter */

static int limiter_alloc(NagLimiter *l, int capacity_frames, int channels)
{
    l->delay = calloc((size_t)capacity_frames * channels, sizeof *l->delay);
    l->min_index = calloc((size_t)capacity_frames + 1, sizeof *l->min_index);
    l->min_value = calloc((size_t)capacity_frames + 1, sizeof *l->min_value);
    l->blur = calloc((size_t)capacity_frames, sizeof *l->blur);
    return l->delay && l->min_index && l->min_value && l->blur ? 0 : -1;
}

static void limiter_free(NagLimiter *l)
{
    free(l->delay);
    free(l->min_index);
    free(l->min_value);
    free(l->blur);
    memset(l, 0, sizeof *l);
}

static void limiter_reset(NagLimiter *l, int channels)
{
    int L = l->frames > 0 ? l->frames : 1;
    l->envelope = 1.0;
    memset(l->delay, 0, (size_t)L * channels * sizeof *l->delay);
    l->delay_pos = 0;
    l->min_head = 0;
    l->min_count = 0;
    l->sample_index = 0;
    for (int i = 0; i < L; i++)
        l->blur[i] = 1.0;
    l->blur_pos = 0;
    l->blur_sum = L;
    l->blur_since_recount = 0;
}

static void limiter_setup(NagLimiter *l, const NagDspParams *p, int rate, int capacity_frames, int channels)
{
    double ms = p->limiter_lookahead_ms;
    if (!(ms >= 0))
        ms = 0;
    if (ms > NAG_DSP_LOOKAHEAD_MAX_MS)
        ms = NAG_DSP_LOOKAHEAD_MAX_MS;
    int frames = (int)lrint(ms * rate / 1000.0);
    if (frames < 1)
        frames = 1;
    if (frames > capacity_frames)
        frames = capacity_frames;
    if (frames != l->frames) {
        /* the lines change length: they start over (a setting changed, not a slider) */
        l->frames = frames;
        if (l->delay)
            limiter_reset(l, channels);
    }
    double threshold_db = p->limiter_threshold_db;
    if (threshold_db > 0)
        threshold_db = 0;
    if (threshold_db < -40)
        threshold_db = -40;
    l->threshold = pow(10.0, threshold_db / 20.0);
    double release_ms = p->limiter_release_ms >= 1 ? p->limiter_release_ms : 1;
    l->release = 1.0 - exp(-1000.0 / (release_ms * rate));
}

static inline double limiter_frame(NagLimiter *l, double *x, int channels, int capacity)
{
    const int L = l->frames;
    const int cap = capacity + 1; /* the minimum queue's ring */
    double peak = 0;
    for (int c = 0; c < channels; c++) {
        double a = fabs(x[c]);
        if (a > peak)
            peak = a;
    }
    /*
     * A hair under the quotient (1e-11: -220 dB), more than the rounding the blur's running sum
     * of up to 7680 gains can gather, so peak * gain never passes the threshold.
     */
    double asked = peak > l->threshold ? l->threshold / peak * (1.0 - 1e-11) : 1.0;
    /* the envelope recovers towards 1 at the release rate and drops at once */
    double env = l->envelope + (1.0 - l->envelope) * l->release;
    if (asked < env)
        env = asked;
    l->envelope = env;
    /* the sliding minimum over the last L envelopes */
    int64_t n = l->sample_index++;
    while (l->min_count > 0) {
        int tail = (l->min_head + l->min_count - 1) % cap;
        if (l->min_value[tail] >= env)
            l->min_count--;
        else
            break;
    }
    int tail = (l->min_head + l->min_count) % cap;
    l->min_index[tail] = n;
    l->min_value[tail] = env;
    l->min_count++;
    while (l->min_index[l->min_head] <= n - L) {
        l->min_head = (l->min_head + 1) % cap;
        l->min_count--;
    }
    double m = l->min_value[l->min_head];
    /* the box blur over L minimums: the gain the delayed frame gets */
    l->blur_sum += m - l->blur[l->blur_pos];
    l->blur[l->blur_pos] = m;
    if (++l->blur_pos == L)
        l->blur_pos = 0;
    if (++l->blur_since_recount >= BLUR_RECOUNT_FRAMES) {
        double sum = 0;
        for (int i = 0; i < L; i++)
            sum += l->blur[i];
        l->blur_sum = sum;
        l->blur_since_recount = 0;
    }
    double gain = l->blur_sum / L;
    if (gain > 1.0)
        gain = 1.0;
    if (gain < 0.0)
        gain = 0.0;
    /* the frame L - 1 frames back goes out with that gain; this one waits */
    if (L > 1) {
        double *d = l->delay + (size_t)l->delay_pos * channels;
        for (int c = 0; c < channels; c++) {
            double held = d[c];
            d[c] = x[c];
            x[c] = held * gain;
        }
        if (++l->delay_pos == L - 1)
            l->delay_pos = 0;
    } else {
        for (int c = 0; c < channels; c++)
            x[c] *= gain;
    }
    return gain;
}

/* -------------------------------------------------------------------- chain */

int nag_dsp_init(NagDsp *d, int channels)
{
    memset(d, 0, sizeof *d);
    if (channels < 1 || channels > NAG_DSP_CHANNELS_MAX)
        return -1;
    d->channels = channels;
    d->capacity_frames = (int)(NAG_DSP_LOOKAHEAD_MAX_MS * NAG_DSP_RATE_MAX / 1000.0 + 1);
    d->gain = 1.0;
    if (limiter_alloc(&d->limiter, d->capacity_frames, channels))
        return -1;
    d->limiter.frames = 1;
    limiter_reset(&d->limiter, channels);
    return 0;
}

void nag_dsp_free(NagDsp *d)
{
    limiter_free(&d->limiter);
}

void nag_dsp_reset(NagDsp *d)
{
    memset(d->state, 0, sizeof d->state);
    if (d->limiter.delay)
        limiter_reset(&d->limiter, d->channels);
}

void nag_dsp_probe(NagDsp *d, int channels)
{
    memset(d, 0, sizeof *d);
    d->channels = channels;
    d->capacity_frames = (int)(NAG_DSP_LOOKAHEAD_MAX_MS * NAG_DSP_RATE_MAX / 1000.0 + 1);
    d->gain = 1.0;
}

static void add_stage(NagDsp *d, const char *name, int count, double db)
{
    if (d->n_stages < (int)(sizeof d->stages / sizeof d->stages[0])) {
        d->stages[d->n_stages].name = name;
        d->stages[d->n_stages].count = count;
        d->stages[d->n_stages].db = db;
        d->n_stages++;
    }
}

static int add_bands(NagDsp *d, const NagEqBand *bands, int count, int first_slot, int rate)
{
    int n = 0;
    for (int i = 0; i < count && d->n_biquads < NAG_DSP_BIQUADS_MAX; i++) {
        if (nag_dsp_biquad(&d->coef[d->n_biquads], &bands[i], rate)) {
            d->slot[d->n_biquads] = first_slot + i;
            d->n_biquads++;
            n++;
        }
    }
    return n;
}

void nag_dsp_compile(NagDsp *d, const NagDspParams *p, int rate)
{
    d->params = p;
    d->rate = rate;
    d->n_biquads = 0;
    d->n_stages = 0;
    d->gain = 1.0;
    d->auto_preamp_db = 0;
    d->active = 0;
    d->limiter_on = 0;
    if (!p || rate <= 0)
        return;
    if (p->bypass) {
        add_stage(d, "bypass", 0, 0);
        return;
    }
    double db = 0;
    if (p->preamp_db != 0) {
        db += p->preamp_db;
        add_stage(d, "preamp", 0, p->preamp_db);
    }
    if (p->graphic_enabled) {
        NagEqBand bands[NAG_GRAPHIC_BANDS];
        for (int i = 0; i < NAG_GRAPHIC_BANDS; i++) {
            bands[i].enabled = 1;
            bands[i].type = NAG_EQ_PEAK;
            bands[i].freq = nag_graphic_frequencies[i];
            bands[i].gain_db = p->graphic_db[i];
            bands[i].q = GRAPHIC_Q;
        }
        int n = add_bands(d, bands, NAG_GRAPHIC_BANDS, 0, rate);
        if (n)
            add_stage(d, "graphic_eq", n, 0);
    }
    if (p->parametric_enabled) {
        int n = add_bands(d, p->parametric, NAG_EQ_BANDS, NAG_GRAPHIC_BANDS, rate);
        if (n)
            add_stage(d, "parametric_eq", n, 0);
    }
    if (p->profile_enabled) {
        int n = add_bands(d, p->profile, NAG_EQ_BANDS, NAG_GRAPHIC_BANDS + NAG_EQ_BANDS, rate);
        if (n || p->profile_preamp_db != 0) {
            db += p->profile_preamp_db;
            add_stage(d, "profile", n, p->profile_preamp_db);
        }
    }
    if (p->auto_preamp && d->n_biquads > 0) {
        /* the EQ's largest gain, over a log grid and at every band's centre, is taken back */
        double top = rate * 0.49 < 20000 ? rate * 0.49 : 20000;
        double max_db = 0;
        for (int i = 0; i <= RESPONSE_POINTS; i++) {
            double hz = 20.0 * pow(top / 20.0, (double)i / RESPONSE_POINTS);
            double r = nag_dsp_response_db(d, hz);
            if (r > max_db)
                max_db = r;
        }
        const NagEqBand *sets[2] = {p->parametric_enabled ? p->parametric : NULL, p->profile_enabled ? p->profile : NULL};
        for (int s = 0; s < 2; s++) {
            if (!sets[s])
                continue;
            for (int i = 0; i < NAG_EQ_BANDS; i++) {
                if (!band_does_something(&sets[s][i]) || sets[s][i].freq >= rate * 0.49)
                    continue;
                double r = nag_dsp_response_db(d, sets[s][i].freq);
                if (r > max_db)
                    max_db = r;
            }
        }
        if (p->graphic_enabled) {
            for (int i = 0; i < NAG_GRAPHIC_BANDS; i++) {
                if (p->graphic_db[i] == 0 || nag_graphic_frequencies[i] >= rate * 0.49)
                    continue;
                double r = nag_dsp_response_db(d, nag_graphic_frequencies[i]);
                if (r > max_db)
                    max_db = r;
            }
        }
        if (max_db > 0) {
            d->auto_preamp_db = -max_db;
            db -= max_db;
            add_stage(d, "auto_preamp", 0, -max_db);
        }
    }
    if (db != 0)
        d->gain = pow(10.0, db / 20.0);
    if (p->limiter_enabled) {
        limiter_setup(&d->limiter, p, rate, d->capacity_frames, d->channels);
        d->limiter_on = 1;
        add_stage(d, "limiter", 0, p->limiter_threshold_db > 0 ? 0 : p->limiter_threshold_db);
    }
    d->active = d->n_biquads > 0 || d->gain != 1.0 || d->limiter_on;
}

void nag_dsp_process(NagDsp *d, double *frames, int n, NagDspBlockMeter *meter)
{
    const int C = d->channels;
    const size_t samples = (size_t)n * C;
    meter->peak = 0;
    meter->min_gain = 1.0;
    meter->clipped = 0;
    if (!d->active || n <= 0)
        return;
    if (d->gain != 1.0)
        for (size_t i = 0; i < samples; i++)
            frames[i] *= d->gain;
    for (int k = 0; k < d->n_biquads; k++) {
        const NagBiquad q = d->coef[k];
        NagBiquadState *st = d->state[d->slot[k]];
        for (int c = 0; c < C; c++) {
            double s1 = st[c].s1, s2 = st[c].s2;
            double *x = frames + c;
            for (int i = 0; i < n; i++, x += C) {
                double in = *x;
                double out = q.b0 * in + s1;
                s1 = q.b1 * in - q.a1 * out + s2;
                s2 = q.b2 * in - q.a2 * out;
                *x = out;
            }
            st[c].s1 = s1;
            st[c].s2 = s2;
        }
    }
    double peak = 0;
    for (size_t i = 0; i < samples; i++) {
        double a = fabs(frames[i]);
        if (a > peak)
            peak = a;
    }
    meter->peak = peak;
    if (d->limiter_on) {
        double min_gain = 1.0;
        for (int i = 0; i < n; i++) {
            double g = limiter_frame(&d->limiter, frames + (size_t)i * C, C, d->capacity_frames);
            if (g < min_gain)
                min_gain = g;
        }
        meter->min_gain = min_gain;
    }
    int64_t clipped = 0;
    for (size_t i = 0; i < samples; i++)
        if (frames[i] > 1.0 || frames[i] < -1.0)
            clipped++;
    meter->clipped = clipped;
}
