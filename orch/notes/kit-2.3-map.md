# Desktop Kit 1.0.0 → 2.3.0 mapped onto Nağmedar 1.0.2 (5 Nov, read-only mapping agent; checked against the code)

Prefixes: kit = `$KIT/kit/src/main/java/desktopkit/`, app = `application/nagmedar-ui/src/main/java/app/nagmedar/ui/`,
repo = the vlc-android clone. Lines at master 0ed1076d and kit bef6b9f.

Main findings
- Most of 2.0's new surface is already in the app in its own form (palette, multi-window, caption-bar menu bar, notices
  and undo bars, sheet drag, file drag and drop, skeletons, wallpaper accent): keep those. The real work: 2.3.0's phone
  motion; 2.0's three-button-navigation band; a handful of 2.1/2.2 fixes; the menu and hover fixes the kit merged after
  8ca85ad (8c23f70 and 20da08e, the "0.2.1"/"0.2.2" ports, are not ancestors of 8ca85ad: the app never got them).
- The platform bump forces minSdk ≥ 24 (navigation 2.10.0-rc01 moved to API 24 with the rest of AndroidX).
- navigation 2.10 plays the predictive Back with NavHost's own scale-to-70 % unless `predictivePop*` is passed: part 1
  must pass the app's page pops or the phone's slide-back regresses.
