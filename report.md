# Emulator smoke test: passed

- app: `app.nagmedar.debug`, APK `personal`
- installed: versionCode=14010016 minSdk=34 targetSdk=37; versionName=1.0.8; pkgFlags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP KILL_AFTER_RESTORE LARGE_HEAP ]
- device: model=sdk_gphone64_x86_64, sdk=37, abi=x86_64, build=sdk_gphone64_x86_64-userdebug 17 CE2A.260420.051.A3 16571990 dev-keys, size=Physical size: 2560x1600, density=Physical density: 320
- source: b1c0a9da1fed7ed0055e73a632c4f11444af7aad (story/release-1.0.8, personal build, API 37.0, page-opens), run 43503835478
- passes: page-opens
- 32 steps in 5 min 49 s; 0 crash/ANR finding(s), 0 driver error(s), 0 StrictMode violation(s), 0 of 37 1.0 checks failed; 0 ANR/crash of the system (the emulator's, not failures)

## 1.0 checks

| pass | step | check | result | seen |
|------|------|-------|--------|------|
| page-opens | 8 | the podcast address field | ok |  |
| page-opens | 10 | the feed is followed | ok |  |
| page-opens | 13 | the page the app opens once the show is followed: the page is on screen 2.5 s after it opened (the animations on) | ok | drawn in the page's place: 14.2 % at 0.3 s, 14.2 % at 0.8 s, 14.2 % at 2.5 s; the page in the UI's tree: yes |
| page-opens | 13 | the page the app opens once the show is followed: the bottom bar is shown | ok | its ⋯ label at y 2128 to 2175 of 2340 |
| page-opens | 14 | the page the app opens once the show is followed: the page scrolls under a swipe | ok | "27 - A long titl" moved up by 609 px under a swipe of 842 px (a page that follows the finger: 336 px at least) |
| page-opens | 15 | Back from the show's page shows the Podcasts again (after following) | ok | 1 show(s) listed |
| page-opens | 15 | Back on the Podcasts the bottom bar is shown (after following) | ok | its ⋯ label at y 2128 to 2175 of 2340 |
| page-opens | 15 | a followed show on the Podcasts | ok | 1 listed |
| page-opens | 16 | the show's page, its row tapped: the page is on screen 2.5 s after it opened (the animations on) | ok | drawn in the page's place: 14.1 % at 0.3 s, 14.2 % at 0.8 s, 14.2 % at 2.5 s; the page in the UI's tree: yes |
| page-opens | 16 | the show's page, its row tapped: the bottom bar is shown | ok | its ⋯ label at y 2128 to 2175 of 2340 |
| page-opens | 17 | the show's page, its row tapped: the page scrolls under a swipe | ok | "27 - A long titl" moved up by 724 px under a swipe of 842 px (a page that follows the finger: 336 px at least) |
| page-opens | 18 | Back from the show's page shows the Podcasts again (its row tapped) | ok | 1 show(s) listed |
| page-opens | 18 | Back on the Podcasts the bottom bar is shown (its row tapped) | ok | its ⋯ label at y 2128 to 2175 of 2340 |
| page-opens | 19 | the show's page, its row tapped again: the page is on screen 2.5 s after it opened (the animations on) | ok | drawn in the page's place: 14.2 % at 0.3 s, 14.2 % at 0.8 s, 14.2 % at 2.5 s; the page in the UI's tree: yes |
| page-opens | 19 | the show's page, its row tapped again: the bottom bar is shown | ok | its ⋯ label at y 2128 to 2175 of 2340 |
| page-opens | 20 | the show's page, its row tapped again: the page scrolls under a swipe | ok | "27 - A long titl" moved up by 707 px under a swipe of 842 px (a page that follows the finger: 336 px at least) |
| page-opens | 20 | an episode on the show's page to open | ok | 5 in sight |
| page-opens | 21 | an episode's page over the show's: the page is on screen 2.5 s after it opened (the animations on) | ok | drawn in the page's place: 15.9 % at 0.3 s, 15.9 % at 0.8 s, 15.9 % at 2.5 s; the page in the UI's tree: yes |
| page-opens | 21 | an episode's page over the show's: the bottom bar is shown | ok | its ⋯ label at y 2128 to 2175 of 2340 |
| page-opens | 22 | the show's page again, Back from the episode: the page is on screen 2.5 s after it opened (the animations on) | ok | drawn in the page's place: 12.1 % at 0.3 s, 12.1 % at 0.8 s, 12.1 % at 2.5 s; the page in the UI's tree: yes |
| page-opens | 22 | the show's page again, Back from the episode: the bottom bar is shown | ok | its ⋯ label at y 2128 to 2175 of 2340 |
| page-opens | 23 | the show's page again, Back from the episode: the page scrolls under a swipe | ok | "55 - A long titl" moved up by 646 px under a swipe of 842 px (a page that follows the finger: 336 px at least) |
| page-opens | 24 | Back from the show's page shows the Podcasts again (after the episode) | ok | 1 show(s) listed |
| page-opens | 24 | Back on the Podcasts the bottom bar is shown (after the episode) | ok | its ⋯ label at y 2128 to 2175 of 2340 |
| page-opens | 26 | the shows as covers | ok | 1 tile(s) |
| page-opens | 27 | the show's page, its tile tapped: the page is on screen 2.5 s after it opened (the animations on) | ok | drawn in the page's place: 14.3 % at 0.3 s, 14.2 % at 0.8 s, 14.2 % at 2.5 s; the page in the UI's tree: yes |
| page-opens | 27 | the show's page, its tile tapped: the bottom bar is shown | ok | its ⋯ label at y 2128 to 2175 of 2340 |
| page-opens | 28 | the show's page, its tile tapped: the page scrolls under a swipe | ok | "27 - A long titl" moved up by 707 px under a swipe of 842 px (a page that follows the finger: 336 px at least) |
| page-opens | 29 | Back from the show's page shows the Podcasts again (after the tile) | ok | 1 show(s) listed |
| page-opens | 29 | Back on the Podcasts the bottom bar is shown (after the tile) | ok | its ⋯ label at y 2128 to 2175 of 2340 |
| page-opens | 30 | the show's page, its tile tapped again: the page is on screen 2.5 s after it opened (the animations on) | ok | drawn in the page's place: 14.4 % at 0.3 s, 14.2 % at 0.8 s, 14.2 % at 2.5 s; the page in the UI's tree: yes |
| page-opens | 30 | the show's page, its tile tapped again: the bottom bar is shown | ok | its ⋯ label at y 2128 to 2175 of 2340 |
| page-opens | 31 | the show's page, its tile tapped again: the page scrolls under a swipe | ok | "27 - A long titl" moved up by 754 px under a swipe of 842 px (a page that follows the finger: 336 px at least) |
| page-opens | 32 | Back from the show's page shows the Podcasts again (after the tile, again) | ok | 1 show(s) listed |
| page-opens | 32 | Back on the Podcasts the bottom bar is shown (after the tile, again) | ok | its ⋯ label at y 2128 to 2175 of 2340 |
| page-opens | 32 | the pages were opened with the animations on: by following, from the row twice, an episode, from the tile twice | ok | 7 opening(s) read: the page the app opens once the show is followed; the show's page, its row tapped; the show's page, its row tapped again; an episode's page over the show's; the show's page again, Back from the episode; the show's page, its tile tapped; the show's page, its tile tapped again |
| all | 32 | no film, series, video, Home, TV Lab or Whisper words on the app's screens | ok |  |

## Frames (dumpsys gfxinfo; informational on an emulator, over 40 % janky is flagged)

| pass | step | gesture | frames | janky | 50th | 90th | 95th | 99th |
|------|------|---------|--------|-------|------|------|------|------|

## Pages opened with the animations on (1.0.5: on screen when more than 1 % of the page's place between the bars is drawn, an empty page has 0.0 %; it scrolls when it follows a swipe by 40 % of its length at least)

| pass | page | 0.3 s | 0.8 s | 2.5 s | in the UI's tree | moved under the swipe | result |
|------|------|-------|-------|-------|------------------|-----------------------|--------|
| page-opens | the page the app opens once the show is followed | [14.2 %](shots/013-page-opens-opened-the-page-the-app-opens-once-the-show-is-f-1.png) | [14.2 %](shots/013-page-opens-opened-the-page-the-app-opens-once-the-show-is-f-2.png) | [14.2 %](shots/013-page-opens-opened-the-page-the-app-opens-once-the-show-is-f-3.png) | yes | 609 px | ok |
| page-opens | the show's page, its row tapped | [14.1 %](shots/016-page-opens-opened-the-show-s-page-its-row-tapped-1.png) | [14.2 %](shots/016-page-opens-opened-the-show-s-page-its-row-tapped-2.png) | [14.2 %](shots/016-page-opens-opened-the-show-s-page-its-row-tapped-3.png) | yes | 724 px | ok |
| page-opens | the show's page, its row tapped again | [14.2 %](shots/019-page-opens-opened-the-show-s-page-its-row-tapped-again-1.png) | [14.2 %](shots/019-page-opens-opened-the-show-s-page-its-row-tapped-again-2.png) | [14.2 %](shots/019-page-opens-opened-the-show-s-page-its-row-tapped-again-3.png) | yes | 707 px | ok |
| page-opens | an episode's page over the show's | [15.9 %](shots/021-page-opens-opened-an-episode-s-page-over-the-show-s-1.png) | [15.9 %](shots/021-page-opens-opened-an-episode-s-page-over-the-show-s-2.png) | [15.9 %](shots/021-page-opens-opened-an-episode-s-page-over-the-show-s-3.png) | yes | not swiped | ok |
| page-opens | the show's page again, Back from the episode | [12.1 %](shots/022-page-opens-opened-the-show-s-page-again-back-from-the-episo-1.png) | [12.1 %](shots/022-page-opens-opened-the-show-s-page-again-back-from-the-episo-2.png) | [12.1 %](shots/022-page-opens-opened-the-show-s-page-again-back-from-the-episo-3.png) | yes | 646 px | ok |
| page-opens | the show's page, its tile tapped | [14.3 %](shots/027-page-opens-opened-the-show-s-page-its-tile-tapped-1.png) | [14.2 %](shots/027-page-opens-opened-the-show-s-page-its-tile-tapped-2.png) | [14.2 %](shots/027-page-opens-opened-the-show-s-page-its-tile-tapped-3.png) | yes | 707 px | ok |
| page-opens | the show's page, its tile tapped again | [14.4 %](shots/030-page-opens-opened-the-show-s-page-its-tile-tapped-again-1.png) | [14.2 %](shots/030-page-opens-opened-the-show-s-page-its-tile-tapped-again-2.png) | [14.2 %](shots/030-page-opens-opened-the-show-s-page-its-tile-tapped-again-3.png) | yes | 754 px | ok |

## The system's ANRs and crashes (the emulator's own: recorded, the step tried once more)

None.

## Steps

| # | pass | step | result | screenshot | note |
|---|------|------|--------|------------|------|
| 1 | page-opens | Launch (the walk's media pushed) | ok | [001-page-opens-launch-the-walk-s-media-pushed.png](shots/001-page-opens-launch-the-walk-s-media-pushed.png) |  |
| 2 | page-opens | First run with the media step 1 (Continue) | ok | [002-page-opens-first-run-with-the-media-step-1-continue.png](shots/002-page-opens-first-run-with-the-media-step-1-continue.png) |  |
| 3 | page-opens | First run with the media step 2 (Continue · 2 sources) | ok | [003-page-opens-first-run-with-the-media-step-2-continue-2-sourc.png](shots/003-page-opens-first-run-with-the-media-step-2-continue-2-sourc.png) |  |
| 4 | page-opens | First run with the media step 3 (Continue) | ok | [004-page-opens-first-run-with-the-media-step-3-continue.png](shots/004-page-opens-first-run-with-the-media-step-3-continue.png) |  |
| 5 | page-opens | First run with the media step 4 (Start) | ok | [005-page-opens-first-run-with-the-media-step-4-start.png](shots/005-page-opens-first-run-with-the-media-step-4-start.png) |  |
| 6 | page-opens | After the scan | ok | [006-page-opens-after-the-scan.png](shots/006-page-opens-after-the-scan.png) |  |
| 7 | page-opens | Podcasts (the animations on) | ok | [007-page-opens-podcasts-the-animations-on.png](shots/007-page-opens-podcasts-the-animations-on.png) |  |
| 8 | page-opens | Add a podcast by its address | ok | [008-page-opens-add-a-podcast-by-its-address.png](shots/008-page-opens-add-a-podcast-by-its-address.png) |  |
| 9 | page-opens | The feed's address typed | ok | [009-page-opens-the-feed-s-address-typed.png](shots/009-page-opens-the-feed-s-address-typed.png) |  |
| 10 | page-opens | Subscribe | ok | [010-page-opens-subscribe.png](shots/010-page-opens-subscribe.png) |  |
| 11 | page-opens | Followed | ok | [011-page-opens-followed.png](shots/011-page-opens-followed.png) |  |
| 12 | page-opens | After following: the page the app opened | ok | [012-page-opens-after-following-the-page-the-app-opened.png](shots/012-page-opens-after-following-the-page-the-app-opened.png) |  |
| 13 | page-opens | the page the app opens once the show is followed: the UI asked | ok | [013-page-opens-the-page-the-app-opens-once-the-show-is-followed.png](shots/013-page-opens-the-page-the-app-opens-once-the-show-is-followed.png) |  |
| 14 | page-opens | the page the app opens once the show is followed: swiped up | ok | [014-page-opens-the-page-the-app-opens-once-the-show-is-followed.png](shots/014-page-opens-the-page-the-app-opens-once-the-show-is-followed.png) |  |
| 15 | page-opens | Back on the Podcasts (after following) | ok | [015-page-opens-back-on-the-podcasts-after-following.png](shots/015-page-opens-back-on-the-podcasts-after-following.png) |  |
| 16 | page-opens | the show's page, its row tapped: the UI asked | ok | [016-page-opens-the-show-s-page-its-row-tapped-the-ui-asked.png](shots/016-page-opens-the-show-s-page-its-row-tapped-the-ui-asked.png) |  |
| 17 | page-opens | the show's page, its row tapped: swiped up | ok | [017-page-opens-the-show-s-page-its-row-tapped-swiped-up.png](shots/017-page-opens-the-show-s-page-its-row-tapped-swiped-up.png) |  |
| 18 | page-opens | Back on the Podcasts (its row tapped) | ok | [018-page-opens-back-on-the-podcasts-its-row-tapped.png](shots/018-page-opens-back-on-the-podcasts-its-row-tapped.png) |  |
| 19 | page-opens | the show's page, its row tapped again: the UI asked | ok | [019-page-opens-the-show-s-page-its-row-tapped-again-the-ui-aske.png](shots/019-page-opens-the-show-s-page-its-row-tapped-again-the-ui-aske.png) |  |
| 20 | page-opens | the show's page, its row tapped again: swiped up | ok | [020-page-opens-the-show-s-page-its-row-tapped-again-swiped-up.png](shots/020-page-opens-the-show-s-page-its-row-tapped-again-swiped-up.png) |  |
| 21 | page-opens | an episode's page over the show's: the UI asked | ok | [021-page-opens-an-episode-s-page-over-the-show-s-the-ui-asked.png](shots/021-page-opens-an-episode-s-page-over-the-show-s-the-ui-asked.png) |  |
| 22 | page-opens | the show's page again, Back from the episode: the UI asked | ok | [022-page-opens-the-show-s-page-again-back-from-the-episode-the-.png](shots/022-page-opens-the-show-s-page-again-back-from-the-episode-the-.png) |  |
| 23 | page-opens | the show's page again, Back from the episode: swiped up | ok | [023-page-opens-the-show-s-page-again-back-from-the-episode-swip.png](shots/023-page-opens-the-show-s-page-again-back-from-the-episode-swip.png) |  |
| 24 | page-opens | Back on the Podcasts (after the episode) | ok | [024-page-opens-back-on-the-podcasts-after-the-episode.png](shots/024-page-opens-back-on-the-podcasts-after-the-episode.png) |  |
| 25 | page-opens | The Podcasts' menu | ok | [025-page-opens-the-podcasts-menu.png](shots/025-page-opens-the-podcasts-menu.png) |  |
| 26 | page-opens | Show as covers | ok | [026-page-opens-show-as-covers.png](shots/026-page-opens-show-as-covers.png) |  |
| 27 | page-opens | the show's page, its tile tapped: the UI asked | ok | [027-page-opens-the-show-s-page-its-tile-tapped-the-ui-asked.png](shots/027-page-opens-the-show-s-page-its-tile-tapped-the-ui-asked.png) |  |
| 28 | page-opens | the show's page, its tile tapped: swiped up | ok | [028-page-opens-the-show-s-page-its-tile-tapped-swiped-up.png](shots/028-page-opens-the-show-s-page-its-tile-tapped-swiped-up.png) |  |
| 29 | page-opens | Back on the Podcasts (after the tile) | ok | [029-page-opens-back-on-the-podcasts-after-the-tile.png](shots/029-page-opens-back-on-the-podcasts-after-the-tile.png) |  |
| 30 | page-opens | the show's page, its tile tapped again: the UI asked | ok | [030-page-opens-the-show-s-page-its-tile-tapped-again-the-ui-ask.png](shots/030-page-opens-the-show-s-page-its-tile-tapped-again-the-ui-ask.png) |  |
| 31 | page-opens | the show's page, its tile tapped again: swiped up | ok | [031-page-opens-the-show-s-page-its-tile-tapped-again-swiped-up.png](shots/031-page-opens-the-show-s-page-its-tile-tapped-again-swiped-up.png) |  |
| 32 | page-opens | Back on the Podcasts (after the tile, again) | ok | [032-page-opens-back-on-the-podcasts-after-the-tile-again.png](shots/032-page-opens-back-on-the-podcasts-after-the-tile-again.png) |  |

## Warnings

None.

### Missing native libraries (arm64-only libraries on an x86_64 emulator)

No link errors were logged.

### StrictMode violations: 0 (the app's; by the system's policy for a debuggable app)


### Error-level log lines of the app (top 25)

- 2x `VLC/LibVLC/Util: WARNING: Can't find shared library`
- 1x `VLC: [0000721017a3f1f0/1386] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [00007210b7aa2960/1386] libvlc input: Your media can't be opened`
- 1x `VLC: [00007210b7aa2960/1386] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/obb/'. Check the log for details.`
- 1x `VLC: [0000721017a3bad0/1389] libvlc access: cannot open file /storage/emulated/0/Android/data/ (Permission denied)`
- 1x `VLC: [00007210b7aa2960/1389] libvlc input: Your media can't be opened`
- 1x `VLC: [00007210b7aa2960/1389] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/data/'. Check the log for details.`
- 1x `SurfaceSyncGroup: Failed to receive transaction ready in 1000ms. Marking SurfaceSyncGroup(wmsSync-VRI[NagmedarActivity]#0) as ready`
- 1x `SurfaceSyncGroup: Failed to receive transaction ready in 1000ms. Marking SurfaceSyncGroup(VRI[NagmedarActivity]#1) as ready`
