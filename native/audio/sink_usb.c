/*
 * Nağmedar Ses Motoru: the USB DAC driven directly (S-903), around Android's mixer.
 *
 * The device comes as the file descriptor of a UsbDeviceConnection. The sink parses the
 * Audio Class descriptors (UAC1 and UAC2, as the Lab's SP-02 code does), picks the playback
 * alternate setting whose sample size matches the format asked for, sets the clock's rate,
 * and streams isochronous transfers from a thread of its own, the engine rendering straight
 * into each packet; an asynchronous DAC's feedback endpoint sets the packets' size. The
 * feature unit's volume control is the hardware volume. A device gone (the cable pulled) ends
 * the transfers with NO_DEVICE: the engine is told through nag_audio_sink_lost and the owner
 * falls back to another output.
 */
#include "nagaudio.h"

#include <libusb.h>

#include <math.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define CS_INTERFACE 0x24
#define AC_HEADER 0x01
#define AC_INPUT_TERMINAL 0x02
#define AC_FEATURE_UNIT 0x06
#define AC_CLOCK_SOURCE 0x0A
#define AC_CLOCK_SELECTOR 0x0B
#define AC_CLOCK_MULTIPLIER 0x0C
#define AS_GENERAL 0x01
#define AS_FORMAT_TYPE 0x02
#define FU_MUTE 0x01
#define FU_VOLUME 0x02

#define MAX_ALTS 32
#define MAX_RATES 32
#define TRANSFERS 8
#define MAX_FEATURE_UNITS 8

typedef struct Alt {
    int iface, alt;
    int channels, subslot, bits;
    int terminal_link;
    uint8_t ep, fb_ep;
    int max_packet, fb_max_packet;
    int interval;
    int sync; /* 1 async, 2 adaptive, 3 sync */
    int implicit_fb;
    unsigned rates[MAX_RATES];
    int n_rates;
    unsigned rate_min, rate_max;
} Alt;

typedef struct FeatureUnit {
    int id, source;
    int volume_master, volume_channels; /* the controls present */
    int mute_master;
} FeatureUnit;

typedef struct Device {
    libusb_context *ctx;
    libusb_device_handle *h;
    int uac;
    int ac_iface;
    int clock_ids[8], n_clocks;
    int terminal_clock[256];
    int selector_first[256];
    Alt alts[MAX_ALTS];
    int n_alts;
    FeatureUnit units[MAX_FEATURE_UNITS];
    int n_units;
    int speed;
} Device;

typedef struct UsbSink {
    NagAudio *engine;
    NagSink *self;
    int fd;
    Device d;
    int device_open;
    const Alt *a;
    int clock;
    NagFormat format;
    int frame_bytes;
    int ac_claimed, as_claimed;
    int streaming; /* transfers allocated, the thread running */
    /* the stream */
    double packets_per_second, nominal_fpp, fpp, acc;
    int packets_per_transfer;
    struct libusb_transfer *out[TRANSFERS];
    struct libusb_transfer *fb;
    unsigned char fb_buf[8];
    int in_flight;
    atomic_int playing;
    atomic_int quit;
    atomic_int lost;
    _Atomic int64_t frames_out;
    int64_t errors;
    pthread_t thread;
    int thread_started;
    /* hardware volume, 1/256 dB */
    const FeatureUnit *fu;
    int vol_min, vol_max, vol_res;
    _Atomic double volume;
    char error[160];
} UsbSink;

/* -------------------------------------------------------------- descriptors */

