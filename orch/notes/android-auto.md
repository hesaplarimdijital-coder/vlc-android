
## android-auto (PR #80, story/android-auto) — notes for the next story of this track
- Auto browses `PlaybackService` (MediaBrowserServiceCompat + `MediaSessionBrowser`); the Media3 session (`nagmedar-session`) is a plain MediaSession, not a browser service: one root. `onGetRoot` no longer needs the media permission.
- Tree = `app.nagmedar.ui.auto`: `CarIds` (ids under `MediaSessionBrowser.ID_ROOT`; `?p=` page, `?i=` start index, directory stations carry `n/u/l`), `CarTree` (pure: children/search/play → `CarPlay`), `CarVoice` (folding over `foldForSearch` + Turkish apostrophe endings, scores EXACT/PREFIX/WORDS/PART, `parse` of "… çal", "… podcast'ini çal", "… radyosunu aç"), `CarVoiceSearch` (music by focus → podcasts → radio), `AppCarCatalog`/`AppCarData`/`AppCarLook` (Android), `CarCatalogObserver` (PlaybackObserver by name + keep rule) sets `CarHook.catalog`.
- vlc-android side: `media/CarCatalog.kt` (CarItem, CarStyle, CarCatalog, CarHook, VoiceRequest, VoiceFocus), `MediaSessionBrowser.toMediaItem`, `ROOT_TABS`; VLC's legacy nodes and VoiceSearchParams deleted. `onPlayFromMediaId` keeps `ID_MEDIA/<id>`; `ID_NO_MEDIA` (empty-list line) plays nothing.
- Session buttons: `app.nagmedar.playback.SessionButtons` (tools) is the one rule; episode = `PlaybackService.isEpisodePlaying` (medialibrary podcast or `EpisodeLookup` → `ResumeHook.owner`, cached per location, main thread). Actions `CUSTOM_ACTION_EPISODE_BACK/FORWARD`, icons `nagmedar_episode_back_15/forward_30` and Auto list icons `nagmedar_auto_*` come from `buildsystem/branding/gen_icons.py` (video shortcut dropped there).
- Data: new read-only PodcastDao queries `inProgress`, `newEpisodeList`, `episodeTitles` (PodcastEpisodeTitle), `episodesById` (no schema change). "Devam eden" order = medialibrary `HISTORY_TYPE_GLOBAL` position of the episode's address, then newest. `EpisodeMedia.of` is the one episode→media path (app + Auto). `RadioCenter.directory/library(context)` accessors; Auto sets `RadioCenter.active`.
- Music snapshot kept 10 min or until the medialibrary changes (`libraryChanged`, Auto told at most every 10 s). Smart lists are not in Auto (medialibrary playlists only).

