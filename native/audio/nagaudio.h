// Nağmedar Ses Motoru (S-901, ADR-06): the music engine behind the playback service for the
// local library. A C core with no Android in it, so that the host builds and tests it:
//
//   source   FFmpeg demuxes and decodes a file into 64-bit float frames at the file's own
//            sample rate, channels mixed to the output's (the integers come through exactly:
//            a 24-bit sample is 2^-23 steps of a double).
//   voice    a track being played: its source, a decoder thread and a ring buffer of frames.
//            Two voices at most: the one playing and the next, decoded ahead for a gapless
//            hand-over (S-904) or a crossfade (S-905).
//   render   the sink's thread pulls frames: the voices' rings are read, mixed, volume and the
//            DSP chain (S-906) applied in double precision, and the result written in the
//            output format (16, 24, 32-bit integers or float), dithered when the depth drops.
//            Nothing in this path blocks or allocates: it runs on AAudio's callback thread.
//   sink     where the frames go: AAudio (Android), a pull sink fed by Java's AudioTrack
//            (the bit-perfect path, S-902), the USB DAC driven directly (S-903), or a file
//            (tests). A sink's format follows the track's: same rate, same depth or wider.
//   events   state changes, track hand-overs, format changes and errors, delivered on an event
//            thread of the engine's, never on the render thread.
#ifndef NAGAUDIO_H
#define NAGAUDIO_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ----------------------------------------------------------------- formats */

typedef enum NagSampleFormat {
    NAG_FMT_NONE = 0,
    NAG_FMT_S16,   /* 16-bit signed, little endian */
    NAG_FMT_S24_3, /* 24-bit signed, packed in 3 bytes, little endian */
    NAG_FMT_S32,   /* 32-bit signed, little endian; 24-bit sources left-justified */
    NAG_FMT_F32,   /* 32-bit float, -1..1 */
    NAG_FMT_F64,   /* 64-bit float, the engine's own (tests) */
} NagSampleFormat;

typedef struct NagFormat {
    int rate;
    int channels;
    NagSampleFormat sample_format;
} NagFormat;

int nag_format_bytes_per_sample(NagSampleFormat f);
int nag_format_bits(NagSampleFormat f);
const char *nag_format_name(NagSampleFormat f);

/* -------------------------------------------------------------- source info */

#define NAG_NAME_MAX 64
#define NAG_TAG_MAX 256

typedef struct NagSourceInfo {
    int rate;
    int channels;      /* the file's channels */
    int bits;          /* bits per sample of the file (0 for a float or lossy codec) */
    int is_float;      /* the decoder outputs float (lossy codecs, float PCM) */
    int64_t duration_ms;
    int64_t bitrate;   /* bits per second, 0 if unknown */
    char codec[NAG_NAME_MAX];
    char container[NAG_NAME_MAX];
    /* ReplayGain / R128 tags (S-907), as in the file; NAN when absent */
    double track_gain_db, track_peak, album_gain_db, album_peak;
    /* S-908: a DSD file's bit rate (2822400 for DSD64), 0 for PCM; dop when it comes as DSD over PCM */
    int dsd_rate;
    int dop;
    /* S-803: the composer tag (ID3 TCOM, Vorbis COMPOSER, MP4 ©wrt), "" when absent */
    char composer[NAG_TAG_MAX];
} NagSourceInfo;

/* ------------------------------------------------------------------- events */

typedef enum NagEventType {
    NAG_EVENT_STATE = 1,     /* arg: NagState */
    NAG_EVENT_TRACK,         /* arg: the track id now rendering (a gapless hand-over or a start) */
    NAG_EVENT_TRACK_ENDED,   /* arg: the track id that ended */
    NAG_EVENT_FORMAT,        /* the output format changed: text "rate/channels/format" */
    NAG_EVENT_ERROR,         /* arg: track id, text: what went wrong */
    NAG_EVENT_DEVICE_LOST,   /* the sink lost its device (S-903): the owner picks another */
    NAG_EVENT_QUEUE_EMPTY,   /* the last voice ended and no next was set */
} NagEventType;

