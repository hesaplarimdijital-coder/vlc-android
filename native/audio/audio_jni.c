// JNI entry points of libnagmedar_audio.so (app.nagmedar.audio.NativeAudio). An engine is a
// handle (its pointer as a long); its events reach the Java listener on the engine's event
// thread, attached to the VM for as long as the engine lives.
#include "nagaudio.h"
#include "source.h"

#include <android/log.h>
#include <jni.h>

#include <math.h>
#include <stdint.h>
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

/* S-808: a part of a file, from and to in CUE frames (1/75 s; to -1: the file's end) */
JNIEXPORT jint JNICALL Java_app_nagmedar_audio_NativeAudio_openPart(JNIEnv *env, jclass cls, jlong handle, jlong id, jstring url, jlong start_ms, jlong from, jlong to)
{
    (void)cls;
    NagAudio *e = engine_of(handle);
    if (!e || !url)
        return -1;
    const char *path = (*env)->GetStringUTFChars(env, url, NULL);
    int ret = nag_audio_open_part(e, id, path, start_ms, from, to);
    (*env)->ReleaseStringUTFChars(env, url, path);
    return ret;
}

JNIEXPORT jint JNICALL Java_app_nagmedar_audio_NativeAudio_setNextPart(JNIEnv *env, jclass cls, jlong handle, jlong id, jstring url, jboolean crossfade, jlong from, jlong to)
{
    (void)cls;
    NagAudio *e = engine_of(handle);
    if (!e || !url)
        return -1;
    const char *path = (*env)->GetStringUTFChars(env, url, NULL);
    int ret = nag_audio_set_next_part(e, id, path, crossfade ? 1 : 0, from, to);
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
             "{\"codec\":\"%s\",\"container\":\"%s\",\"rate\":%d,\"channels\":%d,\"bits\":%d,\"float\":%s,\"duration_ms\":%lld,\"bitrate\":%lld,\"dsd_rate\":%d,\"dop\":%s}",
             info.codec, info.container, info.rate, info.channels, info.bits, info.is_float ? "true" : "false", (long long)info.duration_ms,
             (long long)info.bitrate, info.dsd_rate, info.dop ? "true" : "false");
    return utf8(env, json);
}

/* ---------------------------------------------------------------- S-906 DSP */

/*
 * The chain's parameters come flat (NativeAudio.setDsp):
 *   flags: bypass, auto_preamp, graphic_enabled, parametric_enabled, profile_enabled,
 *          limiter_enabled, then (enabled, type) of the 10 parametric and the 10 profile bands
 *   values: preamp_db, profile_preamp_db, limiter_threshold_db, limiter_release_ms,
 *           limiter_lookahead_ms, the 20 graphic bands' dB, then (freq, gain_db, q) of the
 *           10 parametric and the 10 profile bands
 */
#define DSP_FLAGS (6 + 2 * 2 * NAG_EQ_BANDS)
#define DSP_VALUES (5 + NAG_GRAPHIC_BANDS + 2 * 3 * NAG_EQ_BANDS)

JNIEXPORT jint JNICALL Java_app_nagmedar_audio_NativeAudio_setDsp(JNIEnv *env, jclass cls, jlong handle, jintArray flags_array, jdoubleArray values_array,
                                                                  jstring profile_name)
{
    (void)cls;
    NagAudio *e = engine_of(handle);
    if (!e || !flags_array || !values_array)
        return -1;
    if ((*env)->GetArrayLength(env, flags_array) < DSP_FLAGS || (*env)->GetArrayLength(env, values_array) < DSP_VALUES)
        return -1;
    jint flags[DSP_FLAGS];
    jdouble values[DSP_VALUES];
    (*env)->GetIntArrayRegion(env, flags_array, 0, DSP_FLAGS, flags);
    (*env)->GetDoubleArrayRegion(env, values_array, 0, DSP_VALUES, values);
    NagDspParams p;
    nag_dsp_params_defaults(&p);
    p.bypass = flags[0];
    p.auto_preamp = flags[1];
    p.graphic_enabled = flags[2];
    p.parametric_enabled = flags[3];
    p.profile_enabled = flags[4];
    p.limiter_enabled = flags[5];
    p.preamp_db = values[0];
    p.profile_preamp_db = values[1];
    p.limiter_threshold_db = values[2];
    p.limiter_release_ms = values[3];
    p.limiter_lookahead_ms = values[4];
    for (int i = 0; i < NAG_GRAPHIC_BANDS; i++)
        p.graphic_db[i] = values[5 + i];
    NagEqBand *sets[2] = {p.parametric, p.profile};
    for (int s = 0; s < 2; s++) {
        for (int i = 0; i < NAG_EQ_BANDS; i++) {
            const jint *f = flags + 6 + (s * NAG_EQ_BANDS + i) * 2;
            const jdouble *v = values + 5 + NAG_GRAPHIC_BANDS + (s * NAG_EQ_BANDS + i) * 3;
            sets[s][i].enabled = f[0];
            sets[s][i].type = (NagEqType)f[1];
            sets[s][i].freq = v[0];
            sets[s][i].gain_db = v[1];
            sets[s][i].q = v[2];
        }
    }
    if (profile_name) {
        const char *name = (*env)->GetStringUTFChars(env, profile_name, NULL);
        if (name) {
            snprintf(p.profile_name, sizeof(p.profile_name), "%s", name);
            (*env)->ReleaseStringUTFChars(env, profile_name, name);
        }
    }
    return nag_audio_set_dsp(e, &p);
}

