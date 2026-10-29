# Nağmedar sürümleri

## Kişisel sürüm (1.0'dan beri)

Nağmedar 1.0 yalnızca sizin iki cihazınız (Galaxy S24, Galaxy Tab S11 Ultra; ikisi de arm64) için derlenir ve hiçbir yerde yayımlanmaz. Kişisel sürüm (`personal` derleme türü):

- release kodudur: R8 kodu küçültür ve iyileştirir, kullanılmayan kaynakları atar; Baseline Profile içindedir (ilk açılışta ve kaydırırken JIT beklemez), hata ayıklanamaz ve profillenemez;
- yüklü debug derlemesinin kimliğini taşır: uygulama kimliği `app.nagmedar.debug`, depodaki ortak debug anahtarıyla imzalı. Bu yüzden onun **üzerine güncelleme olarak** kurulur; kütüphane, ayarlar, podcast abonelikleri, çalma listeleri ve indirilenler yerinde kalır;
- başlatıcıda adı yalnızca “Nağmedar”dır;
- Last.fm scrobble'ı ve Podcast Index araması API anahtarı olmadığından kapalı kalır; uygulama bunu kendi uyarısıyla söyler.

### Sürüm numaraları

Kök `build.gradle`: `versionName = '1.0.1'`, `versionCode = 4000000 + major·10000 + minor·100 + yama` = 4010001. Bir APK'nın kendi versionCode'u `10000000 + versionCode + ABI rakamı`dır (`application/app/build.gradle`; armeabi-v7a 5, arm64-v8a 6, x86 7, x86_64 8; ABI'ye bölünmemiş derleme 0).

| Derleme | versionName | versionCode |
| --- | --- | --- |
| Cihazlarınızdaki CI debug derlemesi (arm64, bölünmemiş) | 0.1.0 | 14000100 |
| Kişisel sürüm 1.0.0, arm64 | 1.0.0 | 14010006 |
| Kişisel sürüm 1.0.1, arm64 | 1.0.1 | **14010007** |
| Kişisel sürüm 1.0.1, x86_64 (yalnızca CI emülatörü) | 1.0.1 | 14010009 |
| 1.0.1'den derlenen debug | 1.0.1 | 14010001 |

Kişisel sürümün numarası yüklü debug derlemesininkinden büyük olduğundan Android onu güncelleme sayar. Tersi olmaz: kişisel 1.0.1 kuruluyken aynı sürümden bir debug derlemesi (14010001) daha küçük numaralı olduğundan onun üzerine kurulamaz. Sonraki sürüm için `versionName` ve `versionCode` artırılır (1.0.2: 4010002, kişisel APK'sı 14010008). Yama ile ABI rakamı aynı basamağa toplandığından iki ayrı derleme türünün numaraları çakışabilir (kişisel 1.0.1 ile debug 1.0.7 ikisi de 14010007); kişisel sürümler kendi aralarında hep sırayla büyür.

### Bir kez: teslim anahtarı

CI APK'yı sizin teslim anahtarınızın açık yarısıyla ([`delivery.pub`](delivery.pub)) şifreler; açmak için gizli yarısı `delivery.key` gerekir. O dosya yalnızca sizin bilgisayarınızda durur, depoya, GitHub'a ya da bir gizli bilgiye hiçbir zaman girmez. Kaybedilirse eski teslimler açılamaz: yeni bir anahtar çifti üretip `delivery.pub` dosyasını değiştirmek yeter (`openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out delivery.key && openssl pkey -in delivery.key -pubout -out delivery.pub`).

### Her sürümde

1. GitHub › Actions › CI › Run workflow: dal `master`, **Kişisel sürüm** işaretli. Komut satırından:

   ```sh
   gh api repos/hesaplarimdijital-coder/vlc-android/actions/workflows/ci.yml/dispatches \
     -f ref=master -F 'inputs[personal_release]=true'
   ```

2. “Kişisel sürüm (şifreli)” işi arm64 için `assemblePersonal` derler (lab ve müzik motoru kitaplıklarıyla). Hiçbir şey dışarı çıkmadan APK'yı denetler: kimliği `app.nagmedar.debug`, hata ayıklanamıyor, ortak debug anahtarıyla imzalı, Baseline Profile'ı ve müzik motoru içinde. Sonra [`encrypt_apk.sh`](encrypt_apk.sh) ile şifreler ve yalnızca şifreli dosyaları yetim `ci/personal-release` dalına koyar. Dal her çalıştırmada tek bir commit'le yenilenir; README'sinde versionName, versionCode, derlenen commit ve APK'nın SHA-256'sı yazar. APK hiçbir zaman artefakt olarak yüklenmez.
3. Bilgisayarınızda açın (OpenSSL 1.1.1 ya da daha yenisi gerekir; macOS'ta `brew install openssl`):

   ```sh
   git fetch origin ci/personal-release
   git worktree add /tmp/nagmedar-teslim origin/ci/personal-release
   cat /tmp/nagmedar-teslim/README.md
   sh buildsystem/release/decrypt_apk.sh /tmp/nagmedar-teslim ~/nagmedar/delivery.key Nagmedar-1.0.1.apk
   git worktree remove /tmp/nagmedar-teslim
   ```

   Betik APK'nın SHA-256'sını zarftakiyle karşılaştırır; tutmazsa dosyayı siler.
4. Kurun: USB hata ayıklamasıyla `adb install -r Nagmedar-1.0.1.apk`, ya da dosyayı cihaza kopyalayıp Dosyalarım'dan açın. Android “Güncelle” der; “Uygulama yüklenmedi” derse yüklü olan başka bir anahtarla imzalanmıştır (bu durumda kaldırmadan önce Ayarlar › Yedekleme'den yedek alın).

Kurulum güncelleme olduğundan ilk açılışta kütüphane yeni sürüme göre bir kez gözden geçirilir: veritabanı 22'ye geçer, 0.5'ten kalan video ve TV Lab işleri, dosyaları (çeviri modelleriyle yaklaşık 1 GB) ve artık kullanılmayan klasör izinleri bir kez temizlenir. Müziğinize, podcast indirmelerinize, dökümlere ve radyo kayıtlarınıza dokunulmaz. 1.0.0'dan 1.0.1'e güncellemede veritabanı değişmez, her şey olduğu gibi kalır. Sürümlerin notları [`CHANGELOG.md`](../../CHANGELOG.md) dosyasındadır (1.0.1, 1.0.0).

### R8 ve kurallar

R8 bütün release türü derlemelerde açıktır (release, signedRelease, benchmark, personal). Kurallar [`application/app/proguard-rules.pro`](../../application/app/proguard-rules.pro) dosyasındadır:

- Adlar değiştirilmez (`-dontobfuscate`): cihazdaki bir çökme kaydı eşleme dosyası olmadan okunur (CI'dan şifreli APK'dan başka bir şey çıkmaz) ve debug derlemesinin adla yazdığı şeyler (WorkManager'ın iş sınıfları, Moshi'nin JSON alanları, bekleyen alarmlardaki Parcelable'lar) güncellemeden sonra da aynı okunur. R8 yine de kullanılmayanı atar, satır içine alır ve iyileştirir.
- libVLC (`org.videolan.libvlc.**`) ve medya kütüphanesi (`org.videolan.medialibrary.**`, yerel koddan kurulan sınıflarıyla) bütünüyle korunur: JNI adları silinirse uygulama ancak çalışırken çöker.
- Müzik motorunun (`app.nagmedar.audio.NativeAudio`, olay ve ilerleme geri çağrıları) ve laboratuvarın (`app.nagmedar.lab.NativeLab`: medya incelemesi, whisper, USB ses) yerel yöntemleri ve yerel koddan adıyla çağrılan arayüzleri.
- Oynatma hizmetinin adıyla bulduğu gözlemciler, WorkManager'ın işçileri, Moshi'nin yansımayla okuduğu sınıfların alanları (ayar yedeği, ekolayzer dışa aktarımı, uzaktan erişimin JSON'u ve oturumları, lisanslar), Retrofit'in arayüzleri, Ktor'un Netty motoru, Parcelable ve Serializable sınıfları.
- Room, WorkManager, Media3, Compose, OkHttp ve androidx kitaplıkları kendi kurallarını getirir.
- Kaynak küçültücü uzaktan erişim sayfasının adıyla istediği simgeleri korur (`application/app/src/main/res/raw/nagmedar_keep.xml`).

CI'ın “Uygulama · release” bacağı her itmede arm64 kişisel derlemeyi ve benchmark derlemesini R8 ile derler; APK boyutları işin notlarında görünür. Emülatör iş akışı kişisel derlemeyi gerçekten çalıştırır:

```sh
gh api repos/hesaplarimdijital-coder/vlc-android/actions/workflows/emulator.yml/dispatches \
  -f ref=master -f 'inputs[build]=personal'
```

### Android 17 (API 37)

Uygulama Android 17'nin SDK'sıyla derlenir (`compileSdk 37`; AGP 9.1.1 API 37'yi destekleyen ilk sürümdür), ama davranışı Android 16'nınkidir (`targetSdk 36`). Android 17'yi hedeflemek şunları getirir ve şimdilik karşılanmadıkları için hedef 36'da kaldı:

- **Yerel ağ izni** (`ACCESS_LOCAL_NETWORK`, çalışma zamanında istenir): olmadan Ağ kaynakları (SMB, NFS, UPnP/DLNA, WebDAV), yayınlama ve uzaktan erişim yerel ağa ulaşamaz. Uygulama bu izni henüz istemiyor.
- **Arka planda ses**: arka planda çalma, ses odağı ve ses düzeyi yalnızca çalışan bir ön plan hizmetiyle; alarmın sesi için tam zamanlı alarm izni ve `USAGE_ALARM` gerekir. Oynatma hizmeti ön plandadır, ama Uyku ve alarm özelliği ile radyo kaydı ayrıca denenmeli.
- **Widget bit eşlem sınırı**: ana ekran widget'ının kapak resmi `6 × genişlik × yükseklik` baytı geçerse güncelleme reddedilir; kapaklar buna göre küçültülmeli.
- Ayrıca: büyük ekranda yön ve boyut kısıtlarından çıkış yok (uygulama zaten her yöne ve boyuta uyuyor), Certificate Transparency varsayılan (radyo akışlarının HTTPS sunucuları), `System.load()` ile yüklenen kitaplıkların salt okunur olması (Nağmedar kitaplıklarını APK'dan yükler), statik `final` alanlara yazılamaması (JNI dahil), RFCOMM okumalarının `-1` dönmesi (Nağmedar Bluetooth soketi kullanmaz).

Bunlar karşılanınca `build.gradle` dosyasında `targetSdkVersion = 37` ve `settings.gradle` dosyasında `targetSdk = 37` yapılır. Emülatör iş akışı Android 17 imajında da yürür: `-f 'inputs[api_levels]=35,37.0'` (kanıtlar `ci/emulator-api37.0` dalında).

## İmzalı sürüm (S-1503, şimdilik kullanılmıyor)

Her MVP bir sürümdür: MVP1 0.1.0, MVP2 0.2.0 ve sonrası aynı sırayla. İmzalı APK'lar **yalnızca taslak GitHub sürümlerine** gider. Taslakları yalnızca depoya yazabilenler (şimdilik yalnızca siz) görür. CI hiçbir sürümü yayımlamaz ve imzalı APK'yı artefakt olarak yüklemez. Herkese açık yayın ve F-Droid derlemesi sonraya bırakıldı (12 Ekim 2026). Nağmedar 1.0 kişisel sürümle teslim edilir; imzalı sürüm `app.nagmedar` kimliğiyle ayrı bir uygulama olarak kurulurdu.

### Bir kez: imza anahtarı

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

### Her sürümde

1. [`CHANGELOG.md`](../../CHANGELOG.md) dosyasına `## <sürüm> — MVPn` bölümünü yazın; taslağın notları bu bölümdür.
2. Kök `build.gradle` dosyasında `versionName` ve `versionCode` değerlerini artırın (yukarıdaki formül).
3. GitHub › Actions › CI › Run workflow: dal `master`, **İmzalı release APK üret** işaretli.
4. İş bitince Releases sayfasında “Nağmedar <sürüm>” taslağı durur: dört ABI'nin APK'sı ve `SHA256SUMS`.

Aynı sürüm yeniden derlenirse taslağın notları ve APK'ları yenilenir. Yayımlanmış bir sürüme CI dokunmaz; o durumda sürüm numarası artırılmalıdır. Bir `v*` etiketi göndermek de aynı taslağı üretir.

## Yan yana kurulanlar

| Derleme | Uygulama kimliği | İmza | Nereden |
| --- | --- | --- | --- |
| Kişisel sürüm | `app.nagmedar.debug` | depodaki ortak debug anahtarı | CI'ın kişisel sürüm işi, şifreli (`ci/personal-release`) |
| Debug | `app.nagmedar.debug` | depodaki ortak debug anahtarı | yalnız yerel derleme (CI 1.0'dan beri APK yayımlamaz) |
| Benchmark | `app.nagmedar.benchmark` | ortak debug anahtarı | yalnız yerel derleme ([ölçümler](../benchmark/README.md)) |
| İmzalı sürüm | `app.nagmedar` | sizin anahtarınız | taslak GitHub sürümü |

Kişisel sürüm ile debug aynı uygulamadır (biri ötekini günceller). Benchmark ve imzalı sürüm ayrı uygulamalardır; kütüphaneleri ve ayarları ayrıdır.
