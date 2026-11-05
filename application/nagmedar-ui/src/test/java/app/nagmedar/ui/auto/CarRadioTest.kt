package app.nagmedar.ui.auto

import app.nagmedar.ui.radio.DirectoryCountry
import app.nagmedar.ui.radio.DirectoryLanguage
import app.nagmedar.ui.radio.StationOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.videolan.vlc.media.CarItem
import org.videolan.vlc.media.CarStyle
import org.videolan.vlc.media.MediaSessionBrowser
import java.util.Locale

/**
 * 1.0.3: Android Auto's Radio tab: the favourites and the stations listened to last, the whole world's radio directory by
 * country (the device's first) and by language, its most listened and most liked stations, a page at a time with a row
 * to the next; "Favorilere ekle" and "Favorilerden çıkar" on every station; the lines that say why a list is empty.
 */
class CarRadioTest {

    private fun tree(data: FakeCarData, page: Int = CarTree.PAGE, look: CarLook = FakeCarLook) = CarTree(data, look, page)

    private fun children(data: FakeCarData, id: String, page: Int = CarTree.PAGE, look: CarLook = FakeCarLook): List<CarItem> =
        runBlocking { tree(data, page, look).children(id) } ?: error("no children of $id")

    private fun ids(items: List<CarItem>) = items.map { it.id }

    private fun titles(items: List<CarItem>) = items.map { it.title }

    /** The app's look in Turkish: the order of names, the language listed first. */
    private val turkish = object : CarLook by FakeCarLook {
        override val locale: Locale = Locale.forLanguageTag("tr")
    }

    private val trt = station(0, "TRT FM", uuid = "u-trt", countryCode = "TR", language = "turkish", tags = listOf("pop", "news"), clicks = 900, votes = 40)
    private val eksen = station(0, "Radyo Eksen", uuid = "u-eksen", countryCode = "TR", language = "turkish", tags = listOf("rock"), clicks = 700, votes = 300)
    private val acik = station(0, "Açık Radyo", uuid = "u-acik", countryCode = "TR", language = "turkish", tags = listOf("jazz"), clicks = 100, votes = 120)
    private val swr = station(0, "SWR3", uuid = "u-swr", countryCode = "DE", language = "german", tags = listOf("pop"), clicks = 800, votes = 90)
    private val directory = listOf(acik, swr, trt, eksen)

    @Test
    fun theRadioTabListsTheWholeWorldsDirectory() {
        val data = FakeCarData()
        val tab = children(data, CarIds.RADIO)
        assertEquals(
            listOf(CarIds.FAVOURITES, CarIds.STATIONS_RECENT, CarIds.RADIO_COUNTRIES, CarIds.LANGUAGES, CarIds.TAGS, CarIds.MOST_LISTENED, CarIds.MOST_LIKED),
            ids(tab),
        )
        assertEquals(
            listOf("FavouriteStations", "RecentStations", "Countries", "Languages", "Categories", "MostListened", "MostLiked"),
            titles(tab),
        )
        assertEquals(
            listOf("icon:Favourites", "icon:Recent", "icon:Countries", "icon:Languages", "icon:Categories", "icon:Listened", "icon:Liked"),
            tab.map { it.iconUri },
        )
        // the stations kept are tiles; the directory's lists are rows
        assertEquals(listOf(CarStyle.Grid, CarStyle.Grid) + List(5) { CarStyle.List }, tab.map { it.children })
        assertTrue(tab.all { it.browsable && !it.playable })
        // opening the tab asks the directory nothing
        assertEquals(0, data.directoryAsked)
    }

    @Test
    fun theFavouritesAndTheStationsListenedToSayWhenThereAreNone() {
        val data = FakeCarData(offline = true)
        assertEquals(listOf(CarIds.nothing("NoFavourites")), ids(children(data, CarIds.FAVOURITES)))
        assertEquals(listOf(CarIds.nothing("NoRecentStations")), ids(children(data, CarIds.STATIONS_RECENT)))
        assertTrue(MediaSessionBrowser.isNoMedia(CarIds.nothing("NoFavourites")))
        // they need no network
        assertEquals(0, data.directoryAsked)
    }

