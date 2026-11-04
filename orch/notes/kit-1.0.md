# Desktop Kit 1.0 — porting notes for Nağmedar

PR: hesaplarimdijital-coder/desktop-kit#2 (draft, branch `kit-1.0` → `main`), head SHA: 6d0c714d03bb772e4be391e361d29ca9a27aa6af, CI: green (build success; screenshots 34 same, 0 new, 0 changed).
Version 1.0.0 (gradle.properties `VERSION_NAME=1.0.0`; README badge/install lines; CHANGELOG `## 1.0.0 (2026-10-29)`).

## 0. Dependencies the app must add

| Artifact | Version | Why |
| --- | --- | --- |
| `dev.chrisbanes.haze:haze` | **1.5.4** | blur (`hazeSource` / `hazeEffect`, current names in 1.5.x; `haze`/`hazeChild` are the deprecated ones) |
| `dev.chrisbanes.haze:haze-materials` | 1.5.4 | kit adds it as `api` (requested); kit's own materials live in `DeskGlass` |
| `androidx.activity:activity-compose` (+ `activity`) | 1.10.1 | Haze 1.5.4 depends on `androidx.activity:activity:1.10.1`; requires `compileSdk 35` |

Why 1.5.4: Haze tags were checked in `gradle/libs.versions.toml` per tag. 1.0.0 → 1.5.4 build on Compose Multiplatform 1.7.x
(Jetpack Compose 1.7.x; 1.5.4 = CMP 1.7.3 ≈ Compose 1.7.6) and Kotlin 2.0.21 → 2.1.0. 1.6.0 moves to CMP 1.8.0 (Compose 1.8.0)
and 1.7.x to Compose 1.9 / Kotlin 2.2+, which would force Compose past the BOM 2024.12.01 (1.7.6) both repos use. CI resolved
and built 1.5.4 with BOM 2024.12.01 / Kotlin 2.1.0 / AGP 8.7.3; the app's Kotlin 2.2.10 reads 2.1 metadata fine.

## 1. Files to port (source of truth: `kit/src/main/java/desktopkit/**`)

New: `theme/Glass.kt`, `theme/ViewAnimators.kt`, `shell/ShellPadding.kt`, `components/Sheets.kt`,
`components/ExpandablePlayer.kt`, `components/Dialogs.kt`, `components/Inspector.kt`.
Changed: `theme/Motion.kt`, `theme/Theme.kt`, `DesktopKitRoot.kt`, `shell/DesktopShell.kt`, `shell/ShellLayout.kt`,
`shell/ShellScroll.kt`, `shell/ShellSpec.kt`, `shell/NavigationChrome.kt`, `shell/DetailTransition.kt`,
`components/DenseTable.kt`, `components/Menus.kt`, `settings/DeskPreferences.kt`, `res/values/strings.xml`,
`res/values-tr/strings.xml` (new `dk_player*`, `dk_columns*`, `dk_inspector*`, `dk_resize_inspector`, `dk_key_esc`).
Tests worth porting with them: `theme/GlassTest`, `theme/MotionTierTest`, `components/RulesTest`,
`components/PlayerAndSheetTest`, `shell/ShellPaddingTest`, additions in `components/DenseTableSelectionTest`,
`screenshots/KitScreenshotTest`, `TestFixtures.kt` (TestShell now pins `LocalGlassMode` to Translucent).
Everything 0.2.0 still compiles unchanged (only defaulted parameters were added).

## 2. Public API added / changed

Theme / glass (`desktopkit.theme`)
- `enum GlassThickness { UltraThin, Thin, Regular }`, `enum GlassMode { Blur, Translucent, Solid }`
- `data class GlassMaterial(tint, blurRadius, noise, translucent, solid)`
- `object DeskGlass { chrome(colors, thickness = Regular); raised(colors, thickness = Regular); material(colors, base, thickness);
  tintAlpha(dark, oled, thickness); composite(material, mode, behind); modeFor(sdk, powerSave, reducedMotion, hardwareAccelerated);
  val source: HazeState? @Composable; val mode: GlassMode @Composable }`
