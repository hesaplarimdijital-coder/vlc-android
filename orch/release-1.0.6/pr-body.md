Nağmedar 1.0.6: kaydırılan uyarı bırakıldığı yerde kalmaz; arabanın kendi Android'inde (Android Automotive) arabanın medya uygulaması bağlanınca uygulama çökmez.

## Bildirim
Sahibi, 1.0.5'i Galaxy S24'te (Android 17, 1080 × 2340, 480 dpi, üç düğme, koyu tema) kullanırken, ekran görüntüsüyle (13 Kasım): “Şu alttaki uyarı türü kaydırılınca böyle kalıyor. Sanırım kit ile ilgili. Düzelt aynı zamanda kite ekle. Bir de bana android auto ekran görüntüleri gönder”. Görüntüde Ayarlar açık; “Kütüphane yeniden taranıyor; liste bu arada olduğu gibi kalır” uyarısı yerinden sağa kaymış, yarı saydam, sayfanın üstünde duruyor.

1.0.3'ten beri vardı (kaydırarak kapatma o sürümde geldi). Benim doğrulamamdan kaçtı: testler coroutine'leri hiçbir cihazın çalıştırmadığı bir sırayla çalıştırıyordu, yürüyüşün kaydırmaları da her dokunma olayından sonra uygulamayı bekliyordu.

# 1. Uyarı

## Neden
`swipeToDismiss` parmağın her hareketini kendi coroutine'inde uyguluyordu (`scope.launch { swipe.snapTo(swipe.value + delta) }`), bırakışta da aynı `Animatable`'ı canlandırıyordu. Parmak hareket hâlindeyken kalkınca Android son hareketi ve kalkışı pencereye birlikte verir; Compose'un `draggable`'ı `onDragStopped`'u sırada bekleyenlerden önce başlatır. Son hareketin `snapTo`'su bırakışın `animateTo`'sundan sonra çalışıp onu durduruyordu (`Animatable`'ın kilidi, “Mutation interrupted”). Uyarı bırakıldığı yerde, yarı soluk, süresi dolana kadar kalıyordu; parmak durup öyle kalkınca sorun yoktu.

Testte izlenerek görüldü (`onDragStopped … settle CANCELLED: Mutation interrupted`, ardından son `snapTo`), emülatörde ham dokunma olaylarıyla yeniden üretildi.

## Düzeltme
Uyarının yeri artık parmağın anında taşıdığı bir değer (`SwipeAway`: `dragBy` doğrudan yazar), alt sayfaların 1.0.2'den beri taşındığı gibi (`SheetDrag.pull`). Bırakınca `settle()` dışarı ya da yerine götürür; o yolu yalnız yeni bir dokunuş keser. Kurallar (`SwipeDismissRules`) aynı.

Uygulamanın öteki kaydırmalarına bakıldı, dokunulmadı: alt sayfalar zaten böyle; satır kaydırma (`Swipe.kt`), mini oynatıcının ve kapağın yana kaydırması (`ExpandablePlayer.kt`, `PhonePlayer.kt`) hareketi de bırakışı da aynı sıraya koyuyor, sıra bozulmuyor.

## Kit
Aynı hata Desktop Kit'te de vardı: bildiriminde (`ToastCard`) ve alt sayfasının tutamağında, başlığında (`SheetPull`). Kitin düzeltmesi 2.4.1 için taslak PR: hesaplarimdijital-coder/desktop-kit#10. Nağmedar kitin kodunu kendi içinde taşır; o PR'ı beklemez.

# 2. Araba (bildirilmedi; ekran görüntüleri alınırken görüldü)

Android Auto'nun kendisi emülatörde çalıştırılamıyor: masaüstü ana birimi, Play Store'a giriş yapılmış bir telefon ister. Aynı ağaç, onu Android Auto gibi bağlanıp çizen bir yerde açıldı: Android Automotive emülatörünün medya uygulaması. İlk koşuda uygulama iki kez öldü:

```
FATAL EXCEPTION: DefaultDispatcher-worker-2
java.lang.IllegalStateException: Cannot invoke setValue on a background thread
  at androidx.lifecycle.LiveData.setValue
  at org.videolan.vlc.media.PlayerController.getProgress
  at org.videolan.vlc.media.PlaylistManager.getMusicEngine
  at org.videolan.vlc.media.PlaylistManager.getEngines
  at org.videolan.vlc.PlaybackService.isPlaying
```