- Bugs the kit fixed that the app still has: the player sheet's spring tail covers the bottom bar (#35); the bars jump back
  when the player expands (#48); a context menu jumps to the anchor's corner while it fades out (`offset ?: IntOffset.Zero`, #64).

## 1. Mapping (P = part: 1 platform, 2 phone motion + shell, 3 components + keyboard + selection, 4 tablet + DeX)

| # | Kit change | Kit file(s) | App counterpart | Status | Port / keep | P |
|---|---|---|---|---|---|---|
| | **2.0.0** | | | | | |
| 1 | Platform: AGP 9.4.1, Gradle 9.8.2, Kotlin 2.4.20, BOM 2026.09.00, activity 1.13, nav 2.10.2, lifecycle 2.11, coroutines 1.11 | build files (kit 7845eb5) | repo build.gradle, settings.gradle, gradle.properties, nagmedar-ui/build.gradle | missing | §3 | 1 |
| 2 | Haze 2.0.1 (`hazeBlur`, one `HazeBlurStyle` per material; haze-materials dropped) | theme/Glass.kt | theme/Glass.kt:218-231; nagmedar-ui/build.gradle:71-72 | missing | copy the kit's `blurStyle()`; drop haze-materials, add haze-blur (needed even under Solid: Haze stays a dependency) | 1 |
| 3 | Pre-Android-14 branches removed | ExpandablePlayer.kt:853, Motion.kt:172, Shortcuts.kt | ExpandablePlayer.kt:560-561, input/Shortcuts.kt:340, Theme.kt:303, Glass.kt:139 | partial | only with minSdk 34 (§4) | 1 |
| 4 | Robolectric 4.17 on API 36 (Java 21); predictive-Back and rotation tests | kit tests | robolectric.properties `sdk=34` (ui, data, session, audio); CI Java 17 | missing | §3; sdk 34 can stay | 1 |
| 5 | The shell owns the bottom system bar: bars continue behind the 3 buttons; `SystemBand` (rule when bars slide away, chrome colour on tablets, none with the keyboard); `RootInsets`/`RootPadding`; `systemBottomPx`; `bottomInsetPadding` | DesktopKitRoot.kt, ShellLayout.kt, ShellPadding.kt, DesktopShell.kt | NagmedarApp.kt:219 (root pads all of safeDrawing); ShellLayout.kt (no band; `slideDown` clip :230); ShellPadding.kt:63-67 | missing | port; band solid `colors.chrome` under Solid; system bottom into `bottomPadPx(bars)` incl. full pages; first run (outside the shell) keeps root padding. Audit insets that start applying: music/PhonePlayer.kt:282, components/Sheets.kt:624, lyrics/LyricsView.kt:385, lyrics/editor/LyricsEditor.kt:152, visual/VisualizerPanes.kt:204, cast/CastPicker.kt:211, QueuePage, WindowPage | 2 |
| 6 | Bar icons follow the theme; no system scrim over the chrome | DesktopKitRoot.kt `SystemBarsAppearance` | theme/SystemBars.kt `SystemBarsFollowTheme` (sets LIGHT/DARK scrims :26-28) | partial | drop scrim + contrast enforcement with #5 | 2 |
| 7 | Sheets, expanded player, toasts stay above the buttons | Sheets.kt, ExpandablePlayer.kt, DesktopShell.kt | root padding does it today | n/a until #5 | `bottomInsetPadding` in SheetSurface and PlayerSheet | 2 |
| 8 | Forms K1 (label, unit, clear, password, error, supporting text; search Esc clears; Number fields; tri-state; Switch/CheckboxRow; IME action) | components/Forms.kt | TextFields.kt `NagTextField`, InlineFields.kt `NagSearchField`, Choice.kt:264/:310, PanelParts.kt:208; ad-hoc number fields network/ServerDialog.kt:300, sleep/AlarmSettings.kt:227 | partial | extend NagTextField (clear, error, supporting text); no kit copies | 3 |
| 9 | Feedback K2: progress, StatusBanner, CountBadge | Feedback.kt | States.kt:268 `NagProgressBar`, :471 `AlertBanner`, :136 `ToneBadge` | present | keep | – |
| 10 | Toasts: ToastState, newest replaces, swipe at finger speed (2.3), TalkBack timeout | Feedback.kt | tools/…/Notices.kt + NoticeHost.kt; States.kt:559 `UndoBarState` | partial | keep stacking; add swipe-to-dismiss + recommended timeout to NoticeBar and UndoBar | 3 |
| 11 | Structure K3 (card, KeyValueRow, list row, SelectionBar, menu buttons, tabs) | Structure.kt | Cards.kt `NagCard`, Selection.kt:385, FilterChips.kt:173/:219, per-screen tabs | present/n/a | keep | – |
| 12 | appResources/pluralString | Resources.kt | – (no in-app language switch) | n/a | – | – |
| 13 | DeskButton trailing icon/role; disabled 50 % whole | Basics.kt | Basics.kt:127 `NagButton` | present | – | – |
| 14 | Command palette K4 | CommandPalette.kt | search/CommandPalette.kt, search/PaletteResults.kt (NagDialog) | present | keep; optional Ctrl+Enter new window | 4 |
| 15 | Cover grid K5 | Grid.kt | podcast grid PodcastScreen.kt:563, PlaylistOverview.kt:129 (no selection); `RubberBand` unused | n/a | – | – |
| 16 | Drag & drop K6 + 2.1 hold-to-drag, drag card with count, NavDrop | DragAndDrop.kt, NavigationChrome.kt | input/DragSource.kt:36 `fileDragSource` (mouse, touch slop, URIs); input/Drops.kt; shell/DropOverlay.kt; player-bar drop NagmedarApp.kt:462 | partial | keep URI design; add hold delay + count in `LabelShadow`; NavDrop optional | 4 |
| 17 | Menu bar in the caption bar (M1) | CaptionBar.kt, DesktopKitRoot.kt, NavigationChrome.kt | shell/CaptionBar.kt, SystemBars.kt, NavigationChrome.kt:546 (S-306) | present | optional: hide BrandMark/title (:563-565) when the system's app chip shows | 4 |
| 18 | Multi-window (M2), prefs synced, unknown start route → first | Windows.kt, DeskPreferences.kt, ShellSpec.kt | NagmedarActivity.kt:286, Ctrl+Shift+N (Shortcuts.kt:199), NagmedarPreferences.kt:103-110, manifest, NagmedarApp.kt:375 | present | keep | – |
| 19 | Shared elements in DetailTransition (M3) | DetailTransition.kt | shell/DetailTransition.kt (own Layout; user: PodcastScreen.kt:201) | missing | with #51 | 2 |
| 20 | Density follows a touchpad on/off (M4) | input/InputModeTracker.kt `PointerDevices` | input/InputModeTracker.kt:62 (no-op), :85 (no isEnabled) | missing | port | 4 |
| 21 | Wallpaper accent (M5) | Palette.kt | Theme.kt:302 `systemAccentSeed` | present | – | – |
| 22 | Haptics (M6), touch only | theme/Haptics.kt | only ExpandablePlayer.kt:523, ProgressTimeline.kt:271 | missing | `NagHaptics` in theme/; NagSwitch, NagCheckbox, FilterChip, SegmentedChoice, sheet threshold, sheet switch rows | 3 |
| 23 | Skeletons; DenseTable(loading) | Loading.kt | States.kt:326 `LoadingState`, :375 `SkeletonBlock` | present | table loading optional | 3 |
| 24 | Reading order top bar → navigation → content → player (phone nav last) | ShellLayout.kt `readingOrder` | none | missing | port | 2 |
| 25 | Large text: bottom bar `IntrinsicSize.Min` + heightIn; chips heightIn; ellipsis | NavigationChrome.kt, Chips.kt | fixed heights NavigationChrome.kt:109, FilterChips.kt:90 | partial | port | 2/3 |
| 26 | Slider label + valueText for TalkBack | Slider.kt | Slider.kt:119-159 | missing | add (volume, EQ) | 3 |
| 27 | KeyCap and search field outlined in light theme | NavigationChrome.kt | NavigationChrome.kt:577-612 | missing | port borders; keep `keyHint` | 3 |
| | **2.1.0** | | | | | |
| 28 | Top bar glass fade alpha-only; new title after the old one left | NavigationChrome.kt PageTopBar | :486 (solid; `topBarChange` fades through) | present/conflicts | title rule present; glass fade conflicts with the user's decision | – |
| 29 | First-launch gap under the bottom bar | DesktopKitRoot.kt | – | n/a until #5 | comes with RootPadding | 2 |
| 30 | Sheet closes when pulled anywhere; wheel never pulls | Sheets.kt | Sheets.kt:418 `SheetDrag` | present | keep the app's | – |
| 31 | `MenuEntry.Item(checked)` = switch at the sheet row's end, tap keeps the sheet; menus announce state | Sheets.kt, Menus.kt | Sheets.kt:300-356 (check replaces icon; tap dismisses); Menus.kt:280 announces | partial | port into `SheetActionRow` with `NagSwitch` | 3 |
| 32 | Flying cover keeps its shape | DetailTransition.kt | – | n/a until #51 | – | 2 |
| 33 | Finger tap opens, selects only while selecting; pointer type decides; long press = menu + select | DenseTable.kt `SelectionTracker.finger` | MusicScreen.kt:1027-1043, PlaylistsScreen.kt:691 decide by InputMode; long-press "Seç" (S-310) | partial | keep "Seç"; let the press's PointerType decide | 3 |
| 34 | On-screen keyboard: player stays on it; finger scroll/tap outside closes it; `keepsKeyboardOpen` | DesktopShell.kt, KeyboardDismiss.kt | NagmedarShell.kt:223 resets scroll on keyboard (player fine); no dismiss | partial | port `dismissKeyboardOnTouch` into the shell; mark NagTextField, NagSearchField, EditableTitle | 2/3 |
| 35 | Player sheet goes the moment the strip is back (SHEET_OUT) | ExpandablePlayer.kt | :285 (`progress > 0f`), :366-369 | missing (bug: spring tail covers the bottom bar) | port | 2 |
| 36 | Shortcut hints with keyboard OR mouse (`showShortcutHints`) | HardwareKeyboard.kt | KeyboardHints.kt `keyHint` (keyboard only) | conflicts | keep the app's rule (user decision) | – |
| 37 | Banner action under text on phone; swatches wrap | Feedback.kt, Choice.kt | AlertBanner already; Choice.kt:182 plain Row | partial | FlowRow | 3 |
| 38 | Drop-down near the window bottom opens above its button | Menus.kt (position provider) | Menus.kt:454-470 | missing | port `dropDown` rule | 3 |
| | **2.2.0** | | | | | |
| 39 | Back clears a selection (touch/select mode any; mouse ≥ 2) | DenseTable.kt/Grid.kt `ClearSelectionOnBack` | MusicScreen.kt:978, PlaylistsScreen.kt:585 (`BackHandler(selection.selecting)` only) | partial | also clear when count > 1 | 3 |
| 40 | Determinate progress not read as "loading" | Feedback.kt | NagProgressBar always takes a label | n/a | – | – |
| 41 | One hint rule for toasts, palette, search field | HardwareKeyboard.kt | `keyHint` in UndoBar, palette, SearchField | present (keyboard only) | keep | – |
| 42 | Palette open → activity skips shortcut routing | Shortcuts.kt docs | palette is a NagDialog window (WindowKeys never sees its keys) | n/a | – | – |
| 43 | DeskCard(verticalArrangement) | Structure.kt | – | n/a | – | – |
| | **2.3.0** | | | | | |
| 44 | Spring thresholds (`PIXEL`, threshold params on gesture/spatial); gesture 0.92/600; new open() 0.9/700 | Motion.kt (+ ShellScroll, Sheets, toasts, swipe) | Motion.kt:323, :336; users ShellScroll.kt:78, DetailTransition.kt:118, ExpandablePlayer.kt:105/:421/:512/:546, Sheets.kt:389-391/:460 | missing | port into NagMotion first | 2 |
| 45 | "Remove animations" stops all motion | many | reduced already snaps most; gaps ExpandablePlayer.kt:105 (expand/collapse), :421 (Back cancel) | partial | close the gaps | 2 |
| 46 | Fade-through: phone scale-in starts with the fade | DesktopShell.kt `shellEnter` | Motion.kt:118-119 (Gesture branch starts early) | missing | remove that branch | 2 |
| 47 | Screens without shell padding re-measured while bars slide | sample | NagmedarApp.kt:713 `UnderBars` lacks Equalizer | partial | add Equalizer | 2 |
| 48 | Player opens from where the strip is drawn; bars stay; anchor measured each press; `showBars()` gone | ExpandablePlayer.kt, ShellScroll.kt | ExpandablePlayer.kt:214-226 `beforeExpand` → `onExpandStart`; NagmedarShell.kt:218; ShellScroll.kt:86 | missing (bug: bars jump back) | port | 2 |
| 49 | Flung sheet carries on at the finger's speed, dismissed once out (phone) | Sheets.kt `SheetPull.release`, `carriedOut` | Sheets.kt:451-463 calls onClose(), then :391 animates from standstill | missing | port into SheetDrag; also QueuePage (QueuePanel.kt:421) | 2 |
| 50 | Sheet: one firm spring both ways (0.92/800); `offset{}` instead of a layer | Sheets.kt | Sheets.kt:389-391; :601-605 layer | missing | port the spring; `offset{}` only matters for blur (user's decision) | 2 |
| 51 | Detail depth (12 % in, list to 95 %), `detailSharedCard`, Back on SeekableTransitionState with the list really behind, run back on a change of mind; wide unchanged | DetailTransition.kt | DetailTransition.kt: `DetailMotion.frame` :66, `DetailBackHandler` :161, `placeListAndDetail` :252; users PodcastScreen.kt:201, Panes.kt:143 | missing | keep the app's layout (list stays composed); phone frames into `DetailMotion.frame(tier)`, "list behind" into DetailBackHandler + placeListAndDetail; shared podcast cover optional. Do NOT adopt the kit's AnimatedContent | 2 |
| 52 | Player one surface (strip becomes page, corners 4p(1−p), mini controls unplaced after 0.3, full from 0.6), `playerSharedElement` for cover and title, tap spring 0.9/500, Back shrink rides down | ExpandablePlayer.kt `PlayerFlight` | ExpandablePlayer.kt:347-425; PlayerBars.kt:381-389, :662 `CompactTransportBar`; PhonePlayer.kt:813 | missing | port from the app's card shape; keep CompactTransportBar + artwork accent; mark artwork + title shared; strip-glass-to-page under Solid = surfaceRaised → background (glass part: user's decision) | 2 |
| 53 | Phone menu grows from the point it opened at | Menus.kt provider `origin` | Menus.kt:161 always top-start | missing | port with #38, #64 | 3 |
| 54 | Rows arrive in turn (`rememberListAppear`/`listAppear`) | ListAppear.kt | none | missing | add to components/; music, podcast, radio, playlist lists | 2 |
| 55 | Phone press ¾ as deep, bounces back | Interaction.kt `pressScale` | Interaction.kt:94-96 | missing | port | 3 |
| 56 | Switch thumb stops within half a pixel | Forms.kt | Choice.kt:289 | missing | threshold | 3 |
| 57 | Full player: content scrolls first, then the sheet | ExpandablePlayer.kt `PlayerPull` | ExpandablePlayer.kt:284-313 `PlayerSheetScope.nestedScroll` | present | keep; settle via #66's fling | – |
| 58 | Chip check mark takes the icon's place | Chips.kt | FilterChip has no icon | n/a | – | – |
| 59 | Predictive Back between tabs (phone) via `predictivePop*` | DesktopShell.kt | NagmedarShell.kt:318-326 pop specs only; tab-root Back = plain BackHandler :333 | missing + upgrade risk | P1: pass `predictivePop*` = the page pops. P2: the phone gesture | 1/2 |
| 60 | Accent change recomposes only colour readers | Theme.kt (dynamic local; Material scheme from target; typography remembered) | Theme.kt:34 static local, :96 scheme rebuilt every frame, :112 typography not remembered | missing | port; ArtworkAccent already avoids per-frame changes | 2 |
| 61 | Lookahead only while something moves | DetailTransition.kt | no SharedTransitionLayout | n/a | – | – |
| 62 | One shared glass behind strip, bar, band | ShellLayout.kt `ShellBottomGlass` | – | n/a under Solid | user's decision | 2 |
| | **Not in the CHANGELOG** | | | | | |
| 63 | Focus returns where it was when a sheet/palette closes | Interaction.kt `FocusMemory`, Sheets.kt `ReturnFocusOnClose`, ShellPadding.kt | none | missing | via `nagClickable` and ShellOverlayHost | 3 |
| 64 | Menu keeps its place while fading; new pop-up per opening (`generation`); right click outside replayed; Esc without focus (0.2.2 port) | Menus.kt, input/ContextMenu.kt | Menus.kt:142-166 keeps only entries; input/ContextMenu.kt:127 `offset ?: IntOffset.Zero` (jumps while fading) | partial | port | 3 |
| 65 | Mouse: hover/press colours change at once; to/from transparent alpha-only (`animateInteractionColor`, 0.2.1 port) | Interaction.kt, Basics.kt, NavigationChrome.kt | Interaction.kt:70-86 always eases | partial | instant in pointer mode | 3 |
| 66 | `AnchoredDraggableState(initial)` + `AnchoredDraggableDefaults.flingBehavior` | ExpandablePlayer.kt | ExpandablePlayer.kt:101-107 (old threshold constructor), :253, :309 `settle(velocity)` | missing | required by Compose 1.12 | 1 |
| 67 | Sidebar footer shows its badge | NavigationChrome.kt | :390, :404 `badge = null` | missing | trivial | 4 |

## 2. Conflict hotspots and order

| App file | Parts | What each changes |
|---|---|---|
| shell/NagmedarShell.kt | 1, 2, 4 | 1: NavHost `predictivePop*` (#59). 2: band/system bottom, reading order, keyboard dismiss, `onExpandStart` removal, tab gesture. 4: NavDrop, caption in Rail/Sidebar |
| shell/NavigationChrome.kt | 2, 3, 4 | 2: BottomNavBar (#25). 3: KeyCap, SearchField (#27). 4: MenuBarChrome chip, sidebar/rail drops, footer badge |
| shell/ShellPadding.kt | 2, 3 | 2: systemBottomPx, bottomInsetPadding, ShellOverlayHost cleanup. 3: focus memory, text-field registry |
| components/ExpandablePlayer.kt | 1, 2, 3 | 1: AnchoredDraggable API (+ haptic constant at minSdk 34). 2: #35 #44 #45 #48 #52. 3: haptics |
| components/Sheets.kt | 2, 3 | 2: SheetDrag, GlassBottomSheet, SheetSurface (#7 #49 #50). 3: SheetActionRow/ActionGroupList (#31), haptic, focus return |
| theme/Motion.kt, theme/Theme.kt | 1, 2 | 1: SDK check. 2: motion tokens, dynamic colours |
| NagmedarApp.kt | 2, 3, 4 | 2: root insets. 3: marking fields. 4: drop and new-window wiring |
| music/MusicScreen.kt, music/PlaylistsScreen.kt | 2, 3 | 2: listAppear. 3: selection tap and Back rules |
| tests: TestShell.kt, PlayerAndSheetTest, SheetDragTest, 267 screenshot references | all | regenerate screenshots once per merged part, never in parallel |

Order: (1) Part 1 alone (build + forced code: Glass.kt, ExpandablePlayer API, predictivePop*, minSdk; screenshots), merged
first. (2) Small Part 2a: theme/Motion.kt tokens (#44-#46) + theme/Theme.kt (#60), merged quickly. (3) In parallel: Part 2b
(shell, player, sheet motion, detail); Part 3 on files nobody else edits (Interaction, Menus, ContextMenu, Choice,
FilterChips, Slider, TextFields, NoticeHost, States, DenseTable); Part 4 on InputModeTracker, DragSource, CaptionBar.
(4) After 2b merges: Part 3's Sheets.kt rows, ShellPadding hooks, MusicScreen rules; Part 4's NagmedarShell/NavigationChrome edits.

## 3. Platform upgrade
- build.gradle: :3 AGP 9.1.1 → 9.4.1 with settings.gradle:9 (`com.android.settings` must match); :4 Kotlin 2.2.10 → 2.4.20
  (feeds the Compose compiler :15 and kotlin-reflect vlc-android:230, moviepedia:57); :5 coroutines 1.7.1 → 1.11.0 (VLC's
  `actor`/ObsoleteCoroutinesApi ~35 uses still compile); :22 KSP 2.3.2 → ≥ 2.3.6 (users: vlc-android, mediadb, moviepedia,
  television, nagmedar-data); :120 Room 2.6.1 risky on Kotlin 2.4 (2.7+ is KSP2-native; bump if the processor fails);
  :138-145 BOM 2026.09.00, activityCompose 1.13.0, navigationCompose 2.10.2, haze 2.0.1, lifecycleCompose 2.11.0,
  nagmedarRobolectric 4.17. Leave :103 core 1.12, :105 lifecycle 2.5.1, :107 activity 1.8.2 for VLC (constraints raise them
  at run time; bumping surfaces ~57 `launchWhenX` deprecations). :16 android-maven-gradle-plugin:2.1 unused → remove. :17
  vanniktech 0.34.0 (medialibrary, remote-access-client) and :149 macrobenchmark 1.3.4: verify with AGP 9.4. :154 media3 1.4.1 unchanged.
- gradle.properties: keep :33 `android.builtInKotlin=false` (≈17 modules apply kotlin-android; kapt for data binding in
  vlc-android/moviepedia; parcelize in vlc-android/television) and :34 `android.newDsl=false` (old variant API in
  app/build.gradle:196, medialibrary/build.gradle:79, remote-access-client/build.gradle:76); keep :23-24. Verify the old
  `dataBinding { enabled }` blocks (vlc-android:15, app:31, moviepedia:10, remote-access-server:9, donations:32) on AGP 9.4.
- nagmedar-ui/build.gradle :71-72 Haze 2.0.1 (drop haze-materials, add haze-blur); unitTests: `jvmArgs
  '--add-opens=java.base/jdk.internal.access=ALL-UNNAMED'` (as the kit for Robolectric 4.17); same in data, session, audio.
- Forced code: theme/Glass.kt:218-231 (Haze 2), components/ExpandablePlayer.kt:101-107/:309 (AnchoredDraggable),
  shell/NagmedarShell.kt:318-326 (`predictivePop*`); regenerate the 267 screenshot PNGs and the Baseline Profile (it names
  `dev/chrisbanes/haze/HazeState` and old Compose internals); libraries.json Haze entry stays (AboutScreenTest expects 73).
- Tests/Java: Robolectric 4.17 covers SDK 23-37 (sdk=34 works on Java 17). sdk=36 needs Java 21 in CI (`setup-java '17'`
  in ci.yml :102-106, :150-154, :209-212, :393-396, :474-477; emulator.yml:145). Gradle 9.8.2 runs on 17. Classic VLC tests
  use Robolectric 4.3.1 (build.gradle:132), not run in CI.
- Gradle 9.8.2: CI gets Gradle through buildsystem/compile.sh:332-335 (`GRADLE_VERSION`, `GRADLE_SHA256` from gradle.org);
  cache paths `gradle-9.3.1` at ci.yml:228, :491, emulator.yml:161. vlc-android/build.gradle:272 `project.exec`
  (deprecated for removal; only with -PforceDeterministic) → `providers.exec` as line 9.
- minSdk: settings.gradle:15 must rise to ≥ 24 (§4).

## 4. minSdk
- The floor moves anyway (navigation 2.10 → API 24): the choice is 24 or 34.
- Kit 2.x code needing API 26-34 (guards needed at 24): ExpandablePlayer.kt:853 `GESTURE_THRESHOLD_ACTIVATE` (34);
  DesktopKitRoot.kt:250-251 contrast enforcement (29); Palette.kt:52 `system_accent1_200` (31); Menus.kt:160
  `addOnUnhandledKeyEventListener` (28); InputModeTracker.kt:113 `InputDevice.isEnabled` (27); Motion.kt:172
  `areAnimatorsEnabled` (26); Haptics.kt ToggleOn/Off/SegmentTick/GestureThresholdActivate (34), Confirm/Reject (30).
  Predictive-Back motion (#51 #52 #59) animates only on 34+. API-35 checks stay (CaptionBar.kt:34, DesktopKitRoot.kt:230).
- 34 lets the app drop ~60 of 73 SDK checks in nagmedar-ui (optional cleanup); VLC modules' checks become dead code (lint
  ObsoleteSdkInt only). API-35 checks stay (JobForeground.kt:16, TimeLimit.kt:27, CaptionBar.kt:33, SystemBars.kt:29, StartupPerf.kt:40/47).
- Affects: app/build.gradle:89 `vlcBundle` minSdk 30 override → remove; settings.gradle:15 VLC3 branch (minSdk 17,
  `-PforceVlc3`, compile.sh:282) already incompatible with the AndroidX floor → retire or raise (user decides). Tests:
  nothing pinned below 34; GlassTest.kt:65-69 passes sdk = 30 to the pure `modeFor` (still fine). Emulators 35/37 fine.
  `tools:overrideLibrary` entries become unnecessary (harmless).
- Recommendation: minSdk 34 for the libVLC 4 build (two build-file edits; guards unnecessary; predictive Back always
  available; the user's devices are Android 16/17; not on Play).

## 5. Effort and risks
| Part | Size | Main work |
|---|---|---|
| 1 Platform | L | versions, Haze 2, AnchoredDraggable, predictivePop*, KSP/Room/kapt in the classic modules, minSdk, screenshots + Baseline Profile |
| 2 Phone motion + shell | L | 3-button band + root insets (#5), player single surface + shared elements (#52), detail depth (#51), springs/thresholds, sheet carry-out, player open/close fixes, reading order, listAppear, accent recomposition |
| 3 Components, keyboard, selection | M | haptics, sheet switch rows, menu fixes (#38 #53 #64), interaction colour + press, selection tap/Back, focus return, keyboard fields, toast swipe, slider, keycap, chips, swatches |
| 4 Tablet + DeX | S | touchpad on/off, hold-to-drag + count, caption app chip, footer badge; optional NavDrop, Ctrl+Enter |

Riskiest: (1) Compose 1.7 → 1.12 + navigation 2.10 in one step (predictive Back defaults, AnchoredDraggable, Haze 2, 267
screenshots, Room 2.6.1/KSP/kapt-with-data-binding on Kotlin 2.4); (2) moving the bottom insets from the root to the shell
(#5): every full page, WindowPage, sheet and full-window overlay changes its bottom edge; (3) player single surface (#52):
recording the artwork into GraphicsLayer/Picture while the full player stays composed whenever music plays; the strip-to-page
change on the floating card; (4) detail depth (#51): port the motion into DetailMotion, never the kit's AnimatedContent;
(5) the minSdk ripple (vlcBundle, VLC3).
The user's phone uses three-button navigation: #5 shows on it at once; the gesture-only motion (#51 Back, #52 Back shrink,
#59) will not be seen there.