- `val LocalGlassSource: ProvidableCompositionLocal<HazeState?>`, `val LocalGlassMode: ProvidableCompositionLocal<GlassMode?>`
- `@Composable fun rememberSystemGlassMode(): GlassMode`
- `fun Modifier.glassSource(state: HazeState?, mode: GlassMode): Modifier`
- `@Composable fun Modifier.glass(material, shape = RectangleShape, source = LocalGlassSource.current, mode = DeskGlass.mode, enabled = true, rest = Color.Transparent)`
- `DesktopKitTheme(…, colorChangeMillis: Int = 150, content)`; `fun lerpColors(a: DeskColors, b: DeskColors, t: Float): DeskColors`
- `DesktopKitRoot(prefs, …, accent: Accent = prefs.accent, content)` (accent override, not saved)

Motion (`desktopkit.theme`)
- `enum MotionTier { Gesture, Timed, Reduced }`, `val LocalMotionTier` (DesktopShell and DesktopKitRoot provide it)
- `DeskMotion.gesture(): SpringSpec<T>` (NoBouncy, StiffnessMediumLow); `DeskMotion.tier(widthClass, reduced = false)`;
  `DeskMotion.spatial(tier, timed)`; `DeskMotion.fade(tier, spec)`; `DeskMotion.COLOR_CHANGE_MS = 150`
- `@Composable fun rememberReducedMotion(): Boolean`; `object SystemMotion { fun reduced(context): Boolean }`
- `class ViewAnimators { fun start(animator); fun track(animator); fun endAll(); companion { fun bind(view: View); fun endAtFinalValue(animator) } }`,
  `@Composable fun rememberViewAnimators(): ViewAnimators`

Shell (`desktopkit.shell`)
- `DesktopShell(…, moreActions: List<ActionGroup> = emptyList(), floatingPlayer: Boolean = false, glass: Boolean = true, content)`
- `ShellSpec(…, startRoute, moreStyle: MoreStyle = MoreStyle.Screen)`; `enum MoreStyle { Screen, Sheet }`
- `ShellLayout(…, navigationOffset, overlay = {}, floatingPlayer = false, floatingMargin = 12.dp, floatingMaxWidth = 760.dp)`
  (navigationOffset is now read in the draw phase as a layer translation)
- `@Composable fun rememberShellContentPadding(): PaddingValues`; `@Composable fun Modifier.shellContentPadding()`;
  `val LocalShellContentPadding`; `operator fun PaddingValues.plus(other: PaddingValues): PaddingValues`
- `BottomNavBar(spec, currentRoute, onSelect, modifier, hasMoreActions = false)`; `PageTopBar(…, scrolled, glass = false)`

Components (`desktopkit.components`)
- `enum PlayerValue { Collapsed, Expanded }`; `class ExpandablePlayerState(initialValue, density) { currentValue; targetValue; isExpanded; progress; suspend expand(); suspend collapse(); suspend snapTo(value) }`;
  `@Composable fun rememberExpandablePlayerState(initialValue = Collapsed)`
- `@Composable fun ExpandablePlayer(state, mini, full, modifier = Modifier, onPrevious: (() -> Unit)? = null, onNext: (() -> Unit)? = null, miniHeight = dimens.miniPlayerHeight)`
- `enum MiniSwipeAction { None, Previous, Next }`; `object MiniSwipe { decide(offset, velocity, threshold, flingVelocity, canPrevious, canNext, rtl = false); resist(raw, canPrevious, canNext, rtl = false) }`
- `data class ActionGroup(title: String?, actions: List<MenuEntry.Item>)`; `fun List<ActionGroup>.toMenuEntries()`
- `@Composable fun MoreSheet(visible, onDismiss, groups, modifier, title = null, menuOffset = IntOffset.Zero)`;
  `@Composable fun MoreButton(groups: () -> List<ActionGroup>, modifier, title = null, contentDescription)`;
  `@Composable fun GlassBottomSheet(visible, onDismiss, modifier, title = null, content: ColumnScope.() -> Unit)`
- `class InspectorState(initialWidthDp, initialCollapsed = false, initialTab = 0, onCommit) { widthDp; collapsed; selectedTab; resizeBy(); commit(); updateCollapsed(); toggle() }`;
  `@Composable fun rememberInspectorState(prefs: DeskPreferences? = null, defaultWidth = 320.dp)`;
  `@Composable fun WorkstationLayout(state, main, inspector, modifier, minMain = 360.dp, minInspector = 240.dp, maxInspector = 560.dp, showFrom = WidthClass.Expanded)`;
  `data class InspectorTab(id, label, icon = null)`; `@Composable fun InspectorPane(tabs, selected, onSelect, modifier, onCollapse = null, content: (InspectorTab) -> Unit)`;
  `@Composable fun InspectorToggleButton(state, modifier)`
