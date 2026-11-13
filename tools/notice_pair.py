"""Two phone screenshots side by side (their lower part), captioned: the swiped notice before and after the fix.
usage: notice_pair.py <before.png> <after.png> <out.png> <caption before> <caption after> <heading>"""
import sys
from PIL import Image, ImageDraw, ImageFont
before, after, out, cap_a, cap_b, heading = sys.argv[1:7]
a, b = Image.open(before).convert("RGB"), Image.open(after).convert("RGB")
top = int(a.height * 0.60)
a, b = a.crop((0, top, a.width, a.height)), b.crop((0, top, b.width, b.height))
font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 34)
bold = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 40)
pad, gap, head, cap = 28, 28, 86, 60
sheet = Image.new("RGB", (pad * 2 + a.width + gap + b.width, head + cap + max(a.height, b.height) + pad), (18, 20, 26))
d = ImageDraw.Draw(sheet)
d.text((pad, 24), heading, font=bold, fill=(236, 238, 244))
for i, (im, text) in enumerate(((a, cap_a), (b, cap_b))):
    x = pad + i * (a.width + gap)
    d.text((x + 2, head + 8), text, font=font, fill=(236, 238, 244))
    sheet.paste(im, (x, head + cap))
sheet.save(out, optimize=True)
print(out, sheet.size)