    @Test
    fun theCountriesStartWithTheDevicesThenByNameWithTheirStationCounts() {
        val data = FakeCarData(
            homeCountry = "tr",
            radioCountries = listOf(
                DirectoryCountry("Germany", "DE", 3_000),
                DirectoryCountry("Turkey", "TR", 1_200),
                DirectoryCountry("Austria", "AT", 400),
                // no code: it cannot be asked for
                DirectoryCountry("Kosovo", "", 30),
            ),
        )
        val countries = children(data, CarIds.RADIO_COUNTRIES)
        assertEquals(listOf("country:TR", "country:AT", "country:DE"), titles(countries))
        assertEquals(listOf(CarIds.radioCountry("TR"), CarIds.radioCountry("AT"), CarIds.radioCountry("DE")), ids(countries))
        assertEquals(listOf("1200 Stations", "400 Stations", "3000 Stations"), countries.map { it.subtitle })
        assertTrue(countries.all { it.browsable && !it.playable && it.children == CarStyle.List && it.iconUri == "icon:Countries" })
        // a device in a country the directory does not have: all by name
        data.homeCountry = "JP"
        assertEquals(listOf("country:AT", "country:DE", "country:TR"), titles(children(data, CarIds.RADIO_COUNTRIES)))
        data.homeCountry = null
        assertEquals(listOf("country:AT", "country:DE", "country:TR"), titles(children(data, CarIds.RADIO_COUNTRIES)))
    }

    @Test
    fun aCountryOpensOnItsStationsTheMostListenedFirst() {
        val data = FakeCarData(directory = directory)
        val stations = children(data, CarIds.radioCountry("TR"))
        assertEquals(listOf("TRT FM", "Radyo Eksen", "Açık Radyo"), titles(stations))
        val query = data.listQueries.single()
        assertEquals("TR", query.filters.countryCode)
        assertEquals(StationOrder.Popularity, query.order)
        assertEquals(0, query.offset)
        assertTrue(stations.all { it.playable && !it.browsable })
        assertEquals("country:TR · pop", stations[0].subtitle)
        assertEquals("web:https://radio.example.org/logo/0.png", stations[0].iconUri)
        // a code asked for in lower case is the same country
        assertEquals(titles(stations), titles(children(data, CarIds.radioCountry("tr"))))
    }

    @Test
    fun aLongListIsAPageAtATimeWithARowToTheNext() {
        val many = (1..7).map { n -> station(0, "Radyo $n", uuid = "u-$n", countryCode = "TR", clicks = 100 - n) }
        val data = FakeCarData(directory = many)
        val first = children(data, CarIds.radioCountry("TR"), page = 3)
        assertEquals(listOf("Radyo 1", "Radyo 2", "Radyo 3", "MoreStations"), titles(first))
        val more = first.last()
        assertEquals(CarIds.page(CarIds.radioCountry("TR"), 1), more.id)
        assertTrue(more.browsable && !more.playable && more.children == CarStyle.List)
        assertEquals("icon:More", more.iconUri)
        // one more than a page is asked for: whether there is a next one
        assertEquals(listOf(0 to 4), data.listQueries.map { it.offset to it.limit })
        val second = children(data, more.id, page = 3)
        assertEquals(listOf("Radyo 4", "Radyo 5", "Radyo 6", "MoreStations"), titles(second))
        assertEquals(CarIds.page(CarIds.radioCountry("TR"), 2), second.last().id)
        // the last page has no row to a next one
        assertEquals(listOf("Radyo 7"), titles(children(data, second.last().id, page = 3)))
        assertEquals(listOf(0, 3, 6), data.listQueries.map { it.offset })
        // a page the directory no longer fills says so
        assertEquals(listOf(CarIds.nothing("NoStations")), ids(children(data, CarIds.page(CarIds.radioCountry("TR"), 5), page = 3)))
    }

    @Test
    fun theLanguagesAreNamedInTheAppsLanguageItsOwnFirst() {
        val data = FakeCarData(
            languages = listOf(
                DirectoryLanguage("english", "en", 5_000),
                DirectoryLanguage("german", "de", 2_000),
                DirectoryLanguage("turkish", "tr", 1_200),
                // the directory spells some twice: the most used spelling is listed
                DirectoryLanguage("English", null, 12),
                DirectoryLanguage("klingon", null, 3),
            ),
        )
        val languages = children(data, CarIds.LANGUAGES, look = turkish)
        assertEquals(listOf("Turkish", "English", "German", "Klingon"), titles(languages))
        assertEquals(listOf(CarIds.language("turkish"), CarIds.language("english"), CarIds.language("german"), CarIds.language("klingon")), ids(languages))
        assertEquals("1200 Stations", languages[0].subtitle)
        assertTrue(languages.all { it.browsable && it.children == CarStyle.List && it.iconUri == "icon:Languages" })
        // in a language of no code the app knows: by name alone
        assertEquals(listOf("English", "German", "Klingon", "Turkish"), titles(children(data, CarIds.LANGUAGES)))
    }

