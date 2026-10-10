#!/usr/bin/env python3
"""Generate VectorDrawable path data for the Nağmedar mark.

The design source (svg/nagmedar-icon-foreground.svg) uses an SVG mask: a play
triangle stroked with round joins, cut by a wave line. VectorDrawable has no
masks, so this script computes the same shapes explicitly:

  TRIANGLE  rounded triangle outline (clip path)
  TOP       area above the cut, used for the blue half
  BOTTOM    area below the cut, used for the coral half

Usage: gen_mark_paths.py <out_dir>
Writes the launcher foreground/monochrome layers and in-app logo drawables.
"""
import math
import os
import sys

R_JOIN = 4.0        # triangle stroke-width 8 -> radius 4
HALF_CUT = 1.8      # wave stroke-width 3.6
TRI = [(33.0, 33.5), (33.0, 74.5), (76.0, 54.0)]
LINE = ((22.0, 54.0), (33.0, 54.0))
CUBICS = [((33.0, 54.0), (38.17, 47.33), (43.33, 47.33), (48.5, 54.0)),
          ((48.5, 54.0), (53.67, 60.67), (58.83, 60.67), (64.0, 54.0))]
BLUE, CORAL, BG = '#FF8AB0FF', '#FFFF7A6E', '#FF12161B'


def f(v):
    s = ('%.3f' % v).rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s


def norm(x, y):
    d = math.hypot(x, y)
    return x / d, y / d


def rounded_triangle():
    pts = TRI
    area = sum(pts[i][0] * pts[(i + 1) % 3][1] - pts[(i + 1) % 3][0] * pts[i][1] for i in range(3))
    cw = area > 0  # y-down screen coordinates
    normals = []
    for i in range(3):
        (x1, y1), (x2, y2) = pts[i], pts[(i + 1) % 3]
        tx, ty = norm(x2 - x1, y2 - y1)
        normals.append((ty, -tx) if cw else (-ty, tx))
    out = []
    for i in range(3):
        nx, ny = normals[i]
        (x1, y1), (x2, y2) = pts[i], pts[(i + 1) % 3]
        a = (x1 + R_JOIN * nx, y1 + R_JOIN * ny)
        b = (x2 + R_JOIN * nx, y2 + R_JOIN * ny)
        nnx, nny = normals[(i + 1) % 3]
        c = (x2 + R_JOIN * nnx, y2 + R_JOIN * nny)
        if i == 0:
            out.append('M%s,%s' % (f(a[0]), f(a[1])))
        out.append('L%s,%s' % (f(b[0]), f(b[1])))
        out.append('A%s,%s 0 0,%d %s,%s' % (f(R_JOIN), f(R_JOIN), 1 if cw else 0, f(c[0]), f(c[1])))
    out.append('Z')
    return ''.join(out)


def bez(p0, p1, p2, p3, t):
    u = 1 - t
    x = u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0]
    y = u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1]
    dx = 3 * u * u * (p1[0] - p0[0]) + 6 * u * t * (p2[0] - p1[0]) + 3 * t * t * (p3[0] - p2[0])
    dy = 3 * u * u * (p1[1] - p0[1]) + 6 * u * t * (p2[1] - p1[1]) + 3 * t * t * (p3[1] - p2[1])
    return (x, y), norm(dx, dy)


def curve_samples(steps=48):
    pts = []
    for ci, c in enumerate(CUBICS):
        for k in range(steps + 1):
            if ci > 0 and k == 0:
                continue
            pts.append(bez(*c, k / steps))
    return pts


def up(p, t, r=HALF_CUT):   # side towards -y for an eastward tangent
    return (p[0] + r * t[1], p[1] - r * t[0])


def down(p, t, r=HALF_CUT):
    return (p[0] - r * t[1], p[1] + r * t[0])


def line_y_cross(poly, y):
    """First crossing of polyline with horizontal y; returns (point, index after)."""
    for i in range(len(poly) - 1):
        (x1, y1), (x2, y2) = poly[i], poly[i + 1]
        if (y1 - y) * (y2 - y) <= 0 and y1 != y2:
            s = (y - y1) / (y2 - y1)
            return (x1 + s * (x2 - x1), y), i + 1
    raise ValueError('no crossing')


def cut_sides():
    samples = curve_samples()
    upper_curve = [up(p, t) for p, t in samples]
    lower_curve = [down(p, t) for p, t in samples]
    y_up, y_dn = LINE[0][1] - HALF_CUT, LINE[0][1] + HALF_CUT
    # inner (upper) join: the straight offset meets the curve offset
    join_up, k = line_y_cross(upper_curve, y_up)
    upper = [(LINE[0][0], y_up), join_up] + upper_curve[k:]
    # outer (lower) join: miter point where the extensions meet
    (p0, t0) = samples[0]
    start_dn = lower_curve[0]
    s = (y_dn - start_dn[1]) / t0[1]          # walk backwards along -t0
    miter = (start_dn[0] + s * t0[0], y_dn)
    lower = [(LINE[0][0], y_dn), miter] + lower_curve
    end_p, end_t = samples[-1]
    return upper, lower, end_p, end_t


def arc_points(center, a0, a1, steps=16):
    return [(center[0] + HALF_CUT * math.cos(a0 + (a1 - a0) * k / steps),
             center[1] + HALF_CUT * math.sin(a0 + (a1 - a0) * k / steps)) for k in range(steps + 1)]


