// SP-03: Dolby Vision profile 7 -> 8.1 remux.
#ifndef NAGMEDAR_DVCONVERT_H
#define NAGMEDAR_DVCONVERT_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct DvcStats {
    int input_profile;        /* from the configuration record or the first RPU; 0 = no Dolby Vision */
    char input_el_type[8];    /* FEL / MEL for profile 7 */
    int output_profile;       /* profile of the first converted RPU */
    int64_t video_packets;
    int64_t rpus_converted;
    int64_t rpus_failed;
    int64_t el_nals_dropped;
    int64_t el_bytes_dropped;
    int64_t bytes_in;
    int64_t bytes_out;
    int streams_copied;
    int streams_skipped;
    double media_seconds;     /* converted duration */
    double wall_seconds;
    long peak_rss_kb;
    char first_rpu_error[256];
    char error[512];
} DvcStats;

/** Progress: converted media seconds, total seconds (0 if unknown), bytes written so far. */
typedef void (*dvc_progress_fn)(void *opaque, double done_seconds, double total_seconds, int64_t bytes_out);

/**
 * Remuxes [input] to [output] (Matroska, or MP4 when [mp4] is set): the HEVC base layer is
 * copied bit for bit, enhancement-layer NAL units are dropped and every RPU is converted to
 * profile 8.1 (libdovi mode 2). Other streams are copied; MP4 keeps only audio and mov_text.
 * [max_seconds] > 0 stops after that much media. [cancel] is polled between packets.
 * Returns 0 on success, a negative AVERROR otherwise (stats->error says why).
 */
int dvc_convert(const char *input, const char *output, double max_seconds, int mp4,
                volatile int *cancel, dvc_progress_fn progress, void *opaque, DvcStats *stats);

/** The stats as one JSON object (UTF-8, caller frees). */
char *dvc_stats_json(const DvcStats *stats, const char *input, const char *output);

#ifdef __cplusplus
}
#endif

#endif
