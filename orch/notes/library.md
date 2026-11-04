
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
