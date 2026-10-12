#include "nagaudio.h"
#include "dsp.h"
#include "ring.h"
#include "source.h"

#include <errno.h>
#include <math.h>
#include <stdarg.h>
#include <pthread.h>
#include <semaphore.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define NAGAUDIO_VERSION "0.1.0"

/* frames the render mixes at a time; longer requests are served in rounds */
#define MIX_FRAMES 4096
/* the decoder reads this many frames at a time */
#define DECODE_FRAMES 4096
/* the decoder sleeps this long when the ring is full, unless the render wakes it */
#define DECODER_NAP_MS 20
/* events waiting for the event thread */
#define EVENT_QUEUE 64
#define RETIRED_QUEUE 8

/* ----------------------------------------------------------------- formats */

int nag_format_bytes_per_sample(NagSampleFormat f)
{
    switch (f) {
    case NAG_FMT_S16: return 2;
    case NAG_FMT_S24_3: return 3;
    case NAG_FMT_S32: return 4;
    case NAG_FMT_F32: return 4;
    case NAG_FMT_F64: return 8;
    default: return 0;
    }
}

int nag_format_bits(NagSampleFormat f)
{
    switch (f) {
    case NAG_FMT_S16: return 16;
    case NAG_FMT_S24_3: return 24;
    case NAG_FMT_S32: return 32;
    case NAG_FMT_F32: return 32;
    case NAG_FMT_F64: return 64;
    default: return 0;
    }
}

const char *nag_format_name(NagSampleFormat f)
{
    switch (f) {
    case NAG_FMT_S16: return "s16";
    case NAG_FMT_S24_3: return "s24";
    case NAG_FMT_S32: return "s32";
    case NAG_FMT_F32: return "f32";
    case NAG_FMT_F64: return "f64";
    default: return "none";
    }
}

/* ------------------------------------------------------------------- voices */

typedef struct Voice {
    NagAudio *engine;
    int64_t id;
    char *url;
    NagSource *src;
    NagSourceInfo info;
    NagRing ring;
    pthread_t thread;
    int thread_started;
    atomic_int quit;
    atomic_int eof;          /* the decoder has nothing more; the ring may still hold frames */
    atomic_int failed;
    atomic_int seek_pending; /* seek_ms is read by the decoder once this is set */
    _Atomic int64_t seek_ms;
    int crossfade;           /* this voice, as the next, comes in over the end of the one before */
    _Atomic int64_t played;  /* frames the render took since the start or the last seek */
    _Atomic int64_t base_ms; /* the time of frame 0 of [played] */
    sem_t wake;
    double *scratch;
} Voice;

struct NagAudio {
    NagConfig cfg;
    nag_event_fn on_event;
    void *opaque;
    pthread_mutex_t lock; /* the control plane: open, next, play, pause, stop, the sink */

    NagSink *sink;
    NagFormat out;        /* what the sink is open for */
    atomic_int out_valid; /* the render may convert into [out] */

    atomic_int state;
    _Atomic double volume;

    /* the render plane */
    _Atomic(Voice *) current;
    _Atomic(Voice *) next;
    _Atomic(Voice *) fade_out; /* the voice ending under the current one during a crossfade */
    atomic_int hold;      /* the control plane asks the render to keep its hands off the voices */
    atomic_int rendering; /* the render is inside the voices */
    atomic_int crossfade_ms;
    atomic_int crossfade_curve;
    int64_t fade_total, fade_done; /* frames of the crossfade under way; the render's own */
    double *mix;
    double *mix2;
    uint32_t noise;       /* dither */
    _Atomic int64_t total_frames; /* frames handed to the sink */

    /* S-906: the DSP chain */
    NagDsp dsp;                          /* the render's own */
    const NagDspParams *dsp_active;      /* what the render compiled; freed through dsp_retired */
    _Atomic(NagDspParams *) dsp_pending; /* the control plane's next set: one slot, the newest wins */
    NagDspParams *dsp_params;            /* the control plane's copy of the last set (under the lock) */
    atomic_int dsp_identity;             /* the set leaves the samples alone (voice_format reads it) */
    _Atomic(NagDspParams *) dsp_retired[RETIRED_QUEUE];
    _Atomic unsigned dsp_retired_read, dsp_retired_write;
    atomic_int dsp_reset_wanted;         /* a new track or a seek: the filters forget */
    _Atomic int64_t meter_frames, meter_clipped;
    _Atomic double meter_peak, meter_reduction;

    /* the event plane */
    NagEvent events[EVENT_QUEUE];
    _Atomic unsigned event_read, event_write;
    _Atomic(Voice *) retired[RETIRED_QUEUE];
    _Atomic unsigned retired_read, retired_write;
    atomic_int reopen_wanted; /* a hand-over needs the sink in another format */
    sem_t event_sem;
    pthread_t event_thread;
    int event_thread_started;
    atomic_int event_quit;
};

/* ------------------------------------------------------------------- events */

static void post(NagAudio *e, NagEventType type, int64_t arg, const char *text)
{
    unsigned w = atomic_load_explicit(&e->event_write, memory_order_relaxed);
    unsigned r = atomic_load_explicit(&e->event_read, memory_order_acquire);
    if (w - r >= EVENT_QUEUE)
        return; /* the event thread is far behind: this one is lost rather than blocked on */
    NagEvent *ev = &e->events[w % EVENT_QUEUE];
    ev->type = type;
    ev->arg = arg;
    if (text)
        snprintf(ev->text, sizeof(ev->text), "%s", text);
    else
        ev->text[0] = 0;
    atomic_store_explicit(&e->event_write, w + 1, memory_order_release);
    sem_post(&e->event_sem);
}

static void set_state(NagAudio *e, NagState state)
{
    if ((NagState)atomic_exchange(&e->state, state) != state)
        post(e, NAG_EVENT_STATE, state, NULL);
}

/* ---------------------------------------------------------- render handshake */

/* The render keeps out of the voices until release_render(); waits for a render under way. */
static void hold_render(NagAudio *e)
{
    atomic_fetch_add(&e->hold, 1);
    while (atomic_load(&e->rendering)) {
        struct timespec nap = {0, 200000};
        nanosleep(&nap, NULL);
    }
}

static void release_render(NagAudio *e)
{
    atomic_fetch_sub(&e->hold, 1);
}

/* ------------------------------------------------------------------- voices */

static void voice_free(Voice *v)
{
    if (!v)
        return;
    if (v->thread_started) {
        atomic_store(&v->quit, 1);
        sem_post(&v->wake);
        pthread_join(v->thread, NULL);
    }
    nag_source_close(v->src);
    nag_ring_free(&v->ring);
    sem_destroy(&v->wake);
    free(v->scratch);
    free(v->url);
    free(v);
}