def poly_path(points):
    return 'M' + 'L'.join('%s,%s' % (f(x), f(y)) for x, y in points) + 'Z'


def halves():
    upper, lower, end_p, end_t = cut_sides()
    a_up = math.atan2(-end_t[0], end_t[1])      # direction of the upper offset
    a_dn = math.atan2(end_t[0], -end_t[1])
    a_flat = 0.0                                 # cap meets the straight boundary y=54 at angle 0
    # top: from the right edge along y=54 to the cap, around the cap to the upper offset,
    # back along the upper side, then out to the left edge
    cap_top = arc_points(end_p, a_flat, a_up if a_up < a_flat else a_up - 2 * math.pi)
    top = [(108, 0), (108, 54)] + cap_top + list(reversed(upper)) + [(0, upper[0][1]), (0, 0)]
    cap_bottom = arc_points(end_p, a_flat, a_dn)
    bottom = [(108, 108), (108, 54)] + cap_bottom + list(reversed(lower)) + [(0, lower[0][1]), (0, 108)]
    return poly_path(top), poly_path(bottom)


def vector(width_dp, viewport, body, translate=(0, 0), height_dp=None, viewport_h=None, alpha=None):
    tx, ty = translate
    height_dp = width_dp if height_dp is None else height_dp
    viewport_h = viewport if viewport_h is None else viewport_h
    g_open = '    <group android:translateX="%s" android:translateY="%s">\n' % (f(tx), f(ty)) if translate != (0, 0) else ''
    g_close = '    </group>\n' if translate != (0, 0) else ''
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generated by buildsystem/branding/gen_mark_paths.py; do not edit by hand. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="%sdp"\n    android:height="%sdp"\n'
            '    android:viewportWidth="%s"\n    android:viewportHeight="%s"%s>\n%s%s%s</vector>\n'
            % (f(width_dp), f(height_dp), f(viewport), f(viewport_h),
               '' if alpha is None else '\n    android:alpha="%s"' % f(alpha), g_open, body, g_close))


# Upstream drawables that showed the VLC cone, replaced by the mark at their original size:
# name -> (width dp, height dp, style, alpha). 'mono' keeps the single light fill that the
# widgets tint at runtime; the renderer background keeps its translucency.
CONE_DRAWABLES = {'ic_about_logo': (112, 112, 'color', None), 'ic_cone_o': (46, 46, 'color', None),
                  'ic_default_cone': (96, 96, 'color', None), 'ic_icon': (54, 54, 'color', None),
                  'ic_icon_vlc4': (54, 54, 'color', None), 'ic_widget_icon': (48, 48, 'mono', None),
                  'ic_renderer_background_cone': (510, 600, 'color', 0.85)}
WIDGET_FILL = '#FFFAFAFA'
MARK_CENTER = (54.5, 54.0)
MARK_BOX = 56.0


def mark_vector(w_dp, h_dp, body, alpha=None):
    vw = MARK_BOX if w_dp <= h_dp else MARK_BOX * w_dp / h_dp
    vh = MARK_BOX if h_dp <= w_dp else MARK_BOX * h_dp / w_dp
    translate = (vw / 2 - MARK_CENTER[0], vh / 2 - MARK_CENTER[1])
    return vector(w_dp, vw, body, translate, h_dp, vh, alpha)


def clipped(paths, indent='        '):
    tri = rounded_triangle()
    inner = ''.join('%s<path android:fillColor="%s" android:pathData="%s"/>\n' % (indent + '    ', c, d) for c, d in paths)
    return '%s<group>\n%s    <clip-path android:pathData="%s"/>\n%s%s</group>\n' % (indent, indent, tri, inner, indent)


def main(out):
    os.makedirs(out, exist_ok=True)
    top, bottom = halves()
    files = {
        # adaptive icon layers, 108 x 108 dp
        'ic_launcher_foreground.xml': vector(108, 108, clipped([(BLUE, top), (CORAL, bottom)], '    ')),
        'ic_launcher_monochrome.xml': vector(108, 108, clipped([('#FFFFFFFF', top + bottom)], '    ')),
        # in-app mark, cropped around the triangle (x 29..80, y 29.5..78.5)
        'ic_nagmedar_mark.xml': mark_vector(48, 48, clipped([(BLUE, top), (CORAL, bottom)], '        ')),
        'ic_nagmedar_mark_mono.xml': mark_vector(48, 48, clipped([('#FFFFFFFF', top + bottom)], '        ')),
        'ic_launcher_background.xml': ('<?xml version="1.0" encoding="utf-8"?>\n'
                                       '<!-- Generated by buildsystem/branding/gen_mark_paths.py; do not edit by hand. -->\n'
                                       '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
                                       '    android:width="108dp"\n    android:height="108dp"\n'
                                       '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
                                       '    <path android:fillColor="%s" android:pathData="M0,0h108v108h-108z"/>\n</vector>\n' % BG),
    }
    for name, (w, h, style, alpha) in CONE_DRAWABLES.items():
        paths = [(BLUE, top), (CORAL, bottom)] if style == 'color' else [(WIDGET_FILL, top + bottom)]
        files[name + '.xml'] = mark_vector(w, h, clipped(paths, '        '), alpha)
    for name, text in files.items():
        with open(os.path.join(out, name), 'w', encoding='utf-8') as fh:
            fh.write(text)
    print('wrote', ', '.join(sorted(files)))


if __name__ == '__main__':
    main(sys.argv[1])
