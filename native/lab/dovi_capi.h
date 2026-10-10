// SPDX-License-Identifier: MIT
// The part of libdovi's C API (dovi_tool's dolby_vision crate, feature "capi") that the
// lab uses. Declared by hand so the build does not need cargo-c/cbindgen; the signatures
// follow dolby_vision/src/capi.rs at the pinned dovi_tool version.
#ifndef NAGMEDAR_DOVI_CAPI_H
#define NAGMEDAR_DOVI_CAPI_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct DoviRpuOpaque DoviRpuOpaque;

typedef struct DoviData {
    const uint8_t *data;
    size_t len;
} DoviData;

/* Only the leading fields of DoviRpuDataHeader are read; the struct is #[repr(C)]. */
typedef struct DoviRpuDataHeaderPrefix {
    uint8_t guessed_profile;
    const char *el_type; /* "FEL" or "MEL" for profile 7, NULL otherwise */
} DoviRpuDataHeaderPrefix;

DoviRpuOpaque *dovi_parse_unspec62_nalu(const uint8_t *buf, size_t len);
const char *dovi_rpu_get_error(const DoviRpuOpaque *ptr);
int32_t dovi_convert_rpu_with_mode(DoviRpuOpaque *ptr, uint8_t mode);
const DoviData *dovi_write_unspec62_nalu(DoviRpuOpaque *ptr);
const DoviRpuDataHeaderPrefix *dovi_rpu_get_header(const DoviRpuOpaque *ptr);
void dovi_rpu_free_header(const DoviRpuDataHeaderPrefix *ptr);
void dovi_data_free(const DoviData *data);
void dovi_rpu_free(DoviRpuOpaque *ptr);

#ifdef __cplusplus
}
#endif

#endif