static void parse_ac(Device *d, const unsigned char *p, int len)
{
    for (int i = 0; i + 2 < len && p[i] >= 3; i += p[i]) {
        const unsigned char *x = p + i;
        if (x[1] != CS_INTERFACE)
            continue;
        if (x[2] == AC_HEADER && x[0] >= 5)
            d->uac = x[4] >= 2 ? 2 : 1;
        else if (x[2] == AC_CLOCK_SOURCE && x[0] >= 4 && d->n_clocks < 8)
            d->clock_ids[d->n_clocks++] = x[3];
        else if (x[2] == AC_INPUT_TERMINAL && x[0] >= 9)
            d->terminal_clock[x[3]] = x[7];
        else if ((x[2] == AC_CLOCK_SELECTOR && x[0] >= 6) || (x[2] == AC_CLOCK_MULTIPLIER && x[0] >= 5))
            d->selector_first[x[3]] = x[2] == AC_CLOCK_SELECTOR ? x[5] : x[4];
        else if (x[2] == AC_FEATURE_UNIT && d->n_units < MAX_FEATURE_UNITS) {
            FeatureUnit *u = &d->units[d->n_units];
            memset(u, 0, sizeof *u);
            u->id = x[3];
            u->source = x[4];
            if (d->uac == 2 && x[0] >= 9) {
                /* bmaControls: 4 bytes per channel, master first; two bits per control */
                unsigned master = x[5] | x[6] << 8 | x[7] << 16 | (unsigned)x[8] << 24;
                u->mute_master = ((master >> 0) & 3) == 3;
                u->volume_master = ((master >> 2) & 3) == 3;
                int channels = (x[0] - 7) / 4 - 1;
                for (int c = 1; c <= channels && c <= 2; c++) {
                    const unsigned char *b = x + 5 + 4 * c;
                    unsigned ctl = b[0] | b[1] << 8 | b[2] << 16 | (unsigned)b[3] << 24;
                    if (((ctl >> 2) & 3) == 3)
                        u->volume_channels++;
                }
            } else if (x[0] >= 7) {
                int size = x[5];
                if (size >= 1) {
                    u->mute_master = x[6] & FU_MUTE;
                    u->volume_master = (x[6] & FU_VOLUME) != 0;
                    int channels = (x[0] - 7) / (size ? size : 1) - 1;
                    for (int c = 1; c <= channels && c <= 2; c++)
                        if (x[6 + c * size] & FU_VOLUME)
                            u->volume_channels++;
                }
            }
            d->n_units++;
        }
    }
}

static void parse_as(Alt *a, const unsigned char *p, int len, int uac)
{
    for (int i = 0; i + 2 < len && p[i] >= 3; i += p[i]) {
        const unsigned char *x = p + i;
        if (x[1] != CS_INTERFACE)
            continue;
        if (x[2] == AS_GENERAL) {
            a->terminal_link = x[3];
            if (uac == 2 && x[0] >= 11)
                a->channels = x[10];
        } else if (x[2] == AS_FORMAT_TYPE) {
            if (uac == 2 && x[0] >= 6) {
                a->subslot = x[4];
                a->bits = x[5];
            } else if (uac == 1 && x[0] >= 8) {
                a->channels = x[4];
                a->subslot = x[5];
                a->bits = x[6];
                int n = x[7];
                if (n == 0 && x[0] >= 14) {
                    a->rate_min = x[8] | x[9] << 8 | x[10] << 16;
                    a->rate_max = x[11] | x[12] << 8 | x[13] << 16;
                }
                for (int k = 0; k < n && k < MAX_RATES && 8 + 3 * k + 2 < x[0]; k++)
                    a->rates[a->n_rates++] = x[8 + 3 * k] | x[9 + 3 * k] << 8 | x[10 + 3 * k] << 16;
            }
        }
    }
}

