# Emulator smoke test: FAILED

- app: `app.nagmedar.debug`, APK `personal`
- installed: versionCode=14010010 minSdk=34 targetSdk=36; versionName=1.0.2; pkgFlags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP KILL_AFTER_RESTORE LARGE_HEAP ]
- device: model=sdk_gphone64_x86_64, sdk=37, abi=x86_64, build=sdk_gphone64_x86_64-userdebug 17 CE2A.260420.051.A3 16571990 dev-keys, size=Physical size: 2560x1600, density=Physical density: 320
- source: d343c65d6ea512a3f0efad5cb492e9381c9d1fac (story/platform, personal build, API 37.0), run 42023850509
- 246 steps in 48 min 48 s; 0 crash/ANR finding(s), 0 driver error(s), 0 StrictMode violation(s), 1 of 119 1.0 checks failed

## 1.0 checks

| pass | step | check | result | seen |
|------|------|-------|--------|------|
| phone-en | 42 | bottom bar is Music · Podcast · Radio · More | ok | seen: Music · Podcast · Radio · More |
| phone-en | 92 | a track's ⋯ button | ok |  |
| phone-en | 93 | a track's ⋯ opens its sheet | ok |  |
| phone-en | 94 | Back closes the track's sheet | ok |  |
| phone-en | 95 | the track's sheet opens again | ok |  |
| phone-en | 96 | a drag down from the sheet's middle closes it | **FAILED** | from y=1423 of 2400 |
| phone-en | 97 | bottom bar is Music · Podcast · Radio · More | ok | seen: Music · Podcast · Radio · More |
| phone-en | 98 | the top bar keeps its colour while the list scrolls | ok | at rest (250, 251, 252), while dragged (250, 251, 252), after (250, 251, 252) |
| phone-en | 99 | ⋯ in the bottom bar | ok |  |
| phone-en | 100 | ⋯ opens the More page | ok | seen: Downloads, Settings, Playlists |
| phone-en | 100 | Settings on the More page | ok |  |
| phone-en | 101 | Settings is a page of its own: no bottom bar | ok | bottom bar: none |
| phone-en | 101 | Settings has a back arrow | ok |  |
| phone-en | 102 | Back from Settings shows More | ok | title: Library, More |
| phone-en | 104 | the back arrow returns to More | ok |  |
| phone-en | 106 | a track in the Music's list | ok |  |
| phone-en | 107 | the mini player shows the track | ok |  |
| phone-en | 107 | a mini player to drag | ok |  |
| phone-en | 108 | dragged up, the full player shows | ok | drags: 1 |
| phone-en | 109 | dragged down, the mini player again | ok |  |
| phone-en | 110 | a sideways swipe changes the track | ok | Profile track 1.1 / Nagmedar Artist 1 → Profile track 1.2 / Nagmedar Artist 1 (swipes: 1) |
| phone-en | 110 | the bar's seek bar | ok |  |
| phone-en | 111 | a swipe along the seek bar does not change the track | ok | Profile track 1.2 / Nagmedar Artist 1 → Profile track 1.2 / Nagmedar Artist 1 |
| phone-en | 112 | the Podcasts open | ok |  |
| phone-en | 113 | the podcast address field | ok |  |
| phone-en | 115 | the feed is followed | ok |  |
| phone-en | 116 | an episode on the podcast's page | ok |  |
| phone-en | 116 | the episode's play button | ok |  |
| phone-en | 117 | the episode's play button does not move | ok | first (42, 643, 708, 769) at 2.1 s; then (42, 643, 708, 769) at +3.6 s; (42, 643, 708, 769) at +6.6 s |
| phone-en | 120 | the Radio opens | ok |  |
| phone-en | 121 | Android Auto: the four tabs, the walk's 64 tracks under Albums, its podcast, every list answers | ok | app.nagmedar.macrobenchmark.AutoBrowseTest:.  Time: 4.782  OK (1 test) |
| phone-en | 121 | This device is found (⋯ or the sidebar) | ok |  |
| phone-en | 122 | This device lists the places, the internal storage first | ok |  |
| phone-en | 123 | Internal storage opens and lists its folders (Music among them) | ok | rows: 1:21, Alarms, Android, Audiobooks, DCIM, Documents, Download, Movies, Music, Nagmedar Artist 1, Name, Notifications |
| phone-en | 124 | the Music folder lists the walk's files (tone.mp3) | ok |  |
| phone-en | 125 | a file of the folder plays | ok | mini player |
| phone-en | 126 | back up at the drive, its folders listed | ok |  |
| phone-en | 126 | the sort menu's button | ok |  |
| phone-en | 127 | the sort menu lists the orders and the hidden files | ok |  |
| phone-en | 128 | after 'By size' the drive still lists its folders | ok |  |
| phone-en | 128 | the sort menu's button | ok |  |
| phone-en | 129 | the sort menu lists the orders and the hidden files | ok |  |
| phone-en | 130 | after 'By name' the drive still lists its folders | ok |  |
| phone-en | 130 | the sort menu's button | ok |  |
| phone-en | 131 | the sort menu lists the orders and the hidden files | ok |  |
| phone-en | 132 | after 'Show hidden files' the drive still lists its folders | ok |  |
| phone-en | 132 | the sort menu's button | ok |  |
| phone-en | 133 | the sort menu lists the orders and the hidden files | ok |  |
| phone-en | 134 | after 'Show hidden files' the drive still lists its folders | ok |  |
| phone-en | 135 | Back goes on to the places | ok |  |
| phone-en | 139 | after Home the folder is still open | ok |  |
| phone-en | 140 | rotated, the folder is still open | ok |  |
| phone-en | 141 | rotated back, the folder is still open | ok |  |
| workstation | 161 | the Music shows the inspector | ok |  |
| workstation | 162 | Ctrl+I hides the inspector | ok |  |
| workstation | 163 | Ctrl+I shows the inspector again | ok |  |
| workstation | 163 | the table's column titles | ok | Title, Artist, Album, Year, Duration |
| workstation | 164 | the column chooser opens | ok |  |
| workstation | 165 | Escape closes the column chooser | ok |  |
| workstation | 166 | This device is found (⋯ or the sidebar) | ok |  |
| workstation | 167 | This device lists the places, the internal storage first | ok |  |
| workstation | 168 | Internal storage opens and lists its folders (Music among them) | ok | rows: Alarms, Android, Audiobooks, Changed, DCIM, Documents, Download, Movies, Music, Name, Notifications, Nov 5, 2026 12:21 PM |
| workstation | 169 | the Music folder lists the walk's files (tone.mp3) | ok |  |
| workstation | 170 | a file of the folder plays | ok | transport bar: tone |
| workstation | 170 | the transport bar's open button | ok |  |
| workstation | 171 | the bar's open button opens the big player | ok |  |
| workstation | 171 | the big player covers the window | ok | player (0, 48, 2560, 1536) in 2560x1600 |
| workstation | 171 | the bars stand aside under it | ok |  |
| workstation | 173 | Back closes the big player | ok |  |
| workstation | 173 | the trail names the drive | ok |  |
| workstation | 174 | back up at the drive, its folders listed | ok |  |
| workstation | 174 | the sort menu's button | ok |  |
| workstation | 175 | the sort menu lists the orders and the hidden files | ok |  |
| workstation | 176 | after 'By size' the drive still lists its folders | ok |  |
| workstation | 176 | the sort menu's button | ok |  |
| workstation | 177 | the sort menu lists the orders and the hidden files | ok |  |
| workstation | 178 | after 'By name' the drive still lists its folders | ok |  |
| workstation | 178 | the sort menu's button | ok |  |
| workstation | 179 | the sort menu lists the orders and the hidden files | ok |  |
| workstation | 180 | after 'Show hidden files' the drive still lists its folders | ok |  |
| workstation | 180 | the sort menu's button | ok |  |
| workstation | 181 | the sort menu lists the orders and the hidden files | ok |  |
| workstation | 182 | after 'Show hidden files' the drive still lists its folders | ok |  |
| workstation | 182 | the places stay beside the folder | ok |  |
| workstation | 185 | after Home the folder is still open | ok |  |
| workstation | 186 | rotated, the folder is still open | ok |  |
| workstation | 187 | rotated back, the folder is still open | ok |  |
| phone-tr | 189 | bottom bar is Müzik · Podcast · Radyo · Daha fazla | ok | seen: Müzik · Podcast · Radyo · Daha fazla |
| phone-tr | 190 | the top bar keeps its colour while the list scrolls | ok | at rest (250, 251, 252), while dragged (250, 251, 252), after (250, 251, 252) |
| phone-tr | 191 | ⋯ in the bottom bar | ok |  |
| phone-tr | 192 | ⋯ opens the More page | ok | seen: İndirmeler, Ayarlar, Çalma listeleri |
| phone-tr | 192 | Settings on the More page | ok |  |
| phone-tr | 193 | Settings is a page of its own: no bottom bar | ok | bottom bar: none |
| phone-tr | 193 | Settings has a back arrow | ok |  |
| phone-tr | 194 | Back from Settings shows More | ok | title: Kütüphane, Daha fazla |
| phone-tr | 196 | the back arrow returns to More | ok |  |
| phone-tr | 196 | Bu cihaz is found (⋯ or the sidebar) | ok |  |
| phone-tr | 197 | Bu cihaz lists the places, the internal storage first | ok |  |
| phone-tr | 198 | Dahili depolama opens and lists its folders (Music among them) | ok | rows: 5 Kas 2026 12:21, 5 Kas 2026 12:31, Ad, Alarms, Android, Audiobooks, DCIM, Documents, Download, Movies, Music, Notifications |
| phone-tr | 199 | the Music folder lists the walk's files (tone.mp3) | ok |  |
| phone-tr | 200 | a file of the folder plays | ok | mini player |
| phone-tr | 201 | back up at the drive, its folders listed | ok |  |
| phone-tr | 201 | the sort menu's button | ok |  |
| phone-tr | 202 | the sort menu lists the orders and the hidden files | ok |  |
| phone-tr | 203 | after 'Boyuta göre' the drive still lists its folders | ok |  |
| phone-tr | 203 | the sort menu's button | ok |  |
| phone-tr | 204 | the sort menu lists the orders and the hidden files | ok |  |
| phone-tr | 205 | after 'Ada göre' the drive still lists its folders | ok |  |
| phone-tr | 205 | the sort menu's button | ok |  |
| phone-tr | 206 | the sort menu lists the orders and the hidden files | ok |  |
| phone-tr | 207 | after 'Gizli dosyaları göster' the drive still lists its folders | ok |  |
| phone-tr | 207 | the sort menu's button | ok |  |
| phone-tr | 208 | the sort menu lists the orders and the hidden files | ok |  |
| phone-tr | 209 | after 'Gizli dosyaları göster' the drive still lists its folders | ok |  |
| phone-tr | 210 | Back goes on to the places | ok |  |
| phone-tr | 214 | after Home the folder is still open | ok |  |
| phone-tr | 215 | rotated, the folder is still open | ok |  |
| phone-tr | 216 | rotated back, the folder is still open | ok |  |
| all | 246 | no film, series, video, Home, TV Lab or Whisper words on the app's screens | ok |  |

