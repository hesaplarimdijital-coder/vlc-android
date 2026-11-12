Nağmedar 1.0.5: listeden açılan program sayfası kaybolmaz ve kaydırılır; alt camın altındaki çizgi kapanır; bir sayfa açılıp kapanınca alt çubuk geri gelir.

## Bildirim
Sahibi, 1.0.4'ü Galaxy S24'te (Android 17, 1080 × 2340, 480 dpi, üç düğme, koyu tema) denerken, ekran kaydıyla (12 Kasım):
1. “Podcastte sayfası böyle oluyor. İlk yüklediğimde açılmıştı o zaman da aşağı scroll yapılmıyordu.” Kayıtta: programın satırına dokunuyor, sayfa kayarak geliyor, açılışın bittiği karede sayfanın tamamı gidiyor; liste ancak Geri ile dönüyor.
2. “Alt navigasyon da yukarı kayıyor dikkat edersen arkası gözüküyor.” Kayıtta: pencerenin en alt satırında, alt camın altında, arkadaki sayfa keskin görünüyor.

İkisi de 1.0.3'ten beri vardı. Benim doğrulamamdan kaçtı: emülatör yürüyüşleri sayfaları sistemin animasyonları kapalıyken açıyordu, testler sayfa açıkken başlıyordu.

# 1. Program sayfası

## Neden: iki ayrı hata, aynı yerde
1.0.3 telefonun “liste → ayrıntı” geçişini Compose'un aranabilir geçişine (`SeekableTransitionState`) ve ortak öğelere (`SharedTransitionLayout`: satırdaki kapak sayfadaki yerine uçar) bağladı; içeriği yalnız geçiş sürerken ortak öğelerin yerleşimine alıyor, geçiş bitince düz bir kutuya geri taşıyordu (`DepthHost`).

**a. Sayfa kayboluyor.** Varmış bir aranabilir geçiş animasyonlarını yolun bıraktığı gibi tutar (eski durumun değerinden yeninin değerine) ve oranını 0'a çeker. İçindeki animasyonların toplam süresi sonradan değişirse hepsini o orana yeniden sarar: yolun başına. Geçiş de o andan sonra “çalışıyor” der. Süre, varıştan sonraki karede, ortak öğelerin animasyonları kalkarken değişir; en uzunu onlardan biriyse. O zaman ayrıntının yolu başa döner: sayfa tam bir ekran genişliği sağda, görünmeyen yerde durur. “Hareket var” kararı geçişin “çalışıyor” demesine de baktığı için derinlik ortak öğe yerleşimine geri girip orada kalıyordu. Geri'deki kapanış da o konumdan başladığından oynamıyordu.

**b. Sayfa kaymıyor.** Compose'un tembel listesi bir kez “ileriye bakarak” ölçülünce (`LazyListState.hasLookaheadOccurred`, sıfırlanmaz) sonraki her ölçümü iki geçişin ikincisi sayar ve kaydırmayı ondan almaz. Geçiş bitip içerik düz kutuya dönünce program sayfası da, altındaki liste de böyle birer listeydi: görünen satırların payı kadar (25–27 dp) oynayıp duruyordu. Sayfanın kaybolmadığı açılışlar (abone olunca uygulamanın açtığı sayfa, kutucuktan açılan sayfa) böyleydi: sahibin “açılmıştı, o zaman da scroll yapılmıyordu” dediği.

Kütüphanelerin kaynağından okundu (androidx `Transition.kt`: `onTotalDurationChanged` → `seekToFraction`; `LazyListState.applyMeasureResult`), sonra testle ve emülatörle doğrulandı.

## Düzeltme
- `DepthHost`: ortak öğeli bir derinliğin içeriği yerleşime bir kez girer ve orada kalır (dururken de; ortak öğeler sonradan kapansa da: pil tasarrufu, pencerenin genişlemesi). İçeriğin tek bir yeri vardır; geçişle yalnız iki yanın uçuş bağları gelir gider. Ortak öğesiz derinlik (albüm, sanatçı, tür, çalma listesi, bölüm) eskisi gibi düz kutudur.
- `rememberDepth`: geçişin değerleri her varışta yeniden kurulur (anahtarı geçişin vardığı durum). Duran bir animasyonun başı da sonu da o durumun kendi değeridir: nereye sarılırsa sarılsın yerinde kalır, sonraki yol oradan başlar.
- `DepthState.detailLayer`: varmış ve açık ayrıntı yerini geçişin değerlerinden değil durumdan alır.
- `DepthState.moving`: yalnız derinliğin kendi durumlarına bakar; geçişin “çalışıyor” demesine bakmaz.

Bedeli: Podcast sekmesinin listesi ve program sayfası telefonda kaydırılırken karede iki kez ölçülür (ortak öğe yerleşiminin gereği). 1.0.3 bundan kaçınmak için içeriği taşıyordu; liste kaymadığı sürece bu kazanç değildi.

