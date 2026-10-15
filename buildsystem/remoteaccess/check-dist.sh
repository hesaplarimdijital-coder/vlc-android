#! /bin/sh
#
# check-dist.sh <dist>: the built remote access web app is Nağmedar's (S-1304).
#
# Fails when the page, the manifest or the bundles still carry VLC's identity: the title and the
# manifest name Nağmedar, no user-visible name of VLC in them, the mark, favicon and typeface are
# there, and VLC's orange is gone from the style sheet. VLC is named only in About's attribution,
# which comes from the app's strings (about_vlc_text), not from the page.

dist=${1:-application/remote-access-client/remoteaccess/dist}
failed=0

fail() {
    echo "::error title=Uzaktan erişim web arayüzü::$*" >&2
    failed=1
}

[ -f "$dist/index.html" ] || { fail "$dist/index.html yok: web uygulaması derlenmemiş"; exit 1; }

grep -q '<title>[^<]*Nağmedar[^<]*</title>' "$dist/index.html" || fail "index.html başlığı Nağmedar'ı söylemiyor"
title=$(sed -n 's/.*<title>\(.*\)<\/title>.*/\1/p' "$dist/index.html")
case "$title" in *VLC*) fail "index.html başlığında VLC var: $title";; esac
grep -q 'content="Nağmedar"' "$dist/index.html" || fail "index.html application-name Nağmedar değil"
grep -q 'VLC' "$dist/index.html" && fail "index.html'de VLC geçiyor"

[ -f "$dist/site.webmanifest" ] || fail "site.webmanifest yok"
grep -q '"name": *"Nağmedar"' "$dist/site.webmanifest" || fail "site.webmanifest adı Nağmedar değil"
grep -q '"short_name": *"Nağmedar"' "$dist/site.webmanifest" || fail "site.webmanifest kısa adı Nağmedar değil"
grep -q 'VLC' "$dist/site.webmanifest" && fail "site.webmanifest'te VLC geçiyor"

for f in logo.svg favicon.svg favicon.ico favicon-16x16.png favicon-32x32.png apple-touch-icon.png android-chrome-192x192.png android-chrome-512x512.png; do
    [ -s "$dist/$f" ] || fail "$f yok"
done
grep -q 'Nağmedar' "$dist/favicon.svg" 2>/dev/null || fail "favicon.svg Nağmedar'ın işareti değil"

n=$(ls "$dist"/fonts/ibm-plex-sans-*.woff2 2>/dev/null | wc -l)
[ "$n" -eq 3 ] || fail "IBM Plex Sans yazı tipleri eksik ($n/3)"

css=$(cat "$dist"/css/*.css)
echo "$css" | grep -qi 'IBM Plex Sans' || fail "stil dosyası IBM Plex Sans'ı tanımlamıyor"
echo "$css" | grep -qi '#ff8800\|#ff610a\|#ff7700' && fail "stil dosyasında VLC'nin turuncusu kalmış"
echo "$css" | grep -qi '#8AB0FF' || fail "stil dosyasında Nağmedar'ın vurgu rengi (#8AB0FF) yok"

# the patches' keys: the page asks the app for its name and language
cat "$dist"/js/*.js | grep -q 'APP_NAME' || fail "uygulama APP_NAME anahtarını kullanmıyor: yamalar uygulanmamış"
cat "$dist"/js/*.js | grep -q 'ABOUT_COPYRIGHT' || fail "Hakkında sayfası dayandığı VLC'yi söylemiyor (ABOUT_COPYRIGHT)"
cat "$dist"/js/*.js | grep -q '"/code"\|verify-code\|verifyCode' || fail "tek seferlik kod akışı pakette yok"

[ "$failed" -eq 0 ] && echo "Uzaktan erişim web uygulaması Nağmedar'ın: $dist"
exit "$failed"
