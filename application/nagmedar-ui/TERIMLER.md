# Nağmedar terim sözlüğü

Yeni arayüzün (`nagmedar-ui`) ve Nağmedar'ın klasik arayüze eklediği metinler Türkçe ve
İngilizce olarak bu sözlüğe göre yazılır (S-1501). Her metin iki dilde birlikte eklenir:
`src/main/res/values/strings.xml` (İngilizce) ve `src/main/res/values-tr/strings.xml` (Türkçe).

Sözlüğü testler denetler:

- `GlossaryTest`, aşağıdaki tablonun **Kullanmayın** ve **Avoid** sütunlarındaki karşılıkları
  metinlerde arar; biri bulunursa kırılır. Aranan, sözcüğün başıdır: “kitaplık” “kitaplığı”nı,
  “kuyruk” “kuyruğa”yı da yakalar. Büyük-küçük harf ve Türkçe harfler gözetilmez.
- `StringsTest`, iki dilin aynı anahtarlara ve aynı biçim yer tutucularına (`%1$s`, `%d`)
  sahip olduğunu ve aşağıdaki yazım kurallarından “(test)” ile işaretlenenleri denetler.
- `HardcodedTextTest`, kodda kullanıcıya görünen sabit metin (ve Türkçe harfli dizgi)
  kalmadığını, çeviri metninin kodda büyütülüp küçültülmediğini denetler.

## Terimler

