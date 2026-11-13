# Emulator smoke test: passed

- app: `app.nagmedar.debug`, APK `personal`
- installed: versionCode=14010014 minSdk=34 targetSdk=37; versionName=1.0.6; pkgFlags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP LARGE_HEAP ]
- device: model=Car on x86_64 emulator, sdk=34, abi=x86_64, build=sdk_gcar_x86_64-userdebug 14 UAA1.250512.001 13479943 dev-keys, size=Physical size: 1024x768, density=Physical density: 160
- source: f77a341a0c0017788b52c0987b5eebaaefcb7b20 (story/release-1.0.6, personal build, API 34, auto-shots), run 43342984526
- passes: auto-shots
- 25 steps in 5 min 56 s; 0 crash/ANR finding(s), 0 driver error(s), 0 StrictMode violation(s), 0 of 4 1.0 checks failed; 0 ANR/crash of the system (the emulator's, not failures)

## 1.0 checks

| pass | step | check | result | seen |
|------|------|-------|--------|------|
| auto-shots | 7 | the car's media app shows the app's tabs | ok | music, podcasts, radio, recent of Music, Podcasts, Radio, Recently played; the car's media app says: Albümler, Müzik, Podcast, Radyo, Sanatçılar, Scroll down, Scroll up, Search, Son çalınanlar, Switch apps, Tüm müziği karıştırarak çal, com.android.car.ui.utils.ROTARY_CONTAINER, Çalma listeleri |
| auto-shots | 12 | a track tapped: the car's player names it | ok | tapped "Profile track 2.1"; the player says: Profile track 2.1, 1 / 8 · Nagmedar Artist 1 · Profile album 2, Profile album 2, 0:04, /, 3:00, Now Playing |
| auto-shots | 12 | a track tapped: it played (the car's player's clock ran) | ok | the clock: 0:04; the app's session said it played: yes |
| all | 25 | no film, series, video, Home, TV Lab or Whisper words on the app's screens | ok |  |

## Frames (dumpsys gfxinfo; informational on an emulator, over 40 % janky is flagged)

| pass | step | gesture | frames | janky | 50th | 90th | 95th | 99th |
|------|------|---------|--------|-------|------|------|------|------|

## The system's ANRs and crashes (the emulator's own: recorded, the step tried once more)

None.

## Steps

| # | pass | step | result | screenshot | note |
|---|------|------|--------|------------|------|
| 1 | auto-shots | The app on the car's screen | ok | [001-auto-shots-the-app-on-the-car-s-screen.png](shots/001-auto-shots-the-app-on-the-car-s-screen.png) |  |
| 2 | auto-shots | First run step 1 (Devam) | ok | [002-auto-shots-first-run-step-1-devam.png](shots/002-auto-shots-first-run-step-1-devam.png) |  |
| 3 | auto-shots | First run step 2 (Devam · 2 kaynak) | ok | [003-auto-shots-first-run-step-2-devam-2-kaynak.png](shots/003-auto-shots-first-run-step-2-devam-2-kaynak.png) |  |
| 4 | auto-shots | First run step 3 (Devam) | ok | [004-auto-shots-first-run-step-3-devam.png](shots/004-auto-shots-first-run-step-3-devam.png) |  |
| 5 | auto-shots | First run step 4 (Başla) | ok | [005-auto-shots-first-run-step-4-ba-la.png](shots/005-auto-shots-first-run-step-4-ba-la.png) |  |
| 6 | auto-shots | The app after its first run | ok | [006-auto-shots-the-app-after-its-first-run.png](shots/006-auto-shots-the-app-after-its-first-run.png) |  |
| 7 | auto-shots | The car's media app: Nağmedar's first tab | ok | [007-auto-shots-the-car-s-media-app-na-medar-s-first-tab.png](shots/007-auto-shots-the-car-s-media-app-na-medar-s-first-tab.png) |  |
| 8 | auto-shots | Music | ok | [008-auto-shots-music.png](shots/008-auto-shots-music.png) |  |
| 9 | auto-shots | Music: the albums | ok | [009-auto-shots-music-the-albums.png](shots/009-auto-shots-music-the-albums.png) |  |
| 10 | auto-shots | An album ("Profile album 2") | ok | [010-auto-shots-an-album-profile-album-2.png](shots/010-auto-shots-an-album-profile-album-2.png) |  |
| 11 | auto-shots | A track tapped: the car's player ("Profile track 2.1") | ok | [011-auto-shots-a-track-tapped-the-car-s-player-profile-track-2-.png](shots/011-auto-shots-a-track-tapped-the-car-s-player-profile-track-2-.png) | ; the picture only: this screen does not rest, so it was not read |
| 12 | auto-shots | The car's player, paused | ok | [012-auto-shots-the-car-s-player-paused.png](shots/012-auto-shots-the-car-s-player-paused.png) |  |
| 13 | auto-shots | Podcasts | ok | [013-auto-shots-podcasts.png](shots/013-auto-shots-podcasts.png) |  |
| 14 | auto-shots | Podcasts: Discover | ok | [014-auto-shots-podcasts-discover.png](shots/014-auto-shots-podcasts-discover.png) |  |
| 15 | auto-shots | Discover: the popular shows | ok | [015-auto-shots-discover-the-popular-shows.png](shots/015-auto-shots-discover-the-popular-shows.png) |  |
| 16 | auto-shots | A show of the directory ("Dateline NBC") | ok | [016-auto-shots-a-show-of-the-directory-dateline-nbc.png](shots/016-auto-shots-a-show-of-the-directory-dateline-nbc.png) |  |
| 17 | auto-shots | Radio | ok | [017-auto-shots-radio.png](shots/017-auto-shots-radio.png) |  |
| 18 | auto-shots | Radio: the countries | ok | [018-auto-shots-radio-the-countries.png](shots/018-auto-shots-radio-the-countries.png) |  |
| 19 | auto-shots | A country's stations ("Amerika Birleşik Devletleri") | ok | [019-auto-shots-a-country-s-stations-amerika-birle-ik-devletleri.png](shots/019-auto-shots-a-country-s-stations-amerika-birle-ik-devletleri.png) |  |
| 20 | auto-shots | A station tapped: the car's player ("Classic Vinyl HD") | ok | [020-auto-shots-a-station-tapped-the-car-s-player-classic-vinyl-.png](shots/020-auto-shots-a-station-tapped-the-car-s-player-classic-vinyl-.png) | ; the picture only: this screen does not rest, so it was not read |
| 21 | auto-shots | The car's player, paused | ok | [021-auto-shots-the-car-s-player-paused.png](shots/021-auto-shots-the-car-s-player-paused.png) |  |
| 22 | auto-shots | Recently played | ok | [022-auto-shots-recently-played.png](shots/022-auto-shots-recently-played.png) |  |
| 23 | auto-shots | The car's search | ok | [023-auto-shots-the-car-s-search.png](shots/023-auto-shots-the-car-s-search.png) |  |
| 24 | auto-shots | Search: "Profile" typed | ok | [024-auto-shots-search-profile-typed.png](shots/024-auto-shots-search-profile-typed.png) |  |
| 25 | auto-shots | Search: after Back (the keyboard down) | ok | [025-auto-shots-search-after-back-the-keyboard-down.png](shots/025-auto-shots-search-after-back-the-keyboard-down.png) |  |

## Warnings

None.

### Missing native libraries (arm64-only libraries on an x86_64 emulator)

No link errors were logged.

### StrictMode violations: 0 (the app's; by the system's policy for a debuggable app)


### Error-level log lines of the app (top 25)

- 6x `VLC-std: s_glBindAttribLocation: bind attrib 0 name inPosition`
- 6x `VLC-std: s_glBindAttribLocation: bind attrib 1 name inColor`
- 5x `VLC-std: s_glBindAttribLocation: bind attrib 2 name inCircleEdge`
- 3x `VLC-std: s_glBindAttribLocation: bind attrib 0 name position`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 1 name color`
- 2x `VLC: [00007615ab868b90/1a9f] libvlc demux: Can't stat /dev/sr0`
- 2x `VLC-std: No such file or directory`
- 2x `VLC: [00007615ab868b90/1a9f] libvlc demux: Could not open /dev/sr0`
- 2x `VLC: [00007615ab868b90/1a9f] libvlc demux: DVDRead cannot open source: /dev/sr0`
- 1x `.nagmedar.debug: Not starting debugger since process cannot load the jdwp agent.`
- 1x `VLC/LibVLC/Util: WARNING: Can't find shared library`
- 1x `VLC-std: s_glBindAttribLocation: bind attrib 2 name inTextureCoords`
- 1x `VLC-std: s_glBindAttribLocation: bind attrib 1 name localCoord`
- 1x `VLC-std: s_glBindAttribLocation: bind attrib 2 name localCoord`
- 1x `VLC: [00007615ab8199f0/13c3] libvlc access: cannot open file /storage/emulated/10/Android/obb/ (Permission denied)`
- 1x `VLC: [000076163b8d4bc0/13c3] libvlc input: Your media can't be opened`
- 1x `VLC: [000076163b8d4bc0/13c3] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/10/Android/obb/'. Check the log for details.`
- 1x `VLC: [00007615ab83f850/13c6] libvlc access: cannot open file /storage/emulated/10/Android/data/ (Permission denied)`
- 1x `VLC: [000076163b8d4bc0/13c6] libvlc input: Your media can't be opened`
- 1x `VLC: [000076163b8d4bc0/13c6] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/10/Android/data/'. Check the log for details.`
- 1x `VLC: [00007615ab868b90/1a9f] libvlc demux: Version of libdvdread does not support DVD-VideoRecording`
- 1x `VLC-std: [mp3 @ 0x76161b87e480] Skipping 411 bytes of junk at 0.`
