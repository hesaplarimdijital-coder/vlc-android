# Nağmedar performans ölçümleri (S-1404)

İki yoldan ölçülür:

1. **Cihazın kendisinde, bilgisayarsız:** Ayarlar › Performans. Uygulama her soğuk açılışın ilk kareye kadar süresini, şarjda değilken ve beş dakikadan uzun oynatmalarda saatlik pil tüketimini kaydeder (son onar tane). Aynı satırlar günlüğe `NagmedarPerf` etiketiyle yazılır. “Rapor” düğmesi hepsini kopyalanabilir metin olarak verir.
2. **Bilgisayardan, eşiklerle:** `application/benchmark` modülündeki macrobenchmark'lar. Uygulamanın `benchmark` derlemesini (`app.nagmedar.benchmark`, yüklü uygulamanın yanına kurulur; release kodu, profileable) ölçer ve sonuçları [`thresholds.json`](thresholds.json) ile karşılaştırır.

| Test | Ölçtüğü | Eşik |
| --- | --- | --- |
| `StartupBenchmark.cold` | Başlatıcıdan kütüphanenin ilk karesine, süreç yokken (10 kez, ortanca) | ≤ 1000 ms |
| `StartupBenchmark.warm` | Aynısı, süreç dururken | ≤ 500 ms |
| `ScrollBenchmark.settings` | Ayarlar üç kez aşağı, üç kez yukarı savrulurken kare süreleri | P90 gecikme ≤ 0 ms, P95 CPU ≤ 16 ms |
| `ScrollBenchmark.music` | Müzik'in parça tablosu (kütüphanede müzik yoksa atlanır) | aynı |
| `PlaybackTest.trackPlays` | Müzik'te bir parçaya dokunulur; oynatma oturumu birkaç saniye içinde “çalıyor” der ve `--seconds` sonra hâlâ çalar | 10 s içinde başlar |

## Çalıştırmak

Gerekenler: Android SDK platform-tools (`adb`), Python 3, USB hata ayıklaması açık ve kilidi açık bir cihaz, pil %25'in üstünde.

1. APK'ları yerelde derleyin (CI 1.0'dan beri APK yayımlamaz): `./gradlew :application:app:assembleBenchmark :application:benchmark:assembleBenchmark`, sonra ikisini bir klasöre koyun.
2. Müzik kaydırma ve oynatma testleri için benchmark derlemesinin kütüphanesinde müzik olmalı.
3. Çalıştırın:

```sh
sh buildsystem/benchmark/run.sh --apks ~/Downloads/nagmedar-benchmark
# yalnız biri: --only StartupBenchmark | ScrollBenchmark | PlaybackTest
# parça daha uzun çalsın: --seconds 60
```

Sonuçlar `benchmark-results/TARİH/` altına düşer: macrobenchmark JSON'u ve Perfetto izleri (`device/`), `perf.log`, `instrument.txt` ve tablo (`summary.md`). Bir eşik aşılırsa ya da bir test hata verirse betik 1 ile çıkar.

## Notlar

- Benchmark derlemesi ayrı bir uygulama olarak kurulur: kütüphanesi ve ayarları yüklü Nağmedar'dan ayrıdır. Betik ilk açılış adımlarını “Atla” ile geçer.
- Pil tüketimi bilgisayara bağlıyken ölçülemez (USB şarj eder); onu Ayarlar › Performans gösterir: şarjdan çıkarıp beş dakikadan uzun bir şey dinleyin.
- CI bu testleri yalnızca derler; cihazda çalıştırmak elle yapılır.
- 1.0'dan beri benchmark derlemesi R8'lidir (kişisel sürümün kodu ve kuralları, adlar korunur) ve Baseline Profile'ı içerir: ölçülen, teslim edilenle aynı koddur.

## Baseline Profile (1.0)

Uygulama Baseline Profile ve başlangıç profiliyle gelir: `application/app/src/main/baselineProfiles/baseline-prof.txt` (ilk açılışta ve sık yollarda JIT beklemeden önceden derlenecek sınıflar ve yöntemler) ve `startup-prof.txt` (R8'in dex dosyalarını açılışa göre dizmesi). APK'daki `androidx.profileinstaller` profili yandan yüklenen kurulumda da (mağaza olmadan) etkinleştirir. Profili `application/benchmark` modülündeki `BaselineProfileGenerator` toplar, benchmark derlemesine karşı (R8'li, adlar korunur: toplanan adlar kaynaktakilerle aynıdır):

| Test | Yol |
| --- | --- |
| `startup` | başlatıcıdan soğuk açılış (başlangıç profili de buradan) |
| `phone` | telefon ekranında (1080 × 2340): açılış → Müzik tablosu kaydırılır → bir parça çalınır, telefon oynatıcısı açılır → Podcastler → bir programın sayfası → bir bölümün sayfası → Radyo |
| `tablet` | aynısı yatay tablet ekranında (2560 × 1600) |

Ekranın sunmadığı bir adım (müzik, podcast ya da ağ yoksa) atlanır; arayüz değişince üretici çökmez, yalnızca o yol profile girmez.

### Yeniden üretmek

Arayüz değiştikçe profil yenilenmeli. CI emülatöründe (API 35, x86_64; müzik dosyaları ve koşucunun sunduğu üç bölümlük bir podcast akışıyla) tek bir çalıştırma:

```sh
gh api repos/hesaplarimdijital-coder/vlc-android/actions/workflows/emulator.yml/dispatches \
  -f ref=<dal> -F 'inputs[baseline_profile]=true'
```

İş (yaklaşık 50 dakika) profilleri ve raporu `ci/baseline-profile` dalına, depodaki yerlerine koyar. Dala almak tek komut:

```sh
git fetch origin ci/baseline-profile && git checkout origin/ci/baseline-profile -- application/app/src/main/baselineProfiles
git show origin/ci/baseline-profile:report.md   # kural sayıları ve testlerin sonucu
```

Sonra değişikliği commit'leyin. Bağlı bir cihazda da çalışır: `sh buildsystem/benchmark/baseline-profile.sh --music ~/Müzik/örnek --out /tmp/profil` (sonuç `/tmp/profil/application/app/src/main/baselineProfiles/` altında). Emülatörde müzik motorunun ve laboratuvarın yerel kitaplıkları yoktur (yalnız arm64); onların Kotlin tarafı profile girmez, Android onları ilk kullanımda JIT ile derler.