typedef enum NagState {
    NAG_STATE_STOPPED = 0,
    NAG_STATE_PLAYING,
    NAG_STATE_PAUSED,
    NAG_STATE_ENDED,
} NagState;

typedef struct NagEvent {
    NagEventType type;
    int64_t arg;
    char text[160];
} NagEvent;

/** Called on the engine's event thread; a NULL [event] says that thread is ending. */
typedef void (*nag_event_fn)(void *opaque, const NagEvent *event);

/* -------------------------------------------------------------------- sinks */

typedef struct NagSink NagSink;
typedef struct NagAudio NagAudio;

/**
 * A sink: opened for a format (it answers with the one it got, which may be wider), it pulls
 * frames from nag_audio_render() on a thread of its own while started.
 */
struct NagSink {
    const char *name;
    /* Opens or re-opens the device for [want]; [got] is what the device took (rate and channels
     * equal to [want], the sample format the same or wider). */
    int (*open)(NagSink *sink, NagAudio *engine, const NagFormat *want, NagFormat *got);
    int (*start)(NagSink *sink);
    int (*pause)(NagSink *sink);
    void (*close)(NagSink *sink);
    /** Frames between what render() last produced and the listener's ears; 0 if unknown. */
    int64_t (*latency_frames)(NagSink *sink);
    /** Whether the device goes bit for bit: no mixer, no resampler (S-902, S-903). */
    int (*bit_perfect)(NagSink *sink);
    void (*destroy)(NagSink *sink);
    void *priv;
};

/* The pull sink: the owner (Java's AudioTrack thread, a test) calls nag_audio_render itself. */
NagSink *nag_sink_pull_new(void);
/** S-902: the owner tells whether its output takes the samples as they are, and its latency. */
void nag_sink_pull_set_bit_perfect(NagSink *sink, int bit_perfect, int64_t latency_frames);
#ifdef __ANDROID__
/* AAudio, exclusive and low latency when the device allows, shared otherwise. */
NagSink *nag_sink_aaudio_new(void);
#endif

/*
 * S-903: a USB Audio Class DAC driven directly with libusb, behind the file descriptor of a
 * UsbDeviceConnection (Android) or a usbfs device (tests). Bit-perfect: the samples go to the
 * device's endpoint as they are, at the track's rate; the feature unit's volume control is the
 * volume. A cable pulled ends in nag_audio_sink_lost, after which open fails.
 */
NagSink *nag_sink_usb_new(int fd);
/** The hardware volume, 0..1 as attenuation under the device's loudest; -1 without a volume control. */
int nag_sink_usb_set_volume(NagSink *sink, double volume);
int nag_sink_usb_has_volume(NagSink *sink);
/** Why the last open failed. */
const char *nag_sink_usb_error(NagSink *sink);

/**
 * For sinks: the device went away or the stream broke (AAudio's disconnect, a USB cable
 * pulled). The engine reopens the sink on its event thread and tells its owner
 * (NAG_EVENT_DEVICE_LOST); the owner may set another sink meanwhile.
 */
void nag_audio_sink_lost(NagAudio *engine, NagSink *sink);
/* A sink that writes what it pulls to [path] as raw samples; "-" for stdout (tests, the CLI). */
NagSink *nag_sink_file_new(const char *path);

/* ------------------------------------------------------------------- engine */

typedef struct NagConfig {
    /** Seconds of decoded audio each voice keeps ahead of the render (0.5 s by default). */
    double ring_seconds;
    /** The output's channels (2 by default): sources are mixed to them. */
    int channels;
    /** The widest sample format the sinks are asked for (NAG_FMT_S32 by default). */
    NagSampleFormat max_format;
    /** One sample format for every track, whatever its depth (NAG_FMT_NONE: the track's own). */
    NagSampleFormat fixed_format;
    /** TPDF dither when a track's depth exceeds the output's (on by default). */
    int dither;
} NagConfig;

