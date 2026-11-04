#!/bin/sh
: "${SCRATCH:?set SCRATCH to your scratchpad}" "${ORCH:=$SCRATCH/orch}" "${REPO:?set REPO to the vlc-android clone}" "${WT:=$HOME/wt}"
# ci.sh <sha>: CI status of a commit; annotations (errors/warnings except noise) of finished app jobs.
SHA=${1:-$(git -C $REPO rev-parse HEAD)}
R=hesaplarimdijital-coder/vlc-android
gh api "repos/$R/commits/$SHA/check-runs" --jq '.check_runs[] | "\(.id)\t\(.name)\t\(.status)\t\(.conclusion)"' 2>/dev/null | while IFS="$(printf '\t')" read id name status conclusion; do
  echo "$name: $status $conclusion"
  if [ "$status" = completed ] && [ "$conclusion" != skipped ]; then
    gh api "repos/$R/check-runs/$id/annotations" --jq '.[] | select(.annotation_level != "notice" or (.title|test("Ekran"))) | "  [\(.annotation_level)] \(.path):\(.start_line) \(.title) \(.message[0:600])"' 2>/dev/null \
      | grep -v "Node.js 20\|Application namespace for attribute\|ubuntu-latest label" | head -60
  fi
done
