
## library-folders (PR #29, branch story/library-folders)
- Per-section folders: `library/LibraryScopes.kt` (LibrarySection, SourceFolder, LibraryScopes, Locations, ScopeRules, splitVideos, CameraClips, ClipDates, VideoClip/ClipOrder: pure), `library/LibrarySources.kt` (prefs keys `nagmedar_library_folders_<section>`, lines "+path"/"-path", locationOf(Uri), ensureIndexed → scanSources, suggestions), `library/LibrarySourcesUi.kt` (Settings section + SectionSourcesDialog).
- `NagmedarPreferences.librarySources`; `LibraryAccess.scopes` is a produceState key on every library screen.
- `MediaRepository.videoItems(section)`, `tracks(section)`, `clips()`, `sectionCounts()`, `libraryEmpty()`; unscoped `videoItems()`/`tracks()` stay for search and the palette.
- Videos page: `library/VideosScreen.kt`, Destination.Videos (rail after Series). `filmDrags` is internal with a drag-label param.
- Permissions are never released when a folder leaves a section; backups leave the keys out.

## fix-library-review (PR #45, branch story/fix-library-review)
- Section rules (LibraryScopes.splitVideos): Films/Series = own folders, else library minus Videos' folders; unconstrained Films also skips Series folders' non-episodes; Videos = its folders + every leftover (only an excluded folder hides a video). Tests: LibraryScopesTest.
- FilmInfoCenter: a season TMDB lacks is saved as a FilmEpisodeEntry with episode = NO_EPISODES (-1) + fetchedAt; FilmInfoDao.emptySeasonsAskedBefore; episodes() filters the marker. No schema change.
- Volume: PlaybackService.userVolume / setUserVolume (listener's, saved) / applyUserVolume (not saved); ResumeHook.VolumeRestorer has listenerVolume/engineVolume; VolumeBooster.listenerSets re-bases, release always writes the listener's base. Ducking + SleepTimer restore via applyUserVolume (SleepTarget.userVolume/restoreUserVolume defaults).
- FolderGrants (ui/onboarding): THE check before releasePersistableUriPermission; GrantHolder enum lists every holder; TV Lab/lyrics trees are noted under nagmedar_kept_trees_* (left out of backups). New holders must be added there.
- ColumnSort (components/DenseTable.kt): table sort by column id + StateSaver; rememberFilmKeys (metadata/FilmViews.kt) for film-info keys per item list.

## player-wheel-share (PR #56): Paylaş… and Dosyada göster
- `library/MediaFiles.kt`: `MediaFile` (uri with CUE fragment stripped, video?, name; `local` = file/content), `fileMenuEntries(context, file)` (the two items; none for a stream), `MediaSharing` (`share(context, files)` off-main IO then chooser; `readable(uri, isFile, provider)` content as is / file via `<pkg>.provider`; `sendIntent(files)` SEND or SEND_MULTIPLE + ClipData + READ grant; `commonType`). mediaMenu adds them after "Medya bilgisi"; MusicActions has `share`/`reveal` (ServiceMusicActions implements; group menus and the tracks'/playlists' selection bars use them); MoviesLibrary/ClipsContent take `onShareAll`.
- `device/DeviceReveal.kt`: `FileReveal.show(context, uri)` → `pathReveal` (drive trail, pure, tested) / document via a picked tree (`findDocumentPath`, else external storage ids `storagePath`) → `DeviceRequests.open(DeviceReveal)` (warmed in ProcessState; NagmedarApp navigates to Destination.Device; DeviceScreen calls `model.reveal`, EntryTable selects + scrolls to `model.pointed`, then `pointedShown()`); other trees → files app (ACTION_VIEW, MIME_TYPE_DIR), notices otherwise.
- `MusicActions.share`/`reveal` are default-bodied properties: master's `SourcedMusicActions` (`MusicActions by base`) forwards them, so group/track menus keep them under `playingFrom(...)`.
- Rebase hazard: CHANGELOG.md and TERIMLER.md are `merge=union` too (.git/info/attributes). When master rewords a line your bullet sits next to, the union keeps BOTH wordings silently (no conflict). After a rebase, `git diff origin/master HEAD -- CHANGELOG.md` must show only your own `+` lines.

## library-fast (PR #101, branch story/library-fast): the music list at once, no rescan on every start
- Start path before: StartActivity.startNagmedar → startMedialibrary(parse=true) on every launch → MediaParsingService
  startScan → KEY_MEDIALIBRARY_AUTO_RESCAN → medialibrary.reload() (walk of every folder; unchanged files are not
  re-parsed, but the walk shows "Medya klasörleri aranıyor"). The Music (rememberMusicMedia) had nothing in memory in a new
  process → LoadingState until the ML started; then re-read every 4 s while scanning (loadWhileScanning, removed).
  SafLibrary kept its listing in memory only (every start walked every picked tree).
- Service: `recheck()` (start and ACTION_RECHECK) compares `MediaIndexStamp.read` (vlc-android util: per volume, count +
  max GENERATION_MODIFIED of library audio, Android/ and ringtones left out) with NAGMEDAR_LIBRARY_STAMP/_CHECKED_AT
  (tools NagmedarKeys; left out of backups) via `LibraryRecheck.needed` (tools app.nagmedar.library, 24 h max age).
  A needed look is `quiet` (Show notifications/progress suppressed; first FGS notification without the pause button).
  `fullLook`/`looking`/`fullLookDone`/`lookOver`: the stamp is kept when the reload completes (or the first scan's
  idle end). Work asked by the user (ACTION_RELOAD/FORCE_RELOAD/DISCOVER*) clears `quiet`. `recheckAfter` defers a
  comparison asked while the ML works (made in exitCommand).
- UI: `MusicSnapshot` (library/, no-backup file nagmedar_music_snapshot, binary v1, scope = MediaRepository.musicScope())
  preloaded in NagmedarActivity.onCreate (IO); `rememberRead(kept = …)` gives the first frame. `MusicRead` (+
  `MusicFingerprint`, tags + lastModified) — MediaWrapper.equals is by id, so a list compare must use MusicRead.
  `rememberMusicRead(repository)` (was rememberMusicMedia) re-reads on `MediaRepository.musicChanges()` (ML MediaCb,
  idle, SafLibrary.listen), 1 s / 4 s while working; keeps the list (≤ every 30 s while working).
- `LibraryWatch` (MediaStore observer while a window is started) → `Context.recheckLibrary()` (vlc-android) → quiet look:
  a new album appears within seconds.
- `SafLibrary`: per-tree listing on disk (no-backup nagmedar_picked_folders/, `SafListingStore`), shown at once, walked
  once per process (`SafListing.merge`: unreadable folder/tree keeps rows); `forget` deletes the file, `rescan` re-walks.
- Settings › Kütüphane: "Kütüphaneyi yeniden tara" (`rescanLibrary`: reloadLibrary + SafLibrary.rescan + notice);
  `LibrarySourcesSection(scopes, sources, onRescan)` is the testable body.
- Service stop race fixed: `commands` counter (the delayed stopService skips when a command came meanwhile), the wake lock
  taken first in onStartCommand, released in onDestroy ("WakeLock finalized while still held" is gone from the walk).
- `StartupPerf.musicListShown` logs "NagmedarPerf: music_list ms=… tracks=… kept=…" (process start → list on screen).
- `MusicContent(library, scan, onAddFolder, browser)` (music/MusicScreen.kt) is the page under its header; tested by
  MusicKnownListTest (known list stays through a scan; only a page with no list shows "Kütüphane yükleniyor…").
- Tests: tools LibraryRecheckTest; nagmedar-ui SafListingTest, MusicSnapshotTest (incl. Compose: known list + strip),
  LibrarySettingsScreenshotTest (3 refs, `LibrarySourcesSection(scopes, sources, onRescan)` with faked SourceActions:
  StorageManager in Robolectric poisons the JVM). Walk: phone job `music_after_restart()` right after "After the scan"
  (force-stop, logcat -c, am start -W, rows + the app's music_list line + "startup cold_ms"; fails on a scanning state or
  > 8 s). Measure another commit's app with this driver: dispatch emulator.yml on this branch with inputs[ref]=<sha>.
- Measured (walk, API 35 debug, phone job): master = the list waited for the medialibrary and "Scanning the library" stood
  over it after a restart (be22e99 run: check failed); this branch = the kept list (65 tracks) on screen ~3.7-4.2 s after
  the process started on the emulator, no strip, every run. Emulator first-frame times swing 1.4-4.2 s between runners:
  compare only within one dispatch.
- Left: the ML itself still does the 24 h look (quiet); a picked tree's tags are still its file names (as before).