static void voice_wait(Voice *v)
{
    struct timespec until;
    clock_gettime(CLOCK_REALTIME, &until);
    until.tv_nsec += DECODER_NAP_MS * 1000000L;
    if (until.tv_nsec >= 1000000000L) {
        until.tv_sec++;
        until.tv_nsec -= 1000000000L;
    }
    sem_timedwait(&v->wake, &until);
}

static void *decoder_main(void *arg)
{
    Voice *v = arg;
    NagAudio *e = v->engine;
    while (!atomic_load(&v->quit)) {
        if (atomic_load(&v->seek_pending)) {
            int64_t ms = atomic_load(&v->seek_ms);
            hold_render(e);
            nag_ring_reset(&v->ring);
            int ret = nag_source_seek(v->src, ms);
            atomic_store(&v->played, 0);
            atomic_store(&v->base_ms, ms);
            atomic_store(&v->eof, 0);
            if (atomic_load(&e->current) == v)
                atomic_store(&e->dsp_reset_wanted, 1); /* no tail of the audio before the seek */
            release_render(e);
            /* a later seek wins: only this one is done with */
            int one = 1;
            atomic_compare_exchange_strong(&v->seek_pending, &one, 0);
            if (ret < 0) {
                atomic_store(&v->failed, 1);
                atomic_store(&v->eof, 1);
                post(e, NAG_EVENT_ERROR, v->id, "seek failed");
            }
            continue;
        }
        if (atomic_load(&v->eof)) {
            voice_wait(v);
            continue;
        }
        size_t space = nag_ring_space(&v->ring);
        if (space < DECODE_FRAMES / 4) {
            voice_wait(v);
            continue;
        }
        int want = space < DECODE_FRAMES ? (int)space : DECODE_FRAMES;
        int n = nag_source_read(v->src, v->scratch, want);
        if (n > 0) {
            nag_ring_write(&v->ring, v->scratch, (size_t)n);
        } else if (n == 0) {
            atomic_store(&v->eof, 1);
        } else {
            atomic_store(&v->failed, 1);
            atomic_store(&v->eof, 1);
            post(e, NAG_EVENT_ERROR, v->id, "decoding failed");
        }
    }
    return NULL;
}

static Voice *voice_new(NagAudio *e, int64_t id, const char *url, int64_t start_ms, char *error, size_t error_size)
{
    double ring_seconds = e->cfg.ring_seconds;
    /* a crossfade needs the whole tail of the track decoded before it starts */
    int crossfade_ms = atomic_load(&e->crossfade_ms);
    if (crossfade_ms > 0 && ring_seconds < crossfade_ms / 1000.0 + 0.5)
        ring_seconds = crossfade_ms / 1000.0 + 0.5;
    Voice *v = calloc(1, sizeof(*v));
    if (!v) {
        snprintf(error, error_size, "out of memory");
        return NULL;
    }
    v->engine = e;
    v->id = id;
    v->url = strdup(url);
    sem_init(&v->wake, 0, 0);
    if (nag_source_open(&v->src, url, e->cfg.channels, error, error_size) < 0) {
        voice_free(v);
        return NULL;
    }
    v->info = *nag_source_info(v->src);
    size_t capacity = (size_t)(ring_seconds * v->info.rate);
    if (capacity < DECODE_FRAMES * 2)
        capacity = DECODE_FRAMES * 2;
    if (nag_ring_init(&v->ring, capacity, e->cfg.channels) < 0 || !(v->scratch = malloc(sizeof(double) * DECODE_FRAMES * e->cfg.channels))) {
        snprintf(error, error_size, "out of memory");
        voice_free(v);
        return NULL;
    }
    if (start_ms > 0) {
        if (nag_source_seek(v->src, start_ms) < 0)
            start_ms = 0;
    }
    atomic_store(&v->base_ms, start_ms);
    int ret = pthread_create(&v->thread, NULL, decoder_main, v);
    if (ret != 0) {
        snprintf(error, error_size, "decoder thread: %s", strerror(ret));
        voice_free(v);
        return NULL;
    }
    v->thread_started = 1;
    return v;
}

/* The format the sink is asked for [v]: the track's own depth, or the widest with the DSP chain in the way. */
static NagFormat voice_format(const NagAudio *e, const Voice *v)
{
    NagFormat f = {v->info.rate, e->cfg.channels, NAG_FMT_S32};
    if (e->cfg.fixed_format != NAG_FMT_NONE) {
        f.sample_format = e->cfg.fixed_format;
        return f;
    }
    if (v->info.is_float)
        f.sample_format = NAG_FMT_F32;
    else if (v->info.bits > 0 && v->info.bits <= 16 && atomic_load(&e->dsp_identity))
        f.sample_format = NAG_FMT_S16;
    if (nag_format_bits(f.sample_format) > nag_format_bits(e->cfg.max_format) && e->cfg.max_format != NAG_FMT_NONE)
        f.sample_format = e->cfg.max_format;
    return f;
}

/* -------------------------------------------------------------------- sinks */

/* Opens the sink for [want]; on the control or the event thread, never the render's. */
static int open_sink_for(NagAudio *e, const NagFormat *want)
{
    if (!e->sink) {
        e->out = *want;
        atomic_store(&e->out_valid, 1);
        return 0;
    }
    NagFormat got = {0};
    atomic_store(&e->out_valid, 0);
    int ret = e->sink->open(e->sink, e, want, &got);
    if (ret < 0) {
        char text[96];
        snprintf(text, sizeof(text), "output %d Hz %s failed", want->rate, nag_format_name(want->sample_format));
        post(e, NAG_EVENT_ERROR, -1, text);
        return ret;
    }
    e->out = got;
    atomic_store(&e->out_valid, 1);
    char text[64];
    snprintf(text, sizeof(text), "%d/%d/%s", got.rate, got.channels, nag_format_name(got.sample_format));
    post(e, NAG_EVENT_FORMAT, 0, text);
    if (atomic_load(&e->state) == NAG_STATE_PLAYING && e->sink->start)
        e->sink->start(e->sink);
    return 0;
}

/* Whether the sink, as open, takes the frames of [v]: same rate and channels, depth enough. */
static int sink_fits(const NagAudio *e, const Voice *v)
{
    if (!atomic_load(&e->out_valid))
        return 0;
    NagFormat want = voice_format(e, v);
    if (want.rate != e->out.rate || want.channels != e->out.channels)
        return 0;
    if (want.sample_format == e->out.sample_format)
        return 1;
    /* the sink took a wider format than asked (I32 for S24, float for integers): fine */
    return nag_format_bits(e->out.sample_format) >= nag_format_bits(want.sample_format) ||
           e->out.sample_format == NAG_FMT_F32 || e->out.sample_format == NAG_FMT_F64;
}

