# Desktop Kit 0.2.0 vs Nağmedar — comparison and plan

Kit: hesaplarimdijital-coder/desktop-kit d3e8872 (0.2.0), plus the published design-system export
(tokens.json, README, 19 component cards). App: master 37cb9153, `application/nagmedar-ui`.
Branch: story/desktop-kit (worktree $WT/desktop-kit).
Verdicts: **adopt** / **adopt adapted** / **keep the app's** (reason) / **n/a**.

---------------------------------------------------------------------------------------------------
## 0. Motion first — why the app "feels slow" (the user's report of 23 Oct)

### 0.1 What makes it feel slow (it is mostly not the durations)

The app has very little motion (≈ 30 animation sites), and its tokens are short (120/200/300 ms).
What the user sees as "slow" is what happens *around* a screen change:

1. **Every tab switch shows a loading screen first.** Home, Filmler, Diziler, Videolar, Müzik and
   Arama keep their data in `produceState(null)`; navigation disposes a screen when it is left, so
   coming back starts from `null`: the incoming screen fades in as a skeleton + "Kitaplık
   yükleniyor" bar, then the content replaces it a few frames later (one or more medialibrary
   round trips). Fade → skeleton → pop, on every switch. **Fix:** stale-while-revalidate — the
   last content of each screen is kept in memory (scoped to the `MediaRepository` of the window,
   so tests and other windows do not share it) and is on screen in the first frame; the reload
   runs behind it and replaces it only if something changed.
2. **Images pop in after the screen.** Covers, posters and thumbnails always go through
   `withContext(IO)` even when they sit in the in-memory LRU, so every visible tile shows its
   placeholder for at least one frame after the screen appears. **Fix:** a synchronous memory hit
   gives the first frame its images (CoverCenter, FilmImages, the medialibrary thumbnails).
3. **Film information and technical badges arrive a frame late.** `rememberTech` and
   `rememberFilmInfo` collect Room flows with `initial = emptyMap()`: each visit draws the films
   once with file names, no posters and "probing" marks, then again with the real data.
   **Fix:** both flows are shared per process as `StateFlow`s that keep their last value, so a
   screen starts with what was last read.
4. **The music view rebuilds its whole grouping on the main thread.** `library.groups(view,
   filter)` (group + Collator sort of every track, ~100 ms for a few thousand tracks) runs in
   composition, and again for every batch the engine's probe adds to `TrackTechIndex.tech` —
   repeated main-thread stalls while the library is being probed. **Fix:** after the first
   build, updates are computed on `Dispatchers.Default` while the previous grouping stays on
   screen; the first build stays synchronous (deterministic screenshots).
5. **The screen change itself is a muddy cross-fade.** `NavHost` runs fade-in 200 ms and
   fade-out 120 ms *at the same time* in every layout, so for 120 ms two half-transparent screens
   overlap. **Fix:** the kit's fade-through (old screen out in 90 ms, new one in over 210 ms from
   96 % on a phone / 98 % on a tablet, emphasized-decelerate) and a still 150 ms cross-fade on DeX.
6. **State changes jump.** Hover, press and selection colours change in 0 ms (S-203 "instant"),
   menus and tooltips pop, the sidebar's collapse jumps, the slider's thumb pops. With nothing
   easing, the eye reads the late content (1–4) as lag. **Fix:** the kit's 150 ms colour easing,
   menus that grow from their corner, tooltips that fade in, springs for the nav pill, press scale
   (touch only) and the slider thumb.

Smaller finds: the phone list → detail has fade + 24 dp slide but no predictive Back; the snapshot
flash reads its alpha in composition (recomposes the box every frame → drawn in the layer now);
three `animateScrollToItem` calls ignore "Remove animations" (CueEditorViews, MusicScreen,
RadioViews); one row (CommandPalette) still draws a ripple.

Not found: no `runBlocking`, no image decoding on the main thread (all `BitmapFactory` calls are
on IO), no blur/RenderEffect; lists have keys; the large-surface shadows are dialogs (24 dp) and
floating cards (10–12 dp) only. The detail pane already animates in the layout phase (no
recomposition per frame) — kept.

