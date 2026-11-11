#!/bin/sh
# await_actions.sh <sha> [max-minutes]: while GitHub Actions starts nothing (runs queued without jobs, dispatches ending in
# startup_failure), waits; re-dispatches the Baseline Profile run of <sha>'s branch when the last one failed at its start
# (at most 8 times); ends as soon as a run of <sha> has a job that started, saying which.
SHA=$1; MAX=${2:-110}; BRANCH=${3:-story/release-1.0.3}
R=hesaplarimdijital-coder/vlc-android
tries=0; i=0
while [ $i -lt $MAX ]; do
  runs=$(gh api "repos/$R/actions/runs?head_sha=$SHA&per_page=30" --jq '.workflow_runs[] | "\(.id) \(.event) \(.status) \(.conclusion // "-")"' 2>/dev/null | grep -E '^[0-9]+ ')
  started=""
  for id in $(echo "$runs" | awk '$3!="completed" || $4=="success" || $4=="failure" {print $1}'); do
    j=$(gh api "repos/$R/actions/runs/$id/jobs?per_page=50" --jq '[.jobs[] | select(.status!="queued")] | length' 2>/dev/null | grep -E '^[0-9]+$')
    [ -n "$j" ] && [ "$j" -gt 0 ] && started="$started $id($j)"
  done
  if [ -n "$started" ]; then echo "ACTIONS STARTED: runs with started jobs:$started at $(date -u +%H:%M:%S)"; exit 0; fi
  # the dispatched (profile) runs: none waiting or running, and the newest failed at its start → once more
  disp=$(echo "$runs" | awk '$2=="workflow_dispatch"')
  if [ -n "$runs" ] && ! echo "$disp" | grep -q " queued \| in_progress \| requested \| waiting \| pending "; then
    if [ $tries -lt 8 ]; then
      gh api repos/$R/actions/workflows/emulator.yml/dispatches -f ref=$BRANCH -F 'inputs[baseline_profile]=true' >/dev/null 2>&1 && tries=$((tries+1))
    fi
  fi
  i=$((i+1)); sleep 60
done
echo "STILL NOTHING after $MAX min (dispatch retries: $tries) at $(date -u +%H:%M:%S)"
