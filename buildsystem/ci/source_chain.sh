#!/bin/sh
# Print the native source chain as GitHub workflow notices:
#   libvlcjni (remote, revision) -> vlc (remote, tested commit, applied patches)
# Usage: source_chain.sh [libvlcjni_dir]
J="${1:-${VLC_LIBJNI_PATH:-libvlcjni}}"
jni_url=$(git -C "$J" remote get-url origin 2>/dev/null || echo '?')
jni_rev=$(git -C "$J" rev-parse --short=12 HEAD 2>/dev/null || echo '?')
vlc_url=$(git -C "$J/vlc" remote get-url origin 2>/dev/null || echo '?')
tested=$(sed -n 's/^ *VLC_TESTED_HASH=\([0-9a-f]*\).*/\1/p' "$J/buildsystem/get-vlc.sh" 2>/dev/null | head -1)
if [ -n "$tested" ]; then
    patches=$(git -C "$J/vlc" rev-list --count "${tested}..HEAD" 2>/dev/null || echo '?')
else
    patches='?'
fi
echo "::notice title=Kaynak zinciri::libvlcjni ${jni_url} @ ${jni_rev} -> vlc ${vlc_url} @ ${tested:-?} + ${patches} yama"
clone_lines=$(grep -n 'VLC_REPOSITORY\|git clone' "$J/buildsystem/get-vlc.sh" 2>/dev/null | head -6 | tr '\n' ' ')
echo "::notice title=get-vlc.sh::${clone_lines:-bulunamadı}"
