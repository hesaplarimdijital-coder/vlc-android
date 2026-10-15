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

# the pinned version of VideoLAN's web app (the environment may name another for a test)
: ${REMOTE_ACCESS_TESTED_HASH:=bb1f07bbfca97c3fcff59ef5d4799ee87a618134}
REMOTE_ACCESS_REPOSITORY=https://code.videolan.org/videolan/remoteaccess

# Nağmedar's changes to the pinned web app (S-1304): patches/*.patch with git apply, then assets/ copied over it
BRAND_PATH="$(cd "$(dirname "$0")" && pwd -P)/remoteaccess"

: ${VLC_REMOTE_ACCESS_PATH:="$(pwd -P)/application/remote-access-client/remoteaccess"}
diagnostic "VLC_REMOTE_ACCESS_PATH is $VLC_REMOTE_ACCESS_PATH"

if [ ! -d "$VLC_REMOTE_ACCESS_PATH" ] || [ ! -d "$VLC_REMOTE_ACCESS_PATH/.git" ]; then
    diagnostic "Remote access sources: not found, cloning"
    branch="main"
    if [ ! -d "$VLC_REMOTE_ACCESS_PATH" ]; then
        git clone --single-branch --branch ${branch} "${REMOTE_ACCESS_REPOSITORY}" "$VLC_REMOTE_ACCESS_PATH"
    else # folder exist with only the artifacts
        cd "$VLC_REMOTE_ACCESS_PATH"
        git init
        git remote add origin "${REMOTE_ACCESS_REPOSITORY}"
        git pull origin ${branch}
        cd - > /dev/null
    fi
fi

cd "$VLC_REMOTE_ACCESS_PATH"
# always start from the pinned sources: an earlier run's patches and assets are dropped (node_modules and dist are ignored files, so they stay)
git reset --hard ${REMOTE_ACCESS_TESTED_HASH} || { diagnostic "Remote access sources: REMOTE_ACCESS_TESTED_HASH ${REMOTE_ACCESS_TESTED_HASH} not found"; exit 1; }
git clean -fdq

for patch in "$BRAND_PATH"/patches/*.patch; do
    diagnostic "Applying $(basename "$patch")"
    git apply --whitespace=nowarn "$patch" || { diagnostic "$patch does not apply to ${REMOTE_ACCESS_TESTED_HASH}"; exit 1; }
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
