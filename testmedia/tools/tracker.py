"""Tracker modules for the test set and the engine's tests (S-911).

Writes the same little piece as a ProTracker MOD, a FastTracker II XM, a Scream Tracker 3
S3M and an Impulse Tracker IT: one looping sine sample, four channels, two patterns of 64
rows at speed 6 and 125 BPM (0.12 s a row, 15.36 s in all), a melody on the first channel,
a bass an octave down on the second, chords on the third and fourth, and the volume set on
some notes (MOD has no volume column: its Cxx effect does it). Everything is written by hand
from the formats' specifications, so the set carries no third-party content.

Usage: tracker.py <out.mod|out.xm|out.s3m|out.it> [title]
"""
import math
import struct
import sys

TITLE = 'Nagmedar tracker'
SAMPLE_NAME = 'sinus'

# the sample: a sine of 64 points, played at the formats' middle note (8363 Hz: 130.7 Hz, a C-3)
SAMPLE_LENGTH = 64
SAMPLE = bytes((round(100 * math.sin(2 * math.pi * i / SAMPLE_LENGTH)) & 0xFF) for i in range(SAMPLE_LENGTH))

# (row, channel, semitones from the middle note, volume 0..64 or None) of the piece
MELODY = [0, 4, 7, 12, 7, 4, 0, -5]
BASS = [-12, -12, -8, -8, -12, -12, -5, -5]
CHORDS = [(4, 7), (4, 7), (2, 7), (2, 7), (4, 7), (4, 7), (0, 5), (0, 5)]


def notes_of(pattern):
    """The notes of pattern 0 or 1: the melody goes up a fourth in the second."""
    events = []
    for i in range(8):
        row = i * 8
        events.append((row, 0, MELODY[i] + (5 if pattern else 0), 48 if i % 2 else None))
        events.append((row, 1, BASS[i], 40))
        if i % 2 == 0:
            events.append((row, 2, CHORDS[i][0], 32))
            events.append((row, 3, CHORDS[i][1], 32))
    return events


ROWS = 64
CHANNELS = 4
PATTERNS = 2
SPEED = 6
TEMPO = 125


def bytes_padded(text, size):
    data = text.encode('ascii', 'replace')[:size]
    return data + b'\0' * (size - len(data))


# ---------------------------------------------------------------- ProTracker MOD

# Amiga periods of C-1 … B-3 (finetune 0); MOD's C-2 (period 428) is the middle note
PERIODS = [856, 808, 762, 720, 678, 640, 604, 570, 538, 508, 480, 453,
           428, 404, 381, 360, 339, 320, 302, 285, 269, 254, 240, 226,
           214, 202, 190, 180, 170, 160, 151, 143, 135, 127, 120, 113]


