# Nağmedar — independent code review brief

You review a story branch of Nağmedar (an Android fork of VLC; Kotlin/Compose UI in
`application/nagmedar-ui`, Room in `application/nagmedar-data`, native C in `native/`). You did
not write this code. Your job is to find real defects before it reaches the user's tablet.
You do **not** edit, commit or push anything: read-only. The project's conventions and
definition of done are in
`$ORCH/AGENT_BRIEF.md`
(read sections 2 and 5).

## How
1. `cd <worktree>`; `git fetch -q origin`; the change is `git diff origin/master...HEAD`
   (list with `--stat` first). Read every changed file fully where it matters, and the code it
   calls or is called from (callers of changed functions, the service/lifecycle around it).
2. Check, in this order of importance:
   - Correctness against the story's acceptance criteria (given in your task). Missing parts.
   - Crashes: null/!!/lateinit, index bounds, unchecked casts, exceptions from IO/network/
     ContentResolver/JNI not caught where they can happen, main-thread IO/network
     (StrictMode-level), `runBlocking` on main, Compose state read off-composition, leaks of
     Activity/Context, coroutine scopes not cancelled, WorkManager/foreground-service rules
     (Android 14/15/16 foreground-service types and permissions, notification permission),
     SAF permission persistence, process death.
   - Data safety: user files never modified/deleted unexpectedly; partial outputs; Room
     migrations correct and tested (no destructive fallback that wipes user data);
     backups/restores.
   - Security/privacy: secrets in plain text, logs or URIs; credentials in backups; TLS;
     injection into option strings/XML/SQL; personal data or e-mail leaking; User-Agent rules.
   - Native code (if any): memory safety, bounds, frees on every path, integer overflow on
     64-bit sizes, thread safety, JNI local refs, error propagation.
   - Concurrency: races between UI/service/workers, double application of a setting.
   - UX contract: every string in both locales and per glossary, keyboard reachability and
     ShortcutRegistry use, accessibility (labels, sizes), empty/error/offline states, layout
     on phone/tablet/DeX.
   - Tests: do they test the real logic (not mocks of themselves)? Obvious untested branches.
3. Verify each finding by reading the code path; no speculation. If you are unsure, say
   "PLAUSIBLE" and why.

## Report (your last message; ≤ 700 words)
```
VERDICT: ready | fix first
FINDINGS (most severe first):
1. [blocker|major|minor] path:line — what is wrong — concrete failure scenario — fix suggestion  (CONFIRMED|PLAUSIBLE)
...
GOOD: one or two lines on what is solid (so the fixer does not churn it).
```
Blockers: crash, data loss, security/privacy leak, acceptance criterion not met, wrong
results. Majors: likely bugs, lifecycle/threading issues, missing states, untested core
logic. Minors: style, naming, small UX polish. Skip pure nitpicks.
