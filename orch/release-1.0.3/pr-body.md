Nağmedar 1.0.3'ün sürümü: numara, sürüm notları, kullanıcı rehberi, Baseline Profile; sürümü hazırlarken bulunanların düzeltmeleri; Geri'nin yeni hareketi (Desktop Kit 2.4.0); Android 17'yi hedefleme.

## Sürüm
- `versionName = '1.0.3'`, `versionCode = 4010003`; kişisel arm64 APK'sı **14010009** (1.0.2'nin 14010008'inin üstüne güncelleme olarak kurulur).
- `CHANGELOG.md`: `## 1.0.3 (12 Kasım 2026)`.
- `buildsystem/release/README.md`: sürüm numaraları, güncellemede ne olduğu, ikiye bölünen yürüyüş ve kanıt dalları, Android 17 bölümü.
- Kullanıcı rehberi (tr, en): “Android Auto” bölümü; kütüphanenin bir kez taranması; DeX'te dışarı sürükleme; Gizlilik; Android 17'nin yerel ağ izni ve alarm sayfası.

## Geri (12 Kasım: “Geri tuşundaki efekti hala beğenmiyorum”)
Kare şeritlerinde üç kusur görüldü; üçü de Desktop Kit'te 5 Kasım'da düzeltilmişti (2.4.0 ve `fix/tab-back-button`):
1. **Sekmeler.** Başka bir sekmeden Geri (ve Müzik sekmesine dokunmak) bir sayfanın Geri'sini oynatıyordu: sekme yana kayıp çıkarken Müzik öbür yandan giriyor, aynı renkte iki liste aralarında kenar olmadan yan yana duruyordu. Sekmeler arası her dönüş artık sekme değişimi gibi solarak geçer (`NavRules.tabPop`). Android 16'dan beri üç düğmenin Geri'si de kenarsız bir “öngörülü geri” başlatır; gezinme bunu parmak sanıyordu, düğme artık düğmenin geçişini alır.
2. **Sayfalar.** Sayfadan açılan sayfa alttakini çeyrek ekran yana itiyordu. Sayfa artık opak olarak alttakinin üstüne kayar; alttaki yerinde %95'e çekilir ve perdenin %70'i altında kararır (`ShellPage`); Geri aynı yolun tersi, hareketle de aranır.
3. **Ayrıntı.** Telefonun ayrıntısı (kit 2.3.0'ın derinliği) solarak gelirken liste altında soluyordu: iki ekran birden yarı saydam. Kit 2.4.0'ın yolu: ayrıntı ilk karesinden opak, pencerenin tamamı kadar kayarak gelir; liste çekilir ve kararır; kutucukla sıralı solma, kutucuk hep opak; tabletin paylaşılan ekseni sıralı solar.

4. **Azaltılmış hareket.** Sayfanın geçişine eklenen iki animasyon (alttaki sayfanın derinliği, hareketle bırakılan sekmenin köşeleri) hiçbir şey oynatmadığında da 300 ms sürüyordu; gezinme ayrılan sayfayı geçiş bitene dek ekranda tuttuğundan “Animasyonları kaldır” açıkken Geri alttaki sayfayı 300 ms geç gösteriyordu. İkisi de yalnız gerçekten oynadıklarında süre alır.

Testler: `BackMotionTest` ve `DetailNoDoubleExposureTest` her kareye bakar (sayfalar saf kırmızı ve mavi): hiçbir sayfa ötekinin içinden görünmez, ikisi de opak, alttaki yerinde ve koyu, sekmeler hiçbir karede birlikte değil; düğme, Android 16'nın düğmesi, hareket, vazgeçme, ray, azaltılmış hareket.

## Sürümü hazırlarken bulunanlar
1. **Telefonda “Son çalınan müzik” rafı açılışta görünmüyordu.** Geçmiş listeden sonra okunuyor, raf sütun başlıklarının üstünde görünmeden kalıyordu. Geçmişin parça kimlikleri de saklanır (`RecentKept`), raf ilk karelerde saklanan listeden kurulur; listeden sonra gelen raf, liste hâlâ en baştaysa gösterilir (`DenseTable(leadingPresent)`).
2. **Baseline Profile üreticisi telefonda oynatıcıyı açmadan geçiyordu** (altı turda sıfır). Kaydırmaları listenin içinde kalır, ekran her bakışta yeniden okunur; yeni üretimde altı turun altısı.
3. **Bu cihaz ve Ağ: çift geri oku.** Telefonda açık klasörün iz çubuğunda üst çubuğun okuyla aynı işi yapan ikinci bir ok vardı; iz çubuğu son klasörü görünür tutmuyordu.

## Android 17 ve One UI 9
- `targetSdk 37` (sahibinin kararı, 12 Kasım: “37”): yerel ağ izni ve alarm sayfası devrede (#109'da hazırlanmıştı).
- One UI 9 için gözden geçirme: üç düğmenin Geri'si, DeX'in geniş ve alçak pencereleri, temalı simge, güvenli alan; cihazda denenecekler test listesinde.

## Kanıt
Son commit `56366976` (yeniden üretilen profille):
- **CI:** Uygulama · debug yeşil (2290 test; ekran görüntüleri 281 aynı, 0 yeni, 0 değişti, 0 eksik), Uygulama · release yeşil, laboratuvar işleri yeşil.
- **Yürüyüşler, debug, API 35 (bu PR'ın koşusu):** tablet 134 adım, 67 denetimin hepsi tamam; telefon 131 adım, 61 denetimin hepsi tamam. Çökme, ANR, StrictMode ihlali yok.
- **Yürüyüşler, debug, API 37.0 (koşu 43115460870):** tablet 134 adım, 0/67; telefon 131 adım, 0/61.
- **Yürüyüşler, kişisel derleme, API 35 ve 37.0 (koşu 43115464293):** dördü de geçti (tablet 134 adım, 0/66; telefon 132 adım, 0/61); kurulan paket `targetSdk=37`. API 37.0'daki tek kayıt emülatörün kendi Google Play hizmetlerinin açılıştaki ANR'ı, uygulamanın değil.
- **Baseline Profile (koşu 43111686458):** 44195 kural (11343'ü Nağmedar'ın ve VLC'nin kendi kodu), açılış profili 22872; üreticinin üç testi geçti, telefonun oynatıcısı altı turun altısında açıldı (1.0.2'nin profilinde sıfır).
- **Kare şeritleri:** `ci/shots-pr-113` dalının `motion/` klasörü (tab-back, page-push, page-pop, album-open, album-close, detail-back).

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01CCjCQf9dHq1sPdX5uoz8qa