static int open_device(Device *d, int fd, char *error, size_t error_size)
{
    memset(d, 0, sizeof(*d));
    d->ac_iface = -1;
    int ret = libusb_set_option(NULL, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);
    if (ret == 0)
        ret = libusb_init(&d->ctx);
    if (ret == 0)
        ret = libusb_wrap_sys_device(d->ctx, (intptr_t)fd, &d->h);
    if (ret != 0) {
        snprintf(error, error_size, "libusb: %s", libusb_error_name(ret));
        return ret;
    }
    libusb_device *dev = libusb_get_device(d->h);
    d->speed = libusb_get_device_speed(dev);
    struct libusb_config_descriptor *cfg = NULL;
    if ((ret = libusb_get_active_config_descriptor(dev, &cfg)) != 0) {
        snprintf(error, error_size, "configuration descriptor: %s", libusb_error_name(ret));
        return ret;
    }
    for (int i = 0; i < cfg->bNumInterfaces; i++) {
        const struct libusb_interface *itf = &cfg->interface[i];
        for (int k = 0; k < itf->num_altsetting; k++) {
            const struct libusb_interface_descriptor *id = &itf->altsetting[k];
            if (id->bInterfaceClass == LIBUSB_CLASS_AUDIO && id->bInterfaceSubClass == 1 && d->ac_iface < 0) {
                d->ac_iface = id->bInterfaceNumber;
                parse_ac(d, id->extra, id->extra_length);
            }
        }
    }
    if (!d->uac)
        d->uac = 1;
    for (int i = 0; i < cfg->bNumInterfaces; i++) {
        const struct libusb_interface *itf = &cfg->interface[i];
        for (int k = 0; k < itf->num_altsetting; k++) {
            const struct libusb_interface_descriptor *id = &itf->altsetting[k];
            if (id->bInterfaceClass != LIBUSB_CLASS_AUDIO || id->bInterfaceSubClass != 2 || id->bNumEndpoints == 0)
                continue;
            if (d->n_alts >= MAX_ALTS)
                break;
            Alt *a = &d->alts[d->n_alts];
            memset(a, 0, sizeof(*a));
            a->iface = id->bInterfaceNumber;
            a->alt = id->bAlternateSetting;
            parse_as(a, id->extra, id->extra_length, d->uac);
            int out = 0;
            for (int e = 0; e < id->bNumEndpoints; e++) {
                const struct libusb_endpoint_descriptor *ep = &id->endpoint[e];
                if ((ep->bmAttributes & 3) != LIBUSB_TRANSFER_TYPE_ISOCHRONOUS)
                    continue;
                int usage = (ep->bmAttributes & LIBUSB_ISO_USAGE_TYPE_MASK) >> 4;
                if (!(ep->bEndpointAddress & LIBUSB_ENDPOINT_IN)) {
                    out = 1;
                    a->ep = ep->bEndpointAddress;
                    a->max_packet = (ep->wMaxPacketSize & 0x7FF) * (1 + ((ep->wMaxPacketSize >> 11) & 3));
                    a->interval = ep->bInterval ? ep->bInterval : 1;
                    a->sync = (ep->bmAttributes & LIBUSB_ISO_SYNC_TYPE_MASK) >> 2;
                    a->implicit_fb = usage == LIBUSB_ISO_USAGE_TYPE_IMPLICIT;
                    if (ep->bSynchAddress)
                        a->fb_ep = ep->bSynchAddress | LIBUSB_ENDPOINT_IN;
                } else if (usage == LIBUSB_ISO_USAGE_TYPE_FEEDBACK) {
                    a->fb_ep = ep->bEndpointAddress;
                    a->fb_max_packet = ep->wMaxPacketSize & 0x7FF;
                }
            }
            if (out)
                d->n_alts++;
        }
    }
    libusb_free_config_descriptor(cfg);
    return 0;
}

static void close_device(Device *d)
{
    if (d->h)
        libusb_close(d->h);
    if (d->ctx)
        libusb_exit(d->ctx);
    d->h = NULL;
    d->ctx = NULL;
}

static int clock_for(Device *d, const Alt *a)
{
    int id = d->terminal_clock[a->terminal_link & 0xFF];
    for (int hop = 0; hop < 4 && id && d->selector_first[id]; hop++)
        id = d->selector_first[id];
    if (!id && d->n_clocks)
        id = d->clock_ids[0];
    return id;
}

