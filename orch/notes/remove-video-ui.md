# remove-video-ui (PR #72, story/remove-video-ui)

## NOTES FOR THE NEXT STORY
- Compose app is audio-only. Destinations: Music (start), Podcasts, Radio, Playlists, Device, Network, Streams, TvLab, Downloads, Settings…; `Destination.fromRoute(...) ?: Music`, so "home"/"movies" from StartActivity land on Music.
- Moved: DialogFrame/DialogCard/LocalDialogDismiss and PanelParts → `app.nagmedar.ui.components`; Whisper + SubtitleDocument/NewSubtitleFiles/DocumentIdentity → `app.nagmedar.ui.transcribe` (TranscriptFiles.kt holds writeNewDocument/saveSubtitle); SubtitleEncoding → `lyrics`; EqualizerPreset → `player/equalizer/EqualizerPreset.kt`; snapshotFolderLabel → `library.pickedFolderLabel`.
- Whisper has no entry point any more (only Settings/Downloads model management); its dialog still says "subtitles".
- Videos reaching the app (drop, network/device browsers, podcast video enclosures) play with MEDIA_FORCE_AUDIO.
- Old notification channels are deleted at start (notify/NotificationIds.kt RemovedChannels); backup LEFT_OUT has REMOVED_FEATURES keys.
- Left for the classic side: PlayerChrome reflection (DesktopPlayerChrome, CastingPlayerChrome), PlaybackObserver's RecordingObserver entry, VideoPlayerActivity (+ manifest merge entry in nagmedar-ui), PlaybackActions.playsVideo/videoAway/videoInBars, StartActivity "movies"/"home" routes, vlc-android shortcuts.xml video shortcut, MediaSessionBrowser/PlaybackService video branches, benchmark ScrollBenchmark films()/PlaybackTest, remote access "Videos" part, nagmedar-translate module (unused), PlayerEqualizerScreen (kept, unreachable), Room video entities.
- ComposeSandboxTest sanity threshold lowered to > 50 Compose tests (TV Lab removal will drop more).

## remove-video-classic (PR #76, story/remove-video-classic) — notes for the next story of this track
- Classic side audio-only: no VideoPlayerActivity/gui/video/popup/PiP, no PlayerChrome hooks, no RecordingObserver, no video intent filters or launcher shortcut; libVLC global `--no-video` (resources/VLCOptions.kt); MEDIA_FORCE_AUDIO kept on the Compose side.
- nagmedar-translate module + native/translate gone; native/lab: mediaprobe without video/HDR/DV (no libdovi/cargo in build.sh, versions.env, CI cache key), subtitle_extract gone.
- DB 22 (Migration21To22 in NagmedarDatabase): DROP TABLE IF EXISTS of `NagmedarDatabase.RETIRED_TABLES` (media_settings, transcode_profile, transcode_job, remux_job, film_info, film_episode). Before dropping it notes remux/transcode input/output URIs in `NagmedarDatabase.retiredGrants(context)` (noBackupFilesDir/nagmedar_retired_grants); `RetiredWork.tables` (flag `video_tables`) opens the DB, releases those grants via FolderGrants.release, deletes the note and files/tvlab. `NagmedarDatabase.migrations(grants)`; `MIGRATIONS` = without the note.
- Backups: BackupPart.FileSettings gone; old backups' `file_settings` ignored.
- Remote access: web patch 0004-audio-only (header + routes; RemoteAccessBrandTest allows src/routes.js).
- Left: testmedia/ (separate workflow) still makes video/DV samples with dovi_tool; Compose NowPlaying/QueueEntry.isVideo still only pick a video artwork icon; MediaTechEntry (media_tech) kept (S-802 probe cache).

## retired-cleanup (PR #81, story/retired-cleanup, 41e9f675) — notes for the next story of this track
- RetiredWork has three flags in prefs file nagmedar_retired: tv_lab, video_tables, video_features. A new removal gets a new flag/step; never reset an old one.
- video_features: cancels 0.5's unique works (transcode, subtitle-extract/-translate, translate-model-<id>) and cancelAllWorkByTag(<removed worker FQN>) (WorkManager tags each request with its class); deletes noBackupFilesDir/translate-models|translate-jobs|extract-jobs, files/hls|subtitles|transcode, external transcode, cache film-images|overlays; releases nagmedar_snapshot_folder/recording_folder/subtitle_folder grants, then removes the keys.
- FolderGrants.releaseNow(context, uri, modes): Boolean = release on the calling thread (false = holders unreadable, nothing released). RetiredWork's steps use it and leave their flag unset (retry next start) on false. FolderGrants.release is the async wrapper.
- Tests must call RetiredWork.onProcessStart off the main thread (Room asserts it via uses()); RetiredWorkTest.start() does.
- Not cleaned (user data or per-file): getExternalFilesDir(null)/Subtitles (0.5's saved subtitles), the player's recordings/screenshots under external files, per-file READ grants of subtitle files 0.5 loaded (kept only in the medialibrary's slaves), snapshot format/hdr keys.
