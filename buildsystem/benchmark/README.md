# Nağmedar performans ölçümleri (S-1404)

İki yoldan ölçülür:

1. **Cihazın kendisinde, bilgisayarsız:** Ayarlar › Performans. Uygulama her soğuk açılışın ilk kareye kadar süresini ve her oynatmanın gösterilen ve kayıp karelerini, şarjda değilken ve beş dakikadan uzun oynatmalarda saatlik pil tüketimini kaydeder (son onar tane). Aynı satırlar günlüğe `NagmedarPerf` etiketiyle yazılır. “Rapor” düğmesi hepsini kopyalanabilir metin olarak verir.
2. **Bilgisayardan, eşiklerle:** `application/benchmark` modülündeki macrobenchmark'lar. Uygulamanın `benchmark` derlemesini (`app.nagmedar.benchmark`, yüklü uygulamanın yanına kurulur; release kodu, profileable) ölçer ve sonuçları [`thresholds.json`](thresholds.json) ile karşılaştırır.

| Test | Ölçtüğü | Eşik |
| --- | --- | --- |
| `StartupBenchmark.cold` | Başlatıcıdan kütüphanenin ilk karesine, süreç yokken (10 kez, ortanca) | ≤ 1000 ms |
| `StartupBenchmark.warm` | Aynısı, süreç dururken | ≤ 500 ms |
| `ScrollBenchmark.settings` | Ayarlar üç kez aşağı, üç kez yukarı savrulurken kare süreleri | P90 gecikme ≤ 0 ms, P95 CPU ≤ 16 ms |
| `ScrollBenchmark.films` | Filmler listesi (cihazda film yoksa atlanır) | aynı |
| `PlaybackTest.filmsDropNoFrames` | Test setinin 4K HDR10 örnekleri başka bir uygulamadan açılır gibi (“Şununla aç”) oynatılır; oynatıcı kapanırken yazdığı `NagmedarPerf` satırındaki kayıp kare oranı | ≤ %0,1 |

## Çalıştırmak

Gerekenler: Android SDK platform-tools (`adb`), Python 3, USB hata ayıklaması açık ve kilidi açık bir cihaz, pil %25'in üstünde.

1. GitHub Actions → CI → son başarılı çalışmanın **nagmedar-benchmark** artefaktını indirip açın (uygulamanın benchmark APK'sı, testlerin APK'sı ve bu klasör).
2. Oynatma testi için **nagmedar-test-media** artefaktını (S-1401) cihazda bir klasöre açın; betik `hdr10_2160p23976_hevc10_eac3.mkv` ve `stress_2160p23976_hevc10_100mbps.mkv` dosyalarını kendisi bulur.
3. Çalıştırın:

```sh
sh buildsystem/benchmark/run.sh --apks ~/Downloads/nagmedar-benchmark
# yalnız biri: --only StartupBenchmark | ScrollBenchmark | PlaybackTest
# kendi filminiz: --video /sdcard/Movies/film.mkv --seconds 120
```

Sonuçlar `benchmark-results/TARİH/` altına düşer: macrobenchmark JSON'u ve Perfetto izleri (`device/`), `perf.log`, `instrument.txt` ve tablo (`summary.md`). Bir eşik aşılırsa ya da bir test hata verirse betik 1 ile çıkar.

## Notlar

- Benchmark derlemesi ayrı bir uygulama olarak kurulur: kütüphanesi ve ayarları yüklü Nağmedar'dan ayrıdır. Betik ilk açılış adımlarını “Atla” ile geçer; kaydırma testinin Filmler'i doldurması için cihazda film olmalı.
- Pil tüketimi bilgisayara bağlıyken ölçülemez (USB şarj eder); onu Ayarlar › Performans gösterir: şarjdan çıkarıp beş dakikadan uzun bir film oynatın.
- CI bu testleri yalnızca derler; cihazda çalıştırmak elle yapılır.
