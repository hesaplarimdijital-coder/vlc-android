#!/bin/sh
: "${SCRATCH:?set SCRATCH to your scratchpad}" "${ORCH:=$SCRATCH/orch}" "${REPO:?set REPO to the vlc-android clone}" "${WT:=$HOME/wt}"
# land.sh <id> <sha>: wait for CI of <sha>, then merge.sh
O=$ORCH
sh $O/wait_ci.sh $2 && sh $O/merge.sh $1 $2