    @Test
    fun aLanguageOpensOnItsStationsAsTheDirectorySpellsIt() {
        val data = FakeCarData(directory = directory)
        assertEquals(listOf("TRT FM", "Radyo Eksen", "Açık Radyo"), titles(children(data, CarIds.language("turkish"))))
        assertEquals("turkish", data.listQueries.single().filters.language)
        assertEquals(listOf("SWR3"), titles(children(data, CarIds.language("german"))))
    }

    @Test
    fun theMostListenedAndTheMostLikedAreTheWholeDirectorys() {
        val data = FakeCarData(directory = directory)
        assertEquals(listOf("TRT FM", "SWR3", "Radyo Eksen", "Açık Radyo"), titles(children(data, CarIds.MOST_LISTENED)))
        assertEquals(listOf("Radyo Eksen", "Açık Radyo", "SWR3", "TRT FM"), titles(children(data, CarIds.MOST_LIKED)))
        assertEquals(listOf(StationOrder.Popularity, StationOrder.Votes), data.listQueries.map { it.order })
        // no country, language or tag narrows them
        assertTrue(data.listQueries.all { it.filters.count == 0 && it.text.isEmpty() })
    }

    @Test
    fun aCategoryOpensOnItsStationsTheMostListenedFirst() {
        val data = FakeCarData(directory = directory)
        assertEquals(listOf("TRT FM", "SWR3"), titles(children(data, CarIds.tag("pop"))))
        assertEquals("pop", data.listQueries.single().filters.tag)
        assertEquals(StationOrder.Popularity, data.listQueries.single().order)
    }

    @Test
    fun theDirectorysListsSayWhyTheyAreEmpty() {
        val lists = listOf(CarIds.RADIO_COUNTRIES, CarIds.LANGUAGES, CarIds.TAGS, CarIds.MOST_LISTENED, CarIds.MOST_LIKED, CarIds.radioCountry("TR"), CarIds.language("turkish"), CarIds.tag("jazz"))
        val offline = FakeCarData(offline = true)
        lists.forEach { id -> assertEquals(id, listOf(CarIds.nothing("RadioOffline")), ids(children(offline, id))) }
        val busy = FakeCarData(busy = true)
        lists.forEach { id -> assertEquals(id, listOf(CarIds.nothing("RadioBusy")), ids(children(busy, id))) }
        // a list the directory has nothing in
        val empty = FakeCarData(directory = directory)
        assertEquals(listOf(CarIds.nothing("NoStations")), ids(children(empty, CarIds.radioCountry("JP"))))
        assertEquals(listOf(CarIds.nothing("NoStations")), ids(children(empty, CarIds.tag("klezmer"))))
        assertEquals(listOf(CarIds.nothing("Empty")), ids(children(empty, CarIds.RADIO_COUNTRIES)))
        // an id with no key is nothing
        assertEquals(emptyList<CarItem>(), children(empty, CarIds.radioCountry("")))
        assertEquals(emptyList<CarItem>(), children(empty, CarIds.language(" ")))
    }

    @Test
    fun aStationOfTheDirectoryPlaysAsTheDirectoryGaveIt() {
        val data = FakeCarData(directory = directory)
        val entry = children(data, CarIds.radioCountry("TR")).first()
        val play = runBlocking { tree(data).play(entry.id) } as CarPlay.Radio
        // all of it, so that the radio keeps its country and tags; the player reports the click (by its UUID)
        assertEquals(trt, play.station)
        assertEquals("u-trt", play.station.uuid)
        // one the app no longer knows plays from what its id carries
        val forgotten = FakeCarData()
        val alone = runBlocking { tree(forgotten).play(entry.id) } as CarPlay.Radio
        assertEquals("u-trt", alone.station.uuid)
        assertEquals("TRT FM", alone.station.name)
        assertEquals(trt.streamUrl, alone.station.streamUrl)
        assertEquals(trt.logoUrl, alone.station.logoUrl)
        assertNull(alone.station.countryCode)
    }

    @Test
    fun everyStationOffersToBeAFavouriteOrNoLonger() {
        val kept = eksen.copy(id = 7, favourite = true)
        val data = FakeCarData(directory = directory, favourites = listOf(kept))
        val stations = children(data, CarIds.radioCountry("TR"))
        // the favourite (kept) offers to be one no longer; the others to be one
        assertEquals(
            listOf(listOf(CarActions.FAVOURITE), listOf(CarActions.UNFAVOURITE), listOf(CarActions.FAVOURITE)),
            stations.map { it.actions },
        )
        assertEquals(listOf(CarActions.UNFAVOURITE), children(data, CarIds.FAVOURITES).single().actions)
        // a kept station's entry is the same in every list
        assertEquals(stations[1].id, children(data, CarIds.FAVOURITES).single().id)
    }

