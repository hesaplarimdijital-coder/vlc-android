// JNI entry points of libnagmedar_audio.so (app.nagmedar.audio.NativeAudio). An engine is a
// handle (its pointer as a long); its events reach the Java listener on the engine's event
// thread, attached to the VM for as long as the engine lives.
#include "nagaudio.h"

#include <jni.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct Owner {
    JavaVM *vm;
    jobject listener; /* global */
    jmethodID on_event;
    JNIEnv *thread_env; /* the event thread's, once attached */
} Owner;

static jstring utf8(JNIEnv *env, const char *text)
{
    if (!text)
        text = "";
    size_t len = strlen(text);
    jbyteArray bytes = (*env)->NewByteArray(env, (jsize)len);
    if (!bytes)
        return NULL;
    (*env)->SetByteArrayRegion(env, bytes, 0, (jsize)len, (const jbyte *)text);
    jclass string_class = (*env)->FindClass(env, "java/lang/String");
    jmethodID ctor = (*env)->GetMethodID(env, string_class, "<init>", "([BLjava/lang/String;)V");
    jstring charset = (*env)->NewStringUTF(env, "UTF-8");
    jstring result = (jstring)(*env)->NewObject(env, string_class, ctor, bytes, charset);
    (*env)->DeleteLocalRef(env, bytes);
    (*env)->DeleteLocalRef(env, charset);
    (*env)->DeleteLocalRef(env, string_class);
    return result;
}

static void on_event(void *opaque, const NagEvent *event)
{
    Owner *o = opaque;
    if (!event) {
        /* the event thread ends with the engine */
        if (o->thread_env)
            (*o->vm)->DetachCurrentThread(o->vm);
        o->thread_env = NULL;
        return;
    }
    if (!o->thread_env) {
        JavaVMAttachArgs args = {JNI_VERSION_1_6, "nagaudio-events", NULL};
        void *env_ptr = NULL;
        if ((*o->vm)->AttachCurrentThread(o->vm, (void **)&env_ptr, &args) != JNI_OK)
            return;
        o->thread_env = env_ptr;
    }
    JNIEnv *env = o->thread_env;
    jstring text = utf8(env, event->text);
    (*env)->CallVoidMethod(env, o->listener, o->on_event, (jint)event->type, (jlong)event->arg, text);
    if ((*env)->ExceptionCheck(env))
        (*env)->ExceptionClear(env);
    (*env)->DeleteLocalRef(env, text);
}

static NagAudio *engine_of(jlong handle)
{
    return (NagAudio *)(intptr_t)handle;
}

JNIEXPORT jstring JNICALL Java_app_nagmedar_audio_NativeAudio_version(JNIEnv *env, jclass cls)
{
    (void)cls;
    return utf8(env, nag_audio_version());
}

JNIEXPORT jlong JNICALL Java_app_nagmedar_audio_NativeAudio_create(JNIEnv *env, jclass cls, jobject listener, jdouble ring_seconds, jint max_format, jint fixed_format, jboolean dither)
{
    (void)cls;
    Owner *o = calloc(1, sizeof(*o));
    if (!o)
        return 0;
    (*env)->GetJavaVM(env, &o->vm);
    o->listener = (*env)->NewGlobalRef(env, listener);
    jclass listener_class = (*env)->GetObjectClass(env, listener);
    o->on_event = (*env)->GetMethodID(env, listener_class, "onEvent", "(IJLjava/lang/String;)V");
    (*env)->DeleteLocalRef(env, listener_class);
    NagConfig cfg;
    nag_config_defaults(&cfg);
    if (ring_seconds > 0)
        cfg.ring_seconds = ring_seconds;
    cfg.max_format = (NagSampleFormat)max_format;
    cfg.fixed_format = (NagSampleFormat)fixed_format;
    cfg.dither = dither ? 1 : 0;
    NagAudio *e = nag_audio_new(&cfg, on_event, o);
    if (!e) {
        (*env)->DeleteGlobalRef(env, o->listener);
        free(o);
        return 0;
    }
    return (jlong)(intptr_t)e;
}

/* The owner of an engine: its opaque pointer. */
static Owner *owner_of(NagAudio *e)
{
    return nag_audio_opaque(e);
}

JNIEXPORT void JNICALL Java_app_nagmedar_audio_NativeAudio_destroy(JNIEnv *env, jclass cls, jlong handle)
{
    (void)cls;
    NagAudio *e = engine_of(handle);
    if (!e)
        return;
    Owner *o = owner_of(e);
    nag_audio_free(e);
    if (o) {
        (*env)->DeleteGlobalRef(env, o->listener);
        free(o);
    }
}

JNIEXPORT jint JNICALL Java_app_nagmedar_audio_NativeAudio_setSink(JNIEnv *env, jclass cls, jlong handle, jint kind)
{
    (void)env;
    (void)cls;
    NagAudio *e = engine_of(handle);
    if (!e)
        return -1;
    NagSink *sink = NULL;
    switch (kind) {
    case 0: sink = nag_sink_pull_new(); break;
    case 1: sink = nag_sink_aaudio_new(); break;
    default: break;
    }
    return nag_audio_set_sink(e, sink);
}

JNIEXPORT jint JNICALL Java_app_nagmedar_audio_NativeAudio_open(JNIEnv *env, jclass cls, jlong handle, jlong id, jstring url, jlong start_ms)
{
    (void)cls;
    NagAudio *e = engine_of(handle);
    if (!e || !url)
        return -1;
    const char *path = (*env)->GetStringUTFChars(env, url, NULL);
    int ret = nag_audio_open(e, id, path, start_ms);
    (*env)->ReleaseStringUTFChars(env, url, path);
    return ret;
}

