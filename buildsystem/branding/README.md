# Nağmedar branding

Sources and generators for the Nağmedar name and icon.

| File | Purpose |
| --- | --- |
| `svg/` | Design sources: adaptive icon layers (108 × 108 grid), monochrome layer, status bar icon (24 × 24) |
| `gen_mark_paths.py` | Writes the VectorDrawables (launcher layers, in-app logos) with explicit paths, because VectorDrawable has no masks |
| `render_pngs.py` | Renders the bitmaps: legacy launcher icon, `icon.png`, `ic_stat_nagmedar` (needs Python Playwright + Chromium) |
| `rebrand_strings.py` | Replaces the VLC product name in the English and Turkish strings |

Regenerate after changing the design:

```sh
python3 buildsystem/branding/gen_mark_paths.py application/resources/src/main/res/drawable
python3 buildsystem/branding/render_pngs.py .
```

Colours: blue `#8AB0FF` (upper half), coral `#FF7A6E` (lower half), background `#12161B`.

The TV banner (`drawable-*/banner.png`) uses IBM Plex Sans SemiBold (SIL OFL 1.1). The widget
previews and the sample cover (`drawable-nodpi/vlc_widget_*.png`, `vlc_fake_cover.png`) are the
upstream images with the VLC wordmark removed.

Attribution stays: Nağmedar is based on VLC for Android by VideoLAN (GPLv2 or later). The About
screen keeps the VideoLAN copyright, the authors list and the licence text.
