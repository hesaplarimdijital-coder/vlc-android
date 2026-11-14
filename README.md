# Nağmedar 1.0.9: kişisel sürüm (şifreli)

| | |
| --- | --- |
| Sürüm | 1.0.9 |
| versionCode | 14010015 |
| Kaynak | master @ 5ba07aa6f2c1372c6b78b310e0665cd621fd7954 (run 43533617174) |
| APK SHA-256 | `f0c7b95c98f434c0c8bdfe789661b360e8a9bb50670f4db6423a2ab37a92aedc` |
| APK boyutu | 93,1 MB (97589864 bayt) |
| Uygulama kimliği | `app.nagmedar.debug`: yüklü debug derlemesinin üzerine güncelleme olarak kurulur |
| İmza | depodaki ortak debug anahtarı (SHA-256 `d094907b020a7f3ee143b53c7be0874c33543b51bf3ebe32943df576a9fecbbc`) |
| ABI | arm64-v8a (Galaxy S24, Galaxy Tab S11 Ultra) |
| targetSdk | 37 |

Bu dalda yalnızca şifreli dosyalar durur: `envelope.bin` (APK'nın parolası ve SHA-256 özeti,
teslim anahtarının açık yarısıyla RSA-OAEP-SHA256) ve `apk.enc.part*` (APK, AES-256-CBC,
PBKDF2 100000 yineleme, 90 MB'lık parçalar). Açmak için `delivery.key` gerekir; adımlar
depodaki [buildsystem/release/README.md](https://github.com/hesaplarimdijital-coder/vlc-android/blob/master/buildsystem/release/README.md) dosyasında.
