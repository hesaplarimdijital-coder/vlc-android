#!/bin/sh
: "${SCRATCH:?set SCRATCH to your scratchpad}" "${ORCH:=$SCRATCH/orch}" "${REPO:?set REPO to the vlc-android clone}" "${WT:=$HOME/wt}"
# merge.sh <story-id> <sha>: ff-merge story/<id> at <sha> into master (CI must be green, based on master), clean up
set -e
ID=$1; SHA=$2; R=$REPO
cd $R
git fetch -q origin
[ "$(git ls-remote origin refs/heads/story/$ID | cut -f1)" = "$(git rev-parse $SHA)" ] || { echo "remote head differs"; exit 1; }
git merge-base --is-ancestor origin/master $SHA || { echo "NOT BASED ON MASTER"; exit 2; }
s=$(sh $ORCH/ci.sh $SHA 2>&1 | grep -v notice | awk '/^[^ ]/{skip=($0 ~ /Emülatör/)} !skip')
echo "$s" | grep -q "failure\|in_progress\|queued\|cancelled" && { echo "CI not green:"; echo "$s"; exit 3; }
W=$(git worktree list | grep "\\[story/$ID\\]" | awk '{print $1}')
if [ -n "$W" ]; then
  python3 $ORCH/dupstr.py "$W" || { echo "STRINGS BROKEN"; exit 4; }
  # CI runs the i18n tests (GlossaryTest, StringsTest, HardcodedTextTest) with every other test; the old session also
  # compiled them locally with a stub Kotlin toolchain ($SCRATCH/kt, not handed over) — skipped when it is absent
  S=$SCRATCH
  if [ -x "$S/kt/kct.sh" ]; then
    mkdir -p $S/emptymain
    sh $S/kt/kct.sh $S/i18n-$ID $S/emptymain "$W"/application/nagmedar-ui/src/test/java/app/nagmedar/ui/i18n/*.kt >/dev/null 2>&1 || true
    (cd "$W/application/nagmedar-ui" && java -cp $S/i18n-$ID:/opt/gradle-8.14.3/lib/kotlin-stdlib-2.0.21.jar:/opt/gradle-8.14.3/lib/junit-4.13.2.jar:/opt/gradle-8.14.3/lib/hamcrest-core-1.3.jar org.junit.runner.JUnitCore app.nagmedar.ui.i18n.GlossaryTest app.nagmedar.ui.i18n.StringsTest app.nagmedar.ui.i18n.HardcodedTextTest 2>&1 | grep -q "^OK") || { echo "I18N TESTS FAIL in $W"; exit 5; }
  fi
fi
git checkout -q master && git merge --ff-only -q origin/master && git merge --ff-only -q $SHA && git push -q origin master
git log --oneline -1
[ -d $WT/$ID ] && git worktree remove --force $WT/$ID
git branch -D story/$ID >/dev/null 2>&1 || true
grep -n "const val VERSION" application/nagmedar-data/src/main/java/app/nagmedar/data/NagmedarDatabase.kt