static int uac2_rates(Device *d, int clock, unsigned (*ranges)[3], int max)
{
    unsigned char buf[2 + 12 * MAX_RATES];
    int ret = libusb_control_transfer(d->h, 0xA1, 0x02, 0x0100, (uint16_t)(clock << 8 | d->ac_iface), buf, sizeof(buf), 1000);
    if (ret < 2)
        return ret < 0 ? ret : 0;
    int n = buf[0] | buf[1] << 8;
    if (n > max)
        n = max;
    for (int i = 0; i < n && 2 + 12 * i + 12 <= ret; i++) {
        const unsigned char *r = buf + 2 + 12 * i;
        ranges[i][0] = r[0] | r[1] << 8 | r[2] << 16 | (unsigned)r[3] << 24;
        ranges[i][1] = r[4] | r[5] << 8 | r[6] << 16 | (unsigned)r[7] << 24;
        ranges[i][2] = r[8] | r[9] << 8 | r[10] << 16 | (unsigned)r[11] << 24;
    }
    return n;
}

/* Whether the alternate setting (and its clock) takes [rate]. */
static int rate_supported(Device *d, const Alt *a, int clock, unsigned rate)
{
    if (d->uac == 2) {
        unsigned ranges[MAX_RATES][3];
        int n = clock ? uac2_rates(d, clock, ranges, MAX_RATES) : 0;
        if (n <= 0)
            return 1; /* unknown: try */
        for (int i = 0; i < n; i++) {
            unsigned lo = ranges[i][0], hi = ranges[i][1], res = ranges[i][2];
            if (rate == lo || rate == hi)
                return 1;
            if (rate > lo && rate < hi && (res == 0 || (rate - lo) % res == 0))
                return 1;
        }
        return 0;
    }
    if (a->n_rates == 0 && a->rate_min == 0)
        return 1;
    for (int i = 0; i < a->n_rates; i++)
        if (a->rates[i] == rate)
            return 1;
    return a->rate_min && rate >= a->rate_min && rate <= a->rate_max;
}

static int set_rate(Device *d, const Alt *a, int clock, unsigned rate)
{
    unsigned char b[4] = {rate & 0xFF, rate >> 8 & 0xFF, rate >> 16 & 0xFF, rate >> 24 & 0xFF};
    if (d->uac == 2)
        return libusb_control_transfer(d->h, 0x21, 0x01, 0x0100, (uint16_t)(clock << 8 | d->ac_iface), b, 4, 1000);
    return libusb_control_transfer(d->h, 0x22, 0x01, 0x0100, a->ep, b, 3, 1000);
}

/* ------------------------------------------------------------------ volume */

static int fu_get(UsbSink *s, int request, int channel, int16_t *value)
{
    unsigned char b[2] = {0};
    uint16_t w_value = (uint16_t)(FU_VOLUME << 8 | channel), w_index = (uint16_t)(s->fu->id << 8 | s->d.ac_iface);
    int ret = libusb_control_transfer(s->d.h, 0xA1, (uint8_t)request, w_value, w_index, b, 2, 500);
    if (ret < 2)
        return -1;
    *value = (int16_t)(b[0] | b[1] << 8);
    return 0;
}

static int fu_set(UsbSink *s, int channel, int16_t value)
{
    unsigned char b[2] = {(unsigned char)(value & 0xFF), (unsigned char)((value >> 8) & 0xFF)};
    uint16_t w_value = (uint16_t)(FU_VOLUME << 8 | channel), w_index = (uint16_t)(s->fu->id << 8 | s->d.ac_iface);
    return libusb_control_transfer(s->d.h, 0x21, 0x01, w_value, w_index, b, 2, 500) == 2 ? 0 : -1;
}

