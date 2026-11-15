#!/usr/bin/env python3
"""Make the app's font files (application/nagmedar-ui/src/main/res/font) from Google Fonts' variable fonts.

Nağmedar 1.1.0 (Ebru): Bricolage Grotesque for the interface and Bodoni Moda's italic for the titles, both SIL OFL 1.1
(the licences beside this script). The app ships static instances, cut with fontTools' instancer, so nothing depends
on a renderer's support for font variations: Bricolage at the 14 pt optical size and full width in five weights,
Bodoni's italic once for each role at the optical size it is read at (the masthead at 48 pt, the display lines at 28,
the headlines at 20 and a weight heavier).

Usage: make_fonts.py <res/font directory>   (needs fontTools: pip install fonttools)
"""
import os
import sys
import urllib.request

from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

SOURCES = {
    'bricolage': 'https://raw.githubusercontent.com/google/fonts/main/ofl/bricolagegrotesque/BricolageGrotesque%5Bopsz,wdth,wght%5D.ttf',
    'bodoni_italic': 'https://raw.githubusercontent.com/google/fonts/main/ofl/bodonimoda/BodoniModa-Italic%5Bopsz,wght%5D.ttf',
}

INSTANCES = [
    ('bricolage', {'wght': w, 'opsz': 14, 'wdth': 100}, 'bricolage_grotesque_%d.ttf' % w) for w in (400, 500, 600, 650, 700)
] + [
    ('bodoni_italic', {'wght': 500, 'opsz': 48}, 'bodoni_moda_italic_masthead.ttf'),
    ('bodoni_italic', {'wght': 500, 'opsz': 28}, 'bodoni_moda_italic_display.ttf'),
    ('bodoni_italic', {'wght': 600, 'opsz': 20}, 'bodoni_moda_italic_headline.ttf'),
]


def main(out):
    os.makedirs(out, exist_ok=True)
    fetched = {}
    for key, url in SOURCES.items():
        path = os.path.join(out, '.%s.ttf' % key)
        urllib.request.urlretrieve(url, path)
        fetched[key] = path
    try:
        for key, axes, name in INSTANCES:
            font = instancer.instantiateVariableFont(TTFont(fetched[key]), axes)
            font.save(os.path.join(out, name))
            print(name)
    finally:
        for path in fetched.values():
            os.remove(path)


if __name__ == '__main__':
    main(sys.argv[1])