/* ------------------------------------------------------------- event thread */

static void retire(NagAudio *e, Voice *v)
{
    unsigned w = atomic_load_explicit(&e->retired_write, memory_order_relaxed);
    unsigned r = atomic_load_explicit(&e->retired_read, memory_order_acquire);
    if (w - r >= RETIRED_QUEUE)
        return; /* cannot happen: at most two voices exist; a leak rather than a crash */
    atomic_store(&e->retired[w % RETIRED_QUEUE], v);
    atomic_store_explicit(&e->retired_write, w + 1, memory_order_release);
    sem_post(&e->event_sem);
}

static void free_retired(NagAudio *e)
{
    for (;;) {
        unsigned r = atomic_load_explicit(&e->retired_read, memory_order_relaxed);
        unsigned w = atomic_load_explicit(&e->retired_write, memory_order_acquire);
        if (r == w)
            break;
        Voice *v = atomic_load(&e->retired[r % RETIRED_QUEUE]);
        atomic_store_explicit(&e->retired_read, r + 1, memory_order_release);
        voice_free(v);
    }
    for (;;) {
        unsigned r = atomic_load_explicit(&e->dsp_retired_read, memory_order_relaxed);
        unsigned w = atomic_load_explicit(&e->dsp_retired_write, memory_order_acquire);
        if (r == w)
            break;
        NagDspParams *p = atomic_load(&e->dsp_retired[r % RETIRED_QUEUE]);
        atomic_store_explicit(&e->dsp_retired_read, r + 1, memory_order_release);
        free(p);
    }
}

void nag_audio_sink_lost(NagAudio *e, NagSink *sink)
{
    if (e->sink != sink)
        return;
    atomic_store(&e->out_valid, 0);
    post(e, NAG_EVENT_DEVICE_LOST, 0, sink->name);
    if (!atomic_exchange(&e->reopen_wanted, 1))
        sem_post(&e->event_sem);
}

static void *event_main(void *arg)
{
    NagAudio *e = arg;
    while (!atomic_load(&e->event_quit)) {
        sem_wait(&e->event_sem);
        free_retired(e);
        if (atomic_exchange(&e->reopen_wanted, 0)) {
            pthread_mutex_lock(&e->lock);
            Voice *v = atomic_load(&e->current);
            if (v && !sink_fits(e, v)) {
                NagFormat want = voice_format(e, v);
                open_sink_for(e, &want);
            }
            pthread_mutex_unlock(&e->lock);
        }
        for (;;) {
            unsigned r = atomic_load_explicit(&e->event_read, memory_order_relaxed);
            unsigned w = atomic_load_explicit(&e->event_write, memory_order_acquire);
            if (r == w)
                break;
            NagEvent ev = e->events[r % EVENT_QUEUE];
            atomic_store_explicit(&e->event_read, r + 1, memory_order_release);
            if (e->on_event)
                e->on_event(e->opaque, &ev);
        }
    }
    free_retired(e);
    if (e->on_event)
        e->on_event(e->opaque, NULL);
    return NULL;
}

/* ------------------------------------------------------------------- engine */

void nag_config_defaults(NagConfig *c)
{
    memset(c, 0, sizeof(*c));
    c->ring_seconds = 0.5;
    c->channels = 2;
    c->max_format = NAG_FMT_S32;
    c->dither = 1;
}

NagAudio *nag_audio_new(const NagConfig *config, nag_event_fn on_event, void *opaque)
{
    NagAudio *e = calloc(1, sizeof(*e));
    if (!e)
        return NULL;
    if (config)
        e->cfg = *config;
    else
        nag_config_defaults(&e->cfg);
    if (e->cfg.channels <= 0)
        e->cfg.channels = 2;
    if (e->cfg.ring_seconds <= 0)
        e->cfg.ring_seconds = 0.5;
    e->on_event = on_event;
    e->opaque = opaque;
    e->noise = 0x9E3779B9u;
    atomic_store(&e->volume, 1.0);
    pthread_mutex_init(&e->lock, NULL);
    sem_init(&e->event_sem, 0, 0);
    e->mix = malloc(sizeof(double) * MIX_FRAMES * e->cfg.channels);
    e->mix2 = malloc(sizeof(double) * MIX_FRAMES * e->cfg.channels);
    e->dsp_params = malloc(sizeof(*e->dsp_params));
    int dsp_ok = e->cfg.channels <= NAG_DSP_CHANNELS_MAX && nag_dsp_init(&e->dsp, e->cfg.channels) == 0;
    if (!e->mix || !e->mix2 || !e->dsp_params || !dsp_ok || pthread_create(&e->event_thread, NULL, event_main, e) != 0) {
        if (dsp_ok)
            nag_dsp_free(&e->dsp);
        free(e->dsp_params);
        free(e->mix);
        free(e->mix2);
        sem_destroy(&e->event_sem);
        pthread_mutex_destroy(&e->lock);
        free(e);
        return NULL;
    }
    nag_dsp_params_defaults(e->dsp_params);
    atomic_store(&e->dsp_identity, 1);
    e->event_thread_started = 1;
    return e;
}

/* Takes the voices from the render; the caller frees them. Under the lock. */
static void take_voices(NagAudio *e, Voice **current, Voice **next, Voice **fade_out)
{
    hold_render(e);
    *current = atomic_exchange(&e->current, NULL);
    *next = atomic_exchange(&e->next, NULL);
    *fade_out = atomic_exchange(&e->fade_out, NULL);
    e->fade_total = 0;
    release_render(e);
}

void nag_audio_free(NagAudio *e)
{
    if (!e)
        return;
    pthread_mutex_lock(&e->lock);
    Voice *current, *next, *fade_out;
    take_voices(e, &current, &next, &fade_out);
    atomic_store(&e->state, NAG_STATE_STOPPED);
    if (e->sink) {
        if (e->sink->pause)
            e->sink->pause(e->sink);
        e->sink->close(e->sink);
    }
    pthread_mutex_unlock(&e->lock);
    voice_free(current);
    voice_free(next);
    voice_free(fade_out);
    if (e->event_thread_started) {
        atomic_store(&e->event_quit, 1);
        sem_post(&e->event_sem);
        pthread_join(e->event_thread, NULL);
    }
    free_retired(e);
    if (e->sink && e->sink->destroy)
        e->sink->destroy(e->sink);
    sem_destroy(&e->event_sem);
    pthread_mutex_destroy(&e->lock);
    free(atomic_exchange(&e->dsp_pending, NULL));
    free((NagDspParams *)e->dsp_active);
    free(e->dsp_params);
    nag_dsp_free(&e->dsp);
    free(e->mix);
    free(e->mix2);
    free(e);
}