JNIEXPORT jint JNICALL Java_app_nagmedar_audio_NativeAudio_setNext(JNIEnv *env, jclass cls, jlong handle, jlong id, jstring url, jboolean crossfade)
{
    (void)cls;
    NagAudio *e = engine_of(handle);
    if (!e)
        return -1;
    if (!url)
        return nag_audio_set_next(e, id, NULL, 0);
    const char *path = (*env)->GetStringUTFChars(env, url, NULL);
    int ret = nag_audio_set_next(e, id, path, crossfade ? 1 : 0);
    (*env)->ReleaseStringUTFChars(env, url, path);
    return ret;
}

JNIEXPORT void JNICALL Java_app_nagmedar_audio_NativeAudio_setCrossfade(JNIEnv *env, jclass cls, jlong handle, jint ms, jint curve)
{
    (void)env;
    (void)cls;
    if (engine_of(handle))
        nag_audio_set_crossfade(engine_of(handle), ms, (NagCrossfadeCurve)curve);
}

JNIEXPORT jint JNICALL Java_app_nagmedar_audio_NativeAudio_play(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env;
    (void)cls;
    return engine_of(handle) ? nag_audio_play(engine_of(handle)) : -1;
}

JNIEXPORT jint JNICALL Java_app_nagmedar_audio_NativeAudio_pause(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env;
    (void)cls;
    return engine_of(handle) ? nag_audio_pause(engine_of(handle)) : -1;
}

JNIEXPORT jint JNICALL Java_app_nagmedar_audio_NativeAudio_stop(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env;
    (void)cls;
    return engine_of(handle) ? nag_audio_stop(engine_of(handle)) : -1;
}

JNIEXPORT jint JNICALL Java_app_nagmedar_audio_NativeAudio_seek(JNIEnv *env, jclass cls, jlong handle, jlong ms)
{
    (void)env;
    (void)cls;
    return engine_of(handle) ? nag_audio_seek(engine_of(handle), ms) : -1;
}

JNIEXPORT jint JNICALL Java_app_nagmedar_audio_NativeAudio_state(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env;
    (void)cls;
    return engine_of(handle) ? (jint)nag_audio_state(engine_of(handle)) : 0;
}

JNIEXPORT jlong JNICALL Java_app_nagmedar_audio_NativeAudio_timeMs(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env;
    (void)cls;
    return engine_of(handle) ? nag_audio_time_ms(engine_of(handle)) : 0;
}

JNIEXPORT jlong JNICALL Java_app_nagmedar_audio_NativeAudio_lengthMs(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env;
    (void)cls;
    return engine_of(handle) ? nag_audio_length_ms(engine_of(handle)) : 0;
}

JNIEXPORT jlong JNICALL Java_app_nagmedar_audio_NativeAudio_trackId(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env;
    (void)cls;
    return engine_of(handle) ? nag_audio_track_id(engine_of(handle)) : -1;
}

JNIEXPORT void JNICALL Java_app_nagmedar_audio_NativeAudio_setVolume(JNIEnv *env, jclass cls, jlong handle, jdouble volume)
{
    (void)env;
    (void)cls;
    if (engine_of(handle))
        nag_audio_set_volume(engine_of(handle), volume);
}

/** rate << 16 | channels << 8 | format; 0 when the output is not open. */
JNIEXPORT jlong JNICALL Java_app_nagmedar_audio_NativeAudio_outputFormat(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env;
    (void)cls;
    NagAudio *e = engine_of(handle);
    if (!e)
        return 0;
    NagFormat f = nag_audio_output_format(e);
    if (f.sample_format == NAG_FMT_NONE)
        return 0;
    return ((jlong)f.rate << 16) | ((jlong)f.channels << 8) | (jlong)f.sample_format;
}

JNIEXPORT jint JNICALL Java_app_nagmedar_audio_NativeAudio_render(JNIEnv *env, jclass cls, jlong handle, jobject buffer, jint frames)
{
    (void)cls;
    NagAudio *e = engine_of(handle);
    if (!e || !buffer)
        return -1;
    void *out = (*env)->GetDirectBufferAddress(env, buffer);
    if (!out)
        return -1;
    return nag_audio_render(e, out, frames);
}

JNIEXPORT jstring JNICALL Java_app_nagmedar_audio_NativeAudio_signalPath(JNIEnv *env, jclass cls, jlong handle)
{
    (void)cls;
    NagAudio *e = engine_of(handle);
    char *json = e ? nag_audio_signal_path_json(e) : NULL;
    jstring result = utf8(env, json ? json : "{}");
    free(json);
    return result;
}

JNIEXPORT jstring JNICALL Java_app_nagmedar_audio_NativeAudio_sourceInfo(JNIEnv *env, jclass cls, jlong handle)
{
    (void)cls;
    NagAudio *e = engine_of(handle);
    NagSourceInfo info;
    if (!e || !nag_audio_source_info(e, &info))
        return utf8(env, "{}");
    char json[512];
    snprintf(json, sizeof(json),
             "{\"codec\":\"%s\",\"container\":\"%s\",\"rate\":%d,\"channels\":%d,\"bits\":%d,\"float\":%s,\"duration_ms\":%lld,\"bitrate\":%lld}",
             info.codec, info.container, info.rate, info.channels, info.bits, info.is_float ? "true" : "false", (long long)info.duration_ms,
             (long long)info.bitrate);
    return utf8(env, json);
}
