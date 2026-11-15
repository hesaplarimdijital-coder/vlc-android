# Nağmedar 1.1.0: kişisel sürüm (şifreli)

| | |
| --- | --- |
| Sürüm | 1.1.0 |
| versionCode | 14010106 |
| Kaynak | master @ 77f8a4023d7b6a19179b5f3586eb33d54003c1f2 (run 43719770744) |
| APK SHA-256 | `537496c300c13350899df385108343ca46a425ba1c162d9a25ca3b201d8c293c` |
| APK boyutu | 93,1 MB (97660786 bayt) |
| Uygulama kimliği | `app.nagmedar.debug`: yüklü debug derlemesinin üzerine güncelleme olarak kurulur |
| İmza | depodaki ortak debug anahtarı (SHA-256 `d094907b020a7f3ee143b53c7be0874c33543b51bf3ebe32943df576a9fecbbc`) |
| ABI | arm64-v8a (Galaxy S24, Galaxy Tab S11 Ultra) |
| targetSdk | 37 |

Bu dalda yalnızca şifreli dosyalar durur: `envelope.bin` (APK'nın parolası ve SHA-256 özeti,
teslim anahtarının açık yarısıyla RSA-OAEP-SHA256) ve `apk.enc.part*` (APK, AES-256-CBC,
PBKDF2 100000 yineleme, 90 MB'lık parçalar). Açmak için `delivery.key` gerekir; adımlar
depodaki [buildsystem/release/README.md](https://github.com/hesaplarimdijital-coder/vlc-android/blob/master/buildsystem/release/README.md) dosyasında.
