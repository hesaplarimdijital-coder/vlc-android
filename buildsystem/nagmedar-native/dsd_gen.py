#!/usr/bin/env python3
"""
A DSD test signal for test-audio.sh (S-908): a sine, delta-sigma modulated (second order) at
DSD64, written as a DSF file (blocks per channel, bits LSB first) and as a DSDIFF file
(interleaved bytes, bits MSB first) from the same bitstream, so the engine must decode both to
the same PCM.

  dsd_gen.py OUT.dsf OUT.dff [--hz 1000] [--seconds 0.5] [--amplitude 0.5]
"""
import math
import struct
import sys

RATE = 2822400
BLOCK = 4096


def modulate(hz, seconds, amplitude):
    """The bits of each channel, in time order, as bytearrays of 0/1."""
    n = int(RATE * seconds)
    n -= n % (BLOCK * 8)
    bits = bytearray(n)
    i1 = i2 = 0.0
    y = 1.0
    w = 2 * math.pi * hz / RATE
    for i in range(n):
        x = amplitude * math.sin(w * i)
        # the classic second-order modulator: two integrators, the output fed back into both
        i1 += x - y
        i2 += i1 - y
        y = 1.0 if i2 >= 0 else -1.0
        bits[i] = 1 if y > 0 else 0
    return bits


def pack(bits, msb_first):
    out = bytearray(len(bits) // 8)
    for i in range(0, len(bits), 8):
        byte = 0
        for k in range(8):
            if bits[i + k]:
                byte |= (0x80 >> k) if msb_first else (1 << k)
        out[i // 8] = byte
    return bytes(out)


def dsf(path, left, right, sample_count):
    data = bytearray()
    for off in range(0, len(left), BLOCK):
        data += left[off:off + BLOCK].ljust(BLOCK, b"\0")
        data += right[off:off + BLOCK].ljust(BLOCK, b"\0")
    fmt = struct.pack("<4sQIIIIIIQII", b"fmt ", 52, 1, 0, 2, 2, RATE, 1, sample_count, BLOCK, 0)
    data_chunk = struct.pack("<4sQ", b"data", 12 + len(data)) + data
    total = 28 + 52 + len(data_chunk)
    head = struct.pack("<4sQQQ", b"DSD ", 28, total, 0)
    open(path, "wb").write(head + fmt + data_chunk)


def chunk(ident, payload):
    if len(payload) % 2:
        payload += b"\0"
    return ident + struct.pack(">Q", len(payload)) + payload


def dff(path, left, right):
    inter = bytearray(len(left) * 2)
    inter[0::2] = left
    inter[1::2] = right
    cmpr = b"DSD " + bytes([14]) + b"not compressed"
    prop = chunk(b"PROP", b"SND " + chunk(b"FS  ", struct.pack(">I", RATE)) + chunk(b"CHNL", struct.pack(">H", 2) + b"SLFTSRGT") + chunk(b"CMPR", cmpr))
    body = b"DSD " + chunk(b"FVER", struct.pack(">I", 0x01050000)) + prop + chunk(b"DSD ", bytes(inter))
    open(path, "wb").write(b"FRM8" + struct.pack(">Q", len(body)) + body)


def main():
    args = sys.argv[1:]
    out_dsf, out_dff = args[0], args[1]
    hz, seconds, amplitude = 1000.0, 0.5, 0.5
    i = 2
    while i < len(args):
        if args[i] == "--hz":
            hz = float(args[i + 1])
        elif args[i] == "--seconds":
            seconds = float(args[i + 1])
        elif args[i] == "--amplitude":
            amplitude = float(args[i + 1])
        i += 2
    bits = modulate(hz, seconds, amplitude)
    left = pack(bits, msb_first=False)
    right = pack(bits, msb_first=False)
    dsf(out_dsf, left, right, len(bits))
    left_msb = pack(bits, msb_first=True)
    dff(out_dff, left_msb, left_msb)


if __name__ == "__main__":
    main()
