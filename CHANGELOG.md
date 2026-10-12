# Değişiklik günlüğü

Nağmedar'ın sürümleri, en yenisi üstte. Her MVP bir sürümdür: MVP1 0.1.0, MVP2 0.2.0 ve sonrası aynı sırayla. İmzalı APK'lar şimdilik yalnızca depo sahibinin gördüğü **taslak** GitHub sürümlerinde yer alır; herkese açık yayın ve F-Droid derlemesi sonraya bırakıldı (S-1503, 12 Ekim 2026 kararı). Bir sürümün nasıl hazırlandığı: [`buildsystem/release/README.md`](buildsystem/release/README.md).

## 0.2.0 — MVP2 (hazırlanıyor)

### Müzik motoru
- Nağmedar Ses Motoru: yerel müzik dosyaları için FFmpeg çözücülü, 64-bit kayan noktalı kendi motor; çıkış kaynağın derinliğinde ve hızında, 16 ve 24 bit FLAC ffmpeg'le bit-exact. Ayarlar › Müzik'ten libVLC'ye dönülebilir.
- Boşluksuz oynatma: sıradaki parça önceden çözülür, örnek doğruluğunda devralınır; MP3 ve AAC'nin kodlayıcı gecikmesi ve dolgusu kırpılır.
- Çapraz geçiş: süre (2, 4, 8 sn), eşit güç ya da doğrusal eğri; albümün ardışık parçalarında kendiliğinden kapalı.
- DSP zinciri: ön kazanç (otomatik ön kazançla), 20 bantlı grafik ekolayzer, 10 bantlı parametrik ekolayzer, AutoEq biçiminde kulaklık profili, önden bakışlı sınırlayıcı ve kırpılma göstergesi; tek tuşla atlanır ve atlanınca çıkış bit-perfect kalır. Ekolayzer sayfası (Ses › Ekolayzer…, Ctrl+Shift+E, Ayarlar › Müzik): zincirin frekans yanıtı çizilir, VLC'nin ön ayarları 20 banda taşınır, kulaklık profilleri dosyadan ya da panodan içe alınıp saklanır.
- Bit-perfect çıkış (Android 14+): aygıtın bit-perfect mikser özniteliklerine uyan bir AudioTrack; örnekler karıştırılmadan, yeniden örneklenmeden, dokunulmadan gider; parçanın hızı değişince akış yeniden açılır; desteklenmezse olağan akışa ya da AAudio'ya düşer. Ayarlar › Müzik › Çıkış: otomatik, AAudio, bit-perfect, USB DAC; çalarken o anki çıkış, hız, derinlik ve bit-perfect değilse nedeni yazılır.
- USB doğrudan çıkış: USB DAC libusb ile doğrudan sürülür (UAC1 ve UAC2, asenkron geri bildirim, 44,1–384 kHz, 16/24/32 bit); DAC'ın kendi ses denetimi uygulamanın sesidir; kablo çekilince başka çıkışa düşer.
- Sinyal yolu paneli (Ekolayzer sayfasında, zincirin yanında): kaynaktan çözücüye, ReplayGain, çapraz geçiş, hız ve perde, denge, DSP aşamaları ve ses düzeyinden çıkışa her aşama biçimiyle; örnekleri değiştiren aşamalar işaretli, bit-perfect rozeti ve değilse ilk nedeni. Null testi: null_test.py, yakalanan dijital çıkışı motorun çözümüyle örnek örnek karşılaştırır.
- A/B dinleme testi: A zinciri ayarlandığı gibi, B zinciri atlayarak zincirin ölçülen düzeyinde (≤ 0,1 dB) çalar. Zincir iki tarafta da çalışır, B zincirin gecikmesiyle gecikir, geçiş 10 ms'de yumuşar: tarafları sesten başka bir şey ele vermez. Kör test 10, 16 ya da 20 tur sürer, her turda X rastgele A ya da B'dir; cevaplar sonda, tahminle bu kadar tutturma olasılığıyla açılır. Alt+B tarafı her yerden değiştirir.
- Formatlar: FLAC, ALAC, WavPack, APE, TTA, Musepack, MP3, AAC, Opus, Vorbis, WMA, AIFF, WAV, W64, CAF, Matroska ses; motorun kendi FFmpeg derlemesiyle, her biri ffmpeg'in çözdüğüyle bire bir. (Tracker formatları, libopenmpt, sonraya kaldı.)
- DSD (DSF, DFF): PCM'e çözülerek (DSD64 → 352,8 kHz) ya da bit-perfect çıkışta DoP olarak (bitler 0x05/0xFA işaretleri altında 24 bitlik sözcüklerde, olduğu gibi); sinyal yolu DSD hızını söyler.
- Hız, perde, denge ve tek kanal: perdeyi koruyan hız (0,25–4×, WSOLA), hızı koruyan perde (±24 yarım ton), denge ve tek kanal; her biri kapalıyken çıkış bit-perfect kalır.
- ReplayGain: parça ya da albüm kazancı, ön kazanç, etiketsiz parçalar için kazanç, tepeye göre kırpılma önleme; Opus'un R128 etiketleri de okunur. EBU R128 tarayıcı (ITU-R BS.1770-4, gerçek tepe) kütüphanenin albümlerini arka planda ölçer ve ReplayGain etiketlerini TagLib ile dosyalara yazar (FLAC, MP3, M4A, Opus, WavPack, APE, DSF…).