void nag_config_defaults(NagConfig *config);

NagAudio *nag_audio_new(const NagConfig *config, nag_event_fn on_event, void *opaque);
void nag_audio_free(NagAudio *engine);
/** The [opaque] given to nag_audio_new. */
void *nag_audio_opaque(NagAudio *engine);

/** The sink the engine renders into; the engine owns it from here on. NULL detaches. */
int nag_audio_set_sink(NagAudio *engine, NagSink *sink);
NagSink *nag_audio_sink(NagAudio *engine);

/**
 * Starts [url] (a path or /proc/self/fd/N) as track [id], at [start_ms]: the voice playing
 * is replaced at once. Playback goes on in the state the engine is in (playing after play()).
 */
int nag_audio_open(NagAudio *engine, int64_t id, const char *url, int64_t start_ms);

/**
 * The track to follow the one playing, decoded ahead (S-904); NULL clears it. With [crossfade]
 * the two overlap for the crossfade's duration (S-905), without it the next starts on the
 * sample after the last.
 */
int nag_audio_set_next(NagAudio *engine, int64_t id, const char *url, int crossfade);

/*
 * S-808: a part of a file as a track of its own (a CUE sheet's): from [from] to [to] in CUE
 * frames, 1/75 s (a CD sector; [to] -1: the file's end). The part's bounds are sample exact at
 * the file's rate (44.1 and 48 kHz and their multiples divide by 75), so two parts that meet
 * play on without a gap or a repeated sample, as a gapless hand-over does. Times, seeks and the
 * length count from the part's start.
 */
#define NAG_CUE_FRAMES_PER_SECOND 75
int nag_audio_open_part(NagAudio *engine, int64_t id, const char *url, int64_t start_ms, int64_t from, int64_t to);
int nag_audio_set_next_part(NagAudio *engine, int64_t id, const char *url, int crossfade, int64_t from, int64_t to);

typedef enum NagCrossfadeCurve {
    NAG_CROSSFADE_EQUAL_POWER = 0, /* cosine and sine: the loudness stays level */
    NAG_CROSSFADE_LINEAR = 1,
} NagCrossfadeCurve;

/**
 * S-905: how long a crossfade lasts (0 turns it off; at most NAG_CROSSFADE_MAX_MS) and its
 * curve. A track opened from then on keeps enough decoded audio ahead for it.
 */
void nag_audio_set_crossfade(NagAudio *engine, int ms, NagCrossfadeCurve curve);
int nag_audio_crossfade_ms(NagAudio *engine);
#define NAG_CROSSFADE_MAX_MS 12000

int nag_audio_play(NagAudio *engine);
int nag_audio_pause(NagAudio *engine);
int nag_audio_stop(NagAudio *engine);
int nag_audio_seek(NagAudio *engine, int64_t ms);

NagState nag_audio_state(NagAudio *engine);
int64_t nag_audio_time_ms(NagAudio *engine);
int64_t nag_audio_length_ms(NagAudio *engine);
int64_t nag_audio_track_id(NagAudio *engine);
/** The playing track's file, as the source read it; 0 when none. */
int nag_audio_source_info(NagAudio *engine, NagSourceInfo *info);
/** The format the sink is open for; NAG_FMT_NONE when it is not. */
NagFormat nag_audio_output_format(NagAudio *engine);

/** 0..1 linear; 1 leaves the samples untouched. */
void nag_audio_set_volume(NagAudio *engine, double volume);
double nag_audio_volume(NagAudio *engine);

/**
 * The render: [frames] frames into [out] in the output format, mixed from the voices; what no
 * voice fills is silence. Returns the frames that came from a voice. Called by the sink's
 * thread only; realtime safe.
 */
int nag_audio_render(NagAudio *engine, void *out, int frames);

/* ---------------------------------------------------------------- DSP chain */

