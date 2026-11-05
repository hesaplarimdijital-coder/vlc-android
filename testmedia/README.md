# Nağmedar test medya seti (S-1401)

Oynatıcının ses, şarkı sözü, podcast ve radyo yollarını sınayan örnekler. Hepsi burada **sentezlenir**: tonlar, kendi kapak resmimiz ve sözlerimiz, IBM Plex yazı tipi (OFL). Set depo ile birlikte yayımlanabilir. Nağmedar 1.0'dan beri yalnız ses çaldığı için video, HDR, Dolby Vision ve altyazı örnekleri 1.0.3'te setten çıktı.

## Nereden alınır

GitHub Actions → **Test medya seti** iş akışı → son başarılı çalışma → `nagmedar-test-media` artefaktı (yaklaşık 40 MB; 30 gün saklanır). İş akışı `testmedia/` değişince kendiliğinden, istenince elle (Run workflow) çalışır. Çalışmanın özet sayfasında her örneğin doğrulama sonucu tablo olarak görünür.

Zip'i tablete açın; klasör yapısı korunmalıdır, çünkü şarkı sözü ve CUE dosyaları parçaların yanında aranır.

## Neler var

| Klasör | İçerik |
| --- | --- |
| `audio/` | FLAC 16/44,1 · 24/96 · 24/192 (24 bitte -110 dBFS ton: bit-perfect yol için null test), FLAC 5.1 kanal tanıma, ALAC 24, AAC, Opus, MP3 (USLT + SYLT sözler), AC-3, E-AC-3, TrueHD 24 bit, DTS çekirdek, DSD64 (DSF) |
| `audio/gapless/` | Tek bir kesintisiz glissando, örnek hassasiyetinde üç parçaya bölünmüş; FLAC, MP3, AAC, Opus ve M3U8. Parça geçişlerinde tık ya da boşluk duyulmamalı |
| `audio/cue/` | Tek dosya albüm ve CUE (UTF-8 BOM ve Windows-1254) |
| `audio/tracker/` | Aynı parça MOD, XM, S3M ve IT olarak (`tools/tracker.py` yazar): motor libopenmpt ile çalar, kütüphane türünü gösterir |
| `audio/lyrics/` | LYRICS etiketli FLAC ve kelime zamanlamalı `.lrc` |
| `podcast/` | RSS (iTunes + Podcasting 2.0), ID3 bölümlü bölüm, JSON bölümler |
| `radio/` | M3U8 ve PLS istasyon listeleri |

Her örneğin neyi sınadığı ve hangi story'ye bağlı olduğu [`manifest.json`](manifest.json) içindedir.

## Elle eklenecekler

Sentezlenemeyen biçimler için kendi disklerinizden ya da dosyalarınızdan bir dakikalık kesit alın (bu dosyalar depoya **eklenmez**):

- DTS-HD MA / DTS:X ve TrueHD Atmos: yalnız ses, `mkvmerge -o ses.mka -D -S --split parts:00:05:00-00:06:00 film.mkv`

## Yerelde üretmek

```sh
sudo apt install ffmpeg python3-numpy python3-pil && pip install mutagen
python3 testmedia/generate.py out/testmedia --work /tmp/testmedia-work
python3 testmedia/verify.py out/testmedia
```
