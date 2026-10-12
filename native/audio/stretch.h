/*
 * Nağmedar Ses Motoru: tempo and pitch (S-909), as the render owns them.
 *
 * Tempo without a pitch change is WSOLA (waveform-similarity overlap-add, as SoundTouch does
 * it): the input is cut into sequences, each one overlap-added onto the tail of the one before
 * at the offset, within a seek window, where the waveforms match best. A pitch change is a
 * tempo change undone by resampling: stretched by 2^(semitones/12), then resampled by the same
 * ratio with a Kaiser-windowed sinc. Both stages pull from the stage before them, so the engine
 * pulls output frames and the voices give input frames at their own pace; everything is
 * allocated once, for the longest sequence at the highest rate it takes.
 */
#ifndef NAGAUDIO_STRETCH_H
#define NAGAUDIO_STRETCH_H

#include <stdint.h>

#define NAG_STRETCH_RATE_MAX 192000
#define NAG_STRETCH_CHANNELS_MAX 8
#define NAG_TEMPO_MIN 0.25
#define NAG_TEMPO_MAX 4.0
#define NAG_PITCH_MAX_SEMITONES 24.0

/** Frames into [dst]: how many (fewer when the source is behind), 0 for none now, -1 when it ended. */
typedef int (*nag_pull_fn)(void *opaque, double *dst, int frames);

typedef struct NagStretch {
    int channels;
    int rate;
    double tempo;          /* the output's pace: 2 = twice as fast */
    double pitch;          /* semitones */
    int active;            /* tempo != 1 or pitch != 0 */
    /* WSOLA, in frames at [rate] */
    int seq, seek, overlap;
    double skip;           /* input frames per sequence, nominal */
    double skip_fract;
    double wsola_tempo;    /* the tempo WSOLA applies (the pitch's share undone by the resampler) */
    double *in;            /* input frames waiting */
    int in_cap, in_count;
    double *mid;           /* the last overlap of the sequence before, to add the next onto */
    int mid_valid;
    double *out;           /* WSOLA output waiting for the resampler or the caller */
    int out_cap, out_count, out_read;
    int ended;             /* the source ended: what is left goes out as it is */
    /* the resampler */
    double ratio;          /* input frames per output frame (1: none) */
    double *hist;          /* resampler input: history for the taps + what was pulled */
    int hist_cap, hist_count;
    double pos;            /* the next output frame's position in [hist] */
    int taps_half;         /* taps each side at this ratio */
    double cutoff;
    /* tables, built once */
    double *sinc_table;    /* sinc(x) for x in [0, taps_half_max] at TABLE_PHASES per unit */
    double *window_table;  /* Kaiser window for |x| / half in [0, 1] */
    int table_len;
} NagStretch;

int nag_stretch_init(NagStretch *st, int channels);
void nag_stretch_free(NagStretch *st);
/** New settings or a new rate; a change of lengths starts the buffers over. No allocation. */
void nag_stretch_configure(NagStretch *st, int rate, double tempo, double pitch_semitones);
/** Forgets what is buffered (a seek, a new track). */
void nag_stretch_reset(NagStretch *st);
/**
 * [frames] output frames into [dst], pulling input through [pull]; how many were produced (fewer
 * when the input is behind or ended).
 */
int nag_stretch_pull(NagStretch *st, double *dst, int frames, nag_pull_fn pull, void *opaque);
/** The frames of input waiting inside, as the output's latency, roughly. */
int nag_stretch_latency(const NagStretch *st);

#endif
