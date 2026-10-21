// S-710: what CTranslate2 takes from glibc and Android's bionic lacks, given to its sources
// (forced in with -include on Android; nothing else is changed in them).
#pragma once

#if defined(__ANDROID__) && defined(__cplusplus)
#include <errno.h>
#include <pthread.h>
#include <sched.h>

// a worker pinned to a core (CTranslate2's cpu_core_offset, which the app does not use)
static inline int pthread_setaffinity_np(pthread_t thread, size_t size, const cpu_set_t *set) {
    return sched_setaffinity(pthread_gettid_np(thread), size, set) == 0 ? 0 : errno;
}
#endif