/*
 * S-906: the chain between the voices and the output, in 64-bit floats:
 *   preamp → 20-band graphic EQ → parametric EQ → headphone profile → limiter → clip meter.
 * One bypass turns the whole chain off; with it off, or with nothing in it changing the
 * samples, the engine stays bit-perfect.
 */

#define NAG_GRAPHIC_BANDS 20
#define NAG_EQ_BANDS 10
#define NAG_DSP_LOOKAHEAD_MAX_MS 10.0
#define NAG_DSP_RATE_MAX 768000

/** The graphic EQ's centre frequencies: ISO half-octave bands, 25 Hz to 18 kHz. */
extern const double nag_graphic_frequencies[NAG_GRAPHIC_BANDS];

typedef enum NagEqType {
    NAG_EQ_PEAK = 0,
    NAG_EQ_LOW_SHELF,
    NAG_EQ_HIGH_SHELF,
    NAG_EQ_LOW_PASS,
    NAG_EQ_HIGH_PASS,
} NagEqType;

typedef struct NagEqBand {
    int enabled;
    NagEqType type;
    double freq;    /* Hz */
    double gain_db; /* peaks and shelves; ignored by the passes */
    double q;
} NagEqBand;

typedef struct NagDspParams {
    int bypass; /* the one key: the chain off, whatever is in it */
    double preamp_db;
    /** With auto_preamp the chain's largest gain is taken back, so an EQ boost cannot clip. */
    int auto_preamp;
    int graphic_enabled;
    double graphic_db[NAG_GRAPHIC_BANDS];
    int parametric_enabled;
    NagEqBand parametric[NAG_EQ_BANDS];
    /** A headphone correction: its own bands and preamp (AutoEq's ParametricEQ, say). */
    int profile_enabled;
    char profile_name[64];
    double profile_preamp_db;
    NagEqBand profile[NAG_EQ_BANDS];
    int limiter_enabled;
    double limiter_threshold_db; /* dBFS, at most 0 */
    double limiter_release_ms;
    double limiter_lookahead_ms; /* at most NAG_DSP_LOOKAHEAD_MAX_MS; the chain's latency */
} NagDspParams;

/** Nothing in the chain: no bypass, 0 dB everywhere, the limiter off (its defaults -1 dBFS, 100 ms, 5 ms). */
void nag_dsp_params_defaults(NagDspParams *params);
/** Whether [params] leave the samples as they are: bypassed, or nothing in the chain active. */
int nag_dsp_params_identity(const NagDspParams *params);

/**
 * The chain's parameters, copied; the render takes them over at its next block, the filters
 * keep their memory so a change does not click. Realtime safe on the render's side.
 */
int nag_audio_set_dsp(NagAudio *engine, const NagDspParams *params);
/** The parameters last set (the defaults before any). */
void nag_audio_get_dsp(NagAudio *engine, NagDspParams *params);
/** Whether the chain changes the samples right now. */
int nag_audio_dsp_active(NagAudio *engine);
/** The limiter's lookahead at the output's rate, in frames (0 without it). */
int64_t nag_audio_dsp_latency_frames(NagAudio *engine);

typedef struct NagDspMeter {
    int64_t frames;      /* frames through the chain since the last reset */
    int64_t clipped;     /* samples beyond full scale at the chain's end */
    double peak;         /* the largest |sample| before the limiter, 1.0 = full scale */
    double reduction_db; /* the limiter's largest gain reduction, >= 0 */
} NagDspMeter;

/** The clip meter (S-906); with [reset], the counts start over after the read. */
void nag_audio_dsp_meter(NagAudio *engine, NagDspMeter *meter, int reset);

/*
 * S-912: the A/B listening test. The engine keeps the energy of what goes into the chain and
 * what comes out, over the last stretch of playing (a window of about ten seconds), so the
 * chain's level change is known: nag_audio_dsp_level_db. Side B plays the chain bypassed
 * with that change applied as a gain instead, so both sides are as loud (within 0.1 dB once
 * the window holds a few seconds); the blind part (which side is which) is the owner's.
 * Nothing but the sound tells the sides apart: the chain runs on side B too (its filters,
 * the meter and the level keep going), B's samples are delayed as the chain delays A's, and
 * a switch crossfades over 10 ms.
 */
