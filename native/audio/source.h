// A track read through FFmpeg into interleaved double frames at the file's own rate: the demuxer
// and the decoder, swresample for the sample format and the channel mix, and a sample-accurate
// seek (what lies before the target in the first decoded frames is dropped). A tracker module
// (S-911) is rendered by libopenmpt instead, at 48 kHz, with the same seek.
#ifndef NAG_SOURCE_H
#define NAG_SOURCE_H

#include "nagaudio.h"

#include <stdint.h>

typedef struct NagSource NagSource;

/**
 * Opens [url] with its frames mixed to [channels] (0: the file's own). On failure returns a
 * negative AVERROR and says why in [error].
 */
int nag_source_open(NagSource **out, const char *url, int channels, char *error, size_t error_size);

/** nag_source_open_ex flags */
#define NAG_SOURCE_DOP 1 /* a stereo DSD file as DoP frames (S-908): 24-bit words at the DSD rate / 16 */

/**
 * As nag_source_open; with NAG_SOURCE_DOP a stereo DSD file is not decoded but packed as DSD
 * over PCM: 16 DSD bits per channel per frame under the 0x05/0xFA markers, in 24-bit words
 * (NagSourceInfo.dop set, rate the DoP rate). Anything else opens as usual.
 */
int nag_source_open_ex(NagSource **out, const char *url, int channels, int flags, char *error, size_t error_size);
void nag_source_close(NagSource *source);

const NagSourceInfo *nag_source_info(const NagSource *source);
/** The channels of the frames read (the mix). */
int nag_source_channels(const NagSource *source);
/**
 * ITU-R BS.1770 weights of the channels read, for the loudness scanner: 1 for most, 1.41 for the
 * surrounds (Ls, Rs, Lb, Rb), 0 for the LFE. Fills up to [max]; returns the channels.
 */
int nag_source_channel_weights(const NagSource *source, double *weights, int max);

/**
 * Up to [max_frames] frames into [dst]: how many, 0 at the end of the file, a negative
 * AVERROR on a failure the decoder could not skip.
 */
int nag_source_read(NagSource *source, double *dst, int max_frames);

/** To [ms] (of the part, when one is set), sample accurate: the next read starts at that time. */
int nag_source_seek(NagSource *source, int64_t ms);

/**
 * S-808: only the part of the file from frame [start] to frame [end] (at the file's rate; end
 * -1: to its end), as a CUE sheet's track. From here on reads stop at [end], seeks and times
 * count from [start], and the info's duration is the part's. The next read starts at [start].
 * Negative on a seek failure.
 */
int nag_source_set_part(NagSource *source, int64_t start, int64_t end);

/** S-808: frames [start, end) of the part set, or 0 and -1 for the whole file. */
void nag_source_part(const NagSource *source, int64_t *start, int64_t *end);

#endif
