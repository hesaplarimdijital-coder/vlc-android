#!/usr/bin/env python3
"""
A model of the music engine's DSP chain (S-906) in plain Python, for test-audio.sh: the same
RBJ biquads in doubles, in the same order (preamp, graphic EQ, parametric EQ, profile), so the
engine's output must match it to rounding. The limiter is not modelled: the tests check its
guarantees instead.

  dsp_model.py IN.f64 OUT.f64 RATE CHANNELS [--preamp DB] [--graphic DB,...] [--peq SPEC]
                                            [--profile SPEC] [--profile-preamp DB] [--auto-preamp]
  dsp_model.py --response RATE HZ [chain options]       |H| in dB at HZ (without the gain stage)
"""
import math
import struct
import sys

GRAPHIC = [25, 35.5, 50, 71, 100, 140, 200, 280, 400, 560, 800, 1120, 1600, 2240, 3150, 4500, 6300, 9000, 12500, 18000]
GRAPHIC_Q = 2.871


def biquad(kind, freq, gain_db, q, rate):
    if freq >= rate * 0.49:
        return None
    freq = max(freq, 1.0)
    gain_db = max(-30.0, min(30.0, gain_db))
    q = max(0.05, min(50.0, q))
    A = 10 ** (gain_db / 40)
    w0 = 2 * math.pi * freq / rate
    cw, sw = math.cos(w0), math.sin(w0)
    alpha = sw / (2 * q)
    sqA2 = 2 * math.sqrt(A) * alpha
    if kind == 'pk':
        if gain_db == 0:
            return None
        b0, b1, b2 = 1 + alpha * A, -2 * cw, 1 - alpha * A
        a0, a1, a2 = 1 + alpha / A, -2 * cw, 1 - alpha / A
    elif kind == 'ls':
        if gain_db == 0:
            return None
        b0 = A * ((A + 1) - (A - 1) * cw + sqA2)
        b1 = 2 * A * ((A - 1) - (A + 1) * cw)
        b2 = A * ((A + 1) - (A - 1) * cw - sqA2)
        a0 = (A + 1) + (A - 1) * cw + sqA2
        a1 = -2 * ((A - 1) + (A + 1) * cw)
        a2 = (A + 1) + (A - 1) * cw - sqA2
    elif kind == 'hs':
        if gain_db == 0:
            return None
        b0 = A * ((A + 1) + (A - 1) * cw + sqA2)
        b1 = -2 * A * ((A - 1) + (A + 1) * cw)
        b2 = A * ((A + 1) + (A - 1) * cw - sqA2)
        a0 = (A + 1) - (A - 1) * cw + sqA2
        a1 = 2 * ((A - 1) - (A + 1) * cw)
        a2 = (A + 1) - (A - 1) * cw - sqA2
    elif kind == 'lp':
        b0, b1, b2 = (1 - cw) / 2, 1 - cw, (1 - cw) / 2
        a0, a1, a2 = 1 + alpha, -2 * cw, 1 - alpha
    elif kind == 'hp':
        b0, b1, b2 = (1 + cw) / 2, -(1 + cw), (1 + cw) / 2
        a0, a1, a2 = 1 + alpha, -2 * cw, 1 - alpha
    else:
        raise ValueError(kind)
    return (b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)


def parse_bands(spec):
    bands = []
    for part in spec.split(';'):
        if not part:
            continue
        kind, hz, db, *rest = part.split(':')
        bands.append((kind, float(hz), float(db), float(rest[0]) if rest else 1.0))
    return bands


def chain(args, rate):
    """The gain (linear) and the biquads of the options in [args]."""
    db = 0.0
    coefs = []
    centres = []
    auto = False
    i = 0
    while i < len(args):
        a = args[i]
        if a == '--preamp':
            db += float(args[i + 1]); i += 2
        elif a == '--auto-preamp':
            auto = True; i += 1
        elif a == '--graphic':
            gains = [float(x) for x in args[i + 1].split(',')]
            for b, g in enumerate(gains[:20]):
                c = biquad('pk', GRAPHIC[b], g, GRAPHIC_Q, rate)
                if c:
                    coefs.append(c)
                    centres.append(GRAPHIC[b])
            i += 2
        elif a in ('--peq', '--profile'):
            for kind, hz, g, q in parse_bands(args[i + 1]):
                c = biquad(kind, hz, g, q, rate)
                if c:
                    coefs.append(c)
                    centres.append(hz)
            i += 2
        elif a == '--profile-preamp':
            db += float(args[i + 1]); i += 2
        else:
            raise SystemExit(f'dsp_model.py: unknown option {a}')
    if auto and coefs:
        top = min(20000.0, rate * 0.49)
        points = [20.0 * (top / 20.0) ** (k / 120) for k in range(121)] + centres
        peak = max(response_db(coefs, hz, rate) for hz in points)
        if peak > 0:
            db -= peak
    gain = 10 ** (db / 20) if db != 0 else 1.0
    return gain, coefs


def response_db(coefs, hz, rate):
    w = 2 * math.pi * hz / rate
    c1, s1, c2, s2 = math.cos(w), math.sin(w), math.cos(2 * w), math.sin(2 * w)
    db = 0.0
    for b0, b1, b2, a1, a2 in coefs:
        nr, ni = b0 + b1 * c1 + b2 * c2, b1 * s1 + b2 * s2
        dr, di = 1 + a1 * c1 + a2 * c2, a1 * s1 + a2 * s2
        db += 10 * math.log10((nr * nr + ni * ni) / (dr * dr + di * di))
    return db


def main():
    if sys.argv[1] == '--response':
        rate, hz = int(sys.argv[2]), float(sys.argv[3])
        _, coefs = chain(sys.argv[4:], rate)
        print(f'{response_db(coefs, hz, rate):.6f}')
        return
    src, dst, rate, channels = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
    gain, coefs = chain(sys.argv[5:], rate)
    raw = open(src, 'rb').read()
    n = len(raw) // 8
    x = list(struct.unpack(f'<{n}d', raw))
    if gain != 1.0:
        x = [v * gain for v in x]
    for b0, b1, b2, a1, a2 in coefs:
        for c in range(channels):
            s1 = s2 = 0.0
            for i in range(c, n, channels):
                v = x[i]
                out = b0 * v + s1
                s1 = b1 * v - a1 * out + s2
                s2 = b2 * v - a2 * out
                x[i] = out
    open(dst, 'wb').write(struct.pack(f'<{n}d', *x))


if __name__ == '__main__':
    main()
