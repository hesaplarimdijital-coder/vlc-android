// S-709: where whisper runs on the device.
//
// The Android build carries ggml's Vulkan backend (build.sh android): a job that asks for the GPU
// registers it here, once, and whisper puts the model on the first GPU it finds, with flash
// attention where the GPU's backend has it. ggml's backend registry does not start Vulkan by
// itself: the linker sends its call to ggml_backend_vk_reg to the wrapper below (--wrap), which
// answers nothing until a job asked for the GPU. A job on the processor alone never touches the
// GPU's driver, and a device without Vulkan 1.2 is never asked. Whatever the GPU fails at (no
// device, the model not going onto it, an error in the middle of the work) the processor takes
// over, and the report says so.
#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include "whisper_engine.h"

#include <ggml-backend.h>
#include <ggml.h>
#include <whisper.h>

#include <sched.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <cstdio>
#include <cstring>
#include <exception>
#include <mutex>
#include <vector>

#ifdef __ANDROID__
#include <android/log.h>
#endif

#ifdef NAGMEDAR_VULKAN
extern "C" {
/** vulkan_loader.c: 1 when the system's Vulkan loader is there and speaks Vulkan 1.2, else why not in [why]. */
int nagmedar_vulkan_load(char *why, size_t size);
ggml_backend_reg_t __real_ggml_backend_vk_reg(void);
ggml_backend_reg_t __wrap_ggml_backend_vk_reg(void);
}
#endif