### 0.2 Every animation site (before → after; all specs come from `NagTheme.motion` / `NagMotion`)

| Site | Before | After |
|---|---|---|
| shell/NagmedarShell — NavHost screen change | fadeIn 200 ms (0,0,0,1) ∥ fadeOut 120 ms (0.3,0,1,1), every layout | phone: fade-through 90 out (emph-accel) → 210 in after 90 (emph-decel) from 96 %; tablet same from 98 %; DeX: 150 ms cross-fade (standard), no movement; reduced: none |
| shell/Panes ListDetailPanes, side pane (≥ Expanded) | open 300 ms standard, close 200 ms standard | open 300 ms emphasized, close 200 ms emphasized-accelerate |
| shell/Panes, compact detail | fade + 24 dp slide, 200 in / 120 out, list hidden at once | shared axis X (kit DetailTransition): detail 1/10 width + fade, list −1/10 + fade, 300 ms; predictive Back (scale → 90 %, 28 dp corners, 24 dp lean, `pressed` shade behind) |
| podcast page → episode (phone) | instant swap | same DetailTransition |
| onboarding/FirstRun step | fade + 24 dp slide, 200 in / 120 out | shared axis X: fade 90 out / 210 in (after 90), slide 300 ms emphasized |
| onboarding/FirstRun step dots colour | 200 ms standard | 150 ms (colour token) |
| components/Reorder neighbours | tween 150 (FastOutSlowIn) while dragging | fast spatial spring (0.9 / 1400) |
| components/Swipe remove / snap back | default spring both ways | out 200 ms emphasized-accelerate; back: spatial spring (0.85 / 700) |
| components/States progress sweep, skeleton shimmer | loops 1200 / 1400 ms linear | kept (work-in-progress loops; still with "Remove animations") |
| library/metadata/FilmViews poster lift (hover) | default springs (1.03, shadow 10 dp) | fast spatial (scale), effects spring (shadow) — keep the lift (S-801 design) |
| playback/ProgressTimeline thumb / track grow | 120 ms standard | fast spatial spring (kit slider) |
| playback/ProgressTimeline transport press | 120 ms, 0.86 / 0.92, mouse too | press scale, touch only, fast spatial spring |
| playback/ProgressTimeline play ↔ pause icon | Crossfade 120 ms standard | Crossfade 150 ms (colour token) |
| playback/ProgressTimeline glide | pixel steps, one per frame | kept (position tracking, not a transition) |
| music/PhonePlayer artwork scale + lift (play/pause) | 300 ms standard | 300 ms emphasized |
| music/PhonePlayer swipe / pull-down release | default spring | spatial spring |
| player/snapshot/SnapshotCard flash | 260 ms (FastOutSlowIn), alpha read in composition | 200 ms standard-decelerate, drawn in the layer |
| player/PlayerSheets lock hint, player/PlayerOsd card | fade 200 in / 120 out | fade 200 in / 100 out (enter/exit tokens) |
| player/PlayerOsd loading spinner | loop 900 ms | kept |
| player/DesktopControls chrome | fadeIn 120 / fadeOut 220 ms (FastOutSlowIn), not reduced-aware | 150 in standard-decelerate / 200 out standard-accelerate, reduced-aware |
| player/TouchControls chrome (×4) | fade 200 / 120 | same tokens as DesktopControls |
| lists' animateScrollToItem (lyrics, editor, transcript, settings jumps) | Compose spring, snaps when reduced | kept; CueEditor, Music jump, Radio scroll now also snap when reduced |
| components/Tooltip | appears at once after the delay | fades + grows from 94 % (100 / 150 ms); still mouse only, delay setting kept |
| components/Menus MenuPopup | appears / vanishes at once | grows from its corner 92 % → 100 % + fade 150 ms; fades out 100 ms (no longer focusable while it fades) |
| buttons, icon buttons, segments, chips, switches, checkboxes, menu rows, nav items | colours jump (0 ms) | 150 ms standard; press scale on touch only (button 0.96, icon 0.92, segment 0.96, chip 0.96, nav icon 0.94) |
| shell bottom bar / rail pill | colour jump | grows from the middle, spring 0.85 / 700; bottom bar's selected label 500 |
| shell sidebar collapse | jumps | width 300 ms emphasized, labels fade in after half way (not while the edge is dragged) |
| components/Slider | track height and thumb jump | track 3 → 5 dp and thumb 0 → 1 → 1.25 (drag) on the fast spring |
| phone top bar (new) | — | content scrolled: `chrome` colour + 1 dp rule, 200 ms |
| phone bars hide on scroll (new) | — | follow the finger; half way settles in 250 ms emphasized |

