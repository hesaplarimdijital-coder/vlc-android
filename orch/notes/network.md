# network track — notes after S-1301 + S-206 (PR #8, branch story/S-1301)

- Package `app.nagmedar.ui.network`: Addresses.kt (NetworkProtocol, ServerAddress url/parse/errors, isUnder, signInOptions), Credentials.kt (Credentials w/o password in toString, AesGcmCipher, KeystoreCipher alias `nagmedar_network_credentials`, CredentialSeal with url as AAD), NetworkSource.kt (interface; DiscoveredServer, NetworkEntry, EntryKind, BrowseResult), LibVlcNetwork.kt (LibVlcNetworkSource: MediaBrowser discovery + own IMedia browse; NetworkSignIns answers libVLC LoginDialogs), ServerStore.kt (DatabaseServerStore, SessionSignIns, NetworkSignInObserver), NetworkState.kt (FolderView reducer, Discovery, LanState), NetworkModel.kt (state + actions), NetworkPlayback.kt (ServiceNetworkActions, RemoteRendererHook/RemoteRenderers for S-1302), NetworkScreen.kt, ServerDialog.kt.
- libVLC 4 eap29 parse status: the JNI passes the native enum (done = 6); IMedia.ParsedStatus constants are libVLC 3's — do not use them.
- Hooks: vlc-android `org.videolan.vlc.util.NetworkLoginHook` (DialogDelegate.onDisplay(LoginDialog) asks it first); resources `org.videolan.resources.NetworkMediaHook` (PlaylistManager adds its options); PlaybackObserver list has `app.nagmedar.ui.network.NetworkSignInObserver` (+ proguard keep).
- Room: schema 8 `network_server` (id, name, url UNIQUE, credentials BLOB sealed, created_at, last_used_at).
- Shortcuts: ShortcutId.FolderUp (Backspace; mpv preset Alt+↑), ShortcutId.Refresh (F5) → ScreenCommands.up()/refresh() via BrowserCommands().
- S-1302: implement RemoteRendererHook.renderers (menu "Play on …" appears automatically); ServiceNetworkActions.play goes through the playback service (its renderer if one is set).
- Tests' fakes: FakeNetworkSource / FakeServerStore / RecordingActions (NetworkModelTest.kt), SampleNetworkBrowser (NetworkSamples.kt).
- Not done: libVLC QuestionDialog (e.g. davs self-signed certificate) still goes to the classic delegate, which NagmedarActivity does not show.