/* Finds the volume control and its range; the volume set so far goes to the device. */
static void volume_setup(UsbSink *s)
{
    s->fu = NULL;
    for (int i = 0; i < s->d.n_units; i++) {
        if (s->d.units[i].volume_master || s->d.units[i].volume_channels) {
            s->fu = &s->d.units[i];
            break;
        }
    }
    if (!s->fu)
        return;
    int channel = s->fu->volume_master ? 0 : 1;
    int16_t lo = 0, hi = 0, res = 256;
    if (s->d.uac == 2) {
        unsigned char b[2 + 6 * 4] = {0};
        uint16_t w_value = (uint16_t)(FU_VOLUME << 8 | channel), w_index = (uint16_t)(s->fu->id << 8 | s->d.ac_iface);
        int ret = libusb_control_transfer(s->d.h, 0xA1, 0x02, w_value, w_index, b, sizeof(b), 500);
        if (ret >= 8) {
            lo = (int16_t)(b[2] | b[3] << 8);
            hi = (int16_t)(b[4] | b[5] << 8);
            res = (int16_t)(b[6] | b[7] << 8);
        } else {
            s->fu = NULL;
            return;
        }
    } else {
        /* UAC1: GET_MIN, GET_MAX, GET_RES */
        if (fu_get(s, 0x82, channel, &lo) || fu_get(s, 0x83, channel, &hi) || (lo == 0 && hi == 0)) {
            s->fu = NULL;
            return;
        }
        if (fu_get(s, 0x84, channel, &res) || res <= 0)
            res = 256;
    }
    s->vol_min = lo;
    s->vol_max = hi;
    s->vol_res = res > 0 ? res : 1;
}

static void volume_apply(UsbSink *s)
{
    if (!s->fu || !s->d.h)
        return;
    double v = atomic_load(&s->volume);
    int value;
    if (v <= 0.0) {
        value = s->vol_min;
    } else {
        /* the linear volume as attenuation under the device's loudest, as the Android output does it */
        double db = 20.0 * log10(v > 1.0 ? 1.0 : v);
        value = s->vol_max + (int)lrint(db * 256.0);
        if (value < s->vol_min)
            value = s->vol_min;
        value = s->vol_min + ((value - s->vol_min) / s->vol_res) * s->vol_res;
    }
    if (s->fu->volume_master) {
        fu_set(s, 0, (int16_t)value);
    } else {
        fu_set(s, 1, (int16_t)value);
        fu_set(s, 2, (int16_t)value);
    }
}

/* ------------------------------------------------------------------ stream */

static void fill(UsbSink *s, struct libusb_transfer *t)
{
    int offset = 0;
    const int playing = atomic_load(&s->playing);
    for (int p = 0; p < t->num_iso_packets; p++) {
        s->acc += s->fpp;
        int frames = (int)s->acc;
        s->acc -= frames;
        int bytes = frames * s->frame_bytes;
        if (bytes > s->a->max_packet) {
            frames = s->a->max_packet / s->frame_bytes;
            bytes = frames * s->frame_bytes;
        }
        unsigned char *dst = t->buffer + offset;
        if (playing)
            nag_audio_render(s->engine, dst, frames); /* silence where nothing plays */
        else
            memset(dst, 0, (size_t)bytes);
        t->iso_packet_desc[p].length = (unsigned)bytes;
        offset += bytes;
        atomic_fetch_add(&s->frames_out, frames);
    }
    t->length = offset;
}

static void LIBUSB_CALL out_done(struct libusb_transfer *t)
{
    UsbSink *s = t->user_data;
    s->in_flight--;
    if (t->status == LIBUSB_TRANSFER_NO_DEVICE)
        atomic_store(&s->lost, 1);
    else if (t->status != LIBUSB_TRANSFER_COMPLETED && t->status != LIBUSB_TRANSFER_CANCELLED)
        s->errors++;
    if (atomic_load(&s->quit) || atomic_load(&s->lost))
        return;
    fill(s, t);
    if (libusb_submit_transfer(t) == 0)
        s->in_flight++;
    else
        s->errors++;
}