## Frames (dumpsys gfxinfo; informational on an emulator, over 40 % janky is flagged)

| pass | step | gesture | frames | janky | 50th | 90th | 95th | 99th |
|------|------|---------|--------|-------|------|------|------|------|
| phone-en | 105 | Music list scroll | 65 | 69.2 % **(over 40 %)** | 48 ms | 101 ms | 133 ms | 200 ms |
| phone-en | 108 | player drag up | 33 | 97.0 % **(over 40 %)** | 73 ms | 150 ms | 200 ms | 200 ms |
| phone-en | 109 | player drag down | 34 | 85.3 % **(over 40 %)** | 61 ms | 109 ms | 113 ms | 150 ms |
| workstation | 165 | Music table scroll (tablet) | 34 | 97.1 % **(over 40 %)** | 65 ms | 125 ms | 150 ms | 150 ms |

## Steps

| # | pass | step | result | screenshot | note |
|---|------|------|--------|------------|------|
| 1 | tablet-en | Launch (first run) | ok | [001-tablet-en-launch-first-run.png](shots/001-tablet-en-launch-first-run.png) |  |
| 2 | tablet-en | First run step 1 (Continue) | ok | [002-tablet-en-first-run-step-1-continue.png](shots/002-tablet-en-first-run-step-1-continue.png) |  |
| 3 | tablet-en | First run step 2 (Continue · 1 source) | ok | [003-tablet-en-first-run-step-2-continue-1-source.png](shots/003-tablet-en-first-run-step-2-continue-1-source.png) |  |
| 4 | tablet-en | First run step 3 (Continue) | ok | [004-tablet-en-first-run-step-3-continue.png](shots/004-tablet-en-first-run-step-3-continue.png) |  |
| 5 | tablet-en | First run step 4 (Start) | ok | [005-tablet-en-first-run-step-4-start.png](shots/005-tablet-en-first-run-step-4-start.png) |  |
| 6 | tablet-en | After the first run | ok | [006-tablet-en-after-the-first-run.png](shots/006-tablet-en-after-the-first-run.png) |  |
| 7 | tablet-en | Open Music | ok | [007-tablet-en-open-music.png](shots/007-tablet-en-open-music.png) |  |
| 8 | tablet-en | Open Podcasts | ok | [008-tablet-en-open-podcasts.png](shots/008-tablet-en-open-podcasts.png) |  |
| 9 | tablet-en | Open Radio | ok | [009-tablet-en-open-radio.png](shots/009-tablet-en-open-radio.png) |  |
| 10 | tablet-en | Open This device | ok | [010-tablet-en-open-this-device.png](shots/010-tablet-en-open-this-device.png) |  |
| 11 | tablet-en | Open Network | ok | [011-tablet-en-open-network.png](shots/011-tablet-en-open-network.png) |  |
| 12 | tablet-en | Open Streams | ok | [012-tablet-en-open-streams.png](shots/012-tablet-en-open-streams.png) |  |
| 13 | tablet-en | Open Downloads | ok | [013-tablet-en-open-downloads.png](shots/013-tablet-en-open-downloads.png) |  |
| 14 | tablet-en | Open Playlists | ok | [014-tablet-en-open-playlists.png](shots/014-tablet-en-open-playlists.png) |  |
| 15 | tablet-en | Settings | ok | [015-tablet-en-settings.png](shots/015-tablet-en-settings.png) |  |
| 16 | tablet-en | Settings scroll 1 | ok | [016-tablet-en-settings-scroll-1.png](shots/016-tablet-en-settings-scroll-1.png) |  |
| 17 | tablet-en | Settings scroll 2 | ok | [017-tablet-en-settings-scroll-2.png](shots/017-tablet-en-settings-scroll-2.png) |  |
| 18 | tablet-en | Settings scroll 3 | ok | [018-tablet-en-settings-scroll-3.png](shots/018-tablet-en-settings-scroll-3.png) |  |
| 19 | tablet-en | Settings scroll 4 | ok | [019-tablet-en-settings-scroll-4.png](shots/019-tablet-en-settings-scroll-4.png) |  |
| 20 | tablet-en | Settings scroll 5 | ok | [020-tablet-en-settings-scroll-5.png](shots/020-tablet-en-settings-scroll-5.png) |  |
| 21 | tablet-en | Settings scroll 6 | ok | [021-tablet-en-settings-scroll-6.png](shots/021-tablet-en-settings-scroll-6.png) |  |
| 22 | tablet-en | Settings scroll 7 | ok | [022-tablet-en-settings-scroll-7.png](shots/022-tablet-en-settings-scroll-7.png) |  |
| 23 | tablet-en | Settings scroll 8 | ok | [023-tablet-en-settings-scroll-8.png](shots/023-tablet-en-settings-scroll-8.png) |  |
| 24 | tablet-en | Settings scroll 9 | ok | [024-tablet-en-settings-scroll-9.png](shots/024-tablet-en-settings-scroll-9.png) |  |
| 25 | tablet-en | Settings scroll 10 | ok | [025-tablet-en-settings-scroll-10.png](shots/025-tablet-en-settings-scroll-10.png) |  |
| 26 | tablet-en | Settings scroll 11 | ok | [026-tablet-en-settings-scroll-11.png](shots/026-tablet-en-settings-scroll-11.png) |  |
| 27 | tablet-en | Settings scroll 12 | ok | [027-tablet-en-settings-scroll-12.png](shots/027-tablet-en-settings-scroll-12.png) |  |
| 28 | tablet-en | Settings scroll 13 | ok | [028-tablet-en-settings-scroll-13.png](shots/028-tablet-en-settings-scroll-13.png) |  |
| 29 | tablet-en | Settings scroll 14 | ok | [029-tablet-en-settings-scroll-14.png](shots/029-tablet-en-settings-scroll-14.png) |  |
| 30 | tablet-en | Settings scroll 15 | ok | [030-tablet-en-settings-scroll-15.png](shots/030-tablet-en-settings-scroll-15.png) |  |
| 31 | tablet-en | Settings scroll 16 | ok | [031-tablet-en-settings-scroll-16.png](shots/031-tablet-en-settings-scroll-16.png) |  |
| 32 | tablet-en | Settings scroll 17 | ok | [032-tablet-en-settings-scroll-17.png](shots/032-tablet-en-settings-scroll-17.png) |  |
| 33 | tablet-en | Settings scroll 18 | ok | [033-tablet-en-settings-scroll-18.png](shots/033-tablet-en-settings-scroll-18.png) |  |
| 34 | tablet-en | Settings scroll 19 | ok | [034-tablet-en-settings-scroll-19.png](shots/034-tablet-en-settings-scroll-19.png) |  |
| 35 | tablet-en | Settings scroll 20 | ok | [035-tablet-en-settings-scroll-20.png](shots/035-tablet-en-settings-scroll-20.png) |  |
| 36 | tablet-en | Settings scroll 21 | ok | [036-tablet-en-settings-scroll-21.png](shots/036-tablet-en-settings-scroll-21.png) |  |
| 37 | tablet-en | Settings scroll 22 | ok | [037-tablet-en-settings-scroll-22.png](shots/037-tablet-en-settings-scroll-22.png) |  |
| 38 | tablet-en | Settings scroll 23 | ok | [038-tablet-en-settings-scroll-23.png](shots/038-tablet-en-settings-scroll-23.png) |  |
| 39 | tablet-en | Settings scroll 24 | ok | [039-tablet-en-settings-scroll-24.png](shots/039-tablet-en-settings-scroll-24.png) |  |
| 40 | tablet-en | Open About | ok | [040-tablet-en-open-about.png](shots/040-tablet-en-open-about.png) |  |
| 41 | tablet-en | Back from About | ok | [041-tablet-en-back-from-about.png](shots/041-tablet-en-back-from-about.png) |  |
| 42 | phone-en | Launch (phone) | ok | [042-phone-en-launch-phone.png](shots/042-phone-en-launch-phone.png) |  |
| 43 | phone-en | Open Music | ok | [043-phone-en-open-music.png](shots/043-phone-en-open-music.png) |  |
| 44 | phone-en | Open Podcast | ok | [044-phone-en-open-podcast.png](shots/044-phone-en-open-podcast.png) |  |
| 45 | phone-en | Open Podcasts | ok | [045-phone-en-open-podcasts.png](shots/045-phone-en-open-podcasts.png) |  |
| 46 | phone-en | Open Radio | ok | [046-phone-en-open-radio.png](shots/046-phone-en-open-radio.png) |  |
| 47 | phone-en | Open More (overflow) | ok | [047-phone-en-open-more-overflow.png](shots/047-phone-en-open-more-overflow.png) |  |
| 48 | phone-en | Open This device | ok | [048-phone-en-open-this-device.png](shots/048-phone-en-open-this-device.png) |  |
| 49 | phone-en | Open More (overflow) | ok | [049-phone-en-open-more-overflow.png](shots/049-phone-en-open-more-overflow.png) |  |
| 50 | phone-en | Open Network | ok | [050-phone-en-open-network.png](shots/050-phone-en-open-network.png) |  |
| 51 | phone-en | Open More (overflow) | ok | [051-phone-en-open-more-overflow.png](shots/051-phone-en-open-more-overflow.png) |  |
| 52 | phone-en | Open Streams | ok | [052-phone-en-open-streams.png](shots/052-phone-en-open-streams.png) |  |
| 53 | phone-en | Open More (overflow) | ok | [053-phone-en-open-more-overflow.png](shots/053-phone-en-open-more-overflow.png) |  |
| 54 | phone-en | Open Downloads | ok | [054-phone-en-open-downloads.png](shots/054-phone-en-open-downloads.png) |  |
| 55 | phone-en | Open More (overflow) | ok | [055-phone-en-open-more-overflow.png](shots/055-phone-en-open-more-overflow.png) |  |
| 56 | phone-en | Open Playlists | ok | [056-phone-en-open-playlists.png](shots/056-phone-en-open-playlists.png) |  |
| 57 | phone-en | Open More (overflow) | ok | [057-phone-en-open-more-overflow.png](shots/057-phone-en-open-more-overflow.png) |  |
| 58 | phone-en | Settings | ok | [058-phone-en-settings.png](shots/058-phone-en-settings.png) |  |
| 59 | phone-en | Settings scroll 1 | ok | [059-phone-en-settings-scroll-1.png](shots/059-phone-en-settings-scroll-1.png) |  |
| 60 | phone-en | Settings scroll 2 | ok | [060-phone-en-settings-scroll-2.png](shots/060-phone-en-settings-scroll-2.png) |  |
| 61 | phone-en | Settings scroll 3 | ok | [061-phone-en-settings-scroll-3.png](shots/061-phone-en-settings-scroll-3.png) |  |
| 62 | phone-en | Settings scroll 4 | ok | [062-phone-en-settings-scroll-4.png](shots/062-phone-en-settings-scroll-4.png) |  |
| 63 | phone-en | Settings scroll 5 | ok | [063-phone-en-settings-scroll-5.png](shots/063-phone-en-settings-scroll-5.png) |  |
| 64 | phone-en | Settings scroll 6 | ok | [064-phone-en-settings-scroll-6.png](shots/064-phone-en-settings-scroll-6.png) |  |
| 65 | phone-en | Settings scroll 7 | ok | [065-phone-en-settings-scroll-7.png](shots/065-phone-en-settings-scroll-7.png) |  |
| 66 | phone-en | Settings scroll 8 | ok | [066-phone-en-settings-scroll-8.png](shots/066-phone-en-settings-scroll-8.png) |  |
| 67 | phone-en | Settings scroll 9 | ok | [067-phone-en-settings-scroll-9.png](shots/067-phone-en-settings-scroll-9.png) |  |
| 68 | phone-en | Settings scroll 10 | ok | [068-phone-en-settings-scroll-10.png](shots/068-phone-en-settings-scroll-10.png) |  |
| 69 | phone-en | Open About | ok | [069-phone-en-open-about.png](shots/069-phone-en-open-about.png) |  |
| 70 | phone-en | Back from About | ok | [070-phone-en-back-from-about.png](shots/070-phone-en-back-from-about.png) |  |
| 71 | phone-en | Show the first run again | ok | [071-phone-en-show-the-first-run-again.png](shots/071-phone-en-show-the-first-run-again.png) |  |
| 72 | phone-en | First run again step 1 (Continue) | ok | [072-phone-en-first-run-again-step-1-continue.png](shots/072-phone-en-first-run-again-step-1-continue.png) |  |
| 73 | phone-en | First run again step 2 (Continue · 2 sources) | ok | [073-phone-en-first-run-again-step-2-continue-2-sources.png](shots/073-phone-en-first-run-again-step-2-continue-2-sources.png) |  |
| 74 | phone-en | First run again step 3 (Continue) | ok | [074-phone-en-first-run-again-step-3-continue.png](shots/074-phone-en-first-run-again-step-3-continue.png) |  |
| 75 | phone-en | First run again step 4 (Start) | ok | [075-phone-en-first-run-again-step-4-start.png](shots/075-phone-en-first-run-again-step-4-start.png) |  |
| 76 | phone-en | After the scan | ok | [076-phone-en-after-the-scan.png](shots/076-phone-en-after-the-scan.png) |  |
| 77 | phone-en | Reopen Music | ok | [077-phone-en-reopen-music.png](shots/077-phone-en-reopen-music.png) |  |
| 78 | phone-en | Reopen Podcast | ok | [078-phone-en-reopen-podcast.png](shots/078-phone-en-reopen-podcast.png) |  |
| 79 | phone-en | Reopen Podcasts | ok | [079-phone-en-reopen-podcasts.png](shots/079-phone-en-reopen-podcasts.png) |  |
| 80 | phone-en | Reopen Radio | ok | [080-phone-en-reopen-radio.png](shots/080-phone-en-reopen-radio.png) |  |
| 81 | phone-en | Open More (overflow) | ok | [081-phone-en-open-more-overflow.png](shots/081-phone-en-open-more-overflow.png) |  |
| 82 | phone-en | Reopen This device | ok | [082-phone-en-reopen-this-device.png](shots/082-phone-en-reopen-this-device.png) |  |
| 83 | phone-en | Open More (overflow) | ok | [083-phone-en-open-more-overflow.png](shots/083-phone-en-open-more-overflow.png) |  |
| 84 | phone-en | Reopen Network | ok | [084-phone-en-reopen-network.png](shots/084-phone-en-reopen-network.png) |  |
| 85 | phone-en | Open More (overflow) | ok | [085-phone-en-open-more-overflow.png](shots/085-phone-en-open-more-overflow.png) |  |
| 86 | phone-en | Reopen Streams | ok | [086-phone-en-reopen-streams.png](shots/086-phone-en-reopen-streams.png) |  |
| 87 | phone-en | Open More (overflow) | ok | [087-phone-en-open-more-overflow.png](shots/087-phone-en-open-more-overflow.png) |  |
| 88 | phone-en | Reopen Downloads | ok | [088-phone-en-reopen-downloads.png](shots/088-phone-en-reopen-downloads.png) |  |
| 89 | phone-en | Open More (overflow) | ok | [089-phone-en-open-more-overflow.png](shots/089-phone-en-open-more-overflow.png) |  |
| 90 | phone-en | Reopen Playlists | ok | [090-phone-en-reopen-playlists.png](shots/090-phone-en-reopen-playlists.png) |  |
| 91 | phone-en | Open More (overflow) | ok | [091-phone-en-open-more-overflow.png](shots/091-phone-en-open-more-overflow.png) |  |
| 92 | phone-en | Music | ok | [092-phone-en-music.png](shots/092-phone-en-music.png) |  |
| 93 | phone-en | A track's ⋯ (More actions for Profile track 1.1) | ok | [093-phone-en-a-track-s-more-actions-for-profile-track-1-1.png](shots/093-phone-en-a-track-s-more-actions-for-profile-track-1-1.png) |  |
| 94 | phone-en | Back from the track's sheet | ok | [094-phone-en-back-from-the-track-s-sheet.png](shots/094-phone-en-back-from-the-track-s-sheet.png) |  |
| 95 | phone-en | A track's ⋯ again | ok | [095-phone-en-a-track-s-again.png](shots/095-phone-en-a-track-s-again.png) |  |
| 96 | phone-en | Drag the sheet down from its middle | ok | [096-phone-en-drag-the-sheet-down-from-its-middle.png](shots/096-phone-en-drag-the-sheet-down-from-its-middle.png) | check failed: a drag down from the sheet's middle closes it |
| 97 | phone-en | Music | ok | [097-phone-en-music.png](shots/097-phone-en-music.png) |  |
| 98 | phone-en | Music scrolled (top bar) | ok | [098-phone-en-music-scrolled-top-bar.png](shots/098-phone-en-music-scrolled-top-bar.png) |  |
| 99 | phone-en | Music | ok | [099-phone-en-music.png](shots/099-phone-en-music.png) |  |
| 100 | phone-en | Bottom bar ⋯ | ok | [100-phone-en-bottom-bar.png](shots/100-phone-en-bottom-bar.png) |  |
| 101 | phone-en | Settings from More | ok | [101-phone-en-settings-from-more.png](shots/101-phone-en-settings-from-more.png) |  |
| 102 | phone-en | Back from Settings | ok | [102-phone-en-back-from-settings.png](shots/102-phone-en-back-from-settings.png) |  |
| 103 | phone-en | Settings from More again | ok | [103-phone-en-settings-from-more-again.png](shots/103-phone-en-settings-from-more-again.png) |  |
| 104 | phone-en | The back arrow | ok | [104-phone-en-the-back-arrow.png](shots/104-phone-en-the-back-arrow.png) |  |
| 105 | phone-en | Music | ok | [105-phone-en-music.png](shots/105-phone-en-music.png) |  |
| 106 | phone-en | Music list scroll (after the scroll) | ok | [106-phone-en-music-list-scroll-after-the-scroll.png](shots/106-phone-en-music-list-scroll-after-the-scroll.png) |  |
| 107 | phone-en | Play Profile track 1.1 | ok | [107-phone-en-play-profile-track-1-1.png](shots/107-phone-en-play-profile-track-1-1.png) |  |
| 108 | phone-en | Drag the mini player up | ok | [108-phone-en-drag-the-mini-player-up.png](shots/108-phone-en-drag-the-mini-player-up.png) |  |
| 109 | phone-en | Drag the full player down | ok | [109-phone-en-drag-the-full-player-down.png](shots/109-phone-en-drag-the-full-player-down.png) |  |
| 110 | phone-en | Swipe the mini player sideways | ok | [110-phone-en-swipe-the-mini-player-sideways.png](shots/110-phone-en-swipe-the-mini-player-sideways.png) |  |
| 111 | phone-en | Swipe along the bar's seek bar | ok | [111-phone-en-swipe-along-the-bar-s-seek-bar.png](shots/111-phone-en-swipe-along-the-bar-s-seek-bar.png) |  |
| 112 | phone-en | Podcasts | ok | [112-phone-en-podcasts.png](shots/112-phone-en-podcasts.png) |  |
| 113 | phone-en | Add a podcast by its address | ok | [113-phone-en-add-a-podcast-by-its-address.png](shots/113-phone-en-add-a-podcast-by-its-address.png) |  |
| 114 | phone-en | The feed's address typed | ok | [114-phone-en-the-feed-s-address-typed.png](shots/114-phone-en-the-feed-s-address-typed.png) |  |
| 115 | phone-en | Subscribe | ok | [115-phone-en-subscribe.png](shots/115-phone-en-subscribe.png) |  |
| 116 | phone-en | Followed | ok | [116-phone-en-followed.png](shots/116-phone-en-followed.png) |  |
| 117 | phone-en | Episode page (the play button watched) | ok | [117-phone-en-episode-page-the-play-button-watched.png](shots/117-phone-en-episode-page-the-play-button-watched.png) |  |
| 118 | phone-en | Back from the episode | ok | [118-phone-en-back-from-the-episode.png](shots/118-phone-en-back-from-the-episode.png) |  |
| 119 | phone-en | Back from the podcast | ok | [119-phone-en-back-from-the-podcast.png](shots/119-phone-en-back-from-the-podcast.png) |  |
| 120 | phone-en | Radio | ok | [120-phone-en-radio.png](shots/120-phone-en-radio.png) |  |
| 121 | phone-en | Android Auto's tree read by a MediaBrowserCompat | ok | [121-phone-en-android-auto-s-tree-read-by-a-mediabrowsercompat.png](shots/121-phone-en-android-auto-s-tree-read-by-a-mediabrowsercompat.png) | AutoBrowseTest: app.nagmedar.macrobenchmark.AutoBrowseTest:. Time: 4.782 OK (1 test) |
| 122 | phone-en | Open This device | ok | [122-phone-en-open-this-device.png](shots/122-phone-en-open-this-device.png) |  |
| 123 | phone-en | Open Internal storage | ok | [123-phone-en-open-internal-storage.png](shots/123-phone-en-open-internal-storage.png) |  |
| 124 | phone-en | Open its Music folder | ok | [124-phone-en-open-its-music-folder.png](shots/124-phone-en-open-its-music-folder.png) |  |
| 125 | phone-en | Play tone.mp3 from the folder | ok | [125-phone-en-play-tone-mp3-from-the-folder.png](shots/125-phone-en-play-tone-mp3-from-the-folder.png) |  |
| 126 | phone-en | Back (up to Internal storage) | ok | [126-phone-en-back-up-to-internal-storage.png](shots/126-phone-en-back-up-to-internal-storage.png) |  |
| 127 | phone-en | Sort menu | ok | [127-phone-en-sort-menu.png](shots/127-phone-en-sort-menu.png) |  |
| 128 | phone-en | Sort menu: By size | ok | [128-phone-en-sort-menu-by-size.png](shots/128-phone-en-sort-menu-by-size.png) |  |
| 129 | phone-en | Sort menu | ok | [129-phone-en-sort-menu.png](shots/129-phone-en-sort-menu.png) |  |
| 130 | phone-en | Sort menu: By name | ok | [130-phone-en-sort-menu-by-name.png](shots/130-phone-en-sort-menu-by-name.png) |  |
| 131 | phone-en | Sort menu | ok | [131-phone-en-sort-menu.png](shots/131-phone-en-sort-menu.png) |  |
| 132 | phone-en | Sort menu: Show hidden files | ok | [132-phone-en-sort-menu-show-hidden-files.png](shots/132-phone-en-sort-menu-show-hidden-files.png) |  |
| 133 | phone-en | Sort menu | ok | [133-phone-en-sort-menu.png](shots/133-phone-en-sort-menu.png) |  |
| 134 | phone-en | Sort menu: Show hidden files | ok | [134-phone-en-sort-menu-show-hidden-files.png](shots/134-phone-en-sort-menu-show-hidden-files.png) |  |
| 135 | phone-en | Back (to the places) | ok | [135-phone-en-back-to-the-places.png](shots/135-phone-en-back-to-the-places.png) |  |
| 136 | phone-en | Open Internal storage again | ok | [136-phone-en-open-internal-storage-again.png](shots/136-phone-en-open-internal-storage-again.png) |  |
| 137 | phone-en | Open the Music folder again | ok | [137-phone-en-open-the-music-folder-again.png](shots/137-phone-en-open-the-music-folder-again.png) |  |
| 138 | phone-en | Home with the folder open | warn | [138-phone-en-home-with-the-folder-open.png](shots/138-phone-en-home-with-the-folder-open.png) | the app is not in the foreground (windows: com.google.android.apps.nexuslauncher, com.google.android.deskclock, com.google.android.googlequicksearchbox) |
| 139 | phone-en | Back in the app with the folder open | ok | [139-phone-en-back-in-the-app-with-the-folder-open.png](shots/139-phone-en-back-in-the-app-with-the-folder-open.png) |  |
| 140 | phone-en | Rotated with the folder open | ok | [140-phone-en-rotated-with-the-folder-open.png](shots/140-phone-en-rotated-with-the-folder-open.png) |  |
| 141 | phone-en | Rotated back with the folder open | ok | [141-phone-en-rotated-back-with-the-folder-open.png](shots/141-phone-en-rotated-back-with-the-folder-open.png) |  |
| 142 | phone-en | Back (up to the drive) | ok | [142-phone-en-back-up-to-the-drive.png](shots/142-phone-en-back-up-to-the-drive.png) |  |
| 143 | phone-en | Back (to the places) | ok | [143-phone-en-back-to-the-places.png](shots/143-phone-en-back-to-the-places.png) |  |
| 144 | phone-en | Open the audio file | ok | [144-phone-en-open-the-audio-file.png](shots/144-phone-en-open-the-audio-file.png) | Starting: Intent { act=android.intent.action.VIEW dat=file:///... typ=audio/mpeg pkg=app.nagmedar.debug } |
| 145 | phone-en | Playing the audio file | ok | [145-phone-en-playing-the-audio-file.png](shots/145-phone-en-playing-the-audio-file.png) |  |
| 146 | phone-en | Back from the audio file | ok | [146-phone-en-back-from-the-audio-file.png](shots/146-phone-en-back-from-the-audio-file.png) |  |
| 147 | phone-en | Home during playback | warn | [147-phone-en-home-during-playback.png](shots/147-phone-en-home-during-playback.png) | the app is not in the foreground (windows: com.google.android.apps.nexuslauncher, com.google.android.deskclock, com.google.android.googlequicksearchbox) |
| 148 | phone-en | Back to the app after playback | ok | [148-phone-en-back-to-the-app-after-playback.png](shots/148-phone-en-back-to-the-app-after-playback.png) |  |
| 149 | phone-en | Command palette (Ctrl+K) | ok | [149-phone-en-command-palette-ctrl-k.png](shots/149-phone-en-command-palette-ctrl-k.png) |  |
| 150 | phone-en | Palette query | ok | [150-phone-en-palette-query.png](shots/150-phone-en-palette-query.png) |  |
| 151 | phone-en | Palette: select the first result | ok | [151-phone-en-palette-select-the-first-result.png](shots/151-phone-en-palette-select-the-first-result.png) |  |
| 152 | phone-en | Escape | ok | [152-phone-en-escape.png](shots/152-phone-en-escape.png) |  |
| 153 | phone-en | Shortcut list (Ctrl+/) | ok | [153-phone-en-shortcut-list-ctrl.png](shots/153-phone-en-shortcut-list-ctrl.png) |  |
| 154 | phone-en | Settings (Ctrl+,) | ok | [154-phone-en-settings-ctrl.png](shots/154-phone-en-settings-ctrl.png) |  |
| 155 | phone-en | Rotated to landscape | ok | [155-phone-en-rotated-to-landscape.png](shots/155-phone-en-rotated-to-landscape.png) |  |
| 156 | phone-en | Rotated back | ok | [156-phone-en-rotated-back.png](shots/156-phone-en-rotated-back.png) |  |
| 157 | phone-en | In the background (home screen) | warn | [157-phone-en-in-the-background-home-screen.png](shots/157-phone-en-in-the-background-home-screen.png) | the app is not in the foreground (windows: com.google.android.apps.nexuslauncher, com.google.android.deskclock, com.google.android.googlequicksearchbox) |
| 158 | phone-en | Foreground again | ok | [158-phone-en-foreground-again.png](shots/158-phone-en-foreground-again.png) |  |
| 159 | phone-en | After a memory-trim request | ok | [159-phone-en-after-a-memory-trim-request.png](shots/159-phone-en-after-a-memory-trim-request.png) |  |
| 160 | phone-en | Cold start after the process was killed | ok | [160-phone-en-cold-start-after-the-process-was-killed.png](shots/160-phone-en-cold-start-after-the-process-was-killed.png) |  |
| 161 | workstation | Music | ok | [161-workstation-music.png](shots/161-workstation-music.png) |  |
| 162 | workstation | Ctrl+I (hide the inspector) | ok | [162-workstation-ctrl-i-hide-the-inspector.png](shots/162-workstation-ctrl-i-hide-the-inspector.png) |  |
| 163 | workstation | Ctrl+I (show the inspector) | ok | [163-workstation-ctrl-i-show-the-inspector.png](shots/163-workstation-ctrl-i-show-the-inspector.png) |  |
| 164 | workstation | Long press on the column title Artist | ok | [164-workstation-long-press-on-the-column-title-artist.png](shots/164-workstation-long-press-on-the-column-title-artist.png) |  |
| 165 | workstation | Escape closes the column chooser | ok | [165-workstation-escape-closes-the-column-chooser.png](shots/165-workstation-escape-closes-the-column-chooser.png) |  |
| 166 | workstation | Music table scroll (tablet) (after the scroll) | ok | [166-workstation-music-table-scroll-tablet-after-the-scroll.png](shots/166-workstation-music-table-scroll-tablet-after-the-scroll.png) |  |
| 167 | workstation | Open This device | ok | [167-workstation-open-this-device.png](shots/167-workstation-open-this-device.png) |  |
| 168 | workstation | Open Internal storage | ok | [168-workstation-open-internal-storage.png](shots/168-workstation-open-internal-storage.png) |  |
| 169 | workstation | Open its Music folder | ok | [169-workstation-open-its-music-folder.png](shots/169-workstation-open-its-music-folder.png) |  |
| 170 | workstation | Play tone.mp3 from the folder | ok | [170-workstation-play-tone-mp3-from-the-folder.png](shots/170-workstation-play-tone-mp3-from-the-folder.png) |  |
| 171 | workstation | The bar's open button | ok | [171-workstation-the-bar-s-open-button.png](shots/171-workstation-the-bar-s-open-button.png) |  |
| 172 | workstation | The big player (screenshot) | ok | [172-workstation-the-big-player-screenshot.png](shots/172-workstation-the-big-player-screenshot.png) |  |
| 173 | workstation | Back: the big player closes | ok | [173-workstation-back-the-big-player-closes.png](shots/173-workstation-back-the-big-player-closes.png) |  |
| 174 | workstation | The trail back to Internal storage | ok | [174-workstation-the-trail-back-to-internal-storage.png](shots/174-workstation-the-trail-back-to-internal-storage.png) |  |
| 175 | workstation | Sort menu | ok | [175-workstation-sort-menu.png](shots/175-workstation-sort-menu.png) |  |
| 176 | workstation | Sort menu: By size | ok | [176-workstation-sort-menu-by-size.png](shots/176-workstation-sort-menu-by-size.png) |  |
| 177 | workstation | Sort menu | ok | [177-workstation-sort-menu.png](shots/177-workstation-sort-menu.png) |  |
| 178 | workstation | Sort menu: By name | ok | [178-workstation-sort-menu-by-name.png](shots/178-workstation-sort-menu-by-name.png) |  |
| 179 | workstation | Sort menu | ok | [179-workstation-sort-menu.png](shots/179-workstation-sort-menu.png) |  |
| 180 | workstation | Sort menu: Show hidden files | ok | [180-workstation-sort-menu-show-hidden-files.png](shots/180-workstation-sort-menu-show-hidden-files.png) |  |
| 181 | workstation | Sort menu | ok | [181-workstation-sort-menu.png](shots/181-workstation-sort-menu.png) |  |
| 182 | workstation | Sort menu: Show hidden files | ok | [182-workstation-sort-menu-show-hidden-files.png](shots/182-workstation-sort-menu-show-hidden-files.png) |  |
| 183 | workstation | Open the Music folder again | ok | [183-workstation-open-the-music-folder-again.png](shots/183-workstation-open-the-music-folder-again.png) |  |
| 184 | workstation | Home with the folder open | warn | [184-workstation-home-with-the-folder-open.png](shots/184-workstation-home-with-the-folder-open.png) | the app is not in the foreground (windows: com.google.android.apps.nexuslauncher, com.google.android.deskclock, com.google.android.googlequicksearchbox) |
| 185 | workstation | Back in the app with the folder open | ok | [185-workstation-back-in-the-app-with-the-folder-open.png](shots/185-workstation-back-in-the-app-with-the-folder-open.png) |  |
| 186 | workstation | Rotated with the folder open | ok | [186-workstation-rotated-with-the-folder-open.png](shots/186-workstation-rotated-with-the-folder-open.png) |  |
| 187 | workstation | Rotated back with the folder open | ok | [187-workstation-rotated-back-with-the-folder-open.png](shots/187-workstation-rotated-back-with-the-folder-open.png) |  |
| 188 | phone-tr | Launch (Turkish) | ok | [188-phone-tr-launch-turkish.png](shots/188-phone-tr-launch-turkish.png) |  |
| 189 | phone-tr | Music | ok | [189-phone-tr-music.png](shots/189-phone-tr-music.png) |  |
| 190 | phone-tr | Music scrolled (top bar) | ok | [190-phone-tr-music-scrolled-top-bar.png](shots/190-phone-tr-music-scrolled-top-bar.png) |  |
| 191 | phone-tr | Music | ok | [191-phone-tr-music.png](shots/191-phone-tr-music.png) |  |
| 192 | phone-tr | Bottom bar ⋯ | ok | [192-phone-tr-bottom-bar.png](shots/192-phone-tr-bottom-bar.png) |  |
| 193 | phone-tr | Settings from More | ok | [193-phone-tr-settings-from-more.png](shots/193-phone-tr-settings-from-more.png) |  |
| 194 | phone-tr | Back from Settings | ok | [194-phone-tr-back-from-settings.png](shots/194-phone-tr-back-from-settings.png) |  |
| 195 | phone-tr | Settings from More again | ok | [195-phone-tr-settings-from-more-again.png](shots/195-phone-tr-settings-from-more-again.png) |  |
| 196 | phone-tr | The back arrow | ok | [196-phone-tr-the-back-arrow.png](shots/196-phone-tr-the-back-arrow.png) |  |
| 197 | phone-tr | Open Bu cihaz | ok | [197-phone-tr-open-bu-cihaz.png](shots/197-phone-tr-open-bu-cihaz.png) |  |
| 198 | phone-tr | Open Dahili depolama | ok | [198-phone-tr-open-dahili-depolama.png](shots/198-phone-tr-open-dahili-depolama.png) |  |
| 199 | phone-tr | Open its Music folder | ok | [199-phone-tr-open-its-music-folder.png](shots/199-phone-tr-open-its-music-folder.png) |  |
| 200 | phone-tr | Play tone.mp3 from the folder | ok | [200-phone-tr-play-tone-mp3-from-the-folder.png](shots/200-phone-tr-play-tone-mp3-from-the-folder.png) |  |
| 201 | phone-tr | Back (up to Dahili depolama) | ok | [201-phone-tr-back-up-to-dahili-depolama.png](shots/201-phone-tr-back-up-to-dahili-depolama.png) |  |
| 202 | phone-tr | Sort menu | ok | [202-phone-tr-sort-menu.png](shots/202-phone-tr-sort-menu.png) |  |
| 203 | phone-tr | Sort menu: Boyuta göre | ok | [203-phone-tr-sort-menu-boyuta-g-re.png](shots/203-phone-tr-sort-menu-boyuta-g-re.png) |  |
| 204 | phone-tr | Sort menu | ok | [204-phone-tr-sort-menu.png](shots/204-phone-tr-sort-menu.png) |  |
| 205 | phone-tr | Sort menu: Ada göre | ok | [205-phone-tr-sort-menu-ada-g-re.png](shots/205-phone-tr-sort-menu-ada-g-re.png) |  |
| 206 | phone-tr | Sort menu | ok | [206-phone-tr-sort-menu.png](shots/206-phone-tr-sort-menu.png) |  |
| 207 | phone-tr | Sort menu: Gizli dosyaları göster | ok | [207-phone-tr-sort-menu-gizli-dosyalar-g-ster.png](shots/207-phone-tr-sort-menu-gizli-dosyalar-g-ster.png) |  |
| 208 | phone-tr | Sort menu | ok | [208-phone-tr-sort-menu.png](shots/208-phone-tr-sort-menu.png) |  |
| 209 | phone-tr | Sort menu: Gizli dosyaları göster | ok | [209-phone-tr-sort-menu-gizli-dosyalar-g-ster.png](shots/209-phone-tr-sort-menu-gizli-dosyalar-g-ster.png) |  |
| 210 | phone-tr | Back (to the places) | ok | [210-phone-tr-back-to-the-places.png](shots/210-phone-tr-back-to-the-places.png) |  |
| 211 | phone-tr | Open Dahili depolama again | ok | [211-phone-tr-open-dahili-depolama-again.png](shots/211-phone-tr-open-dahili-depolama-again.png) |  |
| 212 | phone-tr | Open the Music folder again | ok | [212-phone-tr-open-the-music-folder-again.png](shots/212-phone-tr-open-the-music-folder-again.png) |  |
| 213 | phone-tr | Home with the folder open | warn | [213-phone-tr-home-with-the-folder-open.png](shots/213-phone-tr-home-with-the-folder-open.png) | the app is not in the foreground (windows: com.google.android.apps.nexuslauncher, com.google.android.deskclock, com.google.android.googlequicksearchbox) |
| 214 | phone-tr | Back in the app with the folder open | ok | [214-phone-tr-back-in-the-app-with-the-folder-open.png](shots/214-phone-tr-back-in-the-app-with-the-folder-open.png) |  |
| 215 | phone-tr | Rotated with the folder open | ok | [215-phone-tr-rotated-with-the-folder-open.png](shots/215-phone-tr-rotated-with-the-folder-open.png) |  |
| 216 | phone-tr | Rotated back with the folder open | ok | [216-phone-tr-rotated-back-with-the-folder-open.png](shots/216-phone-tr-rotated-back-with-the-folder-open.png) |  |
| 217 | phone-tr | Back (up to the drive) | ok | [217-phone-tr-back-up-to-the-drive.png](shots/217-phone-tr-back-up-to-the-drive.png) |  |
| 218 | phone-tr | Back (to the places) | ok | [218-phone-tr-back-to-the-places.png](shots/218-phone-tr-back-to-the-places.png) |  |
| 219 | phone-tr | Open Müzik | ok | [219-phone-tr-open-m-zik.png](shots/219-phone-tr-open-m-zik.png) |  |
| 220 | phone-tr | Open Podcast | ok | [220-phone-tr-open-podcast.png](shots/220-phone-tr-open-podcast.png) |  |
| 221 | phone-tr | Open Podcastler | ok | [221-phone-tr-open-podcastler.png](shots/221-phone-tr-open-podcastler.png) |  |
| 222 | phone-tr | Open Radyo | ok | [222-phone-tr-open-radyo.png](shots/222-phone-tr-open-radyo.png) |  |
| 223 | phone-tr | Open Daha fazla (overflow) | ok | [223-phone-tr-open-daha-fazla-overflow.png](shots/223-phone-tr-open-daha-fazla-overflow.png) |  |
| 224 | phone-tr | Open Bu cihaz | ok | [224-phone-tr-open-bu-cihaz.png](shots/224-phone-tr-open-bu-cihaz.png) |  |
| 225 | phone-tr | Open Daha fazla (overflow) | ok | [225-phone-tr-open-daha-fazla-overflow.png](shots/225-phone-tr-open-daha-fazla-overflow.png) |  |
| 226 | phone-tr | Open Ağ | ok | [226-phone-tr-open-a.png](shots/226-phone-tr-open-a.png) |  |
| 227 | phone-tr | Open Daha fazla (overflow) | ok | [227-phone-tr-open-daha-fazla-overflow.png](shots/227-phone-tr-open-daha-fazla-overflow.png) |  |
| 228 | phone-tr | Open Akışlar | ok | [228-phone-tr-open-ak-lar.png](shots/228-phone-tr-open-ak-lar.png) |  |
| 229 | phone-tr | Open Daha fazla (overflow) | ok | [229-phone-tr-open-daha-fazla-overflow.png](shots/229-phone-tr-open-daha-fazla-overflow.png) |  |
| 230 | phone-tr | Open İndirmeler | ok | [230-phone-tr-open-ndirmeler.png](shots/230-phone-tr-open-ndirmeler.png) |  |
| 231 | phone-tr | Open Daha fazla (overflow) | ok | [231-phone-tr-open-daha-fazla-overflow.png](shots/231-phone-tr-open-daha-fazla-overflow.png) |  |
| 232 | phone-tr | Open Çalma listeleri | ok | [232-phone-tr-open-alma-listeleri.png](shots/232-phone-tr-open-alma-listeleri.png) |  |
| 233 | phone-tr | Open Daha fazla (overflow) | ok | [233-phone-tr-open-daha-fazla-overflow.png](shots/233-phone-tr-open-daha-fazla-overflow.png) |  |
| 234 | phone-tr | Settings | ok | [234-phone-tr-settings.png](shots/234-phone-tr-settings.png) |  |
| 235 | phone-tr | Settings scroll 1 | ok | [235-phone-tr-settings-scroll-1.png](shots/235-phone-tr-settings-scroll-1.png) |  |
| 236 | phone-tr | Settings scroll 2 | ok | [236-phone-tr-settings-scroll-2.png](shots/236-phone-tr-settings-scroll-2.png) |  |
| 237 | phone-tr | Settings scroll 3 | ok | [237-phone-tr-settings-scroll-3.png](shots/237-phone-tr-settings-scroll-3.png) |  |
| 238 | phone-tr | Settings scroll 4 | ok | [238-phone-tr-settings-scroll-4.png](shots/238-phone-tr-settings-scroll-4.png) |  |
| 239 | phone-tr | Settings scroll 5 | ok | [239-phone-tr-settings-scroll-5.png](shots/239-phone-tr-settings-scroll-5.png) |  |
| 240 | phone-tr | Settings scroll 6 | ok | [240-phone-tr-settings-scroll-6.png](shots/240-phone-tr-settings-scroll-6.png) |  |
| 241 | phone-tr | Settings scroll 7 | ok | [241-phone-tr-settings-scroll-7.png](shots/241-phone-tr-settings-scroll-7.png) |  |
| 242 | phone-tr | Settings scroll 8 | ok | [242-phone-tr-settings-scroll-8.png](shots/242-phone-tr-settings-scroll-8.png) |  |
| 243 | phone-tr | Settings scroll 9 | ok | [243-phone-tr-settings-scroll-9.png](shots/243-phone-tr-settings-scroll-9.png) |  |
| 244 | phone-tr | Settings scroll 10 | ok | [244-phone-tr-settings-scroll-10.png](shots/244-phone-tr-settings-scroll-10.png) |  |
| 245 | phone-tr | Open About | ok | [245-phone-tr-open-about.png](shots/245-phone-tr-open-about.png) |  |
| 246 | phone-tr | Back from About | ok | [246-phone-tr-back-from-about.png](shots/246-phone-tr-back-from-about.png) |  |

## Warnings

- [tablet-en] labels never seen in the UI: Podcast
- [tablet-en] no 'Open the equaliser' control found in Settings
- [phone-en] no 'Open the equaliser' control found in Settings
- [phone-en] Music list scroll: 69.2 % janky frames (over 40 %; informational on an emulator)
- [phone-en] player drag up: 97.0 % janky frames (over 40 %; informational on an emulator)
- [phone-en] player drag down: 85.3 % janky frames (over 40 %; informational on an emulator)
- [workstation] Music table scroll (tablet): 97.1 % janky frames (over 40 %; informational on an emulator)
- [phone-tr] no 'Ekolayzeri aç' control found in Settings

### Missing native libraries (arm64-only libraries on an x86_64 emulator)

No link errors were logged.

### StrictMode violations: 0 (the app's; by the system's policy for a debuggable app)


### Error-level log lines of the app (top 25)

- 4x `VLC/LibVLC/Util: WARNING: Can't find shared library`
- 3x `ActivityThread: Failed to find provider info for androidx.car.app.connection`
- 1x `SurfaceSyncGroup: Failed to receive transaction ready in 1000ms. Marking SurfaceSyncGroup(wmsSync-VRI[NagmedarActivity]#0) as ready`
- 1x `SurfaceSyncGroup: Failed to receive transaction ready in 1000ms. Marking SurfaceSyncGroup(VRI[NagmedarActivity]#1) as ready`
- 1x `VLC: [00007190052657b0/3383] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [0000718fc53ebe10/3383] libvlc input: Your media can't be opened`
- 1x `VLC: [0000718fc53ebe10/3383] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/obb/'. Check the log for details.`
- 1x `VLC: [0000719005235ab0/3389] libvlc access: cannot open file /storage/emulated/0/Android/data/ (Permission denied)`
- 1x `VLC: [0000718fc53ebe10/3389] libvlc input: Your media can't be opened`
- 1x `VLC: [0000718fc53ebe10/3389] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/data/'. Check the log for details.`
- 1x `VLC: [00007190052483b0/4f31] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [0000718fc52b2a90/4f31] libvlc input: Your media can't be opened`
- 1x `VLC: [0000718fc52b2a90/4f31] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/obb/'. Check the log for details.`
- 1x `VLC: [0000719005246e50/4f35] libvlc access: cannot open file /storage/emulated/0/Android/data/ (Permission denied)`
- 1x `VLC: [0000718fc52b2a90/4f35] libvlc input: Your media can't be opened`
- 1x `VLC: [0000718fc52b2a90/4f35] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/data/'. Check the log for details.`
- 1x `VLC: [000071900522f7b0/57b6] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [0000718fc5252490/57b6] libvlc input: Your media can't be opened`
- 1x `VLC: [0000718fc5252490/57b6] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/obb/'. Check the log for details.`
- 1x `VLC: [0000719005230770/57ba] libvlc access: cannot open file /storage/emulated/0/Android/data/ (Permission denied)`
- 1x `VLC: [0000718fc5252490/57ba] libvlc input: Your media can't be opened`
- 1x `VLC: [0000718fc5252490/57ba] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/data/'. Check the log for details.`
