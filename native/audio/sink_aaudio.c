// The AAudio sink: the engine's render on AAudio's callback thread, exclusive and low latency
// where the device allows it, shared otherwise. A disconnected stream (the headphones pulled,
// a USB DAC gone) is reported to the engine, which reopens it from its event thread.
#include "nagaudio.h"

#include <aaudio/AAudio.h>
#include <android/api-level.h>
#include <android/log.h>

#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>

#define TAG "NagmedarAudio"

typedef struct AAudioSink {
    AAudioStream *stream;
    NagAudio *engine;
    NagFormat format;
    int exclusive;
    atomic_int lost;
} AAudioSink;

static aaudio_data_callback_result_t on_data(AAudioStream *stream, void *user, void *audio_data, int32_t frames)
{
    (void)stream;
    NagSink *sink = user;
    AAudioSink *a = sink->priv;
    nag_audio_render(a->engine, audio_data, frames);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

static void on_error(AAudioStream *stream, void *user, aaudio_result_t error)
{
    (void)stream;
    NagSink *sink = user;
    AAudioSink *a = sink->priv;
    __android_log_print(ANDROID_LOG_WARN, TAG, "AAudio stream error: %s", AAudio_convertResultToText(error));
    if (error == AAUDIO_ERROR_DISCONNECTED && !atomic_exchange(&a->lost, 1))
        nag_audio_sink_lost(a->engine, sink);
}

/* the 24 and 32-bit integer formats came with Android 12: before it, floats carry 24 bits exactly */
static aaudio_format_t aaudio_format(NagSampleFormat f)
{
    switch (f) {
    case NAG_FMT_S16: return AAUDIO_FORMAT_PCM_I16;
    case NAG_FMT_F32: return AAUDIO_FORMAT_PCM_FLOAT;
    case NAG_FMT_S24_3: return android_get_device_api_level() >= 31 ? AAUDIO_FORMAT_PCM_I24_PACKED : AAUDIO_FORMAT_PCM_FLOAT;
    default: return android_get_device_api_level() >= 31 ? AAUDIO_FORMAT_PCM_I32 : AAUDIO_FORMAT_PCM_FLOAT;
    }
}

static NagSampleFormat nag_format(aaudio_format_t f)
{
    switch (f) {
    case AAUDIO_FORMAT_PCM_I16: return NAG_FMT_S16;
    case AAUDIO_FORMAT_PCM_FLOAT: return NAG_FMT_F32;
    case AAUDIO_FORMAT_PCM_I32: return NAG_FMT_S32;
    case AAUDIO_FORMAT_PCM_I24_PACKED: return NAG_FMT_S24_3;
    default: return NAG_FMT_NONE;
    }
}

static void close_stream(AAudioSink *a)
{
    if (!a->stream)
        return;
    AAudioStream_requestStop(a->stream);
    AAudioStream_close(a->stream);
    a->stream = NULL;
}

static aaudio_result_t open_stream(NagSink *sink, const NagFormat *want, aaudio_sharing_mode_t sharing)
{
    AAudioSink *a = sink->priv;
    AAudioStreamBuilder *builder = NULL;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK)
        return result;
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setSharingMode(builder, sharing);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSampleRate(builder, want->rate);
    AAudioStreamBuilder_setChannelCount(builder, want->channels);
    AAudioStreamBuilder_setFormat(builder, aaudio_format(want->sample_format));
#if __ANDROID_API__ >= 28
    if (android_get_device_api_level() >= 28) {
        AAudioStreamBuilder_setUsage(builder, AAUDIO_USAGE_MEDIA);
        AAudioStreamBuilder_setContentType(builder, AAUDIO_CONTENT_TYPE_MUSIC);
    }
#endif
    AAudioStreamBuilder_setDataCallback(builder, on_data, sink);
    AAudioStreamBuilder_setErrorCallback(builder, on_error, sink);
    result = AAudioStreamBuilder_openStream(builder, &a->stream);
    AAudioStreamBuilder_delete(builder);
    if (result != AAUDIO_OK) {
        a->stream = NULL;
        return result;
    }
    /* two bursts of buffer: glitch-free with room to spare; the engine's rings hold the rest */
    int32_t burst = AAudioStream_getFramesPerBurst(a->stream);
    if (burst > 0)
        AAudioStream_setBufferSizeInFrames(a->stream, burst * 2);
    a->exclusive = AAudioStream_getSharingMode(a->stream) == AAUDIO_SHARING_MODE_EXCLUSIVE;
    return AAUDIO_OK;
}

