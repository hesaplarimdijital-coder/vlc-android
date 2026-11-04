# Classic VLC UI still reachable from Nağmedar (read-only audit, 22 Oct 2026)

Line numbers are of master 242742cd (drift ±10 lines). File paths relative to the repository.

**Entry.** StartActivity (Theme.VLC.Transparent.NoUI) opens NagmedarActivity while NAGMEDAR_LAUNCH_NEW_UI is true (default); classic only via action nagmedar.shortcut.classic, the pref off, or TV (always classic leanback): application/vlc-android/src/org/videolan/vlc/StartActivity.kt:273,308-315,346-375,411. Beta builds first show the VLC BetaWelcomeActivity (:133-136).

## 1. Classic screens started from nagmedar-ui
Hand-offs are in application/nagmedar-ui/src/main/java/app/nagmedar/ui/NagmedarApp.kt unless noted.

1. **Add folder** (Home/Movies/Series/Music, once media access exists): NagmedarApp.kt:261 calls `openClassic` (:467-471), opening SecondaryActivity STORAGE_BROWSER (Theme.VLC). Replace with a Compose library-folders screen (overlaps the "Library folders and Videos page" agent).
2. **Streams** placeholder (comingIn "MVP4", ui/shell/Destinations.kt:35) and Ctrl+N (NagmedarApp.kt:593): its button (ui/screens/Common.kt:88,115) opens SecondaryActivity STREAMS (MRLPanelFragment). Replace with a Compose open-stream panel beside `NetworkScreen` (NagmedarApp.kt:386).
3. **Device** placeholder (Destinations.kt:33): NagmedarApp.kt:419 starts FILE_BROWSER without KEY_MEDIA, so application/vlc-android/src/org/videolan/vlc/gui/SecondaryActivity.kt:124-127,222-226 finishes at once: a dead button. Build a Compose device browser.
4. **Downloads** (Destinations.kt:38; the only `else`, NagmedarApp.kt:420): opens the entire classic MainActivity. Build a Compose screen or drop the destination.
5. **Menu Tools "Klasik arayüz"** (NagmedarApp.kt:480) and **Settings launcher toggle** (ui/screens/SettingsScreen.kt:151-153): the full classic app. Remove both, `launchNewUi`, `NAGMEDAR_ACTION_CLASSIC`, `openClassic`; make StartActivity.kt:310 unconditional.
6. **"Tüm ayarlar (klasik arayüz)"** (NagmedarApp.kt:391; SettingsScreen.kt:171) opens PreferencesActivity: application/vlc-android/res/xml/preferences{,_ui,_video,_video_controls,_subtitles,_audio,_audio_controls,_casting,_adv,_android_auto,_widgets,_remote_access,_parental_control,_optional,_extension_page,_dev}.xml. Write one generic preference-XML-to-Compose renderer; drop dev/optional/extension pages.
7. **Music "Media info"**: mediaMenu falls back to InfoActivity (ui/library/LibraryScreens.kt:118,134) because ui/music/MusicScreen.kt:370 passes no `info`. Add a Compose info sheet.
8. **Non-video now-playing**: ui/playback/Playback.kt:128-138 and ui/playback/PlayerBars.kt:196-199 keep only TYPE_AUDIO in Nağmedar; radio sets TYPE_STREAM (ui/radio/RadioPlayer.kt:31), so every station opens the classic app. Route all non-video types to Nağmedar's player.
9. **Remote access** "Adresler…", "Nasıl çalışır…" and first enable (ui/remoteaccess/RemoteAccessSettings.kt:112-120,155-157,174) open RemoteAccessShareActivity (Theme.VLC) and RemoteAccessOnboardingActivity (Theme.VLC.Onboarding). Replace with Compose dialogs.
10. **Permissions**: ui/onboarding/MediaAccess.kt:37,56 and ui/player/snapshot/ActivitySnapshots.kt:64 call classic `Permissions` helpers. Use system prompts plus a Compose rationale. (ui/onboarding/FirstRunHost.kt:81 already skips VLC onboarding.)

## 2. Video player
Every video opens classic VideoPlayerActivity (Theme.VLC.Player, application/vlc-android/AndroidManifest.xml:1027-1034) from ui/playback/Playback.kt:91 and from the service (application/vlc-android/src/org/videolan/vlc/media/PlaylistManager.kt:718,829,873).

