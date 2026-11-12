import sys, os, glob
from PIL import Image, ImageDraw
d, out, per, w = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
fs = sorted(glob.glob(os.path.join(d, '*.jpg')) + glob.glob(os.path.join(d, '*.png')))
for k in range(0, len(fs), per):
    part = fs[k:k + per]
    ims = [Image.open(f).convert('RGB') for f in part]
    h = int(ims[0].size[1] * w / ims[0].size[0])
    sheet = Image.new('RGB', (len(part) * (w + 6) - 6, h + 22), 'white')
    dr = ImageDraw.Draw(sheet)
    for i, im in enumerate(ims):
        sheet.paste(im.resize((w, h)), (i * (w + 6), 22))
        dr.text((i * (w + 6) + 4, 4), os.path.basename(part[i]), fill='black')
    sheet.save(os.path.join(out, 'sheet-%03d.png' % (k // per)))
print(len(fs), 'frames')
