// S-1204: how fast a drive reads a film, sequentially.
#ifndef NAGMEDAR_DISKTEST_H
#define NAGMEDAR_DISKTEST_H

#ifdef __cplusplus
extern "C" {
#endif

/** Version of the JSON below. */
#define DISKTEST_VERSION 1

/** Called after every block: [fraction] 0 … 1 of the test. Returns 0 to go on. */
typedef int (*disktest_progress)(void *opaque, double fraction, double mb_per_s);

/**
 * Reads the file behind [fd] (an open, readable descriptor; not closed) sequentially from
 * [offset], in 4 MiB blocks, until [max_bytes] are read, [max_seconds] pass or the file ends,
 * after asking the kernel to drop what it holds of that part, so that the drive is measured and
 * not the page cache. Returns one JSON object (UTF-8, caller frees with free()):
 *
 *   {"version":1,"error":"","bytes":1073741824,"ms":2604,"mb_per_s":412.3,"first_ms":11,"complete":true}
 *
 * mb_per_s is decimal megabytes a second; first_ms the wait for the first block (a drive waking
 * up, a disk spinning up). [cancel], when not NULL, is polled between blocks.
 */
char *disk_read_speed(int fd, long long offset, long long max_bytes, double max_seconds,
                      disktest_progress progress, void *opaque, volatile int *cancel);

#ifdef __cplusplus
}
#endif

#endif