double nag_audio_dsp_level_db(NagAudio *engine);
/** 0: side A (the chain as set); 1: side B (bypassed, level matched). */
void nag_audio_set_ab_side(NagAudio *engine, int side);
int nag_audio_ab_side(NagAudio *engine);
/** The gain side B applies, in dB (frozen when B was chosen). */
double nag_audio_ab_gain_db(NagAudio *engine);

/* --------------------------------------------------------------- ReplayGain */

/* S-907: the gain the tags ask for, applied per track before the DSP chain. */

typedef enum NagReplayGainMode {
    NAG_REPLAYGAIN_OFF = 0,
    NAG_REPLAYGAIN_TRACK,
    NAG_REPLAYGAIN_ALBUM, /* the album's gain, the track's when the album has none */
} NagReplayGainMode;

typedef struct NagReplayGain {
    NagReplayGainMode mode;
    double preamp_db;       /* on top of the tags' gain */
    double untagged_db;     /* for a track without tags (0: as it is) */
    int prevent_clipping;   /* the gain is held back where the tags' peak would pass full scale */
} NagReplayGain;

void nag_audio_set_replaygain(NagAudio *engine, const NagReplayGain *settings);
void nag_audio_get_replaygain(NagAudio *engine, NagReplayGain *settings);
/** The gain in dB the playing track gets from this (0 when off or untagged and no untagged_db). */
double nag_audio_replaygain_db(NagAudio *engine);

/* ------------------------------------------------------------ output depth */

/**
 * S-902: the sample formats the sink is asked for, from here on: at least [min_format] (NONE:
 * the track's own depth) and at most [max_format] (NONE: no cap) for integer tracks; floats
 * stay F32. An output that takes only 24-bit words asks for S24_3 both ways. The sink is
 * reopened for the track playing when its depth changes.
 */
void nag_audio_set_format_range(NagAudio *engine, NagSampleFormat min_format, NagSampleFormat max_format);

/* ---------------------------------------------------------------------- DSD */

/*
 * S-908: how a DSD file (DSF, DFF) plays. As PCM it is decoded (FFmpeg's filter, 8 bits to a
 * sample: DSD64 to 352.8 kHz float). As DoP a stereo file's bits go out as they are, under the
 * DoP markers in 24-bit words at the DSD rate / 16 (DSD64: 176.4 kHz), which only a DAC that
 * takes DoP on a bit-perfect output turns back into DSD; the engine then leaves the samples
 * alone (no gain, no chain, no crossfade). A track opened from then on follows the mode.
 */
typedef enum NagDsdMode {
    NAG_DSD_PCM = 0,
    NAG_DSD_DOP = 1,
} NagDsdMode;

void nag_audio_set_dsd_mode(NagAudio *engine, NagDsdMode mode);
NagDsdMode nag_audio_dsd_mode(NagAudio *engine);

/* ------------------------------------------------------------ tempo, pitch */

/*
 * S-909: the pace without a pitch change (WSOLA), 0.25 to 4; the pitch in semitones without
 * a pace change (stretched, then resampled), -24 to 24. At 1 and 0 the stage is out of the
 * way; above 192 kHz it stays out. The render takes a change at its next block.
 */
void nag_audio_set_tempo(NagAudio *engine, double tempo);
double nag_audio_tempo(NagAudio *engine);
void nag_audio_set_pitch(NagAudio *engine, double semitones);
double nag_audio_pitch(NagAudio *engine);
/** S-909: the balance, -1 (left only) to 1 (right only); the other side is attenuated linearly. */
void nag_audio_set_balance(NagAudio *engine, double balance);
double nag_audio_balance(NagAudio *engine);
/** S-909: the channels mixed to one, on all of them (a mono recording on one earphone, say). */
void nag_audio_set_mono(NagAudio *engine, int mono);
int nag_audio_mono(NagAudio *engine);

