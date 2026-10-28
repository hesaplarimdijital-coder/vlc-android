# Nağmedar sürümleri (S-1503)

Her MVP bir sürümdür: MVP1 0.1.0, MVP2 0.2.0 ve sonrası aynı sırayla. İmzalı APK'lar **yalnızca taslak GitHub sürümlerine** gider. Taslakları yalnızca depoya yazabilenler (şimdilik yalnızca siz) görür. CI hiçbir sürümü yayımlamaz ve imzalı APK'yı artefakt olarak yüklemez. Herkese açık yayın ve F-Droid derlemesi sonraya bırakıldı (12 Ekim 2026).

Depo şu anda herkese açık: kaynak kod ile CI'ın debug ve benchmark APK artefaktlarını GitHub'a giriş yapmış herkes görebilir. Bunları da kapatmak isterseniz depoyu Settings › General › Danger Zone › Change repository visibility ile özel yapabilirsiniz. Taslak sürümler her iki durumda da gizlidir.

## Bir kez: imza anahtarı

1. Anahtar deposunu oluşturun ve güvenli bir yerde yedekleyin. Kaybedilirse aynı uygulamanın güncellemesi imzalanamaz.

   ```sh
   keytool -genkeypair -v -keystore nagmedar-release.jks -alias nagmedar \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

   Derleme, anahtar deposunun ve anahtarın parolasını aynı sayar. keytool'un varsayılan biçimi PKCS12'de ikisi zaten aynıdır.

2. GitHub › Settings › Secrets and variables › Actions › New repository secret ile üç gizli bilgi ekleyin:

   | Ad | Değer |
   | --- | --- |
   | `NAGMEDAR_KEYSTORE_BASE64` | `base64 -w0 nagmedar-release.jks` çıktısı (macOS'ta `base64 -i nagmedar-release.jks`) |
   | `NAGMEDAR_KEYSTORE_PASSWORD` | anahtar deposunun parolası |
   | `NAGMEDAR_KEY_ALIAS` | `nagmedar` |

## Her sürümde

1. [`CHANGELOG.md`](../../CHANGELOG.md) dosyasına `## <sürüm> — MVPn` bölümünü yazın; taslağın notları bu bölümdür.
2. Yeni bir MVP ise kök `build.gradle` dosyasında `versionName` ve `versionCode` değerlerini artırın: `versionCode = 4000000 + major·10000 + minor·100 + yama`, yani 0.2.0 için 4000200.
3. GitHub › Actions › CI › Run workflow: dal `master`, **İmzalı release APK üret** işaretli.
4. İş bitince Releases sayfasında “Nağmedar <sürüm>” taslağı durur: dört ABI'nin APK'sı ve `SHA256SUMS`. Tab S11 Ultra için `Nagmedar-<sürüm>-arm64-v8a.apk` dosyasını indirin.

Aynı sürüm yeniden derlenirse taslağın notları ve APK'ları yenilenir. Yayımlanmış bir sürüme CI dokunmaz; o durumda sürüm numarası artırılmalıdır. Bir `v*` etiketi göndermek de aynı taslağı üretir.

## Yan yana kurulanlar

| Derleme | Uygulama kimliği | İmza | Nereden |
| --- | --- | --- | --- |
| İmzalı sürüm | `app.nagmedar` | sizin anahtarınız | taslak GitHub sürümü |
| Debug | `app.nagmedar.debug` | depodaki ortak debug anahtarı | yalnız yerel derleme (CI 1.0'dan beri APK yayımlamaz: depo herkese açık, APK'da API anahtarları var) |
| Benchmark | `app.nagmedar.benchmark` | ortak debug anahtarı | yalnız yerel derleme ([ölçümler](../benchmark/README.md)) |

Üçü ayrı uygulamadır; kütüphaneleri ve ayarları birbirinden ayrıdır.