`broadcastMetadata()` “çalıyor mu”yu `Dispatchers.Default`'taki coroutine'in içinde okuyordu. Bu soru oynatma motorlarını (ve oynatıcının ilerlemesini, bir `LiveData`) ilk soran kimse onun iş parçacığında kurar. Automotive'de `CarConnection.type` hizmet onu gözlemeye başladığı anda, `onCreate()`'in içinde yanıt verir; ilk güncelleme hiçbir şey çalmadan gelir ve motorları o coroutine kurmaya kalkar. Değer artık coroutine başlamadan okunur (`showNotification()`'ın okuduğu gibi).

**Bilinmeyen:** telefondaki Android Auto'nun aynı yarışı kaybedip kaybetmediği. Orada bağlantı bilgisi biraz sonra, bir sorguyla gelir; telefon ve tablet emülatörlerindeki yürüyüşlerin hiçbiri bu çökmeyi göstermedi. Düzeltme iki durumu da kapatır.

# Testler
- `SwipeLetGoInMotionTest` (yeni, 5 test): son hareketi ve kalkışı birlikte verir, coroutine'leri sıraya koyar (`StandardTestDispatcher`). Fırlatılan “Geri al” çubuğu ve uyarı gider; az kaydırılan çubuk telefonda, geniş pencerede ve azaltılmış harekette yerine döner. Her kaydırma iki hareket sayısıyla yapılır ve bırakıldığı anda parmağın çubuğu taşımış olduğunu da söyler.
- Hizmetin düzeltmesinin birim testi yok (oynatma hizmetini kuran bir JVM testi bu depoda yok); kanıtı araba yürüyüşüdür.

## Düzeltmeden önce kırmızı
- Aynı testler 1.0.5'in `States.kt`'siyle (`exp/notice-swipe`, commit `21c61486`, koşu 43326140078): beşi de kırmızı. Fırlatılan çubuk 90 kare sonra yerinden 144 piksel ötede; az kaydırılan 120 kare sonra 20 piksel ötede.
- İlk test sürümü 1.0.5'te fırlatmaları geçiriyordu: bu testlerde parmağın altında kayan çubuk hareketlerin ancak bir kısmını alır, 1.0.5 yalnız son hareketi aldığında takılır. İzleyerek bulundu; testler art arda iki hareket sayısıyla yapılacak biçimde değişti.

# Yürüyüş (emülatör)
Yürüyüşler `f77a341a` üzerinde yapıldı. Son commit (`cc4a6cdc`) onun üstüne yalnız CHANGELOG'da bir cümle düzeltir; uygulama, testler, yürüyüş betiği ve iş akışları aynıdır (APK bu dosyayı okumaz).

**`notice-swipe` (yeni).** Uyarı, emülatörün dokunmatik ekranına çekirdeğin verdiği biçimde yazılan ham dokunma olaylarıyla kaydırılır (`adb` kök yetkisiyle); son hareket ve kalkış birlikte gelir. `input` ve UiAutomator her olaydan sonra uygulamayı bekler; hareket hâlinde kalkan bir parmağın yaptığını yapamazlar. Telefonun ekran boyutunda, koyu tema, üç düğme, animasyonlar açık. Uyarının yeri bırakıştan yaklaşık 0,3, 0,9 ve 1,6 saniye sonra piksellerden okunur; kalkışın son hareketle birlikte geldiği sistemin kendi kaydından denetlenir.

| | 1.0.5 (`fab4ce73`), kişisel, Android 17 | 1.0.6 (`f77a341a`), kişisel, Android 17 |
| --- | --- | --- |
| Koşu | 43326144857: 11 denetimden 2'si kırmızı | 43342980334: 11 denetimin 11'i yeşil |
| Uzağa kaydırıp durarak bırakmak (kalkış 289 / 316 ms sonra) | gider | gider |
| Fırlatıp hareket hâlinde bırakmak (kalkış 0,0 / 0,1 ms sonra) | **yerinde kalır**: sol kenarı üç okumada da 520 pikselde (yeri 48) | gider: üç okumada da çubuk yok |
| Az kaydırıp hareket hâlinde bırakmak (0,0 / 0,1 ms) | **yana kaymış kalır**: 114 pikselde | yerine döner: üç okumada da 48 pikselde |

**`auto-shots` (yeni).** İş akışı bu geçiş için bir Android Automotive emülatörü açar (API 34). Arabanın medya uygulaması uygulamanın oynatma hizmetine bağlanır; sekmeleri arabanın medya uygulaması göstermeli, dokunulan parça çalmalı (arabanın oynatıcısı adını göstermeli, saati ilerlemiş olmalı), hiçbir adımda çökme olmamalı.
- Hizmet düzeltmesinden önce (`3e1b2834`'ün kişisel derlemesi, koşu 43324177045): iki çökme, arabanın ekranı boş.
- 1.0.6 (`f77a341a`, kişisel, koşu 43342984526): 25 adım, çökme yok, dört denetim yeşil. Sekmeler, albümler, bir albüm, çalan parça, Podcast ve Keşfet, popüler programlar ve bir programın sayfası, radyonun ülkeleri, bir ülkenin istasyonları, çalan bir istasyon, son çalınanlar, arama.

**`page-opens`** (1.0.5'ten): `f77a341a`, kişisel, Android 17, koşu 43342975939: 32 adım, 37 denetimin 37'si yeşil.

**Asıl yürüyüş** (kişisel, API 35 ve 37.0, telefon ve tablet): `f77a341a`, koşu 43342972096, dördü de yeşil. API 35 telefon: 145 adım, 71 denetimin 71'i; API 35 tablet: 134 adım, 66'nın 66'sı; API 37.0 telefon: 141 adım, 71'in 71'i; API 37.0 tablet: 134 adım, 66'nın 66'sı. Çökme, ANR, StrictMode ihlali yok.

PR'ın kendi debug yürüyüşü (API 35) `f77a341a`'da tablette yeşil bitti; telefon işi, CHANGELOG düzeltmesinin itilmesiyle iptal oldu ve `cc4a6cdc` üzerinde yeniden başladı.

# Bağımsız inceleme
Tüm farkı okuyan ayrı bir inceleme uyarı düzeltmesinde ve hizmet düzeltmesinde hata bulmadı; kayıtta ve yürüyüş betiğinde düzeltilecekler gösterdi, hepsi bu PR'da:
- Bir commit'in içeriği iletisine uymuyordu (yürüyüş betiği, yorum düzeltmesinin commit'ine girmişti). İtilmeden önce yeniden bölündü.
- CHANGELOG'un Baseline Profile cümlesi yanlıştı (“değişen yollar profilin saydıklarının içindedir”): uyarının kaydırması profilde hiç yok; `broadcastMetadata` ve `isPlaying` adlarıyla eşleşiyor, coroutine'in kurucu kuralı artık eşleşmiyor. Düzeltildi.
- Yürüyüşün yeni denetimlerinden dördü yanlış nedenle geçebilirdi: araba sekmeleri uygulamanın kendi penceresinde de sayılıyordu; gelmeyen bir ekran görüntüsü “çubuk yok” okunuyordu; kalkışın son hareketle birlikte geldiği yalnız raporlanıyor, istenmiyordu; radyonun listeleri yüklenmeden dokunulabiliyordu. `be140c73`.
- Testlerdeki “her iki hareketten biri” gözleminin nedeni bulundu: Compose, bir öğeye, öğenin içindeki yeri değişmemiş bir hareketi vermez (`HitPathTracker`); parmağı tam izlemiş bir çubuk, testin eşit adımlarında her ikinci harekette bu durumdadır. Yoruma yazıldı (`442e87b5`).
- Kitin PR'ının incelemesinden: testler “yerine döndü” ile “hiç kıpırdamadı”yı ayıramıyordu (`9275a77c`).
- Bakılıp bırakılan: `ExpandablePlayer.kt`'deki mini oynatıcı kaydırması bırakış kararını kaymanın bir hareket gerideki değeriyle veriyor (takılma değil; eşiği son hareketle geçen bir kaydırma geri dönebilir). Bu PR'da yok.
- Yürüyüşte açık kalan: `notice-swipe`, “uyarılar burada uzun süre kalıyor mu”yu okurken ağacı okuyamazsa “kalıyor” sayar. Bu koşuda üçüncü kaydırmanın sonundaki okuma uyarıyı ağaçta gösterdi; betikte düzeltilmesi sonraya kaldı.

# Sürüm
`versionName` 1.0.6, `versionCode` 4010006; kişisel arm64 APK'sı 14010012 (1.0.5'in 14010011'inin üzerine kurulur). Baseline Profile 1.0.3'ünküdür.

# Telefonda bakılacaklar
- Ayarlar › “Kütüphaneyi yeniden tara” uyarısı: fırlatınca gider, az kaydırıp bırakınca yerine döner.
- Bir “Geri al” çubuğu aynı biçimde.
- Android Auto: emülatörde yalnız arabanın kendi Android'i denenebildi.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01CCjCQf9dHq1sPdX5uoz8qa
