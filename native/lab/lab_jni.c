// JNI entry points of libnagmedar_lab.so (app.nagmedar.lab.NativeLab).
#include <jni.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <libavformat/avformat.h>
#include <libavutil/ffversion.h>
#include <libusb.h>

#include "mediaprobe.h"
#include "usb_audio.h"

static volatile int g_cancel;

typedef struct Callback {
    JNIEnv *env;
    jobject listener;
    jmethodID method;
} Callback;

static jstring utf8(JNIEnv *env, const char *text)
{
    /* NewStringUTF wants modified UTF-8; tags and file names may hold 4-byte characters */
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
    snprintf(text, sizeof(text), "FFmpeg %s · libusb %u.%u.%u", FFMPEG_VERSION,
             usb->major, usb->minor, usb->micro);
    return utf8(env, text);
}

JNIEXPORT void JNICALL Java_app_nagmedar_lab_NativeLab_cancel(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    g_cancel = 1;
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

JNIEXPORT jstring JNICALL Java_app_nagmedar_lab_NativeLab_probe(JNIEnv *env, jclass cls, jstring input)
{
    (void)cls;
    const char *in = (*env)->GetStringUTFChars(env, input, NULL);
    /* the library probes in the background: Lab's cancel button is not for it */
    jstring result = take(env, media_probe(in, NULL));
    (*env)->ReleaseStringUTFChars(env, input, in);
    return result;
}