void *nag_audio_opaque(NagAudio *e)
{
    return e->opaque;
}

int nag_audio_set_sink(NagAudio *e, NagSink *sink)
{
    pthread_mutex_lock(&e->lock);
    if (e->sink) {
        if (e->sink->pause)
            e->sink->pause(e->sink);
        e->sink->close(e->sink);
        if (e->sink->destroy)
            e->sink->destroy(e->sink);
    }
    e->sink = sink;
    atomic_store(&e->out_valid, 0);
    int ret = 0;
    Voice *v = atomic_load(&e->current);
    if (v) {
        NagFormat want = voice_format(e, v);
        ret = open_sink_for(e, &want);
    }
    pthread_mutex_unlock(&e->lock);
    return ret;
}

NagSink *nag_audio_sink(NagAudio *e)
{
    return e->sink;
}

int nag_audio_open(NagAudio *e, int64_t id, const char *url, int64_t start_ms)
{
    char error[256] = {0};
    Voice *v = voice_new(e, id, url, start_ms, error, sizeof(error));
    if (!v) {
        post(e, NAG_EVENT_ERROR, id, error);
        return -1;
    }
    pthread_mutex_lock(&e->lock);
    Voice *old_current, *old_next, *old_fade;
    take_voices(e, &old_current, &old_next, &old_fade);
    int ret = 0;
    if (!sink_fits(e, v)) {
        NagFormat want = voice_format(e, v);
        ret = open_sink_for(e, &want);
    }
    atomic_store(&e->current, v);
    atomic_store(&e->dsp_reset_wanted, 1);
    post(e, NAG_EVENT_TRACK, id, NULL);
    if (atomic_load(&e->state) == NAG_STATE_ENDED)
        set_state(e, NAG_STATE_PLAYING);
    if (atomic_load(&e->state) == NAG_STATE_PLAYING && e->sink && e->sink->start)
        e->sink->start(e->sink);
    pthread_mutex_unlock(&e->lock);
    voice_free(old_current);
    voice_free(old_next);
    voice_free(old_fade);
    return ret;
}

int nag_audio_set_next(NagAudio *e, int64_t id, const char *url, int crossfade)
{
    Voice *v = NULL;
    if (url) {
        char error[256] = {0};
        v = voice_new(e, id, url, 0, error, sizeof(error));
        if (!v) {
            post(e, NAG_EVENT_ERROR, id, error);
            return -1;
        }
        v->crossfade = crossfade ? 1 : 0;
    }
    pthread_mutex_lock(&e->lock);
    hold_render(e);
    Voice *old = atomic_exchange(&e->next, v);
    release_render(e);
    pthread_mutex_unlock(&e->lock);
    voice_free(old);
    return 0;
}

int nag_audio_play(NagAudio *e)
{
    pthread_mutex_lock(&e->lock);
    if (atomic_load(&e->state) == NAG_STATE_ENDED && !atomic_load(&e->current)) {
        pthread_mutex_unlock(&e->lock);
        return -1;
    }
    set_state(e, NAG_STATE_PLAYING);
    int ret = 0;
    if (e->sink && e->sink->start && atomic_load(&e->out_valid))
        ret = e->sink->start(e->sink);
    pthread_mutex_unlock(&e->lock);
    return ret;
}

int nag_audio_pause(NagAudio *e)
{
    pthread_mutex_lock(&e->lock);
    if (atomic_load(&e->state) == NAG_STATE_PLAYING) {
        set_state(e, NAG_STATE_PAUSED);
        if (e->sink && e->sink->pause)
            e->sink->pause(e->sink);
    }
    pthread_mutex_unlock(&e->lock);
    return 0;
}

int nag_audio_stop(NagAudio *e)
{
    pthread_mutex_lock(&e->lock);
    Voice *current, *next, *fade_out;
    take_voices(e, &current, &next, &fade_out);
    set_state(e, NAG_STATE_STOPPED);
    atomic_store(&e->dsp_reset_wanted, 1);
    if (e->sink && e->sink->pause)
        e->sink->pause(e->sink);
    pthread_mutex_unlock(&e->lock);
    voice_free(current);
    voice_free(next);
    voice_free(fade_out);
    return 0;
}

void nag_audio_set_crossfade(NagAudio *e, int ms, NagCrossfadeCurve curve)
{
    if (ms < 0)
        ms = 0;
    if (ms > NAG_CROSSFADE_MAX_MS)
        ms = NAG_CROSSFADE_MAX_MS;
    atomic_store(&e->crossfade_ms, ms);
    atomic_store(&e->crossfade_curve, (int)curve);
}

int nag_audio_crossfade_ms(NagAudio *e)
{
    return atomic_load(&e->crossfade_ms);
}

int nag_audio_seek(NagAudio *e, int64_t ms)
{
    Voice *v = atomic_load(&e->current);
    if (!v)
        return -1;
    if (ms < 0)
        ms = 0;
    atomic_store(&v->seek_ms, ms);
    atomic_store(&v->seek_pending, 1);
    sem_post(&v->wake);
    return 0;
}

NagState nag_audio_state(NagAudio *e)
{
    return (NagState)atomic_load(&e->state);
}

int64_t nag_audio_time_ms(NagAudio *e)
{
    Voice *v = atomic_load(&e->current);
    if (!v)
        return 0;
    if (atomic_load(&v->seek_pending))
        return atomic_load(&v->seek_ms);
    int64_t played = atomic_load(&v->played);
    int64_t latency = e->sink && e->sink->latency_frames ? e->sink->latency_frames(e->sink) : 0;
    if (latency > 0 && latency < played)
        played -= latency;
    return atomic_load(&v->base_ms) + played * 1000 / v->info.rate;
}

int64_t nag_audio_length_ms(NagAudio *e)
{
    Voice *v = atomic_load(&e->current);
    return v ? v->info.duration_ms : 0;
}

int64_t nag_audio_track_id(NagAudio *e)
{
    Voice *v = atomic_load(&e->current);
    return v ? v->id : -1;
}

int nag_audio_source_info(NagAudio *e, NagSourceInfo *info)
{
    Voice *v = atomic_load(&e->current);
    if (!v)
        return 0;
    *info = v->info;
    return 1;
}

NagFormat nag_audio_output_format(NagAudio *e)
{
    NagFormat none = {0, 0, NAG_FMT_NONE};
    return atomic_load(&e->out_valid) ? e->out : none;
}

void nag_audio_set_volume(NagAudio *e, double volume)
{
    if (volume < 0)
        volume = 0;
    atomic_store(&e->volume, volume);
}