- `DenseTable(…, toggleOnClick, columnsState: TableColumnsState? = null, glassHeader: Boolean = true)`; `TableColumn(…, hideBelow, id: String = title, value)`;
  `class TableColumnsState(initialOrder = emptyList(), initialHidden = emptySet(), onChange) { order; hidden; arrange(columns); toggle(id, ids); move(id, delta, ids); reset() }`;
  `@Composable fun rememberTableColumnsState(key: String, prefs: DeskPreferences? = null)`; Esc clears a multi-selection.
- `enum DismissStyle { EscKey, CloseButton }`; `class DialogDismiss(style, onDismiss)`; `val LocalDialogDismiss`;
  `fun dismissStyleFor(configuration): DismissStyle`; `@Composable fun DialogDismissButton(modifier)`;
  `@Composable fun DeskDialog(onDismiss, title, modifier, maxWidth = 560.dp, actions = {}, content)`;
  `@Composable fun DialogFrame(onDismiss, title, modifier, maxWidth, actions, dismissStyle = dismissStyleFor(LocalConfiguration.current), content)`

Settings: `DeskPreferences.inspectorWidth`, `inspectorCollapsed`, `updateInspectorWidth()`, `updateInspectorCollapsed()`,
`tableColumns(key)`, `updateTableColumns(key, order, hidden)`.

## 3. What the app must do, part by part

1. **Glass.** Nothing for the shell's own surfaces (bottom bar, compact top bar once scrolled, player strip, sheets,
   menus, table header, floating player): they switch on with the kit. Strips the app puts in `bottomBar` must be
   transparent now (no `background(colors.chrome)`): on the phone `ExpandablePlayer` draws the glass of the mini player;
   on tablet/DeX with `floatingPlayer = true` the shell draws the rounded glass bar. A custom glass surface:
   `Modifier.glass(DeskGlass.chrome(DeskTheme.colors, GlassThickness.Thin))`. Do not add more blurred surfaces (GPU pass
   each, per frame; phone already has up to three: top bar, mini strip, bottom bar). **Caveat:** a Haze source cannot be
   drawn into a software canvas (`View.draw(Canvas(bitmap))` → `IllegalArgumentException: Software rendering doesn't support
   drawRenderNode`). If Nağmedar ever renders the window/compose tree into a bitmap (share card, thumbnails), provide
   `LocalGlassMode provides GlassMode.Translucent` for that subtree or use `PixelCopy`. Robolectric is detected
   (`Build.FINGERPRINT == "robolectric"`) and gets Translucent automatically; app screenshot tests keep working.
   Fallbacks are automatic: API < 31 → translucent tint; battery saver or animator scale 0 → opaque.
2. **Draw-phase bars.** On the phone every screen that scrolls should lay out under the bars:
   `val padding = rememberShellContentPadding()` and `LazyColumn(contentPadding = padding + PaddingValues(…))`
   (for `DenseTable`: `contentPadding = padding + PaddingValues(bottom = 8.dp)`; the table adds its header height itself).
   A screen that does not call it is padded by the shell like 0.2.0 (and only such screens re-measure while bars move),
   so porting can be screen by screen. Non-scrolling screens: `Modifier.shellContentPadding()` or leave as is. Do not
   add `imePadding()` (root already applies safeDrawing). `PageHeader` at the top of a list is fine inside the list.
3. **Motion.** Shell, DetailTransition, sheets, player follow the tier automatically. App animations that move things:
   `DeskMotion.spatial(LocalMotionTier.current, tween(…))`; fades `DeskMotion.fade(tier, tween(…))`. View code with
   `ValueAnimator`s (VLC fork: seek bar / waveform / cover animators): `val animators = ViewAnimators.bind(view)` once per
   view, then `animators.start(ValueAnimator.ofFloat(…))` — ended at their final value on detach and on every
   configuration change; instantly ended with reduced motion. Compose side: `rememberViewAnimators()`.
