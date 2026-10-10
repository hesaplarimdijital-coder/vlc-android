// JNI entry points of libnagmedar_lab.so (app.nagmedar.lab.NativeLab).
#include <jni.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <libavformat/avformat.h>
#include <libavutil/ffversion.h>

#include "dvconvert.h"

#define LIBDOVI_VERSION "dovi_tool 2.3.4"

static volatile int g_cancel;

typedef struct Callback {
    JNIEnv *env;
    jobject listener;
    jmethodID method;
} Callback;

static jstring utf8(JNIEnv *env, const char *text)
{
    /* NewStringUTF wants modified UTF-8; transcripts may hold 4-byte characters */
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

static jstring take(JNIEnv *env, char *json)
{
    jstring result = utf8(env, json ? json : "{\"error\":\"out of memory\"}");
    free(json);
    return result;
}

static Callback callback(JNIEnv *env, jobject listener)
{
    Callback cb = {env, listener, NULL};
    if (listener) {
        jclass c = (*env)->GetObjectClass(env, listener);
        cb.method = (*env)->GetMethodID(env, c, "onProgress", "(DLjava/lang/String;)V");
        (*env)->DeleteLocalRef(env, c);
        if (!cb.method) {
            (*env)->ExceptionClear(env);
            cb.listener = NULL;
        }
    }
    return cb;
}

static void report(Callback *cb, double fraction, const char *message)
{
    if (!cb->listener)
        return;
    JNIEnv *env = cb->env;
    jstring text = (*env)->NewStringUTF(env, message);
    (*env)->CallVoidMethod(env, cb->listener, cb->method, fraction, text);
    (*env)->DeleteLocalRef(env, text);
    if ((*env)->ExceptionCheck(env))
        (*env)->ExceptionClear(env);
}

static void dv_progress(void *opaque, double done, double total, int64_t bytes)
{
    char msg[96];
    snprintf(msg, sizeof(msg), "%.0f / %.0f s · %.0f MB", done, total, bytes / 1e6);
    report(opaque, total > 0 ? done / total : 0, msg);
}

JNIEXPORT jstring JNICALL Java_app_nagmedar_lab_NativeLab_version(JNIEnv *env, jclass cls)
{
    (void)cls;
    char text[256];
    snprintf(text, sizeof(text), "FFmpeg %s · %s", FFMPEG_VERSION, LIBDOVI_VERSION);
    return utf8(env, text);
}

JNIEXPORT void JNICALL Java_app_nagmedar_lab_NativeLab_cancel(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    g_cancel = 1;
}

JNIEXPORT jstring JNICALL Java_app_nagmedar_lab_NativeLab_dvConvert(JNIEnv *env, jclass cls, jstring input, jstring output,
                                                                     jdouble max_seconds, jboolean mp4, jobject listener)
{
    (void)cls;
    g_cancel = 0;
    const char *in = (*env)->GetStringUTFChars(env, input, NULL);
    const char *out = (*env)->GetStringUTFChars(env, output, NULL);
    Callback cb = callback(env, listener);
    DvcStats stats;
    dvc_convert(in, out, max_seconds, mp4, &g_cancel, dv_progress, &cb, &stats);
    jstring result = take(env, dvc_stats_json(&stats, in, out));
    (*env)->ReleaseStringUTFChars(env, input, in);
    (*env)->ReleaseStringUTFChars(env, output, out);
    return result;
}
