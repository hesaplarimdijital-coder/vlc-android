#!/bin/sh
: "${SCRATCH:?set SCRATCH to your scratchpad}" "${ORCH:=$SCRATCH/orch}" "${REPO:?set REPO to the vlc-android clone}" "${WT:=$HOME/wt}"
# schema_land.sh <id> <pr> <version> <sha>: wait CI of <sha>, take schemas/<version>.json from ci/shots-pr-<pr>, commit+push, wait CI, merge
O=$ORCH
ID=$1; PR=$2; V=$3; SHA=$4
W=$WT/$ID
sh $O/wait_ci.sh $SHA | grep -q "failure" && { echo "CI FAILED for $SHA"; sh $O/ci.sh $SHA | grep -v notice | head -20; exit 3; }
cd $W || exit 1
git fetch -q origin ci/shots-pr-$PR || { echo "no shots branch"; exit 2; }
F=application/nagmedar-data/schemas/app.nagmedar.data.NagmedarDatabase/$V.json
git show FETCH_HEAD:schemas/app.nagmedar.data.NagmedarDatabase/$V.json > $F || { echo "no $V.json on ci/shots-pr-$PR"; exit 2; }
python3 -c "import json,sys; d=json.load(open('$F')); assert d['database']['version']==$V, d['database']['version']; print('schema ok', [e['tableName'] for e in d['database']['entities']][-3:])" || exit 2
git add $F && git commit -q -m "$ID: database schema $V, exported by CI

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Lvh4tsdhXUadwhZpYnRA5K" && git push -q origin story/$ID || exit 1
NEW=$(git rev-parse HEAD); echo "pushed $NEW"
sh $O/land.sh $ID $NEW
