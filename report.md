# Emulator smoke test: passed

- app: `app.nagmedar.debug`, APK `personal`
- installed: versionCode=14010016 minSdk=34 targetSdk=37; versionName=1.0.8; pkgFlags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP KILL_AFTER_RESTORE LARGE_HEAP ]
- device: model=sdk_gphone64_x86_64, sdk=37, abi=x86_64, build=sdk_gphone64_x86_64-userdebug 17 CE2A.260420.051.A3 16571990 dev-keys, size=Physical size: 2560x1600, density=Physical density: 320
- source: 96d1d59d63b1c9cd4188785660048d9f8f6bd858 (story/release-1.0.8, personal build, API 37.0, notice-swipe), run 43492272979
- passes: notice-swipe
- 12 steps in 5 min 21 s; 0 crash/ANR finding(s), 0 driver error(s), 0 StrictMode violation(s), 0 of 11 1.0 checks failed; 0 ANR/crash of the system (the emulator's, not failures)

## 1.0 checks

| pass | step | check | result | seen |
|------|------|-------|--------|------|
| notice-swipe | 6 | the emulator's touch screen takes a finger's raw events | ok | virtio_input_multi_touch_1 (/dev/input/event2), its place from the input reader's viewport: {'logical': [0, 0, 1080, 2340], 'physical': [911, 0, 1649, 1600], 'device': [2560, 1600]} |
| notice-swipe | 6 | Settings opens | ok |  |
| notice-swipe | 8 | Settings has the library's Rescan button | ok | at (395, 1245) |
| notice-swipe | 9 | the Rescan button shows its notice | ok | "Scanning the library again; the list stays as it is meanwhile" |
| notice-swipe | 10 | a notice swiped far, the finger lifted at rest: the raw swipe reached it | ok | the notice's bar before the swipe: 48 px; the last move the dispatcher sent: (1471, 1418) of the panel, the swipe's end there: (1471, 1418); the lift 303.4 ms after it (asked: 100 ms at the least) |
| notice-swipe | 10 | a notice swiped far, the finger lifted at rest leaves | ok | its bar's left edge: 48 px before the swipe; no bar, no bar and no bar about 0.3 s, 0.9 s and 1.6 s after the lift; in the UI's tree afterwards: no; notices stay for the system's longer time here: yes |
| notice-swipe | 11 | a notice flung, the finger lifted in motion: the raw swipe reached it | ok | the notice's bar before the swipe: 48 px; the last move the dispatcher sent: (1471, 1418) of the panel, the swipe's end there: (1471, 1418); the lift 0.1 ms after it (asked: 4 ms at the most) |
| notice-swipe | 11 | a notice flung, the finger lifted in motion leaves | ok | its bar's left edge: 48 px before the swipe; no bar, no bar and no bar about 0.3 s, 0.9 s and 1.6 s after the lift; in the UI's tree afterwards: no; notices stay for the system's longer time here: yes |
| notice-swipe | 12 | a notice swiped a little, the finger lifted in motion: the raw swipe reached it | ok | the notice's bar before the swipe: 48 px; the last move the dispatcher sent: (1193, 1418) of the panel, the swipe's end there: (1193, 1418); the lift 0.2 ms after it (asked: 4 ms at the most) |
| notice-swipe | 12 | a notice swiped a little, the finger lifted in motion comes back to its place | ok | its bar's left edge: 48 px before the swipe; 48 px, 48 px and 48 px about 0.3 s, 0.9 s and 1.6 s after the lift; in the UI's tree afterwards: at x 90; notices stay for the system's longer time here: yes |
| all | 12 | no film, series, video, Home, TV Lab or Whisper words on the app's screens | ok |  |

## Frames (dumpsys gfxinfo; informational on an emulator, over 40 % janky is flagged)

| pass | step | gesture | frames | janky | 50th | 90th | 95th | 99th |
|------|------|---------|--------|-------|------|------|------|------|

## A notice swiped by raw touch events (1.0.6: the bar's left edge in px, read from the screen; "no bar": it has left)

| pass | the swipe | the lift after the last move | before | 0.3 s | 0.9 s | 1.6 s | in the UI's tree after | must | result |
|------|-----------|------------------------------|--------|-------|-------|-------|------------------------|------|--------|
| notice-swipe | swiped far, the finger lifted at rest | 303.4 ms | [48 px](shots/010-notice-swipe-notice-swiped-far-the-finger-lifted-at-rest-1.png) | [no bar](shots/010-notice-swipe-notice-swiped-far-the-finger-lifted-at-rest-2.png) | [no bar](shots/010-notice-swipe-notice-swiped-far-the-finger-lifted-at-rest-3.png) | [no bar](shots/010-notice-swipe-notice-swiped-far-the-finger-lifted-at-rest-4.png) | no | leave | ok |
| notice-swipe | flung, the finger lifted in motion | 0.1 ms | [48 px](shots/011-notice-swipe-notice-flung-the-finger-lifted-in-motion-1.png) | [no bar](shots/011-notice-swipe-notice-flung-the-finger-lifted-in-motion-2.png) | [no bar](shots/011-notice-swipe-notice-flung-the-finger-lifted-in-motion-3.png) | [no bar](shots/011-notice-swipe-notice-flung-the-finger-lifted-in-motion-4.png) | no | leave | ok |
| notice-swipe | swiped a little, the finger lifted in motion | 0.2 ms | [48 px](shots/012-notice-swipe-notice-swiped-a-little-the-finger-lifted-in-moti-1.png) | [48 px](shots/012-notice-swipe-notice-swiped-a-little-the-finger-lifted-in-moti-2.png) | [48 px](shots/012-notice-swipe-notice-swiped-a-little-the-finger-lifted-in-moti-3.png) | [48 px](shots/012-notice-swipe-notice-swiped-a-little-the-finger-lifted-in-moti-4.png) | at x 90 | come back | ok |

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
| 10 | notice-swipe | A notice swiped far, the finger lifted at rest: the UI asked | ok | [010-notice-swipe-a-notice-swiped-far-the-finger-lifted-at-rest-th.png](shots/010-notice-swipe-a-notice-swiped-far-the-finger-lifted-at-rest-th.png) |  |
| 11 | notice-swipe | A notice flung, the finger lifted in motion: the UI asked | ok | [011-notice-swipe-a-notice-flung-the-finger-lifted-in-motion-the-u.png](shots/011-notice-swipe-a-notice-flung-the-finger-lifted-in-motion-the-u.png) |  |
| 12 | notice-swipe | A notice swiped a little, the finger lifted in motion: the UI asked | ok | [012-notice-swipe-a-notice-swiped-a-little-the-finger-lifted-in-mo.png](shots/012-notice-swipe-a-notice-swiped-a-little-the-finger-lifted-in-mo.png) |  |

## Warnings

None.

### Missing native libraries (arm64-only libraries on an x86_64 emulator)

No link errors were logged.

### StrictMode violations: 0 (the app's; by the system's policy for a debuggable app)


### Error-level log lines of the app (top 25)

- 2x `VLC/LibVLC/Util: WARNING: Can't find shared library`
- 1x `VLC: [0000745748c493f0/1382] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [0000745778c82800/1382] libvlc input: Your media can't be opened`
- 1x `VLC: [0000745778c82800/1382] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/obb/'. Check the log for details.`
- 1x `VLC: [0000745748c48f70/1386] libvlc access: cannot open file /storage/emulated/0/Android/data/ (Permission denied)`
- 1x `VLC: [0000745778c82800/1386] libvlc input: Your media can't be opened`
- 1x `VLC: [0000745778c82800/1386] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/data/'. Check the log for details.`
- 1x `SurfaceSyncGroup: Failed to receive transaction ready in 1000ms. Marking SurfaceSyncGroup(wmsSync-VRI[NagmedarActivity]#0) as ready`
- 1x `SurfaceSyncGroup: Failed to receive transaction ready in 1000ms. Marking SurfaceSyncGroup(VRI[NagmedarActivity]#1) as ready`
- 1x `VLC: [0000745748c3f9d0/199b] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [0000745778ce4e20/199b] libvlc input: Your media can't be opened`
- 1x `VLC: [0000745778ce4e20/199b] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/obb/'. Check the log for details.`
- 1x `VLC: [0000745748c3f9d0/199d] libvlc access: cannot open file /storage/emulated/0/Android/data/ (Permission denied)`
- 1x `VLC: [0000745778ce4e20/199d] libvlc input: Your media can't be opened`
- 1x `VLC: [0000745778ce4e20/199d] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/data/'. Check the log for details.`
- 1x `VLC: [0000745748c49cf0/1a46] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [0000745778d06030/1a46] libvlc input: Your media can't be opened`
- 1x `VLC: [0000745778d06030/1a46] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/obb/'. Check the log for details.`
- 1x `VLC: [0000745748c4b6d0/1a48] libvlc access: cannot open file /storage/emulated/0/Android/data/ (Permission denied)`
- 1x `VLC: [0000745778d06030/1a48] libvlc input: Your media can't be opened`
- 1x `VLC: [0000745778d06030/1a48] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/data/'. Check the log for details.`
- 1x `VLC: [0000745748c2fb90/1b32] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [0000745778d7e7b0/1b32] libvlc input: Your media can't be opened`
- 1x `VLC: [0000745778d7e7b0/1b32] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/obb/'. Check the log for details.`
- 1x `VLC: [0000745748c2fb90/1b34] libvlc access: cannot open file /storage/emulated/0/Android/data/ (Permission denied)`