- **Chrome gate.** application/vlc-android/src/org/videolan/vlc/gui/video/VideoPlayerActivity.kt:614 attaches Compose DesktopPlayerChrome (:617 CastingPlayerChrome). ui/player/DesktopPlayerChrome.kt:285,387-398 activates it only with a mouse; any finger touch calls `onFinger()` (ui/player/ChromeVisibility.kt:66) and the classic overlay returns. So every touch user sees VLC layouts application/vlc-android/res/layout/{player,player_hud,player_hud_right,player_overlay_seek,player_overlay_info,player_overlay_brightness,player_overlay_volume,player_overlay_tracks,player_overlay_settings,player_options,player_resize,player_orientation,player_tips,swipe_to_unlock,player_remote_control}.xml (driven by application/vlc-android/src/org/videolan/vlc/gui/video/VideoPlayerOverlayDelegate.kt:435,559,1015), plus VideoTouchDelegate gestures (VideoPlayerActivity.kt:721-728,1938).
- **Panels** (VLC bottom sheets and overlays) via PlayerOptionsDelegate (VideoPlayerActivity.kt:1525; application/vlc-android/src/org/videolan/vlc/gui/helpers/PlayerOptionsDelegate.kt:108-129,274,301,320-340): sleep, speed, jump-to-time, chapters, equalizer, video/audio-controls settings, stats, tips, A-B repeat, save-playlist, share, record (layouts application/vlc-android/res/layout/{dialog_playback_speed,dialog_time_picker,dialog_select_chapter,dialog_equalizer,dialog_video_controls_settings,dialog_video_resume,bookmarks}.xml). Also tracks/subtitles (VideoPlayerOverlayDelegate.kt:263; VideoPlayerActivity.kt:2030), delays (application/vlc-android/src/org/videolan/vlc/gui/video/VideoDelayDelegate.kt:238,247), lock (VideoPlayerOverlayDelegate.kt:1056), bookmarks (:1128), PiP, orientation, audio-only, resume dialog (VideoPlayerActivity.kt:850,2555,1889,2515), and cast fallback RenderersDialog (:2131; Compose normally wins through `CastPickerHook`, ui/cast/Casting.kt:60).
- **Mouse mode still leaks**: equalizer (DesktopPlayerChrome.kt:1135 reaches VideoPlayerActivity.kt:1462, the classic EqualizerFragmentDialog), jump-to-time, subtitle picker and classic OSD text (ui/player/PlayerKeys.kt:99,117,88,132,148); ui/cast/CastingPlayerChrome.kt:60 falls back to classic. PlayerWindow (fullscreen) and PlayerMouse are behaviour only, no VLC visuals.
- **Classic audio player**: AudioPlayerContainerActivity hosts AudioPlayer (audio_player*.xml; application/vlc-android/src/org/videolan/vlc/gui/AudioPlayerContainerActivity.kt:128); no separate activity, reachable only inside classic screens.
- **Fix**: make DesktopPlayerChrome the only chrome: remove the mouse and `onFinger` gates (DesktopPlayerChrome.kt:285,398); add touch gestures plus Compose tracks, delay, speed, bookmarks, chapters, lock, orientation, PiP, resume and audio-only panels; reuse SleepTimerDialog (ui/player/PlayerAppWindows.kt:179) and EqualizerScreen; then delete the overlay delegates and layouts.

## 3. Surfaces outside activities
- **Playback notification**: application/vlc-android/src/org/videolan/vlc/gui/helpers/NotificationHelper.kt:74-154 (VLC ic_notif_*/ic_widget_* icons, MediaStyle), posted at application/vlc-android/src/org/videolan/vlc/PlaybackService.kt:982,1105; tap opens VideoPlayerActivity for video, StartActivity (so Nağmedar) for audio (:406-421). Swap icons for application/resources/src/main/res/drawable/ic_nagmedar_mark_mono.xml.
- **Scan and remote-access notifications** (NotificationHelper.kt:168-176,192+): scan tap sends a data-less VIEW to StartActivity, handled as external playback (nothing useful opens); remote-access tap opens the classic share activity (StartActivity.kt:271). Retarget via NAGMEDAR_EXTRA_DESTINATION.
- **Nağmedar's own notifications borrow VLC glyphs** (ic_notif_scan/audio/video, ic_widget_close_w): ui/{convert/ConvertWork.kt:267-311,podcast/PodcastNotifier.kt:59-158,transcode/TranscodeQueue.kt:176-192,transcode/HlsStream.kt:211-216,player/subtitles/whisper/WhisperWorker.kt:249-288,player/subtitles/translate/TranslateWork.kt:149-286,player/record/RecordingFiles.kt:400,radio/record/RecordingNotifications.kt:102,132}. Use the Nağmedar mono mark.
- **Widgets**: VLCAppWidgetProviderWhite/Black, MiniPlayerAppWidgetProvider (manifest :1074-1120, config theme WidgetConfiguration :1052), WidgetExplanationDialog, WidgetMigrationDialog. One Nağmedar widget or remove.
- **Android Auto**: application/vlc-android/src/org/videolan/vlc/media/MediaSessionBrowser.kt:167-299 uses VLC ic_auto_* icons; the host renders the tree, so swap icons only.
- **Shortcuts, labels**: application/vlc-android/res/xml/shortcuts.xml (nagmedar_classic :6-15; last_playlist, browser) map to classic nav ids (StartActivity.kt:111-114): retarget to NAGMEDAR_EXTRA_DESTINATION. Hard-coded "Play with VLC" (manifest :940) shows in the chooser: rename.
- **Search**: ACTION_SEARCH opens classic SearchActivity (StartActivity.kt:229-233); route to Nağmedar search.
- **Snackbars, progress, toasts**: application/vlc-android/src/org/videolan/vlc/media/MediaUtils.kt:147-177 shows a Material Snackbar and :418-447 a ProgressDialog, called from LibraryScreens.kt:122-123 and MusicScreen.kt:367 in Theme.Nagmedar (not Material Components: VLC-styled or crashes; verify on device). nagmedar-ui never calls UiTools or AlertDialog. Re-implement on PlaybackService; add a Compose toast host for ~30 system Toasts (NagmedarApp.kt:544,553,660,719,734).
- **Resume prompts**: video dialog (above); with audio resume set to Ask, PlaylistManager.kt:742 posts waitForConfirmationAudio and returns, but only AudioPlayerContainerActivity.kt:508-516 observes it: from Nağmedar nothing answers, so playback likely hangs. Observe both in NagmedarActivity with a Compose prompt.
- **libVLC login/question/progress**: ui/network/NetworkDialogs.kt:61,94-99 hands them to VLC's DialogDelegate/DialogActivity 10 s after the last Nağmedar window stops; application/vlc-android/src/org/videolan/vlc/StoragesMonitor.kt:47 shows the external-device DialogActivity. Keep a Compose host or notification prompt.
- **Classic-only extras** (application/vlc-android/src/org/videolan/vlc/gui/MainActivity.kt:129,151 WhatsNew/Update; About/licences; playlist, sort, bookmark, equalizer dialogs): reachable only via classic screens, removed with them. Nağmedar has AboutScreen, PlaylistDialogs, EqualizerScreen.

