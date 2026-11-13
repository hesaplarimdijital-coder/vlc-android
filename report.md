# Emulator smoke test: passed

- app: `app.nagmedar.debug`, APK `personal`
- installed: versionCode=14010016 minSdk=34 targetSdk=37; versionName=1.0.8; pkgFlags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP KILL_AFTER_RESTORE LARGE_HEAP ]
- device: model=sdk_gphone64_x86_64, sdk=37, abi=x86_64, build=sdk_gphone64_x86_64-userdebug 17 CE2A.260420.051.A3 16571990 dev-keys, size=Physical size: 2560x1600, density=Physical density: 320
- source: 96d1d59d63b1c9cd4188785660048d9f8f6bd858 (story/release-1.0.8, personal build, API 37.0, search), run 43492268874
- passes: search
- 33 steps in 9 min 7 s; 0 crash/ANR finding(s), 0 driver error(s), 0 StrictMode violation(s), 0 of 33 1.0 checks failed; 0 ANR/crash of the system (the emulator's, not failures)

## 1.0 checks

| pass | step | check | result | seen |
|------|------|-------|--------|------|
| search | 7 | the Music holds the walk's 64 tracks, which the search is compared with | ok | the Music counts 65 |
| search | 8 | Ctrl+K opens the palette (Ctrl+K: a word of every title and album) | ok |  |
| search | 9 | Ctrl+K "profile" (a word of every title and album): the 0 artists that match are all listed, and nothing else | ok | 0 listed |
| search | 9 | Ctrl+K "profile" (a word of every title and album): the 8 albums that match are all listed, and nothing else | ok | 8 listed |
| search | 9 | Ctrl+K "profile" (a word of every title and album): the 64 tracks that match are all listed, and nothing else | ok | 64 listed |
| search | 11 | Ctrl+K opens the palette (Ctrl+K: an artist's name) | ok |  |
| search | 12 | Ctrl+K "nagmedar artist 3" (an artist's name): the 1 artists that match are all listed, and nothing else | ok | 1 listed |
| search | 12 | Ctrl+K "nagmedar artist 3" (an artist's name): the 3 albums that match are all listed, and nothing else | ok | 3 listed |
| search | 12 | Ctrl+K "nagmedar artist 3" (an artist's name): the 29 tracks that match are all listed, and nothing else | ok | 29 listed |
| search | 14 | Ctrl+K opens the palette (Ctrl+K: an album's name) | ok |  |
| search | 15 | Ctrl+K "album 6" (an album's name): the 0 artists that match are all listed, and nothing else | ok | 0 listed |
| search | 15 | Ctrl+K "album 6" (an album's name): the 1 albums that match are all listed, and nothing else | ok | 1 listed |
| search | 15 | Ctrl+K "album 6" (an album's name): the 15 tracks that match are all listed, and nothing else | ok | 15 listed |
| search | 17 | Ctrl+K opens the palette (Ctrl+K: a title with its artist) | ok |  |
| search | 18 | Ctrl+K "track 2.5 artist 1" (a title with its artist): the 0 artists that match are all listed, and nothing else | ok | 0 listed |
| search | 18 | Ctrl+K "track 2.5 artist 1" (a title with its artist): the 0 albums that match are all listed, and nothing else | ok | 0 listed |
| search | 18 | Ctrl+K "track 2.5 artist 1" (a title with its artist): the 1 tracks that match are all listed, and nothing else | ok | 1 listed |
| search | 20 | Ctrl+K opens the palette (Ctrl+K: an artist, by the name) | ok |  |
| search | 21 | Ctrl+K "nagmedar artist 3": the first hit is an artist, by the name | ok | the first row: Nagmedar Artist 3 (Artist · 2 albums · 16 tracks) |
| search | 22 | Enter on Nagmedar Artist 3 (an artist, by the name) opens its tracks in the Music view | ok | its name shows; 8 of the walk's tracks in sight: Profile track 5.1, Profile track 5.2, Profile track 5.3, Profile track 5.4 |
| search | 23 | Ctrl+K opens the palette (Ctrl+K: an album, by its name) | ok |  |
| search | 24 | Ctrl+K "profile album 7": the first hit is an album, by its name | ok | the first row: Profile album 7 (Album · Nagmedar Artist 4 · 8 tracks) |
| search | 25 | Enter on Profile album 7 (an album, by its name) opens its tracks in the Music view | ok | its name shows; 8 of the walk's tracks in sight: Profile track 7.1, Profile track 7.2, Profile track 7.3, Profile track 7.4 |
| search | 26 | Ctrl+K opens the palette (Ctrl+K: a letter many commands have) | ok |  |
| search | 27 | the commands show their best and a line that says how many there are | ok | the heading counts 33; the line: Show all 33 |
| search | 28 | Enter on that line lists every one of the commands, and the commands alone | ok | 33 rows read of 33 |
| search | 30 | the phone's top bar has the search | ok |  |
| search | 32 | the search page "nagmedar artist 3" (an artist's name): the 1 artists that match are all listed, and nothing else | ok | 1 listed |
| search | 32 | the search page "nagmedar artist 3" (an artist's name): the 3 albums that match are all listed, and nothing else | ok | 3 listed |
| search | 32 | the search page "nagmedar artist 3" (an artist's name): the 29 tracks that match are all listed, and nothing else | ok | 29 listed |
| search | 32 | the search page lists the album "Profile album 5" among what it found | ok |  |
| search | 33 | the album tapped on the search page opens its tracks in the Music view | ok | 8 of the walk's tracks in sight: Profile track 5.1, Profile track 5.2, Profile track 5.3, Profile track 5.4 |
| all | 33 | no film, series, video, Home, TV Lab or Whisper words on the app's screens | ok |  |

## Frames (dumpsys gfxinfo; informational on an emulator, over 40 % janky is flagged)

| pass | step | gesture | frames | janky | 50th | 90th | 95th | 99th |
|------|------|---------|--------|-------|------|------|------|------|

## The search (1.0.7: what each query listed, beside what the walk's library holds by the search's rule: every word in the title, the artist or the album)

| pass | where | query | artists listed / in the library | albums | tracks | missing | listed but not in the library | result |
|------|-------|-------|---------------------------------|--------|--------|---------|-------------------------------|--------|
| search | Ctrl+K | `profile` (a word of every title and album) | 0 / 0 | 8 / 8 | 64 / 64 | none | none | ok |
| search | Ctrl+K | `nagmedar artist 3` (an artist's name) | 1 / 1 | 3 / 3 | 29 / 29 | none | none | ok |
| search | Ctrl+K | `album 6` (an album's name) | 0 / 0 | 1 / 1 | 15 / 15 | none | none | ok |
| search | Ctrl+K | `track 2.5 artist 1` (a title with its artist) | 0 / 0 | 0 / 0 | 1 / 1 | none | none | ok |
| search | the search page | `nagmedar artist 3` (an artist's name) | 1 / 1 | 3 / 3 | 29 / 29 | none | none | ok |

## The system's ANRs and crashes (the emulator's own: recorded, the step tried once more)

None.

## Steps

| # | pass | step | result | screenshot | note |
|---|------|------|--------|------------|------|
| 1 | search | Launch (the walk's media pushed) | ok | [001-search-launch-the-walk-s-media-pushed.png](shots/001-search-launch-the-walk-s-media-pushed.png) |  |
| 2 | search | First run with the media step 1 (Continue) | ok | [002-search-first-run-with-the-media-step-1-continue.png](shots/002-search-first-run-with-the-media-step-1-continue.png) |  |
| 3 | search | First run with the media step 2 (Continue · 2 sources) | ok | [003-search-first-run-with-the-media-step-2-continue-2-sourc.png](shots/003-search-first-run-with-the-media-step-2-continue-2-sourc.png) |  |
| 4 | search | First run with the media step 3 (Continue) | ok | [004-search-first-run-with-the-media-step-3-continue.png](shots/004-search-first-run-with-the-media-step-3-continue.png) |  |
| 5 | search | First run with the media step 4 (Start) | ok | [005-search-first-run-with-the-media-step-4-start.png](shots/005-search-first-run-with-the-media-step-4-start.png) |  |
| 6 | search | After the scan | ok | [006-search-after-the-scan.png](shots/006-search-after-the-scan.png) |  |
| 7 | search | Music | ok | [007-search-music.png](shots/007-search-music.png) |  |
| 8 | search | Ctrl+K: a word of every title and album | ok | [008-search-ctrl-k-a-word-of-every-title-and-album.png](shots/008-search-ctrl-k-a-word-of-every-title-and-album.png) |  |
| 9 | search | Palette: profile | ok | [009-search-palette-profile.png](shots/009-search-palette-profile.png) |  |
| 10 | search | The palette closed | ok | [010-search-the-palette-closed.png](shots/010-search-the-palette-closed.png) |  |
| 11 | search | Ctrl+K: an artist's name | ok | [011-search-ctrl-k-an-artist-s-name.png](shots/011-search-ctrl-k-an-artist-s-name.png) |  |
| 12 | search | Palette: nagmedar artist 3 | ok | [012-search-palette-nagmedar-artist-3.png](shots/012-search-palette-nagmedar-artist-3.png) |  |
| 13 | search | The palette closed | ok | [013-search-the-palette-closed.png](shots/013-search-the-palette-closed.png) |  |
| 14 | search | Ctrl+K: an album's name | ok | [014-search-ctrl-k-an-album-s-name.png](shots/014-search-ctrl-k-an-album-s-name.png) |  |
| 15 | search | Palette: album 6 | ok | [015-search-palette-album-6.png](shots/015-search-palette-album-6.png) |  |
| 16 | search | The palette closed | ok | [016-search-the-palette-closed.png](shots/016-search-the-palette-closed.png) |  |
| 17 | search | Ctrl+K: a title with its artist | ok | [017-search-ctrl-k-a-title-with-its-artist.png](shots/017-search-ctrl-k-a-title-with-its-artist.png) |  |
| 18 | search | Palette: track 2.5 artist 1 | ok | [018-search-palette-track-2-5-artist-1.png](shots/018-search-palette-track-2-5-artist-1.png) |  |
| 19 | search | The palette closed | ok | [019-search-the-palette-closed.png](shots/019-search-the-palette-closed.png) |  |
| 20 | search | Ctrl+K: an artist, by the name | ok | [020-search-ctrl-k-an-artist-by-the-name.png](shots/020-search-ctrl-k-an-artist-by-the-name.png) |  |
| 21 | search | Palette: nagmedar artist 3 | ok | [021-search-palette-nagmedar-artist-3.png](shots/021-search-palette-nagmedar-artist-3.png) |  |
| 22 | search | Enter: Nagmedar Artist 3 opens in the Music | ok | [022-search-enter-nagmedar-artist-3-opens-in-the-music.png](shots/022-search-enter-nagmedar-artist-3-opens-in-the-music.png) |  |
| 23 | search | Ctrl+K: an album, by its name | ok | [023-search-ctrl-k-an-album-by-its-name.png](shots/023-search-ctrl-k-an-album-by-its-name.png) |  |
| 24 | search | Palette: profile album 7 | ok | [024-search-palette-profile-album-7.png](shots/024-search-palette-profile-album-7.png) |  |
| 25 | search | Enter: Profile album 7 opens in the Music | ok | [025-search-enter-profile-album-7-opens-in-the-music.png](shots/025-search-enter-profile-album-7-opens-in-the-music.png) |  |
| 26 | search | Ctrl+K: a letter many commands have | ok | [026-search-ctrl-k-a-letter-many-commands-have.png](shots/026-search-ctrl-k-a-letter-many-commands-have.png) |  |
| 27 | search | Palette: s | ok | [027-search-palette-s.png](shots/027-search-palette-s.png) |  |
| 28 | search | Enter on the line: every command | ok | [028-search-enter-on-the-line-every-command.png](shots/028-search-enter-on-the-line-every-command.png) |  |
| 29 | search | The palette closed | ok | [029-search-the-palette-closed.png](shots/029-search-the-palette-closed.png) |  |
| 30 | search | Music | ok | [030-search-music.png](shots/030-search-music.png) |  |
| 31 | search | The search page | ok | [031-search-the-search-page.png](shots/031-search-the-search-page.png) |  |
| 32 | search | Search page: nagmedar artist 3 | ok | [032-search-search-page-nagmedar-artist-3.png](shots/032-search-search-page-nagmedar-artist-3.png) |  |
| 33 | search | The album tapped on the search page | ok | [033-search-the-album-tapped-on-the-search-page.png](shots/033-search-the-album-tapped-on-the-search-page.png) |  |

## Warnings

None.

### Missing native libraries (arm64-only libraries on an x86_64 emulator)

No link errors were logged.

### StrictMode violations: 0 (the app's; by the system's policy for a debuggable app)


### Error-level log lines of the app (top 25)

- 3x `VLC/LibVLC/Util: WARNING: Can't find shared library`
- 2x `FrameTracker: force finish cuj, time out: J<IME_INSETS_SHOW_ANIMATION::0@1@app.nagmedar.debug>`
- 1x `VLC: [000074d24d245cd0/1376] libvlc access: cannot open file /storage/emulated/0/Android/obb/ (Permission denied)`
- 1x `VLC: [000074d17d2c4b70/1376] libvlc input: Your media can't be opened`
- 1x `VLC: [000074d17d2c4b70/1376] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/obb/'. Check the log for details.`
- 1x `VLC: [000074d24d24a4d0/137d] libvlc access: cannot open file /storage/emulated/0/Android/data/ (Permission denied)`
- 1x `VLC: [000074d17d2c4b70/137d] libvlc input: Your media can't be opened`
- 1x `VLC: [000074d17d2c4b70/137d] libvlc input: VLC is unable to open the MRL 'file:///storage/emulated/0/Android/data/'. Check the log for details.`