double nag_audio_volume(NagAudio *e)
{
    return atomic_load(&e->volume);
}

/* ---------------------------------------------------------------- DSP chain */

int nag_audio_set_dsp(NagAudio *e, const NagDspParams *params)
{
    NagDspParams *copy = malloc(sizeof(*copy));
    if (!copy)
        return -1;
    *copy = *params;
    pthread_mutex_lock(&e->lock);
    *e->dsp_params = *params;
    atomic_store(&e->dsp_identity, nag_dsp_params_identity(params));
    /* the render takes the slot's set at its next block; one it never took is dropped here */
    NagDspParams *dropped = atomic_exchange(&e->dsp_pending, copy);
    pthread_mutex_unlock(&e->lock);
    free(dropped);
    return 0;
}

void nag_audio_get_dsp(NagAudio *e, NagDspParams *params)
{
    pthread_mutex_lock(&e->lock);
    *params = *e->dsp_params;
    pthread_mutex_unlock(&e->lock);
}

int nag_audio_dsp_active(NagAudio *e)
{
    return !atomic_load(&e->dsp_identity);
}

int64_t nag_audio_dsp_latency_frames(NagAudio *e)
{
    pthread_mutex_lock(&e->lock);
    int64_t frames = 0;
    if (!e->dsp_params->bypass && e->dsp_params->limiter_enabled && atomic_load(&e->out_valid)) {
        double ms = e->dsp_params->limiter_lookahead_ms;
        if (ms > NAG_DSP_LOOKAHEAD_MAX_MS)
            ms = NAG_DSP_LOOKAHEAD_MAX_MS;
        frames = ms > 0 ? (int64_t)lrint(ms * e->out.rate / 1000.0) - 1 : 0;
        if (frames < 0)
            frames = 0;
    }
    pthread_mutex_unlock(&e->lock);
    return frames;
}

void nag_audio_dsp_meter(NagAudio *e, NagDspMeter *meter, int reset)
{
    meter->frames = atomic_load(&e->meter_frames);
    meter->clipped = atomic_load(&e->meter_clipped);
    meter->peak = atomic_load(&e->meter_peak);
    meter->reduction_db = atomic_load(&e->meter_reduction);
    if (reset) {
        atomic_store(&e->meter_frames, 0);
        atomic_store(&e->meter_clipped, 0);
        atomic_store(&e->meter_peak, 0.0);
        atomic_store(&e->meter_reduction, 0.0);
    }
}

/* The render's side: the newest parameters, a reset asked for, the rate; whether the chain is in the way. */
static int dsp_prepare(NagAudio *e)
{
    if (atomic_load(&e->dsp_pending)) {
        unsigned w = atomic_load_explicit(&e->dsp_retired_write, memory_order_relaxed);
        unsigned r = atomic_load_explicit(&e->dsp_retired_read, memory_order_acquire);
        if (w - r < RETIRED_QUEUE) { /* room to retire the set in use; otherwise next block */
            NagDspParams *p = atomic_exchange(&e->dsp_pending, NULL);
            if (p) {
                if (e->dsp_active) {
                    atomic_store(&e->dsp_retired[w % RETIRED_QUEUE], (NagDspParams *)e->dsp_active);
                    atomic_store_explicit(&e->dsp_retired_write, w + 1, memory_order_release);
                    sem_post(&e->event_sem);
                }
                e->dsp_active = p;
                nag_dsp_compile(&e->dsp, p, e->out.rate);
            }
        }
    }
    if (e->dsp_active && e->dsp.rate != e->out.rate)
        nag_dsp_compile(&e->dsp, e->dsp_active, e->out.rate);
    if (atomic_exchange(&e->dsp_reset_wanted, 0))
        nag_dsp_reset(&e->dsp);
    return e->dsp.active;
}

static inline void atomic_max_double(_Atomic double *target, double value)
{
    double seen = atomic_load(target);
    while (value > seen && !atomic_compare_exchange_weak(target, &seen, value))
        ;
}

static void meter_add(NagAudio *e, const NagDspBlockMeter *m, int frames)
{
    atomic_fetch_add(&e->meter_frames, frames);
    if (m->clipped)
        atomic_fetch_add(&e->meter_clipped, m->clipped);
    atomic_max_double(&e->meter_peak, m->peak);
    if (m->min_gain < 1.0)
        atomic_max_double(&e->meter_reduction, -20.0 * log10(m->min_gain > 1e-12 ? m->min_gain : 1e-12));
}

/* ------------------------------------------------------------------- render */

static inline uint32_t noise_next(uint32_t *s)
{
    uint32_t x = *s;
    x ^= x << 13;
    x ^= x >> 17;
    x ^= x << 5;
    return *s = x;
}

/* TPDF dither of one LSB peak to peak, in units of the output's LSB */
static inline double dither_lsb(uint32_t *s)
{
    double a = noise_next(s) * (1.0 / 4294967296.0) - 0.5;
    double b = noise_next(s) * (1.0 / 4294967296.0) - 0.5;
    return a + b;
}

static inline int32_t clip32(double v)
{
    if (v >= 2147483647.0)
        return INT32_MAX;
    if (v <= -2147483648.0)
        return INT32_MIN;
    return (int32_t)lrint(v);
}

/* [frames] interleaved doubles in [in] into [out] in the output format, with volume and dither. */
static void convert(NagAudio *e, const double *in, void *out, int frames, double volume, int dither)
{
    int samples = frames * e->out.channels;
    switch (e->out.sample_format) {
    case NAG_FMT_S16: {
        int16_t *o = out;
        const double scale = 32768.0;
        for (int i = 0; i < samples; i++) {
            double v = in[i] * volume * scale;
            if (dither)
                v += dither_lsb(&e->noise);
            if (v >= 32767.0)
                o[i] = INT16_MAX;
            else if (v <= -32768.0)
                o[i] = INT16_MIN;
            else
                o[i] = (int16_t)lrint(v);
        }
        break;
    }
    case NAG_FMT_S24_3: {
        uint8_t *o = out;
        const double scale = 8388608.0;
        for (int i = 0; i < samples; i++) {
            double v = in[i] * volume * scale;
            if (dither)
                v += dither_lsb(&e->noise);
            int32_t x;
            if (v >= 8388607.0)
                x = 8388607;
            else if (v <= -8388608.0)
                x = -8388608;
            else
                x = (int32_t)lrint(v);
            o[3 * i] = (uint8_t)(x & 0xFF);
            o[3 * i + 1] = (uint8_t)((x >> 8) & 0xFF);
            o[3 * i + 2] = (uint8_t)((x >> 16) & 0xFF);
        }
        break;
    }
    case NAG_FMT_S32: {
        int32_t *o = out;
        const double scale = 2147483648.0;
        for (int i = 0; i < samples; i++) {
            double v = in[i] * volume * scale;
            if (dither)
                v += dither_lsb(&e->noise) * 256.0; /* a 24-bit DAC's LSB */
            o[i] = clip32(v);
        }
        break;
    }
    case NAG_FMT_F32: {
        float *o = out;
        for (int i = 0; i < samples; i++)
            o[i] = (float)(in[i] * volume);
        break;
    }
    case NAG_FMT_F64: {
        double *o = out;
        for (int i = 0; i < samples; i++)
            o[i] = in[i] * volume;
        break;
    }
    default:
        break;
    }
}