/* S-907: the EBU R128 scanner (ITU-R BS.1770-4) and ReplayGain 2.0's gains (-18 LUFS). */

typedef struct NagLoudnessResult {
    double loudness_lufs; /* integrated; -HUGE_VAL for silence */
    double true_peak;     /* 4x oversampled, 1.0 = full scale */
    double sample_peak;
    double gain_db;       /* ReplayGain 2.0: -18 - loudness */
    int64_t frames;
    int rate;
} NagLoudnessResult;

typedef struct NagLoudnessScan NagLoudnessScan;

/** An album's accumulator: every file scanned into it adds its blocks to the album's gating. */
NagLoudnessScan *nag_loudness_scan_new(void);
void nag_loudness_scan_free(NagLoudnessScan *scan);
/**
 * Scans [url] whole, into [result]; with [album] its blocks count for the album too. [cancel]
 * (may be NULL) set non-zero from another thread stops it with -2. Negative on failure, with
 * [error].
 */
int nag_loudness_scan_file(NagLoudnessScan *album, const char *url, NagLoudnessResult *result, const int *cancel, char *error,
                           size_t error_size);
/** The album's result over everything scanned into it; -1 when nothing was. */
int nag_loudness_scan_result(const NagLoudnessScan *album, NagLoudnessResult *result);

/* S-907: the tags, written in place with TagLib (ID3v2, Vorbis comments, APE, MP4, Opus R128). */

typedef struct NagReplayGainTags {
    double track_gain_db, track_peak; /* NAN leaves a tag alone */
    double album_gain_db, album_peak;
    double track_lufs, album_lufs;    /* REPLAYGAIN_*_RANGE is not written; these go to R128 for Opus */
} NagReplayGainTags;

/**
 * Writes the tags into the file behind [fd] (read-write; [name] with its extension helps the
 * type detection); the descriptor stays the caller's. 0, or negative with [error].
 */
int nag_tags_write_replaygain(int fd, const char *name, const NagReplayGainTags *tags, char *error, size_t error_size);

/**
 * S-1003: the lyrics of the file behind [fd] (read; [name] with its extension helps the type
 * detection) as JSON, {"plain": "...", "synced": [[ms, "line"], ...]}, either part missing; NULL
 * when it has none. The descriptor stays the caller's; the caller frees the text.
 */
char *nag_tags_read_lyrics(int fd, const char *name);

/**
 * S-1005: writes lyrics into the tags of the file behind [fd] (read-write; [name] with its
 * extension helps the type detection), as nag_tags_read_lyrics reads them: [text] as the plain
 * lyrics (USLT, LYRICS, ©lyr; LRC text when the lines are timed), [synced] as ID3v2's SYLT
 * (MP3, AIFF, WAV), JSON [[ms, "line"], ...] in milliseconds. NULL or "" removes what the file
 * had of the kind; nothing else in the tags changes. The descriptor stays the caller's. 0, or
 * negative with [error].
 */
int nag_tags_write_lyrics(int fd, const char *name, const char *text, const char *synced, char *error, size_t error_size);

/**
 * S-1006: the front cover in the tags of the file behind [fd] (else its first picture), its bytes
 * and their *size; NULL when there is none. The descriptor stays the caller's; the caller frees.
 */
unsigned char *nag_tags_read_cover(int fd, const char *name, size_t *size);

/** S-1006: the first value of tag [key] (TagLib's property name), UTF-8; NULL for none. The caller frees. */
char *nag_tags_read_property(int fd, const char *name, const char *key);

