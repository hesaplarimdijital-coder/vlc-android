# Emulator smoke test: passed

- app: `app.nagmedar.debug`, APK `personal`
- installed: versionCode=14010010 minSdk=34 targetSdk=36; versionName=1.0.2; pkgFlags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP LARGE_HEAP ]
- device: model=sdk_gphone64_x86_64, sdk=35, abi=x86_64, build=sdk_gphone64_x86_64-userdebug 15 AE3A.240806.043 12960925 dev-keys, size=Physical size: 2560x1600, density=Physical density: 320
- source: 8d9e266fcc27ee77256fd7a5b063d2dbafb36fe3 (story/train-1, personal build, API 35, phone), run 42160729319
- passes: phone-en
- 131 steps in 27 min 51 s; 0 crash/ANR finding(s), 0 driver error(s), 0 StrictMode violation(s), 0 of 60 1.0 checks failed; 0 ANR/crash of the system (the emulator's, not failures)

## 1.0 checks

| pass | step | check | result | seen |
|------|------|-------|--------|------|
| phone-en | 6 | bottom bar is Music · Podcast · Radio · More | ok | seen: Music · Podcast · Radio · More |
| phone-en | 6 | the bottom bar's colour continues behind the three buttons (at the first launch) | ok | bar (255, 255, 255), behind the buttons (255, 255, 255) |
| phone-en | 41 | after a restart the Music lists its tracks at once, without a scanning state | ok | 12 rows seen 3.7 s after am start; list on screen 1155 ms after the process started (65 tracks, kept list); first frame 1118 ms (am start) |
| phone-en | 57 | a track's ⋯ button | ok |  |
| phone-en | 58 | a track's ⋯ opens its sheet | ok |  |
| phone-en | 59 | Back closes the track's sheet | ok |  |
| phone-en | 60 | the track's sheet opens again | ok |  |
| phone-en | 61 | a drag down from the sheet's middle closes it | ok | from y=1423 of 2400, 12 moves in 826 ms |
| phone-en | 62 | bottom bar is Music · Podcast · Radio · More | ok | seen: Music · Podcast · Radio · More |
| phone-en | 63 | the top bar keeps its colour while the list scrolls | ok | at rest (250, 251, 252), while dragged (250, 251, 252), after (250, 251, 252) |
| phone-en | 64 | ⋯ in the bottom bar | ok |  |
| phone-en | 65 | ⋯ opens the More page | ok | seen: Downloads, Settings, Playlists |
| phone-en | 65 | Settings on the More page | ok |  |
| phone-en | 66 | Settings is a page of its own: no bottom bar | ok | bottom bar: none |
| phone-en | 66 | Settings has a back arrow | ok |  |
| phone-en | 67 | Back from Settings shows More | ok | title: Library, More |
| phone-en | 69 | the back arrow returns to More | ok |  |
| phone-en | 71 | a track in the Music's list | ok |  |
| phone-en | 72 | the mini player shows the track | ok |  |
| phone-en | 72 | a mini player to drag | ok |  |
| phone-en | 73 | dragged up, the full player shows | ok | drags: 1 |
| phone-en | 74 | dragged down, the mini player again | ok |  |
| phone-en | 75 | a sideways swipe changes the track | ok | Profile track 1.1 / Nagmedar Artist 1 → Profile track 1.2 / Nagmedar Artist 1 (swipes: 1) |
| phone-en | 75 | the bar's seek bar | ok |  |
| phone-en | 76 | a swipe along the seek bar does not change the track | ok | Profile track 1.2 / Nagmedar Artist 1 → Profile track 1.2 / Nagmedar Artist 1 |
| phone-en | 77 | the Podcasts open | ok |  |
| phone-en | 78 | the podcast address field | ok |  |
| phone-en | 80 | the feed is followed | ok |  |
| phone-en | 81 | an episode on the podcast's page | ok |  |
| phone-en | 81 | the episode's play button | ok |  |
| phone-en | 82 | the episode's play button does not move | ok | first (42, 643, 708, 769) at 2.2 s; then (42, 643, 708, 769) at +3.7 s; (42, 643, 708, 769) at +6.7 s |
| phone-en | 85 | the Radio opens | ok |  |
| phone-en | 88 | glass B: with animations removed the bottom chrome is opaque, one colour (card, bar, band) | ok | card {'lo': (255, 255, 255), 'hi': (255, 255, 255), 'mean': (255.0, 255.0, 255.0), 'spread': 0}; bar {'lo': (255, 255, 255), 'hi': (255, 255, 255), 'mean': (255.0, 255.0, 255.0), 'spread': 0}; band {'lo': (255, 255, 255), 'hi': (255, 255, 255), 'mean': (255.0, 255.0, 255.0), 'spread': 0} |
| phone-en | 89 | glass B: switched on at run time, the glass blurs what passes beneath it (card, bar, band) | ok | card {'lo': (248, 247, 246), 'hi': (248, 248, 249), 'mean': (248.0, 247.8, 247.5), 'spread': 3}; bar {'lo': (246, 247, 248), 'hi': (247, 248, 249), 'mean': (246.5, 247.5, 248.5), 'spread': 1}; band {'lo': (247, 243, 240), 'hi': (248, 247, 246), 'mean': (247.7, 244.8, 242.0), 'spread': 6}; opaque (25 |
| phone-en | 90 | glass B: the glass changes as the list moves under it | ok | band (247.7, 244.8, 242.0) -> (248.0, 245.7, 243.7); card (248.0, 247.8, 247.5) -> (248.0, 247.8, 247.7) |
| phone-en | 91 | glass B: from a cold start the bar's glass blurs the rows beneath it (no sharp rows) | ok | first frames {'lo': (247, 248, 249), 'hi': (247, 248, 249), 'mean': (247.0, 248.0, 249.0), 'spread': 0}, settled {'lo': (247, 248, 249), 'hi': (247, 248, 249), 'mean': (247.0, 248.0, 249.0), 'spread': 0}; opaque (255.0, 255.0, 255.0) |
| phone-en | 92 | Android Auto: the four tabs, the walk's 64 tracks under Albums, its podcast, every list answers | ok | app.nagmedar.macrobenchmark.AutoBrowseTest:.  Time: 5.507  OK (1 test) |
| phone-en | 92 | This device is found (⋯ or the sidebar) | ok |  |
| phone-en | 93 | This device lists the places, the internal storage first | ok |  |
| phone-en | 94 | Internal storage opens and lists its folders (Music among them) | ok | rows: Alarms, Android, Audiobooks, DCIM, Documents, Download, More, Movies, Music, Name, Notifications, Nov 6, 2026 4:27 AM |
| phone-en | 95 | the Music folder lists the walk's files (tone.mp3) | ok |  |
| phone-en | 96 | a file of the folder plays | ok | mini player |
| phone-en | 97 | back up at the drive, its folders listed | ok |  |
| phone-en | 97 | the sort menu's button | ok |  |
| phone-en | 98 | the sort menu lists the orders and the hidden files | ok |  |
| phone-en | 99 | after 'By size' the drive still lists its folders | ok |  |
| phone-en | 99 | the sort menu's button | ok |  |
| phone-en | 100 | the sort menu lists the orders and the hidden files | ok |  |
| phone-en | 101 | after 'By name' the drive still lists its folders | ok |  |
| phone-en | 101 | the sort menu's button | ok |  |
| phone-en | 102 | the sort menu lists the orders and the hidden files | ok |  |
| phone-en | 103 | after 'Show hidden files' the drive still lists its folders | ok |  |
| phone-en | 103 | the sort menu's button | ok |  |
| phone-en | 104 | the sort menu lists the orders and the hidden files | ok |  |
| phone-en | 105 | after 'Show hidden files' the drive still lists its folders | ok |  |
| phone-en | 106 | Back goes on to the places | ok |  |
| phone-en | 110 | after Home the folder is still open | ok |  |
| phone-en | 111 | rotated, the folder is still open | ok |  |
| phone-en | 112 | rotated back, the folder is still open | ok |  |
| all | 131 | no film, series, video, Home, TV Lab or Whisper words on the app's screens | ok |  |

## Frames (dumpsys gfxinfo; informational on an emulator, over 40 % janky is flagged)

| pass | step | gesture | frames | janky | 50th | 90th | 95th | 99th |
|------|------|---------|--------|-------|------|------|------|------|
| phone-en | 70 | Music list scroll | 93 | 96.8 % **(over 40 %)** | 85 ms | 150 ms | 200 ms | 650 ms |
| phone-en | 73 | player drag up | 16 | 100.0 % **(over 40 %)** | 200 ms | 600 ms | 650 ms | 650 ms |
| phone-en | 74 | player drag down | 17 | 100.0 % **(over 40 %)** | 125 ms | 350 ms | 400 ms | 400 ms |

## The system's ANRs and crashes (the emulator's own: recorded, the step tried once more)

None.

## Steps

| # | pass | step | result | screenshot | note |
|---|------|------|--------|------------|------|
| 1 | phone-en | Launch (phone) | ok | [001-phone-en-launch-phone.png](shots/001-phone-en-launch-phone.png) |  |
| 2 | phone-en | First run step 1 (Continue) | ok | [002-phone-en-first-run-step-1-continue.png](shots/002-phone-en-first-run-step-1-continue.png) |  |
| 3 | phone-en | First run step 2 (Continue · 2 sources) | ok | [003-phone-en-first-run-step-2-continue-2-sources.png](shots/003-phone-en-first-run-step-2-continue-2-sources.png) |  |
| 4 | phone-en | First run step 3 (Continue) | ok | [004-phone-en-first-run-step-3-continue.png](shots/004-phone-en-first-run-step-3-continue.png) |  |
| 5 | phone-en | First run step 4 (Start) | ok | [005-phone-en-first-run-step-4-start.png](shots/005-phone-en-first-run-step-4-start.png) |  |
| 6 | phone-en | After the first run (phone) | ok | [006-phone-en-after-the-first-run-phone.png](shots/006-phone-en-after-the-first-run-phone.png) |  |
| 7 | phone-en | Open Music | ok | [007-phone-en-open-music.png](shots/007-phone-en-open-music.png) |  |
| 8 | phone-en | Open Podcast | ok | [008-phone-en-open-podcast.png](shots/008-phone-en-open-podcast.png) |  |
| 9 | phone-en | Open Podcasts | ok | [009-phone-en-open-podcasts.png](shots/009-phone-en-open-podcasts.png) |  |
| 10 | phone-en | Open Radio | ok | [010-phone-en-open-radio.png](shots/010-phone-en-open-radio.png) |  |
| 11 | phone-en | Open More (overflow) | ok | [011-phone-en-open-more-overflow.png](shots/011-phone-en-open-more-overflow.png) |  |
| 12 | phone-en | Open This device | ok | [012-phone-en-open-this-device.png](shots/012-phone-en-open-this-device.png) |  |
| 13 | phone-en | Open More (overflow) | ok | [013-phone-en-open-more-overflow.png](shots/013-phone-en-open-more-overflow.png) |  |
| 14 | phone-en | Open Network | ok | [014-phone-en-open-network.png](shots/014-phone-en-open-network.png) |  |
| 15 | phone-en | Open More (overflow) | ok | [015-phone-en-open-more-overflow.png](shots/015-phone-en-open-more-overflow.png) |  |
| 16 | phone-en | Open Streams | ok | [016-phone-en-open-streams.png](shots/016-phone-en-open-streams.png) |  |
| 17 | phone-en | Open More (overflow) | ok | [017-phone-en-open-more-overflow.png](shots/017-phone-en-open-more-overflow.png) |  |
| 18 | phone-en | Open Downloads | ok | [018-phone-en-open-downloads.png](shots/018-phone-en-open-downloads.png) |  |
| 19 | phone-en | Open More (overflow) | ok | [019-phone-en-open-more-overflow.png](shots/019-phone-en-open-more-overflow.png) |  |
| 20 | phone-en | Open Playlists | ok | [020-phone-en-open-playlists.png](shots/020-phone-en-open-playlists.png) |  |
| 21 | phone-en | Open More (overflow) | ok | [021-phone-en-open-more-overflow.png](shots/021-phone-en-open-more-overflow.png) |  |
| 22 | phone-en | Settings | ok | [022-phone-en-settings.png](shots/022-phone-en-settings.png) |  |
| 23 | phone-en | Settings scroll 1 | ok | [023-phone-en-settings-scroll-1.png](shots/023-phone-en-settings-scroll-1.png) |  |
| 24 | phone-en | Settings scroll 2 | ok | [024-phone-en-settings-scroll-2.png](shots/024-phone-en-settings-scroll-2.png) |  |
| 25 | phone-en | Settings scroll 3 | ok | [025-phone-en-settings-scroll-3.png](shots/025-phone-en-settings-scroll-3.png) |  |
| 26 | phone-en | Settings scroll 4 | ok | [026-phone-en-settings-scroll-4.png](shots/026-phone-en-settings-scroll-4.png) |  |
| 27 | phone-en | Settings scroll 5 | ok | [027-phone-en-settings-scroll-5.png](shots/027-phone-en-settings-scroll-5.png) |  |
| 28 | phone-en | Settings scroll 6 | ok | [028-phone-en-settings-scroll-6.png](shots/028-phone-en-settings-scroll-6.png) |  |
| 29 | phone-en | Settings scroll 7 | ok | [029-phone-en-settings-scroll-7.png](shots/029-phone-en-settings-scroll-7.png) |  |
| 30 | phone-en | Settings scroll 8 | ok | [030-phone-en-settings-scroll-8.png](shots/030-phone-en-settings-scroll-8.png) |  |
| 31 | phone-en | Settings scroll 9 | ok | [031-phone-en-settings-scroll-9.png](shots/031-phone-en-settings-scroll-9.png) |  |
| 32 | phone-en | Settings scroll 10 | ok | [032-phone-en-settings-scroll-10.png](shots/032-phone-en-settings-scroll-10.png) |  |
| 33 | phone-en | Open About | ok | [033-phone-en-open-about.png](shots/033-phone-en-open-about.png) |  |
| 34 | phone-en | Back from About | ok | [034-phone-en-back-from-about.png](shots/034-phone-en-back-from-about.png) |  |
| 35 | phone-en | Show the first run again | ok | [035-phone-en-show-the-first-run-again.png](shots/035-phone-en-show-the-first-run-again.png) |  |
| 36 | phone-en | First run again step 1 (Continue) | ok | [036-phone-en-first-run-again-step-1-continue.png](shots/036-phone-en-first-run-again-step-1-continue.png) |  |
| 37 | phone-en | First run again step 2 (Continue · 2 sources) | ok | [037-phone-en-first-run-again-step-2-continue-2-sources.png](shots/037-phone-en-first-run-again-step-2-continue-2-sources.png) |  |
| 38 | phone-en | First run again step 3 (Continue) | ok | [038-phone-en-first-run-again-step-3-continue.png](shots/038-phone-en-first-run-again-step-3-continue.png) |  |
| 39 | phone-en | First run again step 4 (Start) | ok | [039-phone-en-first-run-again-step-4-start.png](shots/039-phone-en-first-run-again-step-4-start.png) |  |
| 40 | phone-en | After the scan | ok | [040-phone-en-after-the-scan.png](shots/040-phone-en-after-the-scan.png) |  |
| 41 | phone-en | Cold start: the Music's list | ok | [041-phone-en-cold-start-the-music-s-list.png](shots/041-phone-en-cold-start-the-music-s-list.png) | 12 rows seen 3.7 s after am start; list on screen 1155 ms after the process started (65 tracks, kept list); first frame 1118 ms (am start) |
| 42 | phone-en | Reopen Music | ok | [042-phone-en-reopen-music.png](shots/042-phone-en-reopen-music.png) |  |
| 43 | phone-en | Reopen Podcast | ok | [043-phone-en-reopen-podcast.png](shots/043-phone-en-reopen-podcast.png) |  |
| 44 | phone-en | Reopen Podcasts | ok | [044-phone-en-reopen-podcasts.png](shots/044-phone-en-reopen-podcasts.png) |  |
| 45 | phone-en | Reopen Radio | ok | [045-phone-en-reopen-radio.png](shots/045-phone-en-reopen-radio.png) |  |
| 46 | phone-en | Open More (overflow) | ok | [046-phone-en-open-more-overflow.png](shots/046-phone-en-open-more-overflow.png) |  |
| 47 | phone-en | Reopen This device | ok | [047-phone-en-reopen-this-device.png](shots/047-phone-en-reopen-this-device.png) |  |
| 48 | phone-en | Open More (overflow) | ok | [048-phone-en-open-more-overflow.png](shots/048-phone-en-open-more-overflow.png) |  |
| 49 | phone-en | Reopen Network | ok | [049-phone-en-reopen-network.png](shots/049-phone-en-reopen-network.png) |  |
| 50 | phone-en | Open More (overflow) | ok | [050-phone-en-open-more-overflow.png](shots/050-phone-en-open-more-overflow.png) |  |
| 51 | phone-en | Reopen Streams | ok | [051-phone-en-reopen-streams.png](shots/051-phone-en-reopen-streams.png) |  |
| 52 | phone-en | Open More (overflow) | ok | [052-phone-en-open-more-overflow.png](shots/052-phone-en-open-more-overflow.png) |  |
| 53 | phone-en | Reopen Downloads | ok | [053-phone-en-reopen-downloads.png](shots/053-phone-en-reopen-downloads.png) |  |
| 54 | phone-en | Open More (overflow) | ok | [054-phone-en-open-more-overflow.png](shots/054-phone-en-open-more-overflow.png) |  |
| 55 | phone-en | Reopen Playlists | ok | [055-phone-en-reopen-playlists.png](shots/055-phone-en-reopen-playlists.png) |  |
| 56 | phone-en | Open More (overflow) | ok | [056-phone-en-open-more-overflow.png](shots/056-phone-en-open-more-overflow.png) |  |
| 57 | phone-en | Music | ok | [057-phone-en-music.png](shots/057-phone-en-music.png) |  |
| 58 | phone-en | A track's ⋯ (More actions for Profile track 1.1) | ok | [058-phone-en-a-track-s-more-actions-for-profile-track-1-1.png](shots/058-phone-en-a-track-s-more-actions-for-profile-track-1-1.png) |  |
| 59 | phone-en | Back from the track's sheet | ok | [059-phone-en-back-from-the-track-s-sheet.png](shots/059-phone-en-back-from-the-track-s-sheet.png) |  |
| 60 | phone-en | A track's ⋯ again | ok | [060-phone-en-a-track-s-again.png](shots/060-phone-en-a-track-s-again.png) |  |
| 61 | phone-en | Drag the sheet down from its middle | ok | [061-phone-en-drag-the-sheet-down-from-its-middle.png](shots/061-phone-en-drag-the-sheet-down-from-its-middle.png) |  |
| 62 | phone-en | Music | ok | [062-phone-en-music.png](shots/062-phone-en-music.png) |  |
| 63 | phone-en | Music scrolled (top bar) | ok | [063-phone-en-music-scrolled-top-bar.png](shots/063-phone-en-music-scrolled-top-bar.png) |  |
| 64 | phone-en | Music | ok | [064-phone-en-music.png](shots/064-phone-en-music.png) |  |
| 65 | phone-en | Bottom bar ⋯ | ok | [065-phone-en-bottom-bar.png](shots/065-phone-en-bottom-bar.png) |  |
| 66 | phone-en | Settings from More | ok | [066-phone-en-settings-from-more.png](shots/066-phone-en-settings-from-more.png) |  |
| 67 | phone-en | Back from Settings | ok | [067-phone-en-back-from-settings.png](shots/067-phone-en-back-from-settings.png) |  |
| 68 | phone-en | Settings from More again | ok | [068-phone-en-settings-from-more-again.png](shots/068-phone-en-settings-from-more-again.png) |  |
| 69 | phone-en | The back arrow | ok | [069-phone-en-the-back-arrow.png](shots/069-phone-en-the-back-arrow.png) |  |
| 70 | phone-en | Music | ok | [070-phone-en-music.png](shots/070-phone-en-music.png) |  |
| 71 | phone-en | Music list scroll (after the scroll) | ok | [071-phone-en-music-list-scroll-after-the-scroll.png](shots/071-phone-en-music-list-scroll-after-the-scroll.png) |  |
| 72 | phone-en | Play Profile track 1.1 | ok | [072-phone-en-play-profile-track-1-1.png](shots/072-phone-en-play-profile-track-1-1.png) |  |
| 73 | phone-en | Drag the mini player up | ok | [073-phone-en-drag-the-mini-player-up.png](shots/073-phone-en-drag-the-mini-player-up.png) |  |
| 74 | phone-en | Drag the full player down | ok | [074-phone-en-drag-the-full-player-down.png](shots/074-phone-en-drag-the-full-player-down.png) |  |
| 75 | phone-en | Swipe the mini player sideways | ok | [075-phone-en-swipe-the-mini-player-sideways.png](shots/075-phone-en-swipe-the-mini-player-sideways.png) |  |
| 76 | phone-en | Swipe along the bar's seek bar | ok | [076-phone-en-swipe-along-the-bar-s-seek-bar.png](shots/076-phone-en-swipe-along-the-bar-s-seek-bar.png) |  |
| 77 | phone-en | Podcasts | ok | [077-phone-en-podcasts.png](shots/077-phone-en-podcasts.png) |  |
| 78 | phone-en | Add a podcast by its address | ok | [078-phone-en-add-a-podcast-by-its-address.png](shots/078-phone-en-add-a-podcast-by-its-address.png) |  |
| 79 | phone-en | The feed's address typed | ok | [079-phone-en-the-feed-s-address-typed.png](shots/079-phone-en-the-feed-s-address-typed.png) |  |
| 80 | phone-en | Subscribe | ok | [080-phone-en-subscribe.png](shots/080-phone-en-subscribe.png) |  |
| 81 | phone-en | Followed | ok | [081-phone-en-followed.png](shots/081-phone-en-followed.png) |  |
| 82 | phone-en | Episode page (the play button watched) | ok | [082-phone-en-episode-page-the-play-button-watched.png](shots/082-phone-en-episode-page-the-play-button-watched.png) |  |
| 83 | phone-en | Back from the episode | ok | [083-phone-en-back-from-the-episode.png](shots/083-phone-en-back-from-the-episode.png) |  |
| 84 | phone-en | Back from the podcast | ok | [084-phone-en-back-from-the-podcast.png](shots/084-phone-en-back-from-the-podcast.png) |  |
| 85 | phone-en | Radio | ok | [085-phone-en-radio.png](shots/085-phone-en-radio.png) |  |
| 86 | phone-en | Music | ok | [086-phone-en-music.png](shots/086-phone-en-music.png) |  |
| 87 | phone-en | Search: track | ok | [087-phone-en-search-track.png](shots/087-phone-en-search-track.png) |  |
| 88 | phone-en | Glass B: animations removed (opaque) | ok | [088-phone-en-glass-b-animations-removed-opaque.png](shots/088-phone-en-glass-b-animations-removed-opaque.png) |  |
| 89 | phone-en | Glass B: animations on over the still page | ok | [089-phone-en-glass-b-animations-on-over-the-still-page.png](shots/089-phone-en-glass-b-animations-on-over-the-still-page.png) |  |
| 90 | phone-en | Glass B: the results moved under the bars | ok | [090-phone-en-glass-b-the-results-moved-under-the-bars.png](shots/090-phone-en-glass-b-the-results-moved-under-the-bars.png) |  |
| 91 | phone-en | Glass B: a cold start | ok | [091-phone-en-glass-b-a-cold-start.png](shots/091-phone-en-glass-b-a-cold-start.png) |  |
| 92 | phone-en | Android Auto's tree read by a MediaBrowserCompat | ok | [092-phone-en-android-auto-s-tree-read-by-a-mediabrowsercompat.png](shots/092-phone-en-android-auto-s-tree-read-by-a-mediabrowsercompat.png) | AutoBrowseTest: app.nagmedar.macrobenchmark.AutoBrowseTest:. Time: 5.507 OK (1 test) |
| 93 | phone-en | Open This device | ok | [093-phone-en-open-this-device.png](shots/093-phone-en-open-this-device.png) |  |
| 94 | phone-en | Open Internal storage | ok | [094-phone-en-open-internal-storage.png](shots/094-phone-en-open-internal-storage.png) |  |
| 95 | phone-en | Open its Music folder | ok | [095-phone-en-open-its-music-folder.png](shots/095-phone-en-open-its-music-folder.png) |  |
| 96 | phone-en | Play tone.mp3 from the folder | ok | [096-phone-en-play-tone-mp3-from-the-folder.png](shots/096-phone-en-play-tone-mp3-from-the-folder.png) |  |
| 97 | phone-en | Back (up to Internal storage) | ok | [097-phone-en-back-up-to-internal-storage.png](shots/097-phone-en-back-up-to-internal-storage.png) |  |
| 98 | phone-en | Sort menu | ok | [098-phone-en-sort-menu.png](shots/098-phone-en-sort-menu.png) |  |
| 99 | phone-en | Sort menu: By size | ok | [099-phone-en-sort-menu-by-size.png](shots/099-phone-en-sort-menu-by-size.png) |  |
| 100 | phone-en | Sort menu | ok | [100-phone-en-sort-menu.png](shots/100-phone-en-sort-menu.png) |  |
| 101 | phone-en | Sort menu: By name | ok | [101-phone-en-sort-menu-by-name.png](shots/101-phone-en-sort-menu-by-name.png) |  |
| 102 | phone-en | Sort menu | ok | [102-phone-en-sort-menu.png](shots/102-phone-en-sort-menu.png) |  |
| 103 | phone-en | Sort menu: Show hidden files | ok | [103-phone-en-sort-menu-show-hidden-files.png](shots/103-phone-en-sort-menu-show-hidden-files.png) |  |
| 104 | phone-en | Sort menu | ok | [104-phone-en-sort-menu.png](shots/104-phone-en-sort-menu.png) |  |
| 105 | phone-en | Sort menu: Show hidden files | ok | [105-phone-en-sort-menu-show-hidden-files.png](shots/105-phone-en-sort-menu-show-hidden-files.png) |  |
| 106 | phone-en | Back (to the places) | ok | [106-phone-en-back-to-the-places.png](shots/106-phone-en-back-to-the-places.png) |  |
| 107 | phone-en | Open Internal storage again | ok | [107-phone-en-open-internal-storage-again.png](shots/107-phone-en-open-internal-storage-again.png) |  |
| 108 | phone-en | Open the Music folder again | ok | [108-phone-en-open-the-music-folder-again.png](shots/108-phone-en-open-the-music-folder-again.png) |  |
| 109 | phone-en | Home with the folder open | warn | [109-phone-en-home-with-the-folder-open.png](shots/109-phone-en-home-with-the-folder-open.png) | the app is not in the foreground (windows: com.google.android.apps.nexuslauncher, com.google.android.calendar, com.google.android.deskclock) |
| 110 | phone-en | Back in the app with the folder open | ok | [110-phone-en-back-in-the-app-with-the-folder-open.png](shots/110-phone-en-back-in-the-app-with-the-folder-open.png) |  |
| 111 | phone-en | Rotated with the folder open | ok | [111-phone-en-rotated-with-the-folder-open.png](shots/111-phone-en-rotated-with-the-folder-open.png) |  |
| 112 | phone-en | Rotated back with the folder open | ok | [112-phone-en-rotated-back-with-the-folder-open.png](shots/112-phone-en-rotated-back-with-the-folder-open.png) |  |
| 113 | phone-en | Back (up to the drive) | ok | [113-phone-en-back-up-to-the-drive.png](shots/113-phone-en-back-up-to-the-drive.png) |  |
| 114 | phone-en | Back (to the places) | ok | [114-phone-en-back-to-the-places.png](shots/114-phone-en-back-to-the-places.png) |  |
| 115 | phone-en | Open the audio file | ok | [115-phone-en-open-the-audio-file.png](shots/115-phone-en-open-the-audio-file.png) | Starting: Intent { act=android.intent.action.VIEW dat=file:///... typ=audio/mpeg pkg=app.nagmedar.debug } |
| 116 | phone-en | Playing the audio file | ok | [116-phone-en-playing-the-audio-file.png](shots/116-phone-en-playing-the-audio-file.png) |  |
| 117 | phone-en | Back from the audio file | ok | [117-phone-en-back-from-the-audio-file.png](shots/117-phone-en-back-from-the-audio-file.png) |  |
| 118 | phone-en | Home during playback | warn | [118-phone-en-home-during-playback.png](shots/118-phone-en-home-during-playback.png) | the app is not in the foreground (windows: com.google.android.apps.nexuslauncher, com.google.android.calendar, com.google.android.deskclock) |
| 119 | phone-en | Back to the app after playback | ok | [119-phone-en-back-to-the-app-after-playback.png](shots/119-phone-en-back-to-the-app-after-playback.png) |  |
| 120 | phone-en | Command palette (Ctrl+K) | ok | [120-phone-en-command-palette-ctrl-k.png](shots/120-phone-en-command-palette-ctrl-k.png) |  |
| 121 | phone-en | Palette query | ok | [121-phone-en-palette-query.png](shots/121-phone-en-palette-query.png) |  |
| 122 | phone-en | Palette: select the first result | ok | [122-phone-en-palette-select-the-first-result.png](shots/122-phone-en-palette-select-the-first-result.png) |  |
| 123 | phone-en | Escape | ok | [123-phone-en-escape.png](shots/123-phone-en-escape.png) |  |
| 124 | phone-en | Shortcut list (Ctrl+/) | ok | [124-phone-en-shortcut-list-ctrl.png](shots/124-phone-en-shortcut-list-ctrl.png) |  |
| 125 | phone-en | Settings (Ctrl+,) | ok | [125-phone-en-settings-ctrl.png](shots/125-phone-en-settings-ctrl.png) |  |
| 126 | phone-en | Rotated to landscape | ok | [126-phone-en-rotated-to-landscape.png](shots/126-phone-en-rotated-to-landscape.png) |  |
| 127 | phone-en | Rotated back | ok | [127-phone-en-rotated-back.png](shots/127-phone-en-rotated-back.png) |  |
| 128 | phone-en | In the background (home screen) | warn | [128-phone-en-in-the-background-home-screen.png](shots/128-phone-en-in-the-background-home-screen.png) | the app is not in the foreground (windows: com.google.android.apps.nexuslauncher, com.google.android.calendar, com.google.android.deskclock) |
| 129 | phone-en | Foreground again | ok | [129-phone-en-foreground-again.png](shots/129-phone-en-foreground-again.png) |  |
| 130 | phone-en | After a memory-trim request | ok | [130-phone-en-after-a-memory-trim-request.png](shots/130-phone-en-after-a-memory-trim-request.png) |  |
| 131 | phone-en | Cold start after the process was killed | ok | [131-phone-en-cold-start-after-the-process-was-killed.png](shots/131-phone-en-cold-start-after-the-process-was-killed.png) |  |

## Warnings

- [phone-en] no 'Open the equaliser' control found in Settings
- [phone-en] Music list scroll: 96.8 % janky frames (over 40 %; informational on an emulator)
- [phone-en] player drag up: 100.0 % janky frames (over 40 %; informational on an emulator)
- [phone-en] player drag down: 100.0 % janky frames (over 40 %; informational on an emulator)

### Missing native libraries (arm64-only libraries on an x86_64 emulator)

No link errors were logged.

### StrictMode violations: 0 (the app's; by the system's policy for a debuggable app)


### Error-level log lines of the app (top 25)

- 46x `VLC-std: s_glBindAttribLocation: bind attrib 0 name position`
- 29x `VLC-std: s_glBindAttribLocation: bind attrib 1 name color`
- 25x `VLC-std: s_glBindAttribLocation: bind attrib 0 name inPosition`
- 25x `VLC-std: s_glBindAttribLocation: bind attrib 1 name inColor`
- 22x `VLC-std: s_glBindAttribLocation: bind attrib 2 name localCoord`
- 17x `VLC-std: s_glBindAttribLocation: bind attrib 2 name inCircleEdge`
- 12x `VLC-std: s_glBindAttribLocation: bind attrib 1 name localCoord`
- 5x `VLC-std: s_glBindAttribLocation: bind attrib 1 name coverage`
- 4x `VLC-std: s_glBindAttribLocation: bind attrib 2 name inTextureCoords`
- 4x `VLC-std: s_glBindAttribLocation: bind attrib 2 name inShadowParams`
- 3x `.nagmedar.debug: Not starting debugger since process cannot load the jdwp agent.`
- 3x `VLC/LibVLC/Util: WARNING: Can't find shared library`
- 3x `FrameTracker: force finish cuj, time out: J<IME_INSETS_SHOW_ANIMATION::0@1@app.nagmedar.debug>`
- 3x `FrameTracker: force finish cuj, time out: J<IME_INSETS_HIDE_ANIMATION::1@1@app.nagmedar.debug>`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 0 name radii_selector`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 1 name corner_and_radius_outsets`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 2 name aa_bloat_and_coverage`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 3 name radii_x`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 4 name radii_y`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 5 name skew`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 6 name translate_and_localrotate`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 7 name color`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 2 name color`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 3 name localCoord`
- 2x `VLC-std: s_glBindAttribLocation: bind attrib 0 name a_vertex`
