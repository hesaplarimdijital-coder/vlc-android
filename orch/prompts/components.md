You do Part 3 of Nağmedar's Desktop Kit 2.3.0 port: components, keyboard and selection behaviour. Nağmedar is a VLC-Android fork that is a pure music player; the user runs it on a Galaxy S24 (Android 17, three-button navigation) and a Galaxy Tab S11 Ultra with DeX, keyboard cover and mouse. His priorities: the kit update, testing, smooth transitions.

First read fully $ORCH/AGENT_BRIEF.md (section 0000 first: keycaps/shortcut hints only while a hardware keyboard is connected — the kit's `showShortcutHints()` also shows them with a mouse: keep the app's rule), then $ORCH/notes/kit-2.3-map.md (rows #8, #10, #22, #26, #27, #33, #37, #38, #39, #53, #55, #56, #64, #65 are yours now; #31 and #63 come after Part 2b; §2 says who else edits what), $ORCH/notes/kit-port.md (incl. "## 2.3 port — Part 1 (platform)"), $ORCH/notes/polish-1.0.1.md (KeyboardHints, ModalLayer, blockInputBelow), $ORCH/notes/crash-sweep.md. The kit is at $KIT (2.3.0), its 0.2.1/0.2.2 fixes are commits 8c23f70 and 20da08e (the app never got them).

Worktree: $WT/components, branch `story/components` (from origin/master 6d8c86e). Scratch: $SCRATCH/agents/components/. Force-pushes are refused; `git merge origin/master` when master moves.

**The machine can restart and kill you.** Keep `$SCRATCH/agents/components/progress.md` current after every push (read it first if it exists), push within 45 minutes and then at least every 40 minutes of work (WIP commits are fine).

## Scope (take the kit's fix into the app's own components; never replace them with kit copies)
- Menus (#38, #53, #64): a drop-down near the window's bottom opens above its button; on a phone a menu grows from the point it opened at; a menu keeps its place while it fades (input/ContextMenu.kt:127 `offset ?: IntOffset.Zero` makes it jump to the anchor's corner), a new pop-up per opening, a right click outside is replayed, Esc works with no item focused.
- Press and hover (#55, #65): on a phone a press goes three quarters as deep and bounces back lightly; with a mouse hover/press colours change at once and fades to or from transparent change only alpha.
- Haptics (#22): a `NagHaptics` (touch only: toggle, tick, threshold, confirm, reject) used by NagSwitch, NagCheckbox, FilterChip, SegmentedChoice and the sheet's close threshold.
- Fields (#8): NagTextField/NagSearchField gain clear, error and supporting-text lines where the kit has them; the ad-hoc number fields (network/ServerDialog.kt:300, sleep/AlarmSettings.kt:227) use one.
- Toasts and undo bars (#10): swipe sideways to dismiss at the finger's speed, the system's recommended timeout with TalkBack; keep the app's stacking.
- Slider (#26) label + valueText for TalkBack (volume, EQ, gain); switch thumb stops within half a pixel (#56; `NagMotion.PIXEL` comes with Part 2a — if it has not landed, add the threshold locally and switch to it after merging master); KeyCap and search field outlined in the light theme (#27); colour swatches wrap (#37, FlowRow).
- Selection (#33, #39): a finger tap opens and selects only while selecting (the press's PointerType decides, not the layout; keep the long-press "Seç"); Back clears a selection (touch/select mode: any; mouse: two or more) in music/MusicScreen.kt and music/PlaylistsScreen.kt.
- Keyboard and focus, the user's open items: on DeX, Ctrl+A inside a full-window layer (Sıradaki, the big player) must not reach the page under it; a hardware keyboard's Tab must not move focus into the hidden content behind the open player. Compose 1.12 has subtree focus blocking (`focusProperties { canFocus }`, `onEnter { cancelFocusChange() }`, focusGroup); use it in components/ModalLayer.kt and WindowPage rather than in many places; keep NagmedarShell.kt edits minimal (another part owns it this round).

Tests for each behaviour (Compose UI tests, the kit's as models); screenshot references only where the look changes on purpose (look at each PNG; separate "Screenshots: …" commit). Walks green (debug and personal, API 35 and 37.0).

## Coordination
At the same time: `story/motion-base` (Part 2a: theme/Motion.kt, Theme.kt, spring thresholds at call sites), `story/shell-chrome` (Part 2b-1: ShellLayout, ShellPadding, NagmedarShell, NavigationChrome's bottom bar, SystemBars, NagmedarApp root insets), `story/library-fast`, `story/auto-radio`. Stay out of components/Sheets.kt and components/ExpandablePlayer.kt this round; merge origin/master before each push. Do not edit CHANGELOG.md or the version.

## Done
All CI jobs green, walks green, PR opened as draft (title like "1.0.3: bileşenler — menüler, basma, titreşim, seçim ve klavye"). Final report per brief §6 with Turkish device checks. Append notes to $ORCH/notes/kit-port.md under "## 2.3 port — Part 3 (components)". Do not merge.
