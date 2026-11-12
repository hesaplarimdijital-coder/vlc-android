Nağmedar 1.0.4: Geri düğmesi ayrıntı sayfalarını ve oynatıcıyı küçültmez.

## Bildirim
Sahibi, 1.0.3'ü Galaxy S24'te (Android 17, üç düğmeli gezinme) denerken: “Podcast sayfasında podcast bölümüne tıklayıp geri gelince animasyon eskisi gibi onu da düzeltiver”.

## Neden
Android 16'dan beri üç düğmenin Geri'si uygulamaya bir “öngörülü geri” olarak gelir (platformun `BackAnimationController` ve `BackProgressAnimator`'ı, “Add predictive back animation for 3-button-nav”): kenarsız başlar (`EDGE_NONE`) ve düğme basılı olduğu sürece ilerlemesini sistem kendisi, bir yayla 0'dan 1'e yürütür. Bir dokunuşun 60–150 ms'sinde ilerleme üçte biri, çoğu kez beşte dördü geçer.

1.0.3 düğmeyi parmaktan yalnız sekmelerde ayırıyordu (Desktop Kit 2.4.0). Uygulamanın kendi üç öngörülü geri işleyicisi bu ilerlemeyi parmağınki gibi izliyordu:
- `DepthBackHandler` (telefonun ayrıntıları: podcast bölümü, program, albüm, sanatçı, tür, çalma listesi): sayfa %90'a doğru küçülüyor, köşeleri yuvarlanıyor, yana yaslanıyor, sonra gidiyordu. 1.0.2'nin Geri'si.
- `DetailBackHandler` (tablet ve DeX: ayrıntı, Sıradaki, pencere oynatıcısı): aynısı.
- `ExpandablePlayer` (telefonun büyük oynatıcısı): küçülüp sonra iniyordu.

Bu bir cihazda doğrulanmadı (emülatörün `input keyevent`'i ilerleme göndermez); dayanak platformun kaynağı ve kit'in sekmelerde aynı düğme için yaptığı düzeltme.

## Düzeltme
`BackSource.finger` / `BackEventCompat.fromFinger`: kenarı olmayan bir öngörülü geri parmağın değildir. Üç işleyici yalnız bir kaydırmanın olaylarını izler. Düğmenin altında sayfa yerinde ve tam boyunda kalır; bırakılınca sayfanın kendi okunun oynattığını oynar (ayrıntı opak, bitiş kenarından çıkar; oynatıcı karta iner). Kabuğun sekme kuralı aynı tanımı okur. Parmakla geri değişmedi.

## Testler
- `BackButton` (test): düğmeye sistemin bastığı gibi basar (kenarsız başlangıç, yayın ilerlemesi kare kare, bırakınca Geri). 1.0.3'ün testleri çıplak bir `onBackPressed()` ya da ilerlemesiz bir başlangıç basıyordu: bir klavyenin tuşu. Bu yüzden görülmedi.
- `EpisodeBackButtonTest`: sahibin yolu, telefonun kabuğunda. Program sayfası, üstünde bölüm sayfası: düğme basılıyken yerinde ve tam boyunda; bırakınca aynı boyda dışarı; önce bölüm, sonraki basışta program. Karşı test: aynı ilerlemeyle parmak sayfayı küçültür.
- `DetailNoDoubleExposureTest`: telefonda ve geniş pencerede kare kare (düğmenin altında hiçbir piksel değişmez; çıkışta ayrıntı pencere boyunda); karşı test.
- `PlayerMotionTest`: oynatıcı düğmenin altında kenardan kenara sayfa; bırakınca iner; parmak küçültür.
- `BackMotionTest`: sekme ve sayfa testleri gerçek düğmeyle.
- Şeritler (`motion/`): `episode-open`, `episode-back-button`, `detail-back-button`, `album-close`, `page-pop`, `page-pop-arrow`, `tab-back`, `player-back`.

## Sürüm
`versionName = '1.0.4'`, `versionCode = 4010004`; kişisel arm64 APK'sı **14010010**. `CHANGELOG.md`, `buildsystem/release/README.md`. Baseline Profile 1.0.3'ünkü.

## Kanıt
CI ve yürüyüşler bu dalın son commit'inde koşuyor; sonuçlar buraya yazılacak.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01CCjCQf9dHq1sPdX5uoz8qa
