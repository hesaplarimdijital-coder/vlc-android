# Emulator smoke test: passed

- app: `app.nagmedar.debug`, APK `debug`
- installed: versionCode=14010002 minSdk=34 targetSdk=36; versionName=1.0.2; pkgFlags=[ DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP LARGE_HEAP ]
- device: model=sdk_gphone64_x86_64, sdk=35, abi=x86_64, build=sdk_gphone64_x86_64-userdebug 15 AE3A.240806.043 12960925 dev-keys, size=Physical size: 2560x1600, density=Physical density: 320
- source: d0f3e7648f1276cde4b9cd114dbaa3b41ca23868 (story/shell-chrome, debug build, API 35, glass), run 42105231110
- passes: glass
- 80 steps in 17 min 40 s; 0 crash/ANR finding(s), 0 driver error(s), 0 StrictMode violation(s), 0 of 13 1.0 checks failed; 0 ANR/crash of the system (the emulator's, not failures)

## 1.0 checks

| pass | step | check | result | seen |
|------|------|-------|--------|------|
| glass | 7 | a track in the Music's list | ok |  |
| glass | 8 | the mini player shows the track | ok |  |
| glass | 10 | the mini player shows (A, dark) | ok |  |
| glass | 10 | the bottom bar's colour continues behind the three buttons (dark theme) | ok | bar (12, 15, 18), behind the buttons (12, 15, 18) |
| glass | 22 | the mini player shows (B, dark) | ok |  |
| glass | 34 | the mini player shows (C, dark) | ok |  |
| glass | 46 | the mini player shows (A, light) | ok |  |
| glass | 46 | the bottom bar's colour continues behind the three buttons (light theme) | ok | bar (255, 255, 255), behind the buttons (255, 255, 255) |
| glass | 58 | the mini player shows (B, light) | ok |  |
| glass | 70 | the mini player shows (C, light) | ok |  |
| glass | 80 | the glass screenshots were taken (A, B, C in dark and light) | ok | 36 screenshots |
| all | 80 | no film, series, video, Home, TV Lab or Whisper words on the app's screens | ok |  |
| all | 80 | no StrictMode violation of the app (debug build) | ok | 0 in the log, 0 in the dropbox |

## Frames (dumpsys gfxinfo; informational on an emulator, over 40 % janky is flagged)

| pass | step | gesture | frames | janky | 50th | 90th | 95th | 99th |
|------|------|---------|--------|-------|------|------|------|------|

## The system's ANRs and crashes (the emulator's own: recorded, the step tried once more)

None.

## Steps

| # | pass | step | result | screenshot | note |
|---|------|------|--------|------------|------|
| 1 | glass | Launch (glass) | ok | [001-glass-launch-glass.png](shots/001-glass-launch-glass.png) |  |
| 2 | glass | First run (glass) step 1 (Continue) | ok | [002-glass-first-run-glass-step-1-continue.png](shots/002-glass-first-run-glass-step-1-continue.png) |  |
| 3 | glass | First run (glass) step 2 (Continue · 2 sources) | ok | [003-glass-first-run-glass-step-2-continue-2-sources.png](shots/003-glass-first-run-glass-step-2-continue-2-sources.png) |  |
| 4 | glass | First run (glass) step 3 (Continue) | ok | [004-glass-first-run-glass-step-3-continue.png](shots/004-glass-first-run-glass-step-3-continue.png) |  |
| 5 | glass | First run (glass) step 4 (Start) | ok | [005-glass-first-run-glass-step-4-start.png](shots/005-glass-first-run-glass-step-4-start.png) |  |
| 6 | glass | After the scan (glass) | ok | [006-glass-after-the-scan-glass.png](shots/006-glass-after-the-scan-glass.png) |  |
| 7 | glass | Music | ok | [007-glass-music.png](shots/007-glass-music.png) |  |
| 8 | glass | Play Profile track 1.1 | ok | [008-glass-play-profile-track-1-1.png](shots/008-glass-play-profile-track-1-1.png) |  |
| 9 | glass | Music | ok | [009-glass-music.png](shots/009-glass-music.png) |  |
| 10 | glass | Music (A, dark) | ok | [010-glass-music-a-dark.png](shots/010-glass-music-a-dark.png) |  |
| 11 | glass | Glass A dark music (top) | ok | [011-glass-glass-a-dark-music-top.png](shots/011-glass-glass-a-dark-music-top.png) |  |
| 12 | glass | Glass A dark music (scrolled) | ok | [012-glass-glass-a-dark-music-scrolled.png](shots/012-glass-glass-a-dark-music-scrolled.png) |  |
| 13 | glass | Playlists (A, dark) | ok | [013-glass-playlists-a-dark.png](shots/013-glass-playlists-a-dark.png) |  |
| 14 | glass | Glass A dark playlists (top) | ok | [014-glass-glass-a-dark-playlists-top.png](shots/014-glass-glass-a-dark-playlists-top.png) |  |
| 15 | glass | Glass A dark playlists (scrolled) | ok | [015-glass-glass-a-dark-playlists-scrolled.png](shots/015-glass-glass-a-dark-playlists-scrolled.png) |  |
| 16 | glass | Music | ok | [016-glass-music.png](shots/016-glass-music.png) |  |
| 17 | glass | Search (A, dark) | ok | [017-glass-search-a-dark.png](shots/017-glass-search-a-dark.png) |  |
| 18 | glass | Glass A dark search (top) | ok | [018-glass-glass-a-dark-search-top.png](shots/018-glass-glass-a-dark-search-top.png) |  |
| 19 | glass | Glass A dark search (scrolled) | ok | [019-glass-glass-a-dark-search-scrolled.png](shots/019-glass-glass-a-dark-search-scrolled.png) |  |
| 20 | glass | Back from the search page | ok | [020-glass-back-from-the-search-page.png](shots/020-glass-back-from-the-search-page.png) |  |
| 21 | glass | Music | ok | [021-glass-music.png](shots/021-glass-music.png) |  |
| 22 | glass | Music (B, dark) | ok | [022-glass-music-b-dark.png](shots/022-glass-music-b-dark.png) |  |
| 23 | glass | Glass B dark music (top) | ok | [023-glass-glass-b-dark-music-top.png](shots/023-glass-glass-b-dark-music-top.png) |  |
| 24 | glass | Glass B dark music (scrolled) | ok | [024-glass-glass-b-dark-music-scrolled.png](shots/024-glass-glass-b-dark-music-scrolled.png) |  |
| 25 | glass | Playlists (B, dark) | ok | [025-glass-playlists-b-dark.png](shots/025-glass-playlists-b-dark.png) |  |
| 26 | glass | Glass B dark playlists (top) | ok | [026-glass-glass-b-dark-playlists-top.png](shots/026-glass-glass-b-dark-playlists-top.png) |  |
| 27 | glass | Glass B dark playlists (scrolled) | ok | [027-glass-glass-b-dark-playlists-scrolled.png](shots/027-glass-glass-b-dark-playlists-scrolled.png) |  |
| 28 | glass | Music | ok | [028-glass-music.png](shots/028-glass-music.png) |  |
| 29 | glass | Search (B, dark) | ok | [029-glass-search-b-dark.png](shots/029-glass-search-b-dark.png) |  |
| 30 | glass | Glass B dark search (top) | ok | [030-glass-glass-b-dark-search-top.png](shots/030-glass-glass-b-dark-search-top.png) |  |
| 31 | glass | Glass B dark search (scrolled) | ok | [031-glass-glass-b-dark-search-scrolled.png](shots/031-glass-glass-b-dark-search-scrolled.png) |  |
| 32 | glass | Back from the search page | ok | [032-glass-back-from-the-search-page.png](shots/032-glass-back-from-the-search-page.png) |  |
| 33 | glass | Music | ok | [033-glass-music.png](shots/033-glass-music.png) |  |
| 34 | glass | Music (C, dark) | ok | [034-glass-music-c-dark.png](shots/034-glass-music-c-dark.png) |  |
| 35 | glass | Glass C dark music (top) | ok | [035-glass-glass-c-dark-music-top.png](shots/035-glass-glass-c-dark-music-top.png) |  |
| 36 | glass | Glass C dark music (scrolled) | ok | [036-glass-glass-c-dark-music-scrolled.png](shots/036-glass-glass-c-dark-music-scrolled.png) |  |
| 37 | glass | Playlists (C, dark) | ok | [037-glass-playlists-c-dark.png](shots/037-glass-playlists-c-dark.png) |  |
| 38 | glass | Glass C dark playlists (top) | ok | [038-glass-glass-c-dark-playlists-top.png](shots/038-glass-glass-c-dark-playlists-top.png) |  |
| 39 | glass | Glass C dark playlists (scrolled) | ok | [039-glass-glass-c-dark-playlists-scrolled.png](shots/039-glass-glass-c-dark-playlists-scrolled.png) |  |
| 40 | glass | Music | ok | [040-glass-music.png](shots/040-glass-music.png) |  |
| 41 | glass | Search (C, dark) | ok | [041-glass-search-c-dark.png](shots/041-glass-search-c-dark.png) |  |
| 42 | glass | Glass C dark search (top) | ok | [042-glass-glass-c-dark-search-top.png](shots/042-glass-glass-c-dark-search-top.png) |  |
| 43 | glass | Glass C dark search (scrolled) | ok | [043-glass-glass-c-dark-search-scrolled.png](shots/043-glass-glass-c-dark-search-scrolled.png) |  |
| 44 | glass | Back from the search page | ok | [044-glass-back-from-the-search-page.png](shots/044-glass-back-from-the-search-page.png) |  |
| 45 | glass | Music | ok | [045-glass-music.png](shots/045-glass-music.png) |  |
| 46 | glass | Music (A, light) | ok | [046-glass-music-a-light.png](shots/046-glass-music-a-light.png) |  |
| 47 | glass | Glass A light music (top) | ok | [047-glass-glass-a-light-music-top.png](shots/047-glass-glass-a-light-music-top.png) |  |
| 48 | glass | Glass A light music (scrolled) | ok | [048-glass-glass-a-light-music-scrolled.png](shots/048-glass-glass-a-light-music-scrolled.png) |  |
| 49 | glass | Playlists (A, light) | ok | [049-glass-playlists-a-light.png](shots/049-glass-playlists-a-light.png) |  |
| 50 | glass | Glass A light playlists (top) | ok | [050-glass-glass-a-light-playlists-top.png](shots/050-glass-glass-a-light-playlists-top.png) |  |
| 51 | glass | Glass A light playlists (scrolled) | ok | [051-glass-glass-a-light-playlists-scrolled.png](shots/051-glass-glass-a-light-playlists-scrolled.png) |  |
| 52 | glass | Music | ok | [052-glass-music.png](shots/052-glass-music.png) |  |
| 53 | glass | Search (A, light) | ok | [053-glass-search-a-light.png](shots/053-glass-search-a-light.png) |  |
| 54 | glass | Glass A light search (top) | ok | [054-glass-glass-a-light-search-top.png](shots/054-glass-glass-a-light-search-top.png) |  |
| 55 | glass | Glass A light search (scrolled) | ok | [055-glass-glass-a-light-search-scrolled.png](shots/055-glass-glass-a-light-search-scrolled.png) |  |
| 56 | glass | Back from the search page | ok | [056-glass-back-from-the-search-page.png](shots/056-glass-back-from-the-search-page.png) |  |
| 57 | glass | Music | ok | [057-glass-music.png](shots/057-glass-music.png) |  |
| 58 | glass | Music (B, light) | ok | [058-glass-music-b-light.png](shots/058-glass-music-b-light.png) |  |
| 59 | glass | Glass B light music (top) | ok | [059-glass-glass-b-light-music-top.png](shots/059-glass-glass-b-light-music-top.png) |  |
| 60 | glass | Glass B light music (scrolled) | ok | [060-glass-glass-b-light-music-scrolled.png](shots/060-glass-glass-b-light-music-scrolled.png) |  |
| 61 | glass | Playlists (B, light) | ok | [061-glass-playlists-b-light.png](shots/061-glass-playlists-b-light.png) |  |
| 62 | glass | Glass B light playlists (top) | ok | [062-glass-glass-b-light-playlists-top.png](shots/062-glass-glass-b-light-playlists-top.png) |  |
| 63 | glass | Glass B light playlists (scrolled) | ok | [063-glass-glass-b-light-playlists-scrolled.png](shots/063-glass-glass-b-light-playlists-scrolled.png) |  |
| 64 | glass | Music | ok | [064-glass-music.png](shots/064-glass-music.png) |  |
| 65 | glass | Search (B, light) | ok | [065-glass-search-b-light.png](shots/065-glass-search-b-light.png) |  |
| 66 | glass | Glass B light search (top) | ok | [066-glass-glass-b-light-search-top.png](shots/066-glass-glass-b-light-search-top.png) |  |
| 67 | glass | Glass B light search (scrolled) | ok | [067-glass-glass-b-light-search-scrolled.png](shots/067-glass-glass-b-light-search-scrolled.png) |  |
| 68 | glass | Back from the search page | ok | [068-glass-back-from-the-search-page.png](shots/068-glass-back-from-the-search-page.png) |  |
| 69 | glass | Music | ok | [069-glass-music.png](shots/069-glass-music.png) |  |
| 70 | glass | Music (C, light) | ok | [070-glass-music-c-light.png](shots/070-glass-music-c-light.png) |  |
| 71 | glass | Glass C light music (top) | ok | [071-glass-glass-c-light-music-top.png](shots/071-glass-glass-c-light-music-top.png) |  |
| 72 | glass | Glass C light music (scrolled) | ok | [072-glass-glass-c-light-music-scrolled.png](shots/072-glass-glass-c-light-music-scrolled.png) |  |
| 73 | glass | Playlists (C, light) | ok | [073-glass-playlists-c-light.png](shots/073-glass-playlists-c-light.png) |  |
| 74 | glass | Glass C light playlists (top) | ok | [074-glass-glass-c-light-playlists-top.png](shots/074-glass-glass-c-light-playlists-top.png) |  |
| 75 | glass | Glass C light playlists (scrolled) | ok | [075-glass-glass-c-light-playlists-scrolled.png](shots/075-glass-glass-c-light-playlists-scrolled.png) |  |
| 76 | glass | Music | ok | [076-glass-music.png](shots/076-glass-music.png) |  |
| 77 | glass | Search (C, light) | ok | [077-glass-search-c-light.png](shots/077-glass-search-c-light.png) |  |
| 78 | glass | Glass C light search (top) | ok | [078-glass-glass-c-light-search-top.png](shots/078-glass-glass-c-light-search-top.png) |  |
| 79 | glass | Glass C light search (scrolled) | ok | [079-glass-glass-c-light-search-scrolled.png](shots/079-glass-glass-c-light-search-scrolled.png) |  |
| 80 | glass | Back from the search page | ok | [080-glass-back-from-the-search-page.png](shots/080-glass-back-from-the-search-page.png) |  |

## Warnings

None.

### Missing native libraries (arm64-only libraries on an x86_64 emulator)

No link errors were logged.

### StrictMode violations: 0 (the app's; by the system's policy for a debuggable app)


### Error-level log lines of the app (top 25)

- 21x `VLC-std: s_glBindAttribLocation: bind attrib 0 name position`
- 12x `VLC-std: s_glBindAttribLocation: bind attrib 1 name color`
- 9x `VLC-std: s_glBindAttribLocation: bind attrib 2 name localCoord`
- 6x `VLC-std: s_glBindAttribLocation: bind attrib 0 name inPosition`
- 6x `VLC-std: s_glBindAttribLocation: bind attrib 1 name inColor`
- 6x `VLC-std: s_glBindAttribLocation: bind attrib 1 name localCoord`
- 6x `FrameTracker: force finish cuj, time out: J<IME_INSETS_HIDE_ANIMATION::1@1@app.nagmedar.debug>`
- 5x `VLC-std: s_glBindAttribLocation: bind attrib 2 name inCircleEdge`
- 3x `VLC-std: s_glBindAttribLocation: bind attrib 1 name coverage`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 2 name color`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 3 name localCoord`
- 2x `FrameTracker: force finish cuj, time out: J<IME_INSETS_SHOW_ANIMATION::0@1@app.nagmedar.debug>`
- 1x `VLC/LibVLC/Util: WARNING: Can't find shared library`
- 1x `VLC-std: s_glBindAttribLocation: bind attrib 2 name inTextureCoords`
- 1x `VLC: [00007b383d22e4b0/1215] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [00007b36dd2b2bf0/1215] libvlc input: Your media can't be opened`
- 1x `VLC: [00007b36dd2b2bf0/1215] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/obb/'. Check the log for details.`
- 1x `VLC: [00007b383d225810/1219] libvlc access: cannot open file /storage/emulated/0/Android/data/ (Permission denied)`
- 1x `VLC: [00007b36dd2b2bf0/1219] libvlc input: Your media can't be opened`
- 1x `VLC: [00007b36dd2b2bf0/1219] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/data/'. Check the log for details.`
- 1x `ActivityThread: Failed to find provider info for androidx.car.app.connection`
- 1x `VLC-std: s_glBindAttribLocation: bind attrib 0 name radii_selector`
- 1x `VLC-std: s_glBindAttribLocation: bind attrib 1 name corner_and_radius_outsets`
- 1x `VLC-std: s_glBindAttribLocation: bind attrib 2 name aa_bloat_and_coverage`
- 1x `VLC-std: s_glBindAttribLocation: bind attrib 3 name radii_x`