namespace {

/** Registering a backend and loading a model: one at a time in the process (ggml's registry is not locked). */
std::mutex g_lock;

/** A run holds the GPU: a second one at the same time (the Lab's beside a film's) goes to the processor. */
std::atomic<bool> g_gpu_busy{false};

/** The last error ggml or whisper logged on this thread, for the report. */
thread_local std::string t_last_error;
thread_local ggml_log_level t_last_level = GGML_LOG_LEVEL_NONE;

/** whisper's and ggml's warnings and errors to logcat (stderr off Android); their notes stay out. */
void on_log(ggml_log_level level, const char *text, void *)
{
    if (level == GGML_LOG_LEVEL_CONT)
        level = t_last_level;
    t_last_level = level;
    if (level < GGML_LOG_LEVEL_WARN || !text)
        return;
    if (level == GGML_LOG_LEVEL_ERROR) {
        std::string line(text);
        while (!line.empty() && (line.back() == '\n' || line.back() == ' '))
            line.pop_back();
        if (!line.empty())
            t_last_error = line.substr(0, 300);
    }
#ifdef __ANDROID__
    __android_log_write(level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_WARN, "nagmedar-whisper", text);
#else
    fputs(text, stderr);
#endif
}

void quiet_logs()
{
    static std::once_flag once;
    std::call_once(once, [] {
        whisper_log_set(on_log, nullptr);
        ggml_log_set(on_log, nullptr);
    });
}

/** The model with [params]; null (and why in [detail]) when it cannot be loaded so, an exception included. */
whisper_context *load(const char *model, const whisper_context_params &params, std::string &detail)
{
    t_last_error.clear();
    whisper_context *ctx = nullptr;
    try {
        ctx = whisper_init_from_file_with_params(model, params);
    } catch (const std::exception &e) {
        detail = e.what();
        return nullptr;
    } catch (...) {
        detail = "unknown exception";
        return nullptr;
    }
    if (!ctx)
        detail = t_last_error;
    return ctx;
}

whisper_context *load_on_cpu(const char *model, WhisperEngine &engine)
{
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    // measured on the processor too: the encoder's attention a fifth quicker
    params.flash_attn = true;
    engine.gpu = false;
    engine.device.clear();
    engine.flash_attn = params.flash_attn;
    std::string ignored;
    return load(model, params, ignored);
}

#ifdef NAGMEDAR_VULKAN
std::atomic<bool> g_vulkan_on{false};

/** ggml's Vulkan backend in the registry, registered on the first ask; false (why in [why]) when the device has no Vulkan 1.2 or no driver for it. */
bool vulkan_registered(std::string &why)
{
    static int state = 0;
    static std::string reason;
    if (state == 0) {
        char buf[160] = "";
        ggml_backend_reg_t reg = nullptr;
        if (nagmedar_vulkan_load(buf, sizeof(buf))) {
            g_vulkan_on = true;
            t_last_error.clear();
            try {
                reg = __real_ggml_backend_vk_reg();
            } catch (...) {
                reg = nullptr;
            }
            if (!reg) {
                g_vulkan_on = false;
                snprintf(buf, sizeof(buf), "%s", t_last_error.empty() ? "Vulkan did not start" : t_last_error.c_str());
            }
        }
        if (reg) {
            ggml_backend_register(reg);
            state = 1;
        } else {
            state = -1;
            reason = buf;
        }
    }
    why = reason;
    return state == 1;
}

/** Whether [dev]'s backend has flash attention for whisper's heads (64 wide, F16 keys and values). */
bool flash_attn_on(ggml_backend_dev_t dev)
{
    ggml_init_params params = {8 * ggml_tensor_overhead(), nullptr, true};
    ggml_context *meta = ggml_init(params);
    if (!meta)
        return false;
    ggml_tensor *q = ggml_new_tensor_3d(meta, GGML_TYPE_F32, 64, 1500, 8);
    ggml_tensor *k = ggml_new_tensor_3d(meta, GGML_TYPE_F16, 64, 1500, 8);
    ggml_tensor *v = ggml_new_tensor_3d(meta, GGML_TYPE_F16, 64, 1500, 8);
    ggml_tensor *fa = ggml_flash_attn_ext(meta, q, k, v, nullptr, 0.125f, 0.0f, 0.0f);
    bool on = false;
    try {
        on = ggml_backend_dev_supports_op(dev, fa);
    } catch (...) {
        on = false;
    }
    ggml_free(meta);
    return on;
}
#endif

/** The GPU whisper puts the model on (the registry's first, as whisper picks it), started once to see that it works; null and why in [engine] when there is none. */
ggml_backend_dev_t usable_gpu(WhisperEngine &engine)
{
#ifdef NAGMEDAR_VULKAN
    std::string why;
    if (!vulkan_registered(why)) {
        engine.fallback = "none";
        engine.detail = why;
        return nullptr;
    }
    for (size_t i = 0; i < ggml_backend_dev_count(); i++) {
        ggml_backend_dev_t dev = ggml_backend_dev_get(i);
        enum ggml_backend_dev_type type = ggml_backend_dev_type(dev);
        if (type != GGML_BACKEND_DEVICE_TYPE_GPU && type != GGML_BACKEND_DEVICE_TYPE_IGPU)
            continue;
        ggml_backend_t probe = nullptr;
        t_last_error.clear();
        try {
            probe = ggml_backend_dev_init(dev, nullptr);
        } catch (const std::exception &e) {
            engine.detail = e.what();
        } catch (...) {
            engine.detail = "unknown exception";
        }
        if (!probe) {
            engine.fallback = "init";
            if (engine.detail.empty())
                engine.detail = t_last_error;
            return nullptr;
        }
        ggml_backend_free(probe);
        return dev;
    }
    engine.fallback = "none";
    engine.detail = "no GPU among the Vulkan devices";
    return nullptr;
#else
    engine.fallback = "none";
    engine.detail = "built without a GPU backend";
    return nullptr;
#endif
}

void free_context(whisper_context *ctx)
{
    try {
        whisper_free(ctx);
    } catch (...) {
        // a lost device may throw on its way out; the processor's context is all that matters now
    }
}

std::string json_escape(const std::string &s)
{
    std::string out;
    for (unsigned char c : s) {
        if (c == '"' || c == '\\') {
            out += '\\';
            out += static_cast<char>(c);
        } else if (c < 0x20) {
            char buf[8];
            snprintf(buf, sizeof(buf), "\\u%04x", c);
            out += buf;
        } else {
            out += static_cast<char>(c);
        }
    }
    return out;
}

} // namespace

