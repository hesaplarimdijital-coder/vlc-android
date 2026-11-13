Dokunmatik ekranda parmak hâlâ hareket ederken bırakılan bildirim (`ToastHost`) ve alt sayfa (`GlassBottomSheet`) bırakıldığı yerde kalıyordu: bildirim yana kaymış, yarı soluk, süresi dolana kadar; sayfa hiç kapanmadan. Az çekilip bırakılan da yerine dönmüyordu. Nağmedar'da görüldü (Galaxy S24, Android 17; bildirimi kitin `ToastCard`'ından uyarlanmış), aynı kod kitte de vardı. 2.4.1 olarak hazır; birleştirme ve etiket sende.

**Neden.** Parmağın her hareketi kendi coroutine'inde uygulanıyordu (`scope.launch { snapTo(…) }`). Parmak hareket hâlindeyken kalkınca Android son hareketi ve kalkışı pencereye birlikte verir; Compose'un `draggable`'ı `onDragStopped`'u sırada bekleyenlerden önce başlatır. Son hareketin `snapTo`'su bırakışın başlattığı `animateTo`'dan sonra çalışıp onu durduruyordu (`Animatable`'ın kilidi). Parmak durup öyle kalkınca sorun görünmüyordu; bu yüzden bazen oluyor, bazen olmuyordu.

**Commit'ler** (her biri CI'da yeşil; geçmiş yeniden yazılmadı):

1. `107a0c1` Yalnızca testler: 2.4.0'ın ne yaptığını olduğu gibi söyler (bildirim ve sayfa bırakıldığı yerde kalır) ve yeşildir.
2. `99543d2` Bildirim: yeri artık kit içi bir `FingerOffset`; parmak onu anında taşır, bırakınca `settle()` dışarı ya da yerine götürür. Bildirimin iki testi çevrildi, bir test daha (geniş pencere).
3. `6c9214b` Alt sayfa: `SheetWay.drag` de bir `FingerOffset`; `SheetPull`'un coroutine kapsamı gitti. Sayfanın iki testi çevrildi; listesinden fırlatılan sayfa için bir test daha.
4. `1131f88` İncelemeden: testler, bırakıldığı anda parmağın o şeyi gerçekten taşımış olduğunu da söyler.
5. `f9a345d` Sürüm ve belgeler: `VERSION_NAME=2.4.1`, CHANGELOG, MIGRATION (yeni bölüm), README.

**Testler.** `LetGoInMotionTest` (6 test): son hareketle kalkışı birlikte verir, coroutine'leri cihazın ana iş parçacığındaki gibi sıraya koyar (`StandardTestDispatcher`). Kitin öteki testlerinin kurulduğu kural her coroutine'i başlatıldığı anda çalıştırır; `ToastSwipeTest` ve `SheetGestureTest` bu yüzden sorunu hiç göstermedi (Compose 1.12 o kuralı zaten kullanımdan kaldırıyor; derleme kaydında her test için uyarısı var). Tüm takım: 299 test, 0 hata.

**Doğrulama, neyi kapsıyor.**
- Kitin kendi kodu: kitin testleri (yukarıda). İlk commit aynı testlerin 2.4.0'da tersini gösterdiğini kaydeder.
- Cihaz tarafı Nağmedar'da görüldü; onun bildirimi kitinkinden uyarlanmış, düzeltmesi aynı tasarım. Android 17 emülatöründe, telefon boyutunda ekranda, ham dokunma olaylarıyla (son hareket ve kalkış 0–0,1 ms arayla): 1.0.5'te fırlatılıp bırakılan bildirim bırakıldığı yerde kalıyor, az kaydırılan yana kaymış duruyor; 1.0.6'da fırlatılan gidiyor, az kaydırılan yerine dönüyor.
- Kitin örnek uygulaması bir cihazda ya da emülatörde denenmedi. Etiketten önce CI'ın örnek APK'sında bildirime ve “Glass sheet”e telefonda bir bakman iyi olur.
- Sayfanın listesinden çekilince 2.4.0'da sorun olmadığı koddan okundu, 2.4.0'da test edilmedi.

**Sürüm.** Düzeltme olduğu için üçüncü sayı: yeni bir davranış gelmiyor, 2.3.0'ın yazdığı davranış her bırakışta geçerli oluyor. Önceki sürümlerde görünür telefon değişiklikleri ikinci sayıyı artırmıştı; 2.5.0 dersen değişecek yerler `gradle.properties`, CHANGELOG başlığı, MIGRATION başlığı ve README'deki dört satır.

**Bağımsız inceleme.** Tüm farkı okuyan ayrı bir inceleme kodda engel bulmadı; belgelerde iki yanlışı ve testlerde bir zayıflığı gösterdi, üçü de giderildi:
- MIGRATION'daki “kendi kaydırması olan uygulama” öğüdü eksikti: yeni hareket gelince süren canlandırmayı iptal etmek de gerekir (önceden `Animatable`'ın kilidi yapıyordu). Eklendi.
- CHANGELOG'un doğrulama maddesi neyin nerede görüldüğünü karıştırıyordu. Yeniden yazıldı.
- Testler “yerine döndü” ile “hiç kıpırdamadı”yı ayıramıyordu. `1131f88`.
- Testlerin yorumundaki “parmağın altında kayan şey her iki hareketten birini alır” gözleminin nedeni Nağmedar tarafının incelemesinde bulundu: Compose, bir öğeye, öğenin içindeki yeri değişmemiş bir hareketi vermez (`HitPathTracker`); parmağı tam izlemiş bir şey, testin eşit adımlarında her ikinci harekette bu durumdadır. Yoruma yazıldı.
- Bakılıp bırakılan, bu PR'da yok: `MiniSwipeArea` (`ExpandablePlayer.kt`) aynı hatayı taşımıyor (hareket de bırakış da aynı sıraya giriyor), ama bırakış kararı kaymanın bir hareket gerideki değerine bakıyor; eşiği son hareketle geçen bir kaydırma titreşimi verip geri dönebilir. `ShellScroll.settle()` de yeni bir kaydırma gelince iptal edilmiyor gibi görünüyor (yalnızca okundu). İstersen ayrı bir PR'da bakarım.

**Commit yazarı.** Bu PR'ın commit'leri `Claude <noreply@anthropic.com>` adıyla. Kitin kuralı yazar olarak senin adresini istiyor; Nağmedar oturumunda “e-posta adresimi hiçbir yere koyma” dediğin için adresini yazmadım. Hangisinin geçerli olduğunu sen söyle: atılmış commit'ler geçmiş yeniden yazılmadan değişmez, sonrakileri dediğin gibi atarım.

**Yayın.** PR'ı birleştir, sonra `2.4.1` etiketini `main` üzerinde aç. Uygulamalarda sürümü `2.4.1` yapmak yeter; kitin bildirimini kendi koduna uyarlamış olan Nağmedar düzeltmeyi kendi 1.0.6'sında taşıyor.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01CCjCQf9dHq1sPdX5uoz8qa
