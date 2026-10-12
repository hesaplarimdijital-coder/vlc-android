// S-1204: how fast a drive reads a film, sequentially.
//
// The test reads a part of the film itself, so that it measures the drive the film is on and
// through the same path the player reads it (a USB drive behind Android's FUSE layer, a
// network share). The kernel is asked to drop its cached pages of that part first; a part read
// just before could still come from a cache below (the drive's own), which the first block's
// wait and an unlikely speed give away.
#define _GNU_SOURCE
#include "disktest.h"

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#define BLOCK (4 * 1024 * 1024)

static double now_ms(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000.0 + ts.tv_nsec / 1e6;
}

static char *error_json(const char *what, int err)
{
    char text[256];
    snprintf(text, sizeof(text), "{\"version\":%d,\"error\":\"%s: %s\"}", DISKTEST_VERSION, what, strerror(err));
    return strdup(text);
}

char *disk_read_speed(int fd, long long offset, long long max_bytes, double max_seconds,
                      disktest_progress progress, void *opaque, volatile int *cancel)
{
    struct stat st;
    if (fstat(fd, &st) < 0)
        return error_json("stat", errno);
    long long size = (long long)st.st_size;
    if (offset < 0 || offset >= size)
        offset = 0;
    /* whole blocks, from a block boundary */
    offset -= offset % BLOCK;
    if (max_bytes <= 0 || max_bytes > size - offset)
        max_bytes = size - offset;
    if (max_bytes <= 0)
        return error_json("size", EINVAL);

#ifdef POSIX_FADV_DONTNEED
    posix_fadvise(fd, offset, max_bytes, POSIX_FADV_DONTNEED);
    posix_fadvise(fd, offset, max_bytes, POSIX_FADV_SEQUENTIAL);
#endif

    char *buffer = malloc(BLOCK);
    if (!buffer)
        return error_json("memory", ENOMEM);

    long long done = 0;
    int complete = 1, failed = 0;
    double start = now_ms(), first = -1, elapsed = 0;
    while (done < max_bytes) {
        if (cancel && *cancel) {
            complete = 0;
            break;
        }
        size_t want = (size_t)(max_bytes - done < BLOCK ? max_bytes - done : BLOCK);
        ssize_t got = pread(fd, buffer, want, offset + done);
        if (got < 0) {
            if (errno == EINTR)
                continue;
            failed = errno;
            break;
        }
        if (got == 0)
            break;
        done += got;
        elapsed = now_ms() - start;
        if (first < 0)
            first = elapsed;
        double mbps = elapsed > 0 ? done / 1e3 / elapsed : 0;
        double fraction = (double)done / (double)max_bytes;
        if (max_seconds > 0 && elapsed / 1000.0 / max_seconds > fraction)
            fraction = elapsed / 1000.0 / max_seconds;
        if (progress && progress(opaque, fraction < 1 ? fraction : 1, mbps)) {
            complete = 0;
            break;
        }
        if (max_seconds > 0 && elapsed >= max_seconds * 1000.0)
            break;
    }
    free(buffer);
    if (failed && done == 0)
        return error_json("read", failed);

    char text[256];
    snprintf(text, sizeof(text),
             "{\"version\":%d,\"error\":\"\",\"bytes\":%lld,\"ms\":%.0f,\"mb_per_s\":%.1f,\"first_ms\":%.0f,\"complete\":%s}",
             DISKTEST_VERSION, done, elapsed, elapsed > 0 ? done / 1e3 / elapsed : 0.0, first < 0 ? 0.0 : first,
             complete && !failed ? "true" : "false");
    return strdup(text);
}