static inline void silence(NagAudio *e, void *out, int frames)
{
    memset(out, 0, (size_t)frames * e->out.channels * nag_format_bytes_per_sample(e->out.sample_format));
}

/* The mix buffer's [n_frames] through the DSP chain when it is in the way, then into [out]. */
static void finish_block(NagAudio *e, void *out, int n_frames, double volume, int dither, int dsp_on)
{
    if (dsp_on) {
        NagDspBlockMeter m;
        nag_dsp_process(&e->dsp, e->mix, n_frames, &m);
        meter_add(e, &m, n_frames);
    }
    convert(e, e->mix, out, n_frames, volume, dither);
}

/* Whether converting [v] into the output narrows it: 24 bits into 16, floats into integers. */
static int needs_dither(const NagAudio *e, const Voice *v)
{
    if (!e->cfg.dither)
        return 0;
    int out_bits = e->out.sample_format == NAG_FMT_S32 ? 24 : nag_format_bits(e->out.sample_format);
    if (e->out.sample_format == NAG_FMT_F32 || e->out.sample_format == NAG_FMT_F64)
        return 0;
    if (v->info.is_float || v->info.bits == 0)
        return 1;
    return v->info.bits > out_bits;
}

/* The crossfade's gains at [x] of the way (0..1): the track going and the one coming. */
static inline void fade_gains(int curve, double x, double *out_gain, double *in_gain)
{
    if (curve == NAG_CROSSFADE_LINEAR) {
        *out_gain = 1.0 - x;
        *in_gain = x;
    } else {
        *out_gain = cos(x * M_PI_2);
        *in_gain = sin(x * M_PI_2);
    }
}

/*
 * Ends [v], the voice playing: the next goes on in the same buffer, or the queue is empty.
 * Returns the voice now current, NULL when none.
 */
static Voice *hand_over(NagAudio *e, Voice *v)
{
    Voice *n = atomic_exchange(&e->next, NULL);
    atomic_store(&e->current, n);
    retire(e, v);
    post(e, NAG_EVENT_TRACK_ENDED, v->id, NULL);
    if (n) {
        post(e, NAG_EVENT_TRACK, n->id, NULL);
    } else {
        post(e, NAG_EVENT_QUEUE_EMPTY, 0, NULL);
        set_state(e, NAG_STATE_ENDED);
    }
    return n;
}

int nag_audio_render(NagAudio *e, void *out, int frames)
{
    if (frames <= 0)
        return 0;
    atomic_store(&e->rendering, 1);
    if (atomic_load(&e->hold) || atomic_load(&e->state) != NAG_STATE_PLAYING || !atomic_load(&e->out_valid)) {
        atomic_store(&e->rendering, 0);
        silence(e, out, frames);
        return 0;
    }
    const int channels = e->out.channels;
    const int bytes = nag_format_bytes_per_sample(e->out.sample_format) * channels;
    const double volume = atomic_load(&e->volume);
    const int curve = atomic_load(&e->crossfade_curve);
    const int dsp_on = dsp_prepare(e);
    /* a chain in the way makes every output a conversion from doubles: dithered to integers */
    const int dsp_dither = dsp_on && e->cfg.dither && e->out.sample_format != NAG_FMT_F32 && e->out.sample_format != NAG_FMT_F64;
    int produced = 0;
    int pos = 0;
    while (pos < frames) {
        Voice *v = atomic_load(&e->current);
        if (!v)
            break;
        if (!sink_fits(e, v)) {
            /* the sink must be reopened for this voice: silence until the event thread did */
            if (!atomic_exchange(&e->reopen_wanted, 1))
                sem_post(&e->event_sem);
            break;
        }
        Voice *f = atomic_load(&e->fade_out);
        size_t available = nag_ring_available(&v->ring);
        if (!f) {
            int n_frames = frames - pos;
            if (n_frames > MIX_FRAMES)
                n_frames = MIX_FRAMES;
            /* S-905: the end of this voice in sight, the next one comes in over it */
            Voice *n = atomic_load(&e->next);
            int fade_ms = atomic_load(&e->crossfade_ms);
            if (n && n->crossfade && fade_ms > 0 && n->info.rate == v->info.rate && !atomic_load(&v->seek_pending)) {
                int64_t fade_frames = (int64_t)fade_ms * v->info.rate / 1000;
                int64_t total = v->info.duration_ms > 0 ? v->info.duration_ms * v->info.rate / 1000 : 0;
                int64_t position = atomic_load(&v->base_ms) * v->info.rate / 1000 + atomic_load(&v->played);
                int64_t start_at = total > 0 ? total - fade_frames : -1;
                if (total > 0 && position >= start_at) {
                    /* the track's length says where the fade starts: over its last fade_frames */
                    atomic_store(&e->next, NULL);
                    atomic_store(&e->fade_out, v);
                    atomic_store(&e->current, n);
                    e->fade_total = total - position > 0 ? total - position : 1;
                    e->fade_done = 0;
                    post(e, NAG_EVENT_TRACK, n->id, NULL);
                    continue;
                }
                if (total > 0 && start_at - position < n_frames) {
                    n_frames = (int)(start_at - position); /* up to the fade's first sample, exactly */
                } else if (total <= 0 && atomic_load(&v->eof) && available > 0 && (int64_t)available <= fade_frames) {
                    /* a length unknown: what is left once decoded, if it fits the fade */
                    atomic_store(&e->next, NULL);
                    atomic_store(&e->fade_out, v);
                    atomic_store(&e->current, n);
                    e->fade_total = (int64_t)available;
                    e->fade_done = 0;
                    post(e, NAG_EVENT_TRACK, n->id, NULL);
                    continue;
                }
            }
            if (available == 0) {
                if (atomic_load(&v->eof) && !atomic_load(&v->seek_pending)) {
                    /* this voice is done: the next one goes on in the same buffer */
                    if (!hand_over(e, v))
                        break;
                    continue;
                }
                break; /* the decoder is behind: silence */
            }
            if (n_frames <= 0)
                n_frames = 1;
            if ((size_t)n_frames > available)
                n_frames = (int)available;
            nag_ring_read(&v->ring, e->mix, (size_t)n_frames);
            atomic_fetch_add(&v->played, n_frames);
            if (nag_ring_available(&v->ring) < v->ring.capacity / 2)
                sem_post(&v->wake);
            finish_block(e, (uint8_t *)out + (size_t)pos * bytes, n_frames, volume, needs_dither(e, v) || dsp_dither, dsp_on);
            pos += n_frames;
            produced += n_frames;
            continue;
        }
        /* a crossfade: [f] fades out under [v], which fades in */
        size_t left = nag_ring_available(&f->ring);
        if (left == 0) {
            if (!atomic_load(&f->eof))
                break; /* the outgoing voice's decoder is behind: silence rather than a jump */
            atomic_store(&e->fade_out, NULL);
            retire(e, f);
            post(e, NAG_EVENT_TRACK_ENDED, f->id, NULL);
            e->fade_total = 0;
            continue;
        }
        if (available == 0)
            break; /* the incoming voice's decoder is behind: silence rather than a jump */
        int n_frames = frames - pos;
        if (n_frames > MIX_FRAMES)
            n_frames = MIX_FRAMES;
        if ((size_t)n_frames > available)
            n_frames = (int)available;
        if ((size_t)n_frames > left)
            n_frames = (int)left;
        nag_ring_read(&f->ring, e->mix, (size_t)n_frames);
        nag_ring_read(&v->ring, e->mix2, (size_t)n_frames);
        atomic_fetch_add(&f->played, n_frames);
        atomic_fetch_add(&v->played, n_frames);
        if (nag_ring_available(&v->ring) < v->ring.capacity / 2)
            sem_post(&v->wake);
        const double total = (double)(e->fade_total > 0 ? e->fade_total : 1);
        for (int i = 0; i < n_frames; i++) {
            double out_gain, in_gain;
            double x = (e->fade_done + i + 1) / total;
            fade_gains(curve, x > 1.0 ? 1.0 : x, &out_gain, &in_gain);
            double *a = e->mix + (size_t)i * channels;
            const double *b = e->mix2 + (size_t)i * channels;
            for (int c = 0; c < channels; c++)
                a[c] = a[c] * out_gain + b[c] * in_gain;
        }
        e->fade_done += n_frames;
        finish_block(e, (uint8_t *)out + (size_t)pos * bytes, n_frames, volume,
                     e->cfg.dither && e->out.sample_format != NAG_FMT_F32 && e->out.sample_format != NAG_FMT_F64, dsp_on);
        pos += n_frames;
        produced += n_frames;
    }
    if (pos < frames)
        memset((uint8_t *)out + (size_t)pos * bytes, 0, (size_t)(frames - pos) * bytes);
    atomic_fetch_add(&e->total_frames, frames);
    atomic_store(&e->rendering, 0);
    return produced;
}