Durations: nothing longer than 300 ms outside the loops; the predictive-back gesture follows the
finger. Compose scales every duration by the system animator scale itself (the window recomposer's
`MotionDurationScale`); with "Animasyonları kaldır" (scale 0) `NagMotion.Reduced` also turns the
transitions into `None`/snap and stops the loops, as before.

### 0.3 Frame timings

Not measurable in CI: the macrobenchmarks (S-1404) are built for arm64 and only compiled in CI,
the emulator workflow is x86_64 with software rendering (frame times there say nothing about a
Tab S11). So the findings above come from reading the code paths of a tab switch. Device check
added instead (Ayarlar › Performans has no frame counter for the shell; the check is by eye and
with `adb shell dumpsys gfxinfo app.nagmedar framestats` if wanted).

---------------------------------------------------------------------------------------------------
## 1. (a) Tokens: kit theme files vs app theme files

**Colours (light / dark / OLED): identical** for all 22 shared tokens (background, chrome, surface,
surface-raised, hover, pressed, border, border-strong, text ×3, accent, on-accent, accent-text,
selection, on-selection, focus-ring, success, warning, danger, scrim). Preset accent tones are the
same hand-tuned values (blue #8AB0FF / #1C2A49 / #142039 / #DCE6FF, amber, green, coral; deep =
mix(base, black, 55 %), light selection = mix(base, white, 22 %)).
- kit `brandSecondary` = app `brandCoral` (same values) — app also has `brandBlue` (logo halves)
  and `inverseSurface` / `onInverseSurface` / `inverseAccent` (undo bar) → **keep the app's**
  (superset).
- kit `Accent.custom` + `ensureContrast(4.6)` vs app `Accent.System` (S-204, wallpaper seed tuned in
  OKLCH) → **keep the app's** (no brand colours to pick; S-204 is a story).

**Type: identical** sizes, weights and line-height ratios (pointer 22/18/16/13/13/12.5/11.5/11/11,
touch 26/20/17/15/15/14/13/12/12; 1.25/1.3/1.35/1.4/1.4/1.3/1.3/1.3/1.3). Kit additions:
- `DeskType.grow(step)` + shell `largeWindowTextStep = 1f` → **adopt adapted**: `NagType.grow()`
  applied in `NagmedarTheme` when the window is ≥ 600 dp (rail and sidebar layouts), so the first
  run, dialogs and the player chrome on a tablet get it too, not only the shell's content. The
  app has no pointer step (checked: no `pointerStep`/type override anywhere), so DeX gets +1, not
  +2. Hard-coded `fontSize = N.sp` copies (TV Lab, remux pages, lyrics, first run, jump dialog —
  17 sites) follow the step through `NagTheme.type.size(N)`.
- `DeskFonts` (heading family, system fonts), `pointer(step)` / `touch(step)` / `forMode(step)` →
  **n/a** (one font family, no per-mode step wanted).

**Dimens: identical** Pointer and Touch sets (all 23 values: targets 24/48, control 28/44, rows
32/56, nav 32/48, icon 16/22, large icon 20/26, menu bar 40/48, top bar 48/56, bottom bar 56/72,
rail 72/88, sidebar 240/280, collapsed 56/72, mini player 64/72, gaps 4/8/12/16/24, page 20/16).
- `forCompactWindow()` (0.2.0: bottom bar ≤ 64 dp, touch rows ≤ 52 dp, targets ≥ 48) → **adopt**
  (theme, window < 600 dp, touch density).