4. **ExpandablePlayer (phone).** In `DesktopShell(bottomBar = { kind -> … })`:
   `if (kind == NavKind.BottomBar) ExpandablePlayer(state = rememberExpandablePlayerState(), mini = { MiniPlayer() }, full = { FullPlayer() }, onPrevious = …, onNext = …) else TransportBar()`.
   The mini content has no background, fills `miniHeight` (default `dimens.miniPlayerHeight`, 72 dp touch); the full
   content draws its own background (artwork) and a collapse button `scope.launch { state.collapse() }`. Back and the
   predictive Back gesture collapse it (no app BackHandler needed). Sideways swipe on the mini: towards the start edge =
   `onNext`, other way = `onPrevious` (haptic tick at the threshold). Replace the VLC `BottomSheetBehavior` audio player
   on phones. Works only inside `DesktopShell` (it draws in the shell's overlay layer).
5. **"⋯" (MoreSheet).** Per-item actions: `MoreButton(groups = { listOf(ActionGroup(null, listOf(MenuEntry.Item("Sıradaki", ::playNext, icon = DeskIcons.Play))), …) }, title = track.title)`
   — a glass sheet on phones, a menu on wide windows; or drive `MoreSheet(visible, onDismiss, groups)` yourself.
6. **3 + ⋯ bottom bar.** `ShellSpec(sections = …, bottomBar = listOf("music", "podcasts", "radio"), moreStyle = MoreStyle.Sheet)`
   and `DesktopShell(moreActions = listOf(ActionGroup("Oynatma", listOf(MenuEntry.Item("Ekolayzır", …), MenuEntry.Item("Uyku zamanlayıcısı", …)))))`.
   The sheet lists the other screens grouped by NavSection title (footer last), then `moreActions`. Rail keeps the More screen.
7. **Workstation (tablet/DeX).** In the library screen: `val inspector = rememberInspectorState(prefs)`;
   `WorkstationLayout(inspector, main = { Library() }, inspector = { InspectorPane(tabs, inspector.selectedTab, { inspector.selectedTab = it }, onCollapse = inspector::toggle) { tab -> when (tab.id) { "tags" -> …; "signal" -> …; "lyrics" -> …; "queue" -> … } } })`
   with tabs `InspectorTab("tags", "Etiketler ve spektrum")`, `("signal", "Sinyal yolu")`, `("lyrics", "Sözler")`, `("queue", "Sıra")`.
   `InspectorToggleButton(inspector)` in the page header actions. Below `WidthClass.Expanded` only `main` shows.
   `DesktopShell(floatingPlayer = true)` for the floating glass transport bar; the bar's content transparent.
8. **DenseTable.** Give columns stable ids (`TableColumn("Süre", ColumnWidth.Fixed(64.dp), mono = true, id = "length") { … }`),
   pass `columnsState = rememberTableColumnsState("tracks", prefs)`. Multi-select rules unchanged from 0.1.2 plus Esc.
   Header is glass when rows pass under it (`glassHeader = false` to opt out). Long press / right click on a selected
   row keeps the selection for `rowMenu` (unchanged).
9. **Dialogs.** Use `DeskDialog(onDismiss, title, actions = { DeskButton(…) }) { … }`; custom dialog frames call
   `DialogDismissButton()` (reads `LocalDialogDismiss`; provide `DialogDismiss(dismissStyleFor(LocalConfiguration.current), onDismiss)`).
10. **Artwork accent.** `DesktopKitRoot(prefs, accent = if (followArtwork) Accent.custom("Kapak", paletteColor) else prefs.accent)`;
    changes cross-fade in 150 ms (snap with reduced motion); glass tint follows. `Accent.custom` fixes contrast.

## 4. Behaviour changes to check after porting

- Phone: content now spans the window and bars draw over it (glass). Legacy screens look as before.
- Phone screen transitions: the 96 % scale-in is a spring; the fades keep 90/210 ms. Medium+ unchanged.
- Menus/context menus inside the shell are glass (shadow only when solid). Disabled menu rows are transparent.
- The hide-on-scroll settle is a spring on phones (velocity from what the list could not consume); half-way rule kept.
- `activity-compose` 1.10.1 comes in transitively.

## 5. Known limitations

- Blur is not visible in Robolectric screenshots (software renderer): references show the translucent form.
- `ExpandablePlayer` needs `DesktopShell` (overlay layer); outside it only the mini strip shows.
- Glass in pop-up windows (menus) is drawn once when the menu opens; content changing behind an open menu is not re-blurred.
- In keyboard (non-touch) mode Compose may hand initial focus to the first `focusable()` element (e.g. a slider in the full player).

## 6. Repo state

- Branch history contains WIP commits (CI used as compiler; force-push was not permitted), squash merge recommended.
  A topic-split version of the same tree exists only locally in $KIT as branch `kit-1.0-topics`.
- No tags/releases created. README install line already says `desktop-kit:1.0.0` for when the tag is pushed.