def write_mod(path, title=TITLE):
    out = bytearray()
    out += bytes_padded(title, 20)
    # 31 sample headers: ours first, the rest empty
    out += bytes_padded(SAMPLE_NAME, 22) + struct.pack('>HBBHH', SAMPLE_LENGTH // 2, 0, 64, 0, SAMPLE_LENGTH // 2)
    out += (bytes_padded('', 22) + struct.pack('>HBBHH', 0, 0, 0, 0, 1)) * 30
    out += struct.pack('BB', PATTERNS, 127) + bytes(range(PATTERNS)) + b'\0' * (128 - PATTERNS) + b'M.K.'
    for pattern in range(PATTERNS):
        cells = [[None] * CHANNELS for _ in range(ROWS)]
        for row, channel, semitones, volume in notes_of(pattern):
            cells[row][channel] = (semitones, volume)
        for row in range(ROWS):
            for channel in range(CHANNELS):
                cell = cells[row][channel]
                if cell is None:
                    out += b'\0\0\0\0'
                    continue
                semitones, volume = cell
                period = PERIODS[12 + semitones]
                effect, param = (0xC, volume) if volume is not None else (0, 0)
                sample = 1
                out += bytes([(sample & 0x10) | (period >> 8), period & 0xFF, ((sample & 0x0F) << 4) | effect, param])
    out += SAMPLE
    with open(path, 'wb') as fh:
        fh.write(out)


# ---------------------------------------------------------------- FastTracker II XM

def write_xm(path, title=TITLE):
    out = bytearray()
    out += b'Extended Module: ' + bytes_padded(title, 20) + b'\x1a' + bytes_padded('FastTracker v2.00', 20) + struct.pack('<H', 0x0104)
    out += struct.pack('<IHHHHHHHH', 276, PATTERNS, 0, CHANNELS, PATTERNS, 1, 0, SPEED, TEMPO)
    out += bytes(range(PATTERNS)) + b'\0' * (256 - PATTERNS)
    for pattern in range(PATTERNS):
        cells = [[None] * CHANNELS for _ in range(ROWS)]
        for row, channel, semitones, volume in notes_of(pattern):
            cells[row][channel] = (semitones, volume)
        data = bytearray()
        for row in range(ROWS):
            for channel in range(CHANNELS):
                cell = cells[row][channel]
                if cell is None:
                    data += b'\x80'  # a packed empty cell
                    continue
                semitones, volume = cell
                # unpacked: note (C-4 = 49), instrument, volume column (0x10 + volume), effect, parameter
                data += bytes([49 + semitones, 1, 0x10 + volume if volume is not None else 0, 0, 0])
        out += struct.pack('<IBHH', 9, 0, ROWS, len(data)) + data
    # the instrument: no envelopes, one sample, its data delta-coded
    out += struct.pack('<I', 263) + bytes_padded(SAMPLE_NAME, 22) + b'\0' + struct.pack('<H', 1)
    out += struct.pack('<I', 40) + b'\0' * 96 + b'\0' * 48 + b'\0' * 48 + b'\0' * 14 + struct.pack('<H', 0) + b'\0' * 22
    out += struct.pack('<IIIBbBBbB', SAMPLE_LENGTH, 0, SAMPLE_LENGTH, 64, 0, 1, 128, 0, 0) + bytes_padded(SAMPLE_NAME, 22)
    previous = 0
    for value in SAMPLE:
        signed = value - 256 if value >= 128 else value
        out += bytes([(signed - previous) & 0xFF])
        previous = signed
    with open(path, 'wb') as fh:
        fh.write(out)


# ---------------------------------------------------------------- Scream Tracker 3 S3M

def write_s3m(path, title=TITLE):
    # the header, then the orders and the parapointers (paragraphs of 16 bytes)
    header = bytearray()
    header += bytes_padded(title, 28) + b'\x1a\x10\0\0'
    orders = bytes(range(PATTERNS)) + b'\xff' * 2  # the orders, then the end marks (an even count, as ST3 keeps it)
    header += struct.pack('<HHHHHH', len(orders), 1, PATTERNS, 0, 0x1320, 2) + b'SCRM'
    header += struct.pack('<BBBBBB', 64, SPEED, TEMPO, 0xB0, 0, 0xFC) + b'\0' * 8 + struct.pack('<H', 0)
    header += bytes([0, 8, 1, 9]) + b'\xff' * 28  # L R L R, the other channels unused
    header += orders
    pointers_at = len(header)
    header += b'\0' * (2 * (1 + PATTERNS))
    header += bytes([0x20 | 3, 0x20 | 12, 0x20 | 3, 0x20 | 12]) + b'\0' * 28  # the channels' pans
    header += b'\0' * ((-len(header)) % 16)

    def paragraph(data):
        return data + b'\0' * ((-len(data)) % 16)

    # the patterns, packed: per row, the cells with a note and an instrument (and a volume), then the row's end
    patterns = []
    for pattern in range(PATTERNS):
        rows = [bytearray() for _ in range(ROWS)]
        for row, channel, semitones, volume in notes_of(pattern):
            note = 48 + semitones  # C-4: octave 4, semitone 0
            cell = bytes([channel | 0x20 | (0x40 if volume is not None else 0), ((note // 12) << 4) | (note % 12), 1])
            if volume is not None:
                cell += bytes([volume])
            rows[row] += cell
        data = b''.join(bytes(r) + b'\0' for r in rows)
        patterns.append(paragraph(struct.pack('<H', len(data) + 2) + data))

    # the sample's header, then its data (unsigned, as file format 2 says)
    sample_at = len(header) + 80 + sum(len(p) for p in patterns)
    sample = bytearray(b'\x01' + bytes_padded(SAMPLE_NAME, 12))
    sample += bytes([(sample_at >> 20) & 0xFF]) + struct.pack('<H', (sample_at >> 4) & 0xFFFF)
    sample += struct.pack('<IIIBBBB', SAMPLE_LENGTH, 0, SAMPLE_LENGTH, 64, 0, 0, 1) + struct.pack('<I', 8363) + b'\0' * 12
    sample += bytes_padded(SAMPLE_NAME, 28) + b'SCRS'
    assert len(sample) == 80
    pointers = [len(header) >> 4]
    at = len(header) + 80
    for p in patterns:
        pointers.append(at >> 4)
        at += len(p)
    header[pointers_at:pointers_at + 2 * len(pointers)] = struct.pack('<%dH' % len(pointers), *pointers)
    out = bytes(header) + bytes(sample) + b''.join(patterns) + bytes((v + 128) & 0xFF for v in SAMPLE)
    with open(path, 'wb') as fh:
        fh.write(out)


# ---------------------------------------------------------------- Impulse Tracker IT

def write_it(path, title=TITLE):
    header = bytearray()
    header += b'IMPM' + bytes_padded(title, 26) + struct.pack('<H', 0x0410)
    header += struct.pack('<HHHHHHHH', PATTERNS + 1, 0, 1, PATTERNS, 0x0214, 0x0214, 0x0009, 0)
    header += struct.pack('<BBBBBB', 128, 48, SPEED, TEMPO, 128, 0) + struct.pack('<HI', 0, 0) + b'\0' * 4
    header += bytes([0, 64, 0, 64] + [32] * 60)  # the channels' pans: L R L R
    header += bytes([64] * 64)  # their volumes
    header += bytes(range(PATTERNS)) + b'\xff'  # the orders, then the end mark
    pointers_at = len(header)
    header += b'\0' * (4 * (1 + PATTERNS))

    # the patterns, packed: a mask byte per cell (note, instrument, volume), the row's end
    patterns = []
    for pattern in range(PATTERNS):
        rows = [bytearray() for _ in range(ROWS)]
        for row, channel, semitones, volume in notes_of(pattern):
            mask = 0x03 | (0x04 if volume is not None else 0)
            cell = bytes([(channel + 1) | 0x80, mask, 60 + semitones, 1])
            if volume is not None:
                cell += bytes([volume])
            rows[row] += cell
        data = b''.join(bytes(r) + b'\0' for r in rows)
        patterns.append(struct.pack('<HH', len(data), ROWS) + b'\0' * 4 + data)

    sample_header_at = len(header)
    sample_data_at = sample_header_at + 80 + sum(len(p) for p in patterns)
    sample = bytearray(b'IMPS' + bytes_padded(SAMPLE_NAME, 12) + b'\0')
    sample += struct.pack('<BBB', 64, 0x11, 64) + bytes_padded(SAMPLE_NAME, 26) + struct.pack('<BB', 1, 0)
    sample += struct.pack('<IIIIII', SAMPLE_LENGTH, 0, SAMPLE_LENGTH, 8363, 0, 0) + struct.pack('<I', sample_data_at)
    sample += struct.pack('<BBBB', 0, 0, 0, 0)
    assert len(sample) == 80
    pointers = [sample_header_at]
    at = sample_header_at + 80
    for p in patterns:
        pointers.append(at)
        at += len(p)
    header[pointers_at:pointers_at + 4 * len(pointers)] = struct.pack('<%dI' % len(pointers), *pointers)
    out = bytes(header) + bytes(sample) + b''.join(patterns) + SAMPLE
    with open(path, 'wb') as fh:
        fh.write(out)


WRITERS = {'mod': write_mod, 'xm': write_xm, 's3m': write_s3m, 'it': write_it}


def write(path, title=TITLE):
    extension = path.rsplit('.', 1)[-1].lower()
    WRITERS[extension](path, title)


if __name__ == '__main__':
    write(sys.argv[1], *sys.argv[2:3])