/*
 * S-1010: the tags and front cover of the file behind [src_fd] copied onto the file behind
 * [dst_fd] (read-write; the names, with their extensions, help the type detection), through
 * TagLib's property map: Vorbis comments and METADATA_BLOCK_PICTURE for Opus, ID3v2.4 with
 * APIC for MP3, MP4 atoms with covr for M4A. [overrides] ("KEY=value" lines; "KEY=" removes a
 * tag; may be NULL) go over the source's. What says how the source was made does not go along
 * (ENCODER, an embedded CUESHEET, iTunes' gapless and level atoms, the source's ReplayGain
 * and R128 tags); [gain] is written instead, as nag_tags_write_replaygain writes it, but for
 * Opus as R128_* alone (RFC 7845 leaves REPLAYGAIN_* out of Opus). *written gets the tags
 * written, *cover whether a cover went along. 0, or negative with [error].
 */
int nag_tags_copy(int src_fd, const char *src_name, int dst_fd, const char *dst_name, const char *overrides,
                  const NagReplayGainTags *gain, int *written, int *cover, char *error, size_t error_size);

/**
 * S-1010: the ReplayGain the file behind [fd] carries, from its tags (REPLAYGAIN_*, and
 * R128_* converted to ReplayGain's -18 LUFS); NAN where it has none. Returns how many it has.
 */
int nag_tags_read_replaygain(int fd, const char *name, NagReplayGainTags *gain);

/* ----------------------------------------------------------------- converter */

/*
 * S-1010: a file, or a CUE sheet's part of it, decoded by the engine's source and encoded anew:
 *   Opus  libopus in Ogg, at 48 kHz (the source resampled), VBR at the bit rate asked; the
 *         encoder's lookahead is the header's pre-skip and the last page's granule ends the
 *         audio on its last sample.
 *   MP3   LAME, VBR at a quality (-V 0..9) or a constant bit rate, at the source's rate when
 *         MP3 has it (else 44.1 or 48 kHz); the Xing/LAME frame carries the encoder delay and
 *         the padding.
 *   AAC   FFmpeg's encoder (AAC-LC) in an M4A, at the source's rate up to 96 kHz; the edit
 *         list hides the priming and the padding.
 * Mono stays mono, stereo stereo; more channels are mixed to stereo. The output goes to
 * [out_fd] (read-write, seekable, new and empty: a file that holds anything, or is the source
 * itself, is refused, so nothing is ever truncated), then the tags and the front cover are
 * copied from [in_fd] (nag_tags_copy). When the source carries ReplayGain, the track's gain and
 * peak are measured again on what was written; the album's gain stays the source's (its peak
 * at least the track's).
 */
typedef enum NagConvertCodec {
    NAG_CONVERT_OPUS = 0,
    NAG_CONVERT_MP3 = 1,
    NAG_CONVERT_AAC = 2,
} NagConvertCodec;

typedef struct NagConvertOptions {
    NagConvertCodec codec;
    /** Bits per second: Opus's target, AAC's, MP3's constant rate; 0 for MP3's VBR. */
    int bitrate;
    /** MP3 with bitrate 0: LAME's VBR quality, 0 (best) to 9. */
    int vbr_quality;
    /** The output's rate (0: the source's where the codec has it); Opus is always 48 kHz. */
    int rate;
    /** S-808: the CUE part, in CUE frames (1/75 s): from 0 and to -1 for the whole file. */
    int64_t from, to;
    /** "KEY=value" lines over the source's tags ("KEY=" removes one); NULL for none. */
    const char *tags;
} NagConvertOptions;

typedef struct NagConvertResult {
    int64_t frames;       /* frames encoded, at the output's rate */
    int rate;
    int channels;
    int64_t bytes;        /* the output's size, tags included */
    int tags;             /* tags written */
    int cover;            /* whether a cover went along */
    double lufs;          /* the output's loudness when its ReplayGain was measured, NAN otherwise */
    double gain_db;       /* the track gain written (ReplayGain's, -18 LUFS), NAN for none */
} NagConvertResult;

/** Progress, 0..1, on the converting thread; a non-zero return stops the conversion. */
typedef int (*nag_convert_progress_fn)(void *opaque, double fraction);