## Ne bilinmiyor
Sahibin satırında hangi ortak öğe animasyonunun ayrıntıdan uzun sürdüğü ölçülmedi. İlk commit'lerde yazdığım “kapak ~500 pikselden uzağa uçarsa” tahmini tutmadı: emülatörde listenin tepesindeki satırın kapağı sayfadaki yerine ~120 piksel uzaktayken de sayfa kayboldu. Kesin olan: bir yanın, ayrıntının kendi yolundan uzun süren bir animasyonu sayfayı kaybettirir (`DetailDepthTest`), ve düzeltme bu animasyonun hangisi olduğuna bağlı değildir.

# 2. Alt camın altındaki çizgi

Haze 2.0.1 bulanıklaştırdığı görüntüyü yüzeyin 0,79 katı boyunda, tam piksellik bir katmana kaydeder (`createScaledContentLayer`: `(layerSize * scaleFactor).roundToIntSize()`) ve tam 1/0,79 kat büyüterek geri çizer (`drawScaledContent`). Boy aşağı yuvarlanınca bulanık görüntü yüzeyin alt ya da bitiş kenarına 0,6 piksele kadar yetişmez; o şeritte bulanıklık da renk de çizilmez, arkadaki içerik olduğu gibi görünür. Telefonun alt camı dururken 423 piksel: 334 kaydedilir, 422,5 çizilir. Çubuklar inince 228: 180 kaydedilir, 227,7 çizilir; son satırın ortası örtülü kalır. Kayıtta çizginin dururken görünüp kaydırırken kaybolması bununla uyuşuyor.

`glass()` artık önce malzemenin kendi opak rengini, üstüne bulanıklığı çizer.

**Bu düzeltme emülatörde görülemedi:** telefonun ölçülerindeki emülatör o şeridi çizmiyor (hata orada hiç görünmedi). Dayanağı kaynak koddaki hesaptır; telefonda bakılması gerekir.

# 3. Alt çubuk (bildirilmedi; düzeltilen sayfanın yürüyüşünde görüldü)

