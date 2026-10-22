// SP-03: Dolby Vision profile 7 -> 8.1 remux; its NAL rewrite is shared with the remux engine (S-1206).
#ifndef NAGMEDAR_DVCONVERT_H
#define NAGMEDAR_DVCONVERT_H

#include <stdint.h>
#include <stddef.h>

#include <libavcodec/packet.h>
#include <libavcodec/codec_par.h>
#include <libavutil/rational.h>

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
 * profile 8.1 (libdovi mode 2). Other streams are copied; MP4 keeps only audio and mov_text. A
 * stream the demuxer finds only after the file is open (an MPEG-TS whose PMT names one more
 * halfway) is left out.
 * [max_seconds] > 0 stops after that much media. [cancel] is polled between packets.
 * Returns 0 on success, a negative AVERROR otherwise (stats->error says why).
 */
int dvc_convert(const char *input, const char *output, double max_seconds, int mp4,
                volatile int *cancel, dvc_progress_fn progress, void *opaque, DvcStats *stats);

/** The stats as one JSON object (UTF-8, caller frees). */
char *dvc_stats_json(const DvcStats *stats, const char *input, const char *output);

/* ---- the pieces the remux engine (S-1206) shares with the converter ---------------------- */

/** HEVC NAL unit types of Dolby Vision: the RPU and the enhancement layer. */
#define DVC_NAL_RPU 62
#define DVC_NAL_EL 63

/** NAL unit type and nuh_layer_id of a NAL unit's first two header bytes. */
int dvc_nal_type(const uint8_t *nal);
int dvc_nal_layer(const uint8_t *nal);

/**
 * The size of the length field before each NAL unit in a stream's packets: from hvcC (HEVC)
 * or avcC (H.264) extradata; 0 for Annex B start codes.
 */
int dvc_length_size(int codec_id, const uint8_t *extradata, int size);

/** Called for every NAL unit of a packet (header included, framing excluded); a negative return stops. */
typedef int (*dvc_nal_fn)(void *opaque, const uint8_t *nal, size_t size);

/** Walks the NAL units of [data]: length-prefixed when [length_size] > 0, Annex B otherwise. */
int dvc_for_each_nal(const uint8_t *data, size_t size, int length_size, dvc_nal_fn fn, void *opaque);

/** A growing byte buffer (av_malloc'd, with AV_INPUT_BUFFER_PADDING_SIZE spare). */
typedef struct DvcBuf {
    uint8_t *data;
    size_t size, cap;
} DvcBuf;

/** The rewrite of an HEVC stream's packets to profile 8.1; [stats] counts what it did. */
typedef struct DvcRewriter {
    DvcStats *stats;
    int length_size; /* NAL length field size in the source packets, 0 = Annex B */
} DvcRewriter;

/**
 * [in]'s NAL units into [out] in the source framing: enhancement-layer units dropped, RPUs
 * converted with libdovi, everything else copied. Returns a negative AVERROR when the stream
 * cannot be converted (stats->error says why); a bad RPU is dropped and counted instead.
 */
int dvc_rewrite_packet(DvcRewriter *c, const AVPacket *in, DvcBuf *out);

/**
 * hvcC without the enhancement layer's parameter sets, into [dst] (av_malloc'd). Returns 1
 * when written, 0 when [src] is not hvcC (left as is), a negative AVERROR on a broken record.
 */
int dvc_filter_hvcc(const uint8_t *src, int size, uint8_t **dst, int *dst_size);

/** Replaces [par]'s Dolby Vision configuration record with profile 8.1 at [level]. */
int dvc_profile81_record(AVCodecParameters *par, int level);

/** The Dolby Vision level of a stream from its pixel rate, for a source without a record. */
int dvc_dolby_level(const AVCodecParameters *par, AVRational fps);

/**
 * How many frames the decoder reorders, from the extradata's SPS (HEVC: sps_max_num_reorder_pics
 * of the base layer in hvcC or Annex B; H.264: the VUI's max_num_reorder_frames in avcC or
 * Annex B), or -1 when it does not say.
 */
int dvc_reorder_depth(int codec_id, const uint8_t *extradata, int size);

#define DVC_MAX_REORDER 16

/**
 * The depth the generator takes when the SPS does not say: the deepest it holds, so that no
 * stream reorders past it (x265 and Blu-ray encoders use 2 to 4, but nothing forbids more).
 * The SP-03 converter and the remux (S-1206) both use it.
 */
#define DVC_FALLBACK_REORDER DVC_MAX_REORDER

/**
 * Decoding timestamps for a Matroska H.264 or HEVC stream (the container has none and
 * libavformat cannot derive them without a decoder): the smallest of the last depth + 1
 * presentation times.
 */
typedef struct DvcDtsGen {
    int depth;
    int primed;
    int64_t buf[DVC_MAX_REORDER + 1];
    int64_t last;
} DvcDtsGen;

void dvc_dts_init(DvcDtsGen *g, int depth);
int64_t dvc_dts_next(DvcDtsGen *g, int64_t pts, int64_t duration);

#ifdef __cplusplus
}
#endif

#endif
