#! /bin/sh
#
# *************************************************************************
#  compile-remoteaccess.sh
# **************************************************************************
# Copyright © 2024 VLC authors and VideoLAN
# Author: Nicolas POMEPUY
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 2 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
# ***************************************************************************
#
#
#

set -e

#############
# FUNCTIONS #
#############

diagnostic()
{
    echo "$@" 1>&2;
}

while [ $# -gt 0 ]; do
    case $1 in
        help|--help|-h)
            echo "Fetches the remote access web app at the pinned hash, applies Nağmedar's patches and assets"
            echo "(buildsystem/remoteaccess, S-1304) and builds it into remoteaccess/dist; --init stops before the build"
            exit 0
            ;;
        --init)
            INIT_ONLY=1
            ;;
        *)
            diagnostic "$0: Invalid option '$1'."
            diagnostic "$0: Try --help for more information."
            exit 1
            ;;
    esac
    shift
done

##############################
# Retrieve the remote access #
##############################
diagnostic "Setting up the Remote Access project"

# the pinned version of VideoLAN's web app
REMOTE_ACCESS_TESTED_HASH=bb1f07bbfca97c3fcff59ef5d4799ee87a618134
REMOTE_ACCESS_REPOSITORY=https://code.videolan.org/videolan/remoteaccess
# the same sources as one commit on Nağmedar's own vendor/remoteaccess branch (made by
# .github/workflows/vendor-remoteaccess.yml), pinned by their tree: a build does not need
# code.videolan.org, which is used only when the branch is missing or holds another version
REMOTE_ACCESS_TESTED_TREE=1370413f4966430e8b049b1e8ae86925ea5f8de2
: ${REMOTE_ACCESS_VENDOR_REPOSITORY:=https://github.com/hesaplarimdijital-coder/vlc-android}
REMOTE_ACCESS_VENDOR_BRANCH=vendor/remoteaccess

# Nağmedar's changes to the pinned web app (S-1304): patches/*.patch with git apply, then assets/ copied over it
BRAND_PATH="$(cd "$(dirname "$0")" && pwd -P)/remoteaccess"

: ${VLC_REMOTE_ACCESS_PATH:="$(pwd -P)/application/remote-access-client/remoteaccess"}
diagnostic "VLC_REMOTE_ACCESS_PATH is $VLC_REMOTE_ACCESS_PATH"

# The commit that holds the pinned sources, if this clone has one: VideoLAN's commit itself, or a
# commit of the vendor branch (or of what is checked out) whose tree is the pinned tree
pinned_commit()
{
    if git cat-file -e "${REMOTE_ACCESS_TESTED_HASH}^{commit}" 2>/dev/null; then
        echo "${REMOTE_ACCESS_TESTED_HASH}"
        return 0
    fi
    for ref in HEAD "refs/remotes/${REMOTE_ACCESS_VENDOR_BRANCH}"; do
        git rev-parse -q --verify "${ref}^{commit}" > /dev/null || continue
        commit=$(git log --format='%H %T' "$ref" | awk -v tree="${REMOTE_ACCESS_TESTED_TREE}" '$2 == tree { print $1; exit }')
        if [ -n "$commit" ]; then
            echo "$commit"
            return 0
        fi
    done
    return 1
}

if [ ! -d "$VLC_REMOTE_ACCESS_PATH/.git" ]; then
    diagnostic "Remote access sources: not found, fetching them"
    mkdir -p "$VLC_REMOTE_ACCESS_PATH"
    # a folder with only the artifacts of an earlier build (dist, node_modules) is kept
    git -C "$VLC_REMOTE_ACCESS_PATH" init -q
fi

cd "$VLC_REMOTE_ACCESS_PATH"
if ! commit=$(pinned_commit); then
    diagnostic "Remote access sources: fetching ${REMOTE_ACCESS_VENDOR_BRANCH} from ${REMOTE_ACCESS_VENDOR_REPOSITORY}"
    git fetch -q "${REMOTE_ACCESS_VENDOR_REPOSITORY}" "+refs/heads/${REMOTE_ACCESS_VENDOR_BRANCH}:refs/remotes/${REMOTE_ACCESS_VENDOR_BRANCH}" \
        || diagnostic "Remote access sources: ${REMOTE_ACCESS_VENDOR_BRANCH} could not be fetched"
fi
if ! commit=$(pinned_commit); then
    diagnostic "Remote access sources: ${REMOTE_ACCESS_VENDOR_BRANCH} does not hold the pinned version, fetching ${REMOTE_ACCESS_REPOSITORY}"
    git fetch -q "${REMOTE_ACCESS_REPOSITORY}" main || { diagnostic "Remote access sources: ${REMOTE_ACCESS_REPOSITORY} could not be fetched"; exit 1; }
fi
commit=$(pinned_commit) || { diagnostic "Remote access sources: neither tree ${REMOTE_ACCESS_TESTED_TREE} on ${REMOTE_ACCESS_VENDOR_BRANCH} nor ${REMOTE_ACCESS_TESTED_HASH} was found"; exit 1; }
diagnostic "Remote access sources: ${commit}"

# always start from the pinned sources: an earlier run's patches and assets are dropped (node_modules and dist are ignored files, so they stay)
git -c advice.detachedHead=false checkout -q --force --detach "$commit"
git clean -fdq
# VideoLAN's revision, which the app's About shows, whichever commit holds it
echo "${REMOTE_ACCESS_TESTED_HASH}" > "$(git rev-parse --git-dir)/upstream-revision"

for patch in "$BRAND_PATH"/patches/*.patch; do
    diagnostic "Applying $(basename "$patch")"
    git apply --whitespace=nowarn "$patch" || { diagnostic "$patch does not apply to the pinned sources"; exit 1; }
done
cp -R "$BRAND_PATH"/assets/. .

if [ "$INIT_ONLY" != 1 ]; then
    diagnostic "Building the Remote Access project"
    rm -rf dist
    if [ -f package-lock.json ]; then
        npm ci --no-audit --no-fund
    else
        npm install --no-audit --no-fund
    fi
    npm run build-android
    "$BRAND_PATH"/check-dist.sh dist
fi
