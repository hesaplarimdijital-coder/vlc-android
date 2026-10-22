// S-1202: a file's bitrate over time, from its packets, for the TV Lab's chart.
#ifndef NAGMEDAR_BITRATE_H
#define NAGMEDAR_BITRATE_H

#ifdef __cplusplus
extern "C" {
#endif

/** Version of the JSON below. */
#define BITRATE_VERSION 1

/**
 * Called as packets are read: [fraction] 0 … 1 of the file. Returns 0 to go on, anything else to
 * stop (the result then covers what was read, with "complete": false).
 */
typedef int (*bitrate_progress)(void *opaque, double fraction);

/**
 * Reads every packet of [input] (a path; /proc/self/fd/N for a content URI), at most the first
 * [max_seconds] of it (0 for all), and returns one JSON object (UTF-8, caller frees with
 * free()):
 *
 *   {"version":1,"error":"","complete":true,"duration_ms":…,"size":…,"seconds":7392,
 *    "total":[kbps of every second, all streams together],
 *    "video":[kbps of every second, the video stream alone],
 *    "average_kbps":…,"peak_1s_kbps":…,"peak_5s_kbps":…,
 *    "video_average_kbps":…,"video_peak_1s_kbps":…,"video_peak_5s_kbps":…,
 *    "streams":[{"index":0,"type":"video","codec":"hevc","bytes":…,"average_kbps":…,"peak_1s_kbps":…},…]}
 *
 * A second's figure is the bytes of the packets whose timestamps fall in it, as kilobits per
 * second (1 000 bits). The 5-second peak is the highest mean of five consecutive seconds, what a
 * player's buffer must take. [progress], when not NULL, is called about every half a percent;
 * [cancel], when not NULL, is polled while packets are read.
 */
char *media_bitrate(const char *input, double max_seconds, bitrate_progress progress, void *opaque, volatile int *cancel);

#ifdef __cplusplus
}
#endif

#endif