JNIEXPORT jboolean JNICALL Java_app_nagmedar_audio_NativeAudio_dspActive(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env;
    (void)cls;
    return engine_of(handle) && nag_audio_dsp_active(engine_of(handle)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL Java_app_nagmedar_audio_NativeAudio_dspLatencyFrames(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env;
    (void)cls;
    return engine_of(handle) ? (jlong)nag_audio_dsp_latency_frames(engine_of(handle)) : 0;
}

/* frames, clipped, peak, reduction_db */
JNIEXPORT jdoubleArray JNICALL Java_app_nagmedar_audio_NativeAudio_dspMeter(JNIEnv *env, jclass cls, jlong handle, jboolean reset)
{
    (void)cls;
    jdoubleArray result = (*env)->NewDoubleArray(env, 4);
    if (!result)
        return NULL;
    jdouble values[4] = {0, 0, 0, 0};
    if (engine_of(handle)) {
        NagDspMeter m;
        nag_audio_dsp_meter(engine_of(handle), &m, reset == JNI_TRUE);
        values[0] = (jdouble)m.frames;
        values[1] = (jdouble)m.clipped;
        values[2] = m.peak;
        values[3] = m.reduction_db;
    }
    (*env)->SetDoubleArrayRegion(env, result, 0, 4, values);
    return result;
}

/* ---------------------------------------------------------- S-907 ReplayGain */

JNIEXPORT void JNICALL Java_app_nagmedar_audio_NativeAudio_setReplayGain(JNIEnv *env, jclass cls, jlong handle, jint mode, jdouble preampDb, jdouble untaggedDb,
                                                                        jboolean preventClipping)
{
    (void)env;
    (void)cls;
    NagAudio *e = engine_of(handle);
    if (!e)
        return;
    NagReplayGain rg = {(NagReplayGainMode)mode, preampDb, untaggedDb, preventClipping == JNI_TRUE};
    nag_audio_set_replaygain(e, &rg);
}

JNIEXPORT jdouble JNICALL Java_app_nagmedar_audio_NativeAudio_replayGainDb(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env;
    (void)cls;
    return engine_of(handle) ? nag_audio_replaygain_db(engine_of(handle)) : 0.0;
}

/* A scan: an album's accumulator and its cancel flag. */
typedef struct Scan {
    NagLoudnessScan *album;
    int cancel;
} Scan;

JNIEXPORT jlong JNICALL Java_app_nagmedar_audio_NativeAudio_loudnessNew(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    Scan *s = calloc(1, sizeof(*s));
    if (!s)
        return 0;
    s->album = nag_loudness_scan_new();
    if (!s->album) {
        free(s);
        return 0;
    }
    return (jlong)(intptr_t)s;
}

static jdoubleArray loudness_array(JNIEnv *env, const NagLoudnessResult *r)
{
    jdoubleArray result = (*env)->NewDoubleArray(env, 6);
    if (!result)
        return NULL;
    jdouble values[6] = {r->loudness_lufs, r->true_peak, r->sample_peak, r->gain_db, (jdouble)r->frames, (jdouble)r->rate};
    (*env)->SetDoubleArrayRegion(env, result, 0, 6, values);
    return result;
}

/* loudness LUFS (-inf for silence), true peak, sample peak, gain dB, frames, rate; null on failure or cancel */
JNIEXPORT jdoubleArray JNICALL Java_app_nagmedar_audio_NativeAudio_loudnessScan(JNIEnv *env, jclass cls, jlong handle, jstring path, jboolean forAlbum)
{
    (void)cls;
    Scan *s = (Scan *)(intptr_t)handle;
    if (!s || !path)
        return NULL;
    const char *url = (*env)->GetStringUTFChars(env, path, NULL);
    if (!url)
        return NULL;
    NagLoudnessResult r;
    char error[256] = {0};
    int ret = nag_loudness_scan_file(forAlbum == JNI_TRUE ? s->album : NULL, url, &r, &s->cancel, error, sizeof(error));
    (*env)->ReleaseStringUTFChars(env, path, url);
    if (ret < 0) {
        __android_log_print(ANDROID_LOG_WARN, "NagmedarAudio", "loudness scan: %s", error);
        return NULL;
    }
    return loudness_array(env, &r);
}

JNIEXPORT jdoubleArray JNICALL Java_app_nagmedar_audio_NativeAudio_loudnessAlbum(JNIEnv *env, jclass cls, jlong handle)
{
    (void)cls;
    Scan *s = (Scan *)(intptr_t)handle;
    NagLoudnessResult r;
    if (!s || nag_loudness_scan_result(s->album, &r) < 0)
        return NULL;
    return loudness_array(env, &r);
}

JNIEXPORT void JNICALL Java_app_nagmedar_audio_NativeAudio_loudnessCancel(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env;
    (void)cls;
    Scan *s = (Scan *)(intptr_t)handle;
    if (s)
        __atomic_store_n(&s->cancel, 1, __ATOMIC_RELAXED);
}

JNIEXPORT void JNICALL Java_app_nagmedar_audio_NativeAudio_loudnessFree(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env;
    (void)cls;
    Scan *s = (Scan *)(intptr_t)handle;
    if (!s)
        return;
    nag_loudness_scan_free(s->album);
    free(s);
}

/* NaN leaves a tag alone; the descriptor stays the caller's. 0, or negative. */
JNIEXPORT jint JNICALL Java_app_nagmedar_audio_NativeAudio_writeReplayGain(JNIEnv *env, jclass cls, jint fd, jstring name, jdouble trackGain, jdouble trackPeak,
                                                                          jdouble albumGain, jdouble albumPeak, jdouble trackLufs, jdouble albumLufs)
{
    (void)cls;
    NagReplayGainTags tags = {trackGain, trackPeak, albumGain, albumPeak, trackLufs, albumLufs};
    const char *utf = name ? (*env)->GetStringUTFChars(env, name, NULL) : NULL;
    char error[256] = {0};
    int ret = nag_tags_write_replaygain(fd, utf, &tags, error, sizeof(error));
    if (utf)
        (*env)->ReleaseStringUTFChars(env, name, utf);
    if (ret < 0)
        __android_log_print(ANDROID_LOG_WARN, "NagmedarAudio", "tags: %s", error);
    return ret;
}

/* S-1003: the file's lyrics as JSON; null when it has none. The descriptor stays the caller's. */
JNIEXPORT jstring JNICALL Java_app_nagmedar_audio_NativeAudio_readLyrics(JNIEnv *env, jclass cls, jint fd, jstring name)
{
    (void)cls;
    const char *utf = name ? (*env)->GetStringUTFChars(env, name, NULL) : NULL;
    char *json = nag_tags_read_lyrics(fd, utf);
    if (utf)
        (*env)->ReleaseStringUTFChars(env, name, utf);
    if (!json)
        return NULL;
    jstring result = utf8(env, json);
    free(json);
    return result;
}

/* S-1006: the front cover in the file's tags, its bytes; null when it has none. The descriptor stays the caller's. */
JNIEXPORT jbyteArray JNICALL Java_app_nagmedar_audio_NativeAudio_readCover(JNIEnv *env, jclass cls, jint fd, jstring name)
{
    (void)cls;
    const char *utf = name ? (*env)->GetStringUTFChars(env, name, NULL) : NULL;
    size_t size = 0;
    unsigned char *bytes = nag_tags_read_cover(fd, utf, &size);
    if (utf)
        (*env)->ReleaseStringUTFChars(env, name, utf);
    if (!bytes)
        return NULL;
    jbyteArray result = size <= 0x7fffffff ? (*env)->NewByteArray(env, (jsize)size) : NULL;
    if (result)
        (*env)->SetByteArrayRegion(env, result, 0, (jsize)size, (const jbyte *)bytes);
    free(bytes);
    return result;
}

/* S-1006: the first value of the file's tag [key]; null when it has none. */
JNIEXPORT jstring JNICALL Java_app_nagmedar_audio_NativeAudio_readTag(JNIEnv *env, jclass cls, jint fd, jstring name, jstring key)
{
    (void)cls;
    if (!key)
        return NULL;
    const char *utf = name ? (*env)->GetStringUTFChars(env, name, NULL) : NULL;
    const char *k = (*env)->GetStringUTFChars(env, key, NULL);
    char *value = nag_tags_read_property(fd, utf, k);
    (*env)->ReleaseStringUTFChars(env, key, k);
    if (utf)
        (*env)->ReleaseStringUTFChars(env, name, utf);
    if (!value)
        return NULL;
    jstring result = utf8(env, value);
    free(value);
    return result;
}

/* The file as the source reads it, with its ReplayGain tags, JSON; "{}" when it cannot be opened. */
JNIEXPORT jstring JNICALL Java_app_nagmedar_audio_NativeAudio_probe(JNIEnv *env, jclass cls, jstring path)
{
    (void)cls;
    if (!path)
        return utf8(env, "{}");
    const char *url = (*env)->GetStringUTFChars(env, path, NULL);
    if (!url)
        return utf8(env, "{}");
    char *json = nag_probe_json(url);
    (*env)->ReleaseStringUTFChars(env, path, url);
    jstring result = utf8(env, json ? json : "{}");
    free(json);
    return result;
}

/* S-1001: the waveform's peaks and RMS, two floats a bucket; null when the file does not decode */
JNIEXPORT jfloatArray JNICALL Java_app_nagmedar_audio_NativeAudio_waveform(JNIEnv *env, jclass cls, jstring path, jint points)
{
    (void)cls;
    if (!path || points <= 0 || points > 100000)
        return NULL;
    const char *url = (*env)->GetStringUTFChars(env, path, NULL);
    if (!url)
        return NULL;
    float *buckets = malloc(sizeof(float) * 2 * (size_t)points);
    char error[256] = {0};
    int filled = buckets ? nag_waveform(url, points, buckets, NULL, error, sizeof(error)) : -1;
    (*env)->ReleaseStringUTFChars(env, path, url);
    jfloatArray result = NULL;
    if (filled > 0) {
        result = (*env)->NewFloatArray(env, 2 * points);
        if (result)
            (*env)->SetFloatArrayRegion(env, result, 0, 2 * points, buckets);
    } else {
        __android_log_print(ANDROID_LOG_WARN, "NagmedarAudio", "waveform: %s", error);
    }
    free(buckets);
    return result;
}

/* ------------------------------------------------- S-909 tempo, pitch, balance */

JNIEXPORT void JNICALL Java_app_nagmedar_audio_NativeAudio_setTempo(JNIEnv *env, jclass cls, jlong handle, jdouble tempo)
{
    (void)env;
    (void)cls;
    if (engine_of(handle))
        nag_audio_set_tempo(engine_of(handle), tempo);
}

JNIEXPORT void JNICALL Java_app_nagmedar_audio_NativeAudio_setPitch(JNIEnv *env, jclass cls, jlong handle, jdouble semitones)
{
    (void)env;
    (void)cls;
    if (engine_of(handle))
        nag_audio_set_pitch(engine_of(handle), semitones);
}

JNIEXPORT void JNICALL Java_app_nagmedar_audio_NativeAudio_setBalance(JNIEnv *env, jclass cls, jlong handle, jdouble balance)
{
    (void)env;
    (void)cls;
    if (engine_of(handle))
        nag_audio_set_balance(engine_of(handle), balance);
}

JNIEXPORT void JNICALL Java_app_nagmedar_audio_NativeAudio_setMono(JNIEnv *env, jclass cls, jlong handle, jboolean mono)
{
    (void)env;
    (void)cls;
    if (engine_of(handle))
        nag_audio_set_mono(engine_of(handle), mono == JNI_TRUE);
}

/* ------------------------------------------------------------------ S-908 DSD */

JNIEXPORT void JNICALL Java_app_nagmedar_audio_NativeAudio_setDsdMode(JNIEnv *env, jclass cls, jlong handle, jint mode)
{
    (void)env;
    (void)cls;
    if (engine_of(handle))
        nag_audio_set_dsd_mode(engine_of(handle), (NagDsdMode)mode);
}

/* ------------------------------------------------------- S-902 bit-perfect */

/** The depths the sink is asked for: NagSampleFormat values, FORMAT_NONE for no bound. */
JNIEXPORT void JNICALL Java_app_nagmedar_audio_NativeAudio_setFormatRange(JNIEnv *env, jclass cls, jlong handle, jint minFormat, jint maxFormat)
{
    (void)env;
    (void)cls;
    if (engine_of(handle))
        nag_audio_set_format_range(engine_of(handle), (NagSampleFormat)minFormat, (NagSampleFormat)maxFormat);
}

/** The pull sink's owner says whether its output is bit-perfect, and its latency in frames. */
JNIEXPORT void JNICALL Java_app_nagmedar_audio_NativeAudio_setPullBitPerfect(JNIEnv *env, jclass cls, jlong handle, jboolean bitPerfect, jlong latencyFrames)
{
    (void)env;
    (void)cls;
    NagAudio *e = engine_of(handle);
    if (e)
        nag_sink_pull_set_bit_perfect(nag_audio_sink(e), bitPerfect == JNI_TRUE, latencyFrames);
}

/* ---------------------------------------------------------------- S-903 USB */

/** The USB DAC behind [fd] (a UsbDeviceConnection's) as the sink; the engine owns the sink. */
JNIEXPORT jint JNICALL Java_app_nagmedar_audio_NativeAudio_setUsbSink(JNIEnv *env, jclass cls, jlong handle, jint fd)
{
    (void)env;
    (void)cls;
    NagAudio *e = engine_of(handle);
    if (!e)
        return -1;
    NagSink *sink = nag_sink_usb_new(fd);
    if (!sink)
        return -1;
    return nag_audio_set_sink(e, sink);
}

JNIEXPORT jint JNICALL Java_app_nagmedar_audio_NativeAudio_usbSetVolume(JNIEnv *env, jclass cls, jlong handle, jdouble volume)
{
    (void)env;
    (void)cls;
    NagAudio *e = engine_of(handle);
    return e ? nag_sink_usb_set_volume(nag_audio_sink(e), volume) : -1;
}

JNIEXPORT jboolean JNICALL Java_app_nagmedar_audio_NativeAudio_usbHasVolume(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env;
    (void)cls;
    NagAudio *e = engine_of(handle);
    return e && nag_sink_usb_has_volume(nag_audio_sink(e)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL Java_app_nagmedar_audio_NativeAudio_usbError(JNIEnv *env, jclass cls, jlong handle)
{
    (void)cls;
    NagAudio *e = engine_of(handle);
    return utf8(env, e ? nag_sink_usb_error(nag_audio_sink(e)) : "");
}

/* ----------------------------------------------------------- S-912 A/B test */

JNIEXPORT jdouble JNICALL Java_app_nagmedar_audio_NativeAudio_dspLevelDb(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env;
    (void)cls;
    return engine_of(handle) ? nag_audio_dsp_level_db(engine_of(handle)) : 0.0;
}

JNIEXPORT void JNICALL Java_app_nagmedar_audio_NativeAudio_setAbSide(JNIEnv *env, jclass cls, jlong handle, jint side)
{
    (void)env;
    (void)cls;
    if (engine_of(handle))
        nag_audio_set_ab_side(engine_of(handle), side);
}

JNIEXPORT jint JNICALL Java_app_nagmedar_audio_NativeAudio_abSide(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env;
    (void)cls;
    return engine_of(handle) ? nag_audio_ab_side(engine_of(handle)) : 0;
}

JNIEXPORT jdouble JNICALL Java_app_nagmedar_audio_NativeAudio_abGainDb(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env;
    (void)cls;
    return engine_of(handle) ? nag_audio_ab_gain_db(engine_of(handle)) : 0.0;
}
