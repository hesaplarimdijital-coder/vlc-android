"""Screenshots of the tablet's palette, before and after, stacked in rows of two with captions.
usage: search_pair.py <out.png> <heading> (<before.png> <after.png> <caption before> <caption after>)..."""
import sys
from PIL import Image, ImageDraw, ImageFont
out, heading, rest = sys.argv[1], sys.argv[2], sys.argv[3:]
rows = [rest[i:i + 4] for i in range(0, len(rest), 4)]
font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 26)
bold = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 34)
pad, gap, head, cap = 28, 24, 82, 48
ims = [(Image.open(a).convert("RGB"), Image.open(b).convert("RGB")) for a, b, _, _ in rows]
w = max(max(a.width, b.width) for a, b in ims)
h = [max(a.height, b.height) for a, b in ims]
sheet = Image.new("RGB", (pad * 2 + w * 2 + gap, head + sum(x + cap + gap for x in h) + pad), (18, 20, 26))
d = ImageDraw.Draw(sheet)
d.text((pad, 22), heading, font=bold, fill=(236, 238, 244))
y = head
for (a, b), (_, _, ca, cb), hh in zip(ims, rows, h):
    for i, (im, text) in enumerate(((a, ca), (b, cb))):
        x = pad + i * (w + gap)
        d.text((x + 2, y + 8), text, font=font, fill=(236, 238, 244))
        sheet.paste(im, (x, y + cap))
    y += hh + cap + gap
sheet.save(out, optimize=True)
print(out, sheet.size)
