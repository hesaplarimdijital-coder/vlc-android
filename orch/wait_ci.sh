#!/bin/sh
: "${SCRATCH:?set SCRATCH to your scratchpad}" "${ORCH:=$SCRATCH/orch}" "${REPO:?set REPO to the vlc-android clone}" "${WT:=$HOME/wt}"
# wait_ci.sh <sha>: waits until no CI job of the commit is queued/in progress, prints the result
SHA=$1
for i in $(seq 1 240); do
  s=$(sh $ORCH/ci.sh $SHA 2>&1)
  n=$(echo "$s" | grep -c "Uygulama\|Laboratuvar")
  if [ "$n" -ge 3 ] && ! echo "$s" | grep -q "in_progress\|queued"; then echo "$s" | grep -v "notice\]" | head -30; exit 0; fi
  sleep 30
done
echo "timeout"; echo "$s" | head -10
