
## video-library (phone video 23 Oct, B1–B4, B11) — notes for the next story
- `Medialibrary.getRecentAudio()` is broken in the JNI (C takes 2 jboolean args, sig "()"): never call it. Other mismatches found by a script: nativeGetPlaylistSearchCount, nativeGetPlaylistsCount, nativeGetSearchFoldersCount, nativeSubfoldersCount (arg count differs) — check before using. Debug/release use the prebuilt medialibrary-all AAR (only the `dev` build type compiles medialibrary/jni), so fix such things on the Java/Kotlin side.
- Home "Son çalınan müzik" = `MediaRepository.recentAudio()`: history(HISTORY_TYPE_LOCAL) ∩ Music-section audio ids, ≤ 20.
- Scan strip: `MediaParsingService` has an `Idle` notification (clears `progress` only); `rememberScanState` drops a progress unchanged 30 s while `!isWorking` (`ScanState.over`, `ScanWatch`). The strip is only drawn by `LibraryContent` (library pages). Cold start = reload (`KEY_MEDIALIBRARY_AUTO_RESCAN`), and a reload also fires onDiscoveryStarted/Progress, hence "aranıyor".
- Track years: `releaseYearOf()` in music/MusicLibrary.kt (epoch seconds → year).
- `components/FilterChips.kt` `fadingEdges(scroll, length, color)` is internal now; RadioTabs uses it + BringIntoViewRequester.
- The "small ring" in phone recordings is the recorder's touch indicator, not a progress ring.
