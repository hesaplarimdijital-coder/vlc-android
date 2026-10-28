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
