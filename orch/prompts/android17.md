You prepare Nağmedar for targetSdk 37 (Android 17). Nağmedar is a VLC-Android fork that is a pure music player (Music, Podcasts, Radio); both of the user's devices run Android 17 (Galaxy S24, Galaxy Tab S11 Ultra). Today the app targets 36 (compileSdk 37). **The user decides the bump**: you make the code ready and prove it on a branch, the orchestrator asks him.

First read fully $ORCH/AGENT_BRIEF.md (section 0000 first), then $ORCH/notes/release.md ("compileSdk 37, targetSdk 36 (local network permission, background audio rules, widget bitmap limit not handled yet)"), $ORCH/notes/network.md, $ORCH/notes/radio.md. Read Android 17's behaviour changes for apps targeting 37 on developer.android.com with WebFetch (the release's "behavior changes: apps targeting Android 17" page and the pages it links), and list in progress.md every one that touches this app, with what the app does today.

Worktree: $WT/android17, branch `story/android17` (from origin/master). Scratch: $SCRATCH/agents/android17/. Force-pushes are refused; `git merge origin/master` when master moves. **Keep `$SCRATCH/agents/android17/progress.md` current after every push (read it first if it exists); push within 45 minutes and then at least every 40 minutes.** Do not run local Kotlin compiles while other agents run (memory is shared): CI is the compiler.

## Scope (from the old session's list; add what the behaviour-changes page adds)
1. **Local network access** (Android 17's new permission for the LAN): everything that reaches the local network — SMB/UPnP/DLNA browsing (network/), the remote access server, casting, radio servers on the LAN, NAS shares — asks for it at the moment it is needed, says why in the app's words (Turkish and English), and degrades with a clear notice when it is denied (never a crash, never a silent empty list).
2. **Background audio rules:** starting or resuming playback from the background (media buttons, Bluetooth, Auto, the widget, alarms/sleep timer, notification actions) keeps working under the new rules (foreground service type, the allowed start reasons); check PlaybackService's start paths.
3. **Widget bitmap limit:** the home-screen widget's artwork stays within the new RemoteViews bitmap memory limit (scale to the widget's size).
4. Anything else the page lists that applies.

Make each change work under targetSdk 36 as well (it is still the shipped target). Then prove it: on your branch a commit that sets targetSdk 37 (settings.gradle) — CI green and the walks (debug and personal, API 37.0, tablet and phone) green with it — and a second commit that sets it back to 36, so the branch can land without the bump. Tests for the permission flow and the notices. Report the exact list of changes and what the bump would change for the user.

## Coordination
At the same time: `story/motion-base`, `story/shell-chrome`, `story/components`, `story/detail-lists`, `story/tablet-dex` (UI work in theme/, shell/, components/, music/). You work in the manifest, network/, remoteaccess/, cast/, widget/, PlaybackService and the permission UI; merge origin/master before each push. Do not edit CHANGELOG.md or the version.

## Done
All CI jobs green on the final (targetSdk 36) head, the targetSdk 37 proof's run ids in the report, PR opened as draft (title like "1.0.3: Android 17'ye hazırlık — yerel ağ izni, arka planda ses, widget"). Final report per brief §6 with Turkish device checks. Append notes to $ORCH/notes/release.md under "## Android 17 (targetSdk 37)". Do not merge.
