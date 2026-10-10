// SP-02: drive a USB Audio Class DAC directly with libusb, around the Android mixer.
//
// usb_audio_describe() lists the streaming interfaces, formats, clock and sample rates
// of the device behind a UsbDeviceConnection file descriptor. usb_audio_tone() takes
// the device from the kernel driver, selects the alternate setting that carries the
// requested bit depth, sets the sample rate and plays a sine through isochronous
// transfers, following the feedback endpoint of asynchronous DACs. Both return JSON.
#include "usb_audio.h"

#include <libusb.h>

#include <math.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#define CS_INTERFACE 0x24
#define AC_HEADER 0x01
#define AC_INPUT_TERMINAL 0x02
#define AC_CLOCK_SOURCE 0x0A
#define AC_CLOCK_SELECTOR 0x0B
#define AC_CLOCK_MULTIPLIER 0x0C
#define AS_GENERAL 0x01
#define AS_FORMAT_TYPE 0x02

#define MAX_ALTS 32
#define MAX_RATES 32
#define TRANSFERS 8

typedef struct Alt {
    int iface, alt;
    int channels, subslot, bits;
    int terminal_link;
    uint8_t ep, fb_ep;
    int max_packet, fb_max_packet;
    int interval;
    int sync; /* 1 async, 2 adaptive, 3 sync */
    int implicit_fb;
    unsigned rates[MAX_RATES]; /* UAC1 discrete rates from the format descriptor */
    int n_rates;
    unsigned rate_min, rate_max; /* UAC1 continuous range */
} Alt;

typedef struct Device {
    libusb_context *ctx;
    libusb_device_handle *h;
    int uac; /* 1 or 2 */
    int ac_iface;
    int clock_ids[8], n_clocks;
    int terminal_clock[256]; /* input terminal id -> clock source id (UAC2) */
    int selector_first[256]; /* clock selector/multiplier id -> first source id */
    Alt alts[MAX_ALTS];
    int n_alts;
    int speed;
} Device;

typedef struct Json {
    char *s;
    size_t len, cap;
} Json;

static void jprintf(Json *j, const char *fmt, ...)
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
        size_t cap = j->cap ? j->cap * 2 : 4096;
        if (n >= 0 && cap < j->len + (size_t)n + 1)
            cap = j->len + (size_t)n + 1;
        char *p = realloc(j->s, cap);
        if (!p)
            return;
        j->s = p;
        j->cap = cap;
    }
}

static const char *speed_name(int speed)
{
    switch (speed) {
    case LIBUSB_SPEED_LOW: return "low";
    case LIBUSB_SPEED_FULL: return "full";
    case LIBUSB_SPEED_HIGH: return "high";
    case LIBUSB_SPEED_SUPER: return "super";
    case LIBUSB_SPEED_SUPER_PLUS: return "super+";
    default: return "unknown";
    }
}

static const char *sync_name(int sync)
{
    static const char *names[] = {"none", "async", "adaptive", "sync"};
    return names[sync & 3];
}