    @Test
    fun favouritingAStationKeepsItAsTheDirectoryGaveIt() {
        val data = FakeCarData(directory = directory)
        val tree = tree(data)
        val entry = children(data, CarIds.radioCountry("TR"))[2]
        val made = runBlocking { tree.favourite(entry.id, on = true) }!!
        assertTrue(made.favourite)
        // the directory's own data, not just what the entry's id carries
        assertEquals(acik, data.favouritesMade.single().first)
        assertTrue(data.favouritesMade.single().second)
        assertEquals(listOf("Açık Radyo"), titles(children(data, CarIds.FAVOURITES)))
        // read again, the entry now offers to be one no longer, under the same id
        val again = runBlocking { tree.item(entry.id) }!!
        assertEquals(entry.id, again.id)
        assertEquals(listOf(CarActions.UNFAVOURITE), again.actions)
        // and back
        val unmade = runBlocking { tree.favourite(entry.id, on = false) }!!
        assertFalse(unmade.favourite)
        assertEquals(listOf(CarIds.nothing("NoFavourites")), ids(children(data, CarIds.FAVOURITES)))
        assertEquals(listOf(CarActions.FAVOURITE), runBlocking { tree.item(entry.id) }!!.actions)
    }

    @Test
    fun theUsersOwnStationIsFavouritedByItsRow() {
        val own = station(12, "Mahalle Radyosu", uuid = null, tags = emptyList())
        val data = FakeCarData(recentStations = listOf(own))
        val entry = children(data, CarIds.STATIONS_RECENT).single()
        assertEquals(CarIds.station(12), entry.id)
        assertEquals(listOf(CarActions.FAVOURITE), entry.actions)
        val made = runBlocking { tree(data).favourite(entry.id, on = true) }!!
        assertEquals(12L, made.id)
        assertEquals(listOf(CarActions.UNFAVOURITE), runBlocking { tree(data).item(entry.id) }!!.actions)
    }

    @Test
    fun onlyAStationIsMadeAFavourite() {
        val data = FakeCarData(directory = directory, feeds = listOf(feed(1, "Gece")))
        val tree = tree(data)
        for (id in listOf(CarIds.RADIO, CarIds.FAVOURITES, CarIds.radioCountry("TR"), CarIds.show(1), CarIds.ALBUMS, "//other.app/root")) {
            assertNull(id, runBlocking { tree.favourite(id, on = true) })
        }
        // a station kept by a row no longer there
        assertNull(runBlocking { tree.favourite(CarIds.station(99), on = true) })
        assertTrue(data.favouritesMade.isEmpty())
    }

    @Test
    fun theRadiosIdsComeBack() {
        val country = CarIds.parse(CarIds.page(CarIds.radioCountry("TR"), 2))!!
        assertEquals(CarKind.Country, country.kind)
        assertEquals("TR", country.key)
        assertEquals(2, country.page)
        val language = CarIds.parse(CarIds.language("serbo-croatian & bosnian"))!!
        assertEquals(CarKind.Language, language.kind)
        assertEquals("serbo-croatian & bosnian", language.key)
        // the lists themselves are no keyed entries
        for (id in listOf(CarIds.RADIO_COUNTRIES, CarIds.LANGUAGES, CarIds.MOST_LISTENED, CarIds.MOST_LIKED)) {
            assertNull(id, CarIds.parse(id)!!.kind)
        }
        assertEquals(2, CarIds.parse(CarIds.page(CarIds.MOST_LISTENED, 2))!!.page)
    }

    @Test
    fun theSearchStaysGroupedMusicPodcastsNewPodcastsRadio() {
        val data = FakeCarData(
            tracks = listOf(track(1, "Gece Yolu", artist = "Gece", album = "Gece")),
            feeds = listOf(feed(1, "Gece Notları")),
            episodes = listOf(episode(5, 1, "Gece yarısı")),
            shows = listOf(hit(2, "Gece Kuşu")),
            favourites = listOf(station(3, "Gece FM", favourite = true)),
            directory = listOf(station(0, "Gece Radyosu", uuid = "u-gr")),
        )
        val found = runBlocking { tree(data).search("gece") }
        assertEquals(
            listOf("FoundTracks", "FoundAlbums", "FoundArtists", "FoundEpisodes", "FoundShows", "FoundNewShows", "FoundStations", "FoundStations"),
            found.map { it.group },
        )
        assertEquals(listOf("Gece Yolu", "Gece", "Gece", "Gece yarısı", "Gece Notları", "Gece Kuşu", "Gece FM", "Gece Radyosu"), titles(found))
        // the stations found offer what the lists do
        assertEquals(listOf(listOf(CarActions.UNFAVOURITE), listOf(CarActions.FAVOURITE)), found.takeLast(2).map { it.actions })
    }
}