**Radius: identical** (small 4/6, control 6/12, card 10/16, pill 14/24, chip full pill).

**Durations** | kit | app
---|---|---
short | 100 | 120 (`SHORT`)
colour (hover/press/selection) | 150 | 0 (`INSTANT`, S-203)
medium | 200 | 200
long | 300 | 300
extra long | 400 (unused) | —
fade-through out / in | 90 / 210 | —
DeX cross-fade | 150 | — (200 / 120 cross-fade everywhere)
top-bar title fade | 150 | —
tooltip delay | 550 | 550 default, user setting 300/550/1000/off (S-404)
loops | — | shimmer 1400, sweep 1200, spinner 900

→ **adopt** the kit's durations (the user changed S-203's "instant" deliberately in 0.2.0);
**keep** the loops and the tooltip-delay setting (S-404).

**Easings** | kit | app
---|---|---
emphasized (0.2,0,0,1) | ✓ | = app `Standard`
emphasized decelerate (0.05,0.7,0.1,1) | ✓ | missing
emphasized accelerate (0.3,0,0.8,0.15) | ✓ | missing
standard (0.2,0,0,1) | ✓ | ✓
standard decelerate (0,0,0,1) | ✓ | = app `Enter`
standard accelerate (0.3,0,1,1) | ✓ | = app `Exit`
springs spatial 0.85/700, fast 0.9/1400, effects 1.0/1600 | ✓ | missing

→ **adopt**: `NagMotion` gets the six curves under the kit's names (`Enter`/`Exit` renamed),
the three springs and `color()`; specs stay reduced-aware.

---------------------------------------------------------------------------------------------------
## 2. (b) Changelog 0.1.3 and 0.2.0, one by one

0.1.3
1. Shell transitions (phone fade-through, tablet/DeX 150 ms) — **missing** → adopt (with 0.2.0's
   refinement: tablet fade-through from 98 %, DeX cross-fade).
2. Nav selection / hover colour 150 ms — **missing** → adopt.
3. `CompactTopBar` (title, 48 dp back/close, ≤ 3 icons then ⋮, system Back) — **partly**: the app's
   phone detail pages draw their own header in the content (48 dp chevron back, title ellipsised,
   icon actions); the rules hold, the top bar itself is not reused → **keep the app's for now**:
   moving those headers into the top bar restructures eight phone pages (film/series details,
   podcast page and episode — redesigned on 23 Oct —, playlists, music groups, settings
   sub-pages, folders); worth its own story.
