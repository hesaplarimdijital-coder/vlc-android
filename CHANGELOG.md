# Değişiklik günlüğü

Nağmedar'ın sürümleri, en yenisi üstte. Her MVP bir sürümdür: MVP1 0.1.0, MVP2 0.2.0 ve sonrası aynı sırayla. İmzalı APK'lar şimdilik yalnızca depo sahibinin gördüğü **taslak** GitHub sürümlerinde yer alır; herkese açık yayın ve F-Droid derlemesi sonraya bırakıldı (S-1503, 12 Ekim 2026 kararı). Bir sürümün nasıl hazırlandığı: [`buildsystem/release/README.md`](buildsystem/release/README.md).

## 0.2.0 — MVP2 (hazırlanıyor)

### Müzik motoru
- Nağmedar Ses Motoru: yerel müzik dosyaları için FFmpeg çözücülü, 64-bit kayan noktalı kendi motor; çıkış kaynağın derinliğinde ve hızında, 16 ve 24 bit FLAC ffmpeg'le bit-exact. Ayarlar › Müzik'ten libVLC'ye dönülebilir.
- Boşluksuz oynatma: sıradaki parça önceden çözülür, örnek doğruluğunda devralınır; MP3 ve AAC'nin kodlayıcı gecikmesi ve dolgusu kırpılır.
- Çapraz geçiş: süre (2, 4, 8 sn), eşit güç ya da doğrusal eğri; albümün ardışık parçalarında kendiliğinden kapalı.
- DSP zinciri: ön kazanç (otomatik ön kazançla), 20 bantlı grafik ekolayzer, 10 bantlı parametrik ekolayzer, AutoEq biçiminde kulaklık profili, önden bakışlı sınırlayıcı ve kırpılma göstergesi; tek tuşla atlanır ve atlanınca çıkış bit-perfect kalır. (Ekolayzer ekranı sonraki adımda.)
- Hız, perde, denge ve tek kanal: perdeyi koruyan hız (0,25–4×, WSOLA), hızı koruyan perde (±24 yarım ton), denge ve tek kanal; her biri kapalıyken çıkış bit-perfect kalır.
- ReplayGain: parça ya da albüm kazancı, ön kazanç, etiketsiz parçalar için kazanç, tepeye göre kırpılma önleme; Opus'un R128 etiketleri de okunur. EBU R128 tarayıcı (ITU-R BS.1770-4, gerçek tepe) kütüphanenin albümlerini arka planda ölçer ve ReplayGain etiketlerini TagLib ile dosyalara yazar (FLAC, MP3, M4A, Opus, WavPack, APE, DSF…). (Tarama ve ReplayGain ayar satırları sonraki adımda.)

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
