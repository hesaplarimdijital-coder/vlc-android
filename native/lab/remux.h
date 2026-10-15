// S-1206: the lossless remux engine (libavformat demux -> Matroska mux, packets copied untouched,
// Dolby Vision profile 7 -> 8.1 on request); S-1209: its verification (video packet hashes).
#ifndef NAGMEDAR_REMUX_H
#define NAGMEDAR_REMUX_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * The plan as text, one line per entry, fields separated by tabs (the app writes it from its
 * RemuxPlan; the host command line reads it from a file):
 *
 *   dv<TAB>keep|convert81          Dolby Vision: copied as it is, or profile 7 -> 8.1
 *   chapters<TAB>0|1               (default 1) chapters copied
 *   attachments<TAB>0|1            (default 1) attachments (fonts, covers) copied after the streams
 *   metadata<TAB>0|1               (default 1) the file's tags copied
 *   stream<TAB>v:0<TAB>default=1<TAB>forced=0<TAB>lang=tur<TAB>title=Yorum
 *
 * Streams are written in the order of their lines. A stream is named by its type and its
 * ordinal in the source: v:N the N-th video stream (cover pictures not counted), a:N the N-th
 * audio, s:N the N-th subtitle, as the media probe (S-802) lists them. Each key after the
 * name is optional and overrides the source's: default and forced flags (0/1), the language
 * (ISO 639-2) and the title (empty clears it). Without a stream line nothing is written.
 */
#define REMUX_MAX_STREAMS 64

typedef struct RemuxStreamPlan {
    char ref[8];
    int set_default;  /* -1 as the source, 0 clear, 1 set */
    int set_forced;
    int set_language; /* 0 as the source */
    char language[16];
    int set_title;
    char title[256];
} RemuxStreamPlan;

typedef struct RemuxPlan {
    int dv_convert;
    int chapters;
    int attachments;
    int metadata;
    int count;
    RemuxStreamPlan streams[REMUX_MAX_STREAMS];
} RemuxPlan;

/** Parses the text above; a negative return and [err] on a bad line. */
int remux_plan_parse(const char *text, RemuxPlan *plan, char *err, size_t err_size);

/** What the video hash covers; the verification uses the same scope. */
enum RemuxHashScope {
    REMUX_HASH_PACKETS = 0,          /* every video packet's bytes as the container holds them */
    REMUX_HASH_NAL_UNITS = 1,        /* every NAL unit without its framing (H.264, HEVC) */
    REMUX_HASH_BASE_LAYER = 2,       /* NAL units except the RPU (62) and the enhancement layer (63, nuh_layer_id > 0) */
    REMUX_HASH_AV1_OBUS = 3,         /* AV1 OBUs except temporal delimiters, redundant frame headers, tile lists and padding, which Matroska drops */
};

const char *remux_hash_scope_name(int scope);
int remux_hash_scope_of(const char *name); /* -1 when unknown */

typedef struct RemuxStreamStats {
    char ref[8];
    int source_index;
    int output_index;
    char codec[32];
    int64_t packets;
    int64_t bytes;
} RemuxStreamStats;

/** Why remux_run refused to write at all: the files are one, or the output already holds something. */
#define REMUX_REFUSED_SAME_FILE "same_file"
#define REMUX_REFUSED_OUTPUT_NOT_EMPTY "output_not_empty"

typedef struct RemuxStats {
    char error[512];
    /** One of the REMUX_REFUSED_* codes when nothing was written and the output must be left alone; empty otherwise. */
    char refused[24];
    int cancelled;
    int streams;
    RemuxStreamStats stream[REMUX_MAX_STREAMS];
    int attachments;
    int chapters;
    /* the video stream (the first planned one) */
    int video_codec_id;
    int64_t video_packets;
    int64_t video_units;        /* NAL units hashed (packets in the packet scope) */
    int hash_scope;
    char video_hash[65];        /* SHA-256, lower-case hex */
    double source_seconds;      /* the source's duration as libavformat reads it */
    double media_seconds;       /* how far the video got */
    int64_t bytes_in;
    int64_t bytes_out;
    double wall_seconds;
    double paused_seconds;      /* time the progress callback asked to wait */
    long peak_rss_kb;
    /* Dolby Vision conversion (dv convert81) */
    int dv_input_profile;
    char dv_input_el_type[8];
    int dv_output_profile;
    int64_t dv_rpus_converted;
    int64_t dv_rpus_failed;
    int64_t dv_el_nals_dropped;
    int64_t dv_el_bytes_dropped;
    char dv_first_rpu_error[256];
} RemuxStats;

/**
 * Progress, at most a few times a second: media seconds done of [total] (0 when unknown) and
 * bytes written. Returns 0 to go on, a negative number to stop (the output stays partial, the
 * stats say cancelled), or a number of milliseconds to wait before asking again (the app
 * pauses on a low battery or a hot device).
 */
typedef int (*remux_progress_fn)(void *opaque, double done_seconds, double total_seconds, int64_t bytes_out);

/**
 * Remuxes the file behind [input_fd] into Matroska on [output_fd] (both through libavformat's
 * custom IO, so a descriptor from the storage framework is enough). The output must be empty:
 * a new document, never a film "replaced"; and it must not be the source (compared by device
 * and inode, and by the paths /proc/self/fd resolves to). Either case is refused before a byte
 * is written, with [RemuxStats.refused] set, and the output is to be left alone.
 * Every packet is written as read: timestamps, durations, key frames, side data; the first
 * planned video stream is hashed on the way (see [RemuxStats]). With dv convert81 the HEVC
 * stream's RPUs are converted to profile 8.1 and its enhancement layer dropped (dvconvert.c),
 * and the hash covers the base layer only. [cancel] is polled between packets.
 * Returns 0 on success, a negative AVERROR otherwise (stats->error says why); the source is
 * only read.
 */
int remux_run(int input_fd, int output_fd, const RemuxPlan *plan, volatile int *cancel,
              remux_progress_fn progress, void *opaque, RemuxStats *stats);

/** The stats as one JSON object (UTF-8, caller frees). */
char *remux_stats_json(const RemuxStats *stats);

typedef struct RemuxVerification {
    char error[512];
    int ok;
    int cancelled;
    int hash_scope;
    char expected_hash[65];
    char hash[65];
    int64_t expected_packets;
    int64_t packets;
    int64_t units;
    double duration_seconds;
    /* Dolby Vision, when a profile was expected */
    int dv_expected_profile;
    int dv_profile;
    int dv_compatibility;
    int dv_el_present;
    int64_t rpus;               /* RPU NAL units parsed */
    int64_t rpus_bad;           /* not parsed, or not of the expected profile */
    int64_t packets_without_rpu;
} RemuxVerification;

/**
 * S-1209: reads the output behind [output_fd] back and hashes its first video stream in
 * [scope], comparing with [expected_hash] and [expected_packets]; with [dv_profile] > 0 the
 * stream's Dolby Vision configuration record must say that profile (8: single layer, HDR10
 * compatible, no enhancement layer) and every RPU must parse as it. [result].ok says whether
 * everything matched; a mismatch is not an error of the call.
 */
int remux_verify(int output_fd, int scope, const char *expected_hash, int64_t expected_packets, int dv_profile,
                 volatile int *cancel, remux_progress_fn progress, void *opaque, RemuxVerification *result);

char *remux_verification_json(const RemuxVerification *result);

#ifdef __cplusplus
}
#endif

#endif
