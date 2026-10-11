// S-802: what a media file holds, for the library's technical badges (and later the TV analysis).
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
 *    "video":{"codec":"hevc","profile":"Main 10","width":3840,"height":2160,"fps":23.976,
 *             "bit_depth":10,"transfer":"smpte2084","primaries":"bt2020","matrix":"bt2020nc",
 *             "max_luminance":1000.0,"min_luminance":0.0001,"max_cll":1000,"max_fall":400,
 *             "hdr10plus":false,"dv_profile":7,"dv_level":6,"dv_compatibility":6,"dv_el":"FEL",
 *             "dv_el_present":true,"dv_rpu":true} | null,
 *    "audio":[{"codec":"truehd","profile":"Dolby TrueHD + Dolby Atmos","channels":8,
 *              "layout":"7.1","sample_rate":48000,"bits":24,"bitrate":0,"language":"eng",
 *              "title":"","default":true,"forced":false,"commentary":false}],
 *    "subtitles":[{"codec":"hdmv_pgs_subtitle","language":"tur","title":"","default":false,
 *                  "forced":false,"hearing_impaired":false}]}
 *
 * The Dolby Vision fields come from the configuration record and, for HEVC, from the first
 * RPU (libdovi): dv_profile 0 means no Dolby Vision, dv_el is "FEL" or "MEL" for profile 7.
 * hdr10plus is set by ST 2094-40 SEI messages or HDR10+ side data in the first packets.
 * [cancel], when not NULL, is polled while packets are read.
 */
char *media_probe(const char *input, volatile int *cancel);

#ifdef __cplusplus
}
#endif

#endif
