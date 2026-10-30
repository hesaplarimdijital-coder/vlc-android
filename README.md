# Nağmedar 1.0.2: kişisel sürüm (şifreli)

| | |
| --- | --- |
| Sürüm | 1.0.2 |
| versionCode | 14010008 |
| Kaynak | master @ 0ed1076d3f0519cc1303e375d7494a2e089b3db0 (run 41008503603) |
| APK SHA-256 | `91921e45a7123480833037f14f117d75a4c3129095d48328eedff56bc117e20c` |
| APK boyutu | 84,1 MB (88200708 bayt) |
| Uygulama kimliği | `app.nagmedar.debug`: yüklü debug derlemesinin üzerine güncelleme olarak kurulur |
| İmza | depodaki ortak debug anahtarı (SHA-256 `d094907b020a7f3ee143b53c7be0874c33543b51bf3ebe32943df576a9fecbbc`) |
| ABI | arm64-v8a (Galaxy S24, Galaxy Tab S11 Ultra) |
| targetSdk | 36 |

Bu dalda yalnızca şifreli dosyalar durur: `envelope.bin` (APK'nın parolası ve SHA-256 özeti,
teslim anahtarının açık yarısıyla RSA-OAEP-SHA256) ve `apk.enc.part*` (APK, AES-256-CBC,
PBKDF2 100000 yineleme, 90 MB'lık parçalar). Açmak için `delivery.key` gerekir; adımlar
depodaki [buildsystem/release/README.md](https://github.com/hesaplarimdijital-coder/vlc-android/blob/master/buildsystem/release/README.md) dosyasında.
