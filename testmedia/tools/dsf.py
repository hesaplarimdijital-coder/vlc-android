"""DSF (Sony DSD Stream File) writer for DSD64 test tones.

The tones go through a second-order sigma-delta modulator, compiled from the
C source below when a C compiler is available (about 69 dB in-band SNR at
DSD64). Without one, a first-order modulator in closed form is used
(y[n] = floor(S[n]) - floor(S[n-1]) over the running sum S, about 47 dB):
still valid DSD for playback tests, only noisier.
"""
import os
import shutil
import struct
import subprocess
import tempfile

import numpy as np

_C_SOURCE = r"""
#include <stdio.h>
#include <stdlib.h>
/* second-order sigma-delta: reads float64 samples in [-1, 1], writes one byte (0/1) per sample */
int main(void) {
    static double in[1 << 16];
    static unsigned char out[1 << 16];
    double i1 = 0, i2 = 0, y = 0;
    size_t n;
    while ((n = fread(in, sizeof(double), 1 << 16, stdin)) > 0) {
        for (size_t k = 0; k < n; k++) {
            i1 += 0.5 * (in[k] - y);
            i2 += 0.5 * (i1 - y);
            y = i2 >= 0 ? 1.0 : -1.0;
            out[k] = y > 0;
        }
        fwrite(out, 1, n, stdout);
    }
    return 0;
}
"""
_binary = None


def _compiled():
    global _binary
    if _binary is None:
        cc = shutil.which('cc') or shutil.which('gcc') or shutil.which('clang')
        _binary = ''
        if cc:
            d = tempfile.mkdtemp(prefix='dsdmod')
            src, exe = os.path.join(d, 'sdm.c'), os.path.join(d, 'sdm')
            with open(src, 'w') as fh:
                fh.write(_C_SOURCE)
            if subprocess.run([cc, '-O2', '-o', exe, src]).returncode == 0:
                _binary = exe
    return _binary

DSD64 = 2_822_400
BLOCK = 4096  # bytes per channel per block


def modulate(signal):
    """signal in [-1, 1] at the DSD rate -> array of bits (0/1)."""
    exe = _compiled()
    if exe:
        result = subprocess.run([exe], input=np.asarray(signal, dtype=np.float64).tobytes(), capture_output=True, check=True)
        return np.frombuffer(result.stdout, dtype=np.uint8)
    u = (np.asarray(signal, dtype=np.float64) + 1.0) / 2.0
    s = np.cumsum(u)
    floors = np.floor(s)
    bits = np.empty_like(floors)
    bits[0] = floors[0]
    bits[1:] = floors[1:] - floors[:-1]
    return bits.astype(np.uint8)


def pack_lsb_first(bits):
    pad = (-len(bits)) % 8
    if pad:
        bits = np.concatenate([bits, np.zeros(pad, dtype=np.uint8)])
    return np.packbits(bits.reshape(-1, 8), axis=1, bitorder='little').ravel()


def write_dsf(path, channels, rate=DSD64):
    """channels: list of float arrays in [-1, 1] at `rate`, equal length."""
    n = len(channels[0])
    packed = [pack_lsb_first(modulate(ch)) for ch in channels]
    blocks = (len(packed[0]) + BLOCK - 1) // BLOCK
    data = bytearray()
    for b in range(blocks):
        for ch in packed:
            chunk = ch[b * BLOCK:(b + 1) * BLOCK].tobytes()
            data += chunk + b'\x00' * (BLOCK - len(chunk))
    channel_type = {1: 1, 2: 2}.get(len(channels), 2)
    fmt = struct.pack('<4sQIIIIIIQII', b'fmt ', 52, 1, 0, channel_type, len(channels), rate, 1, n, BLOCK, 0)
    data_chunk = struct.pack('<4sQ', b'data', 12 + len(data)) + data
    total = 28 + len(fmt) + len(data_chunk)
    header = struct.pack('<4sQQQ', b'DSD ', 28, total, 0)
    with open(path, 'wb') as fh:
        fh.write(header + fmt + data_chunk)


def tone(freq, seconds, level_db=-6.0, rate=DSD64):
    t = np.arange(int(seconds * rate), dtype=np.float64) / rate
    return 10 ** (level_db / 20) * np.sin(2 * np.pi * freq * t)