| Türkçe | English | Kullanmayın | Avoid | Not |
|---|---|---|---|---|
| kütüphane | library | kitaplık, arşiv | collection | Medya kütüphanesi de yazılım kütüphanesi de (Hakkında) aynı sözcük |
| film | film | | movie | İngilizcede “Films”, “a film's menu” |
| dizi | series | | TV show | Tekili ve çoğulu aynı: “1 series”, “4 series” |
| bölüm | episode | | | Adlardan tanınır: S01E02, 1x02, “1. Sezon 2. Bölüm” |
| bölüm (filmde) | chapter | | | Bir filmin bölümleri de “bölüm”; yer ayırır: zaman çizelgesinde “Bölüm 14 — Şafak” |
| sezon | season | | | |
| parça | track | şarkı | song | Müzikte tek öğe; arama kutusunda da “parça” |
| söz | lyrics | | | “Söz ve LRC”, “kapak, söz, altyazı” |
| zamanlı söz, düz söz | synced lyrics, plain lyrics | senkron söz | | Satırları LRC ya da SYLT ile zamanlanmış söz çalarken izlenir; düz söz zamansızdır. Sırayla dosyanın etiketlerinden, yanındaki .lrc dosyasından, LRCLIB'den (Ayarlar › Sözler) |
| sanatçı | artist | | | |
| albüm | album | | | |
| çalma listesi | playlist | oynatma listesi, oynatma listeleri | | Kısa yerlerde “liste”: “Yeni liste”, “Listeye ekle…”, “Listeden kaldır” |
| içe aktarmak, dışa aktarmak | import, export | | | Dosyadan okumak, dosyaya yazmak: çalma listeleri (M3U, M3U8, PLS, XSPF), kısayollar, cihaz profilleri |
| Sıradaki | Up next | kuyruk, oynatma sırası | play queue | Sıradaki parçaların paneli ve onu açan kısayol (Ctrl+L) |
| sıra | queue | | | “Sıraya ekle”, “Sıradan kaldır”; “Add to queue”, “Remove from the queue” |
| Hemen sonra oynat | Play next | sonra çal | | Seçileni çalanın hemen arkasına koyar; “Sıraya ekle” en sona |
| oynat | play | | | “Çal” yalnızca müziğe özgü yerlerde: “Şimdi çalıyor”, “Son çalınan müzik”, “Test sesini çal” |
| oynatıcı | player | | | |
| A-B tekrarı | A-B loop | | A-B repeat | A noktası, B noktası; düğmesi sırayla A’yı, B’yi koyar, sonra kapatır |
| tam ekran | fullscreen | | | “Tam ekrandan çık” / “Exit fullscreen” |
| altyazı | subtitle | alt yazı | caption | Tek sözcük |
| kodlama | encoding | karakter seti, kod sayfası | charset | “Altyazı kodlaması”: Otomatik, UTF-8, Türkçe · Windows-1254; film başına hatırlanır |
| ses izi | audio track | ses parçası | sound track | Videodaki ses; müzikteki “parça”dan ayrı |
| ses düzeyi | volume | ses seviyesi, ses yüksekliği | | Düğmede “Sesi kapat” / “Sesi aç”; kısayolda “Sessiz”. Algılanan ses (loudness) başka bir şeydir: gürlük |
| klasör | folder | dizin | | İnternet radyolarının listesi “radyo rehberi” (radio directory) |
| cihaz | device | aygıt | | |
| harici disk | external drive | harici sürücü | external disk | |
| ağ paylaşımı | network share | | | SMB, DLNA, WebDAV |
| akış | stream | | | “Ağ akışı aç…” |
| kısayol | shortcut | | hotkey, key binding | |
| tuş | key | | | |
| pencere | window | | | “Yeni pencerede aç” |
| kenar çubuğu | sidebar | yan çubuk, yan menü | side bar | |
| menü çubuğu | menu bar | | | |
| komut paleti | command palette | | | |
| ayarlar | settings | tercihler | preferences | Menü yolu: “Ayarlar › Ses” |
| seç, seçili | choose, select, selected | seçildi | pick | İngilizcede seçenek, dosya, klasör ve tuş için “Choose”; seçim kipine girmek ve “Select all” için “Select” |
| kaldır | remove | | | Listeden, sıradan, seçimden çıkarır; dosya silinmez. Dosya gerçekten silinecekse “sil” / “delete” |
| vazgeç | cancel | | | İletişim kutularında; süren bir işi (indirme) durdurmak için “İptal” |
| izlendi, izlenmedi | watched, unwatched | | | |
| İzlemeye devam et | Continue watching | | | Raf başlığı; düğmede “Devam et” (Resume) |
| kaldığı yer | stopped at | | | |
| lisans | licence | | license | İngilizcede isim İngiliz yazımıyla; özel adlar olduğu gibi (aşağıda) |
| renk | colour | | color | İngilizce metinler İngiliz yazımıyla yazılır |
| favori | favourite | | favorite | |
| tanımak | recognise | | recognize | |
| analiz etmek | analyse | | analyze | |
| ya da | or | veya | | |
| özgür yazılım | free software | açık kaynak | open source | |
| katkıcı | contributor | | | |
| kapsayıcı | container | | | MKV, MP4 |
| bit hızı | bitrate | bit oranı | bit rate | Saniyedeki bit, Mbps ile; TV Lab'de “1 sn tepe”, “5 sn ortalama” |
| eşik | threshold | | | Bir cihaz profilinin kaldırdığı en yüksek 1 sn bit hızı: “Profil eşiği” |
| cihaz profili | device profile | | | TV Lab'in hedefi: açtığı kapsayıcılar, codec'ler, HDR, eşik; JSON dosyası olarak paylaşılır |
| bulgu | finding | | | TV Lab'de kritik, uyarı ya da uygun |
| kare hızı | frame rate | | | |
| kayıp kare | dropped frames | | lost frames | Oynatıcının gösteremediği kareler: Bilgi sekmesinde ve Ayarlar › Performans'ta; hedef ≤ %0,1 |
| açılış | start-up | | startup | Uygulamanın açılıp ilk karesini çizmesi; soğuk açılış uygulama kapalıyken olur. Ayarlar'daki “Başlangıç” ise ne açılacağını seçer |
| yenileme hızı | refresh rate | tazeleme hızı | | Ekranın hızı, Hz ile: “120 Hz”; “Yenileme hızı eşleme” ekranı filmin kare hızına geçirir: her zaman, kesintisizse ya da hiç |
| vurgu rengi | accent colour | | | |
| ön ayar | preset | | | “VLC”, “mpv” |
| efekt | effect | | | “Efektler ve filtreler” (Ctrl+E); oynatıcı panelinin sekmesi “Efektler” |
| ekolayzer | equaliser | eşitleyici | equalizer | VLC'nin ön ayarları kendi adlarıyla: “Düz”, “Güçlü bas”, “Büyük salon” |
| müzik motoru | music engine | ses motoru | audio engine | Nağmedar Ses Motoru'nun ayarlardaki adı; yerel müzik dosyalarını çalar, libVLC'ye dönülebilir |
| boşluksuz | gapless | | | Bir albümün parçaları arada boşluk ya da tık olmadan birbirini izler; “kesintisiz” ekranın yenileme hızı geçişi için kalır |
| çapraz geçiş | crossfade | | cross-fade | Sonraki parça çalanın sonunun üstüne girer; süresi saniyeyle, eğrisi “Eşit güç” ya da “Doğrusal” |
| DSP zinciri | DSP chain | efekt zinciri | effects chain | Ön kazanç → grafik ekolayzer → parametrik ekolayzer → kulaklık profili → sınırlayıcı; tek tuşla atlanır |
| atla | bypass | baypas | | DSP zincirinin tek tuşla devre dışı kalması: “Zinciri atla”; atlanınca çıkış bit-perfect kalır |
| ön kazanç | preamp | preamp, ön yükseltici | pre-amp | Zincirin başındaki dB kazancı; “Otomatik ön kazanç” en yüksek yükseltmeyi geri alır |
| sınırlayıcı | limiter | limiter, kısıtlayıcı | | Zincirin sonunda tepeleri eşiğin (dBFS) altında tutar; önden bakış süresi gecikmedir |
| kulaklık profili | headphone profile | | | AutoEq'nun ParametricEQ.txt biçiminde içe alınan düzeltme: kendi bantları ve ön kazancı |
| kırpılma | clipping | klip | | Tam ölçeği aşan örnekler; “Kırpılma göstergesi” sayar ve tepeyi dBFS olarak yazar |
| bant | band | | | Ekolayzerin bir bandı: merkez frekansı, kazancı (dB) ve Q'su; “tepe”, “alçak raf”, “yüksek raf”, “alçak geçiren”, “yüksek geçiren” |
| gürlük | loudness | | | Kulağın algıladığı ses, EBU R128'in ölçtüğü, LUFS ile; ses düzeyi (volume) değildir: “Gürlük taraması” albümleri ölçüp ReplayGain etiketlerini yazar |
| gerçek tepe | true peak | | inter-sample peak | Dört kat örneklenmiş tepe, dBTP; ReplayGain'in kırpılma önlemesi buna bakar |
| parça kazancı, albüm kazancı | track gain, album gain | | | ReplayGain'in iki kipi; “Albüm” albümü olmayan parçada parça kazancına düşer |
| hız | speed | tempo | tempo | Perde korunarak çalma hızı: 0,5–3×; motorun tempo aşaması, sesli kitap ve podcast için |
| perde | pitch | pitch | | Yarım ton olarak (−24…+24); hız korunarak değiştirilir |
| denge | balance | balans | | Sol ve sağ arasında; bir yan kısılır, öteki olduğu gibi kalır |
| tek kanal | mono | mono | | Tüm kanalların ortalaması her kanala; “Tek kanal” ayarı |
| DSD | DSD | | | Olduğu gibi; “DSD64”, “DSD128” hız adları; “DoP” (DSD over PCM) da olduğu gibi |
| sinyal yolu | signal path | | | Dosyadan çıkışa her aşama, biçimiyle; bit-perfect değilse ilk nedeni: “Neden: DSP zinciri” |
| çözücü | decoder | kod çözücü | | Müzik motorunda FFmpeg; örnekleri 64 bit kayan noktaya açar, DoP sözcüklerine dokunmaz |
| A/B testi | A/B test | | | Zincirle (A) zincirsizin (B), zincirin düzeyinde karşılaştırılması; A-B tekrarı başka şeydir. Kısayolu Alt+B |
| kör test | blind test | | | A/B testinde her turda X rastgele A ya da B olur; cevaplar testin sonunda açılır |
| dalga biçimi | waveform | dalga formu | | Müzik görünümünün zaman çizelgesi: her çubuk parçanın bir kesiti, tepesi soluk, gürlüğü koyu; tıklanınca oraya atlar |
| bit derinliği, örnekleme hızı | bit depth, sample rate | | | Kütüphanenin filtreleri ve sütunları: “24 bit”, “96 kHz”; teknik gösterim “24/96” iki dilde de noktalı |
| en-boy oranı | aspect ratio | | | “2,39:1” / “2.39:1”; “Doldur” görüntüyü kırparak ekranı doldurur |
| ton eşleme | tone mapping | | | HDR görüntüyü SDR ekrana uyarlar |
| geçmeli tarama | deinterlacing | | | |
| bant giderme | debanding | | | |
| senkron | sync | eşzamanlama | synchronization | Oynatıcı panelinin sekmesi “Senkron”: ses ve altyazı gecikmesi, altyazı hızı |
| gecikme | delay | | | “Pozitif: ses daha geç gelir”; adımlar 10 ve 50 ms. Sözlerde 0,1 sn adımla (Alt+G, Alt+H), parça başına saklanır |
| kontur | outline | | | Altyazı harflerinin çevresindeki çizgi: yok, 2, 4 ya da 6 px |
| tırnaklı, tırnaksız | serif, sans serif | | | Altyazı yazı tipleri; “Eş aralıklı” (monospace), “Dar” (condensed) |
| parmak izi | fingerprint | | hash | Dosyanın OpenSubtitles'taki kimliği: boyutu ve ilk ve son 64 KB'si; “bu dosyaya uygun” altyazılar onunla bulunur |
| kota | quota | | | OpenSubtitles'ın günlük indirme hakkı: “OpenSubtitles kotası: 4/5” |
| ekran görüntüsü | screenshot | anlık görüntü | snapshot | Oynatıcıda Shift+S; dosya filmin adı ve zamanıyla: “Kuzey Işığı 01-12-33.png” |
| yer imi | bookmark | | | Filmin bir anı, adıyla; medya kütüphanesinde saklanır (Ctrl+D ekler) |
| zamana atla | jump to time | | | Ctrl+T; “1:12:33”, “72:33”, “45 sn”, “+30”, “%50” yazılır |
| resim içinde resim | picture in picture | | | Küçük pencerede: 10 sn geri, oynat ya da duraklat, 10 sn ileri |
| ipucu | tooltip | | | |
| dokunun, tıklayın, basın | tap, click, press | | | Dokunmatikte “dokunun”, farede “tıklayın”, klavyede “basın” |
| akıllı liste | smart list | otomatik liste, dinamik liste | auto playlist | Kurallarla kendiliğinden dolan çalma listesi; “Kuralları düzenle…”, “Parçalar uysun: tüm kurallara / herhangi bir kurala” |
| kural | rule | ölçüt | criterion | Akıllı listenin bir satırı: alan, işleç, değer (“Puan en az 4”, “Eklenme en çok 30 gün önce”) |
| puan | rating | derecelendirme, skor | score | 1–5 yıldız; “Puan ver…”, “Puanı kaldır”, “Puan verilmedi” |
| çalınma, atlama | play count, skip | | | Bir parça yarısı ya da dört dakikası dinlenince çalınmış, ondan önce başka parçaya geçilince atlanmış sayılır; “Son çalınma” |
| uyku zamanlayıcısı | sleep timer | uyku modu, kapanma zamanlayıcısı | sleep mode | Süre bitince ya da çalan parça bitince oynatmayı durdurur (Alt+S); “Parça sonu”, “+15 dk”, “Zamanlayıcıyı kapat” |
| alarm | alarm | çalar saat | alarm clock | Ayarlar › Alarm; müzikle ya da internet radyosuyla çalar, ses düzeyi sessizlikten yükselir. Android'in “alarm saati” (alarm clock) olarak kurulur |

