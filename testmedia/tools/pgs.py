"""Minimal PGS (Blu-ray HDMV presentation graphics, .sup) writer.

Renders text captions with Pillow and writes one display set per caption
(PCS, WDS, PDS, ODS, END) plus a clearing display set at its end time.
Enough for player tests: palette, RLE, positioning and timing are real PGS.
"""
import struct

from PIL import Image, ImageDraw, ImageFont

PCS, WDS, PDS, ODS, END = 0x16, 0x17, 0x14, 0x15, 0x80


def _segment(kind, pts_ms, payload):
    pts = int(round(pts_ms * 90))  # 90 kHz clock
    return b'PG' + struct.pack('>IIBH', pts & 0xFFFFFFFF, 0, kind, len(payload)) + payload


def _rgb_to_ycrcb(r, g, b):
    # BT.709, limited range, as Blu-ray players expect
    y = 16 + (65.481 * r + 128.553 * g + 24.966 * b) / 255
    cb = 128 + (-37.797 * r - 74.203 * g + 112.0 * b) / 255
    cr = 128 + (112.0 * r - 93.786 * g - 18.214 * b) / 255
    return [max(16, min(235, round(y))), max(16, min(240, round(cr))), max(16, min(240, round(cb)))]


def _rle(indices, width, height):
    out = bytearray()
    for row in range(height):
        line = indices[row * width:(row + 1) * width]
        x = 0
        while x < width:
            color = line[x]
            run = 1
            while x + run < width and line[x + run] == color and run < 16383:
                run += 1
            if color == 0:
                if run < 64:
                    out += bytes([0, run])
                else:
                    out += bytes([0, 0x40 | (run >> 8), run & 0xFF])
            elif run < 3:
                out += bytes([color]) * run
            elif run < 64:
                out += bytes([0, 0x80 | run, color])
            else:
                out += bytes([0, 0xC0 | (run >> 8), run & 0xFF, color])
            x += run
        out += b'\x00\x00'  # end of line
    return bytes(out)


def render_caption(text, font_path, size=54, padding=12):
    """White text with a black outline on transparency, quantised to <= 16 colours."""
    font = ImageFont.truetype(font_path, size)
    probe = ImageDraw.Draw(Image.new('RGBA', (1, 1)))
    lines = text.split('\n')
    boxes = [probe.textbbox((0, 0), line, font=font, stroke_width=3) for line in lines]
    line_h = max(b[3] - b[1] for b in boxes) + 8
    width = max(b[2] - b[0] for b in boxes) + 2 * padding
    height = line_h * len(lines) + 2 * padding
    image = Image.new('RGBA', (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    for i, (line, box) in enumerate(zip(lines, boxes)):
        x = (width - (box[2] - box[0])) // 2 - box[0]
        draw.text((x, padding + i * line_h - box[1]), line, font=font, fill=(255, 255, 255, 255),
                  stroke_width=3, stroke_fill=(0, 0, 0, 255))
    return image


def _palette_and_indices(image):
    """Index 0 is transparent; antialiasing levels share up to 15 more entries."""
    quantised = image.quantize(colors=15, method=Image.Quantize.FASTOCTREE)
    palette = quantised.getpalette(rawmode='RGBA') or []
    pixels = list(quantised.getdata())
    entries = [(0, 16, 128, 128, 0)]  # id, Y, Cr, Cb, alpha
    remap = {}
    for index in sorted(set(pixels)):
        r, g, b, a = palette[index * 4:index * 4 + 4]
        if a < 8:
            remap[index] = 0
            continue
        remap[index] = len(entries)
        y, cr, cb = _rgb_to_ycrcb(r, g, b)
        entries.append((len(entries), y, cr, cb, a))
    return entries, [remap[p] for p in pixels]


def write_sup(path, captions, font_path, video_size=(1920, 1080), bottom_margin=80):
    """captions: list of (start_ms, end_ms, text)."""
    vw, vh = video_size
    data = bytearray()
    for number, (start, end, text) in enumerate(captions):
        image = render_caption(text, font_path)
        w, h = image.size
        x, y = (vw - w) // 2, vh - h - bottom_margin
        entries, indices = _palette_and_indices(image)
        rle = _rle(indices, w, h)
        if len(rle) + 4 > 0xFFFFFF - 16:
            raise ValueError('caption too large for one object')
        comp = number * 2
        pcs = struct.pack('>HHBHBBBB', vw, vh, 0x10, comp, 0x80, 0, 0, 1) + struct.pack('>HBBHH', 0, 0, 0, x, y)
        wds = struct.pack('>BBHHHH', 1, 0, x, y, w, h)
        pds = struct.pack('>BB', 0, 0) + b''.join(struct.pack('>BBBBB', *e) for e in entries)
        # one object in as many ODS fragments as needed (first fragment carries the size)
        object_data = struct.pack('>HH', w, h) + rle
        fragments = [object_data[i:i + 65000] for i in range(0, len(object_data), 65000)]
        for i, fragment in enumerate(fragments):
            flags = (0x80 if i == 0 else 0) | (0x40 if i == len(fragments) - 1 else 0)
            header = struct.pack('>HBB', 0, 0, flags)
            if i == 0:
                header += struct.pack('>I', len(object_data))[1:]
            ods = header + fragment
            if i == 0:
                data += _segment(PCS, start, pcs) + _segment(WDS, start, wds) + _segment(PDS, start, pds)
            data += _segment(ODS, start, ods)
        data += _segment(END, start, b'')
        # clear at the end time: no composition objects
        clear = struct.pack('>HHBHBBBB', vw, vh, 0x10, comp + 1, 0x00, 0, 0, 0)
        data += _segment(PCS, end, clear) + _segment(WDS, end, wds) + _segment(END, end, b'')
    with open(path, 'wb') as fh:
        fh.write(data)
