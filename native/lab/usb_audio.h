// SP-02: USB Audio Class output through libusb.
#ifndef NAGMEDAR_USB_AUDIO_H
#define NAGMEDAR_USB_AUDIO_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef void (*ua_progress_fn)(void *opaque, double fraction, int64_t frames, int64_t errors);

/** Streaming interfaces, formats, clock and sample rates of the device as JSON (caller frees). */
char *usb_audio_describe(int fd);

/**
 * Plays a -6 dBFS sine of [frequency] Hz for [seconds] at [rate] Hz with [bits]-bit samples,
 * directly on the device (the kernel driver is detached meanwhile). Returns a JSON report:
 * the rate the device confirmed, frames sent against the expected count, feedback rate,
 * transfer and packet errors. The caller frees the string.
 */
char *usb_audio_tone(int fd, unsigned rate, int bits, double seconds, double frequency, ua_progress_fn progress, void *opaque,
                     volatile int *cancel);

#ifdef __cplusplus
}
#endif

#endif
