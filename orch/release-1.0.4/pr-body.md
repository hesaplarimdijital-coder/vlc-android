Nağmedar 1.0.4: Geri düğmesi ayrıntı sayfalarını ve oynatıcıyı küçültmez; çalan podcast bölümü çubukta “Canlı yayın” değildir.

## Bildirimler
Sahibi, 1.0.3'ü Galaxy S24'te (Android 17, üç düğmeli gezinme) denerken:
1. “Podcast sayfasında podcast bölümüne tıklayıp geri gelince animasyon eskisi gibi onu da düzeltiver”.
2. “Bir de podcast çaldığında canlı yayın diyor müzik barı. Canlı yayın olmamalı”.

# 1. Geri düğmesi

## Neden
Android 16'dan beri üç düğmenin Geri'si uygulamaya bir “öngörülü geri” olarak gelir (platformun `BackAnimationController` ve `BackProgressAnimator`'ı, “Add predictive back animation for 3-button-nav”): kenarsız başlar (`EDGE_NONE`) ve düğme basılı olduğu sürece ilerlemesini sistem kendisi, bir yayla 0'dan 1'e yürütür. Bir dokunuşun 60–150 ms'sinde ilerleme üçte biri, çoğu kez beşte dördü geçer.

1.0.3 düğmeyi parmaktan yalnız sekmelerde ayırıyordu (Desktop Kit 2.4.0). Uygulamanın kendi üç öngörülü geri işleyicisi bu ilerlemeyi parmağınki gibi izliyordu:
- `DepthBackHandler` (telefonun ayrıntıları: podcast bölümü, program, albüm, sanatçı, tür, çalma listesi): sayfa %90'a doğru küçülüyor, köşeleri yuvarlanıyor, yana yaslanıyor, sonra gidiyordu. 1.0.2'nin Geri'si.
- `DetailBackHandler` (tablet ve DeX: ayrıntı, Sıradaki, pencere oynatıcısı): aynısı.
- `ExpandablePlayer` (telefonun büyük oynatıcısı): küçülüp sonra iniyordu.

Android 17 emülatöründe görüldü (aşağıda, “Kanıt”): sistemin kendi Geri düğmesi bir saniye basılı tutulunca 1.0.3'ün üç sayfası da küçülür. O sistemde düğmenin öngörülü gerisi uzun basışla başlar; sahibin telefonu (One UI 9) bunu ilk andan başlatıyor olabilir, bu buradan görülemez.

## Düzeltme
`BackSource.finger(swipeEdge, edgeSwipes)`: bir öngörülü geri ancak bir kenar adı taşıyorsa **ve** sistem pencerenin yan kenarlarını geri kaydırması için ayırıyorsa (`rememberBackSwipes`: sistemin hareket boşlukları; kabuğun sekmeler için zaten okuduğu) parmağındır. Düğme (Android 16'dan beri kenarsız), bir tuş ve üç düğmeli gezinmede hangi kenarı yazarsa yazsın her şey parmağın değildir: `EDGE_NONE` platformun sözüdür, bir üreticinin kendi çubuğunun ne yazdığı buradan bilinemez. Üç işleyici yalnız bir kaydırmanın olaylarını izler. Düğmenin altında sayfa yerinde ve tam boyunda kalır; bırakılınca sayfanın kendi okunun oynattığını oynar (ayrıntı opak, bitiş kenarından çıkar; oynatıcı karta iner). Kabuğun sekme kuralı aynı tanımı okur. Parmakla geri değişmedi.

## Testler
- `BackButton` (test): düğmeye sistemin bastığı gibi basar (kenarsız başlangıç, yayın ilerlemesi kare kare, bırakınca Geri). 1.0.3'ün testleri çıplak bir `onBackPressed()` ya da ilerlemesiz bir başlangıç basıyordu: bir klavyenin tuşu. Bu yüzden görülmedi.
- `EpisodeBackButtonTest`: sahibin yolu, telefonun kabuğunda. Program sayfası, üstünde bölüm sayfası: düğme basılıyken yerinde ve tam boyunda; bırakınca aynı boyda dışarı; önce bölüm, sonraki basışta program. Karşı test: aynı ilerlemeyle parmak sayfayı küçültür.
- `DetailNoDoubleExposureTest`: telefonda ve geniş pencerede kare kare (düğmenin altında hiçbir piksel değişmez; çıkışta ayrıntı pencere boyunda); karşı test.
- `PlayerMotionTest`: oynatıcı düğmenin altında kenardan kenara sayfa; bırakınca iner; parmak küçültür.
- `BackMotionTest`: sekme ve sayfa testleri gerçek düğmeyle.
- Şeritler (`motion/`): `episode-open`, `episode-back-button`, `detail-back-button`, `album-close`, `page-pop`, `page-pop-arrow`, `tab-back`, `player-back`.

# 2. “Canlı yayın”

## Neden
`EpisodeMedia` bölümü indirilmişse dosyasından, değilse yayın adresinden çaldırır. Yayın adresinden çalan bölüm oynatıcı için, libVLC uzunluğunu bulana dek, uzunluğu olmayan bir akıştır. 1.0.2'nin çubuk kuralı (`media.length <= 0 && isStream(...)`) her böyle akışı yayındaki istasyon sayıyor, arama çubuğunun yerine “● Canlı yayın” çiziyordu. Uzunluk sonradan bulunsa da çubuğa kimse haber vermediği için öyle kalıyordu; süre de akışlar için oynatıcıdan hiç sorulmuyordu.

## Düzeltme
- `EpisodeAddresses`: oynatıcıya bölüm olarak verilen adresler, yayının bildirdiği süreyle. `EpisodeMedia.of` bölüm çalmadan önce buraya yazar (uygulamanın sayfaları ve Android Auto bölümleri oradan başlatır): çubuk ilk kareden bölüm olduğunu bilir, sorgu yok. Önceki açılıştan kalan bir akış için Room'a bir kez sorulur.
- `PlayingLength`: kural tek yerde. Yayında: uzunluğu olmayan ve bölüm olmayan akış. Bölümün uzunluğu, kütüphanede uzunluğu olmayan dosyada olduğu gibi oynatıcıdan sorulur (istasyonda sorulmaz); oynatıcı yanıtlayana dek yayının bildirdiği süre gösterilir.
- `rememberPlayingLength` çubukların, telefonun büyük oynatıcısının ve tabletin panelinin ortak okumasıdır: üçünde de arama çubuğu, süreler ve kısım işaretleri. Oynatıcının yanıtı bir medyanındır, sonrakine taşmaz.
- İstasyonlar değişmedi.

## Testler
- `PlayingLengthTest`: kuralın üç işlevi; uygulamanın başlattığı bölüm hiçbir bileşimde canlı değil ve süresi yayınınki; önceki açılıştan kalan bölüm Room yanıtlayınca bölüm; istasyon her bileşimde canlı; dosya için Room'a sorulmaz; adreslerin son 64'ü tutulur.

# Yürüyüş (emülatör)
Yürüyüşün Geri'si bir tuştu (`input keyevent 4`); tuş düğmenin ilerlemesini hiç taşımaz. Bu yüzden ne testler ne yürüyüş 1. bildirimi gördü.
- `back_button_check`: sistemin kendi Geri düğmesi bütün pencerelerin hiyerarşisinde bulunur, basılı tutulur (ekran iki kez alınır: bir dokunuş boyu sonra ve bir saniye sonra), sonra dokunulur. Basılıyken sayfa bütün kalmalı (en çok %2'si değişir), dokununca kapanmalı.
- `phone-en`: büyük oynatıcıdan, bölüm sayfasından ve program sayfasından böyle çıkar; sonunda bir bölümü yayın adresinden çalar ve çubuğu okur: süreleriyle arama çubuğu, “live” değil (`episode_plays`).
- İstek üzerine `passes=back-button`: üçü ve çalan bölüm tek başına; `ref` girdisiyle eski bir uygulamayı bu yürüyüşle gezer.

## Sürüm
`versionName = '1.0.4'`, `versionCode = 4010004`; kişisel arm64 APK'sı **14010010**. `CHANGELOG.md`, `buildsystem/release/README.md`. Baseline Profile 1.0.3'ünkü.

## Kanıt
Dalın son commit'i `23a19b22`. Uygulamanın kodu ve testleri `1528be02`'den beri aynıdır; sonraki commit'ler yürüyüşün sürücüsü ve sürüm notlarıdır.

**CI** (koşu 43228481940)
- `Uygulama · debug`: 2314 test başladı, 2314'ü geçti (1.0.3'te 2290'dı). Ekran görüntüleri: 281 aynı, 0 yeni, 0 değişti, 0 eksik.
- `Uygulama · release` ve iki laboratuvar işi yeşil.

**Yürüyüşler**
| Derleme | Sistem | Cihaz | Koşu | Adım | Denetim | Çökme/ANR | StrictMode |
|---|---|---|---|---|---|---|---|
| kişisel (R8, imzalı) | Android 17 (API 37.0) | telefon | 43228474544 | 145 | 71/71 | 0 | 0 |
| kişisel (R8, imzalı) | Android 17 (API 37.0) | tablet | 43220871981 (`01607e78`) | 134 | 66/66 | 0 | 0 |
| debug | API 35 | telefon | 43228481866 | 141 | 71/71 | 0 | 0 |
| debug | API 35 | tablet | 43228481866 | 134 | 67/67 | 0 | 0 |

- Kişisel tablet yürüyüşü `01607e78`'de koştu ve yinelenmedi: iki commit yalnız yürüyüş sürücüsünün telefon geçişindeki son adımında ayrılır.
- Android 17 telefon yürüyüşünde sistemin bir ANR'si kayıtlı: açılışta emülatörün Google Play hizmetleri (`GoogleLocationManagerService`), uygulamanın değil.
- `01607e78`'deki iki telefon yürüyüşü yalnız en son adımda (138) kaldı: yeni `episode_plays` adımı geçişin bittiği Ayarlar sayfasında Podcast sekmesini arıyordu. Sürücünün hatasıydı, `23a19b22` düzeltti; ondan önceki 68 denetim geçmişti.

**Geri düğmesi, aynı Android 17 emülatöründe 1.0.3 ile 1.0.4**
Sistemin kendi Geri düğmesi (başlatıcının görev çubuğundaki) bir saniye basılı tutulurken sayfanın, duran hâlinden farklı olan payı (bütün: en çok %2):

| Sayfa | 1.0.3 (koşu 43217588211) | 1.0.4, kişisel (koşu 43228474544) |
|---|---|---|
| bölüm sayfası | %21,7 | %0,1 |
| program sayfası | %19,8 | %0,0 |
| büyük oynatıcı | %57,1 | %0,1 |

- 1.0.3'te üçü de perdenin üstünde küçülmüş, yuvarlak köşeli bir karttı; 1.0.4'te yerinde ve tam boyunda. İkisinde de düğme bırakılınca sayfa kapandı.
- Basışın ilk anında (bir dokunuş boyu) iki sürümde de %0,0–0,1: bu emülatörde düğmenin öngörülü gerisi uzun basışla başlıyor. Sahibin telefonunun bunu ilk andan başlatıp başlatmadığı buradan görülemez; kural kenar değerine bağlı olmadığından ikisini de kapsar.
- API 35'te de üç sayfa düğmenin altında yerinde kaldı (%0,0–0,1) ve düğmeyle kapandı. Orada bölüm sayfasında ilk basış emülatörün açık kalmış klavyesini kapattı, sayfayı ikincisi (ekran görüntülerinde klavye görülür); uygulamanın geri işleyişiyle ilgisi yok.

**“Canlı yayın”**
- Yürüyüş bir bölümü yayın adresinden çaldı; çubuk süreleriyle arama çubuğunu gösterdi (“0:31 … −0:29”), iki telefon yürüyüşünde de (API 35 debug, Android 17 kişisel).
- Bu, hatanın giderildiğini **göstermez**: aynı yürüyüş 1.0.3'te de aynı satırı okudu. Emülatörün yerel sunucusu uzunluğu oynatıcıya hemen bildiriyor; sahibin yayınlarındaki gecikme burada yeniden üretilemedi. Düzeltmenin dayanağı kod okuması ve `PlayingLengthTest`'tir (1.0.3'ün kuralının böyle bir bölümü canlı saydığını da aynı test gösterir). Asıl deneme sahibin telefonundadır.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01CCjCQf9dHq1sPdX5uoz8qa