## auto-fixes (PR #82, story/auto-fixes) — the review's 10 findings fixed
- Web artwork: `ArtworkProvider` REMOTE http(s) → `CoverHook.webFile(url)` (fun interface `WebImages`, set by `CoverObserver.attach` to `PodcastCovers.webImages`: OkHttp + Nağmedar UA + disk `podcast-covers` store, in-flight dedupe). `AppCarLook.webImage` always passes the URL (it is Auto's Glide key). HttpImageLoader is no longer on the Auto path.
- ML timeouts: `MusicUnavailable` (CarTree.kt) thrown by `CarData.music/recentTracks/playlists/playlistTracks` after `AppCarData.ML_WAIT_MS` (5 s); CarTree turns it into `nothing(CarText.MusicUnavailable)`, search adds that line and goes on, CarVoiceSearch skips music. The music read runs in the catalog's own scope (`AppCarCatalog.release()` on detach); a read slower than the wait calls `musicLate` → Auto is told.
- Snapshot: `generation` (AtomicInteger) + `reading` Deferred; `forgetMusic` bumps it and drops `items`. Trailing notify via `AppCarCatalog(context, tell)`; told ids = Music tab lists + the last 64 music/recent/search ids Auto opened (`asked`).
- Empty lines: id `CarIds.nothing(reason)` = `ID_NO_MEDIA?t=<CarText>`; `MediaSessionBrowser.isNoMedia`; `CarCatalog.nothingText(context, id?)`; `PlaybackService.displayPlaybackError(String)`. Voice no-result with words → error "Sonuç yok".
- `CarCatalog.playingId(location)` (main thread): Auto's last play map (location → tree id) → `PodcastCenter.playing` → `RadioSession.playing`.
- `CarVoice.spoken(raw)`: parse's kept words as said (diacritics, Turkish apostrophe ending dropped) for the directory; voice radio = best word-for-word else directory's first. FakeCarData.findStations = case-insensitive substring like radio-browser, records `stationQueries`.
- `SessionButtons`: episode && carMode → back 15, forward 30, Speed. Media3 `handleSetMediaItems`: no id/uri/query → `playFromSearch("")` (Media3 1.4.1 legacy stub forwards null/empty queries; it does not drop them).
- `MediaRepository.tracksOfFiles` / `recentAudio(musicFiles)`: Son çalınanlar uses the snapshot's file ids. ArtworkProvider VIDEO branch kept (remote-access-server maps "video" to it).

## auto-browse, Music piece (agent 4, 5 Nov 08:42) — 4ec2e2c on story/auto-browse (PR #97)
- Music tab: "Tüm müziği karıştırarak çal" (playable, first) · Albümler · Sanatçılar · Türler (only when some track has a genre; "Türü yok" last) · Çalma listeleri (playlists, then smart lists: `CarIds.smart(id)`, subtitle "Akıllı liste") · Yıllar · Klasörler · Tüm parçalar (A–Z by `MusicLibrary.tracksByTitle()`, pages named by first letters, `CarTree.initial`). Each list plays from the chosen track and queues the rest; smart list = `PlaySource(Playlist, name)`.
- `AppCarData.smartTracks`: `SmartList.select` over the music snapshot + `TrackTechIndex.read` (DB rows, never waits for the probe's lock) + `TrackStatsIndex.read`, cached per snapshot so tapped indexes match. `CarIds.parse` accepts an empty key (genre ""); the empty artist/folder groups now open too. Auto is told of MUSIC/GENRES/TRACKS on library changes. 4 icons in gen_icons.py AUTO_ICONS.
- CI green (267 screenshots same). Personal walk of a56b9ab: Auto check ok (step 121); the run failed only on "Pixel Launcher isn't responding" (system launcher ANR, tablet-en step 1), not the app. Personal walk of 4ec2e2c dispatched: run 41975679764.
- Device checks: - [ ] Auto › Müzik'in ilk satırı "Tüm müziği karıştırarak çal" tüm müziği karışık çalıyor  - [ ] Müzik › Türler › bir tür, seçilen parçadan çalıyor  - [ ] Çalma listeleri'nde akıllı listeler ("Akıllı liste") açılıp çalıyor  - [ ] Tüm parçalar A–Z, harfli sayfalarla açılıyor
- Next: podcast piece (history order, Keşfet, Abone ol), then radio (Ülkeler, Diller, top lists, Favorilere ekle).

## auto-podcasts, Podcast piece (agent 5, 5 Nov 12:56) — story/auto-podcasts, PR #100 (draft), 7f53e33, CI green
- Podcast tab = Devam eden bölümler · Programlarım (string renamed, id SHOWS unchanged) · Yeni bölümler · Keşfet (CarIds.DISCOVER).
  Keşfet = Popüler programlar (CarIds.chart(country)) · Kategoriler (CATEGORIES → category(country, genreId)) · Ülkeler
  (COUNTRIES: ApplePodcasts.COUNTRIES, 55 storefronts, device's first, then Collator of the UI locale).
- Directory: Apple RSS builder `rss.marketingtools.apple.com/api/v2/<cc>/podcasts/top/100/podcasts.json` (no key, no feedUrl,
  `?genre=` IGNORED, 200 = server error) + one `itunes.apple.com/lookup?id=a,b&entity=podcast&country=CC` for feeds.
  `ITunesDirectory.top()`, `AppleChartJson`, `ITunesJson.lookup`, `ApplePodcasts.shows` (PodcastCharts.kt). One iTunes client:
  `PodcastCenter.itunes()` (search + charts share RequestPace 18/min; Apple says ~20/min). Charts kept 6 h, feeds 30 min
  (AppCarData). Categories = the chart's shows grouped by Apple's 19 top-level genre ids (strings nag_auto_genre_*).
  Device country = TelephonyManager network/SIM ISO → UI locale region → default; "us" if not a storefront.
- Show not followed: `CarIds.directoryShow(feedUrl, title, author, image)` (browsable, CarItem.actions = [CarActions.SUBSCRIBE]);
  opens on row `CarIds.subscribe(...)` ("Abone ol", playable → CarPlay.Subscribe) + feed episodes `CarIds.directoryEpisode(url,
  guid)` (play via EpisodeMedia from start; a show followed meanwhile plays its kept episode). Followed: tile says "Abone",
  opens as Programlarım. Unreadable feed → line ShowUnavailable; directory down → PodcastsOffline.
- Subscribing (row or browse action) = `PodcastRepository.subscribe(url, hit)`; message Added/Already/Failed (nag_auto_subscribe_failed
  + FeedFailure.describe); tells Auto SHOWS, NEW and the show's page; PodcastCenter.schedule. Row: `PlaybackService.displayCarMessage`
  (non-fatal error on the current state when media is loaded, else STATE_ERROR).
- Browse actions (androidx.media 1.7, mediaVersion 1.7.0): CarCatalog.browseActions/item/act, CarAction/CarActionResult,
  CarItem.actions → DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST; PlaybackService.onGetRoot puts the ROOT_LIST only when
  rootHints' BROWSER_ROOT_HINTS_KEY_CUSTOM_BROWSER_ACTION_LIMIT > 0; onCustomAction (IO; RESULT_MESSAGE + RESULT_REFRESH_ITEM);
  onLoadItem (MediaSessionBrowser.item). Icons nagmedar_auto_{discover,popular,countries,subscribe} (gen_icons.py).
- Search: "Yeni podcastler" (FoundNewShows) = PodcastCenter.search() hits not followed, ≤10; podcast and radio directories
  asked in parallel (4 s each).
- Devam eden order: `ListenedEpisodes` (prefs nagmedar_podcast_listened, ≤200 ids) noted by PodcastPlaybackObserver when an
  episode starts (notifyChildrenChanged(CONTINUE) only when the order changed); fallback = ML HISTORY_TYPE_GLOBAL (VLC adds a
  long audio only on first play via savePlaycount's !isPodcast), then newest.
- Tests: CarPodcastTest (14), PodcastChartsTest (Robolectric, 7), CarTreeTest updated; AutoBrowseTest (walk) connects with the
  action-limit hint, asserts the root's "Abone ol" and Keşfet's 3 lists. Personal walk API 35 run 42006532076 (7f53e33):
  AutoBrowseTest OK (1 test, 23.8 s) on the R8 build; the walk itself was still running at 13:42 (0 crashes).
- Device checks: - [ ] Podcast › Keşfet › Popüler programlar Türkiye'nin listesini gösteriyor  - [ ] Keşfet › Kategoriler ›
  bir kategori programlarını açıyor  - [ ] Keşfet › Ülkeler: Türkiye başta, sonra adlarına göre  - [ ] Abone olunmamış bir
  program "Abone ol" satırı ve bölümleriyle açılıyor, bir bölüm çalıyor  - [ ] "Abone ol" (satır ya da kutucuktaki simge)
  "… eklendi" diyor, program Programlarım'da çıkıyor  - [ ] Arama "Yeni podcastler" başlığı altında abone olunmamış programları
  gösteriyor  - [ ] Devam eden bölümler en son dinlenen başta
- Next: radio piece (Ülkeler, Diller, top lists, Favorilere ekle as a browse action: reuse CarCatalog.act/CarActions).

## auto-radio, Radio piece (agent 6, 5 Nov 17:55) — story/auto-radio, PR #102 (draft), HEAD 8a928f22, CI green on it (master 6d8c86e merged)
- Radyo tab = Favoriler (Grid) · Son dinlenenler (Grid, string nag_auto_recent_stations) · Ülkeler (CarIds.RADIO_COUNTRIES) ·
  Diller (LANGUAGES) · Kategoriler (TAGS, now rows) · En çok dinlenenler (MOST_LISTENED, clickcount) · En çok beğenilenler
  (MOST_LIKED, votes). Country → CarIds.radioCountry(ISO) (CarKind.Country), language → CarIds.language(directory name).
- Ülkeler: RadioDirectory.countries() (merged by ISO code), device's first (AppCarData.homeCountry: network → SIM → language
  region → default), then Collator of the UI locale; count subtitle (plural nag_auto_stations, TR "radyo"). Diller: the app's
  language first (RadioLabels.languageCode), ≤100, one per localized name.
- Station lists = CarTree.listed(): StationQuery(order Popularity|Votes, filters), page of 100 asked as limit 101 →
  last row "Daha fazla radyo" = CarIds.page(list, p+1). AppCarData.stations(query) keeps pages 30 min (≤40), countries/
  languages/tags 6 h, every station seen by UUID (≤5000) for knownStation(); marks (favourite, row) via RadioLibrary.marks.
- Empty/offline/busy lines: NoFavourites, NoRecentStations, NoStations (nag_radio_*_title reused), RadioOffline, RadioBusy.
- Favourites: CarActions.FAVOURITE / UNFAVOURITE (both in the root list; a station carries one of them by Station.favourite).
  AppCarCatalog.act → CarTree.favourite(id, on) → CarData.favourite → RadioLibrary.favourite (the Radio page's path, keeps the
  directory's data); message nag_auto_favourite_added/removed, tells FAVOURITES, RESULT_REFRESH_ITEM (CarTree.item reads a
  station under the same id). DB failure → nag_radio_failed message.
- Station ids: directory stations are ALWAYS CarIds.directoryStation(uuid,n,u,l) now (CarTree.stationId), so an entry survives
  pruneUnused(); imported ones stay CarIds.station(row). Old $RADIO/station/<id> ids still play. Playing a directory station:
  CarPlay.Radio(knownStation ?: what the id carries) → RadioPlayer.start (click counted as before). STATIONS_RECENT told on play.
- Rows in a country's list omit the country; in a category the category (subtitle = other tag).
- Tests: CarRadioTest (17: tab, countries order, paging, languages, top lists, empty/offline/busy, favourites both ways, ids,
  search groups), CarTreeTest updated; harness $SCRATCH/agents/auto-radio/harness (85 OK). AutoBrowseTest: root has the 3
  actions, Radio's 7 lists + first country answer, stations offer a favourite action. Personal walk run 42037664381 (3b05165,
  API 35): phone + tablet + summary GREEN, Auto check step 87 OK (43 s). CI 3b05165 all green (267 screenshots same).
- Device checks: - [ ] Auto › Radyo'da yedi liste: Favoriler, Son dinlenenler, Ülkeler, Diller, Kategoriler, En çok dinlenenler,
  En çok beğenilenler  - [ ] Ülkeler'de Türkiye başta, istasyon sayılarıyla; Türkiye'den bir radyo çalıyor  - [ ] Bir ülkenin
  listesi en çok dinlenenle başlıyor, sonunda "Daha fazla radyo" sonraki sayfayı açıyor  - [ ] Bir radyodaki yıldız simgesi
  "… favorilere eklendi" diyor, radyo Favoriler'de çıkıyor; yeniden basınca çıkarıyor  - [ ] Diller'de Türkçe başta
  - [ ] Arama gruplu: müzik, programlar, yeni podcastler, radyolar; "Hey Google, Radyo Eksen'i aç" çalıyor
- Left of Auto: nothing of the prompt's §2; TV's TVSearchProvider still inlines APP_ID (TV only, noted in #97).
