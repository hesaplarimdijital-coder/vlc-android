// SP-05: whisper.cpp benchmark on a media file.
#ifndef NAGMEDAR_WHISPER_LAB_H
#define NAGMEDAR_WHISPER_LAB_H

#ifdef __cplusplus
extern "C" {
#endif

/** Progress: fraction 0..1 of the whole run and the stage ("decode", "transcribe"). */
typedef void (*wl_progress_fn)(void *opaque, double fraction, const char *stage);

/**
 * Decodes up to [max_seconds] (0 = all) of the first audio track of [input], transcribes it with
 * the ggml model at [model] in [language] ("tr", "en" … or "auto") on [threads] threads (0 = up to
 * 8 cores) and returns a JSON report with timings, the real-time factor, the text and an SRT.
 * The caller frees the string.
 */
char *whisper_lab_run(const char *model, const char *input, const char *language, double max_seconds, int threads,
                      wl_progress_fn progress, void *opaque, volatile int *cancel);

#ifdef __cplusplus
}
#endif

#endif
