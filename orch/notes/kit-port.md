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

## 2.3 port — Part 2a (motion base) (story/motion-base, PR #104) - NOTES FOR THE NEXT PARTS
- theme/Motion.kt: `NagMotion.PIXEL` (0.5f) and `NagMotion.FRACTION` (0.001f); `NagMotion.gesture(visibilityThreshold)`
  = spring 0.92/600 (was no bounce/400); new `NagMotion.open(visibilityThreshold)` = 0.9/700 (unused so far: Part 2b's
  detail depth #51 and sheet are its users); `NagMotion.spatial(tier, timed, visibilityThreshold)`; the instance
  `motion.spatial(threshold)`/`fastSpatial(threshold)` take one too. Rule: px value -> PIXEL, IntOffset/Rect ->
  `VisibilityThreshold`, Dp -> `Dp.VisibilityThreshold`, 0..1 progress -> FRACTION. Every spring of the app passes one
  except Interaction.kt pressScale, Slider.kt, Choice.kt's switch thumb (#56) and the nav pill: Part 3's files.
- screenEnter: the phone's Gesture branch is gone; phone and tablet both grow with the delayed emphasized tween (#46).
- ExpandablePlayerState.tier (set by ExpandablePlayer from LocalMotionTier) -> expand()/collapse() = spatial(tier,
  gesture(PIXEL), PIXEL); playerFlingBehavior too; Back's shrink = fade(tier, …), its cancel = spatial(…, FRACTION).
- DetailMotion.ARRIVED = 0.02f: the phone's detail spring stops a fiftieth from its end (frames are a function of the
  progress; < 0.05 px left on 1440 px). DetailBackHandler's cancel = spatial(tier, motion.spatial(), FRACTION). Part 2b
  replaces DetailMotion.frame (#51): keep a threshold where < 0.5 px is left.
- Equalizer is in NagmedarApp's UnderBars; EqualizerScreen pads `LocalShellBottomPadding` at its Column's end.
- theme/Theme.kt: `LocalNagColors` is `compositionLocalOf` (dynamic); scheme/selection from `target`; typography
  remembered. Material components now take an accent at once; NagTheme.colors readers still fade.
- Tests: motion/MotionStripsTest (8 strips -> build/outputs/screenshots/motion/, CI puts them in ci/shots-pr-<n>/motion/;
  each frame label "•" while the recomposer has pending work, last label "durdu: N ms"); motion/SpringSettleTest;
  perf/AccentRecompositionTest; theme/MotionTierTest. Frame-by-frame tests: mainClock.autoAdvance=false, then
  advanceTimeByFrame + waitForIdle per frame; `Recomposer.runningRecomposers.value.any { it.hasPendingWork }` = something
  still animates. Every Compose test needs @GraphicsMode(NATIVE) (ComposeSandboxTest).
- Strips before -> after ("durdu"): page push/pop 752 -> 400 ms, player expand 736 -> 400, collapse/Back 752 -> 416,
  sheet open/close 384/400 -> 400/352, tab 368 -> 368 (growth now visible with the fade). Copies:
  $SCRATCH/agents/motion-base/{before2,after}/.
- Seen in the strips, NOT fixed here (Part 2b): on the expand's first frame the sheet's copy of the mini player shows
  "0:00 / −4:18" (a fresh SmoothProgress/PlayerBar copy starts at 0 until its LaunchedEffect reports).
- 270 screenshot references identical (master's own 270 at 2a859324). CI of ff7ad1d0 all green.
- Walks of ff7ad1d0: debug (run 42081165624) and personal (42081170464), API 35 and 37.0, tablet and phone: all 8 jobs
  passed, 0 app crash, every check ok, Auto ok (system ANRs only recorded). Paired phone-en API 35 debug, one dispatch
  each with the same driver: master app 2a85932 (42081778052) list 57 ms/62.5 % janky, drag up 81/88.9, down 65/84.6;
  branch app (42081782304) 57/68.8, 32/90.3, 73/95.0: within swiftshader's noise (frames are not the emulator's
  strength; the strips' timing is the measure of this part).

## 2.3 port — Part 3 (components) (story/components, PR #103) - NOTES FOR THE NEXT PARTS
- Menus (components/Menus.kt): `MenuPopup` holds entries AND offset while fading (`MenuEntriesHolder`); a press outside
  is swallowed and a right click is replayed into the anchor's window (`OutsidePressHandler` on the pop-up's root view);
  Esc without a focused item via `EscapeListener` (unhandled-key listener, acts on key UP like the menu's own Esc; uses
  `onEscape ?: onDismiss`, so the menu bar's Esc still returns to its title). `ClampedOffsetPositionProvider` is internal
  with `origin` (TransformOrigin read in the layer) and the drop-down rule (offset.y >= anchor height → above the
  button). Scale is a graphicsLayer driven by `transition.animateFloat` (0.92 in, 0.96 out); origin = provider.origin
  on MotionTier.Gesture, top-start (top-end RTL) wider up. `MenuState.generation`; `ContextMenuArea` wraps its MenuPopup
  in `key(menu.generation)` (not the MenuSheet branch).
- Interaction.kt: `stateBackground` (same signature, now a `Color`) goes through `animateInteractionColor(target,
  selected, spec, instant = pointer, label)`: pointer hover/press snap, a change of `selected` eases, to/from clear only
  alpha (InteractionFade). `animateStateColor` always eases (instant = false) as before. NagButton, NagPlayButton and
  the navigation pill (NavIndicator) use `animateInteractionColor`. `pressScale`: phone (Gesture, not reduced) presses
  to `phonePressScale(scale)` (¾ depth) and releases on spring(0.75, 1200, 0.001); wider up unchanged.
- theme/Haptics.kt: `NagHaptics` (toggle/tick/threshold/confirm/reject; silent unless InputMode.Touch),
  `rememberNagHaptics()`. Used by NagSwitch, NagCheckbox, FilterChip (toggle) and SegmentedChoice (tick on a new
  option). NOT yet: the sheet's close threshold (Sheets.kt SheetDrag: arm when offset crosses height/3, call
  haptics.threshold() once per crossing, as the kit's SheetPull.onThreshold) — do it with #31/#63 after Part 2b.
  ExpandablePlayer's own threshold haptic (View.performHapticFeedback) is untouched.
- TextFields.kt: `NagTextField(String…)` gains clearable, error, supportingText (live region + semantics error),
  keyboardType, imeAction (Next moves focus), autoCorrect, filled, textStyle, contentPadding, contentAlignment,
  showLabel, interactionSource; a `NagTextField(TextFieldValue…)` overload adds fillWidth. String `nag_field_clear`
  ("Metni temizle"/"Clear the text"). network/ServerDialog.kt `FormField` and sleep/AlarmSettings.kt `TimePart` use it
  (looks identical to 1.0.2: verified network-add-dex-dark "same").
- States.kt: `Modifier.swipeToDismiss(key, onDismiss)` (touch only; `SwipeDismissRules`: CarryOut on Gesture, Dismiss
  wider up/reduced, Back short of NOTICE_DISMISS 0.3 / NOTICE_FLING 1200 px/s) and `recommendedTimeout(millis,
  controls)` (LocalAccessibilityManager); UndoBar and NoticeBar (not progress notices) use both. Stacking unchanged.
  Beware: ExpandablePlayer.kt has file-private SWIPE_FLING/SWIPE_THRESHOLD; package-level names clash at compile time.
- Slider.kt: `NagSlider(label, valueText)`; volume (PlayerBars VolumeControl, localized percent), equaliser's
  LabelledSlider, pitch, balance, alarm loudness pass them. GainSlider already had both.
- Choice.kt: SwatchChoice is a FlowRow; NagSwitch thumb `motion.fastSpatial(PIXEL as Dp)`. After Part 2a merged, every
  spring in Part 3's files has its unit's threshold: pressScale + PressRelease and the slider's thumb FRACTION, the
  slider's track Dp.VisibilityThreshold, the nav pill FRACTION, swiped notices `NagMotion.gesture(PIXEL)`.
  NavigationChrome: KeyCap border `colors.border`, SearchField `borderStrong` outline unless focused (13 light
  references updated in "Screenshots: …" ac4e1391).
- Selection.kt: `ClickModifiers` records the press's pointer (`takeFinger()`; a key forgets it via
  TrackClickModifiers' onPreviewKeyEvent); `ListClick.of(selecting, finger, touchLayout)` (Toggle/Select/Open);
  `ClearSelectionOnBack(selection, touchLayout)` + `SelectionRules.backClears(count, selecting, touch)`. MusicScreen
  TrackPane and PlaylistsScreen use them (DenseTable unchanged: the rule lives in the screens' onRowClick).
- Keyboard behind layers: components/ModalLayer.kt `Modifier.coveredByLayer(covered)` = CoveredNode (a
  FocusPropertiesModifierNode: `onEnter = cancelFocusChange()` while covered, invalidateFocusProperties on change;
  clears the focus that was inside when it becomes covered; registers with `ScreenCommands.cover/uncover`) + focusGroup.
  NagmedarShell's `underPlayer()` and the bottom bar use it (2 lines). `blockInputBelow()` also covers the screen's
  commands while composed (QueuePage, WindowPlayer, lyrics, visualiser, lyrics editor). `ScreenCommands.selectAll/
  escape/up/refresh/toggleInspector` return false while `covered`.
- Tests: components/ComponentRulesTest (pure), components/ComponentsBehaviourTest (frame by frame: colours, press,
  haptics, undo-bar carry-out, slider/field semantics), components/CoveredFocusTest, input/ContextMenuDismissTest
  (window-level injection via ShadowUiAutomation, as the kit's).
- Screenshot references on master were already stale vs master's own run (TV Lab entry, a disabled button's border):
  compare a PR's PNGs with origin/ci/screenshots (master's run), not the committed references.

## 2.3 port — Part 4 (tablet and DeX) (story/tablet-dex, PR #107) - NOTES FOR THE NEXT STORY
- #20 input/InputModeTracker.kt: `PointerDevices(ids, isPrecise)` (internal, pure): added/removed/changed → InputMode? (null = no
  change); only switched-on devices count (`InputDevice.isEnabled`), the cover's layout/key-map changes and keyboards say nothing.
  The listener now handles onInputDeviceChanged. Test: input/PointerDevicesTest.
- #16 input/DragSource.kt: `fileDragSource` keeps the URI design (FileProvider, NAGMEDAR_DRAG, window drop targets) but starts via
  internal `AwaitPointerEventScope.awaitHeldDrag()`: Mouse (touchpad = Mouse in Compose) / Stylus / Eraser, primary button,
  rest within touch slop for longPressTimeoutMillis, then move past slop (that move consumed: the row's click cancels). Fingers
  never (long press = menu). Shadow = `DragCardShadow(DragCard)`: Compose TextMeasurer + CanvasDrawScope onto the View shadow
  canvas; label (bodyStrong, ≤ 240 dp, ellipsis) on surfaceRaised with border + soft shadow, count badge (≥ 2 files, "99+")
  in accent on the end corner (RTL aware); pointer under the card's middle, 14 dp below it. Test: input/DragStartTest (timing
  through performMouseInput + mainClock, card geometry/accent pixels, pictures drag-card-{one,many}-{dark,light}).
  NOT done: NavDrop (drop tracks on "Çalma listeleri"): the window drop targets (input/Drops.kt `dropTarget(key, accepts,
  shape) { files -> }`) can take it: a sidebar entry target for own drags (`NAGMEDAR_DRAG`) whose onDrop maps the URIs back
  to tracks and opens the add-to-playlist window. Optional Ctrl+Enter in the palette (#14) not done either.
- #17 shell/CaptionBar.kt: `CaptionBar.showsAppChip(direction)` (system room at the bar's start: left LTR, right RTL) and
  `@Composable CaptionBar?.showsAppChip()`; MenuBarChrome hides BrandMark + "Nağmedar" then. rememberCaptionBar() already
  measured the chip as `left` (bounding rects at the start), so no platform change. Test: CaptionBarTest (+ caption-dex-chip-light).
- #67 NavigationChrome.kt sidebar footer: `badge = badges[Destination.Settings]` (nothing provides one today; NagmedarApp's
  counts only has Music). Test: shell/SidebarBadgeTest.
- Inspector ReplayGain: AudioTechEntry + track_gain_db/track_peak/album_gain_db/album_peak (REAL, null) — DATABASE 24
  (MIGRATION_23_24 ALTER TABLE ×4; schema 24.json committed; NagmedarDatabaseMigrationTest
  aTwentyThreeKeepsItsProbesAndGainsTheReplayGainColumns). TrackTechIndex.PROBE_VERSION 2 (REPLAY_GAIN_SINCE 2): every file
  re-probed once in the background; `toTech()` accepts rows of 1..PROBE_VERSION (table stays filled, TrackTech unchanged so the
  music list is not rebuilt); gains live in their own `TrackTechIndex.gains` StateFlow (`AudioTechEntry.gains()`), and
  `readGains(context, fileUri)` probes the inspected file at once (IO). music/MusicLibrary.kt `ReplayGainTags`;
  MusicInspector `TrackTags(..., replayGain)`, `TrackInspector(replayGain =)`, `replayGainLines(tags, locale, none)` ("−6,6 dB",
  "0,988 · −0,1 dBFS", "—" = nag_inspector_none, translatable=false). Test: music/ReplayGainTagsTest (+ inspector-replaygain-*).
  MusicSamples.gains: Itrî tagged, Gece Yolcusu read without tags.
- Gotchas met: every Compose test needs @GraphicsMode(NATIVE) (ComposeSandboxTest); `Int * Dp` does not compile (write
  `14.dp * 2`); screenshot_report's "same" allows < 0.1 % changed pixels (> 24 levels): a new text row at the bottom of
  music-dex-* stays "same", so changes there need their own screenshot.

## 2.3 port — Part 2b-1 (shell chrome) (story/shell-chrome, PR #105) - NOTES FOR THE NEXT STORY
- Root: `shell/SystemBand.kt` `WindowRoot(shell, belowCaption)` pads `safeDrawing` (minus the caption bar when the app draws
  in it); with `shell` it leaves the bottom navigation bar (`navigationBars.bottom - ime`) to the shell and provides
  `LocalWindowRootInsets` (`padding`, `navigationBottom`). Decided in composition (no first-launch gap). The first run
  (`prefs.firstRunDone == false`) is padded as before. NagmedarApp uses it; tests use it (SystemBandTest).
- Shell: `NagmedarShell` reads `systemBottom` from `LocalWindowRootInsets` and hands it to `ShellLayout(systemBottom, systemBand)`,
  `ShellChromeMetrics.systemBottomPx` (in `bottomPadPx(bars)` on a phone only, full pages too) and
  `ShellOverlayHost.systemBottomPx`. `SystemBand(navKind, hidden, onGlass)`: phone = `colors.chrome` + a 1 dp `border` rule
  as far as the bars slid away; Rail/Sidebar = `colors.chrome`. No band while the keyboard is up (navigationBottom minus ime = 0).
- ShellLayout: slots placed in reading order with zIndex for drawing (content 0, glass 0.5, chrome 1, band 2, playerLayer 3,
  screenOverlay 4, overlay 5) + `readingOrder()` traversal groups (top 0, content 1, player 2, phone nav 3 / rail 0.5, then
  overlays). The bars stop above the band; `slideDown` cuts them where the band begins. The screen overlay between the bars
  consumes `BottomInsets` (the bars + band below it), so a page in it that calls `navigationBarsPadding()` pads only when it
  reaches the window's bottom (WindowPage). The playerLayer/overlay reach the window's bottom (behind the buttons).
- What keeps its controls above the buttons: GlassBottomSheet's SheetSurface (`bottomInsetPadding(host.systemBottomPx)`, new
  public helper in ShellPadding.kt), the phone player (PhonePlayer's own `navigationBarsPadding`, backdrop insets from
  `LocalWindowRootInsets.padding`), QueuePage (`background + navigationBarsPadding` on the panel), lyrics/visualiser/editor
  (their own `navigationBarsPadding`, full window), notices column (`navigationBarsPadding`), CastPicker sheet (unchanged).
