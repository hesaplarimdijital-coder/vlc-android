# Emulator smoke test: FAILED

- app: `app.nagmedar.debug`, APK `debug`
- installed: versionCode=14010002 minSdk=34 targetSdk=36; versionName=1.0.2; pkgFlags=[ DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP KILL_AFTER_RESTORE LARGE_HEAP ]
- device: model=sdk_gphone64_x86_64, sdk=37, abi=x86_64, build=sdk_gphone64_x86_64-userdebug 17 CE2A.260420.051.A3 16571990 dev-keys, size=Physical size: 2560x1600, density=Physical density: 320
- source: 8d9e266fcc27ee77256fd7a5b063d2dbafb36fe3 (story/train-1, debug build, API 37.0, tablet), run 42160726915
- passes: tablet-en,workstation,phone-tr
- 134 steps in 28 min 29 s; 0 crash/ANR finding(s), 0 driver error(s), 1 StrictMode violation(s), 1 of 67 1.0 checks failed; 1 ANR/crash of the system (the emulator's, not failures)

## 1.0 checks

| pass | step | check | result | seen |
|------|------|-------|--------|------|
| workstation | 49 | the Music shows the inspector | ok |  |
| workstation | 50 | Ctrl+I hides the inspector | ok |  |
| workstation | 51 | Ctrl+I shows the inspector again | ok |  |
| workstation | 51 | the table's column titles | ok | Title, Artist, Album, Year, Duration |
| workstation | 52 | the column chooser opens | ok |  |
| workstation | 53 | Escape closes the column chooser | ok |  |
| workstation | 54 | This device is found (⋯ or the sidebar) | ok |  |
| workstation | 55 | This device lists the places, the internal storage first | ok |  |
| workstation | 56 | Internal storage opens and lists its folders (Music among them) | ok | rows: Alarms, Android, Audiobooks, Changed, DCIM, Documents, Download, Movies, Music, Name, Notifications, Nov 6, 2026 4:19 AM |
| workstation | 57 | the Music folder lists the walk's files (tone.mp3) | ok |  |
| workstation | 58 | a file of the folder plays | ok | transport bar: tone |
| workstation | 58 | the transport bar's open button | ok |  |
| workstation | 59 | the bar's open button opens the big player | ok |  |
| workstation | 59 | the big player covers the window | ok | player (0, 48, 2560, 1600) in 2560x1600 |
| workstation | 59 | the bars stand aside under it | ok |  |
| workstation | 61 | Back closes the big player | ok |  |
| workstation | 61 | the trail names the drive | ok |  |
| workstation | 62 | back up at the drive, its folders listed | ok |  |
| workstation | 62 | the sort menu's button | ok |  |
| workstation | 63 | the sort menu lists the orders and the hidden files | ok |  |
| workstation | 64 | after 'By size' the drive still lists its folders | ok |  |
| workstation | 64 | the sort menu's button | ok |  |
| workstation | 65 | the sort menu lists the orders and the hidden files | ok |  |
| workstation | 66 | after 'By name' the drive still lists its folders | ok |  |
| workstation | 66 | the sort menu's button | ok |  |
| workstation | 67 | the sort menu lists the orders and the hidden files | ok |  |
| workstation | 68 | after 'Show hidden files' the drive still lists its folders | ok |  |
| workstation | 68 | the sort menu's button | ok |  |
| workstation | 69 | the sort menu lists the orders and the hidden files | ok |  |
| workstation | 70 | after 'Show hidden files' the drive still lists its folders | ok |  |
| workstation | 70 | the places stay beside the folder | ok |  |
| workstation | 73 | after Home the folder is still open | ok |  |
| workstation | 74 | rotated, the folder is still open | ok |  |
| workstation | 75 | rotated back, the folder is still open | ok |  |
| phone-tr | 77 | bottom bar is Müzik · Podcast · Radyo · Daha fazla | ok | seen: Müzik · Podcast · Radyo · Daha fazla |
| phone-tr | 78 | the top bar keeps its colour while the list scrolls | ok | at rest (250, 251, 252), while dragged (250, 251, 252), after (250, 251, 252) |
| phone-tr | 79 | ⋯ in the bottom bar | ok |  |
| phone-tr | 80 | ⋯ opens the More page | ok | seen: İndirmeler, Ayarlar, Çalma listeleri |
| phone-tr | 80 | Settings on the More page | ok |  |
| phone-tr | 81 | Settings is a page of its own: no bottom bar | ok | bottom bar: none |
| phone-tr | 81 | Settings has a back arrow | ok |  |
| phone-tr | 82 | Back from Settings shows More | ok | title: Kütüphane, Daha fazla |
| phone-tr | 84 | the back arrow returns to More | ok |  |
| phone-tr | 84 | Bu cihaz is found (⋯ or the sidebar) | ok |  |
| phone-tr | 85 | Bu cihaz lists the places, the internal storage first | ok |  |
| phone-tr | 86 | Dahili depolama opens and lists its folders (Music among them) | ok | rows: 6 Kas 2026 04:19, 6 Kas 2026 04:28, Ad, Alarms, Android, Audiobooks, DCIM, Daha fazla, Documents, Download, Movies, Music |
| phone-tr | 87 | the Music folder lists the walk's files (tone.mp3) | ok |  |
| phone-tr | 88 | a file of the folder plays | ok | mini player |
| phone-tr | 89 | back up at the drive, its folders listed | ok |  |
| phone-tr | 89 | the sort menu's button | ok |  |
| phone-tr | 90 | the sort menu lists the orders and the hidden files | ok |  |
| phone-tr | 91 | after 'Boyuta göre' the drive still lists its folders | ok |  |
| phone-tr | 91 | the sort menu's button | ok |  |
| phone-tr | 92 | the sort menu lists the orders and the hidden files | ok |  |
| phone-tr | 93 | after 'Ada göre' the drive still lists its folders | ok |  |
| phone-tr | 93 | the sort menu's button | ok |  |
| phone-tr | 94 | the sort menu lists the orders and the hidden files | ok |  |
| phone-tr | 95 | after 'Gizli dosyaları göster' the drive still lists its folders | ok |  |
| phone-tr | 95 | the sort menu's button | ok |  |
| phone-tr | 96 | the sort menu lists the orders and the hidden files | ok |  |
| phone-tr | 97 | after 'Gizli dosyaları göster' the drive still lists its folders | ok |  |
| phone-tr | 98 | Back goes on to the places | ok |  |
| phone-tr | 102 | after Home the folder is still open | ok |  |
| phone-tr | 103 | rotated, the folder is still open | ok |  |
| phone-tr | 104 | rotated back, the folder is still open | ok |  |
| all | 134 | no film, series, video, Home, TV Lab or Whisper words on the app's screens | ok |  |
| all | 134 | no StrictMode violation of the app (debug build) | **FAILED** | 1 in the log, 0 in the dropbox |

## Frames (dumpsys gfxinfo; informational on an emulator, over 40 % janky is flagged)

| pass | step | gesture | frames | janky | 50th | 90th | 95th | 99th |
|------|------|---------|--------|-------|------|------|------|------|
| workstation | 53 | Music table scroll (tablet) | 36 | 100.0 % **(over 40 %)** | 101 ms | 150 ms | 250 ms | 450 ms |

## The system's ANRs and crashes (the emulator's own: recorded, the step tried once more)

| pass | step | what | whose | seen | how |
|------|------|------|-------|------|-----|
| tablet-en | 1 | ANR | com.google.android.gms.persistent | Reason: Broadcast of Intent { act=android.intent.action.SIM_STATE_CHANGED flg=0x15000010 xflg=0x4 cmp=com.google.android.gms/.chimera.GmsIntentOperationService$PersistentTrustedReceiver (has extras) } | log |

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
| 42 | workstation | Launch (the walk's media pushed) | ok | [042-workstation-launch-the-walk-s-media-pushed.png](shots/042-workstation-launch-the-walk-s-media-pushed.png) |  |
| 43 | workstation | Show the first run again | ok | [043-workstation-show-the-first-run-again.png](shots/043-workstation-show-the-first-run-again.png) |  |
| 44 | workstation | First run again step 1 (Continue) | ok | [044-workstation-first-run-again-step-1-continue.png](shots/044-workstation-first-run-again-step-1-continue.png) |  |
| 45 | workstation | First run again step 2 (Continue · 2 sources) | ok | [045-workstation-first-run-again-step-2-continue-2-sources.png](shots/045-workstation-first-run-again-step-2-continue-2-sources.png) |  |
| 46 | workstation | First run again step 3 (Continue) | ok | [046-workstation-first-run-again-step-3-continue.png](shots/046-workstation-first-run-again-step-3-continue.png) |  |
| 47 | workstation | First run again step 4 (Start) | ok | [047-workstation-first-run-again-step-4-start.png](shots/047-workstation-first-run-again-step-4-start.png) |  |
| 48 | workstation | After the scan | ok | [048-workstation-after-the-scan.png](shots/048-workstation-after-the-scan.png) |  |
| 49 | workstation | Music | ok | [049-workstation-music.png](shots/049-workstation-music.png) |  |
| 50 | workstation | Ctrl+I (hide the inspector) | ok | [050-workstation-ctrl-i-hide-the-inspector.png](shots/050-workstation-ctrl-i-hide-the-inspector.png) |  |
| 51 | workstation | Ctrl+I (show the inspector) | ok | [051-workstation-ctrl-i-show-the-inspector.png](shots/051-workstation-ctrl-i-show-the-inspector.png) |  |
| 52 | workstation | Long press on the column title Artist | ok | [052-workstation-long-press-on-the-column-title-artist.png](shots/052-workstation-long-press-on-the-column-title-artist.png) |  |
| 53 | workstation | Escape closes the column chooser | ok | [053-workstation-escape-closes-the-column-chooser.png](shots/053-workstation-escape-closes-the-column-chooser.png) |  |
| 54 | workstation | Music table scroll (tablet) (after the scroll) | ok | [054-workstation-music-table-scroll-tablet-after-the-scroll.png](shots/054-workstation-music-table-scroll-tablet-after-the-scroll.png) |  |
| 55 | workstation | Open This device | ok | [055-workstation-open-this-device.png](shots/055-workstation-open-this-device.png) |  |
| 56 | workstation | Open Internal storage | ok | [056-workstation-open-internal-storage.png](shots/056-workstation-open-internal-storage.png) |  |
| 57 | workstation | Open its Music folder | ok | [057-workstation-open-its-music-folder.png](shots/057-workstation-open-its-music-folder.png) |  |
| 58 | workstation | Play tone.mp3 from the folder | ok | [058-workstation-play-tone-mp3-from-the-folder.png](shots/058-workstation-play-tone-mp3-from-the-folder.png) |  |
| 59 | workstation | The bar's open button | ok | [059-workstation-the-bar-s-open-button.png](shots/059-workstation-the-bar-s-open-button.png) |  |
| 60 | workstation | The big player (screenshot) | ok | [060-workstation-the-big-player-screenshot.png](shots/060-workstation-the-big-player-screenshot.png) |  |
| 61 | workstation | Back: the big player closes | ok | [061-workstation-back-the-big-player-closes.png](shots/061-workstation-back-the-big-player-closes.png) |  |
| 62 | workstation | The trail back to Internal storage | ok | [062-workstation-the-trail-back-to-internal-storage.png](shots/062-workstation-the-trail-back-to-internal-storage.png) |  |
| 63 | workstation | Sort menu | ok | [063-workstation-sort-menu.png](shots/063-workstation-sort-menu.png) |  |
| 64 | workstation | Sort menu: By size | ok | [064-workstation-sort-menu-by-size.png](shots/064-workstation-sort-menu-by-size.png) |  |
| 65 | workstation | Sort menu | ok | [065-workstation-sort-menu.png](shots/065-workstation-sort-menu.png) |  |
| 66 | workstation | Sort menu: By name | ok | [066-workstation-sort-menu-by-name.png](shots/066-workstation-sort-menu-by-name.png) |  |
| 67 | workstation | Sort menu | ok | [067-workstation-sort-menu.png](shots/067-workstation-sort-menu.png) |  |
| 68 | workstation | Sort menu: Show hidden files | ok | [068-workstation-sort-menu-show-hidden-files.png](shots/068-workstation-sort-menu-show-hidden-files.png) |  |
| 69 | workstation | Sort menu | ok | [069-workstation-sort-menu.png](shots/069-workstation-sort-menu.png) |  |
| 70 | workstation | Sort menu: Show hidden files | ok | [070-workstation-sort-menu-show-hidden-files.png](shots/070-workstation-sort-menu-show-hidden-files.png) |  |
| 71 | workstation | Open the Music folder again | ok | [071-workstation-open-the-music-folder-again.png](shots/071-workstation-open-the-music-folder-again.png) |  |
| 72 | workstation | Home with the folder open | warn | [072-workstation-home-with-the-folder-open.png](shots/072-workstation-home-with-the-folder-open.png) | the app is not in the foreground (windows: com.google.android.apps.nexuslauncher, com.google.android.calendar, com.google.android.deskclock, com.google.android.googlequicksearchbox) |
| 73 | workstation | Back in the app with the folder open | ok | [073-workstation-back-in-the-app-with-the-folder-open.png](shots/073-workstation-back-in-the-app-with-the-folder-open.png) |  |
| 74 | workstation | Rotated with the folder open | ok | [074-workstation-rotated-with-the-folder-open.png](shots/074-workstation-rotated-with-the-folder-open.png) |  |
| 75 | workstation | Rotated back with the folder open | ok | [075-workstation-rotated-back-with-the-folder-open.png](shots/075-workstation-rotated-back-with-the-folder-open.png) |  |
| 76 | phone-tr | Launch (Turkish) | ok | [076-phone-tr-launch-turkish.png](shots/076-phone-tr-launch-turkish.png) |  |
| 77 | phone-tr | Music | ok | [077-phone-tr-music.png](shots/077-phone-tr-music.png) |  |
| 78 | phone-tr | Music scrolled (top bar) | ok | [078-phone-tr-music-scrolled-top-bar.png](shots/078-phone-tr-music-scrolled-top-bar.png) |  |
| 79 | phone-tr | Music | ok | [079-phone-tr-music.png](shots/079-phone-tr-music.png) |  |
| 80 | phone-tr | Bottom bar ⋯ | ok | [080-phone-tr-bottom-bar.png](shots/080-phone-tr-bottom-bar.png) |  |
| 81 | phone-tr | Settings from More | ok | [081-phone-tr-settings-from-more.png](shots/081-phone-tr-settings-from-more.png) |  |
| 82 | phone-tr | Back from Settings | ok | [082-phone-tr-back-from-settings.png](shots/082-phone-tr-back-from-settings.png) |  |
| 83 | phone-tr | Settings from More again | ok | [083-phone-tr-settings-from-more-again.png](shots/083-phone-tr-settings-from-more-again.png) |  |
| 84 | phone-tr | The back arrow | ok | [084-phone-tr-the-back-arrow.png](shots/084-phone-tr-the-back-arrow.png) |  |
| 85 | phone-tr | Open Bu cihaz | ok | [085-phone-tr-open-bu-cihaz.png](shots/085-phone-tr-open-bu-cihaz.png) |  |
| 86 | phone-tr | Open Dahili depolama | ok | [086-phone-tr-open-dahili-depolama.png](shots/086-phone-tr-open-dahili-depolama.png) |  |
| 87 | phone-tr | Open its Music folder | ok | [087-phone-tr-open-its-music-folder.png](shots/087-phone-tr-open-its-music-folder.png) |  |
| 88 | phone-tr | Play tone.mp3 from the folder | ok | [088-phone-tr-play-tone-mp3-from-the-folder.png](shots/088-phone-tr-play-tone-mp3-from-the-folder.png) |  |
| 89 | phone-tr | Back (up to Dahili depolama) | ok | [089-phone-tr-back-up-to-dahili-depolama.png](shots/089-phone-tr-back-up-to-dahili-depolama.png) |  |
| 90 | phone-tr | Sort menu | ok | [090-phone-tr-sort-menu.png](shots/090-phone-tr-sort-menu.png) |  |
| 91 | phone-tr | Sort menu: Boyuta göre | ok | [091-phone-tr-sort-menu-boyuta-g-re.png](shots/091-phone-tr-sort-menu-boyuta-g-re.png) |  |
| 92 | phone-tr | Sort menu | ok | [092-phone-tr-sort-menu.png](shots/092-phone-tr-sort-menu.png) |  |
| 93 | phone-tr | Sort menu: Ada göre | ok | [093-phone-tr-sort-menu-ada-g-re.png](shots/093-phone-tr-sort-menu-ada-g-re.png) |  |
| 94 | phone-tr | Sort menu | ok | [094-phone-tr-sort-menu.png](shots/094-phone-tr-sort-menu.png) |  |
| 95 | phone-tr | Sort menu: Gizli dosyaları göster | ok | [095-phone-tr-sort-menu-gizli-dosyalar-g-ster.png](shots/095-phone-tr-sort-menu-gizli-dosyalar-g-ster.png) |  |
| 96 | phone-tr | Sort menu | ok | [096-phone-tr-sort-menu.png](shots/096-phone-tr-sort-menu.png) |  |
| 97 | phone-tr | Sort menu: Gizli dosyaları göster | ok | [097-phone-tr-sort-menu-gizli-dosyalar-g-ster.png](shots/097-phone-tr-sort-menu-gizli-dosyalar-g-ster.png) |  |
| 98 | phone-tr | Back (to the places) | ok | [098-phone-tr-back-to-the-places.png](shots/098-phone-tr-back-to-the-places.png) |  |
| 99 | phone-tr | Open Dahili depolama again | ok | [099-phone-tr-open-dahili-depolama-again.png](shots/099-phone-tr-open-dahili-depolama-again.png) |  |
| 100 | phone-tr | Open the Music folder again | ok | [100-phone-tr-open-the-music-folder-again.png](shots/100-phone-tr-open-the-music-folder-again.png) |  |
| 101 | phone-tr | Home with the folder open | warn | [101-phone-tr-home-with-the-folder-open.png](shots/101-phone-tr-home-with-the-folder-open.png) | the app is not in the foreground (windows: com.google.android.apps.nexuslauncher, com.google.android.deskclock, com.google.android.googlequicksearchbox) |
| 102 | phone-tr | Back in the app with the folder open | ok | [102-phone-tr-back-in-the-app-with-the-folder-open.png](shots/102-phone-tr-back-in-the-app-with-the-folder-open.png) |  |
| 103 | phone-tr | Rotated with the folder open | ok | [103-phone-tr-rotated-with-the-folder-open.png](shots/103-phone-tr-rotated-with-the-folder-open.png) |  |
| 104 | phone-tr | Rotated back with the folder open | ok | [104-phone-tr-rotated-back-with-the-folder-open.png](shots/104-phone-tr-rotated-back-with-the-folder-open.png) |  |
| 105 | phone-tr | Back (up to the drive) | ok | [105-phone-tr-back-up-to-the-drive.png](shots/105-phone-tr-back-up-to-the-drive.png) |  |
| 106 | phone-tr | Back (to the places) | ok | [106-phone-tr-back-to-the-places.png](shots/106-phone-tr-back-to-the-places.png) |  |
| 107 | phone-tr | Open Müzik | ok | [107-phone-tr-open-m-zik.png](shots/107-phone-tr-open-m-zik.png) |  |
| 108 | phone-tr | Open Podcast | ok | [108-phone-tr-open-podcast.png](shots/108-phone-tr-open-podcast.png) |  |
| 109 | phone-tr | Open Podcastler | ok | [109-phone-tr-open-podcastler.png](shots/109-phone-tr-open-podcastler.png) |  |
| 110 | phone-tr | Open Radyo | ok | [110-phone-tr-open-radyo.png](shots/110-phone-tr-open-radyo.png) |  |
| 111 | phone-tr | Open Daha fazla (overflow) | ok | [111-phone-tr-open-daha-fazla-overflow.png](shots/111-phone-tr-open-daha-fazla-overflow.png) |  |
| 112 | phone-tr | Open Bu cihaz | ok | [112-phone-tr-open-bu-cihaz.png](shots/112-phone-tr-open-bu-cihaz.png) |  |
| 113 | phone-tr | Open Daha fazla (overflow) | ok | [113-phone-tr-open-daha-fazla-overflow.png](shots/113-phone-tr-open-daha-fazla-overflow.png) |  |
| 114 | phone-tr | Open Ağ | ok | [114-phone-tr-open-a.png](shots/114-phone-tr-open-a.png) |  |
| 115 | phone-tr | Open Daha fazla (overflow) | ok | [115-phone-tr-open-daha-fazla-overflow.png](shots/115-phone-tr-open-daha-fazla-overflow.png) |  |
| 116 | phone-tr | Open Akışlar | ok | [116-phone-tr-open-ak-lar.png](shots/116-phone-tr-open-ak-lar.png) |  |
| 117 | phone-tr | Open Daha fazla (overflow) | ok | [117-phone-tr-open-daha-fazla-overflow.png](shots/117-phone-tr-open-daha-fazla-overflow.png) |  |
| 118 | phone-tr | Open İndirmeler | ok | [118-phone-tr-open-ndirmeler.png](shots/118-phone-tr-open-ndirmeler.png) |  |
| 119 | phone-tr | Open Daha fazla (overflow) | ok | [119-phone-tr-open-daha-fazla-overflow.png](shots/119-phone-tr-open-daha-fazla-overflow.png) |  |
| 120 | phone-tr | Open Çalma listeleri | ok | [120-phone-tr-open-alma-listeleri.png](shots/120-phone-tr-open-alma-listeleri.png) |  |
| 121 | phone-tr | Open Daha fazla (overflow) | ok | [121-phone-tr-open-daha-fazla-overflow.png](shots/121-phone-tr-open-daha-fazla-overflow.png) |  |
| 122 | phone-tr | Settings | ok | [122-phone-tr-settings.png](shots/122-phone-tr-settings.png) |  |
| 123 | phone-tr | Settings scroll 1 | ok | [123-phone-tr-settings-scroll-1.png](shots/123-phone-tr-settings-scroll-1.png) |  |
| 124 | phone-tr | Settings scroll 2 | ok | [124-phone-tr-settings-scroll-2.png](shots/124-phone-tr-settings-scroll-2.png) |  |
| 125 | phone-tr | Settings scroll 3 | ok | [125-phone-tr-settings-scroll-3.png](shots/125-phone-tr-settings-scroll-3.png) |  |
| 126 | phone-tr | Settings scroll 4 | ok | [126-phone-tr-settings-scroll-4.png](shots/126-phone-tr-settings-scroll-4.png) |  |
| 127 | phone-tr | Settings scroll 5 | ok | [127-phone-tr-settings-scroll-5.png](shots/127-phone-tr-settings-scroll-5.png) |  |
| 128 | phone-tr | Settings scroll 6 | ok | [128-phone-tr-settings-scroll-6.png](shots/128-phone-tr-settings-scroll-6.png) |  |
| 129 | phone-tr | Settings scroll 7 | ok | [129-phone-tr-settings-scroll-7.png](shots/129-phone-tr-settings-scroll-7.png) |  |
| 130 | phone-tr | Settings scroll 8 | ok | [130-phone-tr-settings-scroll-8.png](shots/130-phone-tr-settings-scroll-8.png) |  |
| 131 | phone-tr | Settings scroll 9 | ok | [131-phone-tr-settings-scroll-9.png](shots/131-phone-tr-settings-scroll-9.png) |  |
| 132 | phone-tr | Settings scroll 10 | ok | [132-phone-tr-settings-scroll-10.png](shots/132-phone-tr-settings-scroll-10.png) |  |
| 133 | phone-tr | Open About | ok | [133-phone-tr-open-about.png](shots/133-phone-tr-open-about.png) |  |
| 134 | phone-tr | Back from About | ok | [134-phone-tr-back-from-about.png](shots/134-phone-tr-back-from-about.png) | check failed: no StrictMode violation of the app (debug build) |

## Warnings

- [tablet-en] labels never seen in the UI: Podcast
- [tablet-en] no 'Open the equaliser' control found in Settings
- [workstation] Music table scroll (tablet): 100.0 % janky frames (over 40 %; informational on an emulator)
- [phone-tr] no 'Ekolayzeri aç' control found in Settings

### Missing native libraries (arm64-only libraries on an x86_64 emulator)

No link errors were logged.

### StrictMode violations: 1 (the app's; by the system's policy for a debuggable app)

- 1x DiskReadViolation at `app.nagmedar.ui.covers.CoverStore.kept`

The first whole stack of each is in strictmode.txt.

### Error-level log lines of the app (top 25)

- 4x `VLC/LibVLC/Util: WARNING: Can't find shared library`
- 2x `ActivityThread: Failed to find provider info for androidx.car.app.connection`
- 1x `VLC: [0000780630860f70/285a] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [00007805f0a1e150/285a] libvlc input: Your media can't be opened`
- 1x `VLC: [00007805f0a1e150/285a] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/obb/'. Check the log for details.`
- 1x `VLC: [000078063085aeb0/2861] libvlc access: cannot open file /storage/emulated/0/Android/data/ (Permission denied)`
- 1x `VLC: [00007805f0a1e150/2861] libvlc input: Your media can't be opened`
- 1x `VLC: [00007805f0a1e150/2861] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/data/'. Check the log for details.`