#ifdef NAGMEDAR_VULKAN
/** The registry's own call, sent here by the linker: Vulkan only once a job asked for the GPU. */
extern "C" ggml_backend_reg_t __wrap_ggml_backend_vk_reg(void)
{
    return g_vulkan_on ? __real_ggml_backend_vk_reg() : nullptr;
}
#endif

int whisper_engine_threads(int asked)
{
    if (asked > 0)
        return asked;
    long online = std::max(1L, sysconf(_SC_NPROCESSORS_ONLN));
    long all = std::max(online, sysconf(_SC_NPROCESSORS_CONF));
    long allowed = online;
    cpu_set_t set;
    CPU_ZERO(&set);
    if (sched_getaffinity(0, sizeof(set), &set) == 0)
        allowed = std::max(1, CPU_COUNT(&set));
    std::vector<long> speeds;
    for (long cpu = 0; cpu < all; cpu++) {
        char path[96];
        snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%ld/cpufreq/cpuinfo_max_freq", cpu);
        FILE *f = fopen(path, "r");
        if (!f)
            continue;
        long khz = 0;
        if (fscanf(f, "%ld", &khz) == 1 && khz > 0)
            speeds.push_back(khz);
        fclose(f);
    }
    long fast = online;
    if (!speeds.empty()) {
        long top = *std::max_element(speeds.begin(), speeds.end());
        fast = std::count_if(speeds.begin(), speeds.end(), [top](long khz) { return khz * 10 >= top * 7; });
    }
    long cores = std::min(online, allowed);
    return static_cast<int>(std::clamp(fast, std::min(4L, cores), std::min(8L, cores)));
}

whisper_context *whisper_engine_open(const char *model, bool gpu, WhisperEngine &engine)
{
    quiet_logs();
    std::lock_guard<std::mutex> lock(g_lock);
    engine.fallback.clear();
    engine.detail.clear();
    if (gpu) {
        bool expected = false;
        if (!g_gpu_busy.compare_exchange_strong(expected, true)) {
            engine.fallback = "busy";
        } else if (ggml_backend_dev_t dev = usable_gpu(engine)) {
            whisper_context_params params = whisper_context_default_params();
            params.use_gpu = true;
            params.gpu_device = 0;
#ifdef NAGMEDAR_VULKAN
            params.flash_attn = flash_attn_on(dev);
#else
            params.flash_attn = false;
#endif
            whisper_context *ctx = load(model, params, engine.detail);
            if (ctx) {
                engine.gpu = true;
                engine.device = ggml_backend_dev_description(dev);
                engine.flash_attn = params.flash_attn;
                return ctx;
            }
            engine.fallback = "init";
            g_gpu_busy = false;
        } else {
            g_gpu_busy = false;
        }
    }
    return load_on_cpu(model, engine);
}

whisper_context *whisper_engine_fall_back(whisper_context *ctx, const char *model, WhisperEngine &engine, const char *why)
{
    std::string detail = t_last_error;
    if (ctx)
        free_context(ctx);
    std::lock_guard<std::mutex> lock(g_lock);
    if (engine.gpu)
        g_gpu_busy = false;
    engine.fallback = why;
    if (engine.detail.empty())
        engine.detail = detail;
    return load_on_cpu(model, engine);
}

void whisper_engine_close(whisper_context *ctx, WhisperEngine &engine)
{
    if (ctx)
        free_context(ctx);
    if (engine.gpu)
        g_gpu_busy = false;
}

std::string whisper_engine_json(const WhisperEngine &engine)
{
    return "\"gpu\":" + std::string(engine.gpu ? "true" : "false") + ",\"device\":\"" + json_escape(engine.device) +
           "\",\"threads\":" + std::to_string(engine.threads) + ",\"flash_attn\":" + (engine.flash_attn ? "true" : "false") +
           ",\"fallback\":\"" + json_escape(engine.fallback) + "\",\"fallback_detail\":\"" + json_escape(engine.detail) + "\"";
}
