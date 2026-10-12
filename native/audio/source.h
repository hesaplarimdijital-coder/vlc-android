// A track read through FFmpeg into interleaved double frames at the file's own rate: the demuxer
// and the decoder, swresample for the sample format and the channel mix, and a sample-accurate
// seek (what lies before the target in the first decoded frames is dropped).
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
void nag_source_close(NagSource *source);

const NagSourceInfo *nag_source_info(const NagSource *source);
/** The channels of the frames read (the mix). */
int nag_source_channels(const NagSource *source);

/**
 * Up to [max_frames] frames into [dst]: how many, 0 at the end of the file, a negative
 * AVERROR on a failure the decoder could not skip.
 */
int nag_source_read(NagSource *source, double *dst, int max_frames);

/** To [ms], sample accurate: the next read starts at that time. */
int nag_source_seek(NagSource *source, int64_t ms);

#endif
