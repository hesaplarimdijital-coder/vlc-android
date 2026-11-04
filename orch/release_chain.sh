#!/bin/sh
: "${SCRATCH:?set SCRATCH to your scratchpad}" "${ORCH:=$SCRATCH/orch}" "${REPO:?set REPO to the vlc-android clone}" "${WT:=$HOME/wt}"
# release_chain.sh <id> <sha> <version>: land story/<id> at <sha>, dispatch the personal release on master,
# wait for ci/personal-release to name master's sha, decrypt and check the APK.
O=$ORCH
OUT=$SCRATCH/release-$3
R=$REPO
sh $O/land.sh $1 $2 || { echo "LAND FAILED"; exit 1; }
cd $R && git fetch -q origin && M=$(git rev-parse origin/master)
echo "master $M"
gh api repos/hesaplarimdijital-coder/vlc-android/actions/workflows/ci.yml/dispatches -f ref=master -F 'inputs[personal_release]=true' || { echo "DISPATCH FAILED"; exit 2; }
echo "dispatched $(date -u +%H:%M:%S)"
for i in $(seq 1 120); do
  sleep 60
  git fetch -q origin +refs/heads/ci/personal-release:refs/remotes/origin/ci/personal-release 2>/dev/null
  if git show origin/ci/personal-release:README.md 2>/dev/null | grep -q "$M"; then echo "ready after $i min"; break; fi
done
git show origin/ci/personal-release:README.md | head -14
mkdir -p $OUT/enc && rm -f $OUT/enc/*
for f in $(git ls-tree --name-only origin/ci/personal-release); do git show origin/ci/personal-release:$f > $OUT/enc/$f; done
ls -la $OUT/enc
sh $O/delivery/decrypt.sh $OUT/enc $OUT/Nagmedar-$3-arm64-v8a.apk
python3 $SCRATCH/tools/apkcheck.py $OUT/Nagmedar-$3-arm64-v8a.apk 2>&1 | tail -14
