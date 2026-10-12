#include "ring.h"

#include <stdlib.h>
#include <string.h>

int nag_ring_init(NagRing *ring, size_t capacity_frames, int channels)
{
    memset(ring, 0, sizeof(*ring));
    if (capacity_frames == 0 || channels <= 0)
        return -1;
    ring->data = calloc(capacity_frames * (size_t)channels, sizeof(double));
    if (!ring->data)
        return -1;
    ring->capacity = capacity_frames;
    ring->channels = channels;
    atomic_init(&ring->read_pos, 0);
    atomic_init(&ring->write_pos, 0);
    return 0;
}

void nag_ring_free(NagRing *ring)
{
    free(ring->data);
    ring->data = NULL;
    ring->capacity = 0;
}

size_t nag_ring_available(const NagRing *ring)
{
    uint64_t w = atomic_load_explicit(&ring->write_pos, memory_order_acquire);
    uint64_t r = atomic_load_explicit(&ring->read_pos, memory_order_acquire);
    return (size_t)(w - r);
}

size_t nag_ring_space(const NagRing *ring)
{
    return ring->capacity - nag_ring_available(ring);
}

size_t nag_ring_write(NagRing *ring, const double *src, size_t frames)
{
    uint64_t w = atomic_load_explicit(&ring->write_pos, memory_order_relaxed);
    uint64_t r = atomic_load_explicit(&ring->read_pos, memory_order_acquire);
    size_t space = ring->capacity - (size_t)(w - r);
    if (frames > space)
        frames = space;
    size_t slot = (size_t)(w % ring->capacity);
    size_t first = ring->capacity - slot;
    if (first > frames)
        first = frames;
    size_t ch = (size_t)ring->channels;
    memcpy(ring->data + slot * ch, src, first * ch * sizeof(double));
    if (frames > first)
        memcpy(ring->data, src + first * ch, (frames - first) * ch * sizeof(double));
    atomic_store_explicit(&ring->write_pos, w + frames, memory_order_release);
    return frames;
}

size_t nag_ring_read(NagRing *ring, double *dst, size_t frames)
{
    uint64_t r = atomic_load_explicit(&ring->read_pos, memory_order_relaxed);
    uint64_t w = atomic_load_explicit(&ring->write_pos, memory_order_acquire);
    size_t available = (size_t)(w - r);
    if (frames > available)
        frames = available;
    size_t slot = (size_t)(r % ring->capacity);
    size_t first = ring->capacity - slot;
    if (first > frames)
        first = frames;
    size_t ch = (size_t)ring->channels;
    memcpy(dst, ring->data + slot * ch, first * ch * sizeof(double));
    if (frames > first)
        memcpy(dst + first * ch, ring->data, (frames - first) * ch * sizeof(double));
    atomic_store_explicit(&ring->read_pos, r + frames, memory_order_release);
    return frames;
}

size_t nag_ring_skip(NagRing *ring, size_t frames)
{
    uint64_t r = atomic_load_explicit(&ring->read_pos, memory_order_relaxed);
    uint64_t w = atomic_load_explicit(&ring->write_pos, memory_order_acquire);
    size_t available = (size_t)(w - r);
    if (frames > available)
        frames = available;
    atomic_store_explicit(&ring->read_pos, r + frames, memory_order_release);
    return frames;
}

void nag_ring_reset(NagRing *ring)
{
    atomic_store_explicit(&ring->read_pos, 0, memory_order_relaxed);
    atomic_store_explicit(&ring->write_pos, 0, memory_order_release);
}
