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
#ifdef __ANDROID__
/* AAudio, exclusive and low latency when the device allows, shared otherwise. */
NagSink *nag_sink_aaudio_new(void);
#endif

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

/** The engine's source and output as JSON (S-910's signal path reads it). */
char *nag_audio_signal_path_json(NagAudio *engine);

const char *nag_audio_version(void);

#ifdef __cplusplus
}
#endif

#endif
