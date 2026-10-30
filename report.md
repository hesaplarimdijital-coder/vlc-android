# Emulator smoke test: passed

- app: `app.nagmedar.debug`, APK `personal`
- installed: versionCode=14010010 minSdk=23 targetSdk=36; versionName=1.0.2; pkgFlags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP LARGE_HEAP ]
- device: model=sdk_gphone64_x86_64, sdk=35, abi=x86_64, build=sdk_gphone64_x86_64-userdebug 15 AE3A.240806.043 12960925 dev-keys, size=Physical size: 2560x1600, density=Physical density: 320
- source: 7c7293ff38d09c56a9e18b29775d38b7904a9d5b (story/release-1.0.2, personal build, API 35), run 40997128511
- 244 steps in 47 min 34 s; 0 crash/ANR finding(s), 0 driver error(s), 0 StrictMode violation(s), 0 of 118 1.0 checks failed

## 1.0 checks

| pass | step | check | result | seen |
|------|------|-------|--------|------|
| phone-en | 41 | bottom bar is Music · Podcast · Radio · More | ok | seen: Music · Podcast · Radio · More |
| phone-en | 91 | a track's ⋯ button | ok |  |
| phone-en | 92 | a track's ⋯ opens its sheet | ok |  |
| phone-en | 93 | Back closes the track's sheet | ok |  |
| phone-en | 94 | the track's sheet opens again | ok |  |
| phone-en | 95 | a drag down from the sheet's middle closes it | ok | from y=1423 of 2400 |
| phone-en | 96 | bottom bar is Music · Podcast · Radio · More | ok | seen: Music · Podcast · Radio · More |
| phone-en | 97 | the top bar keeps its colour while the list scrolls | ok | at rest (250, 251, 252), while dragged (250, 251, 252), after (250, 251, 252) |
| phone-en | 98 | ⋯ in the bottom bar | ok |  |
| phone-en | 99 | ⋯ opens the More page | ok | seen: Downloads, Settings, Playlists |
| phone-en | 99 | Settings on the More page | ok |  |
| phone-en | 100 | Settings is a page of its own: no bottom bar | ok | bottom bar: none |
| phone-en | 100 | Settings has a back arrow | ok |  |
| phone-en | 101 | Back from Settings shows More | ok | title: Library, More |
| phone-en | 103 | the back arrow returns to More | ok |  |
| phone-en | 105 | a track in the Music's list | ok |  |
| phone-en | 106 | the mini player shows the track | ok |  |
| phone-en | 106 | a mini player to drag | ok |  |
| phone-en | 107 | dragged up, the full player shows | ok | drags: 1 |
| phone-en | 108 | dragged down, the mini player again | ok |  |
| phone-en | 109 | a sideways swipe changes the track | ok | Profile track 1.1 / Nagmedar Artist 1 → Profile track 1.2 / Nagmedar Artist 1 (swipes: 1) |
| phone-en | 109 | the bar's seek bar | ok |  |
| phone-en | 110 | a swipe along the seek bar does not change the track | ok | Profile track 1.2 / Nagmedar Artist 1 → Profile track 1.2 / Nagmedar Artist 1 |
| phone-en | 111 | the Podcasts open | ok |  |
| phone-en | 112 | the podcast address field | ok |  |
| phone-en | 114 | the feed is followed | ok |  |
| phone-en | 115 | an episode on the podcast's page | ok |  |
| phone-en | 115 | the episode's play button | ok |  |
| phone-en | 116 | the episode's play button does not move | ok | first (42, 643, 708, 769) at 2.1 s; then (42, 643, 708, 769) at +3.6 s; (42, 643, 708, 769) at +6.6 s |
| phone-en | 119 | the Radio opens | ok |  |
| phone-en | 119 | This device is found (⋯ or the sidebar) | ok |  |
| phone-en | 120 | This device lists the places, the internal storage first | ok |  |
| phone-en | 121 | Internal storage opens and lists its folders (Music among them) | ok | rows: 0:44, Alarms, Android, Audiobooks, DCIM, Documents, Download, Movies, Music, Nagmedar Artist 1, Name, Notifications |
| phone-en | 122 | the Music folder lists the walk's files (tone.mp3) | ok |  |
| phone-en | 123 | a file of the folder plays | ok | mini player |
| phone-en | 124 | back up at the drive, its folders listed | ok |  |
| phone-en | 124 | the sort menu's button | ok |  |
| phone-en | 125 | the sort menu lists the orders and the hidden files | ok |  |
| phone-en | 126 | after 'By size' the drive still lists its folders | ok |  |
| phone-en | 126 | the sort menu's button | ok |  |
| phone-en | 127 | the sort menu lists the orders and the hidden files | ok |  |
| phone-en | 128 | after 'By name' the drive still lists its folders | ok |  |
| phone-en | 128 | the sort menu's button | ok |  |
| phone-en | 129 | the sort menu lists the orders and the hidden files | ok |  |
| phone-en | 130 | after 'Show hidden files' the drive still lists its folders | ok |  |
| phone-en | 130 | the sort menu's button | ok |  |
| phone-en | 131 | the sort menu lists the orders and the hidden files | ok |  |
| phone-en | 132 | after 'Show hidden files' the drive still lists its folders | ok |  |
| phone-en | 133 | Back goes on to the places | ok |  |
| phone-en | 137 | after Home the folder is still open | ok |  |
| phone-en | 138 | rotated, the folder is still open | ok |  |
| phone-en | 139 | rotated back, the folder is still open | ok |  |
| workstation | 159 | the Music shows the inspector | ok |  |
| workstation | 160 | Ctrl+I hides the inspector | ok |  |
| workstation | 161 | Ctrl+I shows the inspector again | ok |  |
| workstation | 161 | the table's column titles | ok | Title, Artist, Album, Year, Duration |
| workstation | 162 | the column chooser opens | ok |  |
| workstation | 163 | Escape closes the column chooser | ok |  |
| workstation | 164 | This device is found (⋯ or the sidebar) | ok |  |
| workstation | 165 | This device lists the places, the internal storage first | ok |  |
| workstation | 166 | Internal storage opens and lists its folders (Music among them) | ok | rows: Alarms, Android, Audiobooks, Changed, DCIM, Documents, Download, Movies, Music, Name, Notifications, Oct 30, 2026 4:52 AM |
| workstation | 167 | the Music folder lists the walk's files (tone.mp3) | ok |  |
| workstation | 168 | a file of the folder plays | ok | transport bar: tone |
| workstation | 168 | the transport bar's open button | ok |  |
| workstation | 169 | the bar's open button opens the big player | ok |  |
| workstation | 169 | the big player covers the window | ok | player (0, 48, 2560, 1536) in 2560x1600 |
| workstation | 169 | the bars stand aside under it | ok |  |
| workstation | 171 | Back closes the big player | ok |  |
| workstation | 171 | the trail names the drive | ok |  |
| workstation | 172 | back up at the drive, its folders listed | ok |  |
| workstation | 172 | the sort menu's button | ok |  |
| workstation | 173 | the sort menu lists the orders and the hidden files | ok |  |
| workstation | 174 | after 'By size' the drive still lists its folders | ok |  |
| workstation | 174 | the sort menu's button | ok |  |
| workstation | 175 | the sort menu lists the orders and the hidden files | ok |  |
| workstation | 176 | after 'By name' the drive still lists its folders | ok |  |
| workstation | 176 | the sort menu's button | ok |  |
| workstation | 177 | the sort menu lists the orders and the hidden files | ok |  |
| workstation | 178 | after 'Show hidden files' the drive still lists its folders | ok |  |
| workstation | 178 | the sort menu's button | ok |  |
| workstation | 179 | the sort menu lists the orders and the hidden files | ok |  |
| workstation | 180 | after 'Show hidden files' the drive still lists its folders | ok |  |
| workstation | 180 | the places stay beside the folder | ok |  |
| workstation | 183 | after Home the folder is still open | ok |  |
| workstation | 184 | rotated, the folder is still open | ok |  |
| workstation | 185 | rotated back, the folder is still open | ok |  |
| phone-tr | 187 | bottom bar is Müzik · Podcast · Radyo · Daha fazla | ok | seen: Müzik · Podcast · Radyo · Daha fazla |
| phone-tr | 188 | the top bar keeps its colour while the list scrolls | ok | at rest (250, 251, 252), while dragged (250, 251, 252), after (250, 251, 252) |
| phone-tr | 189 | ⋯ in the bottom bar | ok |  |
| phone-tr | 190 | ⋯ opens the More page | ok | seen: İndirmeler, Ayarlar, Çalma listeleri |
| phone-tr | 190 | Settings on the More page | ok |  |
| phone-tr | 191 | Settings is a page of its own: no bottom bar | ok | bottom bar: none |
| phone-tr | 191 | Settings has a back arrow | ok |  |
| phone-tr | 192 | Back from Settings shows More | ok | title: Kütüphane, Daha fazla |
| phone-tr | 194 | the back arrow returns to More | ok |  |
| phone-tr | 194 | Bu cihaz is found (⋯ or the sidebar) | ok |  |
| phone-tr | 195 | Bu cihaz lists the places, the internal storage first | ok |  |
| phone-tr | 196 | Dahili depolama opens and lists its folders (Music among them) | ok | rows: 30 Eki 2026 04:52, 30 Eki 2026 05:01, Ad, Alarms, Android, Audiobooks, DCIM, Documents, Download, Movies, Music, Notifications |
| phone-tr | 197 | the Music folder lists the walk's files (tone.mp3) | ok |  |
| phone-tr | 198 | a file of the folder plays | ok | mini player |
| phone-tr | 199 | back up at the drive, its folders listed | ok |  |
| phone-tr | 199 | the sort menu's button | ok |  |
| phone-tr | 200 | the sort menu lists the orders and the hidden files | ok |  |
| phone-tr | 201 | after 'Boyuta göre' the drive still lists its folders | ok |  |
| phone-tr | 201 | the sort menu's button | ok |  |
| phone-tr | 202 | the sort menu lists the orders and the hidden files | ok |  |
| phone-tr | 203 | after 'Ada göre' the drive still lists its folders | ok |  |
| phone-tr | 203 | the sort menu's button | ok |  |
| phone-tr | 204 | the sort menu lists the orders and the hidden files | ok |  |
| phone-tr | 205 | after 'Gizli dosyaları göster' the drive still lists its folders | ok |  |
| phone-tr | 205 | the sort menu's button | ok |  |
| phone-tr | 206 | the sort menu lists the orders and the hidden files | ok |  |
| phone-tr | 207 | after 'Gizli dosyaları göster' the drive still lists its folders | ok |  |
| phone-tr | 208 | Back goes on to the places | ok |  |
| phone-tr | 212 | after Home the folder is still open | ok |  |
| phone-tr | 213 | rotated, the folder is still open | ok |  |
| phone-tr | 214 | rotated back, the folder is still open | ok |  |
| all | 244 | no film, series, video, Home, TV Lab or Whisper words on the app's screens | ok |  |

