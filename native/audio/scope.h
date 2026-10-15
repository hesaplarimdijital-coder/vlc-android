/*
 * Nağmedar Ses Motoru: the visualisers' tap (S-611).
 *
 * The render writes what it plays, after the DSP chain and before the volume, into a ring of
 * float frames (the first two channels; a mono output on both). A reader on another thread
 * takes the last frames as the listener hears them (the sink's latency back from the newest)
 * and analyses them: a Hann-windowed FFT of both channels at once, the RMS of the window, the
 * largest sample since the previous read and a stretch of the waveform from a rising zero
 * crossing. Nothing is written while the tap is off; the render's cost with it on is a copy and
 * a running maximum per block.
 */
#ifndef NAGAUDIO_SCOPE_H
#define NAGAUDIO_SCOPE_H

#include "nagaudio.h"

#include <stdatomic.h>
#include <stdint.h>

/** Frames the tap keeps: room for the window and the latest sink latency at 768 kHz. */
#define NAG_SCOPE_CAPACITY (1 << 17)

typedef struct NagScopeTap {
    float *data;                /* NAG_SCOPE_CAPACITY frames, two channels interleaved */
    _Atomic uint64_t written;   /* frames written so far; the slot is this modulo the capacity */
    _Atomic double peak[2];     /* the largest |sample| since the last nag_scope_tap_take_peaks */
} NagScopeTap;

int nag_scope_tap_init(NagScopeTap *tap);
void nag_scope_tap_free(NagScopeTap *tap);

/** The render's side: [n] frames of [channels] doubles. Realtime safe. */
void nag_scope_tap_write(NagScopeTap *tap, const double *frames, int n, int channels);

/**
 * The [n] frames that end [delay] frames before the newest, into [dst] (two floats a frame);
 * frames before the first written are silence. [written] gets the frame counter.
 */
void nag_scope_tap_read(NagScopeTap *tap, float *dst, int n, uint64_t delay, uint64_t *written);

/** The largest |sample| of each channel since the last call, and starts over. */
void nag_scope_tap_take_peaks(NagScopeTap *tap, double peak[2]);

/** The FFT's length for [rate]: about 43 ms of audio, a power of two in NAG_SCOPE_FFT_MIN..MAX. */
int nag_scope_fft_size(int rate);

/** What the analysis keeps between two calls: the window and the twiddles for one length. */
typedef struct NagScopeAnalyser {
    int size;
    float *window;
    float *cos_table, *sin_table;
    float *re, *im;
} NagScopeAnalyser;

void nag_scope_analyser_free(NagScopeAnalyser *analyser);

/**
 * Analyses [frames] (two floats a frame, nag_scope_fft_size(rate) of them) into [out]: the
 * spectrum, the RMS and the waveform's [wave_points] points (at most NAG_SCOPE_WAVE_MAX); the
 * peaks and the counter are the caller's. -1 when memory runs out.
 */
int nag_scope_analyse(NagScopeAnalyser *analyser, const float *frames, int rate, int wave_points, NagScopeFrame *out);

/** 20·log10(x), NAG_SCOPE_FLOOR_DB for silence. */
double nag_scope_db(double linear);

#endif
