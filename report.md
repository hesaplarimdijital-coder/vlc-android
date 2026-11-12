# Emulator smoke test: FAILED

- app: `app.nagmedar.debug`, APK `personal`
- installed: versionCode=14010012 minSdk=34 targetSdk=37; versionName=1.0.4; pkgFlags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP KILL_AFTER_RESTORE LARGE_HEAP ]
- device: model=sdk_gphone64_x86_64, sdk=37, abi=x86_64, build=sdk_gphone64_x86_64-userdebug 17 CE2A.260420.051.A3 16571990 dev-keys, size=Physical size: 2560x1600, density=Physical density: 320
- source: 1da17e8f61cbabe58cfb165006c18d78ac52ce42 (exp/page-blank, personal build, API 37.0, page-blank-2), run 43264750372
- passes: page-blank-2
- 34 steps in 6 min 53 s; 0 crash/ANR finding(s), 0 driver error(s), 0 StrictMode violation(s), 10 of 18 1.0 checks failed; 1 ANR/crash of the system (the emulator's, not failures)

## 1.0 checks

| pass | step | check | result | seen |
|------|------|-------|--------|------|
| page-blank-2 | 8 | the Podcasts open | ok |  |
| page-blank-2 | 9 | the podcast address field | ok |  |
| page-blank-2 | 11 | the feed is followed | ok |  |
| page-blank-2 | 16 | [host=swap, nothing played] the podcast's page is on screen 2.5 s after the tap | **FAILED** | drawn in the page's place: 14.3 % at 0.3 s, 0.0 % at 0.8 s, 0.0 % at 2.5 s, 0.0 % after the swipe; 0.0 % once the UI was asked (the page in its tree: NO); back on the list: 4.6 % |
| page-blank-2 | 16 | [host=swap, nothing played] the page moves on screen under a swipe | **FAILED** | 0.0 % of the page's place changed between 2.5 s and after the swipe |
| page-blank-2 | 19 | [host=swap,swipes=const, nothing played] the podcast's page is on screen 2.5 s after the tap | **FAILED** | drawn in the page's place: 11.1 % at 0.3 s, 0.0 % at 0.8 s, 0.0 % at 2.5 s, 0.0 % after the swipe; 0.0 % once the UI was asked (the page in its tree: NO); back on the list: 4.6 % |
| page-blank-2 | 19 | [host=swap,swipes=const, nothing played] the page moves on screen under a swipe | **FAILED** | 0.0 % of the page's place changed between 2.5 s and after the swipe |
| page-blank-2 | 22 | [host=swap,inner=plain, nothing played] the podcast's page is on screen 2.5 s after the tap | **FAILED** | drawn in the page's place: 9.6 % at 0.3 s, 0.0 % at 0.8 s, 0.0 % at 2.5 s, 0.0 % after the swipe; 0.0 % once the UI was asked (the page in its tree: NO); back on the list: 4.6 % |
| page-blank-2 | 22 | [host=swap,inner=plain, nothing played] the page moves on screen under a swipe | **FAILED** | 0.0 % of the page's place changed between 2.5 s and after the swipe |
| page-blank-2 | 25 | [host=swap,swipes=const,inner=plain, nothing played] the podcast's page is on screen 2.5 s after the tap | **FAILED** | drawn in the page's place: 8.7 % at 0.3 s, 0.0 % at 0.8 s, 0.0 % at 2.5 s, 0.0 % after the swipe; 0.0 % once the UI was asked (the page in its tree: NO); back on the list: 4.6 % |
| page-blank-2 | 25 | [host=swap,swipes=const,inner=plain, nothing played] the page moves on screen under a swipe | **FAILED** | 0.0 % of the page's place changed between 2.5 s and after the swipe |
| page-blank-2 | 28 | [host=boxswap, nothing played] the podcast's page is on screen 2.5 s after the tap | ok | drawn in the page's place: 11.2 % at 0.3 s, 14.2 % at 0.8 s, 14.2 % at 2.5 s, 11.9 % after the swipe; 11.9 % once the UI was asked (the page in its tree: yes); back on the list: 4.6 % |
| page-blank-2 | 28 | [host=boxswap, nothing played] the page moves on screen under a swipe | ok | 20.6 % of the page's place changed between 2.5 s and after the swipe |
| page-blank-2 | 31 | [host=stl, nothing played] the podcast's page is on screen 2.5 s after the tap | ok | drawn in the page's place: 4.9 % at 0.3 s, 14.2 % at 0.8 s, 14.2 % at 2.5 s, 11.9 % after the swipe; 11.9 % once the UI was asked (the page in its tree: yes); back on the list: 4.6 % |
| page-blank-2 | 31 | [host=stl, nothing played] the page moves on screen under a swipe | ok | 19.3 % of the page's place changed between 2.5 s and after the swipe |
| page-blank-2 | 34 | [host=swap, nothing played] the podcast's page is on screen 2.5 s after the tap | **FAILED** | drawn in the page's place: 9.6 % at 0.3 s, 0.0 % at 0.8 s, 0.0 % at 2.5 s, 0.0 % after the swipe; 0.0 % once the UI was asked (the page in its tree: NO); back on the list: 4.6 % |
| page-blank-2 | 34 | [host=swap, nothing played] the page moves on screen under a swipe | **FAILED** | 0.0 % of the page's place changed between 2.5 s and after the swipe |
| all | 34 | no film, series, video, Home, TV Lab or Whisper words on the app's screens | ok |  |

## Frames (dumpsys gfxinfo; informational on an emulator, over 40 % janky is flagged)

| pass | step | gesture | frames | janky | 50th | 90th | 95th | 99th |
|------|------|---------|--------|-------|------|------|------|------|

## The system's ANRs and crashes (the emulator's own: recorded, the step tried once more)

| pass | step | what | whose | seen | how |
|------|------|------|-------|------|-----|
| page-blank-2 | 1 | ANR | com.google.android.gms.persistent | Reason: Broadcast of Intent { act=android.intent.action.SIM_STATE_CHANGED flg=0x15000010 xflg=0x4 cmp=com.google.android.gms/.chimera.GmsIntentOperationService$PersistentTrustedReceiver (has extras) } | log |

## Steps

| # | pass | step | result | screenshot | note |
|---|------|------|--------|------------|------|
| 1 | page-blank-2 | Launch (the walk's media pushed) | ok | [001-page-blank-2-launch-the-walk-s-media-pushed.png](shots/001-page-blank-2-launch-the-walk-s-media-pushed.png) |  |
| 2 | page-blank-2 | First run with the media step 1 (Continue) | ok | [002-page-blank-2-first-run-with-the-media-step-1-continue.png](shots/002-page-blank-2-first-run-with-the-media-step-1-continue.png) |  |
| 3 | page-blank-2 | First run with the media step 2 (Continue · 2 sources) | ok | [003-page-blank-2-first-run-with-the-media-step-2-continue-2-sourc.png](shots/003-page-blank-2-first-run-with-the-media-step-2-continue-2-sourc.png) |  |
| 4 | page-blank-2 | First run with the media step 3 (Continue) | ok | [004-page-blank-2-first-run-with-the-media-step-3-continue.png](shots/004-page-blank-2-first-run-with-the-media-step-3-continue.png) |  |
| 5 | page-blank-2 | First run with the media step 4 (Start) | ok | [005-page-blank-2-first-run-with-the-media-step-4-start.png](shots/005-page-blank-2-first-run-with-the-media-step-4-start.png) |  |
| 6 | page-blank-2 | After the scan | ok | [006-page-blank-2-after-the-scan.png](shots/006-page-blank-2-after-the-scan.png) |  |
| 7 | page-blank-2 | Launch (the page that went blank) | ok | [007-page-blank-2-launch-the-page-that-went-blank.png](shots/007-page-blank-2-launch-the-page-that-went-blank.png) |  |
| 8 | page-blank-2 | Podcasts | ok | [008-page-blank-2-podcasts.png](shots/008-page-blank-2-podcasts.png) |  |
| 9 | page-blank-2 | Add a podcast by its address | ok | [009-page-blank-2-add-a-podcast-by-its-address.png](shots/009-page-blank-2-add-a-podcast-by-its-address.png) |  |
| 10 | page-blank-2 | The feed's address typed | ok | [010-page-blank-2-the-feed-s-address-typed.png](shots/010-page-blank-2-the-feed-s-address-typed.png) |  |
| 11 | page-blank-2 | Subscribe | ok | [011-page-blank-2-subscribe.png](shots/011-page-blank-2-subscribe.png) |  |
| 12 | page-blank-2 | Followed | ok | [012-page-blank-2-followed.png](shots/012-page-blank-2-followed.png) |  |
| 13 | page-blank-2 | Back on the Podcasts, the feed followed | ok | [013-page-blank-2-back-on-the-podcasts-the-feed-followed.png](shots/013-page-blank-2-back-on-the-podcasts-the-feed-followed.png) |  |
| 14 | page-blank-2 | [host=swap, nothing played] The Podcasts | ok | [014-page-blank-2-host-swap-nothing-played-the-podcasts.png](shots/014-page-blank-2-host-swap-nothing-played-the-podcasts.png) |  |
| 15 | page-blank-2 | [host=swap, nothing played] The podcast's page, the UI asked | ok | [015-page-blank-2-host-swap-nothing-played-the-podcast-s-page-the-.png](shots/015-page-blank-2-host-swap-nothing-played-the-podcast-s-page-the-.png) |  |
| 16 | page-blank-2 | [host=swap, nothing played] Back on the Podcasts | ok | [016-page-blank-2-host-swap-nothing-played-back-on-the-podcasts.png](shots/016-page-blank-2-host-swap-nothing-played-back-on-the-podcasts.png) | check failed: [host=swap, nothing played] the podcast's page is on screen 2.5 s after the tap; check failed: [host=swap, nothing played] the page moves on screen under a swipe |
| 17 | page-blank-2 | [host=swap,swipes=const, nothing played] The Podcasts | ok | [017-page-blank-2-host-swap-swipes-const-nothing-played-the-podcas.png](shots/017-page-blank-2-host-swap-swipes-const-nothing-played-the-podcas.png) |  |
| 18 | page-blank-2 | [host=swap,swipes=const, nothing played] The podcast's page, the UI asked | ok | [018-page-blank-2-host-swap-swipes-const-nothing-played-the-podcas.png](shots/018-page-blank-2-host-swap-swipes-const-nothing-played-the-podcas.png) |  |
| 19 | page-blank-2 | [host=swap,swipes=const, nothing played] Back on the Podcasts | ok | [019-page-blank-2-host-swap-swipes-const-nothing-played-back-on-th.png](shots/019-page-blank-2-host-swap-swipes-const-nothing-played-back-on-th.png) | check failed: [host=swap,swipes=const, nothing played] the podcast's page is on screen 2.5 s after the tap; check failed: [host=swap,swipes=const, nothing played] the page moves on screen under a swipe |
| 20 | page-blank-2 | [host=swap,inner=plain, nothing played] The Podcasts | ok | [020-page-blank-2-host-swap-inner-plain-nothing-played-the-podcast.png](shots/020-page-blank-2-host-swap-inner-plain-nothing-played-the-podcast.png) |  |
| 21 | page-blank-2 | [host=swap,inner=plain, nothing played] The podcast's page, the UI asked | ok | [021-page-blank-2-host-swap-inner-plain-nothing-played-the-podcast.png](shots/021-page-blank-2-host-swap-inner-plain-nothing-played-the-podcast.png) |  |
| 22 | page-blank-2 | [host=swap,inner=plain, nothing played] Back on the Podcasts | ok | [022-page-blank-2-host-swap-inner-plain-nothing-played-back-on-the.png](shots/022-page-blank-2-host-swap-inner-plain-nothing-played-back-on-the.png) | check failed: [host=swap,inner=plain, nothing played] the podcast's page is on screen 2.5 s after the tap; check failed: [host=swap,inner=plain, nothing played] the page moves on screen under a swipe |
| 23 | page-blank-2 | [host=swap,swipes=const,inner=plain, nothing played] The Podcasts | ok | [023-page-blank-2-host-swap-swipes-const-inner-plain-nothing-playe.png](shots/023-page-blank-2-host-swap-swipes-const-inner-plain-nothing-playe.png) |  |
| 24 | page-blank-2 | [host=swap,swipes=const,inner=plain, nothing played] The podcast's page, the UI asked | ok | [024-page-blank-2-host-swap-swipes-const-inner-plain-nothing-playe.png](shots/024-page-blank-2-host-swap-swipes-const-inner-plain-nothing-playe.png) |  |
| 25 | page-blank-2 | [host=swap,swipes=const,inner=plain, nothing played] Back on the Podcasts | ok | [025-page-blank-2-host-swap-swipes-const-inner-plain-nothing-playe.png](shots/025-page-blank-2-host-swap-swipes-const-inner-plain-nothing-playe.png) | check failed: [host=swap,swipes=const,inner=plain, nothing played] the podcast's page is on screen 2.5 s after the tap; check failed: [host=swap,swipes=const,inner=plain, nothing played] the page moves on screen under a swipe |
| 26 | page-blank-2 | [host=boxswap, nothing played] The Podcasts | ok | [026-page-blank-2-host-boxswap-nothing-played-the-podcasts.png](shots/026-page-blank-2-host-boxswap-nothing-played-the-podcasts.png) |  |
| 27 | page-blank-2 | [host=boxswap, nothing played] The podcast's page, the UI asked | ok | [027-page-blank-2-host-boxswap-nothing-played-the-podcast-s-page-t.png](shots/027-page-blank-2-host-boxswap-nothing-played-the-podcast-s-page-t.png) |  |
| 28 | page-blank-2 | [host=boxswap, nothing played] Back on the Podcasts | ok | [028-page-blank-2-host-boxswap-nothing-played-back-on-the-podcasts.png](shots/028-page-blank-2-host-boxswap-nothing-played-back-on-the-podcasts.png) |  |
| 29 | page-blank-2 | [host=stl, nothing played] The Podcasts | ok | [029-page-blank-2-host-stl-nothing-played-the-podcasts.png](shots/029-page-blank-2-host-stl-nothing-played-the-podcasts.png) |  |
| 30 | page-blank-2 | [host=stl, nothing played] The podcast's page, the UI asked | ok | [030-page-blank-2-host-stl-nothing-played-the-podcast-s-page-the-u.png](shots/030-page-blank-2-host-stl-nothing-played-the-podcast-s-page-the-u.png) |  |
| 31 | page-blank-2 | [host=stl, nothing played] Back on the Podcasts | ok | [031-page-blank-2-host-stl-nothing-played-back-on-the-podcasts.png](shots/031-page-blank-2-host-stl-nothing-played-back-on-the-podcasts.png) |  |
| 32 | page-blank-2 | [host=swap, nothing played] The Podcasts | ok | [032-page-blank-2-host-swap-nothing-played-the-podcasts.png](shots/032-page-blank-2-host-swap-nothing-played-the-podcasts.png) |  |
| 33 | page-blank-2 | [host=swap, nothing played] The podcast's page, the UI asked | ok | [033-page-blank-2-host-swap-nothing-played-the-podcast-s-page-the-.png](shots/033-page-blank-2-host-swap-nothing-played-the-podcast-s-page-the-.png) |  |
| 34 | page-blank-2 | [host=swap, nothing played] Back on the Podcasts | ok | [034-page-blank-2-host-swap-nothing-played-back-on-the-podcasts.png](shots/034-page-blank-2-host-swap-nothing-played-back-on-the-podcasts.png) | check failed: [host=swap, nothing played] the podcast's page is on screen 2.5 s after the tap; check failed: [host=swap, nothing played] the page moves on screen under a swipe |

## Warnings

None.

### Missing native libraries (arm64-only libraries on an x86_64 emulator)

No link errors were logged.

### StrictMode violations: 0 (the app's; by the system's policy for a debuggable app)


### Error-level log lines of the app (top 25)

- 9x `VLC/LibVLC/Util: WARNING: Can't find shared library`
- 7x `SurfaceSyncGroup: Failed to receive transaction ready in 1000ms. Marking SurfaceSyncGroup(wmsSync-VRI[NagmedarActivity]#0) as ready`
- 7x `SurfaceSyncGroup: Failed to receive transaction ready in 1000ms. Marking SurfaceSyncGroup(VRI[NagmedarActivity]#1) as ready`
- 1x `VLC: [00007a2d7522ee50/136a] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [00007a2db52c0b90/136a] libvlc input: Your media can't be opened`
- 1x `VLC: [00007a2db52c0b90/136a] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/obb/'. Check the log for details.`
- 1x `VLC: [00007a2d752328d0/1372] libvlc access: cannot open file /storage/emulated/0/Android/data/ (Permission denied)`
- 1x `VLC: [00007a2db52c0b90/1372] libvlc input: Your media can't be opened`
- 1x `VLC: [00007a2db52c0b90/1372] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/data/'. Check the log for details.`
