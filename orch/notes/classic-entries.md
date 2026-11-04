# classic-entries (PR #32, story/classic-entries)
- New pages: streams/StreamsScreen.kt (StreamsCenter.requestFocus for Ctrl+N; history = medialibrary HISTORY_TYPE_NETWORK), device/DeviceScreen.kt + DeviceModel.kt + DeviceSource.kt (adds folders to sections via LibraryScopes + SectionSourcesDialog), downloads/DownloadsScreen.kt (PodcastDao.observeDownloads, WorkManager model work, WhisperModels).
- library/MediaInfoSheet.kt: MediaInfoRequests.open(media) is mediaMenu's default info; MediaInfoHost in the shell overlay.
- PlaybackActions.nowPlayingDestination()/playsVideo(); "now-playing" destination (NAGMEDAR_DESTINATION_NOW_PLAYING) handled in NagmedarMain; notification tap = StartActivity action nagmedar.now_playing.
- remoteaccess/RemoteAccessPages.kt: RemoteAccessPages.open(Addresses|HowItWorks), RemoteAccessHost (also the OTP code). Server state through RemoteAccessUtils.statusFlow/addressesFlow/connectionsFlow (set by RemoteAccessService).
- StartActivity: startApplication gone, always startNagmedar(…, extras); NAGMEDAR_EXTRA_SEARCH -> ShellRequests.search; NAGMEDAR_EXTRA_REMOTE_ACCESS; NAGMEDAR_ACTION_LIBRARY (scan notification -> home).
- Left for others: classic PreferencesActivity entry (prefs agent), classic "nagmedar_shell" pref in preferences.xml, StoragePermissionsDelegate in StartActivity.startPlaybackFromApp (external file:// opens).