## Frames (dumpsys gfxinfo; informational on an emulator, over 40 % janky is flagged)

| pass | step | gesture | frames | janky | 50th | 90th | 95th | 99th |
|------|------|---------|--------|-------|------|------|------|------|
| phone-en | 104 | Music list scroll | 159 | 57.9 % **(over 40 %)** | 48 ms | 77 ms | 85 ms | 150 ms |
| phone-en | 107 | player drag up | 36 | 91.7 % **(over 40 %)** | 81 ms | 117 ms | 200 ms | 200 ms |
| phone-en | 108 | player drag down | 36 | 77.8 % **(over 40 %)** | 73 ms | 109 ms | 121 ms | 129 ms |
| workstation | 163 | Music table scroll (tablet) | 44 | 84.1 % **(over 40 %)** | 73 ms | 133 ms | 150 ms | 150 ms |

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
| 39 | tablet-en | Open About | ok | [039-tablet-en-open-about.png](shots/039-tablet-en-open-about.png) |  |
| 40 | tablet-en | Back from About | ok | [040-tablet-en-back-from-about.png](shots/040-tablet-en-back-from-about.png) |  |
| 41 | phone-en | Launch (phone) | ok | [041-phone-en-launch-phone.png](shots/041-phone-en-launch-phone.png) |  |
| 42 | phone-en | Open Music | ok | [042-phone-en-open-music.png](shots/042-phone-en-open-music.png) |  |
| 43 | phone-en | Open Podcast | ok | [043-phone-en-open-podcast.png](shots/043-phone-en-open-podcast.png) |  |
| 44 | phone-en | Open Podcasts | ok | [044-phone-en-open-podcasts.png](shots/044-phone-en-open-podcasts.png) |  |
| 45 | phone-en | Open Radio | ok | [045-phone-en-open-radio.png](shots/045-phone-en-open-radio.png) |  |
| 46 | phone-en | Open More (overflow) | ok | [046-phone-en-open-more-overflow.png](shots/046-phone-en-open-more-overflow.png) |  |
| 47 | phone-en | Open This device | ok | [047-phone-en-open-this-device.png](shots/047-phone-en-open-this-device.png) |  |
| 48 | phone-en | Open More (overflow) | ok | [048-phone-en-open-more-overflow.png](shots/048-phone-en-open-more-overflow.png) |  |
| 49 | phone-en | Open Network | ok | [049-phone-en-open-network.png](shots/049-phone-en-open-network.png) |  |
| 50 | phone-en | Open More (overflow) | ok | [050-phone-en-open-more-overflow.png](shots/050-phone-en-open-more-overflow.png) |  |
| 51 | phone-en | Open Streams | ok | [051-phone-en-open-streams.png](shots/051-phone-en-open-streams.png) |  |
| 52 | phone-en | Open More (overflow) | ok | [052-phone-en-open-more-overflow.png](shots/052-phone-en-open-more-overflow.png) |  |
| 53 | phone-en | Open Downloads | ok | [053-phone-en-open-downloads.png](shots/053-phone-en-open-downloads.png) |  |
| 54 | phone-en | Open More (overflow) | ok | [054-phone-en-open-more-overflow.png](shots/054-phone-en-open-more-overflow.png) |  |
| 55 | phone-en | Open Playlists | ok | [055-phone-en-open-playlists.png](shots/055-phone-en-open-playlists.png) |  |
| 56 | phone-en | Open More (overflow) | ok | [056-phone-en-open-more-overflow.png](shots/056-phone-en-open-more-overflow.png) |  |
| 57 | phone-en | Settings | ok | [057-phone-en-settings.png](shots/057-phone-en-settings.png) |  |
| 58 | phone-en | Settings scroll 1 | ok | [058-phone-en-settings-scroll-1.png](shots/058-phone-en-settings-scroll-1.png) |  |
| 59 | phone-en | Settings scroll 2 | ok | [059-phone-en-settings-scroll-2.png](shots/059-phone-en-settings-scroll-2.png) |  |
| 60 | phone-en | Settings scroll 3 | ok | [060-phone-en-settings-scroll-3.png](shots/060-phone-en-settings-scroll-3.png) |  |
| 61 | phone-en | Settings scroll 4 | ok | [061-phone-en-settings-scroll-4.png](shots/061-phone-en-settings-scroll-4.png) |  |
| 62 | phone-en | Settings scroll 5 | ok | [062-phone-en-settings-scroll-5.png](shots/062-phone-en-settings-scroll-5.png) |  |
| 63 | phone-en | Settings scroll 6 | ok | [063-phone-en-settings-scroll-6.png](shots/063-phone-en-settings-scroll-6.png) |  |
| 64 | phone-en | Settings scroll 7 | ok | [064-phone-en-settings-scroll-7.png](shots/064-phone-en-settings-scroll-7.png) |  |
| 65 | phone-en | Settings scroll 8 | ok | [065-phone-en-settings-scroll-8.png](shots/065-phone-en-settings-scroll-8.png) |  |
| 66 | phone-en | Settings scroll 9 | ok | [066-phone-en-settings-scroll-9.png](shots/066-phone-en-settings-scroll-9.png) |  |
| 67 | phone-en | Settings scroll 10 | ok | [067-phone-en-settings-scroll-10.png](shots/067-phone-en-settings-scroll-10.png) |  |
| 68 | phone-en | Open About | ok | [068-phone-en-open-about.png](shots/068-phone-en-open-about.png) |  |
| 69 | phone-en | Back from About | ok | [069-phone-en-back-from-about.png](shots/069-phone-en-back-from-about.png) |  |
| 70 | phone-en | Show the first run again | ok | [070-phone-en-show-the-first-run-again.png](shots/070-phone-en-show-the-first-run-again.png) |  |
| 71 | phone-en | First run again step 1 (Continue) | ok | [071-phone-en-first-run-again-step-1-continue.png](shots/071-phone-en-first-run-again-step-1-continue.png) |  |
| 72 | phone-en | First run again step 2 (Continue · 2 sources) | ok | [072-phone-en-first-run-again-step-2-continue-2-sources.png](shots/072-phone-en-first-run-again-step-2-continue-2-sources.png) |  |
| 73 | phone-en | First run again step 3 (Continue) | ok | [073-phone-en-first-run-again-step-3-continue.png](shots/073-phone-en-first-run-again-step-3-continue.png) |  |
| 74 | phone-en | First run again step 4 (Start) | ok | [074-phone-en-first-run-again-step-4-start.png](shots/074-phone-en-first-run-again-step-4-start.png) |  |
| 75 | phone-en | After the scan | ok | [075-phone-en-after-the-scan.png](shots/075-phone-en-after-the-scan.png) |  |
| 76 | phone-en | Reopen Music | ok | [076-phone-en-reopen-music.png](shots/076-phone-en-reopen-music.png) |  |
| 77 | phone-en | Reopen Podcast | ok | [077-phone-en-reopen-podcast.png](shots/077-phone-en-reopen-podcast.png) |  |
| 78 | phone-en | Reopen Podcasts | ok | [078-phone-en-reopen-podcasts.png](shots/078-phone-en-reopen-podcasts.png) |  |
| 79 | phone-en | Reopen Radio | ok | [079-phone-en-reopen-radio.png](shots/079-phone-en-reopen-radio.png) |  |
| 80 | phone-en | Open More (overflow) | ok | [080-phone-en-open-more-overflow.png](shots/080-phone-en-open-more-overflow.png) |  |
| 81 | phone-en | Reopen This device | ok | [081-phone-en-reopen-this-device.png](shots/081-phone-en-reopen-this-device.png) |  |
| 82 | phone-en | Open More (overflow) | ok | [082-phone-en-open-more-overflow.png](shots/082-phone-en-open-more-overflow.png) |  |
| 83 | phone-en | Reopen Network | ok | [083-phone-en-reopen-network.png](shots/083-phone-en-reopen-network.png) |  |
| 84 | phone-en | Open More (overflow) | ok | [084-phone-en-open-more-overflow.png](shots/084-phone-en-open-more-overflow.png) |  |
| 85 | phone-en | Reopen Streams | ok | [085-phone-en-reopen-streams.png](shots/085-phone-en-reopen-streams.png) |  |
| 86 | phone-en | Open More (overflow) | ok | [086-phone-en-open-more-overflow.png](shots/086-phone-en-open-more-overflow.png) |  |
| 87 | phone-en | Reopen Downloads | ok | [087-phone-en-reopen-downloads.png](shots/087-phone-en-reopen-downloads.png) |  |
| 88 | phone-en | Open More (overflow) | ok | [088-phone-en-open-more-overflow.png](shots/088-phone-en-open-more-overflow.png) |  |
| 89 | phone-en | Reopen Playlists | ok | [089-phone-en-reopen-playlists.png](shots/089-phone-en-reopen-playlists.png) |  |
| 90 | phone-en | Open More (overflow) | ok | [090-phone-en-open-more-overflow.png](shots/090-phone-en-open-more-overflow.png) |  |
| 91 | phone-en | Music | ok | [091-phone-en-music.png](shots/091-phone-en-music.png) |  |
| 92 | phone-en | A track's ⋯ (More actions for Profile track 1.1) | ok | [092-phone-en-a-track-s-more-actions-for-profile-track-1-1.png](shots/092-phone-en-a-track-s-more-actions-for-profile-track-1-1.png) |  |
| 93 | phone-en | Back from the track's sheet | ok | [093-phone-en-back-from-the-track-s-sheet.png](shots/093-phone-en-back-from-the-track-s-sheet.png) |  |
| 94 | phone-en | A track's ⋯ again | ok | [094-phone-en-a-track-s-again.png](shots/094-phone-en-a-track-s-again.png) |  |
| 95 | phone-en | Drag the sheet down from its middle | ok | [095-phone-en-drag-the-sheet-down-from-its-middle.png](shots/095-phone-en-drag-the-sheet-down-from-its-middle.png) |  |
| 96 | phone-en | Music | ok | [096-phone-en-music.png](shots/096-phone-en-music.png) |  |
| 97 | phone-en | Music scrolled (top bar) | ok | [097-phone-en-music-scrolled-top-bar.png](shots/097-phone-en-music-scrolled-top-bar.png) |  |
| 98 | phone-en | Music | ok | [098-phone-en-music.png](shots/098-phone-en-music.png) |  |
| 99 | phone-en | Bottom bar ⋯ | ok | [099-phone-en-bottom-bar.png](shots/099-phone-en-bottom-bar.png) |  |
| 100 | phone-en | Settings from More | ok | [100-phone-en-settings-from-more.png](shots/100-phone-en-settings-from-more.png) |  |
| 101 | phone-en | Back from Settings | ok | [101-phone-en-back-from-settings.png](shots/101-phone-en-back-from-settings.png) |  |
| 102 | phone-en | Settings from More again | ok | [102-phone-en-settings-from-more-again.png](shots/102-phone-en-settings-from-more-again.png) |  |
| 103 | phone-en | The back arrow | ok | [103-phone-en-the-back-arrow.png](shots/103-phone-en-the-back-arrow.png) |  |
| 104 | phone-en | Music | ok | [104-phone-en-music.png](shots/104-phone-en-music.png) |  |
| 105 | phone-en | Music list scroll (after the scroll) | ok | [105-phone-en-music-list-scroll-after-the-scroll.png](shots/105-phone-en-music-list-scroll-after-the-scroll.png) |  |
| 106 | phone-en | Play Profile track 1.1 | ok | [106-phone-en-play-profile-track-1-1.png](shots/106-phone-en-play-profile-track-1-1.png) |  |
| 107 | phone-en | Drag the mini player up | ok | [107-phone-en-drag-the-mini-player-up.png](shots/107-phone-en-drag-the-mini-player-up.png) |  |
| 108 | phone-en | Drag the full player down | ok | [108-phone-en-drag-the-full-player-down.png](shots/108-phone-en-drag-the-full-player-down.png) |  |
| 109 | phone-en | Swipe the mini player sideways | ok | [109-phone-en-swipe-the-mini-player-sideways.png](shots/109-phone-en-swipe-the-mini-player-sideways.png) |  |
| 110 | phone-en | Swipe along the bar's seek bar | ok | [110-phone-en-swipe-along-the-bar-s-seek-bar.png](shots/110-phone-en-swipe-along-the-bar-s-seek-bar.png) |  |
| 111 | phone-en | Podcasts | ok | [111-phone-en-podcasts.png](shots/111-phone-en-podcasts.png) |  |
| 112 | phone-en | Add a podcast by its address | ok | [112-phone-en-add-a-podcast-by-its-address.png](shots/112-phone-en-add-a-podcast-by-its-address.png) |  |
| 113 | phone-en | The feed's address typed | ok | [113-phone-en-the-feed-s-address-typed.png](shots/113-phone-en-the-feed-s-address-typed.png) |  |
| 114 | phone-en | Subscribe | ok | [114-phone-en-subscribe.png](shots/114-phone-en-subscribe.png) |  |
| 115 | phone-en | Followed | ok | [115-phone-en-followed.png](shots/115-phone-en-followed.png) |  |
| 116 | phone-en | Episode page (the play button watched) | ok | [116-phone-en-episode-page-the-play-button-watched.png](shots/116-phone-en-episode-page-the-play-button-watched.png) |  |
| 117 | phone-en | Back from the episode | ok | [117-phone-en-back-from-the-episode.png](shots/117-phone-en-back-from-the-episode.png) |  |
| 118 | phone-en | Back from the podcast | ok | [118-phone-en-back-from-the-podcast.png](shots/118-phone-en-back-from-the-podcast.png) |  |
| 119 | phone-en | Radio | ok | [119-phone-en-radio.png](shots/119-phone-en-radio.png) |  |
| 120 | phone-en | Open This device | ok | [120-phone-en-open-this-device.png](shots/120-phone-en-open-this-device.png) |  |
| 121 | phone-en | Open Internal storage | ok | [121-phone-en-open-internal-storage.png](shots/121-phone-en-open-internal-storage.png) |  |
| 122 | phone-en | Open its Music folder | ok | [122-phone-en-open-its-music-folder.png](shots/122-phone-en-open-its-music-folder.png) |  |
| 123 | phone-en | Play tone.mp3 from the folder | ok | [123-phone-en-play-tone-mp3-from-the-folder.png](shots/123-phone-en-play-tone-mp3-from-the-folder.png) |  |
| 124 | phone-en | Back (up to Internal storage) | ok | [124-phone-en-back-up-to-internal-storage.png](shots/124-phone-en-back-up-to-internal-storage.png) |  |
| 125 | phone-en | Sort menu | ok | [125-phone-en-sort-menu.png](shots/125-phone-en-sort-menu.png) |  |
| 126 | phone-en | Sort menu: By size | ok | [126-phone-en-sort-menu-by-size.png](shots/126-phone-en-sort-menu-by-size.png) |  |
| 127 | phone-en | Sort menu | ok | [127-phone-en-sort-menu.png](shots/127-phone-en-sort-menu.png) |  |
| 128 | phone-en | Sort menu: By name | ok | [128-phone-en-sort-menu-by-name.png](shots/128-phone-en-sort-menu-by-name.png) |  |
| 129 | phone-en | Sort menu | ok | [129-phone-en-sort-menu.png](shots/129-phone-en-sort-menu.png) |  |
| 130 | phone-en | Sort menu: Show hidden files | ok | [130-phone-en-sort-menu-show-hidden-files.png](shots/130-phone-en-sort-menu-show-hidden-files.png) |  |
| 131 | phone-en | Sort menu | ok | [131-phone-en-sort-menu.png](shots/131-phone-en-sort-menu.png) |  |
| 132 | phone-en | Sort menu: Show hidden files | ok | [132-phone-en-sort-menu-show-hidden-files.png](shots/132-phone-en-sort-menu-show-hidden-files.png) |  |
| 133 | phone-en | Back (to the places) | ok | [133-phone-en-back-to-the-places.png](shots/133-phone-en-back-to-the-places.png) |  |
| 134 | phone-en | Open Internal storage again | ok | [134-phone-en-open-internal-storage-again.png](shots/134-phone-en-open-internal-storage-again.png) |  |
| 135 | phone-en | Open the Music folder again | ok | [135-phone-en-open-the-music-folder-again.png](shots/135-phone-en-open-the-music-folder-again.png) |  |
| 136 | phone-en | Home with the folder open | warn | [136-phone-en-home-with-the-folder-open.png](shots/136-phone-en-home-with-the-folder-open.png) | the app is not in the foreground (windows: com.google.android.apps.nexuslauncher, com.google.android.calendar, com.google.android.deskclock) |
| 137 | phone-en | Back in the app with the folder open | ok | [137-phone-en-back-in-the-app-with-the-folder-open.png](shots/137-phone-en-back-in-the-app-with-the-folder-open.png) |  |
| 138 | phone-en | Rotated with the folder open | ok | [138-phone-en-rotated-with-the-folder-open.png](shots/138-phone-en-rotated-with-the-folder-open.png) |  |
| 139 | phone-en | Rotated back with the folder open | ok | [139-phone-en-rotated-back-with-the-folder-open.png](shots/139-phone-en-rotated-back-with-the-folder-open.png) |  |
| 140 | phone-en | Back (up to the drive) | ok | [140-phone-en-back-up-to-the-drive.png](shots/140-phone-en-back-up-to-the-drive.png) |  |
| 141 | phone-en | Back (to the places) | ok | [141-phone-en-back-to-the-places.png](shots/141-phone-en-back-to-the-places.png) |  |
| 142 | phone-en | Open the audio file | ok | [142-phone-en-open-the-audio-file.png](shots/142-phone-en-open-the-audio-file.png) | Starting: Intent { act=android.intent.action.VIEW dat=file:///... typ=audio/mpeg pkg=app.nagmedar.debug } |
| 143 | phone-en | Playing the audio file | ok | [143-phone-en-playing-the-audio-file.png](shots/143-phone-en-playing-the-audio-file.png) |  |
| 144 | phone-en | Back from the audio file | ok | [144-phone-en-back-from-the-audio-file.png](shots/144-phone-en-back-from-the-audio-file.png) |  |
| 145 | phone-en | Home during playback | warn | [145-phone-en-home-during-playback.png](shots/145-phone-en-home-during-playback.png) | the app is not in the foreground (windows: com.google.android.apps.nexuslauncher, com.google.android.calendar, com.google.android.deskclock) |
| 146 | phone-en | Back to the app after playback | ok | [146-phone-en-back-to-the-app-after-playback.png](shots/146-phone-en-back-to-the-app-after-playback.png) |  |
| 147 | phone-en | Command palette (Ctrl+K) | ok | [147-phone-en-command-palette-ctrl-k.png](shots/147-phone-en-command-palette-ctrl-k.png) |  |
| 148 | phone-en | Palette query | ok | [148-phone-en-palette-query.png](shots/148-phone-en-palette-query.png) |  |
| 149 | phone-en | Palette: select the first result | ok | [149-phone-en-palette-select-the-first-result.png](shots/149-phone-en-palette-select-the-first-result.png) |  |
| 150 | phone-en | Escape | ok | [150-phone-en-escape.png](shots/150-phone-en-escape.png) |  |
| 151 | phone-en | Shortcut list (Ctrl+/) | ok | [151-phone-en-shortcut-list-ctrl.png](shots/151-phone-en-shortcut-list-ctrl.png) |  |
| 152 | phone-en | Settings (Ctrl+,) | ok | [152-phone-en-settings-ctrl.png](shots/152-phone-en-settings-ctrl.png) |  |
| 153 | phone-en | Rotated to landscape | ok | [153-phone-en-rotated-to-landscape.png](shots/153-phone-en-rotated-to-landscape.png) |  |
| 154 | phone-en | Rotated back | ok | [154-phone-en-rotated-back.png](shots/154-phone-en-rotated-back.png) |  |
| 155 | phone-en | In the background (home screen) | warn | [155-phone-en-in-the-background-home-screen.png](shots/155-phone-en-in-the-background-home-screen.png) | the app is not in the foreground (windows: com.google.android.apps.nexuslauncher, com.google.android.calendar, com.google.android.deskclock) |
| 156 | phone-en | Foreground again | ok | [156-phone-en-foreground-again.png](shots/156-phone-en-foreground-again.png) |  |
| 157 | phone-en | After a memory-trim request | ok | [157-phone-en-after-a-memory-trim-request.png](shots/157-phone-en-after-a-memory-trim-request.png) |  |
| 158 | phone-en | Cold start after the process was killed | ok | [158-phone-en-cold-start-after-the-process-was-killed.png](shots/158-phone-en-cold-start-after-the-process-was-killed.png) |  |
| 159 | workstation | Music | ok | [159-workstation-music.png](shots/159-workstation-music.png) |  |
| 160 | workstation | Ctrl+I (hide the inspector) | ok | [160-workstation-ctrl-i-hide-the-inspector.png](shots/160-workstation-ctrl-i-hide-the-inspector.png) |  |
| 161 | workstation | Ctrl+I (show the inspector) | ok | [161-workstation-ctrl-i-show-the-inspector.png](shots/161-workstation-ctrl-i-show-the-inspector.png) |  |
| 162 | workstation | Long press on the column title Artist | ok | [162-workstation-long-press-on-the-column-title-artist.png](shots/162-workstation-long-press-on-the-column-title-artist.png) |  |
| 163 | workstation | Escape closes the column chooser | ok | [163-workstation-escape-closes-the-column-chooser.png](shots/163-workstation-escape-closes-the-column-chooser.png) |  |
| 164 | workstation | Music table scroll (tablet) (after the scroll) | ok | [164-workstation-music-table-scroll-tablet-after-the-scroll.png](shots/164-workstation-music-table-scroll-tablet-after-the-scroll.png) |  |
| 165 | workstation | Open This device | ok | [165-workstation-open-this-device.png](shots/165-workstation-open-this-device.png) |  |
| 166 | workstation | Open Internal storage | ok | [166-workstation-open-internal-storage.png](shots/166-workstation-open-internal-storage.png) |  |
| 167 | workstation | Open its Music folder | ok | [167-workstation-open-its-music-folder.png](shots/167-workstation-open-its-music-folder.png) |  |
| 168 | workstation | Play tone.mp3 from the folder | ok | [168-workstation-play-tone-mp3-from-the-folder.png](shots/168-workstation-play-tone-mp3-from-the-folder.png) |  |
| 169 | workstation | The bar's open button | ok | [169-workstation-the-bar-s-open-button.png](shots/169-workstation-the-bar-s-open-button.png) |  |
| 170 | workstation | The big player (screenshot) | ok | [170-workstation-the-big-player-screenshot.png](shots/170-workstation-the-big-player-screenshot.png) |  |
| 171 | workstation | Back: the big player closes | ok | [171-workstation-back-the-big-player-closes.png](shots/171-workstation-back-the-big-player-closes.png) |  |
| 172 | workstation | The trail back to Internal storage | ok | [172-workstation-the-trail-back-to-internal-storage.png](shots/172-workstation-the-trail-back-to-internal-storage.png) |  |
| 173 | workstation | Sort menu | ok | [173-workstation-sort-menu.png](shots/173-workstation-sort-menu.png) |  |
| 174 | workstation | Sort menu: By size | ok | [174-workstation-sort-menu-by-size.png](shots/174-workstation-sort-menu-by-size.png) |  |
| 175 | workstation | Sort menu | ok | [175-workstation-sort-menu.png](shots/175-workstation-sort-menu.png) |  |
| 176 | workstation | Sort menu: By name | ok | [176-workstation-sort-menu-by-name.png](shots/176-workstation-sort-menu-by-name.png) |  |
| 177 | workstation | Sort menu | ok | [177-workstation-sort-menu.png](shots/177-workstation-sort-menu.png) |  |
| 178 | workstation | Sort menu: Show hidden files | ok | [178-workstation-sort-menu-show-hidden-files.png](shots/178-workstation-sort-menu-show-hidden-files.png) |  |
| 179 | workstation | Sort menu | ok | [179-workstation-sort-menu.png](shots/179-workstation-sort-menu.png) |  |
| 180 | workstation | Sort menu: Show hidden files | ok | [180-workstation-sort-menu-show-hidden-files.png](shots/180-workstation-sort-menu-show-hidden-files.png) |  |
| 181 | workstation | Open the Music folder again | ok | [181-workstation-open-the-music-folder-again.png](shots/181-workstation-open-the-music-folder-again.png) |  |
| 182 | workstation | Home with the folder open | warn | [182-workstation-home-with-the-folder-open.png](shots/182-workstation-home-with-the-folder-open.png) | the app is not in the foreground (windows: com.google.android.apps.nexuslauncher, com.google.android.calendar, com.google.android.deskclock) |
| 183 | workstation | Back in the app with the folder open | ok | [183-workstation-back-in-the-app-with-the-folder-open.png](shots/183-workstation-back-in-the-app-with-the-folder-open.png) |  |
| 184 | workstation | Rotated with the folder open | ok | [184-workstation-rotated-with-the-folder-open.png](shots/184-workstation-rotated-with-the-folder-open.png) |  |
| 185 | workstation | Rotated back with the folder open | ok | [185-workstation-rotated-back-with-the-folder-open.png](shots/185-workstation-rotated-back-with-the-folder-open.png) |  |
| 186 | phone-tr | Launch (Turkish) | ok | [186-phone-tr-launch-turkish.png](shots/186-phone-tr-launch-turkish.png) |  |
| 187 | phone-tr | Music | ok | [187-phone-tr-music.png](shots/187-phone-tr-music.png) |  |
| 188 | phone-tr | Music scrolled (top bar) | ok | [188-phone-tr-music-scrolled-top-bar.png](shots/188-phone-tr-music-scrolled-top-bar.png) |  |
| 189 | phone-tr | Music | ok | [189-phone-tr-music.png](shots/189-phone-tr-music.png) |  |
| 190 | phone-tr | Bottom bar ⋯ | ok | [190-phone-tr-bottom-bar.png](shots/190-phone-tr-bottom-bar.png) |  |
| 191 | phone-tr | Settings from More | ok | [191-phone-tr-settings-from-more.png](shots/191-phone-tr-settings-from-more.png) |  |
| 192 | phone-tr | Back from Settings | ok | [192-phone-tr-back-from-settings.png](shots/192-phone-tr-back-from-settings.png) |  |
| 193 | phone-tr | Settings from More again | ok | [193-phone-tr-settings-from-more-again.png](shots/193-phone-tr-settings-from-more-again.png) |  |
| 194 | phone-tr | The back arrow | ok | [194-phone-tr-the-back-arrow.png](shots/194-phone-tr-the-back-arrow.png) |  |
| 195 | phone-tr | Open Bu cihaz | ok | [195-phone-tr-open-bu-cihaz.png](shots/195-phone-tr-open-bu-cihaz.png) |  |
| 196 | phone-tr | Open Dahili depolama | ok | [196-phone-tr-open-dahili-depolama.png](shots/196-phone-tr-open-dahili-depolama.png) |  |
| 197 | phone-tr | Open its Music folder | ok | [197-phone-tr-open-its-music-folder.png](shots/197-phone-tr-open-its-music-folder.png) |  |
| 198 | phone-tr | Play tone.mp3 from the folder | ok | [198-phone-tr-play-tone-mp3-from-the-folder.png](shots/198-phone-tr-play-tone-mp3-from-the-folder.png) |  |
| 199 | phone-tr | Back (up to Dahili depolama) | ok | [199-phone-tr-back-up-to-dahili-depolama.png](shots/199-phone-tr-back-up-to-dahili-depolama.png) |  |
| 200 | phone-tr | Sort menu | ok | [200-phone-tr-sort-menu.png](shots/200-phone-tr-sort-menu.png) |  |
| 201 | phone-tr | Sort menu: Boyuta göre | ok | [201-phone-tr-sort-menu-boyuta-g-re.png](shots/201-phone-tr-sort-menu-boyuta-g-re.png) |  |
| 202 | phone-tr | Sort menu | ok | [202-phone-tr-sort-menu.png](shots/202-phone-tr-sort-menu.png) |  |
| 203 | phone-tr | Sort menu: Ada göre | ok | [203-phone-tr-sort-menu-ada-g-re.png](shots/203-phone-tr-sort-menu-ada-g-re.png) |  |
| 204 | phone-tr | Sort menu | ok | [204-phone-tr-sort-menu.png](shots/204-phone-tr-sort-menu.png) |  |
| 205 | phone-tr | Sort menu: Gizli dosyaları göster | ok | [205-phone-tr-sort-menu-gizli-dosyalar-g-ster.png](shots/205-phone-tr-sort-menu-gizli-dosyalar-g-ster.png) |  |
| 206 | phone-tr | Sort menu | ok | [206-phone-tr-sort-menu.png](shots/206-phone-tr-sort-menu.png) |  |
| 207 | phone-tr | Sort menu: Gizli dosyaları göster | ok | [207-phone-tr-sort-menu-gizli-dosyalar-g-ster.png](shots/207-phone-tr-sort-menu-gizli-dosyalar-g-ster.png) |  |
| 208 | phone-tr | Back (to the places) | ok | [208-phone-tr-back-to-the-places.png](shots/208-phone-tr-back-to-the-places.png) |  |
| 209 | phone-tr | Open Dahili depolama again | ok | [209-phone-tr-open-dahili-depolama-again.png](shots/209-phone-tr-open-dahili-depolama-again.png) |  |
| 210 | phone-tr | Open the Music folder again | ok | [210-phone-tr-open-the-music-folder-again.png](shots/210-phone-tr-open-the-music-folder-again.png) |  |
| 211 | phone-tr | Home with the folder open | warn | [211-phone-tr-home-with-the-folder-open.png](shots/211-phone-tr-home-with-the-folder-open.png) | the app is not in the foreground (windows: com.google.android.apps.nexuslauncher, com.google.android.calendar, com.google.android.deskclock) |
| 212 | phone-tr | Back in the app with the folder open | ok | [212-phone-tr-back-in-the-app-with-the-folder-open.png](shots/212-phone-tr-back-in-the-app-with-the-folder-open.png) |  |
| 213 | phone-tr | Rotated with the folder open | ok | [213-phone-tr-rotated-with-the-folder-open.png](shots/213-phone-tr-rotated-with-the-folder-open.png) |  |
| 214 | phone-tr | Rotated back with the folder open | ok | [214-phone-tr-rotated-back-with-the-folder-open.png](shots/214-phone-tr-rotated-back-with-the-folder-open.png) |  |
| 215 | phone-tr | Back (up to the drive) | ok | [215-phone-tr-back-up-to-the-drive.png](shots/215-phone-tr-back-up-to-the-drive.png) |  |
| 216 | phone-tr | Back (to the places) | ok | [216-phone-tr-back-to-the-places.png](shots/216-phone-tr-back-to-the-places.png) |  |
| 217 | phone-tr | Open Müzik | ok | [217-phone-tr-open-m-zik.png](shots/217-phone-tr-open-m-zik.png) |  |
| 218 | phone-tr | Open Podcast | ok | [218-phone-tr-open-podcast.png](shots/218-phone-tr-open-podcast.png) |  |
| 219 | phone-tr | Open Podcastler | ok | [219-phone-tr-open-podcastler.png](shots/219-phone-tr-open-podcastler.png) |  |
| 220 | phone-tr | Open Radyo | ok | [220-phone-tr-open-radyo.png](shots/220-phone-tr-open-radyo.png) |  |
| 221 | phone-tr | Open Daha fazla (overflow) | ok | [221-phone-tr-open-daha-fazla-overflow.png](shots/221-phone-tr-open-daha-fazla-overflow.png) |  |
| 222 | phone-tr | Open Bu cihaz | ok | [222-phone-tr-open-bu-cihaz.png](shots/222-phone-tr-open-bu-cihaz.png) |  |
| 223 | phone-tr | Open Daha fazla (overflow) | ok | [223-phone-tr-open-daha-fazla-overflow.png](shots/223-phone-tr-open-daha-fazla-overflow.png) |  |
| 224 | phone-tr | Open Ağ | ok | [224-phone-tr-open-a.png](shots/224-phone-tr-open-a.png) |  |
| 225 | phone-tr | Open Daha fazla (overflow) | ok | [225-phone-tr-open-daha-fazla-overflow.png](shots/225-phone-tr-open-daha-fazla-overflow.png) |  |
| 226 | phone-tr | Open Akışlar | ok | [226-phone-tr-open-ak-lar.png](shots/226-phone-tr-open-ak-lar.png) |  |
| 227 | phone-tr | Open Daha fazla (overflow) | ok | [227-phone-tr-open-daha-fazla-overflow.png](shots/227-phone-tr-open-daha-fazla-overflow.png) |  |
| 228 | phone-tr | Open İndirmeler | ok | [228-phone-tr-open-ndirmeler.png](shots/228-phone-tr-open-ndirmeler.png) |  |
| 229 | phone-tr | Open Daha fazla (overflow) | ok | [229-phone-tr-open-daha-fazla-overflow.png](shots/229-phone-tr-open-daha-fazla-overflow.png) |  |
| 230 | phone-tr | Open Çalma listeleri | ok | [230-phone-tr-open-alma-listeleri.png](shots/230-phone-tr-open-alma-listeleri.png) |  |
| 231 | phone-tr | Open Daha fazla (overflow) | ok | [231-phone-tr-open-daha-fazla-overflow.png](shots/231-phone-tr-open-daha-fazla-overflow.png) |  |
| 232 | phone-tr | Settings | ok | [232-phone-tr-settings.png](shots/232-phone-tr-settings.png) |  |
| 233 | phone-tr | Settings scroll 1 | ok | [233-phone-tr-settings-scroll-1.png](shots/233-phone-tr-settings-scroll-1.png) |  |
| 234 | phone-tr | Settings scroll 2 | ok | [234-phone-tr-settings-scroll-2.png](shots/234-phone-tr-settings-scroll-2.png) |  |
| 235 | phone-tr | Settings scroll 3 | ok | [235-phone-tr-settings-scroll-3.png](shots/235-phone-tr-settings-scroll-3.png) |  |
| 236 | phone-tr | Settings scroll 4 | ok | [236-phone-tr-settings-scroll-4.png](shots/236-phone-tr-settings-scroll-4.png) |  |
| 237 | phone-tr | Settings scroll 5 | ok | [237-phone-tr-settings-scroll-5.png](shots/237-phone-tr-settings-scroll-5.png) |  |
| 238 | phone-tr | Settings scroll 6 | ok | [238-phone-tr-settings-scroll-6.png](shots/238-phone-tr-settings-scroll-6.png) |  |
| 239 | phone-tr | Settings scroll 7 | ok | [239-phone-tr-settings-scroll-7.png](shots/239-phone-tr-settings-scroll-7.png) |  |
| 240 | phone-tr | Settings scroll 8 | ok | [240-phone-tr-settings-scroll-8.png](shots/240-phone-tr-settings-scroll-8.png) |  |
| 241 | phone-tr | Settings scroll 9 | ok | [241-phone-tr-settings-scroll-9.png](shots/241-phone-tr-settings-scroll-9.png) |  |
| 242 | phone-tr | Settings scroll 10 | ok | [242-phone-tr-settings-scroll-10.png](shots/242-phone-tr-settings-scroll-10.png) |  |
| 243 | phone-tr | Open About | ok | [243-phone-tr-open-about.png](shots/243-phone-tr-open-about.png) |  |
| 244 | phone-tr | Back from About | ok | [244-phone-tr-back-from-about.png](shots/244-phone-tr-back-from-about.png) |  |

## Warnings

- [tablet-en] labels never seen in the UI: Podcast
- [tablet-en] no 'Open the equaliser' control found in Settings
- [phone-en] no 'Open the equaliser' control found in Settings
- [phone-en] Music list scroll: 57.9 % janky frames (over 40 %; informational on an emulator)
- [phone-en] player drag up: 91.7 % janky frames (over 40 %; informational on an emulator)
- [phone-en] player drag down: 77.8 % janky frames (over 40 %; informational on an emulator)
- [workstation] Music table scroll (tablet): 84.1 % janky frames (over 40 %; informational on an emulator)
- [phone-tr] no 'Ekolayzeri aç' control found in Settings

### Missing native libraries (arm64-only libraries on an x86_64 emulator)

No link errors were logged.

### StrictMode violations: 0 (the app's; by the system's policy for a debuggable app)


### Error-level log lines of the app (top 25)

- 30x `VLC-std: s_glBindAttribLocation: bind attrib 0 name inPosition`
- 30x `VLC-std: s_glBindAttribLocation: bind attrib 1 name inColor`
- 29x `VLC-std: s_glBindAttribLocation: bind attrib 0 name position`
- 23x `VLC-std: s_glBindAttribLocation: bind attrib 2 name inCircleEdge`
- 19x `VLC-std: s_glBindAttribLocation: bind attrib 1 name color`
- 12x `VLC-std: s_glBindAttribLocation: bind attrib 2 name localCoord`
- 9x `VLC-std: s_glBindAttribLocation: bind attrib 1 name localCoord`
- 4x `.nagmedar.debug: Not starting debugger since process cannot load the jdwp agent.`
- 4x `VLC/LibVLC/Util: WARNING: Can't find shared library`
- 4x `VLC-std: s_glBindAttribLocation: bind attrib 2 name inTextureCoords`
- 3x `VLC-std: s_glBindAttribLocation: bind attrib 0 name radii_selector`
- 3x `VLC-std: s_glBindAttribLocation: bind attrib 1 name corner_and_radius_outsets`
- 3x `VLC-std: s_glBindAttribLocation: bind attrib 2 name aa_bloat_and_coverage`
- 3x `VLC-std: s_glBindAttribLocation: bind attrib 3 name radii_x`
- 3x `VLC-std: s_glBindAttribLocation: bind attrib 4 name radii_y`
- 3x `VLC-std: s_glBindAttribLocation: bind attrib 5 name skew`
- 3x `VLC-std: s_glBindAttribLocation: bind attrib 6 name translate_and_localrotate`
- 3x `VLC-std: s_glBindAttribLocation: bind attrib 7 name color`
- 3x `ActivityThread: Failed to find provider info for androidx.car.app.connection`
- 3x `VLC-std: s_glBindAttribLocation: bind attrib 2 name inShadowParams`
- 2x `SurfaceSyncGroup: Failed to receive transaction ready in 1000ms. Marking SurfaceSyncGroup(wmsSync-VRI[NagmedarActivity]#0) as ready`
- 2x `SurfaceSyncGroup: Failed to receive transaction ready in 1000ms. Marking SurfaceSyncGroup(VRI[NagmedarActivity]#1) as ready`
- 2x `FrameTracker: force finish cuj, time out: J<IME_INSETS_SHOW_ANIMATION::0@0@app.nagmedar.debug>`
- 1x `VLC: [00007fe59e4c0330/3177] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [00007fe47e5216f0/3177] libvlc input: Your media can't be opened`
