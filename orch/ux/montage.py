"""Before/after montages of the Back transitions from two sets of MotionStripsTest strips.

usage: montage.py <before dir> <after dir> <out dir>
A strip is frames of FRAME_W x FRAME_H under a LABEL-high caption row, GAP apart (MotionStripsTest.saveStrip).
"""
import sys
from PIL import Image, ImageDraw, ImageFont

FRAME_W, FRAME_H, LABEL, GAP = 308, 668, 28, 6
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
REGULAR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BG = (24, 24, 28)
BEFORE = (214, 92, 92)
AFTER = (92, 176, 112)


def frames(path):
    strip = Image.open(path).convert("RGB")
    count = (strip.width + GAP) // (FRAME_W + GAP)
    return [strip.crop((i * (FRAME_W + GAP), 0, i * (FRAME_W + GAP) + FRAME_W, LABEL + FRAME_H)) for i in range(count)]


def row(draw, sheet, y, tag, colour, note, picked, margin, big, small):
    draw.rectangle((margin, y, margin + 150, y + 44), fill=colour)
    draw.text((margin + 14, y + 7), tag, font=big, fill=(255, 255, 255))
    draw.text((margin + 170, y + 10), note, font=small, fill=(220, 220, 226))
    y += 56
    for i, frame in enumerate(picked):
        sheet.paste(frame, (margin + i * (FRAME_W + 12), y))
    return y + LABEL + FRAME_H + 26


def montage(title, before, after, picks, note_before, note_after, out, after_picks=None):
    b = frames(before)
    a = frames(after)
    picked_b = [b[i] for i in picks if i < len(b)]
    picked_a = [a[i] for i in (after_picks or picks) if i < len(a)]
    margin = 28
    width = margin * 2 + len(picks) * FRAME_W + (len(picks) - 1) * 12
    height = 96 + 2 * (56 + LABEL + FRAME_H + 26) + 10
    sheet = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(sheet)
    big = ImageFont.truetype(BOLD, 28)
    head = ImageFont.truetype(BOLD, 38)
    small = ImageFont.truetype(REGULAR, 24)
    draw.text((margin, 26), title, font=head, fill=(255, 255, 255))
    y = 96
    y = row(draw, sheet, y, "ÖNCE", BEFORE, note_before, picked_b, margin, big, small)
    row(draw, sheet, y, "SONRA", AFTER, note_after, picked_a, margin, big, small)
    sheet.save(out)
    print(out, sheet.size)


if __name__ == "__main__":
    before, after, out = sys.argv[1:4]
    # frames: 0 önce, 1 16 ms, 2 48 ms, 3 96 ms, 4 144 ms, 5 208 ms, 6 304 ms
    montage(
        "Sekmeden Geri (Podcast → Müzik)",
        f"{before}/tab-back.png", f"{after}/tab-back.png", [0, 2, 3, 4],
        "iki liste yan yana kayıyor", "eski sekme soluyor, sonra Müzik beliriyor",
        f"{out}/geri-1-sekme.png",
    )
    montage(
        "Sayfadan Geri (Ekolayzer → Daha fazla)",
        f"{before}/page-pop.png", f"{after}/page-pop.png", [0, 2, 3, 4],
        "iki sayfa aynı renkte yan yana", "sayfa opak kayıyor, alttaki yerinde aydınlanıyor",
        f"{out}/geri-2-sayfa.png",
    )
    montage(
        "Albüm açılırken (Geri aynı yolun tersi)",
        f"{before}/album-open.png", f"{after}/album-open.png", [0, 2, 3, 4],
        "iki sayfa birden yarı saydam", "sayfa opak geliyor, liste kararıyor",
        f"{out}/geri-3-albüm.png",
    )
