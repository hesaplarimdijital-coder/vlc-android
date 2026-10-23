// JNI entry points of libnagmedar_lab.so (app.nagmedar.lab.NativeLab).
#include <jni.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <libavformat/avformat.h>
#include <libavutil/ffversion.h>
#include <libusb.h>

#include "bitrate.h"
#include "disktest.h"
#include "dvconvert.h"
#include "mediaprobe.h"
#include "remux.h"
#include "usb_audio.h"
#include "whisper_lab.h"

#define LIBDOVI_VERSION "dovi_tool 2.3.4"
#define WHISPER_LABEL "whisper.cpp 1.9.5"

static volatile int g_cancel;
/* the remux queue's own flag (S-1206): a Lab tool stopped must not stop a running job */
static volatile int g_remux_cancel;

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

static void whisper_progress(void *opaque, double fraction, const char *stage)
{
    report(opaque, fraction, stage);
}

static int bitrate_progress_cb(void *opaque, double fraction)
{
    char msg[64];
    snprintf(msg, sizeof(msg), "%.0f %%", fraction * 100);
    report(opaque, fraction, msg);
    return g_cancel;
}

static int disk_progress_cb(void *opaque, double fraction, double mb_per_s)
{
    char msg[64];
    snprintf(msg, sizeof(msg), "%.0f MB/s", mb_per_s);
    report(opaque, fraction, msg);
    return g_cancel;
}

static void usb_progress(void *opaque, double fraction, int64_t frames, int64_t errors)
{
    char msg[96];
    snprintf(msg, sizeof(msg), "%lld frames, %lld errors", (long long)frames, (long long)errors);
    report(opaque, fraction, msg);
}

JNIEXPORT jstring JNICALL Java_app_nagmedar_lab_NativeLab_version(JNIEnv *env, jclass cls)
{
    (void)cls;
    const struct libusb_version *usb = libusb_get_version();
    char text[256];
    snprintf(text, sizeof(text), "FFmpeg %s · %s · %s · libusb %u.%u.%u", FFMPEG_VERSION, LIBDOVI_VERSION, WHISPER_LABEL,
             usb->major, usb->minor, usb->micro);
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

JNIEXPORT jstring JNICALL Java_app_nagmedar_lab_NativeLab_whisper(JNIEnv *env, jclass cls, jstring model, jstring input,
                                                                   jstring language, jdouble max_seconds, jint threads,
                                                                   jboolean gpu, jobject listener)
{
    (void)cls;
    g_cancel = 0;
    const char *m = (*env)->GetStringUTFChars(env, model, NULL);
    const char *in = (*env)->GetStringUTFChars(env, input, NULL);
    const char *lang = (*env)->GetStringUTFChars(env, language, NULL);
    Callback cb = callback(env, listener);
    jstring result = take(env, whisper_lab_run(m, in, lang, max_seconds, threads, gpu == JNI_TRUE, whisper_progress, &cb, &g_cancel));
    (*env)->ReleaseStringUTFChars(env, model, m);
    (*env)->ReleaseStringUTFChars(env, input, in);
    (*env)->ReleaseStringUTFChars(env, language, lang);
    return result;
}

JNIEXPORT jstring JNICALL Java_app_nagmedar_lab_NativeLab_usbDescribe(JNIEnv *env, jclass cls, jint fd)
{
    (void)cls;
    return take(env, usb_audio_describe(fd));
}

JNIEXPORT jstring JNICALL Java_app_nagmedar_lab_NativeLab_usbTone(JNIEnv *env, jclass cls, jint fd, jint rate, jint bits,
                                                                  jdouble seconds, jdouble frequency, jobject listener)
{
    (void)cls;
    g_cancel = 0;
    Callback cb = callback(env, listener);
    return take(env, usb_audio_tone(fd, (unsigned)rate, bits, seconds, frequency, usb_progress, &cb, &g_cancel));
}

/* S-1206: the job's listener answers how to go on (0), to stop (-1) or to wait (milliseconds) */
typedef struct RemuxCallback {
    JNIEnv *env;
    jobject listener;
    jmethodID method;
} RemuxCallback;

static int remux_progress_cb(void *opaque, double done, double total, int64_t bytes)
{
    RemuxCallback *cb = opaque;
    if (!cb->listener)
        return 0;
    JNIEnv *env = cb->env;
    jint answer = (*env)->CallIntMethod(env, cb->listener, cb->method, done, total, (jlong)bytes);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return -1;
    }
    return answer;
}

