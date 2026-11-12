# Emulator smoke test: FAILED

- app: `app.nagmedar.debug`, APK `debug`
- installed: versionCode=14010005 minSdk=34 targetSdk=37; versionName=1.0.5; pkgFlags=[ DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP KILL_AFTER_RESTORE LARGE_HEAP ]
- device: model=sdk_gphone64_x86_64, sdk=37, abi=x86_64, build=sdk_gphone64_x86_64-userdebug 17 CE2A.260420.051.A3 16571990 dev-keys, size=Physical size: 2560x1600, density=Physical density: 320
- source: fab4ce73523ecf7bdff10494697e7bdc9fbf98ba (fab4ce73523ecf7bdff10494697e7bdc9fbf98ba, debug build, API 37.0, notice-swipe), run 43322397479
- passes: notice-swipe
- 12 steps in 5 min 43 s; 0 crash/ANR finding(s), 0 driver error(s), 0 StrictMode violation(s), 3 of 9 1.0 checks failed; 0 ANR/crash of the system (the emulator's, not failures)

## 1.0 checks

| pass | step | check | result | seen |
|------|------|-------|--------|------|
| notice-swipe | 6 | the emulator's touch screen takes a finger's raw events | ok | virtio_input_multi_touch_1 (/dev/input/event2), its place from the input reader's viewport: {'logical': [0, 0, 1080, 2340], 'physical': [911, 0, 1649, 1600], 'device': [2560, 1600]} |
| notice-swipe | 6 | Settings opens | ok |  |
| notice-swipe | 8 | Settings has the library's Rescan button | ok | at (395, 1075) |
| notice-swipe | 9 | the Rescan button shows its notice | ok | "Scanning the library again; the list stays as it is meanwhile" |
| notice-swipe | 10 | a notice swiped far, the finger lifted at rest: the raw swipe reached it | **FAILED** | the notice's bar before the swipe: 48 px; the last move sent: (1471, 1418), the swipe's end: (820, 2075); the lift 327 ms after it |
| notice-swipe | 11 | a notice flung, the finger lifted in motion: the raw swipe reached it | **FAILED** | the notice's bar before the swipe: 48 px; the last move sent: (1471, 1418), the swipe's end: (820, 2075); the lift 1 ms after it |
| notice-swipe | 12 | a notice swiped a little, the finger lifted in motion: the raw swipe reached it | **FAILED** | the notice's bar before the swipe: 48 px; the last move sent: (1193, 1418), the swipe's end: (414, 2075); the lift 0 ms after it |
| all | 12 | no film, series, video, Home, TV Lab or Whisper words on the app's screens | ok |  |
| all | 12 | no StrictMode violation of the app (debug build) | ok | 0 in the log, 0 in the dropbox |

## Frames (dumpsys gfxinfo; informational on an emulator, over 40 % janky is flagged)

| pass | step | gesture | frames | janky | 50th | 90th | 95th | 99th |
|------|------|---------|--------|-------|------|------|------|------|

## A notice swiped by raw touch events (1.0.6: the bar's left edge in px, read from the screen; "no bar": it has left)

| pass | the swipe | the lift after the last move | before | 0.3 s | 0.9 s | 1.6 s | in the UI's tree after | must | result |
|------|-----------|------------------------------|--------|-------|-------|-------|------------------------|------|--------|
| notice-swipe | swiped far, the finger lifted at rest | 327 ms | [48 px](shots/010-notice-swipe-notice-swiped-far-the-finger-lifted-at-rest-1.png) | [no bar](shots/010-notice-swipe-notice-swiped-far-the-finger-lifted-at-rest-2.png) | [no bar](shots/010-notice-swipe-notice-swiped-far-the-finger-lifted-at-rest-3.png) | [no bar](shots/010-notice-swipe-notice-swiped-far-the-finger-lifted-at-rest-4.png) | no | leave | **not reached** |
| notice-swipe | flung, the finger lifted in motion | 1 ms | [48 px](shots/011-notice-swipe-notice-flung-the-finger-lifted-in-motion-1.png) | [520 px](shots/011-notice-swipe-notice-flung-the-finger-lifted-in-motion-2.png) | [520 px](shots/011-notice-swipe-notice-flung-the-finger-lifted-in-motion-3.png) | [520 px](shots/011-notice-swipe-notice-flung-the-finger-lifted-in-motion-4.png) | at x 562 | leave | **not reached** |
| notice-swipe | swiped a little, the finger lifted in motion | 0 ms | [48 px](shots/012-notice-swipe-notice-swiped-a-little-the-finger-lifted-in-moti-1.png) | [114 px](shots/012-notice-swipe-notice-swiped-a-little-the-finger-lifted-in-moti-2.png) | [114 px](shots/012-notice-swipe-notice-swiped-a-little-the-finger-lifted-in-moti-3.png) | [114 px](shots/012-notice-swipe-notice-swiped-a-little-the-finger-lifted-in-moti-4.png) | at x 156 | come back | **not reached** |

## The system's ANRs and crashes (the emulator's own: recorded, the step tried once more)

None.

## Steps

| # | pass | step | result | screenshot | note |
|---|------|------|--------|------------|------|
| 1 | notice-swipe | Launch (the walk's media pushed) | ok | [001-notice-swipe-launch-the-walk-s-media-pushed.png](shots/001-notice-swipe-launch-the-walk-s-media-pushed.png) |  |
| 2 | notice-swipe | First run with the media step 1 (Continue) | ok | [002-notice-swipe-first-run-with-the-media-step-1-continue.png](shots/002-notice-swipe-first-run-with-the-media-step-1-continue.png) |  |
| 3 | notice-swipe | First run with the media step 2 (Continue · 2 sources) | ok | [003-notice-swipe-first-run-with-the-media-step-2-continue-2-sourc.png](shots/003-notice-swipe-first-run-with-the-media-step-2-continue-2-sourc.png) |  |
| 4 | notice-swipe | First run with the media step 3 (Continue) | ok | [004-notice-swipe-first-run-with-the-media-step-3-continue.png](shots/004-notice-swipe-first-run-with-the-media-step-3-continue.png) |  |
| 5 | notice-swipe | First run with the media step 4 (Start) | ok | [005-notice-swipe-first-run-with-the-media-step-4-start.png](shots/005-notice-swipe-first-run-with-the-media-step-4-start.png) |  |
| 6 | notice-swipe | After the scan | ok | [006-notice-swipe-after-the-scan.png](shots/006-notice-swipe-after-the-scan.png) |  |
| 7 | notice-swipe | Settings (the animations on) | ok | [007-notice-swipe-settings-the-animations-on.png](shots/007-notice-swipe-settings-the-animations-on.png) |  |
| 8 | notice-swipe | Settings at the library's Rescan button | ok | [008-notice-swipe-settings-at-the-library-s-rescan-button.png](shots/008-notice-swipe-settings-at-the-library-s-rescan-button.png) |  |
| 9 | notice-swipe | The Rescan notice | ok | [009-notice-swipe-the-rescan-notice.png](shots/009-notice-swipe-the-rescan-notice.png) |  |
| 10 | notice-swipe | A notice swiped far, the finger lifted at rest: the UI asked | ok | [010-notice-swipe-a-notice-swiped-far-the-finger-lifted-at-rest-th.png](shots/010-notice-swipe-a-notice-swiped-far-the-finger-lifted-at-rest-th.png) | check failed: a notice swiped far, the finger lifted at rest: the raw swipe reached it |
| 11 | notice-swipe | A notice flung, the finger lifted in motion: the UI asked | ok | [011-notice-swipe-a-notice-flung-the-finger-lifted-in-motion-the-u.png](shots/011-notice-swipe-a-notice-flung-the-finger-lifted-in-motion-the-u.png) | check failed: a notice flung, the finger lifted in motion: the raw swipe reached it |
| 12 | notice-swipe | A notice swiped a little, the finger lifted in motion: the UI asked | ok | [012-notice-swipe-a-notice-swiped-a-little-the-finger-lifted-in-mo.png](shots/012-notice-swipe-a-notice-swiped-a-little-the-finger-lifted-in-mo.png) | check failed: a notice swiped a little, the finger lifted in motion: the raw swipe reached it |

## Warnings

None.

### Missing native libraries (arm64-only libraries on an x86_64 emulator)

No link errors were logged.

### StrictMode violations: 0 (the app's; by the system's policy for a debuggable app)


### Error-level log lines of the app (top 25)

- 2x `VLC/LibVLC/Util: WARNING: Can't find shared library`
- 1x `VLC: [000079e869e4a9f0/13ab] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [000079e889ec4bf0/13ab] libvlc input: Your media can't be opened`
- 1x `VLC: [000079e889ec4bf0/13ab] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/obb/'. Check the log for details.`
- 1x `VLC: [000079e869e433d0/13ae] libvlc access: cannot open file /storage/emulated/0/Android/data/ (Permission denied)`
- 1x `VLC: [000079e889ec4bf0/13ae] libvlc input: Your media can't be opened`
- 1x `VLC: [000079e889ec4bf0/13ae] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/data/'. Check the log for details.`
- 1x `SurfaceSyncGroup: Failed to receive transaction ready in 1000ms. Marking SurfaceSyncGroup(wmsSync-VRI[NagmedarActivity]#0) as ready`
- 1x `SurfaceSyncGroup: Failed to receive transaction ready in 1000ms. Marking SurfaceSyncGroup(VRI[NagmedarActivity]#1) as ready`
- 1x `VLC: [000079e869e46e50/19db] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [000079e889f6e680/19db] libvlc input: Your media can't be opened`
- 1x `VLC: [000079e889f6e680/19db] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/obb/'. Check the log for details.`
- 1x `VLC: [000079e869e46e50/19dd] libvlc access: cannot open file /storage/emulated/0/Android/data/ (Permission denied)`
- 1x `VLC: [000079e889f6e680/19dd] libvlc input: Your media can't be opened`
- 1x `VLC: [000079e889f6e680/19dd] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/data/'. Check the log for details.`
- 1x `VLC: [000079e869e44930/1a77] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [000079e889f78720/1a77] libvlc input: Your media can't be opened`
- 1x `VLC: [000079e889f78720/1a77] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/obb/'. Check the log for details.`
- 1x `VLC: [000079e869e44930/1a79] libvlc access: cannot open file /storage/emulated/0/Android/data/ (Permission denied)`
- 1x `VLC: [000079e889f78720/1a79] libvlc input: Your media can't be opened`
- 1x `VLC: [000079e889f78720/1a79] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/data/'. Check the log for details.`
- 1x `VLC: [000079e869e460d0/1b4e] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [000079e889f8b850/1b4e] libvlc input: Your media can't be opened`
- 1x `VLC: [000079e889f8b850/1b4e] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/obb/'. Check the log for details.`
- 1x `VLC: [000079e869e46e50/1b50] libvlc access: cannot open file /storage/emulated/0/Android/data/ (Permission denied)`