static void parse_ac(Device *d, const unsigned char *p, int len)
{
    for (int i = 0; i + 2 < len && p[i] >= 3; i += p[i]) {
        const unsigned char *x = p + i;
        if (x[1] != CS_INTERFACE)
            continue;
        if (x[2] == AC_HEADER && x[0] >= 5)
            d->uac = x[4] >= 2 ? 2 : 1; /* bcdADC 0x0200 for UAC2 */
        else if (x[2] == AC_CLOCK_SOURCE && x[0] >= 4 && d->n_clocks < 8)
            d->clock_ids[d->n_clocks++] = x[3];
        else if (x[2] == AC_INPUT_TERMINAL && x[0] >= 9)
            d->terminal_clock[x[3]] = x[7]; /* UAC2 bCSourceID */
        else if ((x[2] == AC_CLOCK_SELECTOR && x[0] >= 6) || (x[2] == AC_CLOCK_MULTIPLIER && x[0] >= 5))
            d->selector_first[x[3]] = x[2] == AC_CLOCK_SELECTOR ? x[5] : x[4];
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
            if (id->bInterfaceClass != LIBUSB_CLASS_AUDIO)
                continue;
            if (id->bInterfaceSubClass == 1 && d->ac_iface < 0) {
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
                d->n_alts++; /* playback interfaces only */
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

/* UAC2 sample rate ranges of a clock source (GET RANGE). */
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

static unsigned get_rate(Device *d, const Alt *a, int clock)
{
    unsigned char b[4] = {0};
    int ret;
    if (d->uac == 2)
        ret = libusb_control_transfer(d->h, 0xA1, 0x01, 0x0100, (uint16_t)(clock << 8 | d->ac_iface), b, 4, 1000);
    else
        ret = libusb_control_transfer(d->h, 0xA2, 0x81, 0x0100, a->ep, b, 3, 1000);
    return ret > 0 ? (unsigned)(b[0] | b[1] << 8 | b[2] << 16 | (unsigned)b[3] << 24) : 0;
}

static int set_rate(Device *d, const Alt *a, int clock, unsigned rate)
{
    unsigned char b[4] = {rate & 0xFF, rate >> 8 & 0xFF, rate >> 16 & 0xFF, rate >> 24 & 0xFF};
    if (d->uac == 2)
        return libusb_control_transfer(d->h, 0x21, 0x01, 0x0100, (uint16_t)(clock << 8 | d->ac_iface), b, 4, 1000);
    return libusb_control_transfer(d->h, 0x22, 0x01, 0x0100, a->ep, b, 3, 1000);
}

static void describe_alt(Json *j, Device *d, const Alt *a)
{
    jprintf(j, "{\"interface\":%d,\"alt\":%d,\"channels\":%d,\"subslot_bytes\":%d,\"bits\":%d,\"endpoint\":%d,"
               "\"max_packet\":%d,\"interval\":%d,\"sync\":\"%s\",\"feedback_endpoint\":%d,\"implicit_feedback\":%s",
            a->iface, a->alt, a->channels, a->subslot, a->bits, a->ep, a->max_packet, a->interval, sync_name(a->sync),
            a->fb_ep, a->implicit_fb ? "true" : "false");
    if (d->uac == 2) {
        int clock = clock_for(d, a);
        unsigned ranges[MAX_RATES][3];
        int n = clock ? uac2_rates(d, clock, ranges, MAX_RATES) : 0;
        jprintf(j, ",\"clock\":%d,\"rates\":[", clock);
        for (int i = 0; i < n; i++)
            jprintf(j, i ? ",[%u,%u,%u]" : "[%u,%u,%u]", ranges[i][0], ranges[i][1], ranges[i][2]);
        jprintf(j, "]");
    } else {
        jprintf(j, ",\"rates\":[");
        for (int i = 0; i < a->n_rates; i++)
            jprintf(j, i ? ",%u" : "%u", a->rates[i]);
        jprintf(j, "],\"rate_range\":[%u,%u]", a->rate_min, a->rate_max);
    }
    jprintf(j, "}");
}

char *usb_audio_describe(int fd)
{
    Device d;
    char error[256] = {0};
    Json j = {0};
    if (open_device(&d, fd, error, sizeof(error)) == 0) {
        /* GET RANGE on the clock goes to the control interface, which the kernel driver holds */
        libusb_set_auto_detach_kernel_driver(d.h, 1);
        int claimed = d.ac_iface >= 0 && libusb_claim_interface(d.h, d.ac_iface) == 0;
        jprintf(&j, "{\"uac\":%d,\"speed\":\"%s\",\"control_interface\":%d,\"control_claimed\":%s,\"playback\":[", d.uac,
                speed_name(d.speed), d.ac_iface, claimed ? "true" : "false");
        for (int i = 0; i < d.n_alts; i++) {
            if (i)
                jprintf(&j, ",");
            describe_alt(&j, &d, &d.alts[i]);
        }
        jprintf(&j, "],\"error\":\"\"}");
        if (claimed)
            libusb_release_interface(d.h, d.ac_iface);
    } else {
        jprintf(&j, "{\"error\":\"%s\"}", error);
    }
    close_device(&d);
    return j.s;
}

/* ------------------------------------------------------------------ tone */

typedef struct Stream {
    Device *d;
    const Alt *a;
    unsigned rate;
    int frame_bytes;
    double packets_per_second;
    double nominal_fpp, fpp, acc;
    double phase, step, amplitude;
    int stop;
    int in_flight;
    int64_t packets, frames, errors, packet_errors, fb_count;
    double fb_sum;
    struct libusb_transfer *out[TRANSFERS];
    struct libusb_transfer *fb;
    unsigned char fb_buf[8];
    int packets_per_transfer;
} Stream;

static void fill(Stream *s, struct libusb_transfer *t)
{
    int offset = 0;
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
        int channels = s->a->channels ? s->a->channels : 2;
        for (int f = 0; f < frames; f++) {
            double v = sin(s->phase) * s->amplitude;
            s->phase += s->step;
            if (s->phase > 2 * M_PI)
                s->phase -= 2 * M_PI;
            int32_t sample = (int32_t)lrint(v * 2147483647.0); /* 32-bit full scale */
            for (int c = 0; c < channels; c++) {
                if (s->a->subslot == 2) {
                    int16_t x = (int16_t)(sample >> 16);
                    *dst++ = x & 0xFF;
                    *dst++ = x >> 8 & 0xFF;
                } else if (s->a->subslot == 3) {
                    int32_t x = sample >> 8;
                    *dst++ = x & 0xFF;
                    *dst++ = x >> 8 & 0xFF;
                    *dst++ = x >> 16 & 0xFF;
                } else { /* 4-byte subslot: left-justified */
                    int32_t x = s->a->bits && s->a->bits < 32 ? (sample >> (32 - s->a->bits)) << (32 - s->a->bits) : sample;
                    *dst++ = x & 0xFF;
                    *dst++ = x >> 8 & 0xFF;
                    *dst++ = x >> 16 & 0xFF;
                    *dst++ = x >> 24 & 0xFF;
                }
            }
        }
        t->iso_packet_desc[p].length = (unsigned)bytes;
        offset += bytes;
        s->frames += frames;
    }
    t->length = offset;
}

static void LIBUSB_CALL out_done(struct libusb_transfer *t)
{
    Stream *s = t->user_data;
    s->in_flight--;
    if (t->status != LIBUSB_TRANSFER_COMPLETED && t->status != LIBUSB_TRANSFER_CANCELLED)
        s->errors++;
    for (int p = 0; p < t->num_iso_packets; p++)
        if (t->iso_packet_desc[p].status != LIBUSB_TRANSFER_COMPLETED)
            s->packet_errors++;
    s->packets += t->num_iso_packets;
    if (s->stop || t->status == LIBUSB_TRANSFER_NO_DEVICE)
        return;
    fill(s, t);
    if (libusb_submit_transfer(t) == 0)
        s->in_flight++;
    else
        s->errors++;
}

static void LIBUSB_CALL fb_done(struct libusb_transfer *t)
{
    Stream *s = t->user_data;
    s->in_flight--;
    if (t->status == LIBUSB_TRANSFER_COMPLETED && t->iso_packet_desc[0].actual_length >= 3) {
        const unsigned char *b = t->buffer;
        double value;
        if (s->d->speed >= LIBUSB_SPEED_HIGH) /* Q16.16 frames per microframe */
            value = (b[0] | b[1] << 8 | b[2] << 16 | (unsigned)(t->iso_packet_desc[0].actual_length >= 4 ? b[3] : 0) << 24) / 65536.0;
        else /* Q10.14 frames per frame */
            value = (b[0] | b[1] << 8 | b[2] << 16) / 16384.0;
        double interval = s->d->speed >= LIBUSB_SPEED_HIGH ? 8000.0 / s->packets_per_second : 1000.0 / s->packets_per_second;
        double fpp = value * interval;
        /* some devices report per packet interval or in other units: scale by powers of two */
        for (int k = 0; k < 4 && fpp > 1.5 * s->nominal_fpp; k++)
            fpp /= 2;
        for (int k = 0; k < 4 && fpp > 0 && fpp < 0.66 * s->nominal_fpp; k++)
            fpp *= 2;
        if (fpp > 0.9 * s->nominal_fpp && fpp < 1.1 * s->nominal_fpp) {
            s->fpp = fpp;
            s->fb_sum += fpp * s->packets_per_second;
            s->fb_count++;
        }
    }
    if (s->stop)
        return;
    if (libusb_submit_transfer(t) == 0)
        s->in_flight++;
}

static double now_seconds(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec + ts.tv_nsec / 1e9;
}

char *usb_audio_tone(int fd, unsigned rate, int bits, double seconds, double frequency, ua_progress_fn progress, void *opaque,
                     volatile int *cancel)
{
    Device d;
    Json j = {0};
    char error[256] = {0};
    Stream s;
    memset(&s, 0, sizeof(s));
    int ac_claimed = 0, as_claimed = 0, clock = 0;
    unsigned before = 0, after = 0;
    double started = 0, elapsed = 0;
    const Alt *a = NULL;

    if (open_device(&d, fd, error, sizeof(error)) != 0)
        goto out;
    /* the alternate setting with the requested resolution, two channels preferred */
    for (int i = 0; i < d.n_alts; i++) {
        const Alt *c = &d.alts[i];
        if (c->bits != bits)
            continue;
        if (!a || (c->channels == 2 && a->channels != 2))
            a = c;
    }
    if (!a) {
        snprintf(error, sizeof(error), "no playback format with %d bits", bits);
        goto out;
    }
    libusb_set_auto_detach_kernel_driver(d.h, 1);
    int ret;
    if (d.ac_iface >= 0 && (ret = libusb_claim_interface(d.h, d.ac_iface)) == 0)
        ac_claimed = 1;
    if ((ret = libusb_claim_interface(d.h, a->iface)) != 0) {
        snprintf(error, sizeof(error), "claim streaming interface %d: %s", a->iface, libusb_error_name(ret));
        goto out;
    }
    as_claimed = 1;
    if ((ret = libusb_set_interface_alt_setting(d.h, a->iface, a->alt)) != 0) {
        snprintf(error, sizeof(error), "alternate setting %d: %s", a->alt, libusb_error_name(ret));
        goto out;
    }
    clock = d.uac == 2 ? clock_for(&d, a) : 0;
    before = get_rate(&d, a, clock);
    if ((ret = set_rate(&d, a, clock, rate)) < 0) {
        snprintf(error, sizeof(error), "set sample rate %u: %s", rate, libusb_error_name(ret));
        goto out;
    }
    after = get_rate(&d, a, clock);

    s.d = &d;
    s.a = a;
    s.rate = rate;
    s.frame_bytes = a->subslot * (a->channels ? a->channels : 2);
    int interval_units = 1 << (a->interval > 0 ? a->interval - 1 : 0);
    s.packets_per_second = (d.speed >= LIBUSB_SPEED_HIGH ? 8000.0 : 1000.0) / interval_units;
    s.nominal_fpp = rate / s.packets_per_second;
    s.fpp = s.nominal_fpp;
    s.step = 2 * M_PI * frequency / rate;
    s.amplitude = 0.5; /* -6 dBFS */
    s.packets_per_transfer = (int)(s.packets_per_second / 250); /* 4 ms per transfer */
    if (s.packets_per_transfer < 1)
        s.packets_per_transfer = 1;
    if (s.frame_bytes <= 0 || a->max_packet <= 0) {
        snprintf(error, sizeof(error), "unusable format: %d bytes per frame, %d bytes per packet", s.frame_bytes, a->max_packet);
        goto out;
    }
    for (int i = 0; i < TRANSFERS; i++) {
        struct libusb_transfer *t = libusb_alloc_transfer(s.packets_per_transfer);
        unsigned char *buf = malloc((size_t)a->max_packet * s.packets_per_transfer);
        if (!t || !buf) {
            snprintf(error, sizeof(error), "out of memory");
            free(buf);
            libusb_free_transfer(t);
            goto stop;
        }
        libusb_fill_iso_transfer(t, d.h, a->ep, buf, a->max_packet * s.packets_per_transfer, s.packets_per_transfer, out_done, &s, 1000);
        t->flags = LIBUSB_TRANSFER_FREE_BUFFER;
        s.out[i] = t;
    }
    if (a->fb_ep && a->sync == 1 && !a->implicit_fb) {
        s.fb = libusb_alloc_transfer(1);
        if (s.fb) {
            int size = a->fb_max_packet > 0 && a->fb_max_packet <= 8 ? a->fb_max_packet : 4;
            libusb_fill_iso_transfer(s.fb, d.h, a->fb_ep, s.fb_buf, size, 1, fb_done, &s, 1000);
            libusb_set_iso_packet_lengths(s.fb, (unsigned)size);
            if (libusb_submit_transfer(s.fb) == 0)
                s.in_flight++;
        }
    }
    started = now_seconds();
    for (int i = 0; i < TRANSFERS; i++) {
        fill(&s, s.out[i]);
        if (libusb_submit_transfer(s.out[i]) == 0)
            s.in_flight++;
        else
            s.errors++;
    }
    double last_progress = 0;
    while (!s.stop) {
        struct timeval tv = {0, 100000};
        libusb_handle_events_timeout_completed(d.ctx, &tv, NULL);
        elapsed = now_seconds() - started;
        if (elapsed >= seconds || (cancel && *cancel) || s.in_flight == 0)
            s.stop = 1;
        if (progress && elapsed - last_progress >= 0.5) {
            last_progress = elapsed;
            progress(opaque, seconds > 0 ? elapsed / seconds : 0, s.frames, s.errors + s.packet_errors);
        }
    }
stop:
    s.stop = 1;
    for (int i = 0; i < TRANSFERS; i++)
        if (s.out[i])
            libusb_cancel_transfer(s.out[i]);
    if (s.fb)
        libusb_cancel_transfer(s.fb);
    for (int spins = 0; s.in_flight > 0 && spins < 50; spins++) {
        struct timeval tv = {0, 100000};
        libusb_handle_events_timeout_completed(d.ctx, &tv, NULL);
    }
    elapsed = started > 0 ? now_seconds() - started : 0;
    for (int i = 0; i < TRANSFERS; i++)
        if (s.out[i])
            libusb_free_transfer(s.out[i]);
    if (s.fb)
        libusb_free_transfer(s.fb);
    libusb_set_interface_alt_setting(d.h, a->iface, 0);

out:;
    double expected = elapsed * rate;
    jprintf(&j, "{\"uac\":%d,\"speed\":\"%s\",\"requested_rate\":%u,\"requested_bits\":%d,\"rate_before\":%u,\"rate_after\":%u,"
                "\"clock\":%d,\"seconds\":%.3f,\"frames\":%lld,\"expected_frames\":%.0f,\"packets\":%lld,\"transfer_errors\":%lld,"
                "\"packet_errors\":%lld,\"feedback_rate\":%.3f,\"feedback_updates\":%lld,\"format\":",
            d.uac, speed_name(d.speed), rate, bits, before, after, clock, elapsed, (long long)s.frames, expected,
            (long long)s.packets, (long long)s.errors, (long long)s.packet_errors, s.fb_count ? s.fb_sum / s.fb_count : 0.0,
            (long long)s.fb_count);
    if (a)
        describe_alt(&j, &d, a);
    else
        jprintf(&j, "null");
    jprintf(&j, ",\"error\":\"%s\"}", error);
    /* released after the report: describing the clock needs the control interface */
    if (d.h) {
        if (as_claimed)
            libusb_release_interface(d.h, a->iface);
        if (ac_claimed)
            libusb_release_interface(d.h, d.ac_iface);
    }
    close_device(&d);
    return j.s;
}
