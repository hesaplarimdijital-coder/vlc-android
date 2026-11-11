#!/bin/sh
: "${SCRATCH:?set SCRATCH to your scratchpad}" "${ORCH:=$SCRATCH/orch}" "${REPO:?set REPO to the vlc-android clone}" "${WT:=$HOME/wt}"
# watch_sha.sh <sha> [max-minutes]: waits until no CI job or emulator walk of <sha> is queued or running, re-running once
# the walk jobs that failed on the runner's setup (the SDK's "Error reading Zip content", the emulator never booting:
# "the walk produced no report"), then prints the app jobs, the walk summaries and every failure with its cause.
SHA=$1; MAX=${2:-240}
R=hesaplarimdijital-coder/vlc-android
RERUN_DONE=""
i=0
while [ $i -lt $((MAX * 2)) ]; do
  s=$(sh $ORCH/ci.sh $SHA 2>&1)
  if ! echo "$s" | grep -q "in_progress\|queued"; then
    # infra failures: re-run the failed jobs of those runs once
    runs=$(gh api "repos/$R/actions/runs?head_sha=$SHA&per_page=20" --jq '.workflow_runs[] | select(.conclusion=="failure") | .id' 2>/dev/null)
    again=""
    for run in $runs; do
      case " $RERUN_DONE " in *" $run "*) continue;; esac
      infra=0
      for jid in $(gh api "repos/$R/actions/runs/$run/jobs" --jq '.jobs[] | select(.conclusion=="failure") | .id' 2>/dev/null); do
        if gh api "repos/$R/actions/jobs/$jid/logs" 2>/dev/null | grep -q "Error reading Zip content\|##\[error\]the walk produced no report"; then infra=1; fi
      done
      if [ $infra = 1 ]; then
        gh api -X POST "repos/$R/actions/runs/$run/rerun-failed-jobs" >/dev/null 2>&1 && again="$again $run"
        RERUN_DONE="$RERUN_DONE $run"
      fi
    done
    if [ -z "$again" ]; then break; fi
    echo "re-ran infra-failed jobs of:$again"
    sleep 60
  fi
  sleep 30; i=$((i + 1))
done
echo "== app jobs"; echo "$s" | grep "^Uygulama\|^Laboratuvar"
echo "== walks"; echo "$s" | grep -A1 "^Emülatör · özet" | grep "notice\]" | sed 's/.*Ekran özeti //' | cut -c1-600
echo "== failures"; echo "$s" | grep "failure\]" | grep -v "Process completed\|/usr/bin/sh" | cut -c1-300
