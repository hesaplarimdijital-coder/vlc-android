#!/usr/bin/env python3
"""Generate the remote access web app's brand assets from the app's own sources (S-1304).

The web app is VideoLAN's, pinned in compile-remoteaccess.sh; what makes it Nağmedar lives here:

  assets/src/scss/colors.scss   the colours of NagTheme (theme/Palette.kt, theme/AccentTones.kt),
                                as Sass variables and the CSS filters that tint its black icons
  assets/public/*.png, .ico     favicon, touch icons and manifest icons from the branding SVGs
  assets/public/logo.svg        the mark, for the page header
  assets/src/assets/fonts/*.woff2
                                IBM Plex Sans (SIL OFL 1.1), the app's typeface, as web fonts

Usage: gen_brand.py <repo_root>   (needs Pillow, fontTools with brotli, Playwright with Chromium)
"""
import colorsys
import io
import os
import random
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
BRANDING = os.path.join(HERE, '..', 'branding', 'svg')
MARK_VIEWBOX = '26.5 26 56 56'
BG = '#12161B'


def hex_of(rgb):
    return '#%02X%02X%02X' % tuple(round(max(0, min(1, c)) * 255) for c in rgb)


def parse_hex(text):
    text = text.lstrip('#')
    return tuple(int(text[i:i + 2], 16) / 255 for i in (0, 2, 4))


def mix(a, b, fraction):
    """Kotlin's mixSrgb: a * fraction + b * (1 - fraction) in gamma-encoded sRGB."""
    return tuple(x * fraction + y * (1 - fraction) for x, y in zip(a, b))


def fields(source, header):
    """The `name = Color(0xFFRRGGBB)` fields of the NagColors built after `header`."""
    start = source.index(header)
    body = source[start:source.index('\n    )', start)]
    return {m.group(1): parse_hex(m.group(2)) for m in re.finditer(r'(\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)', body)}


def tokens(root):
    ui = os.path.join(root, 'application/nagmedar-ui/src/main/java/app/nagmedar/ui/theme')
    palette = open(os.path.join(ui, 'Palette.kt'), encoding='utf-8').read()
    tones = open(os.path.join(ui, 'AccentTones.kt'), encoding='utf-8').read()
    blue = parse_hex(re.search(r'Blue\(Color\(0xFF([0-9A-Fa-f]{6})\)\)', palette).group(1))
    selection = re.search(r'val Blue = fixed\(Accent\.Blue\.base, Color\(0xFF(\w{6})\), Color\(0xFF(\w{6})\), Color\(0xFF(\w{6})\)\)', tones)
    dark_selection, _, on_dark_selection = (parse_hex(selection.group(i)) for i in (1, 2, 3))
    deep = mix(blue, (0, 0, 0), 0.55)
    light_selection = mix(blue, (1, 1, 1), 0.22)
    dark = fields(palette, 'fun dark(tones: AccentTones) = NagColors(')
    light = fields(palette, 'fun light(tones: AccentTones) = NagColors(')
    dark.update(accent=blue, accentText=blue, selection=dark_selection, onSelection=on_dark_selection)
    light.update(accent=deep, accentText=deep, selection=light_selection)
    return light, dark


# CSS filters: the app's icons are black SVGs, tinted with `filter` (brightness(0) first).
def _clamp(v):
    return max(0.0, min(1.0, v))


def _apply(rgb, matrix):
    r, g, b = rgb
    return tuple(_clamp(matrix[i][0] * r + matrix[i][1] * g + matrix[i][2] * b) for i in range(3))


def _sepia(amount):
    a = 1 - min(1, amount)
    return [[0.393 + 0.607 * a, 0.769 - 0.769 * a, 0.189 - 0.189 * a],
            [0.349 - 0.349 * a, 0.686 + 0.314 * a, 0.168 - 0.168 * a],
            [0.272 - 0.272 * a, 0.534 - 0.534 * a, 0.131 + 0.869 * a]]


def _saturate(amount):
    return [[0.213 + 0.787 * amount, 0.715 - 0.715 * amount, 0.072 - 0.072 * amount],
            [0.213 - 0.213 * amount, 0.715 + 0.285 * amount, 0.072 - 0.072 * amount],
            [0.213 - 0.213 * amount, 0.715 - 0.715 * amount, 0.072 + 0.928 * amount]]


def _hue_rotate(degrees):
    import math
    s, c = math.sin(math.radians(degrees)), math.cos(math.radians(degrees))
    return [[0.213 + c * 0.787 - s * 0.213, 0.715 - c * 0.715 - s * 0.715, 0.072 - c * 0.072 + s * 0.928],
            [0.213 - c * 0.213 + s * 0.143, 0.715 + c * 0.285 + s * 0.140, 0.072 - c * 0.072 - s * 0.283],
            [0.213 - c * 0.213 - s * 0.787, 0.715 - c * 0.715 + s * 0.715, 0.072 + c * 0.928 + s * 0.072]]