static RemuxCallback remux_callback(JNIEnv *env, jobject listener)
{
    RemuxCallback cb = {env, listener, NULL};
    if (listener) {
        jclass c = (*env)->GetObjectClass(env, listener);
        cb.method = (*env)->GetMethodID(env, c, "onProgress", "(DDJ)I");
        (*env)->DeleteLocalRef(env, c);
        if (!cb.method) {
            (*env)->ExceptionClear(env);
            cb.listener = NULL;
        }
    }
    return cb;
}

JNIEXPORT void JNICALL Java_app_nagmedar_lab_NativeLab_remuxCancel(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    g_remux_cancel = 1;
}

JNIEXPORT jstring JNICALL Java_app_nagmedar_lab_NativeLab_remux(JNIEnv *env, jclass cls, jint input_fd, jint output_fd, jbyteArray plan_utf8,
                                                                jobject listener)
{
    (void)cls;
    g_remux_cancel = 0;
    /* the plan comes as UTF-8 bytes: GetStringUTFChars would give modified UTF-8, wrong for a title beyond the BMP */
    jsize len = (*env)->GetArrayLength(env, plan_utf8);
    char *text = malloc((size_t)len + 1);
    RemuxPlan plan;
    char err[256];
    RemuxStats stats;
    memset(&stats, 0, sizeof(stats));
    if (!text) {
        snprintf(stats.error, sizeof(stats.error), "out of memory");
        return take(env, remux_stats_json(&stats));
    }
    (*env)->GetByteArrayRegion(env, plan_utf8, 0, len, (jbyte *)text);
    text[len] = 0;
    if (remux_plan_parse(text, &plan, err, sizeof(err)) < 0) {
        snprintf(stats.error, sizeof(stats.error), "bad plan: %s", err);
    } else {
        RemuxCallback cb = remux_callback(env, listener);
        remux_run(input_fd, output_fd, &plan, &g_remux_cancel, listener ? remux_progress_cb : NULL, &cb, &stats);
    }
    free(text);
    return take(env, remux_stats_json(&stats));
}

JNIEXPORT jstring JNICALL Java_app_nagmedar_lab_NativeLab_remuxVerify(JNIEnv *env, jclass cls, jint output_fd, jstring scope_name,
                                                                      jstring expected_hash, jlong expected_packets, jint dv_profile,
                                                                      jobject listener)
{
    (void)cls;
    g_remux_cancel = 0;
    const char *scope = (*env)->GetStringUTFChars(env, scope_name, NULL);
    const char *hash = (*env)->GetStringUTFChars(env, expected_hash, NULL);
    RemuxCallback cb = remux_callback(env, listener);
    RemuxVerification result;
    remux_verify(output_fd, remux_hash_scope_of(scope), hash, expected_packets, dv_profile, &g_remux_cancel,
                 listener ? remux_progress_cb : NULL, &cb, &result);
    (*env)->ReleaseStringUTFChars(env, scope_name, scope);
    (*env)->ReleaseStringUTFChars(env, expected_hash, hash);
    return take(env, remux_verification_json(&result));
}

JNIEXPORT jstring JNICALL Java_app_nagmedar_lab_NativeLab_probe(JNIEnv *env, jclass cls, jstring input)
{
    (void)cls;
    const char *in = (*env)->GetStringUTFChars(env, input, NULL);
    /* the library probes in the background: Lab's cancel button is not for it */
    jstring result = take(env, media_probe(in, NULL));
    (*env)->ReleaseStringUTFChars(env, input, in);
    return result;
}

JNIEXPORT jstring JNICALL Java_app_nagmedar_lab_NativeLab_bitrate(JNIEnv *env, jclass cls, jstring input, jdouble max_seconds,
                                                                  jobject listener)
{
    (void)cls;
    g_cancel = 0;
    const char *in = (*env)->GetStringUTFChars(env, input, NULL);
    Callback cb = callback(env, listener);
    jstring result = take(env, media_bitrate(in, max_seconds, listener ? bitrate_progress_cb : NULL, &cb, &g_cancel));
    (*env)->ReleaseStringUTFChars(env, input, in);
    return result;
}

JNIEXPORT jstring JNICALL Java_app_nagmedar_lab_NativeLab_diskSpeed(JNIEnv *env, jclass cls, jint fd, jlong offset, jlong max_bytes,
                                                                    jdouble max_seconds, jobject listener)
{
    (void)cls;
    g_cancel = 0;
    Callback cb = callback(env, listener);
    return take(env, disk_read_speed(fd, offset, max_bytes, max_seconds, listener ? disk_progress_cb : NULL, &cb, &g_cancel));
}
