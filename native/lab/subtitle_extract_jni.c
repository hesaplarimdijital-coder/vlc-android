// The JNI side of the embedded subtitles' extraction (app.nagmedar.lab.NativeLab.extractSubtitle).
#include <jni.h>

#include <stdlib.h>

#include "subtitle_extract.h"

/* its own flag: the Lab's Cancel and the transcription's do not stop an extraction, nor it them */
static volatile int g_extract_cancel;

typedef struct Listener {
    JNIEnv *env;
    jobject target;
    jmethodID progress;
} Listener;

static void on_progress(void *opaque, double fraction)
{
    Listener *l = opaque;
    (*l->env)->CallVoidMethod(l->env, l->target, l->progress, fraction);
    if ((*l->env)->ExceptionCheck(l->env))
        (*l->env)->ExceptionClear(l->env);
}

JNIEXPORT void JNICALL Java_app_nagmedar_lab_NativeLab_cancelSubtitleExtraction(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    g_extract_cancel = 1;
}

JNIEXPORT jstring JNICALL Java_app_nagmedar_lab_NativeLab_extractSubtitle(JNIEnv *env, jclass cls, jstring input, jint track,
                                                                          jstring output, jobject listener)
{
    (void)cls;
    g_extract_cancel = 0;
    jclass c = (*env)->GetObjectClass(env, listener);
    Listener l = {env, listener, (*env)->GetMethodID(env, c, "onProgress", "(D)V")};
    (*env)->DeleteLocalRef(env, c);
    if (!l.progress) {
        (*env)->ExceptionClear(env);
        return (*env)->NewStringUTF(env, "{\"error\":\"listener\",\"detail\":\"\",\"codec\":\"\",\"format\":\"\",\"events\":0,\"partial\":false}");
    }
    const char *in = (*env)->GetStringUTFChars(env, input, NULL);
    const char *out = (*env)->GetStringUTFChars(env, output, NULL);
    char *json = NULL;
    if (in && out)
        json = subtitle_extract(in, track, out, on_progress, &l, &g_extract_cancel);
    if (in)
        (*env)->ReleaseStringUTFChars(env, input, in);
    if (out)
        (*env)->ReleaseStringUTFChars(env, output, out);
    /* the JSON is ASCII: the codec's name, an FFmpeg or system error message */
    jstring result = (*env)->NewStringUTF(env, json ? json : "{\"error\":\"memory\",\"detail\":\"\",\"codec\":\"\",\"format\":\"\",\"events\":0,\"partial\":false}");
    free(json);
    return result;
}
