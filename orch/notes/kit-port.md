# kit-port (Desktop Kit 1.0 → Nağmedar), phone track (story/kit-port-phone)

PR A ready: #74 ee7d6785 (all CI jobs green; screenshots 251 same — Robolectric draws glass translucent, within the report's tolerance)

## PR A — what the workstation port can build on
- Deps: Haze 1.5.4 (`hazeVersion` in root build.gradle; haze + haze-materials in nagmedar-ui), activity-compose 1.10.1. Haze in libraries.json (About test counts 88).
- theme/Glass.kt: `NagGlass.chrome/raised/material/composite/modeFor`, `GlassMode`, `GlassThickness`, `GlassMaterial`, `LocalGlassSource`, `LocalGlassMode`, `rememberSystemGlassMode()`, `Modifier.glassSource()`, `Modifier.glass(material, shape, source, mode, enabled, rest)`. Reduced motion = `NagTheme.motion.reduced`. Robolectric → Translucent automatically.
- theme/ViewAnimators.kt (`ViewAnimators`, `rememberViewAnimators()`), `SystemMotion.reduced(context)` in Motion.kt.
- theme/Motion.kt: `MotionTier {Gesture, Timed, Reduced}`, `LocalMotionTier` (NagmedarTheme provides by window width, NagmedarShell by width class), `NagMotion.gesture()/tier()/spatial(tier, timed)/fade(tier, spec)`, `NagMotion.COLOR_CHANGE`; `screenEnter/screenExit(…, tier)`.
- theme/Theme.kt: `NagmedarTheme(colorChangeMillis = 150)` cross-fades palettes (`animatePalette`, `lerpColors`); `NagColors.withAccent(tones)`; `ArtworkAccent(seed) { }` (player surfaces: PlayerBar, PhonePlayerHost).
- shell/ShellPadding.kt: `rememberShellContentPadding()`, `Modifier.shellContentPadding()`, `PaddingValues.plus/topOnly/bottomOnly`, `LocalShellContentPadding`, `UnderShellBars(listOnTop) { }` + `LocalShellBottomPadding` / `LocalShellTopPadding` (lists opt in: `contentPadding = PaddingValues(…) + LocalShellBottomPadding.current`), internal `ShellChromeMetrics` (incl. underFloatingPlayer), `ShellScreenFrame`, `ShellOverlayHost` (+ `LocalShellOverlayHost`, rootView check).
- shell/ShellLayout.kt: content first then chrome; BottomBar content full window, nav/player slide via graphicsLayer; `screenOverlay` slot (app's undo/notice/lyrics overlay, between the bars), `overlay` slot (sheets, expanded player); `floatingPlayer`/`floatingMargin`/`floatingMaxWidth` already ported for Rail/Sidebar (unused yet: `NagmedarShell(floatingPlayer = true)` draws the rounded glass bar and pads screens).
- shell/NagmedarShell.kt: new params `floatingPlayer`, `glass`; provides LocalMotionTier, LocalGlassMode, LocalGlassSource, LocalShellOverlayHost, LocalShellContentPadding. `contentOnly` kept (tablet's phone player overlay).
- components/Sheets.kt: `GlassBottomSheet`, `MenuSheet` (now glass), `ActionGroup`, `toMenuEntries()`, `toActionGroups()`, `MoreSheet`, `MoreButton`, internal `OverlayLayer`, `shellOverlayHere()`.
- components/DialogCard.kt: `DismissStyle`, `DialogDismiss`, `LocalDialogDismiss` (now `DialogDismiss?`), `dismissStyleFor()`, `DialogDismissButton()`; `DialogFrame(onDismiss, dismissStyle = …)`.
- Menus: MenuPopup glass inside the shell (no shadow then); disabled rows transparent.
- Not ported (workstation's): Inspector.kt, DenseTable column state/glass header, DeskPreferences inspector/columns.
- Tests: theme/GlassTest, theme/MotionTierTest, shell/ShellPaddingTest.

## PR B (#77, story/kit-port-phone-b) — the phone's "pure listening" UX
- components/ExpandablePlayer.kt: `PlayerValue`, `ExpandablePlayerState` (AnchoredDraggable, saveable; `expand()/collapse()/snapTo()`, `progress` read only in graphicsLayer/offset lambdas), `rememberExpandablePlayerState()`, `ExpandablePlayer(state, mini, full, modifier, expandable, onPrevious, onNext)`, `MiniSwipe.decide/resist`, `MiniSwipeAction`. Drawn in the shell overlay at `ShellOverlayHost.PLAYER` (new `playerLayer` slot of ShellLayout: over the chrome, under `screenOverlay`, so lyrics/visualiser/queue/notices come over the expanded player); sheets at `ShellOverlayHost.SHEET`. `ShellOverlayHost.navigationOffset` lets the player anchor where the strip rests with the bars shown; `expandProgress` slides the bottom bar (draw phase) and, via derivedStateOf, only the end state re-measures the screen overlays.
- NagmedarApp: on BottomBar the mini player (PlayerBar → ExpandablePlayer) grows into PhonePlayerHost (moved, not rewritten); the Rail keeps the S-1002 overlay (`phonePlayer` flag, AnimatedVisibility); rotating swaps between the two keeping it open. Stations/episodes: `expandable = false`, their mini player opens their pages, no sideways swipe (the transport has no seek steps for them). Music: swipe = previous/next, haptic at threshold (GESTURE_THRESHOLD_ACTIVATE ≥ 34, else CLOCK_TICK), spring back.
- Bottom bar ⋯: NagmedarShell(moreActions) — `Destination.More` on BottomBar opens a GlassBottomSheet with the overflow pages grouped by NavSection + the app's "Oynatma" (Ekolayzer…, Uyku zamanlayıcısı…, Sıradaki); rail/sidebar unchanged (More page). Shared shell code touched: NagmedarShell (moreActions, select, playerLayer), ShellLayout (playerLayer slot, placed in every layout), ShellPadding (Layer z, navigationOffset), NavigationChrome (⋯ icon for More on the bottom bar only).
- Item ⋯ (compact only, `MoreButton` → MoreSheet; a menu on wider windows): DenseTable `rowMoreButton/rowMoreTitle` (Music TrackPane, PlaylistTable, SmartPane), MusicScreen GroupRow (albums/artists/folders), PodcastPageView EpisodeRow, RadioViews StationRow (all widths: was IconMenuButton), Search track hits. Menus reuse the long-press builders.
- Tests: components/MiniSwipeTest, components/PlayerAndSheetTest; ShellScreenshotTest compact-more-sheet-dark / -light-en. TestShell gained `player` and `moreActions` (screen lambda kept last).
- PR B ready: #77 7d9a1b39 (all CI jobs green; screenshots 244 same). origin/master 400f8505 (PR A) is already in the branch, so no merge was needed.
- Open: the glass sheet's translucent fallback (API < 31, Robolectric) lets the list behind show through visibly; with blur (S24) it is frosted. If it reads badly on a device, raise the sheet material's translucent alpha in theme/Glass.kt (shared with the workstation).

## Tablet/DeX workstation (story/kit-port-tablet, PR #78)
- components/Inspector.kt: `InspectorState` (widthDp, collapsed, selectedTab, resizeBy/resizeTo/commit/toggle), `rememberInspectorState(key = WORKSTATION_PANE, 384.dp)` (width + hidden kept through `PaneWidths`: new default methods `paneCollapsed/savePaneCollapsed`, NagmedarPreferences `nagmedar_pane_collapsed_<key>`), `WorkstationLayout(state, main, inspector, minMain, minInspector=260, maxInspector=560, showFrom=Expanded)` (uses S-303's PaneDivider; pane cut not squeezed while sliding), `InspectorPane(tabs, selected, onSelect, onCollapse?)`, `InspectorTab(id,label)`, `InspectorToggleButton`.
- Ctrl+I = `ShortcutId.ToggleInspector` (View group) → `ScreenCommands.toggleInspector()`; a screen registers with `InspectorCommands { … }` (Selection.kt).
- music/MusicInspector.kt: `MusicInspectorContent(tags, signal, lyrics, queue)` slots, `InspectorSubject(track, selected)`, pure `TrackInspector`, `TrackTags`, `WaveformStrip`; app wiring `rememberMusicInspector(onFullScreenLyrics, onFullScreenVisualizer, undo)` (NagmedarApp passes it to `MusicScreen(inspector = …)`; `nowPlaying` param removed). Tags tab for what plays = `MusicNowPlaying(withQueue=false, withLyrics=false, below = tags)`.
- MusicBrowser: selection hoisted (`rememberMultiSelection` in MusicBrowser, passed to TrackPane); beside a shown inspector on windows < Large `wide` is false (groups then table, Back returns); minMain 560/760 dp.
- Columns: `MusicColumns.toggle/move` (MusicTable.kt), `MusicBrowserState.move/resetColumns/columnsOf`, per-view key `columnsKey(view)` = `nagmedar_music_columns_<view>` (old global key as fallback); `columnMenu(state, labels, words, clicked)`; DenseTable `headerMenu: (Int) -> List<MenuEntry>` per title, `TableColumn.id`, `glassHeader` (opt-in; music on non-compact), `onExtendSelection` (Shift+↑/↓; `MultiSelection.extend`). Selection's right-click menu = `tracksMenu(res, tracks, actions, rate = true)`.
- Floating bar: `NagmedarShell(floatingPlayer = true)` in the app; `LocalFloatingPlayer` → `TransportBar/MiniPlayer(transparent = true)`; floatingMaxWidth 1180 dp on Sidebar, 760 on Rail. TestShell(floatingPlayer = false by default): only the music references show the floating bar; other tablet/DeX references still show the stacked bar.
- QueuePanel(onClose = null, background) for embedding; `softLevels` and `channelsText` now internal.

## UI review fixes (story/ui-fixes, PR #83)
- GlassBottomSheet: `showing` = derivedStateOf(visible || progress > 0); returns before anything else when closed (no LaunchedEffect, no OverlayLayer, no focus/BackHandler). Keep it that way: every row's ⋯ / ActionMenuButton / ContextMenuArea(asSheet) holds one.
- Accent: `ArtworkAccent` provides the target palette once (no animatePalette per frame). Surfaces that draw the accent largely read `animatedAccent(colors.accent)` (State) inside draw lambdas: ProgressTimeline canvas, ProgressHairline, PlayPauseCircle (drawBehind). New big accent surfaces in the player should do the same; reading `colors.accent` in composition just snaps.
- ExpandablePlayer: the full player is composed whenever music plays (OverlayLayer always registered while `canExpand`), measured but placed only while `PlayerSheetScope.shown` (+ clearAndSetSemantics). The sheet's mini copy is composed only while shown. `LocalPlayerSheet` (components) tells the full player it is hosted: PhonePlayer then drops its BackHandler and `drop`, Stage detects only horizontal drags; ProgressTimeline's glide, `rememberVisualizerConditions` (shown) and `rememberWaveform(uri, wanted)` wait for `shown`. `PlayerSheetScope.nestedScroll` hands overscroll (lyrics at top) to `draggable.dispatchRawDelta` and settles in onPreFling. PhonePlayerHost wraps ArtworkAccent only when not hosted (`AccentUnless`).
- `beforeExpand` runs from `awaitVerticalDragStart` (Initial pass, watches only) or expand() (click), not on every press.
- NagmedarApp snaps `expandingPlayer` to Collapsed while `nowPlaying.media == null`.
- NagmedarShell: `Modifier.underPlayer(playerOver)` (always-on graphicsLayer alpha + clearAndSetSemantics) on content, top bar, player slot; bottom nav's layer alpha. Tab focus into the hidden screen is still possible with a hardware keyboard (no stable subtree focus block in Compose 1.7).
- Music: `MusicLibrary(tracks, locale)` has no history; `library.recentGroups(recent, filter)` for the Recent view; `MusicBrowser(recent =, shelf =)`; OrderKey includes the Recent history. MusicScreen reads history with `repository.historyChanges()` (Medialibrary.HistoryCb callbackFlow) inside rememberRead; on phones `recent` passed as emptyList() and only the shelf lambda reads the State. DenseTable `leading` slot = first LazyColumn item + stickyHeader titles (not with reorder/glassHeader); GroupList `leading` + `rowPadding`.
- `moreActionsLabel(name)` = "<ad> için diğer işlemler" (nag_more_actions_for); MoreButton's default.
- ViewAnimators.kt and SystemMotion deleted (no View animators left).
- Tests: components/PlayerAndSheetTest (composed once, drag from artwork, lyrics overscroll, closed sheets no overlay), perf/RecompositionTest, MusicLibraryTest Recent.


## 2.3 port — Part 1 (platform) (story/platform, PR #99) - NOTES FOR THE NEXT PARTS
- Platform now = Desktop Kit 2.3.0's: AGP 9.4.1 (+ settings.gradle com.android.settings), Gradle 9.8.2 (compile.sh sha
  21bd5da2…9607; CI caches gradle-9.8.2), Kotlin 2.4.20 (+ Compose compiler plugin), KSP 2.3.11, coroutines 1.11.0, Compose
  BOM 2026.09.00 (1.12.1), activity-compose 1.13.0, navigation-compose 2.10.2, lifecycle-compose 2.11.0, Haze 2.0.1
  (haze + haze-blur), Robolectric 4.17 (tests still sdk=34; `--add-opens=java.base/jdk.internal.access=ALL-UNNAMED` in ui,
  data, session, audio). builtInKotlin/newDsl still false; VLC modules keep their own AndroidX versions. Java/Kotlin
  bytecode 17 (build.gradle targetJVM): Compose 1.12 inlines Java 11 bytecode, 1.8 cannot.
- minSdk 34 for both libVLC branches; the ~60 now-dead SDK checks are still there: delete the ones you touch.
- Gone: application/television, application/moviepedia (+ Retrofit, colour picker, leanback-preference, the TV search
  provider and its authority, 62 drawables, 52 strings). RetiredWork flag "moviepedia" deletes moviepedia_database and the
  sort keys (MOVIEPEDIA_KEYS, also left out of backups). leanback (base lib) stays for the classic player's options panel.
- Kotlin 2.4 turns "always false is-check" and "reified intersection" into errors (fixed: NagmedarDatabase.recreate's
  arrayOf<Any>, HeaderMediaListActivity, BaseBrowserFragment). Expect the same in new code.
- theme/Glass.kt: `glass()` uses Haze 2's `hazeBlur(HazeInput.Sources(source), style)`, `GlassMaterial.blurStyle()`
  (one HazeBlurStyle per material, remembered). HazeState()/hazeSource unchanged. App still GlassMode.Solid.
- components/ExpandablePlayer.kt: `AnchoredDraggableState(initialValue)`; release rules = `playerFlingBehavior(state)`
  (AnchoredDraggableDefaults.flingBehavior, half way, NagMotion.gesture()) passed to both anchoredDraggable modifiers;
  expand()/collapse() animate with NagMotion.gesture() explicitly; PlayerSheetScope.fling + private settle(velocity)
  (anchoredDrag + ScrollScope + performFling, as the kit's PlayerPull). Fling speed is Compose's own (125 dp/s,
  AnchoredDraggableMinFlingVelocity) instead of the old 400 dp/s: no API sets it any more. Part 2's #44/#48/#52/#66 build here.
- shell/NagmedarShell.kt: NavHost `predictivePopEnterTransition/ExitTransition` = the shell's popEnter/popExit (swipe edge
  ignored); without it navigation 2.10 plays its own scale-to-70 %. Part 2's phone gesture (#59) replaces these lambdas.
- Compose 1.12 deprecations fixed: LocalClipboardManager → platform ClipboardManager (lab report, lyrics import),
  focusProperties `exit` → `onExit { cancelFocusChange() }` (ModalLayer). Other UI warnings are the old ones.
- Screenshots: Compose 1.12 + Robolectric 4.17 draw all 267 exactly as master's own run (ci/screenshots of 60fd901). 91
  references differ from master's own rendering by < 0.1 % (stale since before this part, e.g. the floating player card
  #F8F9FA vs surfaceRaised #FFFFFF): a part that needs a clean baseline should take ci/screenshots of master once.
- APK: minSdk ≥ 28 makes AGP store dex uncompressed: personal arm64 87.6 → 97.2 MB (install size smaller, download
  same). The delivery splits at 90 MB, so fine; `packaging { dex { useLegacyPackaging true } }` would restore the old size.
- Baseline Profile regenerated (ci/baseline-profile 9513466): 45,966 rules, startup 22,571; phone player 5/6 as in 1.0.2.
- Walks of 6d8c86e (the split walk, master 1e6a657 merged): debug and personal, API 35 and 37.0, tablet and phone: all
  8 jobs passed (0 app crash, every check ok; Auto's AutoBrowseTest OK on both personal walks; the system's gms ANRs only
  recorded). Frames are noisy on swiftshader (same app, list p50 38-57 ms on API 35): a paired phone-en API 35 run,
  branch vs master's app via inputs[ref], gave list 38 ms/31.6 % janky vs 48/55.4, drag up 81/76.3 vs 77/84.6, down
  46/69.1 vs 61/81.1. Compare frames only within one dispatch, never across runners.
- The old walk's two failures on this branch were the walk's, not the app's: "the trail names the drive" (debug 37.0,
  9a4328e) = the 30 s tone ended between the dump that saw the big player and Back, so Back left This device (the guard
  reads the dump before the screenshot; the tone plays ~28-36 s before Back on every runner); "a drag down from the
  sheet's middle" (personal 37.0, d343c65) = 1.0.2's input-swipe stall (release.md "## 1.0.3 walk"). Both passed on the
  split walk. A longer tone.mp3 (60 s) would remove the first race for good.