Aşağı kaydırmak alt çubuğu indirir, yalnız yukarı kaydırmak geri getirirdi (Desktop Kit 0.2.0'ın kuralı). Kaydırılamayacak kadar kısa liste onu geri getiremez: tek programlı listede, programın sayfası kaydırılıp Geri'ye basılınca liste ve sonra açılan her sayfa alt çubuksuz kalıyordu. 1.0.3 ve 1.0.4'te sayfa hiç kaymadığı için ortaya çıkmıyordu; 1.0.5 bunu görünür kılacaktı.

“Her ekran çubuklar görünürken başlar” kuralı sekmeler arasında vardı; artık telefonun ekran içinde açtığı sayfalar için de geçerli: `ShellScrollState.showBars`, `DetailTransition` ve `ListDetailPanes` ayrıntı açılınca, kapanınca ya da değişince çağırır.

# Testler
- `PodcastPageOpensTest` (yeni): telefonun kabuğunda Podcast sekmesi, sahibin ekran boyutunda, açık sayfa yok. Son satırdan açılan sayfa yerinde ve yirmi kare sonra da yerinde; kaydırılınca üstü ekrandan çıkıyor; üstüne bölüm sayfası gelip gidiyor; Geri ile kapanıyor; ilk satırdan ve yeniden son satırdan açılıyor; kaydırınca alt çubuk iniyor, Geri'den sonra yerinde.
- `DetailDepthTest`: yanında ayrıntının yolundan uzun süren bir animasyon olan ayrıntı yerinde kalır, kaydırılır, kapanırken yolunu oynar, yeniden açılır; derinlik durur. Ortak öğeli derinliğin ayrıntısı kendi yolundan sonra da kaydırılır; listesi dururken, ayrıntı gelip gittikten sonra ve hareket sonradan azaltılınca kaydırılır. 1.0.3'ün “ortak öğeli derinliğin duran listesi ileriye bakılarak ölçülmez” testi artık ortak öğesiz derinlik içindir.
- `ShellScrollStateTest`: ekran içinde açılan ya da kapanan sayfa çubukları geri getirir.

## Düzeltmeden önce kırmızı
- Yalnız testler, 1.0.4'ün koduyla (commit `b27c2667`, koşu 43275028253): 7 test kırmızı. `PodcastPageOpensTest`'in üçü: sayfa `Rect(1080, 168, 2160, 1809)`, bir ekran genişliği sağda. `DetailDepthTest`'in dördü: ayrıntı 410,7 dp sağda; derinliğin değerleri yolun başında (ayrıntının yolu 1000‰, kartın ayrıntısı 0, kartın listesi 1000), “hareket var”, geçiş “çalışıyor”.
- Yalnız değerler düzeltilmiş, içerik hâlâ düz kutuya dönerken (commit `ba6df4bf`, koşu 43277885554): sayfa yerinde ama `DetailDepthTest`'in kaydırma testi kırmızı (“the rows moved under the finger”). Hata (b) böyle bulundu.

# Yürüyüş (emülatör)
Yeni geçiş `page-opens`: sahibin ekran boyutunda, koyu tema, üç düğme, **sistemin animasyonları açık**, oynatıcı boş, temiz uygulama. Uzun bir yayın (80 bölüm, kapak, uzun açıklama) adresiyle eklenir. Sırayla: abone olunca uygulamanın açtığı sayfa; satırdan iki açılış; sayfanın üstüne bir bölüm ve geri dönüş; kutucuktan iki açılış. Her açılış dokunuştan 0,3, 0,8 ve 2,5 saniye sonra arayüze hiçbir şey sorulmadan piksellerinden okunur (boş sayfa %0,0); sonra sayfa 842 piksel kaydırılır ve görünen bir bölümün bunun en az %40'ı kadar yukarı gitmesi beklenir; her açılışta ve her Geri'den sonra alt çubuğun yerinde olduğuna bakılır. `buildsystem/release/README.md` bu geçişi sürüm öncesi adımlara ekler.

Geçişin ilk kaydırma denetimi “sayfanın yerinin kaçta kaçı değişti”ye bakıyordu ve 1.0.4'ün kaymayan sayfasını geçirdi (%17 değişmişti). Ekran görüntülerine bakınca görüldü; denetim sayfanın parmağı izlemesini ölçecek biçimde değiştirildi.

## Sürüm
`versionName = '1.0.5'`, `versionCode = 4010005`; kişisel arm64 APK'sı **14010011**. `CHANGELOG.md`, `buildsystem/release/README.md`. Baseline Profile 1.0.3'ünkü.

## Kanıt
Dalın son commit'i `fab4ce73`.

**CI** (koşu 43289458930)
- `Uygulama · debug`: 2327 test başladı, 2327'si geçti (1.0.4'te 2314'tü; 13 yeni). Ekran görüntüleri: 281 aynı, 0 yeni, 0 değişti, 0 eksik.
- `Uygulama · release` ve iki laboratuvar işi yeşil.

**Emülatör, kişisel derleme** (hepsi `fab4ce73`: versionName 1.0.5, x86_64 versionCode 14010013)
- `page-opens`, Android 17 (koşu 43289445307): geçti; 32 adım, 37 denetimin hiçbiri kırmızı değil. Yedi açılışın hepsinde sayfa 0,3 / 0,8 / 2,5 saniyede ekranda (program sayfası %14,2, bölüm sayfası %15,9) ve arayüzün ağacında; 842 piksellik kaydırmayı 633–808 pikselle izliyor; her açılışta ve her Geri'den sonra alt çubuk yerinde. Ekran görüntülerine tek tek bakıldı.
- Sürüm yürüyüşleri (koşu 43289450097): Android 17 telefon 145 adım, 0/71; Android 17 tablet 134 adım, 0/66; Android 15 telefon 145 adım, 0/71; Android 15 tablet 134 adım, 0/66. Çökme, ANR, StrictMode yok.
- PR'ın kendi yürüyüşü (debug, Android 15; koşu 43289458842): tablet 0/67, telefon 0/71.

**Aynı geçiş 1.0.4'te** (`23a19b22`, kişisel derleme, Android 17)
- Koşu 43277971874 (geçişin ilk hâliyle): satırdan açılan sayfa iki açılışta da 0,3 saniyede %14,2, 0,8 saniyede %0,0 dolu ve ağaçta yok (kırmızı). Abone olunca açılan ve kutucuktan açılan sayfa ekranda, ama kaydırmada yalnız 75–82 piksel oynuyor, üstü ekranda kalıyor (o koşunun arayüz dökümlerinden ölçüldü; geçişin o günkü denetimi bunu geçirmişti).
- Koşu 43294020905 (geçişin son hâliyle): abone olunca uygulamanın açtığı sayfa bu kez boş kaldı (ekran görüntüsünde üst ve alt çubuk arasında hiçbir şey yok). Geçiş ne sayfayı ne listeyi bulabildiği için orada durdu (kırmızı, ama “yayın eklenemedi” diye). Geçişin boş sayfadan Geri ile devam edip satırları da denemesi gerekirdi; bu eksik uygulamayı değil yürüyüşü ilgilendiriyor ve bu PR'da düzeltilmedi.
- Düzeltmenin ara hâli (`32296bbb`, koşu 43283001856): sayfalar ekranda ve 565–825 piksel kayıyor; ama ilk kaydırmadan sonra alt çubuk hiçbir sayfada geri gelmiyor. Üçüncü düzeltmenin nedeni bu koşudur.

Kanıt dalları: `ci/emulator-api37.0-personal-page-opens` (şu an 1.0.4'ün ikinci koşusunu tutuyor; birleştirmeden sonra master üzerinde yeniden gezilecek), `ci/emulator-api37.0-personal-phone`, `ci/emulator-api37.0-personal-tablet`, `ci/emulator-personal-phone`, `ci/emulator-personal-tablet`.

**Emülatörde görülemeyen:** alt camın altındaki çizgi. Telefonda bakılacak.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01CCjCQf9dHq1sPdX5uoz8qa
