# Phone screen recording of 23 Oct (207 s, Samsung phone, 1080×2340) — frame-by-frame findings

Source: `$SCRATCH/video/rec.mp4`
(no audio; the user says sound was fine). Tools in that folder: `f2/` 2 fps frames, `sheets/ov_NN.png`
contact sheets (15 s each), `f30/` 30 fps grey frames + `motion.txt` (per-segment motion: start,
duration, moving frames, longest stall, max diff, shape), `zoom.py <name> <t0> <dur> [cols] [tile_w]`
(30 fps colour zoom sheet with the frame-to-frame diff on each tile; env CROP=crop=w:h:x:y) → `zoom/`.

Timeline: 0–10 s launcher/recents (the app was swiped away at 8.4 s and cold-started at 10.1 s;
content visible 170 ms later), 10–23 s Home + a 4K video opened twice, 24–75 s video player (panel
tabs, visualiser notice), 75–130 s music (list, phone player, queue, lyrics, visualiser, rating),
130–180 s podcasts (episode page, downloads, copy), 180–207 s radio search, More, Streams, Convert.

## A. Motion (what the user calls "animasyonlar olmamış")

Measured at 30 fps (a "cut" = the whole screen changes within one 33 ms frame, i.e. no transition):

| t (s) | what | result |
|---|---|---|
| 57.23, 68.83 | video player: bottom panel (Senkron / Altyazı tab) closes | **cut**; the video jumps from the top third back to centre |
| 64.13 | video player: panel (Efektler) opens | **cut**; video jumps up |
| 90.80 | music player → "Sıradaki" (queue) | **cut**, and the queue first paints its EMPTY state ("Sırada başka bir şey yok …") for 2 frames before the list appears |
| 92.00 | queue → player | **cut** |
| 99.90 / 100.97 | player → full-screen visualiser / back | **cut** both ways |
| 119.90 | queue → player (lyrics shown) | **cut** |
| 120.33 | phone player closes (back) → Müzik list | **cut** (no predictive back, no slide down) |
| 14.23 → 14.40 | tap on a Home video card | the mini player pops in with the VIDEO ("20261012_183… · Şimdi çalıyor · HD") 170 ms before the player activity opens; the "Son çalınan müzik" shelf vanishes at the same moment (see B1) |
| 18.20–18.77 | video open | system window animation (fine), first video frame 570 ms after the tap (fine) |
| 19.37–19.67 | video close | system animation (fine); the mini player again shows the video for ~300 ms, then disappears |
| bottom-nav switches (193–206 s) | fade-through 170–230 ms | fine (kit) |
| hide-on-scroll bars (160.5, 161.6, 176.3, 177.8) | follow the fling, 2–3 frames | fine (kit design: the bar moves with the content) |
| scrolls (podcast page, music list, radio list) | 15–27 diff every frame, no stalls | fine |

Root causes (code read on master a7279a68):
- `NagmedarApp.kt` ~436–475: `PhonePlayerHost`, `PlayingLyricsFullScreen`, `PlayingVisualizerFullScreen`,
  `QueueSheet` are composed behind plain `if (flag)` — no `AnimatedVisibility`/transition at all.
- `player/panel/PanelHost.kt` `relayout()`: the panel frame is `VISIBLE/GONE` and the player root's margin
  is set in one step — no animation; the video surface jumps.
- `playback/QueuePanel.kt` / `rememberPlayQueue()`: the empty state is drawn while the queue is still
  being read.
- `PlayerBar` shows `nowPlaying.media` whatever its type: a video flashes in the bar around the player
  activity.

## B. Other defects seen

1. **Home: "Son çalınan müzik" shelf disappears** when a video is tapped (14.23) and never returns
   (Home at 70 s and 207 s shows only "İzlemeye devam et" with empty space; a small progress ring
   (≈20 px) shows in the empty area 19.5–19.8 s). `HomeScreen` reads `repository.recentAudio()` =
   medialibrary `getRecentAudio()` = **audio sorted by insertion date** (recently ADDED, not played);
   `loadWhileScanning` re-reads every 4 s while a scan is active (it was active the whole video).
2. **Scan strip "Medya klasörleri aranıyor" stays for the whole 3.5 min, on every page** (Home,
   Films, Music, Podcast, Radio, More…). `ScanState.from(progress)` takes `inDiscovery` from the last
   `ScanProgress` posted; `MediaParsingService.onDiscoveryCompleted` flips the flag without posting;
   nothing bounds the strip when the medialibrary stays "working".
3. **Films page**: first open shows a blank "Medya klasörleri aranıyor" state (`empty && scan.active`
   → placeholder) although films exist.
4. **Music › Yıllar** groups show epoch seconds ("1767225600") instead of years: `MediaWrapper.releaseYear`
   carries the medialibrary's `releaseDate()` (seconds) for tracks; `toTrack()` only drops ≤ 0.
5. **Video player panel tab row clipped** on the phone: "Altyazı · Senkron · Efektler · Bilgi · Yer im|".
6. **Visualiser notice in the video player** ("…mikrofon izni ister" + "İzin ver"): taps on the button
   do nothing and fall through to the player (play/pause toggles) — the touch layer eats them.
7. **Full-screen spectrum**: frequency axis labels overlap on the phone ("50 Hz100 Hz200 Hz…").
8. **Dialogs show a desktop "Esc" keycap** at the top right on the phone (Puan ver, Uyku zamanlayıcısı…):
   `player/navigate/NavigationDialogs.kt` `DialogCard` always draws `KeyCap("Esc")` (≈40 dialogs use it);
   no touch close control.
9. **Podcast episode page**: the main button flips between "Sürdür" and "Devam et · 0:43"
   (`nag_pod_ep_continue` vs `nag_pod_ep_resume`); the timeline shows 0:00 / −37:04 although a position
   is saved; the "36:20 kaldı" chip appears late and shifts the layout; starting playback takes 3–5 s
   with the play/pause state flapping before "Duraklat" settles.
10. **Two "copied" notices**: the app's "Adres kopyalandı" and Android 13+'s own "Kopyalandı." at once
    (podcast page 176 s; the same helper pattern in `nag_hls_copied`, `nag_streams_copied`, `nag_ra_copied`).
11. **Radio page tab row clipped**: "Favoriler · Son çalınanlar 3 · Ara · İçe aktarı|".
12. Mini player shows a video ("Şimdi çalıyor") around the video activity (see A).

Not defects: launcher/recents system animations; the radio list reordering while scrolling was a long
fling; hide-on-scroll speed.