4. `LocalWidthClass` / `currentWidthClass()` — **already** (the app's has a configuration fallback).
5. `PageTopBar(onBack, backIcon, actions)` — see 3.
6. `PageHeader` leaves no empty strip on a phone — **missing** (16 dp strip on Home, Müzik, …) →
   adopt.
7. `ArrowLeft` icon, `dk_back`/`dk_close`/`dk_more_options` — **n/a** (the app's back is the
   chevron; only needed with 3).

0.2.0
1. Text +1 sp on tablet/DeX — **missing** → adopt adapted (theme by window width, see §1).
2. Compact sizes (64 dp bar, 52 dp rows) — **missing** → adopt.
3. Bars that hide on scroll (bottom nav + mini player strip) and the top bar that takes `chrome`
   with a hairline when scrolled — **missing** → adopt (phone only; reset per screen; never while
   the keyboard is up).
4. Chips (`DeskChip`, `ChipRow`, `FilterChips`) — **partly**: the app's `FilterChip` + `ChipRow`
   always scroll, never wrap, no faded edges, check pops, 48 dp tall pill on touch → adopt
   adapted (app API kept: one line with faded edges on a phone, wrapping on wider windows; 36 dp
   pill in a 48 dp target on touch, 28 dp with a pointer; the check slides in).
5. `TableColumn(hideBelow)` — **already, better**: the film/series/video tables build their
   columns per width class (`videoColumns`: poster + title + length on a phone) and `ColumnSort`
   keeps the sort on its column when columns come and go → keep the app's.
6. Responsive `SettingRow` — **already** (control under the label below 160 dp of label room) →
   adopt adapted: the kit's rule (`min(label's own width, threshold)`), so a short label keeps a
   wide control beside it; the threshold stays the app's 160 dp (the kit's is 120 dp), so a label
   beside its control never gets less room than it had.
7. `DetailTransition` + predictive Back — **missing** → adopt adapted: built into
   `ListDetailPanes`' compact mode (the list stays composed, so its scroll and selection survive,
   unlike the kit's AnimatedContent) and used for the podcast page → episode.
   `android:enableOnBackInvokedCallback="true"` on **NagmedarActivity only** (activity-level,
   Android 14+): the classic `VideoPlayerActivity` still has two resume dialogs that catch
   `KEYCODE_BACK` in an `OnKeyListener` (they would stop working with the flag), and VLC's other
   activities use `onBackPressedDispatcher` already (no `onBackPressed` overrides anywhere).
   Inside Nağmedar, the menu bar's "Back leaves the bar" moves from the key handler to a
   `BackHandler` (the flag routes Back past key handlers).
8. Main screens fade-through / DeX 150 ms; predictive Back plays it — **missing** → adopt
   (navigation-compose 2.8.4 seeks the pop transition with the gesture).
9. Sidebar width animation, menus from their corner, tooltip fade — **missing** → adopt (sidebar:
   only on collapse/expand; the S-303 drag stays direct).
10. Nav pill spring + selected label weight — **missing** → adopt (bottom bar label 500, as the
    kit's code; rail unchanged).
11. Press scale on touch, 150 ms colours, no ripple, slider thumb spring — **missing** → adopt.
12. `DeskMotion` tokens, system scale, "Remove animations" — **partly** (the app snaps with
    reduced motion) → adopt the tokens.
13. `animatedStateBackground`, `Modifier.pressScale` — **missing** → adopt.
- From 0.1.2 (not in the task's list, found on the way): the bottom bar hides while the keyboard is
  up — **missing** → adopt; top bar / menu bar / search field are *at least* their height instead
  of fixed (large text is not clipped) — **missing** → adopt.

---------------------------------------------------------------------------------------------------
## 3. (c) Components (kit + component cards vs app `components/`, `shell/`)

| Kit | App | Verdict |
|---|---|---|
| DeskButton | NagButton (+ `fill`/`onFill`, disabled alpha) | adopt: animated colour, press scale |
| DeskIconButton | NagIconButton (+ hover tooltip with shortcut) | adopt: animated colour, press scale 0.92 |
| SegmentedChoice | same (+ `fill`, `enabled`) | adopt: animated colours, press scale |
| SwatchChoice | same (+ tooltip) | keep |
| SettingRow | same, responsive | adopt adapted (rule, §2.6) |
| DeskChip / ChipRow / FilterChips | FilterChip / ChipRow | adopt adapted (§2.4) |
| DeskSlider | NagSlider (+ marks, hover label) | adopt: springs |
| DenseTable (+ hideBelow) | DenseTable (+ content cells, double click, drag rows, ColumnSort) | keep the app's; row colours 150 ms |
| MenuPopup | MenuPopup (+ keyboard menu bar S-302, focus-first) | adopt: grow/fade |
| TooltipArea | TooltipArea + HoverTooltip (+ delay setting) | adopt: fade-in |
| PageHeader | PageHeader | adopt: no empty strip on a phone; title ellipsised |
| PageTopBar / CompactTopBar | PageTopBar (+ caption bar S-306) | adopt: heightIn, scrolled state; CompactTopBar kept for later (§2.3) |
| NavRail / BottomNavBar / Sidebar | same (+ draggable sidebar S-303, badges) | adopt: pill spring, colours, sidebar width animation |
| DetailTransition | ListDetailPanes (+ side pane, drag width) | adopt adapted (§2.7) |
| MessageScreen / OverflowScreen | StatePanel / MoreScreen | keep the app's (more states) |
| KeyCap, SearchField, MenuBarChrome | same (+ caption bar) | heightIn |
| Settings sections (Appearance, Input, Shortcut list) | richer app sections | keep |
| DeskIcons (+ArrowLeft) | NagIcons (gen_icons.py) | n/a (no new icon needed) |

---------------------------------------------------------------------------------------------------
## 4. (d) Kit README rules the app breaks

| Rule | App | Verdict |
|---|---|---|
| No ripple | one: CommandPalette rows `.clickable(onClick)` | fix |
| Press scale only on touch | the player bar's transport buttons scale with the mouse too | fix (touch only) |
| Text never below 11 sp | KeyboardMap key legends 9 sp on keys < 30 dp (phone) | keep the app's for now — the alternative is a sideways-scrolling keyboard map; flagged |
| Single-line titles end in "…", not clipped | 119 single-line `Text`s without `overflow` (PageHeader title, menu titles, chip/segment labels, badges, hints…) | fix (Ellipsis everywhere) |
| Tooltip after 550 ms, mouse only | ✓ (550 default, user setting) | keep + fade |
| Shadow only on menus (12 dp) | menus 12 ✓; dialogs 24, notices/undo 10, floating cards 12; poster hover lift 10, phone artwork lift | keep: dialogs/notices are floating surfaces the kit has none of; the poster lift (S-801) and the phone artwork (23 Oct redesign, the user's Spotify reference) are designs |
| Settings page ≤ 760 dp | ✓ (Settings, Tüm ayarlar, Guide) | — |
| Fixed-height bars clip large text | top bar, menu bar, search field use `height()` | fix (`heightIn`) |

---------------------------------------------------------------------------------------------------
## 5. Order of work on the branch

1. Theme: motion tokens; text step; compact sizes.
2. Shell: fade-through, nav pill/colours, sidebar width, hide-on-scroll + scrolled top bar,
   bottom bar under the keyboard, DetailTransition in ListDetailPanes, predictive Back.
3. Library/performance: stale-while-revalidate screens, image memory hits, shared tech/film
   flows, music grouping off the main thread.
4. Components: state colours, press scale, menus, tooltips, slider, chips, SettingRow,
   PageHeader, ellipsis, ripple.
5. Tests (pure parts), CHANGELOG, screenshots from the PR's CI.

---------------------------------------------------------------------------------------------------
## 6. Left for later (on purpose)

- `CompactTopBar` for the phone's detail pages (§2.3): eight pages would move their header into
  the top bar; its own story.
- Settings › Tüm ayarlar sub-pages and the series list → show page keep their instant swap: the
  show page's header is part of the list's scroll (the shared-axis move made it jump); both want
  the CompactTopBar first.
- KeyboardMap's 9 sp key legends on a phone (§4) — now 10 sp on tablets with the step.
- Macrobenchmark frame timings (§0.3) — not possible on the CI emulator.

Commits: Theme (tokens, text step, compact sizes) → Components (state colours, press scale,
menus, tooltips, slider, chips, setting row, page header, ripple, player tokens) → Components
(ellipsis sweep, 32 files) → Shell (fade-through, nav pill, sidebar, hide-on-scroll, scrolled top
bar, keyboard, DetailTransition + predictive Back, heightIn) → Perf (screen memory, image memory
hits, shared StateFlows, music grouping off the main thread, parsed film names) + CHANGELOG →
fixes from the screenshot review (palette/search chips in ChipRow; ChipRow lineSpacing for the
radio's menu buttons; segment labels without "…" as in the kit; switch thumb in the layout phase;
two test frames roomier) → Screenshots (293 replaced from ci/shots-pr-60, 943afb6).
PR #60 (draft), HEAD 0f64c1d4: CI green, screenshots 366 same.

Found on the way, for later: Settings › Music's output choice does not fit a phone in English
("USB DAC" shows as "USB"); the films header's count shows "7 …" on a tablet with the details
pane open; the first run's step 2 on DeX now scrolls (its last hint is cut at the button).
