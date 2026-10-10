#!/usr/bin/env python3
"""Render the bitmap branding resources from the SVG sources in svg/.

VectorDrawables (gen_mark_paths.py) cover the adaptive icon and in-app logos;
these PNGs are for the places that need bitmaps:

  mipmap-*/ic_launcher.png      legacy launcher icon (API < 26), 48 dp
  drawable-*/icon.png           mark on transparent background (logo, TV badge)
  drawable-*/ic_stat_nagmedar   status bar icon, 24 dp, white on transparent

Requires Python Playwright with Chromium.
Usage: render_pngs.py <repo_root>
"""
import os
import re
import sys

from playwright.sync_api import sync_playwright

HERE = os.path.dirname(os.path.abspath(__file__))
BG = '#12161B'
# The mark sits at (54.5, 54) in the 108-unit icon grid; a 56-unit box frames it.
MARK_VIEWBOX = '26.5 26 56 56'
# Launchers show the central 72 x 72 of the 108-unit adaptive canvas; legacy icons match that.
LEGACY_VIEWBOX = '18 18 72 72'
DENSITIES = [('mdpi', 1.0), ('hdpi', 1.5), ('xhdpi', 2.0), ('xxhdpi', 3.0), ('xxxhdpi', 4.0)]
# icon.png keeps the pixel sizes of the upstream file it replaces
ICON_SIZES = [('ldpi', 64), ('mdpi', 96), ('hdpi', 144), ('xhdpi', 192), ('xxhdpi', 288), ('xxxhdpi', 384)]


def svg(name, viewbox=None):
    with open(os.path.join(HERE, 'svg', name), encoding='utf-8') as fh:
        text = fh.read()
    text = re.sub(r'(<svg[^>]*?) width="\d+" height="\d+"', r'\1', text, count=1)
    if viewbox:
        text = re.sub(r'viewBox="[^"]*"', 'viewBox="%s"' % viewbox, text, count=1)
    return text


def shoot(page, path, size, inner, bg='transparent', radius='0'):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    page.set_viewport_size({'width': size, 'height': size})
    page.set_content('<html><body style="margin:0;background:transparent">'
                     '<div style="width:%dpx;height:%dpx;background:%s;border-radius:%s;overflow:hidden">%s</div>'
                     '<style>svg{width:%dpx;height:%dpx;display:block}</style></body></html>'
                     % (size, size, bg, radius, inner, size, size))
    page.screenshot(path=path, omit_background=True)


def main(root):
    app_res = os.path.join(root, 'application/vlc-android/res')
    res = os.path.join(root, 'application/resources/src/main/res')
    legacy, mark = svg('nagmedar-icon-foreground.svg', LEGACY_VIEWBOX), svg('nagmedar-icon-foreground.svg', MARK_VIEWBOX)
    notification = svg('nagmedar-notification.svg')
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page()
        for density, scale in DENSITIES:
            shoot(page, '%s/mipmap-%s/ic_launcher.png' % (app_res, density), round(48 * scale), legacy, BG, '22%')
            shoot(page, '%s/drawable-%s/ic_stat_nagmedar.png' % (res, density), round(24 * scale), notification)
        for density, size in ICON_SIZES:
            shoot(page, '%s/drawable-%s/icon.png' % (res, density), size, mark)
        browser.close()


if __name__ == '__main__':
    main(sys.argv[1])
