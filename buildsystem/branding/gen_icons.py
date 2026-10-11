#!/usr/bin/env python3
"""Generate application/nagmedar-ui/.../components/NagIcons.kt from the mockup icons.

The mockups draw every icon on a 16 x 16 grid with 1.6 px round-capped strokes; some parts
are filled with currentColor. Rects and circles are converted to path data, then each icon
becomes a lazily built Compose ImageVector (tinted by Icon()).

Usage: gen_icons.py <output .kt file>
"""
import re
import sys

ICONS = {
    'Home': '<path d="M3 7.5 8 3l5 4.5V13H9.5V9.5h-3V13H3z"/>',
    'Movies': '<rect x="2.5" y="3" width="11" height="10" rx="1.5"/><path d="M5.5 3v10M10.5 3v10M2.5 6.5h3M2.5 9.5h3M10.5 6.5h3M10.5 9.5h3"/>',
    'Series': '<rect x="2" y="3.5" width="12" height="8" rx="1.5"/><path d="M6 14h4"/>',
    'Music': '<path d="M6 12V4l7-1.5v8"/><circle cx="4.5" cy="12" r="1.5"/><circle cx="11.5" cy="10.5" r="1.5"/>',
    'Podcasts': '<rect x="6" y="2" width="4" height="7.5" rx="2"/><path d="M3.5 8a4.5 4.5 0 0 0 9 0M8 12.5v2"/>',
    'Radio': '<rect x="2" y="5.5" width="12" height="8" rx="1.5"/><path d="M4.5 5.5 11 2.5M4.5 8.5h3M4.5 10.5h3"/><circle cx="10.5" cy="9.5" r="1.8"/>',
    'Device': '<rect x="3" y="2" width="10" height="12" rx="1.5"/><path d="M7 12h2"/>',
    'ExternalDrive': '<rect x="2" y="9" width="12" height="4.5" rx="1.2"/><path d="M3.5 9 5 3.5h6L12.5 9"/>',
    'Network': '<rect x="6" y="2" width="4" height="3.5" rx=".8"/><rect x="2" y="10.5" width="4" height="3.5" rx=".8"/><rect x="10" y="10.5" width="4" height="3.5" rx=".8"/><path d="M8 5.5V8M4 10.5V8h8v2.5"/>',
    'Streams': '<path d="M6.5 9.5 9.5 6.5M7 4.5l1.2-1.2a2.6 2.6 0 0 1 3.7 3.7l-1.2 1.2M9 11.5l-1.2 1.2a2.6 2.6 0 0 1-3.7-3.7l1.2-1.2"/>',
    'TvLab': '<path d="M6 2.5h4M6.8 2.5v4L3 13a1 1 0 0 0 .9 1.5h8.2A1 1 0 0 0 13 13L9.2 6.5v-4M4.6 10.5h6.8"/>',
    'Convert': '<path d="M3 5.5h9l-2.5-2.5M13 10.5H4l2.5 2.5"/>',
    'Downloads': '<path d="M8 2.5v8M4.8 7.5 8 10.7l3.2-3.2M3 13.5h10"/>',
    'Settings': '<circle cx="8" cy="8" r="2.2"/><path d="M8 1.8v1.7M8 12.5v1.7M1.8 8h1.7M12.5 8h1.7M3.6 3.6l1.2 1.2M11.2 11.2l1.2 1.2M3.6 12.4l1.2-1.2M11.2 4.8l1.2-1.2"/>',
    'Plus': '<path d="M8 3v10M3 8h10"/>',
    'Grid': '<rect x="2.5" y="2.5" width="4.5" height="4.5" rx="1"/><rect x="9" y="2.5" width="4.5" height="4.5" rx="1"/><rect x="2.5" y="9" width="4.5" height="4.5" rx="1"/><rect x="9" y="9" width="4.5" height="4.5" rx="1"/>',
    'ListView': '<path d="M5.5 4h8M5.5 8h8M5.5 12h8M2.5 4h.5M2.5 8h.5M2.5 12h.5"/>',
    'ChevronDown': '<path d="m4 6 4 4 4-4"/>',
    'ChevronUp': '<path d="m4 10 4-4 4 4"/>',
    'ChevronLeft': '<path d="m10 4-4 4 4 4"/>',
    'ChevronRight': '<path d="m6 4 4 4-4 4"/>',
    'Check': '<path d="m3.5 8.5 3 3 6-7"/>',
    'Play': '<path d="M5 3.2v9.6L13 8z" style="fill:currentColor"/>',
    'Resume': '<path d="M3 8a5 5 0 1 0 1.6-3.7M3 2.5v2.8h2.8"/>',
    'Forward': '<path d="M13 8a5 5 0 1 1-1.6-3.7M13 2.5v2.8h-2.8"/>',
    'OpenWindow': '<rect x="2.5" y="4.5" width="9" height="9" rx="1.2"/><path d="M6.5 2.5h7v7M13.5 2.5 8 8"/>',
    'Subtitle': '<rect x="2" y="3.5" width="12" height="9" rx="1.5"/><path d="M4.5 8.5h3M9 8.5h2.5M4.5 10.5h5"/>',
    'Info': '<circle cx="8" cy="8" r="5.5"/><path d="M8 7.5V11M8 5.2v.3"/>',
    'PlaylistAdd': '<path d="M2.5 4h8M2.5 7.5h8M2.5 11h5M11.5 9.5v4.5M9.25 11.75h4.5"/>',
    'Folder': '<path d="M2.5 4.5a1 1 0 0 1 1-1h3L8 5h4.5a1 1 0 0 1 1 1v6.5a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1z"/>',
    'Delete': '<path d="M3 4.5h10M6.5 4.5V3h3v1.5M4.5 4.5l.6 8.5a1 1 0 0 0 1 .9h3.8a1 1 0 0 0 1-.9l.6-8.5"/>',
    'Close': '<path d="m4 4 8 8M12 4l-8 8"/>',
    'MoreHorizontal': '<circle cx="3.5" cy="8" r="1.3" style="fill:currentColor"/><circle cx="8" cy="8" r="1.3" style="fill:currentColor"/><circle cx="12.5" cy="8" r="1.3" style="fill:currentColor"/>',
    'MoreVertical': '<circle cx="8" cy="3.5" r="1.4" style="fill:currentColor"/><circle cx="8" cy="8" r="1.4" style="fill:currentColor"/><circle cx="8" cy="12.5" r="1.4" style="fill:currentColor"/>',
    'Shuffle': '<path d="M2.5 4.5h2.2c1.4 0 2.3.7 3 1.8l1.6 3.4c.7 1.1 1.6 1.8 3 1.8h1.2M11.8 9.6l1.7 1.9-1.7 1.9M2.5 11.5h2.2c.9 0 1.6-.3 2.2-.8M9.3 5.3c.6-.5 1.3-.8 2.2-.8h2M11.8 2.6l1.7 1.9-1.7 1.9"/>',
    'Previous': '<path d="M4 3.5v9"/><path d="M12.5 3.5 6 8l6.5 4.5z" style="fill:currentColor;stroke:none"/>',
    'Next': '<path d="M12 3.5v9"/><path d="M3.5 3.5 10 8l-6.5 4.5z" style="fill:currentColor;stroke:none"/>',
    'Pause': '<path d="M5.5 3.5v9M10.5 3.5v9"/>',
    'Repeat': '<path d="M3 7V6a2 2 0 0 1 2-2h7.5M10.5 2l2 2-2 2M13 9v1a2 2 0 0 1-2 2H3.5M5.5 14l-2-2 2-2"/>',
    'Queue': '<path d="M2.5 4h11M2.5 8h11M2.5 12h7"/>',
    'Artists': '<circle cx="8" cy="5.5" r="2.5"/><path d="M3 13.5c.8-2.4 2.7-3.6 5-3.6s4.2 1.2 5 3.6"/>',
    'Albums': '<circle cx="8" cy="8" r="5.5"/><circle cx="8" cy="8" r="1.6"/>',
    'History': '<path d="M8 4.5V8l2.5 1.5"/><circle cx="8" cy="8" r="5.5"/>',
    'Equalizer': '<path d="M4 2.5v11M8 2.5v11M12 2.5v11"/><path d="M2.5 10h3M6.5 5.5h3M10.5 8.5h3"/>',
    'SmartList': '<path d="M3 4.5h10M5 8h6M7 11.5h2"/>',
    'FramePrevious': '<rect x="7.5" y="3.5" width="6" height="9" rx="1"/><path d="M5 5 2.5 8 5 11"/>',
    'FrameNext': '<rect x="2.5" y="3.5" width="6" height="9" rx="1"/><path d="M11 5l2.5 3L11 11"/>',
    'AudioTrack': '<path d="M3 10V6M5.5 12V4M8 11V5M10.5 13V3M13 10V6"/>',
    'Screenshot': '<path d="M2.5 5.5a1 1 0 0 1 1-1h1.8L6.5 3h3l1.2 1.5h1.8a1 1 0 0 1 1 1v6.5a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1z"/><circle cx="8" cy="8.5" r="2.2"/>',
    'Effects': '<path d="M3 4.5h10M3 8h10M3 11.5h10"/><circle cx="6" cy="4.5" r="1.4"/><circle cx="10.5" cy="8" r="1.4"/><circle cx="5" cy="11.5" r="1.4"/>',
    'PictureInPicture': '<rect x="2" y="3" width="12" height="10" rx="1.5"/><rect x="8" y="8" width="4.5" height="3.5" rx=".6" style="fill:currentColor;stroke:none"/>',
    'Fullscreen': '<path d="M2.5 6V2.5H6M10 2.5h3.5V6M13.5 10v3.5H10M6 13.5H2.5V10"/>',
    'Search': '<circle cx="7" cy="7" r="4.5"/><path d="m10.5 10.5 3 3"/>',
    'Sort': '<path d="M4.5 3v10M2.5 11l2 2 2-2M11.5 13V3M9.5 5l2-2 2 2"/>',
    'Lyrics': '<path d="M2.5 4h7M2.5 7.5h11M2.5 11h8"/>',
    'Volume': '<path d="M2.5 6h2.5L8.5 3v10L5 10H2.5z"/><path d="M10.8 5.6a3.4 3.4 0 0 1 0 4.8M12.6 3.8a6 6 0 0 1 0 8.4"/>',
    'Menu': '<path d="M3 4.5h10M3 8h10M3 11.5h10"/>',
    'Waveform': '<path d="M2.5 7v2M5 5v6M7.5 3v10M10 5.5v5M12.5 7v2"/>',
    'Keyboard': '<rect x="1.5" y="4" width="13" height="8" rx="1.5"/><path d="M4 6.5h.5M6.5 6.5h.5M9 6.5h.5M11.5 6.5h.5M5 9.5h6"/>',
    'SidebarToggle': '<rect x="2" y="2.5" width="12" height="11" rx="1.5"/><path d="M6 2.5v11"/>',
    # S-308 states board
    'FolderAdd': '<path d="M2.5 4.5a1 1 0 0 1 1-1h3L8 5h4.5a1 1 0 0 1 1 1v6.5a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1zM8 7.5v4M6 9.5h4"/>',
    'Shield': '<path d="M8 1.8 13 4v4c0 3-2.2 5.2-5 6.2-2.8-1-5-3.2-5-6.2V4zM6 8l1.5 1.5L10.5 6.5"/>',
    'ErrorCircle': '<circle cx="8" cy="8" r="5.5"/><path d="M6 6l4 4M10 6l-4 4"/>',
    'SuccessCircle': '<circle cx="8" cy="8" r="5.5"/><path d="M5.5 8.2l1.8 1.8 3.3-3.6"/>',
    'Warning': '<path d="M8 2.5 14 13H2z"/><path d="M8 6.5v3M8 11.2v.3"/>',
    'DriveRemoved': '<path d="M2 9h12v4.5H2zM3.5 9 5 3.5h6L12.5 9M5 11.2h.01"/>',
    'DiskFull': '<path d="M2 9h12v4.5H2zM3.5 9 5 3.5h6L12.5 9M8 5.5v2"/>',
    'NetworkOff': '<path d="M2 6.5a9 9 0 0 1 12 0M4.2 9a5.8 5.8 0 0 1 7.6 0M8 12.5h.01M3 3l10 10"/>',
    'SearchOff': '<circle cx="7" cy="7" r="4.5"/><path d="m10.5 10.5 3 3M5.5 7h3"/>',
    'Lock': '<rect x="3.5" y="7" width="9" height="6.5" rx="1.2"/><path d="M5.5 7V5a2.5 2.5 0 0 1 5 0v2"/>',
}

