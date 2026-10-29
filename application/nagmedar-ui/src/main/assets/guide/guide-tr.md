# Nağmedar kullanıcı rehberi

<!--
  Rehberin Türkçe metni (S-1504). Biçim: "## Başlık {kısa-ad}" bir bölüm başlatır; "### " küçük başlık,
  "- " madde, "1. " adım, "> " ipucu, **kalın**. {key:KısayolAdı} o kısayolun tuşlarını, tablodaki
  son haliyle yazar (ShortcutId'nin adı); kullanıcı bir tuşu değiştirirse rehber de değişir.
  Yazım kuralları ve terimler: application/nagmedar-ui/TERIMLER.md. İngilizcesi guide-en.md'dedir;
  iki dosyanın bölümleri aynı sırada ve aynı kısa adlarla gider.
-->

## İlk açılış ve kütüphane {first-run}

Nağmedar bir ses oynatıcısıdır: müzik, podcast ve radyo. Kütüphanesini bu cihazdaki ve bağlı disklerdeki müzikten kurar. Hiçbir dosya yüklenmez, dosyalarınıza siz istemedikçe dokunulmaz.

### İlk açılışta
1. **Medya erişimi:** “Medya dosyaları” iznini verin ya da “Klasör seç” ile yalnızca göstermek istediğiniz klasörleri seçin.
2. **Kaynaklar:** bu cihazdaki medya, harici disk, seçtiğiniz klasörler, ağ paylaşımları, podcast ve radyo. İstediklerinizi işaretleyin. Altında müziğin klasörlerini de seçebilirsiniz; seçmezseniz Müzik kütüphanenin tümünü gösterir. Hepsini sonra Ayarlar › Kütüphane'den değiştirebilirsiniz.
3. **Ses çıkışı:** “Test sesini çal” deyin, sonra “Duydum” ya da “Duymadım” seçin.
4. **Bildirimler:** kilit ekranındaki oynatma denetimleri ve tarama, indirme ilerlemesi için izin verin.

Her adım “Atla” ile geçilir. Adımları yeniden görmek için Ayarlar › Başlangıç › “İlk açılış adımlarını yeniden göster”.

### Kütüphanede
- Uygulama **Müzik** sayfasıyla açılır: parçaları, sanatçıları, albümleri, türleri, besteci ve yıllara göre ve klasörler olarak gösterir.
- Telefonda Müzik sayfasının üstündeki **Son çalınan müzik** rafı en son çaldığınız parçaları gösterir; geniş pencerede aynı parçalar araç çubuğundaki **Son çalınanlar** görünümündedir.
- Ara kutusu ya da {key:CommandPalette} kütüphaneyi, ekranları ve komutları tek yerden arar.

### Müziğin klasörleri
Müzik yalnızca seçtiğiniz klasörleri gösterebilir; alt klasörler de dahildir. Klasör seçmezseniz kütüphanedeki bütün müziği gösterir. Nerede: Ayarlar › Kütüphane ya da Müzik'in boş sayfasındaki “Klasör ekle”.
1. “Klasör ekle” düğmesine basın.
2. “Klasör seç…” ile sistemin seçicisinden bir klasör seçin ya da önerilen klasörlerden birine dokunun (bir SD kartın üst klasörlerinden biri ya da bir diskin tümü gibi).
3. Bir alt klasörü göstermemek için onu da ekleyin ve menüsünden “Hariç tut” deyin; “Kaldır” klasörü listeden çıkarır.

> Klasörler bu cihaza özgüdür; yedeğe girmez.

> Telefonda alt çubukta Müzik, Podcast, Radyo ve ⋯ durur; ⋯ (Daha fazla) öteki ekranları listeler: çalma listeleri, bu cihaz, ağ, akışlar, indirmeler, ayarlar.

## Ağ kaynakları {network}

SMB, FTP, SFTP, NFS, UPnP/DLNA ve WebDAV sunucularındaki medyaya yerel dosya gibi bakın. Nerede: Kaynaklar › Ağ (telefonda Daha fazla › Ağ).

1. “Bu ağda” altında kendini duyuran sunucular (SMB, UPnP/DLNA, mDNS) kendiliğinden görünür.
2. Görünmeyen bir sunucu için “Sunucu ekle…”: protokol, sunucu, bağlantı noktası, klasör, görünen ad, kullanıcı adı ve parola. Bir adres yapıştırırsanız form kendini doldurur.
3. Klasörleri tablo gibi gezin: Enter ya da çift tıklama açar, {key:FolderUp} bir üst klasöre çıkar, {key:Refresh} yeniler; sağ tık menüsü de vardır.
4. Dosyayı oynatın ya da sıraya ekleyin. Klasörü alt klasörleriyle oynatabilir, VLC'nin taradığı protokollerde “Kütüphaneye ekle” ile kütüphaneye aldırabilirsiniz.

> Parolayı “Bu cihazda hatırla (şifreli)” ile saklarsanız Android Keystore'un anahtarıyla şifrelenir. Saklamazsanız, uygulama yeniden başlayınca kütüphane taramaları oturum açamaz.

> Kendi imzaladığı sertifikayla çalışan bir HTTPS ya da WebDAV sunucusu için libVLC sertifikayı kabul edip etmeyeceğinizi sorar: “Sertifikayı göster”, ardından “24 saat kabul et” ya da “Kalıcı olarak kabul et”; emin değilseniz “Vazgeç”.

## Gecikme profilleri {sync}

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
- ReplayGain parçaları aynı gürlüğe getirir: parça ya da albüm kazancı, ön kazanç, kırpılmayı önleme. “Kütüphaneyi tara” albümleri ölçüp ReplayGain etiketlerini dosyalara siz başlatınca yazar; her dosyanın kopyası, sözlerde olduğu gibi, yazılırken tutulur. CUE sayfasının parçaları atlanır: hepsi aynı dosyadadır.

### Ekolayzer ve test
- {key:Equalizer} (Ses › Ekolayzer…): 20 bantlı grafik ekolayzer, 10 bantlı parametrik ekolayzer, AutoEq kulaklık profili ve sınırlayıcı. “Zinciri atla” tek tuşla hepsini devre dışı bırakır ve çıkışı bit-perfect yapar.
- **A/B testi** (Ekolayzer sayfasında; {key:AbSwitch} tarafı değiştirir) zincirli (A) ve zincirsiz (B) sesi aynı düzeyde karşılaştırır; kör testte X her turda rastgele A ya da B olur ve cevaplar sonda açılır.
- Hız (perde korunarak), perde, denge ve tek kanal ayrıca ayarlanır; DSD (DSF, DFF) ve tracker modülleri (MOD, XM, IT…) de çalınır.

> Hız, perde, denge, tek kanal, çapraz geçiş ve DSP zinciri örnekleri değiştirir; ayarlıyken çıkış bit-perfect olmaz.

## Müzik kütüphanesi ve listeler {music-library}

Müzik sayfasında filtreleyin, sıralayın, listeler yapın. Tablette ve DeX'te üç bölme vardır (gruplar, parça tablosu, sağda ayrıntı paneli) ve oynatıcı çubuğu içeriğin üstünde buzlu cam olarak durur; telefonda mini oynatıcıya dokununca tam ekran oynatıcı açılır (kapağı sola kaydırmak sonraki parçaya geçer, sağa kaydırmak Önceki düğmesi gibidir).

- **Araç çubuğu:** solda Sanatçılar, Albümler, Türler… (dar pencerede ve telefonda şu anki görünümü gösteren bir menü), sağda Oynat, Karıştır, Sütunlar ve Filtrele. Tablonun sütunlarını “Sütunlar”dan ya da başlığın sağ tık menüsünden seçersiniz; sütun başlığının menüsü o sütunu sola ya da sağa taşır, “Sütunları sıfırla” baştakileri geri getirir. Her görünüm (Albümler, Türler…) kendi sütunlarını hatırlar.
- **Ayrıntı paneli** ({key:ToggleInspector}): tabloda seçili parçanın, seçili yoksa çalanın sekmeleri: Etiketler ve spektrum (etiketler, biçim, dalga biçimi, çalarken görselleştirici), Sinyal yolu, Sözler ve Sıra. Kenarından sürükleyerek genişletin; genişliği ve gizli olup olmadığı hatırlanır. Sekmeler arasında ← ve → ile gezinirsiniz.
- **Seçim:** Ctrl ile tıklamak ekler ya da çıkarır, Shift ile tıklamak ve Shift+↑/↓ aralığı seçer, {key:SelectAll} hepsini seçer, Esc seçimi bırakır. Seçili satırlardan birine sağ tıklayınca menü seçilenlerin hepsine uygulanır.
- **Filtreler:** “Filtrele” bit derinliği (16, 24, 32 bit, DSD, kayıplı) ve örnekleme hızı filtrelerini bir panelde açar; bir filtre açıkken düğmede bir nokta görünür, “Filtreleri temizle” hepsini kaldırır.
- **Sıradaki** ({key:PlayQueue}): parçanın menüsündeki “Hemen sonra oynat” çalanın ardına, “Sıraya ekle” sona koyar. Sıralamak için tutamaçla sürükleyin ya da Alt+↑/↓ kullanın.
- **Çalma listeleri:** bütün listeler, akıllı listeler de, kart olarak görünür: kapak, ad, parça sayısı, süre ve son çalınma. Listelerde arayın; ada, son çalınmaya ya da oluşturulmaya göre sıralayın. Açık listenin adına basınca olduğu yerde değişir (Enter kaydeder, Esc vazgeçer); menüsünde yeniden adlandır, kopyasını yap, Hemen sonra oynat, Sıraya ekle, dışa aktar ve sil vardır.
- **İçe ve dışa aktarma:** M3U, M3U8, PLS ve XSPF içe ve dışa aktarılır; başka bilgisayarda yapılmış listelerin dosyaları adlarından bulunur.
- **Akıllı liste:** Çalma listeleri › Akıllı liste ile kurallarla (sanatçı, tür, puan, çalınma, eklenme…) kendiliğinden dolan liste yapın; “Kuralları düzenle…” ile değiştirin.
- **CUE sayfası:** yanında aynı adlı CUE sayfası (.cue) olan tek dosyalık albüm kütüphanede parçalarıyla görünür ve örnek doğruluğunda çalınır; bir şey yapmanız gerekmez.
- **Puan:** 1–5 yıldızı tabloda, ayrıntı panelinde ya da “Puan ver…” ile verin. Bir parça yarısı ya da dört dakikası dinlenince çalınmış, önce geçilirse atlanmış sayılır.

### Çalarken
- İlerleme çubuğunun (şimdi çalıyor bölmesi, telefonun oynatıcısı, alttaki çubuk) arkasında parçanın dalga biçimi silik görünür, geçen ve kalan süre yazar. Tutamacı sürüklerken süre baloncuğu çıkar, bırakınca oraya atlanır; çubuk odaktayken ← ve → 5 sn sarar, Home ve End başa ve sona götürür.
- Oynatma düğmeleri: karıştır ({key:Shuffle}), önceki, dolu bir daire içinde oynat ya da duraklat, sonraki ve tekrarla ({key:Repeat}). Açık olan karıştırma ya da tekrar vurgu renginde ve altında bir noktayla görünür; tekrarla düğmesi sırayla “Tümünü tekrarla” ve “Bunu tekrarla” olur, ikincisinde üstünde “1” yazar.
- Önceki düğmesi ve {key:Previous} önce parçayı başa sarar; parçanın ilk 3 saniyesinde ya da başa sardıktan sonraki 3 saniye içinde basarsanız önceki parçaya geçer. Bildirim, kilit ekranı, widget ve Bluetooth da aynı kurala uyar.
- Sanatçı ve albüm adı bağlantıdır: sanatçı Sanatçılar'da o sanatçının, albüm Albümler'de o albümün parçalarını açar.
- Ses düzeyi (çubuktaki kaydırıcı, {key:VolumeUp}, {key:VolumeDown}, {key:Mute}) saklanır: sonraki parçada ve uygulamayı yeniden açtığınızda aynıdır; ilk açılışta %100'dür.

## Dönüştürücü, uyku zamanlayıcısı ve alarm {convert-sleep}

Müzik dosyalarını başka biçime yeni dosyalar olarak çevirin; uyku zamanlayıcısıyla müzik kendiliğinden dursun, alarmla müzik ya da radyoyla uyanın.

### Müzik dönüştürmek
1. Parçaları seçin (ya da albümün, sanatçının, klasörün başlığındaki “Dönüştür…” düğmesine basın).
2. “Dönüştür…” deyin; biçimi seçin: Opus (128, 160, 192 kbps), MP3 (V0, 320 kbps) ya da AAC (256 kbps, M4A).
3. Hedef klasörü sistemin seçicisiyle bir kez seçin. Yeni dosyalar “Sanatçı/Albüm/03 Başlık” düzeniyle ya da tek klasöre yazılır; asıl dosyalara dokunulmaz.
4. Çok kanallı bir kaynak (5.1, 7.1) Opus ve AAC'de kanal düzeniyle korunur; iki kanala indirmek için “Kanallar” altında “Stereoya indir” deyin. MP3 her zaman stereodur. Etiketler, ön kapak ve zamanlı sözler yeni dosyaya taşınır.

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
2. {key:LrcWordMode} satır ve sözcük kipleri arasında geçirir; {key:LrcLineUp} ve {key:LrcLineDown} satır seçer, {key:PlayPause} oynatır ve duraklatır.
3. Sözleri “Dosyanın yanına (.lrc)” ya da “Etikete” yazın. Önceki sözler hiç silinmez.

> Etikete yazarken Nağmedar dosyanın bir kopyasını tutar: yazılan dosya yeniden okunur, sesi aynı çıkmazsa kopya geri konur; uygulama yazarken kapanırsa bu denetim sonraki açılışta yapılır. O arada başka yerde değişmiş ya da bir hafta bulunamamış bir dosyaya dokunulmaz: kopyası uygulamanın depodaki klasöründe “Kurtarılan”a konur ve nerede olduğu bir kısa haberle söylenir. Kopyaya yer yoksa sözler yazılmaz ve nedeni söylenir.

### Scrobble
Ayarlar › Scrobble'da Last.fm (kullanıcı adı ve parola; parola saklanmaz) ya da ListenBrainz (kullanıcı belirteci) ile giriş yapın. Bir parça yarısı ya da dört dakikası dinlenince sayılır; podcast ve akış gönderilmez. Çevrimdışıyken scrobble'lar cihazda bekler ve ağ gelince gönderilir.

## Podcastler {podcasts}

Podcastler ekranı abonelikleri, yeni bölümleri ve indirmeleri tutar. Hesap gerekmez.

1. **Bulmak:** arama kutusuna yazın ({key:PodcastSearch}); sonuçlar Podcast Index'ten, yetmezse iTunes'tan gelir. “Abone ol” tek dokunuşla ekler (Geri al çubuğu vardır). Elinizde bir RSS adresi varsa üç noktalı menüden “RSS adresiyle ekle…”.
2. **Taşımak:** başka uygulamalardan “OPML içe aktar…”; AntennaPod, Pocket Casts, Podcast Addict ve Overcast aynı dosyayı okur. “OPML dışa aktar…” abonelik listenizi verir.
3. **Dinlemek:** bölümün satırındaki Oynat düğmesine basın; satırın kendisine dokunmak bölüm sayfasını açar. Her bölüm kaldığı yerden (ya da girişten sonra) sürer, kapanışa ya da %95'e gelince dinlendi sayılır; “Kaldığınız yerden devam edilsin mi?” diye sorulmaz. Bölüm sayfasında kısımlar, döküm (oynatmayı izler, içinde aranır), notlar ve indirme vardır.
4. **Kısımlar:** çalan bölümün kısımları alttaki çubukta, mini oynatıcıda, şimdi çalıyor bölmesinde, ve telefonun oynatıcısında işaretlidir; işaretin üstüne gelince kısmın adı yazar. “Önceki kısım” ve “Sonraki kısım” düğmeleri ve kısım listesi oradadır; {key:PodcastPreviousChapter} ve {key:PodcastNextChapter} de kısımlar arasında geçer.
5. **İndirmek:** “İndir” bölümü cihaza alır; Araçlar › İndirmeler süren, bekleyen, inmiş ve inemeyen indirmeleri gösterir. Ayarlar › Podcastler'de yenileme sıklığı (1, 3, 6, 12 ya da 24 saat), yalnızca Wi-Fi, bildirimler, depolama sınırı ve dinlenenleri silme günü ayarlanır.
6. **Program ayarları:** her podcast'in “Podcast ayarları” penceresinde otomatik indirme, hız (0,5–3×), girişi ve kapanışı atlama ve ses yükseltme vardır. Ses yükseltme (Kapalı, Hafif, Güçlü) kısık kayıtlı bir programın bölümlerini ses düzeyinizden yüksek çalar; diğer medya her zamanki gibi çalar. Güçlendirilmiş bir bölüm çalarken ses kaydırıcısı ve tuşları sizin kendi ses düzeyinizi gösterir ve değiştirir, bölüm onun üstünde güçlendirilmiş çalmayı sürdürür.

> Podcast abonelikleri yedeğe girmez; yedek yerine OPML dışa aktarın.

## Radyo {radio}

Radyo ekranı internet radyolarını Radio Browser rehberinden bulur ve kayda alır. Sekmeler: Favoriler, Son çalınanlar, Ara, İçe aktarılanlar ve Kayıtlar.

1. **Ara** sekmesinde ada göre arayın ({key:RadioSearch} oraya götürür); ülke, dil, etiket, kodek ve en düşük bit hızıyla filtreleyin, oy, çok dinlenen, ad ve bit hızına göre sıralayın.
2. Radyoyu Enter ile çalın; sağ tık menüsünde favorilere ekleyebilir, kayda alabilir, “Bu radyoya oy ver” diyebilir (günde bir) ve akış adresini kopyalayabilirsiniz.
3. M3U, M3U8 ya da PLS dosyalarını “Dosya içe aktar…” ile alın; zaten kayıtlı olanlar ayrı sayılır.
4. Çalarken sayfanın üstündeki şeritte radyonun logosu, o an çalan “Sanatçı - Parça” başlığı ve oynat/duraklat düğmesi durur; bildirimde ve kilit ekranında da görünür.

### Radyo kaydı
- **Kayda almak:** çalan radyoyu {key:RadioRecord} ya da radyonun menüsündeki “Kayda al” kayda alır, yeniden {key:RadioRecord} kaydı durdurur. Kayıt, radyonun her yeni parça adında ayrı bir dosyaya kesilir (“Sanatçı - Parça.mp3”); duyuru örnek hassasiyetinde olmadığından kesim bir iki saniye kayabilir. Aynı anda en çok 3 radyo kayda alınır.
- **HLS ve tek dosya:** HLS radyoları (.m3u8; TRT gibi) ve parça adı göndermeyen akışlar oturum başına tek dosya olur. Şifreli HLS akışları kayda alınmaz.
- **Kayıtlar sekmesi** ({key:RadioRecordings}): süren kayıtlar (“Kaydı durdur”), zamanlanmış kayıtlar ve kayda alınan parçalar, radyoya ve güne göre. Bir parçayı çalın, paylaşın, yeniden adlandırın ya da silin (silme geri alınır); “Yalnızca tam parçaları tut” eksik parçaları siler.
- **Zamanlanmış kayıt:** “Kayıt zamanla” ile radyoyu, başlangıcı, süreyi ya da bitişi ve tekrarı (Bir kez, Her gün, Hafta içi, Haftalık) seçin; çakışan zamanlamalar işaretlenir. Kayıt sürerken cihaz ya da uygulama yeniden başlarsa, zamanlamanın süresi dolmadıysa kayıt yeni bir parçayla sürer ve satırda “kesildi” yazar; hiç kayda alınmadan geçen zaman “kaçırıldı” diye işaretlenir.
- **Ayarlar › Radyo:** kayıt klasörü (varsayılanı uygulamanın müzik klasörü), en kısa parça, parça adı değişmeden önce ve sonra tutulacak pay, boş kalacak yer, kayıtların toplam boyutu ve sınıra gelince ne olacağı (“Kaydı durdur” ya da “En eskileri sil”).
- **Alarm:** Ayarlar › Alarm bir radyoyu istediğiniz saatte ve günlerde uyandırma alarmı olarak çalar.

> Zamanlanmış kayıtlar yedeğe girer, kaydedilen sesler girmez. Kaydın dakikasında başlaması için Android'in tam alarm iznini verin; izin yoksa Kayıtlar sekmesi “Zamanlanmış kayıtlar geç başlayabilir” der.

## Yayınlama (Chromecast ve DLNA) {cast}

Müziği aynı ağdaki bir Chromecast'e ya da DLNA cihazına (TV, alıcı, hoparlör) gönderin; gönderen libVLC'dir.

1. Telefon ve cihaz aynı ağda olsun.
2. Alt çubuktaki, mini oynatıcıdaki, şimdi çalıyor bölmesindeki ya da telefonun oynatıcısındaki Yayınla düğmesine basın; {key:Cast} ya da Oynatma menüsü de açar.
3. Listeden cihazı seçin; bulunan cihazlar geldikçe ve gittikçe güncellenir. “Bu cihaz” yayını bitirir.
4. Yayın sürerken oynatıcı denetimleri uzaktaki cihazı yönetir; yayını durdurunca parça bu cihazda kaldığı yerden sürer.

> Cihaz listede görünmüyorsa denetleyin: ikisi de aynı Wi-Fi'de olsun (misafir ağında değil), TV açık ve DLNA ya da medya paylaşımı açık olsun, Chromecast uyanık olsun, modemde multicast (mDNS, UPnP) açık ve istemci yalıtımı kapalı olsun. Bu cihazdaki bir VPN yerel ağı gizleyebilir.

## Tarayıcıdan uzaktan erişim {remote-access}

Bu ağdaki bir bilgisayarın ya da telefonun tarayıcısından kütüphanenize bakın, oynatmayı denetleyin, dosya gönderin. Sayfa bu cihazdaki sunucudan gelir; internetten geçmez.

1. Ayarlar › Uzaktan erişim › “Tarayıcıdan aç” anahtarını açın; sunucu başlar.
2. “Adresler ve bağlantılar…” sunucunun durumunu (Başlat, Durdur), her adresi QR koduyla (Kopyala, Paylaş…) ve bağlanan tarayıcıları gösterir; adreslerden birini tarayıcıda açın ya da QR kodunu okutun.
3. Sertifikayı bu cihaz kendisi ürettiği için tarayıcı bir kez uyarır: adresi denetleyin ve devam edin.
4. Cihazın bildirimde (pencere öndeyken pencerede de) gösterdiği 6 haneli tek seferlik kodu girin (60 sn geçerlidir). Her yeni tarayıcı için yeni kod gerekir; sizin istemediğiniz bir bağlantıyı “Ben değilim” geri çevirir.

Tarayıcıya hangi bölümlerin (müzik, çalma listeleri, arama), dosya ve ağ gezgininin, geçmişin, oynatma denetiminin ve günlük dosyalarının açık olacağını Ayarlar › Uzaktan erişim sayfasından seçersiniz. “Nasıl çalışır…” bunları beş adımda anlatır.

> Bağlantı her zaman HTTPS'tir ve bunun kapatılabilen bir ayarı yoktur. Kullanmadığınız zaman anahtarı kapatın; günlük dosyalarını yalnızca bir sorunun peşindeyken açın.

## DeX, klavye ve fare {desktop}

Geniş pencerede (DeX, harici ekran) Nağmedar masaüstü gibi çalışır: üstte menü çubuğu, solda kenar çubuğu, altta oynatıcı çubuğu.

- **Menü çubuğu:** Medya, Oynatma, Ses, Araçlar, Görünüm ve Yardım menüleri; her öğenin yanında tuşu yazar. Alt'a tek başına ya da F10'a basınca klavye menü çubuğuna geçer. Yardım menüsünde bu rehber ve kısayol kartı vardır.
- **Komut paleti** ({key:CommandPalette}): komutlar, ekranlar, görünüm ayarları ve kütüphanedeki parçalar tek kutuda aranır.
- **Fare:** sağ tık (ya da Menü tuşu) bağlam menüleri açar; Ctrl ve Shift ile tıklamak birden çok öğe seçer; boş yerde sürükleyerek lastik bantla seçersiniz. Fare geri ve ileri tuşları ekranlar arasında gezinir.
- **Sürükle ve bırak:** dışarıdan dosya bırakmak oynatır, medyayı oynatıcı çubuğuna bırakmak sıraya ekler.
- **Pencere:** {key:ToggleSidebar} kenar çubuğunu daraltır, {key:ToggleInspector} Müzik'in ayrıntı panelini gizler ya da gösterir, {key:OpenInNewWindow} aynı ekranı yeni pencerede açar. Pencerenin yeri ve boyutu hatırlanır.
- **Kısayollar:** kısayol kartı ({key:ShortcutList}) hepsini listeler; değiştirmek için Ayarlar › Klavye kısayolları'nda bir tuşa tıklayıp yenisine basın. VLC ve mpv ön ayarları, dışa ve içe aktarma ve Türkçe Q klavyede tuşların yerini gösteren bir klavye haritası vardır.
- Ayarlar › Giriş: arayüz yoğunluğu ve ipucu gecikmesi.

> Rehberi {key:UserGuide} ile açarsınız.

## VLC'nin ekranları nerede? {classic}

Nağmedar VLC for Android'e dayanır, ama VLC'nin klasik ekranları artık açılmaz: uygulama simgesi, bildirimler ve paylaşma menüsündeki “Nağmedar ile oynat” hep Nağmedar'ı açar. VLC'de alıştıklarınız şimdi şuradadır:

- **VLC'nin ayarları:** Ayarlar › Hakkında › “Tüm ayarlar”, Nağmedar'ın tasarımında. İlk sayfadaki arama bütün sayfalarda arar; komut paleti ({key:CommandPalette}) her ayarı adıyla bulur.
- **Ağ akışı:** Kaynaklar › Akışlar. Adresi yazın ya da “Yapıştır” deyin, Enter oynatır; daha önce oynatılan akışlar altta durur. {key:OpenNetwork} oraya götürür.
- **Dosyalar:** Kaynaklar › Bu cihaz dahili depolamayı, SD kartları ve USB diskleri klasör tablosu olarak açar; bir klasörü menüsünden oynatın, sıraya ekleyin ya da müziğin kaynak klasörü yapın.
- **İndirmeler:** Araçlar › İndirmeler podcast bölümlerini ve Whisper modellerini gösterir; süren indirme iptal edilir, inemeyen yeniden denenir, dosya onayınızla silinir.
- **Medya bilgisi:** menüdeki “Medya bilgisi” Nağmedar'ın penceresini açar: etiketler, dosyanın konumu (“Konumu kopyala”) ve teknik bilgiler.
- **Kısa haberler ve sorular:** “Kopyalandı” gibi kısa haberler pencerenin altında birkaç saniye görünür. Sunucunun oturum ve sertifika soruları Nağmedar penceresinde gelir; pencere açık değilse “Nağmedar bir soru soruyor: …” bildirimine dokunun. Yeni bir harici disk takılınca “Kütüphane klasörleri” düğmeli bir haber çıkar.
- **Kaldığınız yerden:** “Kaldığınız yerden devam edilsin mi?” sorusu müzikte, radyoda ve akışlarda da Nağmedar penceresinde gelir: “Devam et” ya da “Baştan başla”, isterseniz “Listenin kalanında da böyle yap”.
- **İzinler:** medya, bildirim ve depolama izinlerini Android'in kendi penceresi sorar.
- **Widget:** ana ekranda tek Nağmedar widget'ı vardır: 4×1'de kapak, çalan, önceki, oynat ya da duraklat ve sonraki; 4×2'ye büyütünce kapak yanda durur.

## Uzun işler ve Android'in süre sınırı {jobs}

Dönüştürme, indirmeler ve kayıtlar arka planda sürer; ilerlemelerini bildirimde izler, oradan durdurabilirsiniz. Dönüştürme pil zayıfken ya da cihaz ısınınca bekler.

Android 15 ve sonrası, arka planda süren işlere günde altı saatlik bir süre sınırı koyar. Sınır dolunca Nağmedar bunu bildirimde söyler ve iş şöyle sürer:
- **Podcast indirmeleri** sonra kaldıkları yerden sürer.
- **Müzik dönüştürme** bekler, sonra kaldığı yerden sürer.
- **Radyo kaydı** durur; o ana kadarki parçalar kaydedilmiştir.
- **Kütüphane taraması** bildirimini kaldırıp sürer.

> İndirmeleri Araçlar › İndirmeler'de izleyebilirsiniz.

## Yedekleme ve geri yükleme {backup}

Ayarlarınızı, listelerinizi ve puanlarınızı tek bir ZIP dosyasına yazın; başka bir cihaza ya da yeniden kurulumdan sonra geri getirin. Nerede: Ayarlar › Yedekleme.

### Yedek almak
1. “Yedekle…” deyin ve sistemin seçicisinde dosyanın yerini seçin (nagmedar-yedek-<tarih>.zip).
2. Dosyada ayarlar, kısayollar, çalma listeleri, akıllı listeler, puanlar ve çalınmalar, kendi sözleriniz ve söz gecikmeleri, dosya ayarları, elle seçtiğiniz kapaklar, gecikme profilleri ve zamanlanmış radyo kayıtları bulunur.

### Geri yüklemek
1. “Geri yükle…” deyin ve yedek dosyasını seçin. Ne zaman ve hangi sürümle alındığı, bölümleri sayılarıyla görünür.
2. Geri getirmek istediğiniz bölümleri işaretleyip “Geri yükle” deyin.
3. Hiçbir şey silinmez: aynı adlı ayar, liste ve profiller yedektekiyle değişir, puanlar ve çalınmalar birleştirilir. Çalma listelerinin dosyaları kütüphanede adlarından bulunur; bulunamayanlar sayılır, tarama bitince yeniden geri yüklemek yeter. Zamanlanmış radyo kayıtlarının alarmları yeniden kurulur.

> Yedeğe medya dosyalarınız, kaydedilen sesler, hesap girişleriniz, bu cihazın klasör izinleri ve müziğin klasörleri, podcast abonelikleri ve radyo favorileri girmez. Podcast'ler için OPML dışa aktarın.

## Gizlilik: neler internete çıkar? {privacy}

Nağmedar'da hesap, reklam ve izleme yoktur. Taramalar cihazda yapılır, medyanız cihazınızda kalır. Ağ yalnızca aşağıdaki özellikleri kullandığınızda açılır; istekler Nağmedar'ın adını taşır; adınız ya da e-posta adresiniz hiçbirinde yoktur.

- **Sözler:** dosyada ve yanında zamanlı söz yoksa parçanın adı, sanatçısı, albümü ve süresi lrclib.net'e gider (Ayarlar › Sözler'den kapatılır). Sözü LRCLIB'e yüklemek yalnızca siz isterseniz olur.
- **Kapaklar:** dosyada ve klasörde kapak yoksa albümün adı ve sanatçısı MusicBrainz'e, kapak için Cover Art Archive'a gider (Ayarlar › Kapaklar'dan kapatılır).
- **Podcastler ve radyo:** aradığınız sözcük Podcast Index'e, iTunes'a ya da Radio Browser'a gider; abone olduğunuz yayınlar kendi sunucularından okunur. Radio Browser'a her çalmada bir “tıklama” bildirilir, oy verirseniz oy gider.
- **Scrobble:** yalnızca giriş yaparsanız, çaldığınız parçalar Last.fm ve ListenBrainz'e gider.
- **Modeller:** Whisper modelleri bir kez indirilir; sonra konuşma cihazdan çıkmaz.
- **Aynı ağ içinde:** yayınlama ve uzaktan erişim yalnızca bu ağdaki cihazlarla konuşur.
- **Parolalar ve belirteçler:** sunucu parolaları, ListenBrainz belirteci ve Last.fm oturum anahtarı Android Keystore'un anahtarıyla şifrelenir; Last.fm parolası hiç saklanmaz.

> Şu an neyin açık olduğunu görmek için Ayarlar'daki Sözler, Kapaklar ve Scrobble bölümlerine bakın.
