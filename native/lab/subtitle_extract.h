// A film's embedded text subtitles written out as a file of their own (SRT, or ASS/SSA kept as they are).
#ifndef NAGMEDAR_SUBTITLE_EXTRACT_H
#define NAGMEDAR_SUBTITLE_EXTRACT_H

#ifdef __cplusplus
extern "C" {
#endif

/** How far an extraction is: [fraction] of the file read, 0..1; every call comes on the thread that runs it. */
typedef void (*SeProgress)(void *opaque, double fraction);

/**
 * Writes subtitle track [track] of [input] (the n-th subtitle stream, 0 first, as the media probe
 * lists them; a path or /proc/self/fd/N) into [output] as a NEW file: SubRip, WebVTT, MP4 timed
 * text and plain text become SRT, ASS and SSA stay ASS and SSA with their header and styles. The
 * file is read once from the start to the end, without seeking where the container allows it,
 * and only the lines are kept in memory. [cancel] set to non-zero stops it soon. Returns a JSON
 * object (the caller frees it): "error" ("" or "open", "no_stream", "image", "unsupported",
 * "empty", "too_large", "write", "cancelled", "memory"), "detail", "codec", "format" ("srt",
 * "ass", "ssa") and "events".
 */
char *subtitle_extract(const char *input, int track, const char *output, SeProgress progress, void *opaque, volatile int *cancel);

#ifdef __cplusplus
}
#endif

#endif
