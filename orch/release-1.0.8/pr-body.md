Nağmedar 1.0.8: Desktop Kit 2.8.0'ın getirdikleri, Nağmedar'da karşılığı olan her yerde.

## İstek
Sahibi (13 Kasım 19:19): “Desktop kit 2.8.0 e göre güncelle”. İlk soruya “Karşılığı olanlar” dedi, 19:44'te “Vazgeçtim hepsini taşı”. Ana eylem düğmesinin nerede kullanılacağı sorusuna: “Yeni liste” (yalnız Çalma Listeleri'nin “Yeni liste”si yüzen düğmeye geçer). Adres çipli alanın (TokenField) taşınmayacağı kendisine söylendi: Nağmedar'da adres yazılan alan yok.

## Kapsam
Kitin 2.5.0'dan 2.8.0'a commit'leri tek tek eşlendi (`orch/port-1.0.8.md` bu oturumun notunda). Var olan genişletildi, kopyası yazılmadı: `DenseTable`, `ListDetailPanes`, `HorizontalSplitPane`, `SwipeToRemove`, `SheetAccess`, `TooltipArea`, `PageTopBar`, `NagButton`, `SettingRow`, `AlertBanner`, `WorkstationLayout`, `PaneWidths` aynı adlarla duruyor; yeni olanlar kitin Nağmedar'da hiç karşılığı olmayan parçaları: `PullToRefresh`, `GridZoom`/`GridStops`, `TabHop`, `Scrollbar`, `ShellPrimaryAction`, `NavIconBadge`, `HeightClass`, `OnReselect`, `MovableDetails`.

| Kit | Nağmedar |
| --- | --- |
| Liste ve ayrıntı (`ListDetailLayout`) | `ListDetailPanes` iki bölmeyi ancak ikisinin en küçük genişliklerine yer varsa yan yana koyar; Müzik'in grupları ve açık grubun parçaları düzenler arasında durumlarıyla taşınır (`MovableDetails`), yeni grup önce eskisi gidip sonra gelir; bölücüler sürüklenir ve saklanır |
| Tab ve odak, kaydırma çubuğu, çerçeveyle seçim, fareyle beklemeden sürükleme | tablo, kapak ızgaraları, denetçinin sekmeleri, açık alt sayfa; çubuk tablolarda, ızgaralarda, menülerde, sayfalarda, palette, “Daha fazla”da |
| İpuçları | klavye odağında da, komşusu hemen |
| Kontrollerin kenarı, basılı/devre dışı/tehlikeli düğme | `controlBorder`, renkler, `NagButton(danger)` beş düğmede |
| Alt sayfa jestsiz kapanır | tutamak “Sayfayı kapat”, sayfalar üst üste binmez |
| Görünür komutlar | dokunarak “⋯”: abonelikler, sunucular, Sıradaki |
| Çekip yenileme | abonelikler (dönemsel turun kendisi), program sayfası, ağ klasörü |
| Kaydırma eylemleri | `SwipeToRemove` (Sıradaki): eşik rengi, simge, titreşim; kaldırılan satır bir saniye dışarıda |
| Ana eylem | “Yeni liste” (sahibinin seçimi) |
| Simge rozetleri | Podcastler'de yeni bölüm sayısı |
| Sekmeye yeniden dokunmak | Müzik, Podcastler, Çalma Listeleri başına gider |
| Yükseklik sınıfı | yan çevrilmiş telefon telefonun ölçülerini alır |
| Raydaki menüler | “≡” |
| Kapak büyüklüğü (zoom) | Podcastler ve Çalma Listeleri'nin ızgaraları, ızgara başına saklanır |
| Denetçi alt sayfa, gizli başlayabilir | Müzik'in denetçisi |
| Hareket, üst çubuk, kabuğun dolgusu, metinler | Nağmedar'ın kendi hareket, başlık ve kabuk kodunda |
| Baseline Profile | `nagmedar-ui/src/main/baseline-prof.txt` |

## Taşınmayanlar
İmleci bilen metin alanı (öyle bir alan yok), bildirimin kendi süresi ve kapatma düğmesi (uyarılar zaten durur, kapatma düğmesi yok), değişen gezinme (ekranlar sabit), ızgaranın boş hâli ve ayrıntının yer tutucusu (ekranların kendi boş durumu var), kaydırmanın ikinci eylemi (doğal bir ikinci eylem yok), TokenField. Nedenleri CHANGELOG'da.

