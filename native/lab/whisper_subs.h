// S-709: subtitles from a film's speech with whisper.cpp, on the device.
#ifndef NAGMEDAR_WHISPER_SUBS_H
#define NAGMEDAR_WHISPER_SUBS_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/** What a transcription tells as it goes; every call comes on the thread that runs it. */
typedef struct WsCallbacks {
    void *opaque;
    /** How far it is: [done] seconds of the film of [total] (0 when the film does not say). */
    void (*progress)(void *opaque, double done, double total);
    /** One line of speech, its times in milliseconds of the film. */
    void (*segment)(void *opaque, int64_t start_ms, int64_t end_ms, const char *text);
    /** The language whisper heard ("tr"), once, when it was asked to find it. */
    void (*language)(void *opaque, const char *code);
} WsCallbacks;

/**
 * Transcribes audio track [audio_track] of [input] (the n-th audio stream, 0 first; -1 for the
 * one FFmpeg finds best) with the ggml model at [model], in [language] ("tr", "en" or "auto"),
 * into English when [translate], on [threads] threads. The track is decoded and transcribed five
 * minutes at a time, so memory stays the same for a film of any length. [cancel] set to non-zero
 * stops it soon. Returns a JSON object: "error" ("" or "open", "no_audio", "decoder", "model",
 * "whisper", "cancelled"), "detail", "language", "audio_seconds", "segments". The caller frees it.
 */
char *whisper_subs_run(const char *model, const char *input, int audio_track, const char *language, int translate, int threads,
                       const WsCallbacks *callbacks, volatile int *cancel);

#ifdef __cplusplus
}
#endif

#endif