#define NAG_CONVERT_CANCELLED (-2)

/**
 * Converts the file behind [in_fd] ([in_name] with its extension helps the tag reading) into
 * [out_fd] ([out_name] likewise). 0 on success; NAG_CONVERT_CANCELLED when [progress] asked to
 * stop; another negative value with [error] saying why. The descriptors stay the caller's; a
 * failed or stopped output is left as it is, for the caller to remove.
 */
int nag_convert(int in_fd, const char *in_name, int out_fd, const char *out_name, const NagConvertOptions *options,
                nag_convert_progress_fn progress, void *opaque, NagConvertResult *result, char *error, size_t error_size);

/** The result as JSON into [out]: frames, rate, channels, bytes, tags, cover, lufs, gain_db (null when NAN). */
void nag_convert_result_json(const NagConvertResult *result, char *out, size_t size);

/* -------------------------------------------------------------- visualisers */

/*
 * S-611: the visualisers' tap. Off by default, when the render does nothing for it. On, the
 * render keeps the last frames it played, after the DSP chain and before the volume (the first
 * two channels; mono on both), and nag_audio_scope analyses those the listener hears now: the
 * sink's latency is taken back from the newest.
 */
#define NAG_SCOPE_FFT_MIN 1024
#define NAG_SCOPE_FFT_MAX 16384
#define NAG_SCOPE_WAVE_MAX 1024
#define NAG_SCOPE_FLOOR_DB -120.0

typedef struct NagScopeFrame {
    /** Frames the tap took since it was turned on; the last read's count when nothing played since. */
    uint64_t frames;
    int rate;
    /** spectrum_db holds fft_size / 2 bins, bin k at k * rate / fft_size Hz. */
    int fft_size;
    /** Each channel's largest sample since the last read, dBFS. */
    double peak_db[2];
    /** Each channel's RMS over the FFT's window, dBFS (a full-scale sine reads -3.01). */
    double rms_db[2];
    /** Both channels' mean power per bin, Hann-windowed, in dB of a sine's amplitude (a sine of
     * amplitude 0.5 on a bin's frequency reads -6.02 there). */
    float spectrum_db[NAG_SCOPE_FFT_MAX / 2];
    int wave_points;
    /** About 20 ms of each channel from a rising zero crossing, -1..1, wave_points points. */
    float wave[2][NAG_SCOPE_WAVE_MAX];
} NagScopeFrame;

/** Turns the tap on or off; -1 when its memory (1 MB) cannot be had. */
int nag_audio_set_scope(NagAudio *engine, int enabled);
int nag_audio_scope_enabled(NagAudio *engine);
/**
 * The analysis of what is heard now into [frame], the waveform in [wave_points] points (at most
 * NAG_SCOPE_WAVE_MAX). -1 while the tap is off or no output is open. Not the render's thread.
 */
int nag_audio_scope(NagAudio *engine, NagScopeFrame *frame, int wave_points);

/** The engine's source, DSP stages and output as JSON (S-910's signal path reads it). */
char *nag_audio_signal_path_json(NagAudio *engine);

/* ------------------------------------------------------- probe and waveform */

/**
 * S-803: what the library keeps of a file, as the engine reads it: codec, container, rate,
 * channels, bits, float, duration, bitrate, the ReplayGain tags, a DSD rate and the composer, as
 * JSON (malloc'd, the caller frees it); NULL when the file does not open.
 */
char *nag_probe_json(const char *url);

/**
 * S-1001: the waveform of [url] in [points] buckets of equal length: out[2i] the peak and
 * out[2i + 1] the RMS of the bucket's samples, all channels, 0..1. Decodes the whole file;
 * [cancel] set non-zero from another thread stops it (-2). Returns the buckets filled, or a
 * negative error with [error] saying why.
 */
int nag_waveform(const char *url, int points, float *out, const int *cancel, char *error, size_t error_size);

const char *nag_audio_version(void);

#ifdef __cplusplus
}
#endif

#endif