static void LIBUSB_CALL fb_done(struct libusb_transfer *t)
{
    UsbSink *s = t->user_data;
    s->in_flight--;
    if (t->status == LIBUSB_TRANSFER_NO_DEVICE)
        atomic_store(&s->lost, 1);
    if (t->status == LIBUSB_TRANSFER_COMPLETED && t->iso_packet_desc[0].actual_length >= 3) {
        const unsigned char *b = t->buffer;
        double value;
        if (s->d.speed >= LIBUSB_SPEED_HIGH)
            value = (b[0] | b[1] << 8 | b[2] << 16 | (unsigned)(t->iso_packet_desc[0].actual_length >= 4 ? b[3] : 0) << 24) / 65536.0;
        else
            value = (b[0] | b[1] << 8 | b[2] << 16) / 16384.0;
        double interval = s->d.speed >= LIBUSB_SPEED_HIGH ? 8000.0 / s->packets_per_second : 1000.0 / s->packets_per_second;
        double fpp = value * interval;
        for (int k = 0; k < 4 && fpp > 1.5 * s->nominal_fpp; k++)
            fpp /= 2;
        for (int k = 0; k < 4 && fpp > 0 && fpp < 0.66 * s->nominal_fpp; k++)
            fpp *= 2;
        if (fpp > 0.9 * s->nominal_fpp && fpp < 1.1 * s->nominal_fpp)
            s->fpp = fpp;
    }
    if (atomic_load(&s->quit) || atomic_load(&s->lost))
        return;
    if (libusb_submit_transfer(t) == 0)
        s->in_flight++;
}

static void *usb_main(void *arg)
{
    UsbSink *s = arg;
    int told = 0;
    while (!atomic_load(&s->quit)) {
        struct timeval tv = {0, 50000};
        libusb_handle_events_timeout_completed(s->d.ctx, &tv, NULL);
        if (atomic_load(&s->lost) && !told) {
            told = 1;
            nag_audio_sink_lost(s->engine, s->self);
        }
    }
    return NULL;
}

static void stop_stream(UsbSink *s)
{
    if (!s->streaming)
        return;
    atomic_store(&s->quit, 1);
    for (int i = 0; i < TRANSFERS; i++)
        if (s->out[i])
            libusb_cancel_transfer(s->out[i]);
    if (s->fb)
        libusb_cancel_transfer(s->fb);
    if (s->thread_started) {
        pthread_join(s->thread, NULL);
        s->thread_started = 0;
    }
    for (int spins = 0; s->in_flight > 0 && spins < 20; spins++) {
        struct timeval tv = {0, 50000};
        libusb_handle_events_timeout_completed(s->d.ctx, &tv, NULL);
    }
    for (int i = 0; i < TRANSFERS; i++) {
        if (s->out[i])
            libusb_free_transfer(s->out[i]);
        s->out[i] = NULL;
    }
    if (s->fb)
        libusb_free_transfer(s->fb);
    s->fb = NULL;
    if (s->a && s->d.h && !atomic_load(&s->lost))
        libusb_set_interface_alt_setting(s->d.h, s->a->iface, 0);
    if (s->as_claimed && s->d.h)
        libusb_release_interface(s->d.h, s->a->iface);
    s->as_claimed = 0;
    s->streaming = 0;
    s->in_flight = 0;
}

static int subslot_for(NagSampleFormat f)
{
    switch (f) {
    case NAG_FMT_S16: return 2;
    case NAG_FMT_S24_3: return 3;
    case NAG_FMT_S32: return 4;
    default: return 0;
    }
}

static NagSampleFormat format_for(int subslot)
{
    switch (subslot) {
    case 2: return NAG_FMT_S16;
    case 3: return NAG_FMT_S24_3;
    case 4: return NAG_FMT_S32;
    default: return NAG_FMT_NONE;
    }
}