static int aaudio_open(NagSink *sink, NagAudio *engine, const NagFormat *want, NagFormat *got)
{
    AAudioSink *a = sink->priv;
    a->engine = engine;
    close_stream(a);
    atomic_store(&a->lost, 0);
    aaudio_result_t result = open_stream(sink, want, AAUDIO_SHARING_MODE_EXCLUSIVE);
    if (result != AAUDIO_OK)
        result = open_stream(sink, want, AAUDIO_SHARING_MODE_SHARED);
    if (result != AAUDIO_OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "AAudio open %d Hz %s: %s", want->rate, nag_format_name(want->sample_format),
                            AAudio_convertResultToText(result));
        return -1;
    }
    got->rate = AAudioStream_getSampleRate(a->stream);
    got->channels = AAudioStream_getChannelCount(a->stream);
    got->sample_format = nag_format(AAudioStream_getFormat(a->stream));
    if (got->sample_format == NAG_FMT_NONE || got->channels != want->channels || got->rate != want->rate) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "AAudio gave %d Hz, %d channels, format %d", got->rate, got->channels,
                            AAudioStream_getFormat(a->stream));
        close_stream(a);
        return -1;
    }
    a->format = *got;
    __android_log_print(ANDROID_LOG_INFO, TAG, "AAudio %s %d Hz %s, burst %d", a->exclusive ? "exclusive" : "shared", got->rate,
                        nag_format_name(got->sample_format), AAudioStream_getFramesPerBurst(a->stream));
    return 0;
}

static int aaudio_start(NagSink *sink)
{
    AAudioSink *a = sink->priv;
    if (!a->stream)
        return -1;
    aaudio_stream_state_t state = AAudioStream_getState(a->stream);
    if (state == AAUDIO_STREAM_STATE_STARTED || state == AAUDIO_STREAM_STATE_STARTING)
        return 0;
    return AAudioStream_requestStart(a->stream) == AAUDIO_OK ? 0 : -1;
}

static int aaudio_pause(NagSink *sink)
{
    AAudioSink *a = sink->priv;
    if (!a->stream)
        return 0;
    AAudioStream_requestPause(a->stream);
    return 0;
}

static void aaudio_close(NagSink *sink)
{
    close_stream(sink->priv);
}

static int64_t aaudio_latency(NagSink *sink)
{
    AAudioSink *a = sink->priv;
    if (!a->stream)
        return 0;
    int64_t written = AAudioStream_getFramesWritten(a->stream);
    int64_t read = AAudioStream_getFramesRead(a->stream);
    return written > read ? written - read : 0;
}

static int aaudio_bit_perfect(NagSink *sink)
{
    (void)sink;
    /* exclusive or not, the frames cross the audio framework's own processing */
    return 0;
}

static void aaudio_destroy(NagSink *sink)
{
    close_stream(sink->priv);
    free(sink->priv);
    free(sink);
}

NagSink *nag_sink_aaudio_new(void)
{
    NagSink *sink = calloc(1, sizeof(*sink));
    AAudioSink *a = calloc(1, sizeof(*a));
    if (!sink || !a) {
        free(sink);
        free(a);
        return NULL;
    }
    sink->name = "aaudio";
    sink->open = aaudio_open;
    sink->start = aaudio_start;
    sink->pause = aaudio_pause;
    sink->close = aaudio_close;
    sink->latency_frames = aaudio_latency;
    sink->bit_perfect = aaudio_bit_perfect;
    sink->destroy = aaudio_destroy;
    sink->priv = a;
    return sink;
}
