# Nağmedar kullanıcı rehberi

<!--
  Rehberin Türkçe metni (S-1504). Biçim: "## Başlık {kısa-ad}" bir bölüm başlatır; "### " küçük başlık,
  "- " madde, "1. " adım, "> " ipucu, **kalın**. {key:KısayolAdı} o kısayolun tuşlarını, tablodaki
  son haliyle yazar (ShortcutId'nin adı); kullanıcı bir tuşu değiştirirse rehber de değişir.
  Yazım kuralları ve terimler: application/nagmedar-ui/TERIMLER.md. İngilizcesi guide-en.md'dedir;
  iki dosyanın bölümleri aynı sırada ve aynı kısa adlarla gider.
-->

## İlk açılış ve kütüphane {first-run}

Nağmedar kütüphanesini bu cihazdaki ve bağlı disklerdeki videolardan ve müzikten kurar. Hiçbir dosya yüklenmez, dosyalarınıza siz istemedikçe dokunulmaz.

### İlk açılışta
1. **Medya erişimi:** “Medya dosyaları” iznini verin ya da “Klasör seç” ile yalnızca göstermek istediğiniz klasörleri seçin.
2. **Kaynaklar:** bu cihazdaki medya, harici disk, seçtiğiniz klasörler, ağ paylaşımları, podcast ve radyo. İstediklerinizi işaretleyin.
3. **Ses çıkışı:** “Test sesini çal” deyin, sonra “Duydum” ya da “Duymadım” seçin.
4. **Bildirimler:** kilit ekranındaki oynatma denetimleri ve tarama, indirme ilerlemesi için izin verin.

Her adım “Atla” ile geçilir. Adımları yeniden görmek için Ayarlar › Başlangıç › “İlk açılış adımlarını yeniden göster”.

### Kütüphanede
- **Ana sayfa** “İzlemeye devam et” rafını gösterir. **Filmler** ve **Diziler** ızgara ya da yoğun tablo olarak açılır; diziler adlardan tanınır (S01E02, 1x02, “1. Sezon 2. Bölüm”). 4K, HDR, Dolby Vision ve Atmos süzgeçleri vardır.
- **Müzik** parçaları, sanatçıları, albümleri, türleri, besteci ve yıllara göre ve klasörler olarak gösterir.
- Bir sayfa boşsa “Klasör ekle” ile klasör ekleyin.
- Ara kutusu ya da {key:CommandPalette} kütüphaneyi, ekranları ve komutları tek yerden arar; altyazıların içinde de arar.

> Telefonda alt çubuktaki “Daha fazla” sayfası alt çubuğa sığmayan ekranları listeler.

## Ağ kaynakları {network}

SMB, FTP, SFTP, NFS, UPnP/DLNA ve WebDAV sunucularındaki medyaya yerel dosya gibi bakın. Nerede: Kaynaklar › Ağ (telefonda Daha fazla › Ağ).

1. “Bu ağda” altında kendini duyuran sunucular (SMB, UPnP/DLNA, mDNS) kendiliğinden görünür.
2. Görünmeyen bir sunucu için “Sunucu ekle…”: protokol, sunucu, bağlantı noktası, klasör, görünen ad, kullanıcı adı ve parola. Bir adres yapıştırırsanız form kendini doldurur.
3. Klasörleri tablo gibi gezin: Enter ya da çift tıklama açar, {key:FolderUp} bir üst klasöre çıkar, {key:Refresh} yeniler; sağ tık menüsü de vardır.
4. Dosyayı oynatın ya da sıraya ekleyin. Klasörü alt klasörleriyle oynatabilir, VLC'nin taradığı protokollerde “Kütüphaneye ekle” ile kütüphaneye aldırabilirsiniz.

> Parolayı “Bu cihazda hatırla (şifreli)” ile saklarsanız Android Keystore'un anahtarıyla şifrelenir. Saklamazsanız, uygulama yeniden başlayınca kütüphane taramaları oturum açamaz.

> Kendi imzaladığı sertifikayla çalışan bir HTTPS ya da WebDAV sunucusu için libVLC sertifikayı kabul edip etmeyeceğinizi sorar (video oynatıcıda da): “Sertifikayı göster”, ardından “24 saat kabul et” ya da “Kalıcı olarak kabul et”; emin değilseniz “Vazgeç”.

## Video oynatıcı {video}

Filmler ve diziler libVLC 4 ile oynar. Dokunmatik ekranda VLC'nin hareketleri, fareyle (DeX) masaüstü denetim çubuğu ve yan panel kullanılır; parmakla dokunursanız video klasik denetimlere döner.

### Dokunmatik hareketler
- Ekranın solunda yukarı ya da aşağı kaydırmak parlaklığı, sağında ses düzeyini değiştirir.
- Yatay kaydırmak ileri ya da geri sarar.
- Sol ya da sağ kenara çift dokunmak 10 sn geri ya da ileri atlar, ortaya çift dokunmak oynatır ya da duraklatır.
- Dokunup tutmak hızlı oynatır; iki parmakla yakınlaştırmak görüntüyü büyütür.
- Hangi hareketlerin açık olduğunu Ayarlar › “Tüm ayarlar (klasik arayüz)” altından seçersiniz.

### Fare ve klavye
- Denetim çubuğunda bölüm işaretli zaman çizelgesi, kare önizleme, A-B tekrarı, hız, ses ve iz menüleri bulunur. Fare tekerleği ses düzeyini değiştirir, Ctrl ile basılıyken sarar; bunu Ayarlar › Giriş'ten ters çevirebilirsiniz.
- {key:PlayPause} oynatır ya da duraklatır, {key:Fullscreen} tam ekrana geçirir, {key:JumpBack} ve {key:JumpForward} 10 sn atlar, {key:JumpToTime} istediğiniz ana götürür.
- Yan panel: {key:Effects} Efektler, {key:MediaInfo} Bilgi, {key:Bookmarks} Yer imleri sekmesini açar; Altyazı ve Senkron sekmeleri de oradadır.
- {key:Snapshot} ekran görüntüsü alır (Ayarlar › Ekran görüntüleri: biçim, klasör, HDR). {key:AddBookmark} o ana yer imi koyar. Resim içinde resim ve yenileme hızı eşleme de oynatıcıdadır.
- {key:Record} oynayan akışı olduğu gibi kaydeder; bkz. Radyo.

> Altyazı gecikmesi, altyazı kodlaması ve görüntü efektleri filme göre hatırlanır; aynı filmi yeniden açınca ayarlamanız gerekmez.

## Efektler ve filtreler {effects}

Parlaklık, kontrast, HDR, döndürme, logo ve daha fazlası: oynatıcı panelinin Efektler sekmesi ({key:Effects}) görüntüyü oynatırken ayarlar.

1. **Görüntü:** parlaklık, kontrast, doygunluk, ton ve gama; “Sıfırla” hepsini geri alır.
2. **En-boy ve kırpma:** en-boy oranı, “Siyah bantları algıla”, 90° döndürme ve çevirme.
3. **HDR ve ölçekleme:** HDR'yi ekrana geçirin ya da SDR'ye ton eşleyin; ölçekleyiciyi seçin.
4. **Gelişmiş video:** logo, videonun üstünde yazı (saat ya da tarihle), açıyla döndürme ve yan yana 3B filmler için anaglif 3B.

> Görüntü ayarları filme göre hatırlanır; Gelişmiş video ayarları uygulama kapanana dek her filme uygulanır. Açıyla döndürme ve 3B işlemciyi yorar; panel bunu söyler.

## Altyazılar {subtitles}

Altyazıyı dosyadan, klasörden ya da internetten ekleyin; araçlarla temizleyin ve düzeltin. Nerede: oynatıcı panelinin Altyazı sekmesi ve oynatıcının altyazı menüsü.

### Altyazı eklemek
1. Dosyadan: {key:AddSubtitleFile} ya da altyazı dosyasını videonun üstüne bırakın. Filmin klasöründeki altyazılar bulunursa “Hangisi gösterilsin?” diye sorar.
2. İnternetten: “Altyazı ara”. Film adını denetleyin; “Önce dosyanın parmak iziyle” seçiliyse “bu dosyaya uygun” sonuçlar öne gelir. “İndir” altyazıyı filmin yanına kaydeder (yazılamazsa uygulamanın klasörüne).
3. Sağlayıcılar: Ayarlar › Altyazı'da OpenSubtitles.com ve seçtiğiniz bir altyazı klasörü açılıp kapatılır ve sıralanır; arama hepsinde birlikte yapılır, sonuçlar tek listede birleşir.
4. Harfler bozuksa “Altyazı kodlaması…” ile Otomatik'i ya da “Türkçe · Windows-1254”ü seçin; film için hatırlanır.

### Altyazı araçları
{key:SubtitleTools} dört sekmeli bir pencere açar: **Temizle** (işitme engelliler için notları, konuşmacı etiketlerini kaldırır; önce/sonra önizlemesi gösterir), **Kare hızı** (dönüştürür ya da kaydırır), **Satırlar** (satırı böler, birleştirir, başlangıç ve bitişi oynatma konumundan alır, hata ve çakışmayı denetler) ve **Çevir**. SRT ve WebVTT okunup yazılır; ASS/SSA'da stiller korunur. Sonuç hep yeni bir dosyadır, özgün altyazıya dokunulmaz.

Görünüm (yazı tipi, boyut, kontur, gölge, arka plan, renk, yükseklik; “İnce”, “Sarı”, “Kutulu” ön ayarları) Altyazı sekmesinin Görünüm bölümündedir.

> OpenSubtitles'ın günlük bir indirme hakkı vardır (“OpenSubtitles kotası: 4/5”). Bu derlemede OpenSubtitles anahtarı yoksa çevrimiçi arama kapalıdır ve bunu söyler; klasör sağlayıcısı yine çalışır.

## Altyazı üretmek, çevirmek ve çift altyazı {subtitles-ai}

Konuşmadan altyazı üretin, altyazıyı çevirin, iki altyazıyı birlikte gösterin. Üçü de cihazda çalışır; konuşmanız, filminiz ya da altyazınız internete gönderilmez.

### Whisper ile altyazı üretmek
1. {key:GenerateSubtitles} ya da panelde “Altyazı üret…” deyin.
2. Ses izini ve dili (Otomatik, Türkçe, İngilizce) seçin; isterseniz “İngilizceye çevir”i işaretleyin.
3. Modeli seçin: base en hızlı ve en kaba, small önerilen, large-v3-turbo en doğru ama filmin süresinden uzun sürebilir. Model ilk kullanımda boyutu söylenerek, onayınızla bir kez indirilir.
4. “Üret” deyin. İş arka planda sürer; bildirimden izleyebilir ya da iptal edebilirsiniz. Altyazı `film.tr.whisper.srt` adıyla filmin yanına yazılır; klasör yazılamazsa uygulamanın klasörüne yazılır ve “Farklı kaydet…” sunulur.

Modelleri Ayarlar › Altyazı üretimi'nden silebilirsiniz. Whisper arm64 telefon ve tabletlerde çalışır.

### Altyazıyı çevirmek
“Altyazıyı çevir…” (ya da altyazı araçlarının Çevir sekmesi) Türkçe ↔ İngilizce çevirir. Çeviri modeli (İngilizce → Türkçe 475 MB, Türkçe → İngilizce 81 MB) boyutu söylenip onayla indirilir ve Ayarlar › Altyazı'dan silinir. Sonuç yeni bir dosyadır (`film.tr.srt`) ve özgünüyle birlikte ikincil altyazı olarak gösterilir.

### Çift altyazı
{key:SecondarySubtitles} ikincil altyazıyı açar ya da kapatır; ikinci izi Altyazı sekmesinin “İkincil altyazı” bölümünden seçersiniz. Bir dili öğrenirken özgün ve çeviri altyazıyı birlikte okumak için uygundur.

## Senkron ve gecikme {sync}

Altyazı ya da ses kayıyorsa üç yol var: küçük adımlar, senkron asistanı ve çıkış cihazının gecikme profili.

1. Altyazıyı {key:SubtitleDelayDown} ve {key:SubtitleDelayUp} ile küçük adımlarla öne ya da arkaya alın; sesi {key:AudioDelayDown} ve {key:AudioDelayUp} ile.
2. Ne kadar kaydığını bilmiyorsanız **senkron asistanını** kullanın: repliği duyunca {key:SyncMarkAudio}, altyazıda göründüğünde {key:SyncMarkSubtitle}, sonra {key:SyncApply}. Beğenmezseniz {key:SyncUndo}.
3. Panelin **Senkron** sekmesi ses ve altyazı gecikmesini, altyazı hızını ve yenileme hızı eşlemeyi (HDMI'de 23,976 Hz gibi) gösterir. Yaptığınız gecikme o dosya için hatırlanır.

### Gecikme profilleri
Bluetooth kulaklık, AV alıcı ve USB DAC gibi her çıkış cihazı sesi farklı gecikmeyle çalar. Ayarlar › Gecikme profilleri'nde cihazın gecikmesini elle ya da mikrofonla (beş kısa “test sesi” çalınır) ölçerek kaydedin; cihaz çıkış olunca profili kendiliğinden uygulanır ve kısa bir bildirim gösterilir. Profil, VLC'nin klasik oynatıcısındaki tek Bluetooth gecikmesinin yerini alır; ikisi toplanmaz. Ölçüm için duraklatılan oynatma, ölçüm bitince ayarları kapatmış olsanız da sürer.

> Ölçüm sessiz bir odada yapılmalıdır. Tekrarlar birbirinden 20 ms'den fazla ayrılırsa ölçüm tutarsızdır ve bunu söyler.

## Müzik motoru ve ses kalitesi {music-engine}

Yerel müzik dosyalarını Nağmedar'ın kendi **müzik motoru** çalar: FFmpeg çözücülü, 64 bit kayan noktalı bir motor. Nerede: Ayarlar › Müzik. Motoru kapatırsanız müzik libVLC ile çalar.

### Bit-perfect ve USB DAC
- Ayarlar › Müzik › Çıkış: Otomatik, AAudio, bit-perfect ya da USB DAC. Otomatik, cihaz alıyorsa (Android 14 ve sonrası; USB DAC ya da HDMI) bit-perfect, almıyorsa AAudio kullanır.
- Bit-perfect'te örnekler karıştırılmadan, yeniden örneklenmeden gider. USB DAC seçilince DAC doğrudan sürülür ve DAC'ın ses denetimi uygulamanın sesidir; kablo çekilince çıkış başka yere düşer.
- Ekolayzer sayfasında zincirin yanındaki **sinyal yolu** panelinde dosyadan çıkışa her aşama görünür; bit-perfect değilse ilk nedeni yazar.

### Boşluksuz, çapraz geçiş, ReplayGain
- Bir albümün parçaları arada boşluk ya da tık olmadan birbirini izler: sıradaki parça önceden çözülür ve örnek doğruluğunda devralınır.
- Çapraz geçiş (2, 4, 8 sn; “Eşit güç” ya da “Doğrusal”) Ayarlar › Müzik'tedir; albüm içindeki ardışık parçalarda kendiliğinden kapalıdır.
- ReplayGain parçaları aynı gürlüğe getirir: parça ya da albüm kazancı, ön kazanç, kırpılmayı önleme. “Kütüphaneyi tara” albümleri ölçüp ReplayGain etiketlerini dosyalara siz başlatınca yazar.

### Ekolayzer ve test
- {key:Equalizer} (Ses › Ekolayzer…): 20 bantlı grafik ekolayzer, 10 bantlı parametrik ekolayzer, AutoEq kulaklık profili ve sınırlayıcı. “Zinciri atla” tek tuşla hepsini devre dışı bırakır ve çıkışı bit-perfect yapar.
- **A/B testi** (Ekolayzer sayfasında; {key:AbSwitch} tarafı değiştirir) zincirli (A) ve zincirsiz (B) sesi aynı düzeyde karşılaştırır; kör testte X her turda rastgele A ya da B olur ve cevaplar sonda açılır.
- Hız (perde korunarak), perde, denge ve tek kanal ayrıca ayarlanır; DSD (DSF, DFF) ve tracker modülleri (MOD, XM, IT…) de çalınır.

> Hız, perde, denge, tek kanal, çapraz geçiş ve DSP zinciri örnekleri değiştirir; ayarlıyken çıkış bit-perfect olmaz.

## Müzik kütüphanesi ve listeler {music-library}

Müzik sayfasında filtreleyin, sıralayın, listeler yapın. Büyük pencerede üç bölme vardır (gruplar, parça tablosu, şimdi çalıyor); telefonda mini oynatıcıya dokununca tam ekran oynatıcı açılır (kapağı sola kaydırmak sonraki, sağa kaydırmak önceki parçayı çalar).

- **Filtreler ve sütunlar:** bit derinliği, örnekleme hızı ve kayıplı biçim filtreleri; tablonun sütunlarını başlığın sağ tık menüsünden seçersiniz.
- **Sıradaki** ({key:PlayQueue}): parçanın menüsündeki “Hemen sonra oynat” çalanın ardına, “Sıraya ekle” sona koyar. Sıralamak için tutamaçla sürükleyin ya da Alt+↑/↓ kullanın.
- **Çalma listeleri:** her liste bir sekmedir. M3U, M3U8, PLS ve XSPF içe ve dışa aktarılır; başka bilgisayarda yapılmış listelerin dosyaları adlarından bulunur.
- **Akıllı liste:** Çalma listeleri › Akıllı liste ile kurallarla (sanatçı, tür, puan, çalınma, eklenme…) kendiliğinden dolan liste yapın; “Kuralları düzenle…” ile değiştirin.
- **CUE sayfası:** yanında aynı adlı CUE sayfası (.cue) olan tek dosyalık albüm kütüphanede parçalarıyla görünür ve örnek doğruluğunda çalınır; bir şey yapmanız gerekmez.
- **Puan:** 1–5 yıldızı tabloda, şimdi çalıyor bölmesinde ya da “Puan ver…” ile verin. Bir parça yarısı ya da dört dakikası dinlenince çalınmış, önce geçilirse atlanmış sayılır.

## Dönüştürücü, uyku zamanlayıcısı ve alarm {convert-sleep}

Müzik dosyalarını başka biçime yeni dosyalar olarak çevirin; uyku zamanlayıcısıyla müzik kendiliğinden dursun, alarmla müzik ya da radyoyla uyanın.

### Müzik dönüştürmek
1. Parçaları seçin (ya da albümün, sanatçının, klasörün başlığındaki “Dönüştür…” düğmesine basın).
2. “Dönüştür…” deyin; biçimi seçin: Opus (128, 160, 192 kbps), MP3 (V0, 320 kbps) ya da AAC (256 kbps, M4A).
3. Hedef klasörü sistemin seçicisiyle bir kez seçin. Yeni dosyalar “Sanatçı/Albüm/03 Başlık” düzeniyle ya da tek klasöre yazılır; asıl dosyalara dokunulmaz.

İlerleme ve İptal bildirimde ve kütüphanenin üstündeki şeritte görünür. Pil azalınca ya da cihaz ısınınca iş bekler. Kayıplı bir parçayı yeniden dönüştürmek kalitesini biraz daha düşürür.

### Uyku zamanlayıcısı ve alarm
- {key:SleepTimer}: 15 dk'dan 2 saate ya da parça sonuna kadar; ses son 30 sn'de yavaşça kısılır, “+15 dk” uzatır.
- Ayarlar › Alarm: saat, günler ve son çalınan müzik, bir çalma listesi ya da internet radyosu; ses sessizlikten seçtiğiniz sürede yükselir. Android'in alarm saati olarak kurulur, yeniden başlatmadan sonra da çalışır.

## Sözler, söz düzenleyici ve scrobble {lyrics}

Parçanın sözlerini izleyin, kendi zamanlı sözünüzü yazın ve dinlediklerinizi Last.fm ya da ListenBrainz'e gönderin.

### Sözler
{key:Lyrics} çalan parçanın sözlerini pencerenin tamamında açar (şimdi çalıyor bölmesinde ve telefonun oynatıcısında “Sözler” düğmesi de vardır). Sözler sırayla dosyanın etiketlerinden, yanındaki .lrc dosyasından, en son LRCLIB'den gelir; zamanlı sözler düz sözlerden önce gelir. Söylenen satır vurgulanır, bir satıra tıklamak parçayı oraya atlatır.

Söz geç ya da erken kalıyorsa {key:LyricsEarlier} ve {key:LyricsLater} 0,1 sn'lik adımlarla kaydırır; gecikme parça başına saklanır.

### Söz düzenleyici
Söz görünümündeki “Düzenle” (zamanlı sözü olmayan parçada “Zamanla”) düğmesiyle açılır.
1. Parça çalarken {key:LrcStamp} o anki yeri satıra damgalar; {key:LrcUnstamp} son damgayı geri alır.
2. {key:LrcWordMode} satır ve sözcük kipleri arasında geçirir; ↑ ve ↓ satır seçer, Boşluk oynatır ve duraklatır.
3. Sözleri “Dosyanın yanına (.lrc)” ya da “Etikete” yazın. Önceki sözler hiç silinmez.

### Scrobble
Ayarlar › Scrobble'da Last.fm (kullanıcı adı ve parola; parola saklanmaz) ya da ListenBrainz (kullanıcı belirteci) ile giriş yapın. Bir parça yarısı ya da dört dakikası dinlenince sayılır; video, podcast ve akış gönderilmez. Çevrimdışıyken scrobble'lar cihazda bekler ve ağ gelince gönderilir.

## Podcastler {podcasts}

Podcastler ekranı abonelikleri, yeni bölümleri ve indirmeleri tutar. Hesap gerekmez.

1. **Bulmak:** arama kutusuna yazın ({key:PodcastSearch}); sonuçlar Podcast Index'ten, yetmezse iTunes'tan gelir. “Abone ol” tek dokunuşla ekler (Geri al çubuğu vardır). Elinizde bir RSS adresi varsa üç noktalı menüden “RSS adresiyle ekle…”.
2. **Taşımak:** başka uygulamalardan “OPML içe aktar…”; AntennaPod, Pocket Casts, Podcast Addict ve Overcast aynı dosyayı okur. “OPML dışa aktar…” abonelik listenizi verir.
3. **Dinlemek:** bölüme dokunun; her bölüm kaldığı yerden sürer, kapanışa ya da %95'e gelince dinlendi sayılır. Bölüm sayfasında kısımlar, döküm (oynatmayı izler, içinde aranır), notlar ve indirme vardır. {key:PodcastNextChapter} ve {key:PodcastPreviousChapter} kısımlar arasında geçer.
4. **İndirmek:** “İndir” bölümü cihaza alır. Ayarlar › Podcastler'de yenileme sıklığı (1, 3, 6, 12 ya da 24 saat), yalnızca Wi-Fi, bildirimler, depolama sınırı ve dinlenenleri silme günü ayarlanır. Her podcast'in kendi ayarında otomatik indirme, hız (0,5–3×), girişi ve kapanışı atlama vardır.

> Podcast abonelikleri yedeğe girmez; yedek yerine OPML dışa aktarın.

## Radyo {radio}

Radyo ekranı internet radyolarını Radio Browser rehberinden bulur. Sekmeler: Favoriler, Son çalınanlar, Ara, İçe aktarılanlar.

1. **Ara** sekmesinde ada göre arayın ({key:RadioSearch} oraya götürür); ülke, dil, etiket, kodek ve en düşük bit hızıyla süzün, oy, çok dinlenen, ad ve bit hızına göre sıralayın.
2. Radyoyu Enter ile çalın; sağ tık menüsünde favorilere ekleyebilir, “Bu radyoya oy ver” diyebilir (günde bir) ve akış adresini kopyalayabilirsiniz.
3. M3U, M3U8 ya da PLS dosyalarını “Dosya içe aktar…” ile alın; zaten kayıtlı olanlar ayrı sayılır.
4. Çalarken sayfanın üstündeki şeritte radyonun logosu, o an çalan “Sanatçı - Parça” başlığı ve oynat/duraklat düğmesi durur; bildirimde ve kilit ekranında da görünür.

### Kayıt ve program
- **Kayıt:** {key:Record} (video oynatıcıda ekran görüntüsü düğmesinin menüsü ve klasik oynatıcıların seçenekleri de) oynayan akışı geldiği gibi kaydeder; en iyisi yayınlar, radyo ve TV içindir. Müzik motorunun çaldığı yerel dosyalar kaydedilmez. Kayıt önce uygulamanın alanına yazılır, bitince Ayarlar › Kayıt'ta seçtiğiniz klasöre konur.
- **Program:** Ayarlar › Alarm bir radyoyu istediğiniz saatte ve günlerde uyandırma alarmı olarak çalar; podcast'lerin yeni bölümleri arka planda kendiliğinden yenilenir.

## Yayınlama (Chromecast ve DLNA) {cast}

Videoyu ya da müziği aynı ağdaki bir Chromecast'e ya da DLNA cihazına (TV, alıcı, hoparlör) gönderin; gönderen libVLC'dir.

1. Telefon ve cihaz aynı ağda olsun.
2. Alt çubuktaki, mini oynatıcıdaki, şimdi çalıyor bölmesindeki ya da oynatıcıdaki Yayınla düğmesine basın; {key:Cast} ya da Oynatma menüsü de açar.
3. Listeden cihazı seçin; bulunan cihazlar geldikçe ve gittikçe güncellenir. “Bu cihaz” yayını bitirir.
4. Yayın sürerken oynatıcı denetimleri uzaktaki cihazı yönetir.
5. Bir filmin yayınını mini oynatıcıdan, bildirimden ya da listeden durdurursanız film bu cihazda kaldığı yerden video oynatıcıda sürer; uygulama arka plandaysa o yerde duraklatılır ve oynatıcıyı açınca oradan devam eder.

> Cihaz listede görünmüyorsa denetleyin: ikisi de aynı Wi-Fi'de olsun (misafir ağında değil), TV açık ve DLNA ya da medya paylaşımı açık olsun, Chromecast uyanık olsun, modemde multicast (mDNS, UPnP) açık ve istemci yalıtımı kapalı olsun. Bu cihazdaki bir VPN yerel ağı gizleyebilir.

## Tarayıcıdan uzaktan erişim {remote-access}

Bu ağdaki bir bilgisayarın ya da telefonun tarayıcısından kütüphanenize bakın, oynatmayı denetleyin, dosya gönderin. Sayfa bu cihazdaki sunucudan gelir; internetten geçmez.

1. Ayarlar › Uzaktan erişim › “Tarayıcıdan aç” anahtarını açın; sunucu başlar.
2. “Adresler ve bağlantılar…” sayfanın adresini gösterir; tarayıcıda açın.
3. Sertifikayı bu cihaz kendisi ürettiği için tarayıcı bir kez uyarır: adresi denetleyin ve devam edin.
4. Cihazın bildirimde gösterdiği 6 haneli tek seferlik kodu girin (60 sn geçerlidir). Her yeni tarayıcı için yeni kod gerekir.

Tarayıcıya hangi bölümlerin (videolar, müzik, çalma listeleri, arama), dosya ve ağ gezgininin, geçmişin, oynatma denetiminin ve günlük dosyalarının açık olacağını Ayarlar › Uzaktan erişim sayfasından seçersiniz.

> Bağlantı her zaman HTTPS'tir ve bunun kapatılabilen bir ayarı yoktur. Kullanmadığınız zaman anahtarı kapatın; günlük dosyalarını yalnızca bir sorunun peşindeyken açın.

## DeX, klavye ve fare {desktop}

Geniş pencerede (DeX, harici ekran) Nağmedar masaüstü gibi çalışır: üstte menü çubuğu, solda kenar çubuğu, altta oynatıcı çubuğu.

- **Menü çubuğu:** Medya, Oynatma, Ses, Video, Altyazı, Araçlar, Görünüm ve Yardım menüleri; her öğenin yanında tuşu yazar. Alt'a tek başına ya da F10'a basınca klavye menü çubuğuna geçer. Yardım menüsünde bu rehber ve kısayol kartı vardır.
- **Komut paleti** ({key:CommandPalette}): komutlar, ekranlar, görünüm ayarları ve kütüphanedeki filmler ve parçalar tek kutuda aranır.
- **Fare:** sağ tık (ya da Menü tuşu) bağlam menüleri açar; Ctrl ve Shift ile tıklamak birden çok öğe seçer; boş yerde sürükleyerek lastik bantla seçersiniz. Fare geri ve ileri tuşları ekranlar arasında gezinir.
- **Sürükle ve bırak:** dışarıdan dosya bırakmak oynatır, altyazıyı videoya bırakmak yükler, medyayı oynatıcı çubuğuna bırakmak sıraya ekler.
- **Pencere:** {key:ToggleSidebar} kenar çubuğunu daraltır, {key:OpenInNewWindow} aynı ekranı yeni pencerede açar. Pencerenin yeri ve boyutu hatırlanır.
- **Kısayollar:** kısayol kartı ({key:ShortcutList}) hepsini listeler; değiştirmek için Ayarlar › Klavye kısayolları'nda bir tuşa tıklayıp yenisine basın. VLC ve mpv ön ayarları, dışa ve içe aktarma ve Türkçe Q klavyede tuşların yerini gösteren bir klavye haritası vardır.
- Ayarlar › Giriş: arayüz yoğunluğu, ipucu gecikmesi, oynatıcının tam ekran açılması, fare tekerleği ve orta tık.

> Rehberi {key:UserGuide} ile açarsınız. Tam ekran video oynatıcıda da {key:UserGuide}, {key:ShortcutList}, {key:CommandPalette} ve {key:SleepTimer} pencerelerini filmin üstünde açar, film altta sürer; {key:Settings} ana pencereyi açar.

## Yedekleme ve geri yükleme {backup}

Ayarlarınızı, listelerinizi ve puanlarınızı tek bir ZIP dosyasına yazın; başka bir cihaza ya da yeniden kurulumdan sonra geri getirin. Nerede: Ayarlar › Yedekleme.

### Yedek almak
1. “Yedekle…” deyin ve sistemin seçicisinde dosyanın yerini seçin (`nagmedar-yedek-<tarih>.zip`).
2. Dosyada ayarlar, kısayollar, çalma listeleri, akıllı listeler, puanlar ve çalınmalar, kendi sözleriniz ve söz gecikmeleri, dosya ayarları, elle seçtiğiniz kapaklar ve gecikme profilleri bulunur.

### Geri yüklemek
1. “Geri yükle…” deyin ve yedek dosyasını seçin. Ne zaman ve hangi sürümle alındığı, bölümleri sayılarıyla görünür.
2. Geri getirmek istediğiniz bölümleri işaretleyip “Geri yükle” deyin.
3. Hiçbir şey silinmez: aynı adlı ayar, liste ve profiller yedektekiyle değişir, puanlar ve çalınmalar birleştirilir. Çalma listelerinin dosyaları kütüphanede adlarından bulunur; bulunamayanlar sayılır, tarama bitince yeniden geri yüklemek yeter.

> Yedeğe medya dosyalarınız, hesap girişleriniz, bu cihazın klasör izinleri, podcast abonelikleri ve radyo favorileri girmez. Podcast'ler için OPML dışa aktarın.

## Gizlilik: neler internete çıkar? {privacy}

Nağmedar'da hesap, reklam ve izleme yoktur. Taramalar cihazda yapılır, medyanız cihazınızda kalır. Ağ yalnızca aşağıdaki özellikleri kullandığınızda açılır; istekler Nağmedar'ın adını taşır; adınız ya da e-posta adresiniz hiçbirinde yoktur.

- **Altyazı arama:** OpenSubtitles.com'a film adı ve dosyanın parmak izi gider (Ayarlar › Altyazı'dan sağlayıcıyı kapatabilirsiniz).
- **Sözler:** dosyada ve yanında zamanlı söz yoksa parçanın adı, sanatçısı, albümü ve süresi lrclib.net'e gider (Ayarlar › Sözler'den kapatılır). Sözü LRCLIB'e yüklemek yalnızca siz isterseniz olur.
- **Kapaklar:** dosyada ve klasörde kapak yoksa albümün adı ve sanatçısı MusicBrainz'e, kapak için Cover Art Archive'a gider (Ayarlar › Kapaklar'dan kapatılır).
- **Podcastler ve radyo:** aradığınız sözcük Podcast Index'e, iTunes'a ya da Radio Browser'a gider; abone olduğunuz yayınlar kendi sunucularından okunur. Radio Browser'a her çalmada bir “tıklama” bildirilir, oy verirseniz oy gider.
- **Scrobble:** yalnızca giriş yaparsanız, çaldığınız parçalar Last.fm ve ListenBrainz'e gider.
- **Modeller:** Whisper ve çeviri modelleri bir kez indirilir; sonra konuşma, film ve altyazı cihazdan çıkmaz.
- **Aynı ağ içinde:** yayınlama ve uzaktan erişim yalnızca bu ağdaki cihazlarla konuşur.
- **Parolalar ve belirteçler:** sunucu parolaları, ListenBrainz belirteci ve Last.fm oturum anahtarı Android Keystore'un anahtarıyla şifrelenir; Last.fm parolası hiç saklanmaz.

> Şu an neyin açık olduğunu görmek için Ayarlar'daki Altyazı, Sözler, Kapaklar ve Scrobble bölümlerine bakın.