/* -------------------------------------------------------------- signal path */

typedef struct Str {
    char *s;
    size_t len, cap;
} Str;

static void str_add(Str *j, const char *fmt, ...)
{
    va_list ap;
    for (;;) {
        size_t room = j->cap - j->len;
        va_start(ap, fmt);
        int n = j->s ? vsnprintf(j->s + j->len, room, fmt, ap) : -1;
        va_end(ap);
        if (j->s && n >= 0 && (size_t)n < room) {
            j->len += (size_t)n;
            return;
        }
        size_t cap = j->cap ? j->cap * 2 : 1024;
        if (n >= 0 && cap < j->len + (size_t)n + 1)
            cap = j->len + (size_t)n + 1;
        char *p = realloc(j->s, cap);
        if (!p)
            return;
        j->s = p;
        j->cap = cap;
    }
}

static void str_add_quoted(Str *j, const char *text)
{
    str_add(j, "\"");
    for (const char *p = text; *p; p++) {
        if (*p == '"' || *p == '\\')
            str_add(j, "\\%c", *p);
        else if ((unsigned char)*p < 0x20)
            str_add(j, "\\u%04x", (unsigned char)*p);
        else
            str_add(j, "%c", *p);
    }
    str_add(j, "\"");
}

char *nag_audio_signal_path_json(NagAudio *e)
{
    Str j = {0};
    pthread_mutex_lock(&e->lock);
    Voice *v = atomic_load(&e->current);
    NagFormat out = nag_audio_output_format(e);
    double volume = atomic_load(&e->volume);
    int sink_exclusive = e->sink && e->sink->bit_perfect && e->sink->bit_perfect(e->sink);
    int bit_perfect = 0;
    const char *why;
    if (!v || out.sample_format == NAG_FMT_NONE)
        why = "idle";
    else if (v->info.is_float || v->info.bits == 0)
        why = "lossy";
    else if (volume < 1.0)
        why = "volume";
    else if (!atomic_load(&e->dsp_identity))
        why = "dsp";
    else if (out.sample_format != NAG_FMT_F32 && out.sample_format != NAG_FMT_F64 && nag_format_bits(out.sample_format) < v->info.bits)
        why = "depth";
    else if (!sink_exclusive)
        why = "output";
    else if (atomic_load(&e->crossfade_ms) > 0)
        why = "crossfade";
    else {
        why = "";
        bit_perfect = 1;
    }
    str_add(&j, "{\"version\":\"%s\",\"state\":%d,\"source\":", NAGAUDIO_VERSION, atomic_load(&e->state));
    if (v) {
        str_add(&j, "{\"id\":%lld,\"url\":", (long long)v->id);
        str_add_quoted(&j, v->url);
        str_add(&j, ",\"codec\":");
        str_add_quoted(&j, v->info.codec);
        str_add(&j, ",\"container\":");
        str_add_quoted(&j, v->info.container);
        str_add(&j, ",\"rate\":%d,\"channels\":%d,\"bits\":%d,\"float\":%s,\"duration_ms\":%lld,\"bitrate\":%lld}", v->info.rate,
                v->info.channels, v->info.bits, v->info.is_float ? "true" : "false", (long long)v->info.duration_ms, (long long)v->info.bitrate);
    } else {
        str_add(&j, "null");
    }
    str_add(&j, ",\"output\":");
    if (out.sample_format != NAG_FMT_NONE) {
        str_add(&j, "{\"sink\":\"%s\",\"rate\":%d,\"channels\":%d,\"format\":\"%s\",\"bits\":%d,\"exclusive\":%s}",
                e->sink ? e->sink->name : "none", out.rate, out.channels, nag_format_name(out.sample_format), nag_format_bits(out.sample_format),
                sink_exclusive ? "true" : "false");
    } else {
        str_add(&j, "null");
    }
    str_add(&j, ",\"volume\":%.4f,\"crossfade_ms\":%d,\"dsp\":[", volume, atomic_load(&e->crossfade_ms));
    {
        /* S-906: the stages as the parameters compile at the output's rate (a dry compile) */
        NagDsp probe;
        nag_dsp_probe(&probe, e->cfg.channels);
        nag_dsp_compile(&probe, e->dsp_params, out.rate > 0 ? out.rate : 48000);
        for (int i = 0; i < probe.n_stages; i++) {
            const NagDspStage *s = &probe.stages[i];
            str_add(&j, "%s{\"stage\":\"%s\"", i ? "," : "", s->name);
            if (s->count)
                str_add(&j, ",\"bands\":%d", s->count);
            if (s->db != 0 || strcmp(s->name, "preamp") == 0 || strcmp(s->name, "limiter") == 0)
                str_add(&j, ",\"db\":%.2f", s->db);
            if (strcmp(s->name, "profile") == 0) {
                str_add(&j, ",\"name\":");
                str_add_quoted(&j, e->dsp_params->profile_name);
            }
            if (strcmp(s->name, "limiter") == 0)
                str_add(&j, ",\"lookahead_ms\":%.1f,\"release_ms\":%.0f", probe.limiter.frames * 1000.0 / probe.rate, e->dsp_params->limiter_release_ms);
            str_add(&j, "}");
        }
    }
    str_add(&j, "],\"dsp_active\":%s,\"bit_perfect\":%s,\"not_bit_perfect_because\":\"%s\"}", atomic_load(&e->dsp_identity) ? "false" : "true",
            bit_perfect ? "true" : "false", why);
    pthread_mutex_unlock(&e->lock);
    return j.s;
}

