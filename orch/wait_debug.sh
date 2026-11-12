#!/bin/sh
# wait_debug.sh <sha>: waits (at most 60 min) until the app's debug job of <sha> has completed, then prints the commit's CI state
: "${SCRATCH:?}" "${ORCH:=$SCRATCH/orch}"
R=hesaplarimdijital-coder/vlc-android
i=0
while [ $i -lt 120 ]; do
  s=$(gh api "repos/$R/commits/$1/check-runs" --jq '.check_runs[] | select(.name=="Uygulama · debug") | "\(.status) \(.conclusion)"' 2>/dev/null | head -1)
  case "$s" in completed*) break;; esac
  i=$((i + 1)); sleep 30
done
sh $ORCH/ci.sh $1 2>&1 | cut -c1-900 | head -80
date -u +%H:%M:%S