static int usb_open(NagSink *sink, NagAudio *engine, const NagFormat *want, NagFormat *got)
{
    UsbSink *s = sink->priv;
    s->engine = engine;
    s->self = sink;
    if (atomic_load(&s->lost))
        return -1; /* gone: the owner chooses another output */
    if (want->channels != 2)
        return -1;
    if (s->streaming && s->format.rate == want->rate && s->format.sample_format == want->sample_format) {
        *got = s->format;
        return 0;
    }
    stop_stream(s);
    atomic_store(&s->quit, 0);
    if (!s->device_open) {
        if (open_device(&s->d, s->fd, s->error, sizeof(s->error)) != 0)
            return -1;
        s->device_open = 1;
        libusb_set_auto_detach_kernel_driver(s->d.h, 1);
        if (s->d.ac_iface >= 0 && libusb_claim_interface(s->d.h, s->d.ac_iface) == 0)
            s->ac_claimed = 1;
        volume_setup(s);
    }
    /* the alternate setting: two channels, the sample size asked for, else the nearest wider, else narrower */
    int wanted = subslot_for(want->sample_format);
    if (wanted == 0)
        wanted = 4; /* floats: the widest */
    const Alt *best = NULL;
    int best_score = -1;
    for (int i = 0; i < s->d.n_alts; i++) {
        const Alt *a = &s->d.alts[i];
        if ((a->channels ? a->channels : 2) != 2 || a->subslot < 2 || a->subslot > 4 || a->max_packet <= 0)
            continue;
        int clock = s->d.uac == 2 ? clock_for(&s->d, a) : 0;
        if (!rate_supported(&s->d, a, clock, (unsigned)want->rate))
            continue;
        int score = a->subslot == wanted ? 3 : a->subslot > wanted ? 2 : 1;
        if (score > best_score) {
            best_score = score;
            best = a;
        }
    }
    if (!best) {
        snprintf(s->error, sizeof(s->error), "the device takes no 2-channel format at %d Hz", want->rate);
        return -1;
    }
    s->a = best;
    s->clock = s->d.uac == 2 ? clock_for(&s->d, best) : 0;
    int ret = libusb_claim_interface(s->d.h, best->iface);
    if (ret != 0) {
        snprintf(s->error, sizeof(s->error), "claim: %s", libusb_error_name(ret));
        return -1;
    }
    s->as_claimed = 1;
    if ((ret = libusb_set_interface_alt_setting(s->d.h, best->iface, best->alt)) != 0) {
        snprintf(s->error, sizeof(s->error), "alternate setting: %s", libusb_error_name(ret));
        return -1;
    }
    if ((ret = set_rate(&s->d, best, s->clock, (unsigned)want->rate)) < 0) {
        snprintf(s->error, sizeof(s->error), "rate %d: %s", want->rate, libusb_error_name(ret));
        return -1;
    }
    s->format.rate = want->rate;
    s->format.channels = 2;
    s->format.sample_format = format_for(best->subslot);
    s->frame_bytes = best->subslot * 2;
    int interval_units = 1 << (best->interval > 0 ? best->interval - 1 : 0);
    s->packets_per_second = (s->d.speed >= LIBUSB_SPEED_HIGH ? 8000.0 : 1000.0) / interval_units;
    s->nominal_fpp = want->rate / s->packets_per_second;
    s->fpp = s->nominal_fpp;
    s->acc = 0;
    s->packets_per_transfer = (int)(s->packets_per_second / 250); /* 4 ms a transfer */
    if (s->packets_per_transfer < 1)
        s->packets_per_transfer = 1;
    for (int i = 0; i < TRANSFERS; i++) {
        struct libusb_transfer *t = libusb_alloc_transfer(s->packets_per_transfer);
        unsigned char *buf = malloc((size_t)best->max_packet * s->packets_per_transfer);
        if (!t || !buf) {
            free(buf);
            libusb_free_transfer(t);
            snprintf(s->error, sizeof(s->error), "out of memory");
            return -1;
        }
        libusb_fill_iso_transfer(t, s->d.h, best->ep, buf, best->max_packet * s->packets_per_transfer, s->packets_per_transfer, out_done, s, 1000);
        t->flags = LIBUSB_TRANSFER_FREE_BUFFER;
        s->out[i] = t;
    }
    if (best->fb_ep && best->sync == 1 && !best->implicit_fb) {
        s->fb = libusb_alloc_transfer(1);
        if (s->fb) {
            int size = best->fb_max_packet > 0 && best->fb_max_packet <= 8 ? best->fb_max_packet : 4;
            libusb_fill_iso_transfer(s->fb, s->d.h, best->fb_ep, s->fb_buf, size, 1, fb_done, s, 1000);
            libusb_set_iso_packet_lengths(s->fb, (unsigned)size);
            if (libusb_submit_transfer(s->fb) == 0)
                s->in_flight++;
        }
    }
    s->streaming = 1;
    for (int i = 0; i < TRANSFERS; i++) {
        fill(s, s->out[i]);
        if (libusb_submit_transfer(s->out[i]) == 0)
            s->in_flight++;
        else
            s->errors++;
    }
    if (pthread_create(&s->thread, NULL, usb_main, s) != 0) {
        snprintf(s->error, sizeof(s->error), "no thread");
        return -1;
    }
    s->thread_started = 1;
    volume_apply(s);
    *got = s->format;
    return 0;
}