const char *nag_audio_version(void)
{
    return NAGAUDIO_VERSION;
}

/* ---------------------------------------------------------------- pull sink */

typedef struct PullSink {
    NagFormat format;
    int open;
} PullSink;

static int pull_open(NagSink *sink, NagAudio *engine, const NagFormat *want, NagFormat *got)
{
    (void)engine;
    PullSink *p = sink->priv;
    p->format = *want;
    p->open = 1;
    *got = *want;
    return 0;
}

static int pull_nop(NagSink *sink)
{
    (void)sink;
    return 0;
}

static void pull_close(NagSink *sink)
{
    ((PullSink *)sink->priv)->open = 0;
}

static int64_t pull_latency(NagSink *sink)
{
    (void)sink;
    return 0;
}

static int pull_bit_perfect(NagSink *sink)
{
    (void)sink;
    return 0;
}

static void pull_destroy(NagSink *sink)
{
    free(sink->priv);
    free(sink);
}

NagSink *nag_sink_pull_new(void)
{
    NagSink *sink = calloc(1, sizeof(*sink));
    PullSink *p = calloc(1, sizeof(*p));
    if (!sink || !p) {
        free(sink);
        free(p);
        return NULL;
    }
    sink->name = "pull";
    sink->open = pull_open;
    sink->start = pull_nop;
    sink->pause = pull_nop;
    sink->close = pull_close;
    sink->latency_frames = pull_latency;
    sink->bit_perfect = pull_bit_perfect;
    sink->destroy = pull_destroy;
    sink->priv = p;
    return sink;
}

/* ---------------------------------------------------------------- file sink */

typedef struct FileSink {
    char *path;
    FILE *file;
    NagFormat format;
    NagAudio *engine;
    pthread_t thread;
    int thread_started;
    atomic_int run;
} FileSink;

static void *file_main(void *arg)
{
    NagSink *sink = arg;
    FileSink *f = sink->priv;
    const int frames = 1024;
    size_t bytes = (size_t)frames * f->format.channels * nag_format_bytes_per_sample(f->format.sample_format);
    void *buf = malloc(bytes);
    if (!buf)
        return NULL;
    while (atomic_load(&f->run)) {
        int n = nag_audio_render(f->engine, buf, frames);
        if (n > 0 && f->file)
            fwrite(buf, 1, (size_t)n * f->format.channels * nag_format_bytes_per_sample(f->format.sample_format), f->file);
        if (n < frames) {
            /* nothing to pull: as a device would, wait a block's worth */
            struct timespec nap = {0, (long)(1e9 * frames / (f->format.rate > 0 ? f->format.rate : 48000))};
            nanosleep(&nap, NULL);
        }
    }
    free(buf);
    return NULL;
}

static int file_open(NagSink *sink, NagAudio *engine, const NagFormat *want, NagFormat *got)
{
    FileSink *f = sink->priv;
    f->engine = engine;
    f->format = *want;
    *got = *want;
    if (!f->file) {
        f->file = strcmp(f->path, "-") == 0 ? stdout : fopen(f->path, "wb");
        if (!f->file)
            return -1;
    }
    return 0;
}

static int file_start(NagSink *sink)
{
    FileSink *f = sink->priv;
    if (f->thread_started)
        return 0;
    atomic_store(&f->run, 1);
    if (pthread_create(&f->thread, NULL, file_main, sink) != 0)
        return -1;
    f->thread_started = 1;
    return 0;
}

static int file_pause(NagSink *sink)
{
    FileSink *f = sink->priv;
    if (f->thread_started) {
        atomic_store(&f->run, 0);
        pthread_join(f->thread, NULL);
        f->thread_started = 0;
    }
    return 0;
}

static void file_close(NagSink *sink)
{
    FileSink *f = sink->priv;
    file_pause(sink);
    if (f->file && f->file != stdout)
        fclose(f->file);
    else if (f->file)
        fflush(f->file);
    f->file = NULL;
}

static int64_t file_latency(NagSink *sink)
{
    (void)sink;
    return 0;
}

static int file_bit_perfect(NagSink *sink)
{
    (void)sink;
    return 1;
}

static void file_destroy(NagSink *sink)
{
    FileSink *f = sink->priv;
    file_close(sink);
    free(f->path);
    free(f);
    free(sink);
}

NagSink *nag_sink_file_new(const char *path)
{
    NagSink *sink = calloc(1, sizeof(*sink));
    FileSink *f = calloc(1, sizeof(*f));
    if (!sink || !f) {
        free(sink);
        free(f);
        return NULL;
    }
    f->path = strdup(path ? path : "-");
    sink->name = "file";
    sink->open = file_open;
    sink->start = file_start;
    sink->pause = file_pause;
    sink->close = file_close;
    sink->latency_frames = file_latency;
    sink->bit_perfect = file_bit_perfect;
    sink->destroy = file_destroy;
    sink->priv = f;
    return sink;
}
