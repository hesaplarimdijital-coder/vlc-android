You do Part 4 of Nağmedar's Desktop Kit 2.3.0 port: tablet and DeX. Nağmedar is a VLC-Android fork that is a pure music player; the user's tablet is a Galaxy Tab S11 Ultra with DeX, a keyboard cover with touchpad, and a mouse (the sidebar + dense music table + inspector "MusicBee" layout, the floating TransportBar, the big artwork player).

First read fully $ORCH/AGENT_BRIEF.md (section 0000 first), then $ORCH/notes/kit-2.3-map.md (rows #14 (optional), #16, #17, #20, #67 are yours; §2 for who edits what), $ORCH/notes/kit-port.md (the tablet workstation part and "## 2.3 port — Part 1 (platform)"), $ORCH/notes/big-player.md. The kit is at $KIT (2.3.0: input/InputModeTracker.kt `PointerDevices`, components/DragAndDrop.kt, shell/CaptionBar.kt, shell/NavigationChrome.kt).

Worktree: $WT/tablet-dex, branch `story/tablet-dex` (from origin/master). Scratch: $SCRATCH/agents/tablet-dex/. Force-pushes are refused; `git merge origin/master` when master moves. **Keep `$SCRATCH/agents/tablet-dex/progress.md` current after every push (read it first if it exists); push within 45 minutes and then at least every 40 minutes.** Do not run local Kotlin compiles while other agents run (memory is shared): CI is the compiler.

## Scope
1. **Input devices (#20):** density/pointer mode follows the keyboard cover's touchpad being switched on or off and a mouse waking (input/InputModeTracker.kt:62 does nothing on `onInputDeviceChanged`, :85 counts disabled devices); only enabled devices count; other device changes do not flip it.
2. **Drag and drop (#16):** keep the app's URI design (input/DragSource.kt, Drops.kt, shell/DropOverlay.kt); a drag starts after the system's long-press timeout of rest, then movement (mouse, touchpad, pen; a slow click stays a click); a small card with the label and, for several items, a count in the accent colour instead of the row's ghost. Optional, if small: dropping tracks on "Çalma listeleri" in the sidebar adds them to a list (NavDrop).
3. **Caption bar (#17):** on DeX/desktop windows where the system shows its own app chip in the caption, hide the app's BrandMark and title there (shell/NavigationChrome.kt:563-565).
4. **Sidebar footer badge (#67):** the footer entries show their badge (NavigationChrome.kt:390, :404 pass `badge = null`).
5. **The user's open item, the inspector:** per-track ReplayGain (track and album gain) and peak in the inspector's Etiketler/Sinyal yolu (music/MusicInspector.kt, music/TrackTechIndex.kt: read them where the tech index reads tags; show "—" when absent). The other open item ("open player" with the inspector hidden) is decided: no change.

Tests for each (Robolectric; DeX-sized tests carry "-keysexposed-qwerty"); screenshot references where the look changes on purpose (look at each PNG; separate "Screenshots: …" commit). Walks green (debug and personal, API 35 and 37.0; the tablet job covers DeX-like widths).

## Coordination
At the same time: `story/motion-base`, `story/shell-chrome` (NagmedarShell, ShellLayout, ShellPadding, NavigationChrome's bottom bar), `story/components` (Menus, Interaction, KeyCap/SearchField in NavigationChrome.kt), `story/detail-lists`, `story/android17`. Keep NavigationChrome.kt edits to the lines above; merge origin/master before each push. Do not edit CHANGELOG.md or the version.

## Done
All CI jobs green, walks green, PR opened as draft (title like "1.0.3: tablet ve DeX — touchpad, sürükle bırak, başlık çubuğu, ReplayGain"). Final report per brief §6 with Turkish device checks. Append notes to $ORCH/notes/kit-port.md under "## 2.3 port — Part 4 (tablet and DeX)". Do not merge.
