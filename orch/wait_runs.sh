#!/bin/sh
: "${SCRATCH:?set SCRATCH to your scratchpad}" "${ORCH:=$SCRATCH/orch}" "${REPO:?set REPO to the vlc-android clone}" "${WT:=$HOME/wt}"
# wait_runs.sh <branch> <sha-prefix>: wait until every Emulator run of <branch> at <sha> has finished; print them
B=$1; SHA=$2
for i in $(seq 1 100); do
  sleep 60
  out=$(gh api "repos/hesaplarimdijital-coder/vlc-android/actions/runs?branch=$B&per_page=10" --jq ".workflow_runs[] | select(.head_sha | startswith(\"$SHA\")) | select(.name==\"Emulator\") | \"\(.id) \(.status) \(.conclusion) \(.display_title)\"")
  n=$(echo "$out" | grep -c . ); busy=$(echo "$out" | grep -c "in_progress\|queued\|waiting")
  [ "$n" -ge 2 ] && [ "$busy" -eq 0 ] && break
done
echo "$out"