### Müzik kütüphanesi
- Kütüphane: parçalar, sanatçılar (albüm sanatçısına göre), albümler, türler, besteciler, yıllar ve klasörler; Türkçe sıralama. Bit derinliği (16, 24, 32 bit, DSD, kayıplı) ve örnekleme hızı (44,1 kHz'den 192 kHz ve üstüne) filtreleri; derinlik, hız ve besteci motorun kendi incelemesinden okunur ve dosya değişene dek Nağmedar'ın veritabanında kalır.
- Masaüstü müzik görünümü: büyük pencerede üç bölme (gruplar, parça tablosu, şimdi çalıyor). Tablonun sütunları seçilir (başlığın sağ tık menüsü ya da "Sütunlar": besteci, biçim, derinlik, örnekleme, bit hızı, çalınma, eklenme…), her sütuna göre sıralanır, klavyeyle gezilir. Şimdi çalıyor bölmesinde kapak, biçim ve çıkış, dalga biçimi zaman çizelgesi (tıklanınca atlar, oklarla 5 sn) ve sıradakiler.
- Telefonun müzik oynatıcısı: mini oynatıcıdan tam ekran; büyük kapak, dalga biçimi, başparmağın ulaştığı yerde denetimler. Kapağı sola kaydırmak sonraki, sağa kaydırmak önceki parçayı çalar; aşağı kaydırmak ya da Geri kapatır.
- Sıradaki: kütüphanede seçilen parçalar “Hemen sonra oynat” ile çalanın ardına, “Sıraya ekle” ile sonuna girer, çalan liste değişmez; panelde tutamaçla sürüklenir, Alt+↑/↓ ile taşınır, Delete ile çıkarılır.
- Çalma listeleri: her liste bir sekme; satırlar tutamaçla sürüklenir, Alt+↑/↓ ile taşınır, Delete ile kaldırılır (Geri al geri getirir). M3U, M3U8, PLS ve XSPF içe ve dışa aktarılır: listenin klasör ağacındaki dosyalar göreli yazılır, başka bir bilgisayarda yapılmış listelerin dosyaları kütüphanede adlarından bulunur, eski Türkçe M3U'lar Windows-1254 olarak okunur. Müzik'te seçilen parçalar “Listeye ekle…” ile bir listeye girer.
- Akıllı listeler (Çalma listeleri › Akıllı liste): kurallarla kendiliğinden dolan listeler; başlık, sanatçı, albüm, tür, besteci, klasör, format, bit derinliği, örnekleme hızı, yıl, süre, puan, çalınma, atlama, son çalınma ve eklenme kuralları; tüm kurallar ya da herhangi biri, bir sıra ve en fazla kaç parça. Kütüphane, puanlar ve çalınmalar değiştikçe kendiliğinden güncellenir; hazır listelerden başlanabilir (En sevilenler, En çok çalınanlar, Hiç çalınmayanlar, Son eklenenler, Yüksek çözünürlük, Unutulan sevilenler) ve o anki hâliyle M3U8, XSPF, PLS ya da M3U olarak dışa aktarılır.
- Puan ve çalma istatistikleri: parçalara 1–5 yıldız verilir (müzik tablosunun “Puan” sütunu, şimdi çalıyor bölmesi, telefonun oynatıcısı ya da “Puan ver…” penceresi; seçilen parçalara birlikte de). Bir parça yarısı ya da dört dakikası dinlenince “çalındı”, ondan önce başka parçaya geçilince “atlandı” sayılır; sayım oynatma servisinde yapılır, çalmayı ne başlatmış olursa olsun. Puan, çalınma, atlama ve son çalınma Nağmedar'ın veritabanında kalır, kütüphane yeniden taransa da kaybolmaz; tablonun sütunları bunlara göre sıralanır.
- Uyku zamanlayıcısı: 15 dk'dan 2 saate ya da çalan parçanın sonuna; süre bitince istenirse parça sonuna kadar çalar, ses son 30 saniyede yavaşça kısılır ve sonraki oynatmada eski düzeyine döner. Telefonun oynatıcısında ay düğmesi, masaüstünde Oynatma menüsü, komut paleti ve Alt+S; çalışırken kalan süre çubukta görünür, “+15 dk” uzatır.
- Alarm (Ayarlar › Alarm): saat, günler (seçilmezse bir kez), son çalınan müzik, bir çalma listesi ya da internet radyosu akışı; ses sıfırdan seçilen sürede (30 sn–5 dk) yükselir, cihazın medya ses düzeyi gerekirse ayarlanan düzeye çıkarılır. Android'in alarm saati olarak kurulur (durum çubuğunda ve kilit ekranında görünür), yeniden başlatmadan ve saat değişikliğinden sonra yeniden kurulur; akış yanıt vermezse son çalınan müzik çalar.
- Sözler: dosyanın kendi etiketlerinden (USLT, SYLT, LYRICS, ©lyr; içlerindeki LRC de), yanındaki .lrc dosyasından ya da LRCLIB'den (lrclib.net); zamanlı sözler düzlerden önce gelir. LRCLIB'in bulduğu sözler Nağmedar'ın veritabanında saklanır, çevrimdışıyken de gelir; LRCLIB'e Nağmedar adıyla, saniyede en fazla bir, dakikada en fazla yirmi istek gider, “bekleyin” yanıtından sonra bir süre hiç gitmez; bulunamayan bir hafta sonra yeniden sorulur. Ayarlar › Sözler'den kapatılır, saklananlar temizlenir.
- Zamanlı söz görünümü: müzik görünümünün şimdi çalıyor bölmesinde ve telefonun oynatıcısında Sözler düğmesiyle; söylenen satır vurgulanır ve bölmenin üçte birinde tutulur, satıra tıklanınca parça oraya atlar, kelime zamanlı LRC'de kelimeler sırayla yanar; düz sözler metin olarak okunur. Gecikme 0,1 sn adımlarla ayarlanır (Alt+G, Alt+H) ve parça başına saklanır. Tam ekran sözler: Ctrl+Shift+L ya da bölmedeki düğme; Esc kapatır.
- Kapaklar: dosyanın etiketlerinden (APIC, PICTURE, covr; birkaç resim varsa ön kapak), klasöründeki cover, folder, front… resminden ya da Cover Art Archive'dan (etiketlerdeki MusicBrainz kimliğiyle, yoksa albümün adı ve sanatçısıyla bulunan sürüm grubundan, 1200 piksel); bulunan kapak Nağmedar'ın klasöründe saklanır, albümün parçaları onu paylaşır. Albüm başlığındaki kapağa ya da şimdi çalıyor bölmesindeki kapağa tıklanınca ya da uzun basılınca “Kapağı değiştir…” elle bir resim seçtirir, “Kapağı yeniden ara” tazeler. Ayarlar › Kapaklar'dan Cover Art Archive kapatılır, saklananlar temizlenir (elle seçilenler kalır).
- Bildirim, kilit ekranı, Android Auto ve widget'lar yeni motorla: Nağmedar'ın albüm kapakları bildirimde, kilit ekranında, Android Auto'da (çalan parça, sıra ve albüm listeleri) ve ana ekran widget'larında da görünür; çalan albümün kapağı bulununca, değiştirilince ya da kaldırılınca hepsi hemen yenilenir. Müzik motoruyla çalarken bildirimden, Android Auto'dan ya da uyku zamanlayıcısından durdurmak motoru da durdurur; Android Auto'nun ileri ve geri sarma düğmeleri yersiz bir “atlanamıyor” uyarısı vermez; etiketleri kütüphanede olmayan dosyaların başlığı ve sanatçısı dosyanın kendi etiketlerinden okunur. Ana ekrana yeni konan widget hemen güncellenir; telefonun (4×1, 2×2, 2×1) ve tabletin (5×2, 3×3) boyutlarında doğru düzen seçilir.

## 0.1.0 — MVP1

Nağmedar'ın ilk sürümü: VLC for Android'in libVLC 4 (4.0.0-eap29) üzerine kurulu çatalı, DeX ve klavye-fare için yeni bir arayüzle. Uygulama kimliği `app.nagmedar`; klasik VLC arayüzü Ayarlar › Başlangıç'tan seçilebilir.

### Yeni arayüz
- Uyarlanabilir gezinme: telefonda alt çubuk, tablette ray, DeX penceresinde daraltılabilir kenar çubuğu.
- Menü çubuğu (Medya, Oynatma, Ses, Video, Altyazı, Araçlar, Görünüm, Yardım), komut paleti (Ctrl+K), başlık çubuğunda arama, çoklu pencere, kalıcı mini oynatıcı.
- Açık, koyu ve OLED tema; duvar kağıdından ya da dört renkten vurgu rengi.
- İlk açılış adımları: medya izni ya da klasör, kaynaklar, ses çıkışı testi, bildirimler; her biri atlanabilir.
- Hakkında ve lisanslar ekranı.

### Masaüstü ve DeX
- VLC'nin masaüstü kısayolları, kısayol düzenleyici (VLC ve mpv ön ayarları), Türkçe Q klavyede fiziksel tuş konumu ve klavye haritası.
- Sağ tık menüleri, imleç ve ipuçları, Ctrl ve Shift ile çoklu seçim ve lastik bant, sürükle-bırak (dışarıdan dosya, altyazıyı videoya, pencereler arası).
- Yalnız klavyeyle tam gezinme (Tab metin kutularından da çıkar), fare tekerleği ve geri-ileri tuşları, pencerenin yeri ve boyutunun hatırlanması.

### Kütüphane ve arama
- Filmler, diziler (sezon ve bölüm), klasörler, İzlemeye devam et; ızgara ve yoğun tablo; 4K, HDR, Dolby Vision ve Atmos süzgeçleri; teknik rozetler.
- Gruplu arama sonuçları, altyazıların içinde arama.

### Oynatıcı
- Masaüstü kontrol çubuğu: bölüm işaretli zaman çizelgesi, kare önizleme, A-B tekrarı, hız, ses ve iz menüleri.
- Yerleştirilebilir panel: efektler ve filtreler, senkron (ses ve altyazı gecikmesi, altyazı hızı), medya bilgisi ve canlı istatistikler (Ctrl+I).
- Ekran görüntüsü (PNG ve WebP, HDR'yi koruyarak ya da ton eşleyerek), zamana atla, yer imleri, bölüm listesi, resim içinde resim, yenileme hızı eşleme (HDMI'de 23,976 Hz).

### Altyazı
- Dosya seçiciyle, sürükle-bırakla ya da klasörden otomatik eşlemeyle altyazı; Türkçe kodlamanın (Windows-1254) kendiliğinden tanınması.
- Çift altyazı, senkron asistanı ve dosya başına gecikme, stil ön ayarları, OpenSubtitles.com araması ve kota göstergesi.

### TV Lab
- Bir filmi oynatılacağı cihaza göre analiz eder: kapsayıcı, codec, HDR ve Dolby Vision (profil, BL/EL/RPU, FEL/MEL), ses ve altyazı izleri.
- Paket düzeyinde bit hızı grafiği (1 ve 5 sn tepe, USB 2.0 eşiği; PNG ve CSV olarak dışa aktarılır), harici diskte sıralı okuma testi, bulgular ve rapor.
- Cihaz profilleri: LG OLED B4 ve Tab S11 Ultra; düzenlenebilir, JSON dosyası olarak paylaşılır ve içe aktarılır.

### Laboratuvar ve ölçüm
- Ayarlar › Laboratuvar: ses çıkışı ve bit-perfect, USB DAC, HDMI ekran ve 24p denemeleri; Dolby Vision profil 7'den 8.1'e dönüştürme; whisper ile konuşmadan altyazı.
- Ayarlar › Performans: soğuk açılış süresi, oynatmaların kayıp kareleri ve pil tüketimi; bilgisayardan çalışan macrobenchmark'lar ve eşikleri (`buildsystem/benchmark`).

### Dil ve erişilebilirlik
- Türkçe ve İngilizce, tek bir terim sözlüğüyle; dokunma alanı, metin boyutu ve kontrast denetimleri.

### Bilinen sınırlar
- Bu cihaz, Ağ ve Akışlar ekranları MVP4'te gelir; bu sürümde ekranlarındaki düğmeyle klasik arayüzde açılır. Çalma listeleri MVP2'de, Dönüştür MVP3'te, podcast ve radyo MVP4'te.
- APK, yayımlanmış libVLC 4 AAR'ıyla derlenir. Nağmedar'ın kendi libVLC eklemeleri (S-503 … S-508: oynarken altyazı boyutu, tam kare ileri, renk ve geçmeli tarama ayarları, ses cihazı ve kanal düzeni, kırpma) yalnızca CI'ın native libVLC derlemesinde vardır; bu APK'da olmayanlar bir şey yapmaz ve bunu söyler.
- Laboratuvarın yerel araçları (USB, Dolby Vision, whisper) ve TV Lab'in analizi yalnızca arm64 APK'sında çalışır.