## 4. Theming leaks
- Application theme Theme.VLC.Transparent (application/app/src/main/AndroidManifest.xml:19; application/vlc-android/res/values/styles.xml:476, Material Components) covers every window Nağmedar doesn't override: VideoPlayerActivity, classic activities (manifest :972-1025: Theme.VLC, .Onboarding, .NoStatus, .PickerDialog, .SendCrashDialog), BenchActivity (:1137), StartActivity's transparent trampoline (:100).
- Theme.Nagmedar (ui/res/values/themes.xml:5) extends platform Material, not AppCompat/Material Components: Snackbars, ProgressDialogs and DialogFragments mis-style there.
- Compose code is clean: IBM Plex (ui/theme/Typography.kt:14-21), no colorResource, AndroidView or XML layouts; launcher icon and app_name already Nağmedar. Remaining VLC visuals: notification glyphs, ic_auto_*, widget layouts, every classic layout above.

## Defects found
Device button dead (1.3); audio resume prompt unanswered (3); Snackbar on non-Material theme (3); scan-notification tap does nothing (3); radio stations' now-playing opens the classic app (1.8).

## Suggested order
1) Compose touch player chrome (largest). 2) Remove classic entry points; retarget StartActivity, shortcuts, notifications. 3) Generic preference renderer. 4) Resume prompts, snackbars, libVLC dialogs. 5) Icon swaps.

## Packages (orchestrator)
- classic-player: touch chrome + Compose panels (opus).
- classic-entries: entry points, routing, Streams/Device/Downloads screens, info sheet, remote-access pages, permissions, shortcuts, labels, search (opus).
- classic-prefs: generic preference renderer replacing PreferencesActivity (opus).
- classic-surfaces: resume prompts, snackbars/toasts host, libVLC dialogs after window stop, external-device prompt, notification icons, widgets, Auto icons, theme parent (sonnet/opus).

## classic-surfaces (PR #37, story/classic-surfaces) — notes for the next story
- Notices bus: org.videolan.tools.Notices (show(context, text|@StringRes, long, error, actionLabel, action), progress(text)/dismiss(id)); host = components/NoticeHost (shell overlay over UndoBarHost, PlayerAppWindowsHost over the film). No host up → system Toast; progress shows nowhere. Use it instead of Toast/Snackbar everywhere.
- ResumePrompts/ResumePromptHost (ui/playback/ResumePrompt.kt): waitForConfirmationAudio; podcasts auto-restart (tracker places them). Video resume stays the player chrome's (askResume).
- NetworkDialogs: router.host always the queue (+ PromptNotification when no window; 10 s after last window); VLC's DialogDelegate no longer used from there.
- NewStorageHook (vlc-android) → ui/library/NewDrives; ShellRequests.librarySources opens Settings › Library (NewDrives.EXTRA_LIBRARY_SOURCES).
- Icons: resources/drawable nagmedar_notif_{previous,next,stop,close,bookmark,fullscreen} + S-609 nagmedar_pip_*; small icon ic_nagmedar_mark_mono; ic_auto_* redrawn in place (generator: agents/story-classic-surfaces/tools/genauto.py).
- Widget: ui/widget/PlayerWidget.kt (PlayerWidget provider, PlayerWidgets renderer, PlayerWidgetObserver PlaybackObserver, PlayerWidgetMigration); VLC widget code, WidgetRepository, PreferencesWidgets + preferences_widgets.xml deleted (WidgetDao/Widget model in mediadb kept).
- Theme.Nagmedar = MaterialComponents.DayNight.NoActionBar; app theme = Theme.Nagmedar; Theme.VLC.Player accent = nagmedar_accent_dark.
