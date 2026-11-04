# 1.0.1 — the user's usage video of 29 Oct (Galaxy S24, One UI 9 / Android 17, 1.0.0 release APK)

Video: scratchpad/video2/rec.mp4 (256.8 s, 1080×2340). Overview sheets: video2/sheets/ov_00..ov_17.png
(2 fps, clean decode). Zooms: video2/zoom/*.png. NOTE: the Samsung recorder uses intra refresh, so
frames extracted after a fast seek (`ffmpeg -ss T -i`) show stale macroblocks ("two sheets", "three
layers"). Those ghosts are NOT app bugs; zoom.py now decodes from the start (`-i … -ss T`).

## The user's words (16:34)
"Ayarları yarım sayfa yapmışsın. Buna gerek yok. Ayrı bir sayfa olarak açılsın. Geri tuşu ile veya
menüden geri gidilsin. Depolamaya girince uygulama çöküyor. Üst menü dokununca renk değiştiriyor.
Videoda yok ama sıradaki sayfası şu anda boş gözüküyor çünkü müzik çalmıyor ama scroll yapınca
müzikler kayıyor. İki sayfa üst üste binmiş. Artık bu kadar uğraştıktan sonra bunlar kabul edilebilir
değil. Lütfen her şeyi düzelt. Sana tüm yetkiyi veriyorum. Öyle bir sonuç çıkar ki hayran kalayım"
("üst menü" = the top bar; "menüden geri" = a back arrow in the top bar.)

## Findings

### F1 — CRASH: Daha fazla › Bu cihaz › Dahili depolama (141 s, 150 s, 160 s; three times, then One UI's
"önbellek temizlensin mi?" dialog)
The folder opens, its list is empty for ~0.5 s (Loading), then the process dies when the first row
composes. Root cause: `DeviceScreen.EntryTable` passes `key = { it.location }` to `DenseTable`, which
hands it to `LazyColumn.itemsIndexed(key = …)`. `DeviceLocation.Path/Document` are plain data
classes; a lazy item key must be storable in a Bundle → `IllegalArgumentException: Type of the key
Path(path=/storage/emulated/0/…) is not supported. On Android you can only use types which can be
stored inside the Bundle.` Every device folder listing crashes (internal storage, SD/USB, picked
folders). The emulator walk never opened a drive.

### F2 — the top bar changes colour (4–5 s, 113–134 s, 173 s, 242 s…)
Phone `PageTopBar(glass = true, scrolled = …)` turns into Haze glass once `ShellScrollState.scrolled`;
dark artwork passing beneath makes it grey, and `scrolled` flips on/off while scrolling (onPostScroll
`availableY > 0` resets it), so it flickers frame to frame (4.10 / 4.70 / 5.20 s grey, others white).
The user wants no colour change at all.

### F3 — "⋯ Daha fazla" is a glass bottom sheet, the user wants pages (125 s, 132 s, 157 s, 162 s, 165–171 s, 240–255 s)
The sheet slides up, is translucent (blurred artwork blobs show through), the pages it opens appear
as the sheet slides down over them; destinations have no back arrow; Back from Settings goes to
Müzik (navigate() pops to the start destination); Ekolayzer highlights the **Müzik** tab (242 s, 253 s);
the sleep timer dialog opens while the sheet is still going down (251 s).

### F4 — "Sıradaki" overlaps the page and lets touches through (254 s; the user's report)
On a phone the queue is an AnimatedVisibility in the screen overlay *between* the top bar and the
bottom bar: the page's title ("Ekolayzer") stays above "Sıradaki [Temizle] ×", the bottom nav still
shows (with the wrong tab), and the empty state has no pointer input, so a drag on it scrolls the
list underneath ("scroll yapınca müzikler kayıyor"). "Temizle" looks enabled on an empty queue. The
sheet fades in translucent for a frame (253.9 s).

### F5 — translucent menus and popups inside dialogs (83.7–84.3 s)
The speed dropdown inside "Podcast ayarları" (a Dialog window) is glass whose blur source is the main
window: it shows blurred blobs of the podcast page instead of a solid surface. Menus.kt's `glassy`
does not check the window (Sheets.kt does). Dialog enter/exit fades show the busy page through the
card (81.2 s).

### F6 — undo bar on a phone shows a "Ctrl+Z" keycap (94.7 s, 110 s); durations are fine (8 s each).

### F7 — Settings on a phone
- "Çıkış" chip row overflows the width: "Otomatik · AAudio · Bit-perfect · U…" (180–186 s).
- Touch-only phone shows desktop-only sections: Giriş (Arayüz yoğunluğu, İpucu gecikmesi = hover
  tooltip delay), Klavye kısayolları with the whole shortcut table (175–180 s).
- Laboratuvar (S-502 Klasik/Media3, SP-01/02/05 test buttons) and Performans are developer tools in
  the middle of the user's settings (236–238 s).

### F8 — small polish
- Podcasts › search: the "Ara" button is filled on an empty field and fades to almost invisible once
  text is typed (96.5–100 s) — inverted enabled look.
- Akışlar: "Enter adresi oynatır…" keyboard hint on a touch phone (166.5 s).
- Full player: "FLAC 16/44.1 · AAudi…" cut short (12–15 s).
- Latency result: "5 test sesinden 5 tanesi · yayılım" / "1 ms" wraps awkwardly (206 s).
- Podcasts tab: a row holding only "+" and "⋮" under the top bar (95 s) — wasted height.

### Not bugs
- The vertical pill over the artwork at 12.8–15 s is One UI's volume panel (volume key).
- The "double sheet" / "three layers" in early zooms were decoding artefacts (see the note above).
- Undo bars last 8 s each (two separate bars at 94.7 s and 110 s).

## Work split (1.0.1)
- story/nav-pages (A): F2, F3, F5 surfaces, Settings as a separate page, back arrows, tab highlight.
- story/crash-sweep (B): F1 + every lazy key / rememberSaveable / duplicate key in the app, the
  emulator walk extended with the user's flows.
- story/polish-1.0.1 (C): F4, F6, F7, F8, overlays that let touches through, dialog transitions.
- story/fix-1.0.1 (running before): podcast subscribe by address, column title long press, "Durati…".
