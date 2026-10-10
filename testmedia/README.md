# Nağmedar test medya seti (S-1401)

Oynatıcının video, ses, altyazı, şarkı sözü, podcast ve radyo yollarını sınayan örnekler. Hepsi burada **sentezlenir**: test desenleri, tonlar, kendi altyazılarımız ve IBM Plex yazı tipi (OFL). Tek istisna Dolby Vision profil 7 örneğidir; o da dovi_tool'un MIT lisanslı test dosyalarından üretilir. Set depo ile birlikte yayımlanabilir.

## Nereden alınır

GitHub Actions → **Test medya seti** iş akışı → son başarılı çalışma → `nagmedar-test-media` artefaktı (yaklaşık 350 MB; 30 gün saklanır). İş akışı `testmedia/` değişince kendiliğinden, istenince elle (Run workflow) çalışır; elle çalıştırırken 100 Mbit/s yük testi kapatılabilir. Çalışmanın özet sayfasında her örneğin doğrulama sonucu tablo olarak görünür.

Zip'i tablete (ya da TV testleri için harici diske) açın; klasör yapısı korunmalıdır, çünkü altyazı ve şarkı sözü dosyaları videoların ve şarkıların yanında aranır.

## Neler var

| Klasör | İçerik |
| --- | --- |
| `video/` | 4K HEVC Main 10: HDR10, HDR10+ (üç sahne), Dolby Vision 8.1 (mkvmerge yapılandırma kaydıyla), Dolby Vision 5 (yalnız işaretleme: taban katman gerçek IPT değil, renkler bilerek yanlış), 100 Mbit/s yük testi; Dolby Vision 7 MEL (dovi_tool'un MIT lisanslı test dosyalarından, 256×144, E-AC-3 + SRT + bölümlerle: SP-03 dönüştürme girdisi); HLG 1080p50; 1080i25 interlaced H.264; AV1 10 bit; dört altyazı izli MKV (SRT, ASS + gömülü yazı tipi, PGS, zorunlu izler, bölümler) ve yanındaki Windows-1254 SRT, UTF-8 SRT, ASS |
| `audio/` | FLAC 16/44,1 · 24/96 · 24/192 (24 bitte -110 dBFS ton: bit-perfect yol için null test), FLAC 5.1 kanal tanıma, ALAC 24, AAC, Opus, MP3 (USLT + SYLT sözler), AC-3, E-AC-3, TrueHD 24 bit, DTS çekirdek, DSD64 (DSF) |
| `audio/gapless/` | Tek bir kesintisiz glissando, örnek hassasiyetinde üç parçaya bölünmüş; FLAC, MP3, AAC, Opus ve M3U8. Parça geçişlerinde tık ya da boşluk duyulmamalı |
| `audio/cue/` | Tek dosya albüm ve CUE (UTF-8 BOM ve Windows-1254) |
| `audio/lyrics/` | LYRICS etiketli FLAC ve kelime zamanlamalı `.lrc` |
| `podcast/` | RSS (iTunes + Podcasting 2.0), ID3 bölümlü bölüm, JSON bölümler |
| `radio/` | M3U8 ve PLS istasyon listeleri |

Her örneğin neyi sınadığı ve hangi story'ye bağlı olduğu [`manifest.json`](manifest.json) içindedir.

## Elle eklenecekler

Sentezlenemeyen biçimler için kendi disklerinizden ya da dosyalarınızdan bir dakikalık kesit alın (bu dosyalar depoya **eklenmez**):

- Dolby Vision profil 7 FEL ve gerçek profil 5 (renk doğruluğu için; set yalnızca MEL'i ve P5 işaretlemesini içerir): `mkvmerge -o dv_p7.mkv --split parts:00:20:00-00:21:00 film.mkv`
- DTS-HD MA / DTS:X ve TrueHD Atmos: aynı yöntemle, yalnız ses: `mkvmerge -o ses.mka -D -S --split parts:00:05:00-00:06:00 film.mkv`
- VobSub: `mkvextract film.mkv tracks 3:altyazi.idx`

## Yerelde üretmek

```sh
sudo apt install ffmpeg mkvtoolnix python3-numpy python3-pil && pip install mutagen
# dovi_tool 2.3.4 ve hdr10plus_tool 1.7.2 PATH üzerinde olmalı; P7 örneği için dovi_tool deposunun test dosyaları:
export DOVI_TOOL_ASSETS=/yol/dovi_tool/assets/hevc_tests
python3 testmedia/generate.py out/testmedia --work /tmp/testmedia-work
python3 testmedia/verify.py out/testmedia
```

`mkvmerge` yoksa HEVC örnekleri ffmpeg ile birleştirilemez ve Dolby Vision yapılandırma kaydı yazılmaz; `verify.py` bunu hata olarak bildirir.