## after review fixes (PR #8, HEAD cd6052a4)
- Schema is now 9 (MIGRATION_8_9 network_server); radio took 8.
- libVLC requests: vlc-android `LibVlcDialogHook` (display/canceled/progress; DialogDelegate asks it first for Login/Question/Progress). `NetworkDialogs` (installed in AppSetupDelegate, also by browse/observer/host) wraps Dialog → `LibVlcPrompt`; `DialogRouter` (Prompts.kt, pure, tested) routes: browse claims by host (WebDAV realm-only → the single WebDAV claim), play claims (NetworkSignInObserver, 60 s), saved-server by host in text (library scans, once a minute), questions to the folder page (`FolderStatus.Question`), the rest to `LibVlcDialogHost` (Compose, NagmedarApp, while started) else classic.
- LibVlcNetworkSource: everything on the "nagmedar-network" HandlerThread, Handler per flow, cleanup in finally, parseAsync 30 s limit; `ReadingEnd.PARSED_DONE = 6`.
- Credentials: `UnreadableCredentialsException.lost` (false = Keystore didn't answer → StoredSignIn.Unavailable, kept); save returns `SaveResult` (`Saved(id, signInKept)` / `AddressTaken`).

## after verification fixes (PR #8, HEAD 388c6e00)
- Schema now 10 (MIGRATION_9_10 network_server; S-613 took 9).
- Hosts are matched whole with `hostNamed(words, host)` (Addresses.kt); play claims expire in every lookup; the library-scan auto-answer is used once per host until `DialogRouter.renewed(host)` (called by DatabaseServerStore.save on a new sign-in).
- `NetworkDialogs.queue` is process-wide; `windowStarted/windowStopped` from LibVlcDialogHost; after 10 s with no window, queued requests go back to VLC's DialogDelegate (handBack).
- Each browse/discovery flow has its own HandlerThread (quit after release).

## after S-1302 (PR #14, branch story/S-1302): casting to Chromecast / DLNA
- Discovery stays VLC's `RendererDelegate` (vlc-android): on demand only — `want(owner, Boolean)`, `wantWhileStarted(lifecycleOwner)` (classic ContentActivity, RenderersDialog), `CASTING` owner held by `PlaybackService.setRenderer` while a renderer is set; `discovering: StateFlow`, `restart()` ("Search again"). Dedupe by display name + type: `app.nagmedar.playback.RendererRoster` (tools). Casting ends only on a real network loss (connected → disconnected), not on NetworkMonitor's first placeholder.
- libVLC 4 renderer types: "chromecast" (microdns_renderer), "stream_out_dlna" (upnp_renderer; the upnp module also has the dlna sout) → `RendererKind`. `RendererItem.flags` (audio/video capability) is package-private in libvlcjni: not read.
- Pure rules in tools `app/nagmedar/playback/Casting.kt`: RendererKind/Key/Roster, `CastItem`/`CastSupport` (tracker → LocalOnly, DSD → AsPcm), `CastHandover` + `HandoverHost` (PlaylistManager's `castHost`; replay via `castResume` in playIndex: exact start, paused kept, CUE offset). Tracker modules are skipped in playIndex while casting (toast `nagmedar_cast_tracker_skipped`). `castItemOf(uri)` in vlc-android `media/CastItems.kt`; `MusicFormats.isDsd`.
- `:no-sout-chromecast-video` now only for non-video items (upstream added it to every media → films were cast as audio only).
- `PlayerController.setRenderer` re-applies the audio delay (`AudioDelayMix.rendererChanged`).
- UI `app.nagmedar.ui.cast`: `Casting` (object: picker request state, cast/stop/play(payload), `RemoteRenderers` impl, `install()` from AppSetupDelegate sets RemoteRendererHook + `CastPickerHook` (vlc-android util, classic video overlay button)), `CastPicker.kt` (`CastPickerDialog`, `CastPickerSurface`, `showCastPicker(activity)` for non-Compose activities), `CastModel.kt` (`CastDevice`, `CastPickerView`, `CastPicker.view/notices/offered`), `CastingViews.kt` (`CastButton`, `CastingStrip`, `CastingScreen`), `CastingPlayerChrome` (PlayerChrome found by name via `PlayerChrome.createCasting()` when `displayManager.isOnRenderer`; proguard keep).
- `RemoteRenderers` gained `choose(entries, index)` and `offered(entry)`; network file menu: one item per device found + "Play on…" (picker). Library `mediaMenu` has "Play on…".
- Shortcut `ShortcutId.Cast` (Ctrl+Shift+C, Playback group, global); AppActions and both player chromes open the picker.
- PlayerBarInfo/NowPlayingPanel/PhonePlayer take `canCast` + `casting`; PlayerChromeState `canCast`, PlayerChromeActions `cast()`.
- Not done: volume on renderers (libVLC's renderers take none; UI says so); reopening the video player when casting a video stops from the music side (upstream's dead check in PlaybackService.setRenderer left as is).

# NOTES FOR THE NEXT STORY — after S-1304 (PR #13, branch story/S-1304)
- Remote access web app: VideoLAN's `remoteaccess`, pinned in buildsystem/compile-remoteaccess.sh
  (`: ${REMOTE_ACCESS_TESTED_HASH:=bb1f07bb…}`). Nağmedar's changes = buildsystem/remoteaccess/patches/*.patch
  (git apply, regenerate with `git diff` in a clean clone) + assets/ copied over (colors.scss from gen_brand.py).
  check-dist.sh guards the brand. Read upstream: dispatch vendor-remoteaccess.yml → branch vendor/remoteaccess.
- CI: composite action .github/actions/remoteaccess-web (Node 20, cache by hash) in the app job and the
  signed release job; debug leg walks the page with buildsystem/remoteaccess/preview.py (mock server, Playwright)
  → ci/web-pr-<n>; non-gating.
- Every non-bundle variant uses :application:remote-access-client (no Maven AAR); release refuses a missing dist.
- Server texts for the page: TranslationMapping.StringMapping (APP_NAME replaced VLC_VERSION; ABOUT_*, SOURCE_*, LANG).
- New UI: app.nagmedar.ui.remoteaccess (RemoteAccessSettings over the classic prefs + RemoteAccessControl;
  RemoteAccessSection in SettingsScreen after Covers). Enabling resets KEY_REMOTE_ACCESS_LAST_STATE_STOPPED.
- PR with a merge conflict gets no pull_request CI run at all (mergeable_state "dirty"): rebase.

# NOTES FOR THE NEXT STORY — after story/fix-player (player, casting and dialogs: remaining work)
- Casting end (S-1302): `PlaylistManager.setRenderer` → `filmBack()` when casting a film ends and `VideoPlayerActivity.shown` is false (companion flag, onStart/onStop): pauses libVLC first, `CastResume(film = true)` (exact position, paused = was paused or app in background), FORCE_AUDIO removed; in front `switchToVideo()` (which lets a castFilm through even before libVLC reports video tracks), in the background `videoBackground = true` so libVLC's ESAdded does not open the player; the notification/mini bar open it later and `playIndex` takes the CastResume. The player on screen still relies on DisplayManager's recreate. `PlaylistManager.castEndsForNewPlayback()` (Casting.play with device null) skips it. Upstream's dead check in PlaybackService.setRenderer removed.
- libVLC questions: `PlayerAppWindowsHost` (player/PlayerAppWindows.kt) composes `LibVlcDialogHost()` in both player chromes, so the prompt queue shows in the video player window too (on DeX both windows show the same prompt; answering one closes the other). `LibVlcWords.localized(res, text)` translates gnutls' certificate question (title, buttons, both texts; anything else raw). `PromptPanel(focusFirst = true)` in windows: focus on cancel.
- Player app shortcuts: `PlayerAppWindows` (Guide/ShortcutCard/Palette/SleepTimer over the film; Settings opens NagmedarActivity like TranscodeRequests). `offered(id)` = Player scope minus Cue* + `PLAYER_GLOBALS` + `APP`. PlayerKeys now matches Player scope first, then Global.
- S-613: `AudioDelayMix.legacyBluetoothUs` (classic KEY_BLUETOOTH_DELAY as output share, only when outputLatencyUs == 0; reset in mediaStarted, given again by VideoPlayerActivity.setPlaybackParameters on every onPlaying); `PlayerController.legacyBluetoothDelayUs`. `LatencyMeasureSession(onEnded)`; `LatencyMeasuring.resumePlayback()` resumes on end even with the screen closed.
