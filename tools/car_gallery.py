"""The car pass's screenshots as one sheet with Turkish captions, and each picture under a plain name.
usage: car_gallery.py <shots dir> <out dir> <heading>"""
import glob, os, re, sys
from PIL import Image, ImageDraw, ImageFont

PICKS = [
    (r"-the-car-s-media-app-", "01-muzik-sekmesi", "Müzik sekmesi"),
    (r"-music-the-albums", "02-albumler", "Müzik › Albümler"),
    (r"-an-album-", "03-album", "Bir albümün parçaları"),
    (r"-a-track-tapped-", "04-calan-parca", "Çalan parça: arabanın oynatıcısı"),
    (r"-podcasts\.png$", "05-podcast-sekmesi", "Podcast sekmesi (altta küçük oynatıcı)"),
    (r"-podcasts-discover", "06-podcast-kesfet", "Podcast › Keşfet"),
    (r"-discover-the-popular-shows", "07-populer-programlar", "Keşfet › Popüler programlar"),
    (r"-a-show-of-the-directory-", "08-bir-program", "Rehberden bir program: “Abone ol” ve bölümler"),
    (r"-radio\.png$", "09-radyo-sekmesi", "Radyo sekmesi"),
    (r"-radio-the-countries", "10-radyo-ulkeler", "Radyo › Ülkeler"),
    (r"-a-country-s-stations-", "11-radyo-istasyonlar", "Bir ülkenin istasyonları"),
    (r"-a-station-tapped-", "12-calan-istasyon", "Çalan istasyon"),
    (r"-recently-played", "13-son-calinanlar", "Son çalınanlar"),
    (r"-search-after-back", "14-arama", "Arama: “Profile”"),
]

def main(shots, out, heading):
    os.makedirs(out, exist_ok=True)
    files = sorted(glob.glob(os.path.join(shots, "*.png")))
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 30)
    bold = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 38)
    small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 24)
    tiles = []
    for pattern, name, caption in PICKS:
        hit = [f for f in files if re.search(pattern, os.path.basename(f))]
        if not hit:
            print("missing:", pattern)
            continue
        im = Image.open(hit[0]).convert("RGB")
        im.save(os.path.join(out, name + ".png"), optimize=True)
        tiles.append((im, caption))
    cols, gap, cap, pad, head = 3, 18, 54, 24, 120
    w, h = tiles[0][0].size
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGB", (pad * 2 + cols * w + (cols - 1) * gap, head + rows * (h + cap + gap) + pad), (18, 20, 26))
    d = ImageDraw.Draw(sheet)
    d.text((pad, 22), heading, font=bold, fill=(236, 238, 244))
    d.text((pad, 74), "Android Automotive emülatörü (arabanın kendi medya uygulaması). Android Auto'nun çizimi değildir; sekmeler, listeler ve çalan şey aynı ağaçtır.",
           font=small, fill=(168, 174, 190))
    for i, (im, caption) in enumerate(tiles):
        x = pad + (i % cols) * (w + gap)
        y = head + (i // cols) * (h + cap + gap)
        d.text((x + 2, y + 6), "%d. %s" % (i + 1, caption), font=font, fill=(236, 238, 244))
        sheet.paste(im, (x, y + cap))
    path = os.path.join(out, "00-nagmedar-arabada.png")
    sheet.save(path, optimize=True)
    print(len(tiles), "pictures;", path, sheet.size, os.path.getsize(path) // 1024, "KB")

main(*sys.argv[1:4])
