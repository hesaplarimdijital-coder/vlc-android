Nağmedar 1.0.9: Desktop Kit 2.9.0'ın getirdikleri.

## İstek
Sahibi (14 Kasım 01:11): “Şimdi 2.9.0 yükselt”. Kitin 2.9.0'ı main'de `2a6c41a`'da etiketli: `HideBottomNavigation` (desktop-kit #14) ve pencerenin alt kenarı (desktop-kit #16). Nağmedar 1.0.8'in bulduğu kit hataları (desktop-kit #17) onda yok.

## Kit → Nağmedar
| Kit 2.9.0 | Nağmedar |
| --- | --- |
| Cam: bulanıklığın altında malzemenin opak rengi | 1.0.5'ten beri var (`theme/Glass.kt`, 12 Kasım “arkası gözüküyor”) |
| Kök: alt kenardaki en uzun sistem payı kabuğa, eksi klavye | `shell/SystemBand.kt`, `rememberWindowRootInsets` (`fc46c5ae`) |
| `HideBottomNavigation()`: tam ekran bir işin altında alt gezinme durmaz | Nağmedar'ın kendi `WindowPage`'iyle, Sıradaki gibi: telefonda tam ekran sözler, görselleştirici ve söz düzenleyicisi pencerenin tamamını alır, çubuklar altlarında kenara çekilir (`PhoneWindowPage`, `1ae4b90d`). Kısayolla mini oynatıcının üstünden açıldıklarında çubukların arasında kalıyorlardı. Formlar küçük pencerelerdir; Ayarlar ve sayfaları 1.0.1'den beri çubukları kenara çeker (`NavRules.fullPages`) |
| `WindowEdgeTest` | `shell/WindowEdgeTest.kt`: uygulamanın kökü, kabuk, mini oynatıcı ve alt cam; S24 ölçüsünde üç düğme, hareketle gezinme, kaydırınca gizlenen çubuklar, 1440 piksel, bir Ayarlar sayfası, tutmayan paylar |

## Bağımsız inceleme
Bütün fark, işi görmemiş ayrı bir inceleyiciye okutuldu. Buldukları ve sonuçları:
- Test API 36'da koşamaz: Robolectric 4.17 Android 16 için Java 21 istiyor, uygulamanın testleri Java 17'de (CI 43520923610 da gösterdi). Test API 34'e döndü, pencereyi öteki kabuk testleri gibi çizer (`26405106`).
- Kısayolla (telefon genişliğindeki pencerede klavyeyle) mini oynatıcının üstünden açılan tam ekran sözler ve söz düzenleyicisi çubukların arasında kalıyordu; düzenleyicinin düğmeleri dokunulabilir bir alt çubuğun üstündeydi. Düzeltildi (`1ae4b90d`, `PhoneWindowPageTest`).
- Notların bir cümlesi denenenden fazlasını söylüyordu (“bakamıyor”); denenen yazıldı: API 34 ve 36, 35 denenmedi (`2985c636`).
- Şerit denetimi satırın başına bakmıyordu; bakar (`40c83b64`).
- Pencereyi kaplayan sayfalar ve uyarılar alt boşluklarını yalnız gezinme çubuğuna göre bırakır: 1.0.8'deki gibi, bilinen sınır olarak yazıldı.

## Testler ve koşular
- CI 43519261081 (`62ac5161`, test düzeltmeden önce, tek başına): tutmayan paylarda alt cam 778 dp'de, pencere 780 dp'de bitiyordu (düzeltilen hata). Öteki dört durum donanım çizicisinde durdu: Robolectric'in API 34 yerel çalışma ortamı Haze'in gürültü ve renk gölgelendiricisini derleyemiyor.
- CI 43522843314 (`7073b7e1`): debug kolu yeşil, 2432 testin hepsi geçti (WindowEdgeTest'in beşi dahil), 281 ekran görüntüsü referanslarla aynı.
- CI 43524716529 (`2985c636`): iki kol yeşil, 2435 testin hepsi geçti (`PhoneWindowPageTest`'in üçü dahil), 281 ekran görüntüsü aynı.
- Kişisel derlemenin yürüyüşü 43524771273 (`2985c636`): Android 15 ve 17, telefon 71/71, tablet 66/66 denetim; hepsi yeşil.
- `5ba07aa6` yalnız notları değiştirir.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01CCjCQf9dHq1sPdX5uoz8qa
