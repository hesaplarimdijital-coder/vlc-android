// S-802: what a media file holds, for the library's technical facts.
#ifndef NAGMEDAR_MEDIAPROBE_H
#define NAGMEDAR_MEDIAPROBE_H

#ifdef __cplusplus
extern "C" {
#endif

/** Version of the JSON below; the app re-probes files cached with an older one. */
#define MEDIA_PROBE_VERSION 1

/**
 * Opens [input] (a path; /proc/self/fd/N for a content URI) with libavformat and returns one
 * JSON object (UTF-8, caller frees with free()):
 *
 *   {"version":1,"error":"","format":"matroska,webm","brand":"","duration_ms":…,"size":…,
 *    "bitrate":…,"chapters":…,
 *    "audio":[{"codec":"truehd","profile":"Dolby TrueHD + Dolby Atmos","channels":8,
 *              "layout":"7.1","sample_rate":48000,"bits":24,"bitrate":0,"language":"eng",
 *              "title":"","default":true,"forced":false,"commentary":false}],
 *    "subtitles":[{"codec":"hdmv_pgs_subtitle","language":"tur","title":"","default":false,
 *                  "forced":false,"hearing_impaired":false}]}
 *
 * A file's video stream is not reported (Nağmedar 1.0 plays sound only; the probes of version 1
 * cached before have a "video" object the app passes over). [cancel] is kept for the callers;
 * nothing the probe does now outlasts the stream info.
 */
char *media_probe(const char *input, volatile int *cancel);

#ifdef __cplusplus
}
#endif

#endif