static int usb_start(NagSink *sink)
{
    atomic_store(&((UsbSink *)sink->priv)->playing, 1);
    return 0;
}

static int usb_pause(NagSink *sink)
{
    atomic_store(&((UsbSink *)sink->priv)->playing, 0);
    return 0;
}

static void usb_close(NagSink *sink)
{
    stop_stream(sink->priv);
}

static int64_t usb_latency(NagSink *sink)
{
    UsbSink *s = sink->priv;
    return (int64_t)(TRANSFERS * s->packets_per_transfer * s->nominal_fpp);
}

static int usb_bit_perfect(NagSink *sink)
{
    (void)sink;
    return 1;
}

static void usb_destroy(NagSink *sink)
{
    UsbSink *s = sink->priv;
    stop_stream(s);
    if (s->ac_claimed && s->d.h)
        libusb_release_interface(s->d.h, s->d.ac_iface);
    close_device(&s->d);
    free(s);
    free(sink);
}

NagSink *nag_sink_usb_new(int fd)
{
    NagSink *sink = calloc(1, sizeof(*sink));
    UsbSink *s = calloc(1, sizeof(*s));
    if (!sink || !s) {
        free(sink);
        free(s);
        return NULL;
    }
    s->fd = fd;
    atomic_store(&s->volume, 1.0);
    sink->name = "usb";
    sink->open = usb_open;
    sink->start = usb_start;
    sink->pause = usb_pause;
    sink->close = usb_close;
    sink->latency_frames = usb_latency;
    sink->bit_perfect = usb_bit_perfect;
    sink->destroy = usb_destroy;
    sink->priv = s;
    return sink;
}

int nag_sink_usb_set_volume(NagSink *sink, double volume)
{
    if (!sink || sink->destroy != usb_destroy)
        return -1;
    UsbSink *s = sink->priv;
    atomic_store(&s->volume, volume < 0 ? 0 : volume);
    if (!s->fu)
        return -1;
    volume_apply(s);
    return 0;
}

int nag_sink_usb_has_volume(NagSink *sink)
{
    return sink && sink->destroy == usb_destroy && ((UsbSink *)sink->priv)->fu != NULL;
}

const char *nag_sink_usb_error(NagSink *sink)
{
    return sink && sink->destroy == usb_destroy ? ((UsbSink *)sink->priv)->error : "";
}
