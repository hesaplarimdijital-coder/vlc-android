# music-services track (S-1008 scrobbling, S-1005 LRC editor) — PR #2, branch story/S-1008

- Scrobbling lives in `ui/scrobble/`: `ScrobbleTracker` (30 s / half / 4 min rule), `ScrobbleHub` fed by
  `PlayStatsRecorder`, Room `scrobble_queue` (DB v13, MIGRATION_12_13, schema 13.json committed) flushed by WorkManager
  (`ScrobbleWork`, unique work, network constraint). Secrets sealed by `KeystoreSecretBox`; excluded from backups.
  Last.fm needs repo secrets NAGMEDAR_LASTFM_API_KEY / NAGMEDAR_LASTFM_SECRET (empty -> Last.fm disabled in UI).
- `NagTextField` (components/TextFields.kt) is the shared settings text field (`secret`, `lines`).
- LRC editor: `ui/lyrics/editor/`. `StampState` is a pure state machine; `LyricsWriter` keeps replaced lyrics once as
  LyricsSource.ORIGINAL_FILE / ORIGINAL_TAG rows (kept by `LyricsDao.kept()`, i.e. survive cache clears, in backup).
  Native `nag_tags_write_lyrics` (tags.cpp) + `TagLyrics.write` write LYRICS / USLT+SYLT; `nagaudio setlyrics` CLI.
- Shortcuts got `ShortcutScope.Editor` (overlaps only itself; `ShortcutRegistry.clash`, scope-aware `reservedReason`);
  editor keys handled by the editor's own `onPreviewKeyEvent`, not WindowKeys. Menu bar skips Editor scope.
- LRCLIB publish: `ProofOfWork.solve` (multi-worker, cancellable), `OkHttpLyricsPublisher`, UA from `Scrobbling.userAgent()`.
- Next on this track: a "Restore original lyrics" entry outside the editor; ListenBrainz "love"/Last.fm loved tracks;
  write .lrc via a picked folder for content:// tracks outside picked trees.
- Robustness (PR #23): tag writes go through `TagBackups.guarded` (copy, verify by TagLib audio, put back) and `TagLyrics.write` returns `TagWrite`; any new in-place tag writer should use `FileTags.write` too (ReplayGain's `writeReplayGain` still writes in place unguarded).

## music library toolbar (PR #27, branch story/music-library)
- `ui/music/MusicToolbar.kt`: the one toolbar row (`MusicToolbar`: SegmentedChoice from 1000 dp else MenuButton for the view; Play/Shuffle/Columns/Filter at the right, icon-only below 520 dp) and the filter popover. A new filter = one `FilterGroup` entry in `musicFilterGroups(state)` (+ the field in `TechFilter`/saver in `MusicBrowserState`). Filter button dot = `state.filter.active`.
- `MusicBrowser` computes `shown` (open group's tracks in table order, `effectiveSort`) and hands it to the toolbar and `TrackPane`; the pane header (back, cover, title, Convert) only shows outside the Tracks view. `NagIcons.Filter` added via gen_icons.py. No music shortcuts existed for these controls.

## music player (PR #39, branch story/music-player): NOTES FOR THE NEXT STORY
- One progress component for every player: `ui/playback/ProgressTimeline.kt` — `ProgressTimeline(progress, lengthMs, onSeek, playing, rate, waveform, marks, style = Panel|Phone|Bar)` (soft waveform from `softLevels`, tnum times, bubble, seek on release, haptic tick at marks, Left/Right 5 s), `ProgressHairline` (mini player), `SmoothProgress` (extrapolates between readings, holds a seek target 1.2 s), `TransportControls`/`TransportButton`/`PlayPauseCircle` (`TransportSize.Bar|Phone`). `WaveformTimeline` and the bars' `ProgressLine`/`PlayPauseButton` are gone; `NagSlider` stays for volume/equaliser.
- Position: `PlaybackService.preciseTime()` (= `PlaylistManager.currentTime()`: libVLC's `mediaplayer.time`, as PlayerController.progress moves by >950 ms steps). `rememberPlaybackProgress` polls 500 ms playing / 1 s paused; `playbackRate()` for the timelines.
- Play state: `PlaylistManager.enginePlaying` (StateFlow, set in mediaplayerEventListener on Playing/Paused/Stopped, reset in stop()/onDestroy). `playingState` is VLC's "has media" and stays as it was (RemoteAccessServer, LibVlcRestart use it). `rememberNowPlaying().playing` reads enginePlaying.
- Previous: `app.nagmedar.playback.PreviousRule` (tools, WINDOW_MS 3000) used by `PlaylistManager.previous`; `NagmedarSessionPlayer.handleSeek` sends every SEEK_TO_PREVIOUS* to `source.previous()`.
- Links: `ui/music/MusicLinks.kt` (`MusicLink(view, keys)`, `artistOf/albumOf(MediaWrapper)`, `pending`/`open`/`taken`); keys from `MusicLibrary.artistKey/albumKey` (same as `groups()`); `MusicBrowser` takes the link (off-main lookup, `MusicBrowserState.show`, `reveal` scrolls GroupList); NagmedarApp navigates to Music and closes the phone player. `components/LinkText.kt`: `LinkText`, `ArtistAlbumLine`. `PlayerBarActions.openArtist/openAlbum`, `PlayerBarInfo.album/linked/repeatOne/rate`.
- Volume: `NAGMEDAR_VOLUME` (tools NagmedarKeys); `PlaybackService.setUserVolume` (debounced 400 ms save) from the bars' slider (`PlaybackActions.setVolume`), NagmedarApp's VolumeUp/Down/Mute; applied in `PlaylistManager.engines` lazy init to both engines. Video player's own volume keys/gestures still use the system volume, not persisted.
