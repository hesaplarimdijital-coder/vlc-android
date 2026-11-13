# Nağmedar sürümleri

## Kişisel sürüm (1.0'dan beri)

Nağmedar 1.0 yalnızca sizin iki cihazınız (Galaxy S24, Galaxy Tab S11 Ultra; ikisi de arm64) için derlenir ve hiçbir yerde yayımlanmaz. Kişisel sürüm (`personal` derleme türü):

- release kodudur: R8 kodu küçültür ve iyileştirir, kullanılmayan kaynakları atar; Baseline Profile içindedir (ilk açılışta ve kaydırırken JIT beklemez), hata ayıklanamaz ve profillenemez;
- yüklü debug derlemesinin kimliğini taşır: uygulama kimliği `app.nagmedar.debug`, depodaki ortak debug anahtarıyla imzalı. Bu yüzden onun **üzerine güncelleme olarak** kurulur; kütüphane, ayarlar, podcast abonelikleri, çalma listeleri ve indirilenler yerinde kalır;
- başlatıcıda adı yalnızca “Nağmedar”dır;
- Android 14 ve sonrasına kurulur (1.0.3'ten beri `minSdk 34`);
- Last.fm scrobble'ı ve Podcast Index araması API anahtarı olmadığından kapalı kalır; uygulama bunu kendi uyarısıyla söyler.

### Sürüm numaraları

Kök `build.gradle`: `versionName = '1.0.8'`, `versionCode = 4000000 + major·10000 + minor·100 + yama` = 4010008. Bir APK'nın kendi versionCode'u `10000000 + versionCode + ABI rakamı`dır (`application/app/build.gradle`; armeabi-v7a 5, arm64-v8a 6, x86 7, x86_64 8; ABI'ye bölünmemiş derleme 0).

| Derleme | versionName | versionCode |
| --- | --- | --- |
| Cihazlarınızdaki CI debug derlemesi (arm64, bölünmemiş) | 0.1.0 | 14000100 |
| Kişisel sürüm 1.0.0, arm64 | 1.0.0 | 14010006 |
| Kişisel sürüm 1.0.1, arm64 | 1.0.1 | 14010007 |
| Kişisel sürüm 1.0.2, arm64 | 1.0.2 | 14010008 |
| Kişisel sürüm 1.0.3, arm64 | 1.0.3 | 14010009 |
| Kişisel sürüm 1.0.4, arm64 | 1.0.4 | 14010010 |
| Kişisel sürüm 1.0.5, arm64 | 1.0.5 | 14010011 |
| Kişisel sürüm 1.0.6, arm64 | 1.0.6 | 14010012 |
| Kişisel sürüm 1.0.7, arm64 | 1.0.7 | 14010013 |
| Kişisel sürüm 1.0.8, arm64 | 1.0.8 | **14010014** |
| Kişisel sürüm 1.0.8, x86_64 (yalnızca CI emülatörü) | 1.0.8 | 14010016 |
| 1.0.8'den derlenen debug | 1.0.8 | 14010008 |

Kişisel sürümün numarası yüklü debug derlemesininkinden büyük olduğundan Android onu güncelleme sayar. Tersi olmaz: kişisel 1.0.8 kuruluyken aynı sürümden bir debug derlemesi (14010008) daha küçük numaralı olduğundan onun üzerine kurulamaz. Sonraki sürüm için `versionName` ve `versionCode` artırılır (1.0.9: 4010009, kişisel APK'sı 14010015). Yama ile ABI rakamı aynı basamağa toplandığından iki ayrı derleme türünün numaraları çakışabilir (kişisel 1.0.1 ile debug 1.0.7 ikisi de 14010007); kişisel sürümler kendi aralarında hep sırayla büyür.

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
   sh buildsystem/release/decrypt_apk.sh /tmp/nagmedar-teslim ~/nagmedar/delivery.key Nagmedar-1.0.8.apk
   git worktree remove /tmp/nagmedar-teslim
   ```

   Betik APK'nın SHA-256'sını zarftakiyle karşılaştırır; tutmazsa dosyayı siler.
4. Kurun: USB hata ayıklamasıyla `adb install -r Nagmedar-1.0.8.apk`, ya da dosyayı cihaza kopyalayıp Dosyalarım'dan açın. Android “Güncelle” der; “Uygulama yüklenmedi” derse yüklü olan başka bir anahtarla imzalanmıştır (bu durumda kaldırmadan önce Ayarlar › Yedekleme'den yedek alın).

Kurulum güncelleme olduğundan ilk açılışta kütüphane yeni sürüme göre bir kez gözden geçirilir: veritabanı 22'ye geçer, 0.5'ten kalan video ve TV Lab işleri, dosyaları (çeviri modelleriyle yaklaşık 1 GB) ve artık kullanılmayan klasör izinleri bir kez temizlenir. Müziğinize, podcast indirmelerinize, dökümlere ve radyo kayıtlarınıza dokunulmaz. 1.0.0'dan 1.0.1'e güncellemede veritabanı değişmez, her şey olduğu gibi kalır. 1.0.2'ye güncellemede veritabanı 23'e geçer (yalnız Whisper'ın sütunları çıkar; programlar, bölümler, dinleme yerleri ve indirmeler kalır) ve Whisper'ın bekleyen işleri, indirilmiş modelleri ve ürettiği dökümler bir kez silinir; yayınların kendi dökümlerine dokunulmaz. 1.0.3'e güncellemede veritabanı 24'e geçer (ses bilgisi tablosuna ReplayGain'in dört sütunu eklenir, satırlar kalır) ve her müzik dosyasının başlığı ReplayGain değerleri için arka planda bir kez yeniden okunur; film bilgisinin 0.5'ten kalan veritabanı silinir. Müzik listesi ilk açılışta bir kez eskisi gibi okunur ve cihazda saklanır; sonraki açılışlarda hemen gelir, kütüphane her açılışta yeniden taranmaz (Ayarlar › Kütüphane › “Kütüphaneyi yeniden tara” elle tarar). Sürümlerin notları [`CHANGELOG.md`](../../CHANGELOG.md) dosyasındadır.

### R8 ve kurallar

R8 bütün release türü derlemelerde açıktır (release, signedRelease, benchmark, personal). Kurallar [`application/app/proguard-rules.pro`](../../application/app/proguard-rules.pro) dosyasındadır:

- Adlar değiştirilmez (`-dontobfuscate`): cihazdaki bir çökme kaydı eşleme dosyası olmadan okunur (CI'dan şifreli APK'dan başka bir şey çıkmaz) ve debug derlemesinin adla yazdığı şeyler (WorkManager'ın iş sınıfları, Moshi'nin JSON alanları, bekleyen alarmlardaki Parcelable'lar) güncellemeden sonra da aynı okunur. R8 yine de kullanılmayanı atar, satır içine alır ve iyileştirir.
- libVLC (`org.videolan.libvlc.**`) ve medya kütüphanesi (`org.videolan.medialibrary.**`, yerel koddan kurulan sınıflarıyla) bütünüyle korunur: JNI adları silinirse uygulama ancak çalışırken çöker.
- Müzik motorunun (`app.nagmedar.audio.NativeAudio`, olay ve ilerleme geri çağrıları) ve laboratuvarın (`app.nagmedar.lab.NativeLab`: medya incelemesi, USB ses) yerel yöntemleri ve yerel koddan adıyla çağrılan arayüzleri.
- Oynatma hizmetinin adıyla bulduğu gözlemciler, WorkManager'ın işçileri, Moshi'nin yansımayla okuduğu sınıfların alanları (ayar yedeği, ekolayzer dışa aktarımı, uzaktan erişimin JSON'u ve oturumları, lisanslar), Ktor'un Netty motoru, Parcelable ve Serializable sınıfları.
- Room, WorkManager, Media3, Compose, OkHttp ve androidx kitaplıkları kendi kurallarını getirir.
- Kaynak küçültücü uzaktan erişim sayfasının adıyla istediği simgeleri korur (`application/app/src/main/res/raw/nagmedar_keep.xml`).

CI'ın “Uygulama · release” bacağı her itmede arm64 kişisel derlemeyi ve benchmark derlemesini R8 ile derler; APK boyutları işin notlarında görünür. Emülatör iş akışı kişisel derlemeyi gerçekten çalıştırır:

```sh
gh api repos/hesaplarimdijital-coder/vlc-android/actions/workflows/emulator.yml/dispatches \
  -f ref=master -f 'inputs[build]=personal' -f 'inputs[api_levels]=35,37.0'
```

1.0.3'ten beri yürüyüş her Android sürümünde iki iştir: tablet işi (tablet, iş istasyonu, Türkçe telefon) ve telefon işi (İngilizce telefon, üç düğmeli gezinme; kişisel derlemede Android Auto'nun ağacı da okunur). “Emülatör · özet” işi ikisinin sonucunu tek satırda toplar. Kanıtlar (rapor, ekran görüntüleri, StrictMode kaydı) `ci/emulator[-api<sürüm>][-personal]-<tablet|phone>` dallarına yazılır: debug ve API 35 için `ci/emulator-tablet` ile `ci/emulator-phone`, kişisel derleme ve Android 17 için `ci/emulator-api37.0-personal-tablet` ile `ci/emulator-api37.0-personal-phone`.

1.0.5'ten beri bir sürümden önce `page-opens` geçişi de gezilir:

```sh
gh api repos/hesaplarimdijital-coder/vlc-android/actions/workflows/emulator.yml/dispatches \
  -f ref=master -f 'inputs[build]=personal' -f 'inputs[api_levels]=37.0' -f 'inputs[passes]=page-opens'
```

Öteki geçişler sayfaları sistemin animasyonları kapalıyken açar: geçiş o zaman tek karedir, ortak öğeler ve cam hiç kurulmaz. `page-opens` telefonun ekran boyutunda (1080 × 2340, 480 dpi; koyu tema, üç düğme), animasyonlar açık ve oynatıcı boşken gezer: temiz uygulamada bir program adresiyle eklenir, sayfası satırından ve kutucuğundan açılır, üstüne bir bölüm gelip gider. Her açılış piksellerinden okunur ve sayfa kaydırılır. 1.0.3 ve 1.0.4'te program sayfası açılınca boş kalıyor ya da kaymıyordu; animasyonları kapalı gezen hiçbir geçiş bunu görmedi. Kanıtı `ci/emulator-api37.0-personal-page-opens` dalına yazılır; `inputs[ref]` ile eski bir sürüm aynı geçişle gezilebilir.

1.0.6'dan beri iki geçiş daha gezilir:

```sh
gh api repos/hesaplarimdijital-coder/vlc-android/actions/workflows/emulator.yml/dispatches \
  -f ref=master -f 'inputs[build]=personal' -f 'inputs[api_levels]=37.0' -f 'inputs[passes]=notice-swipe'
gh api repos/hesaplarimdijital-coder/vlc-android/actions/workflows/emulator.yml/dispatches \
  -f ref=master -f 'inputs[build]=personal' -f 'inputs[passes]=auto-shots'
```

`notice-swipe` bir uyarıyı parmağın kaydırdığı gibi kaydırır: dokunma olayları emülatörün dokunmatik ekranına çekirdeğin verdiği biçimde yazılır (`adb` kök yetkisiyle), son hareket ve kalkış birlikte gelir. `input` ve UiAutomator her olayı uygulama işleyene kadar bekler; hareket hâlinde kalkan bir parmağın yaptığını yapamazlar. 1.0.3–1.0.5'te böyle bırakılan uyarı bırakıldığı yerde kalıyordu ve hiçbir geçiş bunu görmedi. Telefonun ekran boyutunda, animasyonlar açık gezer; uyarının yeri bırakıştan sonra piksellerden okunur.

`auto-shots` başka bir emülatörde gezer: Android Automotive (API 34; `api_levels` girdisine bakmaz). Arabanın medya uygulaması uygulamanın oynatma hizmetine Android Auto'nun bağlandığı gibi bağlanır ve Android Auto'nun gezdiği ağacı çizer: sekmeler, listeler, çalan parça ve arabanın oynatıcısı. Android Auto'nun kendisi emülatörde çalıştırılamaz (masaüstü ana birimi, Play Store'a giriş yapılmış bir telefon ister); görüntüler arabanın sisteminin çizimidir. Geçiş 1.0.6'da bir çökmeyi buldu: arabanın medya uygulaması bağlanınca uygulama ölüyordu.

1.0.7'den beri arama da gezilir:

```sh
gh api repos/hesaplarimdijital-coder/vlc-android/actions/workflows/emulator.yml/dispatches \
  -f ref=master -f 'inputs[build]=personal' -f 'inputs[api_levels]=37.0' -f 'inputs[passes]=search'
```

`search` aramayı, her etiketini yürüyüşün bildiği kütüphaneyle gezer: 64 parça (“Profile track a.t”), sekiz albüm (“Profile album a”), dört sanatçı (“Nagmedar Artist 1–4”, ikişer albüm). Tabletin ekranında klavyenin Ctrl+K'si ile açılan palete bir sanatçının adı, bir albümün adı, her parçada geçen bir kelime ve bir parça adı sanatçısıyla birlikte yazılır; listelenen her satır okunur (listenin başı, sonu ve arası: seçim oklarla indirilir, her okuma bir öncekiyle ortak bir satır paylaşır; emülatörün ekran klavyesi yazdıktan sonra Geri ile kapatılır) ve kütüphanenin aramanın kuralına göre vermesi gerekenlerle karşılaştırılır: yazılan her kelime adda, sanatçıda ya da albümde. Bir sanatçı ve bir albüm paletten Müzik'te açılır; yalnız en iyileri gösterilen türün altındaki satır okunur ve Enter ile açılır. Sonra telefonun ekranında arama sayfası: sanatçının adı için sanatçılar, albümler ve parçalar, bir albümün açılması. 1.0.6'da palet parçayı yalnız adında arıyor ve her türden altı satır gösteriyordu; öteki geçişler palete tek bir sorgu yazıp ilk sonucu seçiyordu ve bunu görmedi. Kanıtı `ci/emulator-api37.0-personal-search` dalına yazılır; `inputs[ref]` ile eski bir sürüm aynı geçişle gezilebilir.

### Android 17 (API 37)

Uygulama Android 17'nin SDK'sıyla derlenir ve onu hedefler (`compileSdk 37`, `targetSdk 37`). Hedefi yükseltme kararını 12 Kasım 2026'da siz verdiniz (“37”); 1.0.2'ye kadar hedef 36'ydı.

Hedeften bağımsız olarak Android 17'li cihazda geçerli olanlar:

- **Arka planda ses**: Android 17 sesi, ses odağını ve ses düzeyini yalnızca görünen bir pencereye ya da çalışan bir ön plan hizmetine verir. Oynatma hizmeti motor çalmadan ve odak istemeden önce ön plana geçer (kulaklık düğmesi, kilit ekranı, Android Auto ve widget'tan sürdürme); alarm ses düzeyini hizmet ön plandayken yükseltir.
- **Widget bit eşlem sınırı**: ana ekran widget'ının kapağı yerinin boyutunda (96 dp) kare olarak gönderilir, `6 × genişlik × yükseklik` baytı geçmez; reddedilen güncelleme yer tutucuyla yeniden gönderilir.

Hedef 37 ile gelenler (1.0.3'te devrede):

- **Yerel ağ izni** (`ACCESS_LOCAL_NETWORK`, sistemde “Yakındaki cihazlar”): yalnızca 37'yi hedefleyen derleme bildirir (36'da bildirmek Android 17'de yerel ağı keserdi; izin `app/flavors/targetSdk37/AndroidManifest.xml` dosyasındadır). Kaynaklar › Ağ, Yayınla…, Ayarlar › Uzaktan erişim ve yerel ağdaki bir radyo ya da akış adresi izni bir kez sorar; reddedilince sayfa nedenini ve “İzin ver”i, kalıcı reddedilince “Ayarları aç”ı gösterir.
- **Alarm**: kilit ekranında alarm sayfasıyla çalar (tam ekran bildirim); telefon kullanılırken sesli bir bildirim gelir, dokununca müzik başlar.
- Kullanıcı rehberi ikisini de anlatır (Ağ kaynakları, Alarm, Yayınlama, Uzaktan erişim).
- Bilinen sınır: uygulama arka plandayken ve hiçbir şey çalmıyorken uzaktan erişimden verilen “oynat” komutu sessiz kalabilir (Android 17 arka plandan ses başlatılmasına izin vermez).
- Ayrıca (uygulamayı etkilemeyenler): büyük ekranda yön ve boyut kısıtlarından çıkış yok (uygulama zaten her yöne ve boyuta uyuyor), Certificate Transparency varsayılan (radyo akışlarının HTTPS sunucuları), `System.load()` ile yüklenen kitaplıkların salt okunur olması (Nağmedar kitaplıklarını APK'dan yükler), statik `final` alanlara yazılamaması (JNI dahil), RFCOMM okumalarının `-1` dönmesi (Nağmedar Bluetooth soketi kullanmaz).

Hedef iki yerde yazar: `settings.gradle` dosyasında `targetSdk`, `build.gradle` dosyasında `targetSdkVersion`. 36'ya dönmek bu ikisini değiştirmektir (izin ve alarm sayfası kendiliğinden kapanır). Emülatör iş akışı her sürümde Android 17 imajında da yürür: `-f 'inputs[api_levels]=35,37.0'`. Emülatör Samsung'un One UI'ını çalıştırmaz: One UI 9'a özgü olanlar (Now Bar, kilit ekranının oynatıcısı, DeX'in masaüstleri ve üst-alt yaslaması) cihaz test listesindedir.

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
