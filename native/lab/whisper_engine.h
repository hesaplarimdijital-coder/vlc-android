// S-709: where whisper runs on the device, for the subtitles from speech and the SP-05 benchmark.
#ifndef NAGMEDAR_WHISPER_ENGINE_H
#define NAGMEDAR_WHISPER_ENGINE_H

#include <string>

struct whisper_context;

/** What a transcription runs on: the GPU (by its name) or the processor, on how many threads. */
struct WhisperEngine {
    bool gpu = false;
    /** The GPU's name as its driver gives it; empty on the processor. */
    std::string device;
    int threads = 0;
    /** Flash attention, on wherever the backend has it. */
    bool flash_attn = false;
    /**
     * Why the GPU was asked for and the processor runs: "" (it runs, or it was not asked),
     * "none" (no GPU this build or this device can use), "busy" (another run has it), "init"
     * (the model could not be put on it), "failed" (it stopped in the middle of the work).
     */
    std::string fallback;
    /** What ggml or the driver said when the GPU failed. */
    std::string detail;
};

/**
 * The threads whisper gets: [asked] when above 0, else the performance cores, those whose top
 * frequency is at least 70 % of the fastest one's (cpufreq's cpuinfo_max_freq), at least 4 and at
 * most 8, never more than the cores this process may run on (ggml's threads wait for each other
 * by spinning: one thread too many for the cores makes it several times slower).
 */
int whisper_engine_threads(int asked);

/**
 * [model] loaded on the GPU when [gpu] asks for it and the device has one ggml's Vulkan backend
 * can use, else on the processor; null when the model cannot be read at all. [engine] says what
 * it runs on (its threads are left as they are).
 */
whisper_context *whisper_engine_open(const char *model, bool gpu, WhisperEngine &engine);

/** The GPU failed in the middle of the work ([why]): [ctx] freed and [model] loaded again on the processor. */
whisper_context *whisper_engine_fall_back(whisper_context *ctx, const char *model, WhisperEngine &engine, const char *why);

/** [ctx] freed, and the GPU free for the next run when [engine] had it. */
void whisper_engine_close(whisper_context *ctx, WhisperEngine &engine);

/** [engine] as JSON fields, without the braces: "gpu", "device", "threads", "flash_attn", "fallback", "fallback_detail". */
std::string whisper_engine_json(const WhisperEngine &engine);

#endif