def _filtered(p):
    """Black through invert, sepia, saturate, hue-rotate, brightness and contrast (percentages, degrees)."""
    invert, sepia, sat, hue, bright, contrast = p
    v = invert / 100
    rgb = (v, v, v)
    rgb = _apply(rgb, _sepia(sepia / 100))
    rgb = _apply(rgb, _saturate(sat / 100))
    rgb = _apply(rgb, _hue_rotate(hue))
    rgb = tuple(_clamp(c * bright / 100) for c in rgb)
    c = contrast / 100
    return tuple(_clamp(x * c + (-(0.5 * c) + 0.5)) for x in rgb)


def tint_filter(target):
    """A filter chain that turns black into [target], found by a seeded random hill climb."""
    rng = random.Random(1304)
    t = tuple(target)
    th, tl, ts = colorsys.rgb_to_hls(*t)

    def loss(p):
        rgb = _filtered(p)
        h, l, s = colorsys.rgb_to_hls(*rgb)
        return sum(abs(a - b) for a, b in zip(rgb, t)) * 100 + abs(h - th) * 20 + abs(s - ts) * 20 + abs(l - tl) * 20

    ranges = [(0, 100), (0, 100), (0, 7500), (0, 360), (0, 200), (0, 200)]
    best, best_loss = None, 1e9
    for _ in range(30):
        p = [rng.uniform(lo, hi if hi < 1000 else 1000) for lo, hi in ranges]
        step = [50, 50, 500, 90, 50, 50]
        current = loss(p)
        for it in range(6000):
            i = rng.randrange(6)
            q = list(p)
            q[i] = max(ranges[i][0], min(ranges[i][1], q[i] + rng.uniform(-1, 1) * step[i]))
            ql = loss(q)
            if ql < current:
                p, current = q, ql
            if it % 1000 == 999:
                step = [s * 0.5 for s in step]
        if current < best_loss:
            best, best_loss = p, current
    i, s, sat, h, b, c = best
    return ('brightness(0) saturate(100%%) invert(%d%%) sepia(%d%%) saturate(%d%%) hue-rotate(%ddeg) brightness(%d%%) contrast(%d%%)'
            % (round(i), round(s), round(sat), round(h), round(b), round(c))), _filtered([round(i), round(s), round(sat), round(h), round(b), round(c)])


def write_scss(root):
    light, dark = tokens(root)
    primary_light, light_error = tint_filter(light['accent'])
    primary_dark, dark_error = tint_filter(dark['accent'])
    muted_light, _ = tint_filter(light['textTertiary'])
    muted_dark, _ = tint_filter(dark['textTertiary'])
    for name, got, want in (('light', light_error, light['accent']), ('dark', dark_error, dark['accent'])):
        miss = max(abs(a - b) for a, b in zip(got, want)) * 255
        if miss > 4:
            raise SystemExit('the %s accent filter is %d off' % (name, miss))
    h = hex_of
    lines = [
        '// Generated by buildsystem/remoteaccess/gen_brand.py from NagTheme (Palette.kt, AccentTones.kt); do not edit by hand.',
        '// Replaces the upstream colors.scss: the names stay, the values are Nağmedar\'s (S-1304).',
        '',
        '// Neutrals (light, then dark)',
        '$gray-100: %s;' % h(light['background']),
        '$gray-200: %s;' % h(light['hover']),
        '$gray-300: %s;' % h(light['border']),
        '$gray-400: %s;' % h(light['borderStrong']),
        '$gray-500: %s;' % h(dark['textTertiary']),
        '$gray-600: %s;' % h(light['textTertiary']),
        '$gray-700: %s;' % h(light['textSecondary']),
        '$gray-800: %s;' % h(dark['surfaceRaised']),
        '$gray-900: %s;' % h(dark['surface']),
        '',
        '// Accent: the dark themes use the light tone, the light theme the deep one',
        '$primary-color: %s;' % h(dark['accent']),
        '$primary-color-dark: %s;' % h(light['accent']),
        '$on-primary: %s;' % h(light['onAccent']),
        '$on-primary-dark: %s;' % h(dark['onAccent']),
        '$selection: %s;' % h(light['selection']),
        '$selection-dark: %s;' % h(dark['selection']),
        '$on-selection-dark: %s;' % h(dark['onSelection']),
        '',
        '$background: %s;' % h(light['background']),
        '$surface: %s;' % h(light['surface']),
        '$light-gray: %s;' % h(light['hover']),
        '$light-gray-dark: %s;' % h(dark['hover']),
        '$lighter-gray: %s;' % h(light['background']),
        '$gray-border: %s;' % h(light['border']),
        '$gray-border-dark: %s;' % h(dark['border']),
        '$gray-text: %s;' % h(light['textSecondary']),
        '$gray-text-dark: %s;' % h(dark['textTertiary']),
        '$hover-gray: %s;' % h(light['hover']),
        '$dark-background: %s;' % h(dark['hover']),
        '$darker-background: %s;' % h(dark['chrome']),
        '$body-dark: %s;' % h(dark['background']),
        '$text-color: %s;' % h(light['text']),
        '$text-color-dark: %s;' % h(dark['text']),
        '$progress-background: rgba(18, 22, 27, 0.15);',
        '$progress-background-dark: rgba(230, 233, 237, 0.25);',
        '$gray-overlay: %s;' % h(light['hover']),
        '',
        '// The icons are black SVGs: filters that tint them to the accent and to the muted text colour',
        '$img-tint-active: %s;' % primary_light,
        '$img-tint-active-dark: %s;' % primary_dark,
        '$img-tint: %s;' % muted_light,
        '$img-tint-dark: %s;' % muted_dark,
        '',
        '$rgb-primary: %d, %d, %d;' % tuple(round(c * 255) for c in light['accent']),
        '$rgb-primary-dark: %d, %d, %d;' % tuple(round(c * 255) for c in dark['accent']),
        '',
    ]
    path = os.path.join(HERE, 'assets/src/scss/colors.scss')
    with open(path, 'w', encoding='utf-8') as fh:
        fh.write('\n'.join(lines))