# Icons that point along the reading direction: drawn mirrored in right-to-left layouts.
# Media transport icons (Play, Next, Resume, …) keep their direction in every language.
MIRRORED = {'ChevronLeft', 'ChevronRight'}


def num(v):
    s = ('%.3f' % v).rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s


def rect_path(x, y, w, h, rx):
    if rx <= 0:
        return 'M%s,%sh%sv%sh%sz' % (num(x), num(y), num(w), num(h), num(-w))
    return ('M%s,%sH%sA%s,%s 0 0 1 %s,%sV%sA%s,%s 0 0 1 %s,%sH%sA%s,%s 0 0 1 %s,%sV%sA%s,%s 0 0 1 %s,%sZ' % (
        num(x + rx), num(y), num(x + w - rx), num(rx), num(rx), num(x + w), num(y + rx), num(y + h - rx), num(rx), num(rx),
        num(x + w - rx), num(y + h), num(x + rx), num(rx), num(rx), num(x), num(y + h - rx), num(y + rx), num(rx), num(rx),
        num(x + rx), num(y)))


def circle_path(cx, cy, r):
    return 'M%s,%sa%s,%s 0 1 0 %s,0a%s,%s 0 1 0 %s,0Z' % (num(cx - r), num(cy), num(r), num(r), num(2 * r), num(r), num(r), num(-2 * r))


