// S-709: the JNI side of the subtitles from speech (app.nagmedar.lab.NativeLab.transcribe).
#include <jni.h>

#include <stdlib.h>
#include <string.h>

#include "whisper_subs.h"

/* its own flag: the Lab's Cancel does not stop a film's subtitles, nor they the Lab */
static volatile int g_transcribe_cancel;

typedef struct Listener {
    JNIEnv *env;
    jobject target;
    jmethodID progress;
    jmethodID segment;
    jmethodID language;
    jmethodID engine;
} Listener;

/* A Java string of UTF-8 [text]: NewStringUTF wants modified UTF-8, and speech may hold 4-byte characters. */
static jstring java_string(JNIEnv *env, const char *text)
{
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

static void clear_exception(JNIEnv *env)
{
    if ((*env)->ExceptionCheck(env))
        (*env)->ExceptionClear(env);
}

static void on_progress(void *opaque, double done, double total)
{
    Listener *l = opaque;
    (*l->env)->CallVoidMethod(l->env, l->target, l->progress, done, total);
    clear_exception(l->env);
}

static void on_segment(void *opaque, int64_t start_ms, int64_t end_ms, const char *text)
{
    Listener *l = opaque;
    jstring s = java_string(l->env, text);
    if (!s) {
        clear_exception(l->env);
        return;
    }
    (*l->env)->CallVoidMethod(l->env, l->target, l->segment, (jlong)start_ms, (jlong)end_ms, s);
    (*l->env)->DeleteLocalRef(l->env, s);
    clear_exception(l->env);
}

static void on_language(void *opaque, const char *code)
{
    Listener *l = opaque;
    jstring s = (*l->env)->NewStringUTF(l->env, code);
    if (!s) {
        clear_exception(l->env);
        return;
    }
    (*l->env)->CallVoidMethod(l->env, l->target, l->language, s);
    (*l->env)->DeleteLocalRef(l->env, s);
    clear_exception(l->env);
}

static void on_engine(void *opaque, int gpu, const char *device, int threads)
{
    Listener *l = opaque;
    jstring s = java_string(l->env, device);
    if (!s) {
        clear_exception(l->env);
        return;
    }
    (*l->env)->CallVoidMethod(l->env, l->target, l->engine, gpu ? JNI_TRUE : JNI_FALSE, s, (jint)threads);
    (*l->env)->DeleteLocalRef(l->env, s);
    clear_exception(l->env);
}

JNIEXPORT void JNICALL Java_app_nagmedar_lab_NativeLab_cancelTranscription(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    g_transcribe_cancel = 1;
}

JNIEXPORT jstring JNICALL Java_app_nagmedar_lab_NativeLab_transcribe(JNIEnv *env, jclass cls, jstring model, jstring input,
                                                                      jint audio_track, jstring language, jboolean translate,
                                                                      jint threads, jboolean gpu, jlong start_ms, jobject listener)
{
    (void)cls;
    g_transcribe_cancel = 0;
    jclass c = (*env)->GetObjectClass(env, listener);
    Listener l = {
        env,
        listener,
        (*env)->GetMethodID(env, c, "onProgress", "(DD)V"),
        (*env)->GetMethodID(env, c, "onSegment", "(JJLjava/lang/String;)V"),
        (*env)->GetMethodID(env, c, "onLanguage", "(Ljava/lang/String;)V"),
        (*env)->GetMethodID(env, c, "onEngine", "(ZLjava/lang/String;I)V"),
    };
    (*env)->DeleteLocalRef(env, c);
    if (!l.progress || !l.segment || !l.language || !l.engine) {
        clear_exception(env);
        return (*env)->NewStringUTF(env, "{\"error\":\"listener\",\"detail\":\"\",\"language\":\"\",\"audio_seconds\":0,\"segments\":0}");
    }
    const char *m = (*env)->GetStringUTFChars(env, model, NULL);
    const char *in = (*env)->GetStringUTFChars(env, input, NULL);
    const char *lang = (*env)->GetStringUTFChars(env, language, NULL);
    WsCallbacks callbacks = {&l, on_progress, on_segment, on_language, on_engine};
    char *json = whisper_subs_run(m, in, audio_track, lang, translate == JNI_TRUE, threads, gpu == JNI_TRUE, (int64_t)start_ms, &callbacks, &g_transcribe_cancel);
    (*env)->ReleaseStringUTFChars(env, model, m);
    (*env)->ReleaseStringUTFChars(env, input, in);
    (*env)->ReleaseStringUTFChars(env, language, lang);
    jstring result = java_string(env, json ? json : "{\"error\":\"memory\",\"detail\":\"\",\"language\":\"\",\"audio_seconds\":0,\"segments\":0}");
    free(json);
    return result;
}
