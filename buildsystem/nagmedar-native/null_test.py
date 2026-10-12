#!/usr/bin/env python3
"""
S-1405: the null test. A recording of the DAC's digital output (a USB or S/PDIF capture on a
computer, raw samples) against the engine's own decode of the same file (nagaudio decode
FILE -f s24 -o reference.raw): the two are aligned on their first non-silent sample and
compared sample by sample. Bit-perfect means no sample differs.

  null_test.py REFERENCE.raw CAPTURE.raw [--format s16|s24|s32] [--channels 2] [--rate 44100]

Prints JSON: the offset found, the frames compared, the samples that differ, the largest
difference in LSB and the first differing frame. Exit 0 when nothing differs.
"""
import json
import struct
import sys


def load(path, fmt, channels):
    data = open(path, "rb").read()
    if fmt == "s16":
        n = len(data) // 2
        samples = list(struct.unpack("<%dh" % n, data[:n * 2]))
    elif fmt == "s32":
        n = len(data) // 4
        samples = list(struct.unpack("<%di" % n, data[:n * 4]))
    else:
        n = len(data) // 3
        samples = [0] * n
        for i in range(n):
            v = data[3 * i] | data[3 * i + 1] << 8 | data[3 * i + 2] << 16
            samples[i] = v - 0x1000000 if v & 0x800000 else v
    frames = len(samples) // channels
    return samples[:frames * channels], frames


def first_sound(samples, channels, threshold):
    for i in range(0, len(samples), channels):
        for c in range(channels):
            if abs(samples[i + c]) > threshold:
                return i // channels
    return None


def main():
    args = sys.argv[1:]
    if len(args) < 2:
        sys.exit(__doc__)
    reference, capture = args[0], args[1]
    fmt, channels, rate = "s24", 2, 0
    i = 2
    while i < len(args):
        if args[i] == "--format":
            fmt = args[i + 1]
        elif args[i] == "--channels":
            channels = int(args[i + 1])
        elif args[i] == "--rate":
            rate = int(args[i + 1])
        i += 2
    ref, ref_frames = load(reference, fmt, channels)
    cap, cap_frames = load(capture, fmt, channels)
    # a capture may carry a little noise in its silence: a few LSB count as silence there
    ref_start = first_sound(ref, channels, 0)
    cap_start = first_sound(cap, channels, 4)
    if ref_start is None or cap_start is None:
        print(json.dumps({"error": "silence only", "reference_frames": ref_frames, "capture_frames": cap_frames}))
        sys.exit(2)
    # the exact offset: among the frames around the first sound, the one where the next 4096 frames match best
    best_offset, best_matches = None, -1
    window = min(4096, ref_frames - ref_start)
    for shift in range(-64, 65):
        offset = cap_start + shift - ref_start
        if offset < 0 or offset + ref_start + window > cap_frames:
            continue
        matches = 0
        base_r, base_c = ref_start * channels, (ref_start + offset) * channels
        for k in range(window * channels):
            if ref[base_r + k] == cap[base_c + k]:
                matches += 1
        if matches > best_matches:
            best_matches, best_offset = matches, offset
    if best_offset is None:
        print(json.dumps({"error": "the capture is shorter than the reference's start"}))
        sys.exit(2)
    frames = min(ref_frames, cap_frames - best_offset)
    differing, largest, first = 0, 0, None
    for f in range(frames):
        for c in range(channels):
            d = abs(ref[f * channels + c] - cap[(f + best_offset) * channels + c])
            if d:
                differing += 1
                if d > largest:
                    largest = d
                if first is None:
                    first = f
    result = {
        "offset_frames": best_offset,
        "offset_seconds": best_offset / rate if rate else None,
        "frames_compared": frames,
        "reference_frames": ref_frames,
        "capture_frames": cap_frames,
        "samples_differing": differing,
        "largest_difference_lsb": largest,
        "first_difference_frame": first,
        "bit_perfect": differing == 0,
    }
    print(json.dumps(result))
    sys.exit(0 if differing == 0 else 1)


if __name__ == "__main__":
    main()