def parts(svg):
    out = []
    for m in re.finditer(r'<(path|rect|circle)([^>]*)/>', svg):
        tag, attrs = m.group(1), m.group(2)
        a = dict(re.findall(r'([a-z-]+)="([^"]*)"', attrs))
        filled = 'fill:currentColor' in a.get('style', '')
        if tag == 'path':
            d = a['d']
        elif tag == 'rect':
            d = rect_path(float(a['x']), float(a['y']), float(a['width']), float(a['height']), float(a.get('rx', '0')))
        else:
            d = circle_path(float(a['cx']), float(a['cy']), float(a['r']))
        out.append((d, filled))
    return out


HEADER = '''package app.nagmedar.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Generated from the mockup icons (16 x 16 grid, 1.6 stroke, round caps) by
// buildsystem/branding/gen_icons.py; edit the generator, not this file.
object NagIcons {'''

FOOTER = '''}

private class IconPart(val d: String, val filled: Boolean)

private fun stroke(d: String) = IconPart(d, filled = false)

private fun fill(d: String) = IconPart(d, filled = true)

private fun icon(name: String, vararg parts: IconPart, mirrored: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f,
        autoMirror = mirrored,
    )
        .apply {
            parts.forEach { part ->
                if (part.filled) {
                    addPath(pathData = addPathNodes(part.d), fill = SolidColor(Color.Black))
                } else {
                    addPath(
                        pathData = addPathNodes(part.d),
                        fill = null,
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 1.6f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
        }
        .build()
'''


def main(out):
    lines = [HEADER]
    for name, svg in ICONS.items():
        args = ', '.join('%s("%s")' % ('fill' if filled else 'stroke', d) for d, filled in parts(svg))
        if name in MIRRORED:
            args += ', mirrored = true'
        lines.append('    val %s: ImageVector by lazy { icon("%s", %s) }' % (name, name, args))
    lines.append('')
    lines.append('    /** Every icon in the generator\'s order: the icon board and its tests. */')
    lines.append('    val all: List<ImageVector> get() = listOf(')
    names = list(ICONS)
    for i in range(0, len(names), 8):
        lines.append('        %s,' % ', '.join(names[i:i + 8]))
    lines.append('    )')
    with open(out, 'w', encoding='utf-8') as fh:
        fh.write('\n'.join(lines) + '\n' + FOOTER)
    print('wrote %d icons to %s' % (len(ICONS), out))


if __name__ == '__main__':
    main(sys.argv[1])
