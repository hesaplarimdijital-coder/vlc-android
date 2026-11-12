#!/bin/sh
# wait_ids.sh <run id>…: waits (at most 110 min) until each of the runs has completed, then prints every job's result
R=hesaplarimdijital-coder/vlc-android
i=0
while [ $i -lt 110 ]; do
  busy=0
  for id in "$@"; do
    st=$(gh api repos/$R/actions/runs/$id --jq .status 2>/dev/null || true)
    [ "$st" = completed ] || busy=1
  done
  [ $busy = 0 ] && break
  i=$((i + 1)); sleep 60
done
for id in "$@"; do
  gh api repos/$R/actions/runs/$id --jq '"== \(.id) \(.status) \(.conclusion) \(.head_sha[0:8])"' 2>/dev/null
  gh api "repos/$R/actions/runs/$id/jobs?per_page=50" --jq '.jobs[] | "   \(.name): \(.conclusion)"' 2>/dev/null
done
date -u +%H:%M:%S