## Özel adlar

Bu adlar olduğu gibi yazılır; yukarıdaki tablo onları denetlemez.

- AutoEq
- EBU R128
- GNU General Public License
- ReplayGain
- SIL Open Font License
- VLC for Android

## Yazım kuralları

1. Kullanıcıya “siz” diye seslenilir: “seçin”, “basın”, “değiştirebilirsiniz”. (test)
2. Büyük harf yalnızca cümle başında ve özel adlarda, iki dilde de: “Klasik arayüz”,
   “Open in new window”.
3. Kısayollar artıyla ve boşluksuz yazılır: Ctrl+K, Ctrl+Shift+N. (test)
4. Üç nokta tek karakterdir: “Dosya aç…”. (test)
5. Tırnak “…” biçimindedir; düz tırnak (") yalnızca arama sözdizimi örneğinde, çünkü aramada
   öyle yazılır. (test)
6. Sayı ile birim arasında boşluk vardır: “50 ms”, “44,1 kHz”, “10 sn”. (test)
7. Ondalık ayırıcı dile göredir: Türkçede virgül (23,976 Hz), İngilizcede nokta. Sayılar
   koddan dile göre biçimlenir (`NumberFormat`, `getString` yer tutucuları). Teknik gösterimler
   (5.1, 7.1, 24/96, 16/44.1, P8.1, sürüm numaraları) iki dilde de noktalıdır.
8. Yüzde Türkçede sayıdan önce (%50), İngilizcede sonra (50%) yazılır.
9. Eksi işareti “−” (U+2212): “−50 ms”; artı “+50 ms”; aralık “±50 ms”.
10. Ayraç, iki yanında boşlukla orta noktadır: “Klavye: Türkçe Q · tuşlar üzerlerinde yazdığı gibi”.
11. Menü yolu “›” ile yazılır: “Ayarlar › Ses”.
12. Süre kısaltmaları: Türkçede sn, dk, sa; İngilizcede s, min, h.
13. Çeviri metni kodda büyütülüp küçültülmez. Bir başlık büyük harfle gösterilecekse arayüzün
    diliyle büyütülür (`uppercase(currentLocale())`: “FİLMLER”, “FILMS”); kodlar ve kısaltmalar
    `Locale.ROOT` ile (“QAI”, “HEVC”). (test)
14. Arama Türkçe harfleri eşler: “isik” “Işık”ı, “kamu mali” “Kamu malı”nı bulur
    (`foldForSearch`).
15. Kodda kullanıcıya görünen sabit metin yazılmaz; her metin iki dilde kaynak dosyalarındadır.
    (test)

## İstisnalar

- Laboratuvar (Ayarlar › Laboratuvar) raporlarının gövdesi: MVP0 deneylerinin (SP-01…SP-05)
  tanı çıktısıdır, kopyalanıp deneyin karar notuna yapıştırılır; Türkçe kalır. Araçların adları,
  açıklamaları ve seçenekleri iki dildedir.
- Lisans metinleri ve kütüphanelerin telif satırları özgün dillerinde (İngilizce) kalır;
  lisansların kısa adları Türkçeleşir (“Kamu malı”, “3 maddeli BSD”).
- Klasik arayüz (VLC'nin kendi ekranları) VLC'nin çevirileriyle gelir; bu sözlük yeni arayüz ve
  Nağmedar'ın eklediği metinler içindir.
