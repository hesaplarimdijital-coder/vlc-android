/*
 * Nağmedar Ses Motoru: the DSP chain (S-906), as the render owns it.
 *
 * The chain is compiled from NagDspParams for one rate and one channel count, without
 * allocating: biquad coefficients for the active EQ bands, one gain for the preamps, and the
 * limiter's lengths. Its memory (the filters' states, the limiter's delay lines) is allocated
 * once, for the longest lookahead at the highest rate, and survives a recompile, so changing
 * a band while playing does not click.
 */
#ifndef NAGAUDIO_DSP_H
#define NAGAUDIO_DSP_H

#include "nagaudio.h"

#include <stdint.h>

#define NAG_DSP_CHANNELS_MAX 8
#define NAG_DSP_BIQUADS_MAX (NAG_GRAPHIC_BANDS + 2 * NAG_EQ_BANDS)

typedef struct NagBiquad {
    double b0, b1, b2, a1, a2; /* a0 normalised away */
} NagBiquad;

typedef struct NagBiquadState {
    double s1, s2; /* transposed direct form II */
} NagBiquadState;

typedef struct NagLimiter {
    int frames;        /* the lookahead, L >= 1; the delay is L - 1 frames */
    double threshold;  /* linear */
    double release;    /* the one-pole coefficient of the gain's recovery */
    double envelope;   /* the gain the last frame asked for, recovering */
    /* the signal, delayed */
    double *delay;     /* capacity_frames * channels */
    int delay_pos;
    /* the sliding minimum of the envelope over L frames: a monotonic queue of (index, value) */
    int64_t *min_index;
    double *min_value;
    int min_head, min_count;
    int64_t sample_index;
    /* the box blur of the minimum over L frames */
    double *blur;
    int blur_pos;
    double blur_sum;
    int64_t blur_since_recount;
} NagLimiter;

typedef struct NagDspStage {
    const char *name;
    int count;   /* bands or 0 */
    double db;   /* a gain stage's dB */
} NagDspStage;

typedef struct NagDsp {
    int channels;
    int capacity_frames; /* the longest lookahead in frames at NAG_DSP_RATE_MAX */
    /* compiled */
    int rate;
    const NagDspParams *params; /* what the compile came from (not owned) */
    int active;                 /* the chain changes the samples */
    double gain;                /* every preamp, linear */
    double auto_preamp_db;      /* what auto_preamp took back */
    int n_biquads;
    NagBiquad coef[NAG_DSP_BIQUADS_MAX];
    int slot[NAG_DSP_BIQUADS_MAX]; /* each compiled biquad's band: its memory stays with the band */
    int limiter_on;
    /* memory */
    NagBiquadState state[NAG_DSP_BIQUADS_MAX][NAG_DSP_CHANNELS_MAX];
    NagLimiter limiter;
    /* the stages, for the signal path */
    NagDspStage stages[8];
    int n_stages;
} NagDsp;

/** The meter of one process() call. */
typedef struct NagDspBlockMeter {
    double peak;      /* before the limiter */
    double min_gain;  /* the limiter's smallest gain, 1 when it did nothing */
    int64_t clipped;  /* samples beyond full scale at the end */
} NagDspBlockMeter;

int nag_dsp_init(NagDsp *dsp, int channels);
void nag_dsp_free(NagDsp *dsp);
/** A chain without memory, for compiling a description (the stages, the auto preamp); never processes. */
void nag_dsp_probe(NagDsp *dsp, int channels);
/** Clears the filters' memory and the limiter's delay (a new track, a seek). */
void nag_dsp_reset(NagDsp *dsp);
/** Compiles [params] for [rate]; no allocation. NULL params: the identity. */
void nag_dsp_compile(NagDsp *dsp, const NagDspParams *params, int rate);
/** [frames] interleaved frames in place through the compiled chain. */
void nag_dsp_process(NagDsp *dsp, double *frames, int n, NagDspBlockMeter *meter);
/** The biquad's coefficients for a band at [rate]; 0 when the band does nothing (or is out of range). */
int nag_dsp_biquad(NagBiquad *out, const NagEqBand *band, int rate);
/** |H| of the compiled EQ (without the gain stage) at [hz], in dB. */
double nag_dsp_response_db(const NagDsp *dsp, double hz);

#endif
