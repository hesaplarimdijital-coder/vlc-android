# Emulator smoke test: FAILED

- app: `app.nagmedar.debug`, APK `personal`
- installed: versionCode=14010011 minSdk=34 targetSdk=37; versionName=1.0.3; pkgFlags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP KILL_AFTER_RESTORE LARGE_HEAP ]
- device: model=sdk_gphone64_x86_64, sdk=37, abi=x86_64, build=sdk_gphone64_x86_64-userdebug 17 CE2A.260420.051.A3 16571990 dev-keys, size=Physical size: 2560x1600, density=Physical density: 320
- source: 563669760910b847eeb9af808067c116bfa7be6f (563669760910b847eeb9af808067c116bfa7be6f, personal build, API 37.0, page-blank), run 43263760366
- passes: page-blank
- 45 steps in 8 min 28 s; 0 crash/ANR finding(s), 0 driver error(s), 0 StrictMode violation(s), 20 of 26 1.0 checks failed; 1 ANR/crash of the system (the emulator's, not failures)

## 1.0 checks

| pass | step | check | result | seen |
|------|------|-------|--------|------|
| page-blank | 8 | the Podcasts open | ok |  |
| page-blank | 9 | the podcast address field | ok |  |
| page-blank | 11 | the feed is followed | ok |  |
| page-blank | 16 | [host=swap, nothing played] the podcast's page is on screen 2.5 s after the tap | **FAILED** | drawn in the page's place: 14.2 % at 0.3 s, 0.0 % at 0.8 s, 0.0 % at 2.5 s, 0.0 % after the swipe; 0.0 % once the UI was asked (the page in its tree: NO); back on the list: 4.6 % |
| page-blank | 16 | [host=swap, nothing played] the page moves on screen under a swipe | **FAILED** | 0.0 % of the page's place changed between 2.5 s and after the swipe |
| page-blank | 19 | [host=swap,glass=solid, nothing played] the podcast's page is on screen 2.5 s after the tap | **FAILED** | drawn in the page's place: 14.2 % at 0.3 s, 0.0 % at 0.8 s, 0.0 % at 2.5 s, 0.0 % after the swipe; 0.0 % once the UI was asked (the page in its tree: NO); back on the list: 4.6 % |
| page-blank | 19 | [host=swap,glass=solid, nothing played] the page moves on screen under a swipe | **FAILED** | 0.0 % of the page's place changed between 2.5 s and after the swipe |
| page-blank | 22 | [host=stl, nothing played] the podcast's page is on screen 2.5 s after the tap | **FAILED** | drawn in the page's place: 14.5 % at 0.3 s, 0.0 % at 0.8 s, 0.0 % at 2.5 s, 0.0 % after the swipe; 0.0 % once the UI was asked (the page in its tree: NO); back on the list: 4.6 % |
| page-blank | 22 | [host=stl, nothing played] the page moves on screen under a swipe | **FAILED** | 0.0 % of the page's place changed between 2.5 s and after the swipe |
| page-blank | 25 | [host=box, nothing played] the podcast's page is on screen 2.5 s after the tap | **FAILED** | drawn in the page's place: 14.2 % at 0.3 s, 0.0 % at 0.8 s, 0.0 % at 2.5 s, 0.0 % after the swipe; 0.0 % once the UI was asked (the page in its tree: NO); back on the list: 4.6 % |
| page-blank | 25 | [host=box, nothing played] the page moves on screen under a swipe | **FAILED** | 0.0 % of the page's place changed between 2.5 s and after the swipe |
| page-blank | 28 | [host=redraw, nothing played] the podcast's page is on screen 2.5 s after the tap | **FAILED** | drawn in the page's place: 14.2 % at 0.3 s, 0.0 % at 0.8 s, 0.0 % at 2.5 s, 0.0 % after the swipe; 0.0 % once the UI was asked (the page in its tree: NO); back on the list: 4.6 % |
| page-blank | 28 | [host=redraw, nothing played] the page moves on screen under a swipe | **FAILED** | 0.0 % of the page's place changed between 2.5 s and after the swipe |
| page-blank | 31 | [host=redraw-host, nothing played] the podcast's page is on screen 2.5 s after the tap | **FAILED** | drawn in the page's place: 14.2 % at 0.3 s, 0.0 % at 0.8 s, 0.0 % at 2.5 s, 0.0 % after the swipe; 0.0 % once the UI was asked (the page in its tree: NO); back on the list: 4.6 % |
| page-blank | 31 | [host=redraw-host, nothing played] the page moves on screen under a swipe | **FAILED** | 0.0 % of the page's place changed between 2.5 s and after the swipe |
| page-blank | 34 | [host=redraw-detail, nothing played] the podcast's page is on screen 2.5 s after the tap | **FAILED** | drawn in the page's place: 14.2 % at 0.3 s, 0.0 % at 0.8 s, 0.0 % at 2.5 s, 0.0 % after the swipe; 0.0 % once the UI was asked (the page in its tree: NO); back on the list: 4.6 % |
| page-blank | 34 | [host=redraw-detail, nothing played] the page moves on screen under a swipe | **FAILED** | 0.0 % of the page's place changed between 2.5 s and after the swipe |
| page-blank | 37 | [host=swap, nothing played] the podcast's page is on screen 2.5 s after the tap | **FAILED** | drawn in the page's place: 14.3 % at 0.3 s, 0.0 % at 0.8 s, 0.0 % at 2.5 s, 0.0 % after the swipe; 0.0 % once the UI was asked (the page in its tree: NO); back on the list: 4.6 % |
| page-blank | 37 | [host=swap, nothing played] the page moves on screen under a swipe | **FAILED** | 0.0 % of the page's place changed between 2.5 s and after the swipe |
| page-blank | 38 | a track in the Music's list | ok |  |
| page-blank | 39 | the mini player shows the track | ok |  |
| page-blank | 42 | [host=swap, a track plays] the podcast's page is on screen 2.5 s after the tap | **FAILED** | drawn in the page's place: 13.8 % at 0.3 s, 0.5 % at 0.8 s, 0.5 % at 2.5 s, 0.5 % after the swipe; 0.5 % once the UI was asked (the page in its tree: NO); back on the list: 5.1 % |
| page-blank | 42 | [host=swap, a track plays] the page moves on screen under a swipe | **FAILED** | 0.0 % of the page's place changed between 2.5 s and after the swipe |
| page-blank | 45 | [host=swap, a track paused] the podcast's page is on screen 2.5 s after the tap | **FAILED** | drawn in the page's place: 13.9 % at 0.3 s, 0.5 % at 0.8 s, 0.5 % at 2.5 s, 0.5 % after the swipe; 0.5 % once the UI was asked (the page in its tree: NO); back on the list: 5.1 % |
| page-blank | 45 | [host=swap, a track paused] the page moves on screen under a swipe | **FAILED** | 0.0 % of the page's place changed between 2.5 s and after the swipe |
| all | 45 | no film, series, video, Home, TV Lab or Whisper words on the app's screens | ok |  |

## Frames (dumpsys gfxinfo; informational on an emulator, over 40 % janky is flagged)

| pass | step | gesture | frames | janky | 50th | 90th | 95th | 99th |
|------|------|---------|--------|-------|------|------|------|------|

## The system's ANRs and crashes (the emulator's own: recorded, the step tried once more)

| pass | step | what | whose | seen | how |
|------|------|------|-------|------|-----|
| page-blank | 1 | ANR | com.google.android.gms.persistent | Reason: Broadcast of Intent { act=android.intent.action.SIM_STATE_CHANGED flg=0x15000010 xflg=0x4 cmp=com.google.android.gms/.chimera.GmsIntentOperationService$PersistentTrustedReceiver (has extras) } | log |

## Steps

| # | pass | step | result | screenshot | note |
|---|------|------|--------|------------|------|
| 1 | page-blank | Launch (the walk's media pushed) | ok | [001-page-blank-launch-the-walk-s-media-pushed.png](shots/001-page-blank-launch-the-walk-s-media-pushed.png) |  |
| 2 | page-blank | First run with the media step 1 (Continue) | ok | [002-page-blank-first-run-with-the-media-step-1-continue.png](shots/002-page-blank-first-run-with-the-media-step-1-continue.png) |  |
| 3 | page-blank | First run with the media step 2 (Continue · 2 sources) | ok | [003-page-blank-first-run-with-the-media-step-2-continue-2-sourc.png](shots/003-page-blank-first-run-with-the-media-step-2-continue-2-sourc.png) |  |
| 4 | page-blank | First run with the media step 3 (Continue) | ok | [004-page-blank-first-run-with-the-media-step-3-continue.png](shots/004-page-blank-first-run-with-the-media-step-3-continue.png) |  |
| 5 | page-blank | First run with the media step 4 (Start) | ok | [005-page-blank-first-run-with-the-media-step-4-start.png](shots/005-page-blank-first-run-with-the-media-step-4-start.png) |  |
| 6 | page-blank | After the scan | ok | [006-page-blank-after-the-scan.png](shots/006-page-blank-after-the-scan.png) |  |
| 7 | page-blank | Launch (the page that went blank) | ok | [007-page-blank-launch-the-page-that-went-blank.png](shots/007-page-blank-launch-the-page-that-went-blank.png) |  |
| 8 | page-blank | Podcasts | ok | [008-page-blank-podcasts.png](shots/008-page-blank-podcasts.png) |  |
| 9 | page-blank | Add a podcast by its address | ok | [009-page-blank-add-a-podcast-by-its-address.png](shots/009-page-blank-add-a-podcast-by-its-address.png) |  |
| 10 | page-blank | The feed's address typed | ok | [010-page-blank-the-feed-s-address-typed.png](shots/010-page-blank-the-feed-s-address-typed.png) |  |
| 11 | page-blank | Subscribe | ok | [011-page-blank-subscribe.png](shots/011-page-blank-subscribe.png) |  |
| 12 | page-blank | Followed | ok | [012-page-blank-followed.png](shots/012-page-blank-followed.png) |  |
| 13 | page-blank | Back on the Podcasts, the feed followed | ok | [013-page-blank-back-on-the-podcasts-the-feed-followed.png](shots/013-page-blank-back-on-the-podcasts-the-feed-followed.png) |  |
| 14 | page-blank | [host=swap, nothing played] The Podcasts | ok | [014-page-blank-host-swap-nothing-played-the-podcasts.png](shots/014-page-blank-host-swap-nothing-played-the-podcasts.png) |  |
| 15 | page-blank | [host=swap, nothing played] The podcast's page, the UI asked | ok | [015-page-blank-host-swap-nothing-played-the-podcast-s-page-the-.png](shots/015-page-blank-host-swap-nothing-played-the-podcast-s-page-the-.png) |  |
| 16 | page-blank | [host=swap, nothing played] Back on the Podcasts | ok | [016-page-blank-host-swap-nothing-played-back-on-the-podcasts.png](shots/016-page-blank-host-swap-nothing-played-back-on-the-podcasts.png) | check failed: [host=swap, nothing played] the podcast's page is on screen 2.5 s after the tap; check failed: [host=swap, nothing played] the page moves on screen under a swipe |
| 17 | page-blank | [host=swap,glass=solid, nothing played] The Podcasts | ok | [017-page-blank-host-swap-glass-solid-nothing-played-the-podcast.png](shots/017-page-blank-host-swap-glass-solid-nothing-played-the-podcast.png) |  |
| 18 | page-blank | [host=swap,glass=solid, nothing played] The podcast's page, the UI asked | ok | [018-page-blank-host-swap-glass-solid-nothing-played-the-podcast.png](shots/018-page-blank-host-swap-glass-solid-nothing-played-the-podcast.png) |  |
| 19 | page-blank | [host=swap,glass=solid, nothing played] Back on the Podcasts | ok | [019-page-blank-host-swap-glass-solid-nothing-played-back-on-the.png](shots/019-page-blank-host-swap-glass-solid-nothing-played-back-on-the.png) | check failed: [host=swap,glass=solid, nothing played] the podcast's page is on screen 2.5 s after the tap; check failed: [host=swap,glass=solid, nothing played] the page moves on screen under a swipe |
| 20 | page-blank | [host=stl, nothing played] The Podcasts | ok | [020-page-blank-host-stl-nothing-played-the-podcasts.png](shots/020-page-blank-host-stl-nothing-played-the-podcasts.png) |  |
| 21 | page-blank | [host=stl, nothing played] The podcast's page, the UI asked | ok | [021-page-blank-host-stl-nothing-played-the-podcast-s-page-the-u.png](shots/021-page-blank-host-stl-nothing-played-the-podcast-s-page-the-u.png) |  |
| 22 | page-blank | [host=stl, nothing played] Back on the Podcasts | ok | [022-page-blank-host-stl-nothing-played-back-on-the-podcasts.png](shots/022-page-blank-host-stl-nothing-played-back-on-the-podcasts.png) | check failed: [host=stl, nothing played] the podcast's page is on screen 2.5 s after the tap; check failed: [host=stl, nothing played] the page moves on screen under a swipe |
| 23 | page-blank | [host=box, nothing played] The Podcasts | ok | [023-page-blank-host-box-nothing-played-the-podcasts.png](shots/023-page-blank-host-box-nothing-played-the-podcasts.png) |  |
| 24 | page-blank | [host=box, nothing played] The podcast's page, the UI asked | ok | [024-page-blank-host-box-nothing-played-the-podcast-s-page-the-u.png](shots/024-page-blank-host-box-nothing-played-the-podcast-s-page-the-u.png) |  |
| 25 | page-blank | [host=box, nothing played] Back on the Podcasts | ok | [025-page-blank-host-box-nothing-played-back-on-the-podcasts.png](shots/025-page-blank-host-box-nothing-played-back-on-the-podcasts.png) | check failed: [host=box, nothing played] the podcast's page is on screen 2.5 s after the tap; check failed: [host=box, nothing played] the page moves on screen under a swipe |
| 26 | page-blank | [host=redraw, nothing played] The Podcasts | ok | [026-page-blank-host-redraw-nothing-played-the-podcasts.png](shots/026-page-blank-host-redraw-nothing-played-the-podcasts.png) |  |
| 27 | page-blank | [host=redraw, nothing played] The podcast's page, the UI asked | ok | [027-page-blank-host-redraw-nothing-played-the-podcast-s-page-th.png](shots/027-page-blank-host-redraw-nothing-played-the-podcast-s-page-th.png) |  |
| 28 | page-blank | [host=redraw, nothing played] Back on the Podcasts | ok | [028-page-blank-host-redraw-nothing-played-back-on-the-podcasts.png](shots/028-page-blank-host-redraw-nothing-played-back-on-the-podcasts.png) | check failed: [host=redraw, nothing played] the podcast's page is on screen 2.5 s after the tap; check failed: [host=redraw, nothing played] the page moves on screen under a swipe |
| 29 | page-blank | [host=redraw-host, nothing played] The Podcasts | ok | [029-page-blank-host-redraw-host-nothing-played-the-podcasts.png](shots/029-page-blank-host-redraw-host-nothing-played-the-podcasts.png) |  |
| 30 | page-blank | [host=redraw-host, nothing played] The podcast's page, the UI asked | ok | [030-page-blank-host-redraw-host-nothing-played-the-podcast-s-pa.png](shots/030-page-blank-host-redraw-host-nothing-played-the-podcast-s-pa.png) |  |
| 31 | page-blank | [host=redraw-host, nothing played] Back on the Podcasts | ok | [031-page-blank-host-redraw-host-nothing-played-back-on-the-podc.png](shots/031-page-blank-host-redraw-host-nothing-played-back-on-the-podc.png) | check failed: [host=redraw-host, nothing played] the podcast's page is on screen 2.5 s after the tap; check failed: [host=redraw-host, nothing played] the page moves on screen under a swipe |
| 32 | page-blank | [host=redraw-detail, nothing played] The Podcasts | ok | [032-page-blank-host-redraw-detail-nothing-played-the-podcasts.png](shots/032-page-blank-host-redraw-detail-nothing-played-the-podcasts.png) |  |
| 33 | page-blank | [host=redraw-detail, nothing played] The podcast's page, the UI asked | ok | [033-page-blank-host-redraw-detail-nothing-played-the-podcast-s-.png](shots/033-page-blank-host-redraw-detail-nothing-played-the-podcast-s-.png) |  |
| 34 | page-blank | [host=redraw-detail, nothing played] Back on the Podcasts | ok | [034-page-blank-host-redraw-detail-nothing-played-back-on-the-po.png](shots/034-page-blank-host-redraw-detail-nothing-played-back-on-the-po.png) | check failed: [host=redraw-detail, nothing played] the podcast's page is on screen 2.5 s after the tap; check failed: [host=redraw-detail, nothing played] the page moves on screen under a swipe |
| 35 | page-blank | [host=swap, nothing played] The Podcasts | ok | [035-page-blank-host-swap-nothing-played-the-podcasts.png](shots/035-page-blank-host-swap-nothing-played-the-podcasts.png) |  |
| 36 | page-blank | [host=swap, nothing played] The podcast's page, the UI asked | ok | [036-page-blank-host-swap-nothing-played-the-podcast-s-page-the-.png](shots/036-page-blank-host-swap-nothing-played-the-podcast-s-page-the-.png) |  |
| 37 | page-blank | [host=swap, nothing played] Back on the Podcasts | ok | [037-page-blank-host-swap-nothing-played-back-on-the-podcasts.png](shots/037-page-blank-host-swap-nothing-played-back-on-the-podcasts.png) | check failed: [host=swap, nothing played] the podcast's page is on screen 2.5 s after the tap; check failed: [host=swap, nothing played] the page moves on screen under a swipe |
| 38 | page-blank | Music | ok | [038-page-blank-music.png](shots/038-page-blank-music.png) |  |
| 39 | page-blank | Play Profile track 1.1 | ok | [039-page-blank-play-profile-track-1-1.png](shots/039-page-blank-play-profile-track-1-1.png) |  |
| 40 | page-blank | [host=swap, a track plays] The Podcasts | ok | [040-page-blank-host-swap-a-track-plays-the-podcasts.png](shots/040-page-blank-host-swap-a-track-plays-the-podcasts.png) |  |
| 41 | page-blank | [host=swap, a track plays] The podcast's page, the UI asked | ok | [041-page-blank-host-swap-a-track-plays-the-podcast-s-page-the-u.png](shots/041-page-blank-host-swap-a-track-plays-the-podcast-s-page-the-u.png) |  |
| 42 | page-blank | [host=swap, a track plays] Back on the Podcasts | ok | [042-page-blank-host-swap-a-track-plays-back-on-the-podcasts.png](shots/042-page-blank-host-swap-a-track-plays-back-on-the-podcasts.png) | check failed: [host=swap, a track plays] the podcast's page is on screen 2.5 s after the tap; check failed: [host=swap, a track plays] the page moves on screen under a swipe |
| 43 | page-blank | [host=swap, a track paused] The Podcasts | ok | [043-page-blank-host-swap-a-track-paused-the-podcasts.png](shots/043-page-blank-host-swap-a-track-paused-the-podcasts.png) |  |
| 44 | page-blank | [host=swap, a track paused] The podcast's page, the UI asked | ok | [044-page-blank-host-swap-a-track-paused-the-podcast-s-page-the-.png](shots/044-page-blank-host-swap-a-track-paused-the-podcast-s-page-the-.png) |  |
| 45 | page-blank | [host=swap, a track paused] Back on the Podcasts | ok | [045-page-blank-host-swap-a-track-paused-back-on-the-podcasts.png](shots/045-page-blank-host-swap-a-track-paused-back-on-the-podcasts.png) | check failed: [host=swap, a track paused] the podcast's page is on screen 2.5 s after the tap; check failed: [host=swap, a track paused] the page moves on screen under a swipe |

## Warnings

None.

### Missing native libraries (arm64-only libraries on an x86_64 emulator)

No link errors were logged.

### StrictMode violations: 0 (the app's; by the system's policy for a debuggable app)


### Error-level log lines of the app (top 25)

- 11x `VLC/LibVLC/Util: WARNING: Can't find shared library`
- 1x `VLC: [000075e5484492d0/13be] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [000075e5c8499940/13be] libvlc input: Your media can't be opened`
- 1x `VLC: [000075e5c8499940/13be] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/obb/'. Check the log for details.`
- 1x `VLC: [000075e5484385f0/13c1] libvlc access: cannot open file /storage/emulated/0/Android/data/ (Permission denied)`
- 1x `VLC: [000075e5c8499940/13c1] libvlc input: Your media can't be opened`
- 1x `VLC: [000075e5c8499940/13c1] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/data/'. Check the log for details.`
- 1x `FrameTracker: force finish cuj, time out: J<IME_INSETS_SHOW_ANIMATION::0@1@app.nagmedar.debug>`
- 1x `ActivityThread: Failed to find provider info for androidx.car.app.connection`
