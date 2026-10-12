// A single-producer, single-consumer ring of interleaved double frames: the decoder thread
// writes, the render thread reads, neither waits for the other. Positions are 64-bit frame
// counters that only grow; the slot is the position modulo the capacity.
#ifndef NAG_RING_H
#define NAG_RING_H

#include <stdatomic.h>
#include <stddef.h>
#include <stdint.h>

typedef struct NagRing {
    double *data;
    size_t capacity; /* frames */
    int channels;
    _Atomic uint64_t read_pos;
    _Atomic uint64_t write_pos;
} NagRing;

int nag_ring_init(NagRing *ring, size_t capacity_frames, int channels);
void nag_ring_free(NagRing *ring);

/** Frames waiting to be read. */
size_t nag_ring_available(const NagRing *ring);
/** Frames that can be written before the ring is full. */
size_t nag_ring_space(const NagRing *ring);

/** Writes up to [frames] frames from [src]; returns how many fitted. Producer only. */
size_t nag_ring_write(NagRing *ring, const double *src, size_t frames);
/** Reads up to [frames] frames into [dst]; returns how many there were. Consumer only. */
size_t nag_ring_read(NagRing *ring, double *dst, size_t frames);
/** Skips up to [frames] frames; returns how many were skipped. Consumer only. */
size_t nag_ring_skip(NagRing *ring, size_t frames);

/** Empties the ring. Only while neither side touches it (the engine holds the render first). */
void nag_ring_reset(NagRing *ring);

#endif