## Bağımsız inceleme
Sürümün bütün farkı işi görmemiş ayrı bir inceleyiciye okutuldu. Düzeltilenler:
- Telefonda denetçinin alt sayfasındaki bir “⋯”un açtığı menü, “tek sayfa” kuralı denetçinin sayfasını kapattığından onunla birlikte gidiyordu (`0b8e77e2`).
- “Yeni liste” büyümüş oynatıcının altında görünmeden dokunulabiliyor, TalkBack'te okunuyordu (`39bcc664`).
- Yenileme turu ekranın kendi denetleyicisinde tutulduğundan yeniden açılan ekran onu göstermiyor, ikinci bir çekiş ikinci bir tur başlatıyordu (`a754249e`).
- Sıradaki'de iki kez sıradaki bir parçanın birinci kopyası kaydırılınca ikincisi bir saniye boş görünüyordu (`58e37890`).
- Gizli kaydırma çubuğu kaydırılan her karede listeyi ölçüyordu; iki test uygulamanın düzenini (denetçiyle) ve menünün gerçekten açıldığını denemiyordu (`43e4d57c`).

Bırakılanlar: ağ klasöründe disk dönmez (okunurken klasörün yerini bağlanma göstergesi alır); bölünmüş bölme pencere genişliği değişince bölücünün aralığını bir kare sonra günceller; telefonda açılan bir grubun parçaları sırayla gelmez (kitin kuralı).

## Yürüyüşün bulduğu hata
`96d1d59d`'nin ana yürüyüşünde (43492250730) iki tablette “Boyuta göre”den sonra sürücünün klasörlerine bakan denetim kırmızıydı. Hata 1.0.3'ten beri uygulamanın tablosundaydı: satırlar adlarıyla tutulur ve liste, ilk gördüğü satırı (adına göre ilk klasör, Alarms) yeni sıra onu nereye koyarsa orada gösteriyordu; boyuta göre sırada o sonuncu olduğundan liste sonunu gösteriyordu. Aynı denetim 1.0.3'te ve 1.0.7'de birer kez kırmızıydı ve denetimin kendisine yorulmuştu (1.0.7'nin bilinen sınırı). Sıra değişince satırlar artık baştan başlar (`627633e6`, test `DenseTableRequestsTest.anotherOrderStartsTheRowsAtTheTop`). `627633e6`'nın mesajı sürücünün “en küçük klasörlerini” gösterdiğini söylüyor; yanlış: klasörün boyutu sayılmaz, `cb0f20a2` yorumu ve notları düzeltti.

## Testler ve koşular
- Yeni ve değişen testlerin listesi CHANGELOG'da.
- İlk koşular dört şey buldu: eksik bir içe aktarma, simge listesinde eksik `Minus`, `ReselectTest`'in telefonun çubuğundaki kısa adı (“Podcast”) bilmemesi ve gerçek bir çökme: Müzik'in grup listesi telefonun düzeninden tabletinkine geçerken `HorizontalSplitPane`'in alt kompozisyonuna taşınırken “Cannot insert LayoutNode … because it already has a parent”. Bölme kitinki gibi düz bir `Layout` oldu (`c7bbc37b`).
- CI 43488970710 (`c7bbc37b`): iki kol yeşil, 2422 test; 103 ekran görüntüsü farklıydı, uygulamanın kendi çizdiği alanların kenarını eski renkte gösterdi (`47bd5ca3`).
- Bu PR'ın koşusu 43494155786 (`47bd5ca3`): 281 ekran görüntüsünün 129'u farklı, her biri farkıyla incelendi (`ci/shots-pr-118`); referanslar bu koşunun görüntüleri (`7a7603b5`).
- `b1c0a9da`: bu PR'ın koşusu 43500824445 iki kol yeşil, 2427 testin hepsi geçti, 281 ekran görüntüsü referanslarla aynı. Kişisel derlemenin yürüyüşleri: ana yürüyüş 43500813342 (Android 15 ve 17, telefon 71/71, tablet 66/66 denetim), alt sayfa denemeleri 43500817348, sayfa açılışları 43503835478, geri düğmesi 43503840365; hepsi yeşil. Arama (43492268874) ve bildirim kaydırması (43492272979) `96d1d59d`'de yeşildi; sonraki commit'ler aramada yalnız kutunun kenar rengini değiştirdi, bildirimlere dokunmadı.
- `cb0f20a2` yalnız notları ve bir yorumu değiştirir; koşusu 43508507598 iki kol yeşil, 2427 testin hepsi geçti, 281 ekran görüntüsü referanslarla aynı. `master` bu commit'e ileri sarıldı.

## Kit
Kitte de olan dört hata (bir sayfanın içinden açılan sayfa, tablonun sırası, büyümüş oynatıcının altındaki düğme, gizli kaydırma çubuğu) hesaplarimdijital-coder/desktop-kit#17'de (taslak; altı adım, her biri CI'da yeşil). Birleştirmek ve 2.9.0'ı etiketlemek sahibinin.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01CCjCQf9dHq1sPdX5uoz8qa