- theme/SystemBars.kt: transparent nav bar scrims, `isNavigationBarContrastEnforced = false`, `isStatusBarContrastEnforced = false`.
- BottomNavBar: `height(IntrinsicSize.Min)` + items `heightIn(min = bottomBarHeight)`; `onGlass` drops its surface.
- Glass decision (#62), OFF by default: `shell/ChromeGlass.kt` `ChromeGlass {Solid, Bottom, Kit}`, `DebugChrome.glass` set only by a
  debuggable build's intent extra `app.nagmedar.debug.CHROME_GLASS` (solid|bottom|kit or a|b|c; NagmedarActivity onCreate and
  onNewIntent). NagmedarShell(chromeGlass): Bottom/Kit draw `ShellLayout(bottomGlass)` (one `NagGlass.chrome` surface from the
  mini player's top to the window bottom, placed with `offset {}` by the bars' offset), `LocalOnBottomGlass` makes
  `floatingPlayerCard()` and BottomNavBar transparent; Kit also gives `PageTopBar(glass, scrolled)` (alpha-only fade to glass +
  rule once `ShellScrollState.scrolled`). The content is a Haze source while chromeMode != Solid. Haze 2.0.1 quirk: switching
  the mode at run time over a page that does not redraw shows the bottom glass unblurred until the page draws again.
