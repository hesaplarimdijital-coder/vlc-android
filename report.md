# Emulator smoke test: passed

- app: `app.nagmedar.debug`, APK `personal`
- installed: versionCode=14010016 minSdk=34 targetSdk=37; versionName=1.0.8; pkgFlags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP KILL_AFTER_RESTORE LARGE_HEAP ]
- device: model=sdk_gphone64_x86_64, sdk=37, abi=x86_64, build=sdk_gphone64_x86_64-userdebug 17 CE2A.260420.051.A3 16571990 dev-keys, size=Physical size: 2560x1600, density=Physical density: 320
- source: b1c0a9da1fed7ed0055e73a632c4f11444af7aad (story/release-1.0.8, personal build, API 37.0, sheet-trials), run 43500817348
- passes: sheet-trials
- 78 steps in 12 min 3 s; 0 crash/ANR finding(s), 0 driver error(s), 0 StrictMode violation(s), 0 of 1 1.0 checks failed; 1 ANR/crash of the system (the emulator's, not failures)

## 1.0 checks

| pass | step | check | result | seen |
|------|------|-------|--------|------|
| all | 78 | no film, series, video, Home, TV Lab or Whisper words on the app's screens | ok |  |

## Frames (dumpsys gfxinfo; informational on an emulator, over 40 % janky is flagged)

| pass | step | gesture | frames | janky | 50th | 90th | 95th | 99th |
|------|------|---------|--------|-------|------|------|------|------|

## The system's ANRs and crashes (the emulator's own: recorded, the step tried once more)

| pass | step | what | whose | seen | how |
|------|------|------|-------|------|-----|
| sheet-trials | 1 | ANR | com.google.android.gms.persistent | Reason: executing service com.google.android.gms/.geotimezone.GeoTimeZoneService, waited 20367ms | log |

## Steps

| # | pass | step | result | screenshot | note |
|---|------|------|--------|------------|------|
| 1 | sheet-trials | Launch (the walk's media pushed) | ok | [001-sheet-trials-launch-the-walk-s-media-pushed.png](shots/001-sheet-trials-launch-the-walk-s-media-pushed.png) |  |
| 2 | sheet-trials | First run with the media step 1 (Continue) | ok | [002-sheet-trials-first-run-with-the-media-step-1-continue.png](shots/002-sheet-trials-first-run-with-the-media-step-1-continue.png) |  |
| 3 | sheet-trials | First run with the media step 2 (Continue · 2 sources) | ok | [003-sheet-trials-first-run-with-the-media-step-2-continue-2-sourc.png](shots/003-sheet-trials-first-run-with-the-media-step-2-continue-2-sourc.png) |  |
| 4 | sheet-trials | First run with the media step 3 (Continue) | ok | [004-sheet-trials-first-run-with-the-media-step-3-continue.png](shots/004-sheet-trials-first-run-with-the-media-step-3-continue.png) |  |
| 5 | sheet-trials | First run with the media step 4 (Start) | ok | [005-sheet-trials-first-run-with-the-media-step-4-start.png](shots/005-sheet-trials-first-run-with-the-media-step-4-start.png) |  |
| 6 | sheet-trials | After the scan | ok | [006-sheet-trials-after-the-scan.png](shots/006-sheet-trials-after-the-scan.png) |  |
| 7 | sheet-trials | Music | ok | [007-sheet-trials-music.png](shots/007-sheet-trials-music.png) |  |
| 8 | sheet-trials | Trial 1: a track's ⋯ | ok | [008-sheet-trials-trial-1-a-track-s.png](shots/008-sheet-trials-trial-1-a-track-s.png) |  |
| 9 | sheet-trials | Trial 1: dragged by swipe | ok | [009-sheet-trials-trial-1-dragged-by-swipe.png](shots/009-sheet-trials-trial-1-dragged-by-swipe.png) |  |
| 10 | sheet-trials | Trial 2: a track's ⋯ | ok | [010-sheet-trials-trial-2-a-track-s.png](shots/010-sheet-trials-trial-2-a-track-s.png) |  |
| 11 | sheet-trials | Trial 2: dragged by drag | ok | [011-sheet-trials-trial-2-dragged-by-drag.png](shots/011-sheet-trials-trial-2-dragged-by-drag.png) |  |
| 12 | sheet-trials | Trial 3: a track's ⋯ | ok | [012-sheet-trials-trial-3-a-track-s.png](shots/012-sheet-trials-trial-3-a-track-s.png) |  |
| 13 | sheet-trials | Trial 3: dragged by stalled | ok | [013-sheet-trials-trial-3-dragged-by-stalled.png](shots/013-sheet-trials-trial-3-dragged-by-stalled.png) |  |
| 14 | sheet-trials | Trial 3: Back closes the sheet | ok | [014-sheet-trials-trial-3-back-closes-the-sheet.png](shots/014-sheet-trials-trial-3-back-closes-the-sheet.png) |  |
| 15 | sheet-trials | Trial 4: a track's ⋯ | ok | [015-sheet-trials-trial-4-a-track-s.png](shots/015-sheet-trials-trial-4-a-track-s.png) |  |
| 16 | sheet-trials | Trial 4: dragged by swipe | ok | [016-sheet-trials-trial-4-dragged-by-swipe.png](shots/016-sheet-trials-trial-4-dragged-by-swipe.png) |  |
| 17 | sheet-trials | Trial 5: a track's ⋯ | ok | [017-sheet-trials-trial-5-a-track-s.png](shots/017-sheet-trials-trial-5-a-track-s.png) |  |
| 18 | sheet-trials | Trial 5: dragged by drag | ok | [018-sheet-trials-trial-5-dragged-by-drag.png](shots/018-sheet-trials-trial-5-dragged-by-drag.png) |  |
| 19 | sheet-trials | Trial 6: a track's ⋯ | ok | [019-sheet-trials-trial-6-a-track-s.png](shots/019-sheet-trials-trial-6-a-track-s.png) |  |
| 20 | sheet-trials | Trial 6: dragged by stalled | ok | [020-sheet-trials-trial-6-dragged-by-stalled.png](shots/020-sheet-trials-trial-6-dragged-by-stalled.png) |  |
| 21 | sheet-trials | Trial 6: Back closes the sheet | ok | [021-sheet-trials-trial-6-back-closes-the-sheet.png](shots/021-sheet-trials-trial-6-back-closes-the-sheet.png) |  |
| 22 | sheet-trials | Trial 7: a track's ⋯ | ok | [022-sheet-trials-trial-7-a-track-s.png](shots/022-sheet-trials-trial-7-a-track-s.png) |  |
| 23 | sheet-trials | Trial 7: dragged by swipe | ok | [023-sheet-trials-trial-7-dragged-by-swipe.png](shots/023-sheet-trials-trial-7-dragged-by-swipe.png) |  |
| 24 | sheet-trials | Trial 8: a track's ⋯ | ok | [024-sheet-trials-trial-8-a-track-s.png](shots/024-sheet-trials-trial-8-a-track-s.png) |  |
| 25 | sheet-trials | Trial 8: dragged by drag | ok | [025-sheet-trials-trial-8-dragged-by-drag.png](shots/025-sheet-trials-trial-8-dragged-by-drag.png) |  |
| 26 | sheet-trials | Trial 9: a track's ⋯ | ok | [026-sheet-trials-trial-9-a-track-s.png](shots/026-sheet-trials-trial-9-a-track-s.png) |  |
| 27 | sheet-trials | Trial 9: dragged by stalled | ok | [027-sheet-trials-trial-9-dragged-by-stalled.png](shots/027-sheet-trials-trial-9-dragged-by-stalled.png) |  |
| 28 | sheet-trials | Trial 9: Back closes the sheet | ok | [028-sheet-trials-trial-9-back-closes-the-sheet.png](shots/028-sheet-trials-trial-9-back-closes-the-sheet.png) |  |
| 29 | sheet-trials | Trial 10: a track's ⋯ | ok | [029-sheet-trials-trial-10-a-track-s.png](shots/029-sheet-trials-trial-10-a-track-s.png) |  |
| 30 | sheet-trials | Trial 10: dragged by swipe | ok | [030-sheet-trials-trial-10-dragged-by-swipe.png](shots/030-sheet-trials-trial-10-dragged-by-swipe.png) |  |
| 31 | sheet-trials | Trial 11: a track's ⋯ | ok | [031-sheet-trials-trial-11-a-track-s.png](shots/031-sheet-trials-trial-11-a-track-s.png) |  |
| 32 | sheet-trials | Trial 11: dragged by drag | ok | [032-sheet-trials-trial-11-dragged-by-drag.png](shots/032-sheet-trials-trial-11-dragged-by-drag.png) |  |
| 33 | sheet-trials | Trial 12: a track's ⋯ | ok | [033-sheet-trials-trial-12-a-track-s.png](shots/033-sheet-trials-trial-12-a-track-s.png) |  |
| 34 | sheet-trials | Trial 12: dragged by stalled | ok | [034-sheet-trials-trial-12-dragged-by-stalled.png](shots/034-sheet-trials-trial-12-dragged-by-stalled.png) |  |
| 35 | sheet-trials | Trial 12: Back closes the sheet | ok | [035-sheet-trials-trial-12-back-closes-the-sheet.png](shots/035-sheet-trials-trial-12-back-closes-the-sheet.png) |  |
| 36 | sheet-trials | Trial 13: a track's ⋯ | ok | [036-sheet-trials-trial-13-a-track-s.png](shots/036-sheet-trials-trial-13-a-track-s.png) |  |
| 37 | sheet-trials | Trial 13: dragged by swipe | ok | [037-sheet-trials-trial-13-dragged-by-swipe.png](shots/037-sheet-trials-trial-13-dragged-by-swipe.png) |  |
| 38 | sheet-trials | Trial 14: a track's ⋯ | ok | [038-sheet-trials-trial-14-a-track-s.png](shots/038-sheet-trials-trial-14-a-track-s.png) |  |
| 39 | sheet-trials | Trial 14: dragged by drag | ok | [039-sheet-trials-trial-14-dragged-by-drag.png](shots/039-sheet-trials-trial-14-dragged-by-drag.png) |  |
| 40 | sheet-trials | Trial 15: a track's ⋯ | ok | [040-sheet-trials-trial-15-a-track-s.png](shots/040-sheet-trials-trial-15-a-track-s.png) |  |
| 41 | sheet-trials | Trial 15: dragged by stalled | ok | [041-sheet-trials-trial-15-dragged-by-stalled.png](shots/041-sheet-trials-trial-15-dragged-by-stalled.png) |  |
| 42 | sheet-trials | Trial 15: Back closes the sheet | ok | [042-sheet-trials-trial-15-back-closes-the-sheet.png](shots/042-sheet-trials-trial-15-back-closes-the-sheet.png) |  |
| 43 | sheet-trials | Trial 16: a track's ⋯ | ok | [043-sheet-trials-trial-16-a-track-s.png](shots/043-sheet-trials-trial-16-a-track-s.png) |  |
| 44 | sheet-trials | Trial 16: dragged by swipe | ok | [044-sheet-trials-trial-16-dragged-by-swipe.png](shots/044-sheet-trials-trial-16-dragged-by-swipe.png) |  |
| 45 | sheet-trials | Trial 17: a track's ⋯ | ok | [045-sheet-trials-trial-17-a-track-s.png](shots/045-sheet-trials-trial-17-a-track-s.png) |  |
| 46 | sheet-trials | Trial 17: dragged by drag | ok | [046-sheet-trials-trial-17-dragged-by-drag.png](shots/046-sheet-trials-trial-17-dragged-by-drag.png) |  |
| 47 | sheet-trials | Trial 18: a track's ⋯ | ok | [047-sheet-trials-trial-18-a-track-s.png](shots/047-sheet-trials-trial-18-a-track-s.png) |  |
| 48 | sheet-trials | Trial 18: dragged by stalled | ok | [048-sheet-trials-trial-18-dragged-by-stalled.png](shots/048-sheet-trials-trial-18-dragged-by-stalled.png) |  |
| 49 | sheet-trials | Trial 18: Back closes the sheet | ok | [049-sheet-trials-trial-18-back-closes-the-sheet.png](shots/049-sheet-trials-trial-18-back-closes-the-sheet.png) |  |
| 50 | sheet-trials | Trial 19: a track's ⋯ | ok | [050-sheet-trials-trial-19-a-track-s.png](shots/050-sheet-trials-trial-19-a-track-s.png) |  |
| 51 | sheet-trials | Trial 19: dragged by swipe | ok | [051-sheet-trials-trial-19-dragged-by-swipe.png](shots/051-sheet-trials-trial-19-dragged-by-swipe.png) |  |
| 52 | sheet-trials | Trial 20: a track's ⋯ | ok | [052-sheet-trials-trial-20-a-track-s.png](shots/052-sheet-trials-trial-20-a-track-s.png) |  |
| 53 | sheet-trials | Trial 20: dragged by drag | ok | [053-sheet-trials-trial-20-dragged-by-drag.png](shots/053-sheet-trials-trial-20-dragged-by-drag.png) |  |
| 54 | sheet-trials | Trial 21: a track's ⋯ | ok | [054-sheet-trials-trial-21-a-track-s.png](shots/054-sheet-trials-trial-21-a-track-s.png) |  |
| 55 | sheet-trials | Trial 21: dragged by stalled | ok | [055-sheet-trials-trial-21-dragged-by-stalled.png](shots/055-sheet-trials-trial-21-dragged-by-stalled.png) |  |
| 56 | sheet-trials | Trial 21: Back closes the sheet | ok | [056-sheet-trials-trial-21-back-closes-the-sheet.png](shots/056-sheet-trials-trial-21-back-closes-the-sheet.png) |  |
| 57 | sheet-trials | Trial 22: a track's ⋯ | ok | [057-sheet-trials-trial-22-a-track-s.png](shots/057-sheet-trials-trial-22-a-track-s.png) |  |
| 58 | sheet-trials | Trial 22: dragged by swipe | ok | [058-sheet-trials-trial-22-dragged-by-swipe.png](shots/058-sheet-trials-trial-22-dragged-by-swipe.png) |  |
| 59 | sheet-trials | Trial 22: Back closes the sheet | ok | [059-sheet-trials-trial-22-back-closes-the-sheet.png](shots/059-sheet-trials-trial-22-back-closes-the-sheet.png) |  |
| 60 | sheet-trials | Trial 23: a track's ⋯ | ok | [060-sheet-trials-trial-23-a-track-s.png](shots/060-sheet-trials-trial-23-a-track-s.png) |  |
| 61 | sheet-trials | Trial 23: dragged by drag | ok | [061-sheet-trials-trial-23-dragged-by-drag.png](shots/061-sheet-trials-trial-23-dragged-by-drag.png) |  |
| 62 | sheet-trials | Trial 24: a track's ⋯ | ok | [062-sheet-trials-trial-24-a-track-s.png](shots/062-sheet-trials-trial-24-a-track-s.png) |  |
| 63 | sheet-trials | Trial 24: dragged by stalled | ok | [063-sheet-trials-trial-24-dragged-by-stalled.png](shots/063-sheet-trials-trial-24-dragged-by-stalled.png) |  |
| 64 | sheet-trials | Trial 24: Back closes the sheet | ok | [064-sheet-trials-trial-24-back-closes-the-sheet.png](shots/064-sheet-trials-trial-24-back-closes-the-sheet.png) |  |
| 65 | sheet-trials | Trial 25: a track's ⋯ | ok | [065-sheet-trials-trial-25-a-track-s.png](shots/065-sheet-trials-trial-25-a-track-s.png) |  |
| 66 | sheet-trials | Trial 25: dragged by swipe | ok | [066-sheet-trials-trial-25-dragged-by-swipe.png](shots/066-sheet-trials-trial-25-dragged-by-swipe.png) |  |
| 67 | sheet-trials | Trial 26: a track's ⋯ | ok | [067-sheet-trials-trial-26-a-track-s.png](shots/067-sheet-trials-trial-26-a-track-s.png) |  |
| 68 | sheet-trials | Trial 26: dragged by drag | ok | [068-sheet-trials-trial-26-dragged-by-drag.png](shots/068-sheet-trials-trial-26-dragged-by-drag.png) |  |
| 69 | sheet-trials | Trial 27: a track's ⋯ | ok | [069-sheet-trials-trial-27-a-track-s.png](shots/069-sheet-trials-trial-27-a-track-s.png) |  |
| 70 | sheet-trials | Trial 27: dragged by stalled | ok | [070-sheet-trials-trial-27-dragged-by-stalled.png](shots/070-sheet-trials-trial-27-dragged-by-stalled.png) |  |
| 71 | sheet-trials | Trial 27: Back closes the sheet | ok | [071-sheet-trials-trial-27-back-closes-the-sheet.png](shots/071-sheet-trials-trial-27-back-closes-the-sheet.png) |  |
| 72 | sheet-trials | Trial 28: a track's ⋯ | ok | [072-sheet-trials-trial-28-a-track-s.png](shots/072-sheet-trials-trial-28-a-track-s.png) |  |
| 73 | sheet-trials | Trial 28: dragged by swipe | ok | [073-sheet-trials-trial-28-dragged-by-swipe.png](shots/073-sheet-trials-trial-28-dragged-by-swipe.png) |  |
| 74 | sheet-trials | Trial 29: a track's ⋯ | ok | [074-sheet-trials-trial-29-a-track-s.png](shots/074-sheet-trials-trial-29-a-track-s.png) |  |
| 75 | sheet-trials | Trial 29: dragged by drag | ok | [075-sheet-trials-trial-29-dragged-by-drag.png](shots/075-sheet-trials-trial-29-dragged-by-drag.png) |  |
| 76 | sheet-trials | Trial 30: a track's ⋯ | ok | [076-sheet-trials-trial-30-a-track-s.png](shots/076-sheet-trials-trial-30-a-track-s.png) |  |
| 77 | sheet-trials | Trial 30: dragged by stalled | ok | [077-sheet-trials-trial-30-dragged-by-stalled.png](shots/077-sheet-trials-trial-30-dragged-by-stalled.png) |  |
| 78 | sheet-trials | Trial 30: Back closes the sheet | ok | [078-sheet-trials-trial-30-back-closes-the-sheet.png](shots/078-sheet-trials-trial-30-back-closes-the-sheet.png) |  |

## Warnings

- [sheet-trials] swipe: 9 of 10 closed the sheet
- [sheet-trials] drag: 10 of 10 closed the sheet
- [sheet-trials] stalled: 0 of 10 closed the sheet

### Missing native libraries (arm64-only libraries on an x86_64 emulator)

No link errors were logged.

### StrictMode violations: 0 (the app's; by the system's policy for a debuggable app)


### Error-level log lines of the app (top 25)

- 2x `VLC/LibVLC/Util: WARNING: Can't find shared library`
- 1x `VLC: [00007eba69444eb0/1354] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [00007eb9f946b5e0/1354] libvlc input: Your media can't be opened`
- 1x `VLC: [00007eb9f946b5e0/1354] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/obb/'. Check the log for details.`
- 1x `VLC: [00007eba69444eb0/1358] libvlc access: cannot open file /storage/emulated/0/Android/data/ (Permission denied)`
- 1x `VLC: [00007eb9f946b5e0/1358] libvlc input: Your media can't be opened`
- 1x `VLC: [00007eb9f946b5e0/1358] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/data/'. Check the log for details.`
