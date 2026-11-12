#!/bin/sh
# await_enabled.sh [max-minutes]: GitHub stopped running this fork's workflows ("Workflows are not currently being run
# on this fork due to the scale of GitHub Actions usage"; a dispatch answers 422 "Actions has been disabled for this
# repository", pushes and pull requests start nothing; the API still says the workflows are active). Only the owner's
# click on "Enable workflows" on the repository's Actions tab clears it. This asks every two minutes with a dispatch that can
# start nothing (a ref that does not exist): while the workflows are off the answer is "disabled", once they are on it
# is about the ref. Ends then.
R=hesaplarimdijital-coder/vlc-android
MAX=${1:-115}; i=0; out=""
while [ $i -lt $MAX ]; do
  out=$(gh api repos/$R/actions/workflows/emulator.yml/dispatches -f ref=probe/no-such-branch 2>&1 || true)
  case "$out" in
    *"has been disabled"*) ;;
    *[Rr]ef*) echo "ACTIONS ENABLED at $(date -u +%H:%M:%S): $(echo "$out" | head -c 160)"; exit 0 ;;
  esac
  i=$((i + 2)); sleep 120
done
echo "STILL DISABLED after $MAX min at $(date -u +%H:%M:%S): $(echo "$out" | head -c 200)"