- Walk: `three_button_navigation()` (cmd overlay enable-exclusive …navbar.threebutton) at the start of phone-en and glass;
  `band_check(nodes, what)` (pixel between the first two tabs == pixel behind the buttons). On-demand pass `glass`
  (debug build): album folders with cover.jpg + an M3U each (`glass_media`), A/B/C × dark/light × Music/Playlists/Search, top
  and scrolled; `glass/README.md` + PNGs on ci/emulator-glass. Dispatch: `-f inputs[passes]=glass`.
- Tests: shell/SystemBandTest (band colour, padding incl. buttons, unpadded screens, keyboard, tablet chrome, first run,
  first launch, theme buttons, reading order, 4 screenshot refs), shell/ChromeGlassTest. References: compact-{dark,light,
  large-text}-three-button-navigation, medium-dark-three-button-navigation; player-bar-phone-narrow-large-light changed
  (bar grows with 200 % text).
- Robolectric dispatches no navigation-bar insets by default: the existing 269 references did not move; only tests that
  dispatch insets (SystemBandTest) show the band.
- Part 3 final: PR #103, HEAD f0e90c66 (master ff7ad1d0 merged); CI green; walks 42095640470 (debug) and 42095653654
  (personal), API 35 + 37.0, tablet + phone: 8/8 passed. Left for after Part 2b: the sheet's close-threshold haptic,
  #31 (switch rows in sheets), #63 (focus return), and the kit's NumberField (the app has no K1 number field: the two
  ad-hoc fields now use NagTextField).