def svg(name, viewbox=None):
    with open(os.path.join(BRANDING, name), encoding='utf-8') as fh:
        text = fh.read()
    text = re.sub(r'(<svg[^>]*?) width="\d+" height="\d+"', r'\1', text, count=1)
    if viewbox:
        text = re.sub(r'viewBox="[^"]*"', 'viewBox="%s"' % viewbox, text, count=1)
    return text


def render_icons():
    from PIL import Image
    from playwright.sync_api import sync_playwright
    out = os.path.join(HERE, 'assets/public')
    mark = svg('nagmedar-icon-foreground.svg', MARK_VIEWBOX)
    # the mark alone, for the header
    with open(os.path.join(out, 'logo.svg'), 'w', encoding='utf-8') as fh:
        fh.write(mark.replace('<svg ', '<svg width="56" height="56" ', 1))
    # the tab icon: the mark on the dark tile, rounded
    with open(os.path.join(out, 'favicon.svg'), 'w', encoding='utf-8') as fh:
        fh.write('<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="72" height="72">'
                 '<title>Nağmedar</title><rect x="18" y="18" width="72" height="72" rx="16" fill="%s"/>%s</svg>'
                 % (BG, re.sub(r'^<svg[^>]*>|</svg>$', '', svg('nagmedar-icon-foreground.svg', '18 18 72 72').strip())))
    tile = svg('nagmedar-icon-foreground.svg', '18 18 72 72')
    sizes = {'favicon-16x16.png': (16, '22%'), 'favicon-32x32.png': (32, '22%'), 'apple-touch-icon.png': (180, '0'),
             'android-chrome-192x192.png': (192, '0'), 'android-chrome-512x512.png': (512, '0')}
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page()
        for name, (size, radius) in sizes.items():
            page.set_viewport_size({'width': size, 'height': size})
            page.set_content('<html><body style="margin:0;background:transparent"><div style="width:%dpx;height:%dpx;background:%s;'
                             'border-radius:%s;overflow:hidden">%s</div><style>svg{width:%dpx;height:%dpx;display:block}</style></body></html>'
                             % (size, size, BG, radius, tile, size, size))
            page.screenshot(path=os.path.join(out, name), omit_background=True)
        browser.close()
    icon32 = Image.open(os.path.join(out, 'favicon-32x32.png'))
    icon32.save(os.path.join(out, 'favicon.ico'), sizes=[(16, 16), (32, 32)])
    for name in ('favicon-16x16.png', 'favicon-32x32.png', 'apple-touch-icon.png', 'android-chrome-192x192.png', 'android-chrome-512x512.png'):
        path = os.path.join(out, name)
        Image.open(path).convert('RGBA').save(path, optimize=True)


def convert_fonts(root):
    from fontTools.ttLib import TTFont
    source = os.path.join(root, 'application/nagmedar-ui/src/main/res/font')
    out = os.path.join(HERE, 'assets/src/assets/fonts')
    for weight in ('regular', 'medium', 'semibold'):
        font = TTFont(os.path.join(source, 'ibm_plex_sans_%s.ttf' % weight))
        font.flavor = 'woff2'
        font.save(os.path.join(out, 'ibm-plex-sans-%s.woff2' % weight))


if __name__ == '__main__':
    repo = os.path.abspath(sys.argv[1])
    write_scss(repo)
    render_icons()
    convert_fonts(repo)
