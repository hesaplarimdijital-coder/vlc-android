# Emulator smoke test: FAILED

- app: `app.nagmedar.debug`, APK `debug`
- installed: versionCode=14010003 minSdk=34 targetSdk=37; versionName=1.0.3; pkgFlags=[ DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP KILL_AFTER_RESTORE LARGE_HEAP ]
- device: model=sdk_gphone64_x86_64, sdk=37, abi=x86_64, build=sdk_gphone64_x86_64-userdebug 17 CE2A.260420.051.A3 16571990 dev-keys, size=Physical size: 2560x1600, density=Physical density: 320
- source: 563669760910b847eeb9af808067c116bfa7be6f (master, debug build, API 37.0, back-button), run 43217588211
- passes: back-button
- 30 steps in 6 min 16 s; 0 crash/ANR finding(s), 0 driver error(s), 0 StrictMode violation(s), 3 of 21 1.0 checks failed; 0 ANR/crash of the system (the emulator's, not failures)

## 1.0 checks

| pass | step | check | result | seen |
|------|------|-------|--------|------|
| back-button | 8 | the Podcasts open | ok |  |
| back-button | 9 | the podcast address field | ok |  |
| back-button | 11 | the feed is followed | ok |  |
| back-button | 12 | an episode on the podcast's page | ok |  |
| back-button | 12 | the episode's play button | ok |  |
| back-button | 13 | the episode's play button does not move | ok | first (42, 643, 708, 769) at 2.1 s; then (42, 643, 708, 769) at +3.6 s; (42, 643, 708, 769) at +6.6 s |
| back-button | 15 | the episode's page stays whole while the system's Back button is held | **FAILED** | of the page differs from the page at rest: 0.0 % a tap's length into the press, 21.7 % a second on (the button: back of com.google.android.apps.nexuslauncher at 228, 2337) |
| back-button | 15 | the system's Back button closes the episode's page | ok | held and let go: closed; tapped: not needed |
| back-button | 17 | the podcast's page stays whole while the system's Back button is held | **FAILED** | of the page differs from the page at rest: 0.0 % a tap's length into the press, 19.8 % a second on (the button: back of com.google.android.apps.nexuslauncher at 228, 2337) |
| back-button | 17 | the system's Back button closes the podcast's page | ok | held and let go: closed; tapped: not needed |
| back-button | 18 | the Podcasts open | ok |  |
| back-button | 18 | a followed podcast | ok |  |
| back-button | 22 | an episode that plays is not on the air in the bar: a seek bar with its times | ok | the bar's line: 0:31 Track position −0:29 |
| back-button | 26 | a track in the Music's list | ok |  |
| back-button | 27 | the mini player shows the track | ok |  |
| back-button | 28 | a tap on the mini player opens the full player | ok |  |
| back-button | 30 | the full player stays whole while the system's Back button is held | **FAILED** | of the page differs from the page at rest: 0.0 % a tap's length into the press, 57.1 % a second on (the button: back of com.google.android.apps.nexuslauncher at 228, 2337) |
| back-button | 30 | the system's Back button closes the full player | ok | held and let go: closed; tapped: not needed |
| back-button | 30 | the system's Back button was pressed on the episode's page, the podcast's and the full player | ok | pressed on: the episode's page, the podcast's page, the full player |
| all | 30 | no film, series, video, Home, TV Lab or Whisper words on the app's screens | ok |  |
| all | 30 | no StrictMode violation of the app (debug build) | ok | 0 in the log, 0 in the dropbox |

## Frames (dumpsys gfxinfo; informational on an emulator, over 40 % janky is flagged)

| pass | step | gesture | frames | janky | 50th | 90th | 95th | 99th |
|------|------|---------|--------|-------|------|------|------|------|

## The system's Back button (1.0.4: touched as the owner touches it; whole: at most 2 % of the page differs)

The system: navigation_mode=0; flags: none listed

| pass | page | the button | at rest | held, a tap's length | differs | held, a second on | differs | let go | tapped |
|------|------|------------|---------|----------------------|---------|-------------------|---------|--------|--------|
| back-button | the episode's page | back of com.google.android.apps.nexuslauncher at 228, 2337 | [014-back-button-before-the-back-button-the-episode-s-page.png](shots/014-back-button-before-the-back-button-the-episode-s-page.png) | [015-back-button-back-button-held-on-the-episode-s-page-tap.png](shots/015-back-button-back-button-held-on-the-episode-s-page-tap.png) | 0.0 % | [015-back-button-back-button-held-on-the-episode-s-page-long.png](shots/015-back-button-back-button-held-on-the-episode-s-page-long.png) | 21.7 % | closed | not needed |
| back-button | the podcast's page | back of com.google.android.apps.nexuslauncher at 228, 2337 | [016-back-button-before-the-back-button-the-podcast-s-page.png](shots/016-back-button-before-the-back-button-the-podcast-s-page.png) | [017-back-button-back-button-held-on-the-podcast-s-page-tap.png](shots/017-back-button-back-button-held-on-the-podcast-s-page-tap.png) | 0.0 % | [017-back-button-back-button-held-on-the-podcast-s-page-long.png](shots/017-back-button-back-button-held-on-the-podcast-s-page-long.png) | 19.8 % | closed | not needed |
| back-button | the full player | back of com.google.android.apps.nexuslauncher at 228, 2337 | [029-back-button-before-the-back-button-the-full-player.png](shots/029-back-button-before-the-back-button-the-full-player.png) | [030-back-button-back-button-held-on-the-full-player-tap.png](shots/030-back-button-back-button-held-on-the-full-player-tap.png) | 0.0 % | [030-back-button-back-button-held-on-the-full-player-long.png](shots/030-back-button-back-button-held-on-the-full-player-long.png) | 57.1 % | closed | not needed |

## The system's ANRs and crashes (the emulator's own: recorded, the step tried once more)

None.

## Steps

| # | pass | step | result | screenshot | note |
|---|------|------|--------|------------|------|
| 1 | back-button | Launch (the walk's media pushed) | ok | [001-back-button-launch-the-walk-s-media-pushed.png](shots/001-back-button-launch-the-walk-s-media-pushed.png) |  |
| 2 | back-button | First run with the media step 1 (Continue) | ok | [002-back-button-first-run-with-the-media-step-1-continue.png](shots/002-back-button-first-run-with-the-media-step-1-continue.png) |  |
| 3 | back-button | First run with the media step 2 (Continue · 2 sources) | ok | [003-back-button-first-run-with-the-media-step-2-continue-2-sourc.png](shots/003-back-button-first-run-with-the-media-step-2-continue-2-sourc.png) |  |
| 4 | back-button | First run with the media step 3 (Continue) | ok | [004-back-button-first-run-with-the-media-step-3-continue.png](shots/004-back-button-first-run-with-the-media-step-3-continue.png) |  |
| 5 | back-button | First run with the media step 4 (Start) | ok | [005-back-button-first-run-with-the-media-step-4-start.png](shots/005-back-button-first-run-with-the-media-step-4-start.png) |  |
| 6 | back-button | After the scan | ok | [006-back-button-after-the-scan.png](shots/006-back-button-after-the-scan.png) |  |
| 7 | back-button | Launch (the Back button) | ok | [007-back-button-launch-the-back-button.png](shots/007-back-button-launch-the-back-button.png) |  |
| 8 | back-button | Podcasts | ok | [008-back-button-podcasts.png](shots/008-back-button-podcasts.png) |  |
| 9 | back-button | Add a podcast by its address | ok | [009-back-button-add-a-podcast-by-its-address.png](shots/009-back-button-add-a-podcast-by-its-address.png) |  |
| 10 | back-button | The feed's address typed | ok | [010-back-button-the-feed-s-address-typed.png](shots/010-back-button-the-feed-s-address-typed.png) |  |
| 11 | back-button | Subscribe | ok | [011-back-button-subscribe.png](shots/011-back-button-subscribe.png) |  |
| 12 | back-button | Followed | ok | [012-back-button-followed.png](shots/012-back-button-followed.png) |  |
| 13 | back-button | Episode page (the play button watched) | ok | [013-back-button-episode-page-the-play-button-watched.png](shots/013-back-button-episode-page-the-play-button-watched.png) |  |
| 14 | back-button | Before the Back button: the episode's page | ok | [014-back-button-before-the-back-button-the-episode-s-page.png](shots/014-back-button-before-the-back-button-the-episode-s-page.png) |  |
| 15 | back-button | The Back button held on the episode's page, then let go | ok | [015-back-button-the-back-button-held-on-the-episode-s-page-then-.png](shots/015-back-button-the-back-button-held-on-the-episode-s-page-then-.png) | check failed: the episode's page stays whole while the system's Back button is held |
| 16 | back-button | Before the Back button: the podcast's page | ok | [016-back-button-before-the-back-button-the-podcast-s-page.png](shots/016-back-button-before-the-back-button-the-podcast-s-page.png) |  |
| 17 | back-button | The Back button held on the podcast's page, then let go | ok | [017-back-button-the-back-button-held-on-the-podcast-s-page-then-.png](shots/017-back-button-the-back-button-held-on-the-podcast-s-page-then-.png) | check failed: the podcast's page stays whole while the system's Back button is held |
| 18 | back-button | Podcasts | ok | [018-back-button-podcasts.png](shots/018-back-button-podcasts.png) |  |
| 19 | back-button | The podcast's page | ok | [019-back-button-the-podcast-s-page.png](shots/019-back-button-the-podcast-s-page.png) |  |
| 20 | back-button | An episode's page (to play it) | ok | [020-back-button-an-episode-s-page-to-play-it.png](shots/020-back-button-an-episode-s-page-to-play-it.png) |  |
| 21 | back-button | Play the episode | ok | [021-back-button-play-the-episode.png](shots/021-back-button-play-the-episode.png) |  |
| 22 | back-button | The episode plays | ok | [022-back-button-the-episode-plays.png](shots/022-back-button-the-episode-plays.png) |  |
| 23 | back-button | Pause the episode | ok | [023-back-button-pause-the-episode.png](shots/023-back-button-pause-the-episode.png) |  |
| 24 | back-button | Back from the episode | ok | [024-back-button-back-from-the-episode.png](shots/024-back-button-back-from-the-episode.png) |  |
| 25 | back-button | Back from the podcast | ok | [025-back-button-back-from-the-podcast.png](shots/025-back-button-back-from-the-podcast.png) |  |
| 26 | back-button | Music | ok | [026-back-button-music.png](shots/026-back-button-music.png) |  |
| 27 | back-button | Play Profile track 1.1 | ok | [027-back-button-play-profile-track-1-1.png](shots/027-back-button-play-profile-track-1-1.png) |  |
| 28 | back-button | A tap on the mini player | ok | [028-back-button-a-tap-on-the-mini-player.png](shots/028-back-button-a-tap-on-the-mini-player.png) |  |
| 29 | back-button | Before the Back button: the full player | ok | [029-back-button-before-the-back-button-the-full-player.png](shots/029-back-button-before-the-back-button-the-full-player.png) |  |
| 30 | back-button | The Back button held on the full player, then let go | ok | [030-back-button-the-back-button-held-on-the-full-player-then-let.png](shots/030-back-button-the-back-button-held-on-the-full-player-then-let.png) | check failed: the full player stays whole while the system's Back button is held |

## Warnings

None.

### Missing native libraries (arm64-only libraries on an x86_64 emulator)

No link errors were logged.

### StrictMode violations: 0 (the app's; by the system's policy for a debuggable app)


### Error-level log lines of the app (top 25)

- 2x `VLC/LibVLC/Util: WARNING: Can't find shared library`
- 1x `VLC: [000078e44d03cc90/13b6] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [000078e47d0c3c30/13b6] libvlc input: Your media can't be opened`
- 1x `VLC: [000078e47d0c3c30/13b6] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/obb/'. Check the log for details.`
- 1x `VLC: [000078e44d044bb0/13ba] libvlc access: cannot open file /storage/emulated/0/Android/data/ (Permission denied)`
- 1x `VLC: [000078e47d0c3c30/13ba] libvlc input: Your media can't be opened`
- 1x `VLC: [000078e47d0c3c30/13ba] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/data/'. Check the log for details.`
- 1x `ActivityThread: Failed to find provider info for androidx.car.app.connection`