- Final state (6 Nov 00:50): PR #105 HEAD 3c7d7524 (master f0e90c66 merged), debug + release green, screenshots 274 same.
  Walks of 3c7d7524 all green: debug API 35/37.0 and personal API 35/37.0, phone (three-button navigation on, band check
  ok) and tablet. Glass screenshots (run 42105231110 of d0f3e764, ci/emulator-glass): 36 PNGs + README copied to
  $SCRATCH/agents/shell-chrome/glass/. The user decides A/B/C; nothing to change in the product until he does. If he picks
  B or C: make `ChromeGlass` the shell's default (NagmedarApp `chromeGlass =`), keep it Solid under battery saver / reduced
  motion (chromeMode already follows rememberSystemGlassMode), and drop DebugChrome's extra or keep it debug-only.
- Device checks (Part 2b-1): S24 three-button: bar colour behind Back/Home/Recents, no strip (light, dark); bars hidden on
  scroll leave the band with a hairline; Settings' last row above the buttons; keyboard up: no band, mini player on the
  keyboard; full player, Sıradaki, lyrics, visualiser, ⋯ sheets reach behind the buttons, controls above them; first launch
  without a gap; buttons dark on light theme; Tab S11: chrome colour behind the taskbar/buttons; 200 % text: bar grows,
  labels end in "…"; TalkBack: top bar → content → mini player → bottom bar.
