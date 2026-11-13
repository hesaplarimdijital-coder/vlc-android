package app.nagmedar.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-305: prefixes, groups in their order, the cut per group, the recent list and its boost.
 *
 * 1.0.7: the media are found by their artist and album too, in any order of the words, and every one found is
 * listed; a kind that shows its best only says how many it has and leads to them. In 1.0.6 a track was looked
 * for in its title alone, six media were shown of however many (fifty of the media asked for with "@").
 */
class PaletteResultsTest {

    private fun item(id: String, kind: PaletteKind, label: String) = PaletteItem(id, kind, label) {}

    private val commands = (1..10).map { item("c$it", PaletteKind.Command, "Komut $it") }
    private val screens = listOf(item("s1", PaletteKind.Screen, "Komutlar ekranı"), item("s2", PaletteKind.Screen, "Ayarlar"))
    private val media = listOf(item("m1", PaletteKind.Media, "Kuzey Işığı"))
    private val all = commands + screens + media

    @Test
    fun prefixesNarrowTheSearch() {
        assertEquals("ses" to PaletteKind.Command, parsePaletteQuery("> ses", null))
        assertEquals("kuz" to PaletteKind.Media, parsePaletteQuery("@kuz", PaletteKind.Command))
        assertEquals("tema" to PaletteKind.Setting, parsePaletteQuery("#tema", null))
        assertEquals("fil" to PaletteKind.Screen, parsePaletteQuery("/fil", null))
        assertEquals("ses" to PaletteKind.Screen, parsePaletteQuery(" ses ", PaletteKind.Screen))
    }

    @Test
    fun groupsComeInTheirOrderAndAreCut() {
        val results = paletteResults("kom", null, all)
        assertEquals(listOf(PaletteKind.Command, PaletteKind.Screen), results.groups.map { it.kind })
        assertEquals(6, results.groups[0].hits.size)
        assertEquals(10, results.groups[0].total)
        assertEquals(11, results.count)
        // one kind: no cut
        assertEquals(10, paletteResults("kom", PaletteKind.Command, all).hits.size)
        assertEquals(listOf("m1"), paletteResults("@isigi", null, all).hits.map { it.item.id })
    }

    @Test
    fun withoutAQueryTheRecentLead() {
        val results = paletteResults("", null, all, recent = listOf("m1", "gone", "s2"))
        assertNull(results.groups[0].kind)
        assertEquals(listOf("m1", "s2"), results.groups[0].hits.map { it.item.id })
        // then commands, not every screen and file
        assertEquals(PaletteKind.Command, results.groups[1].kind)
        assertEquals(listOf("s1"), paletteResults("", PaletteKind.Screen, all, recent = listOf("s2")).groups[1].hits.map { it.item.id })
    }

    @Test
    fun eachItemOnceThoughListedTwice() {
        // 1.0.1: the palette keys its rows by kind and id; the library can list a file twice, the recent list an id twice
        val twice = all + item("m1", PaletteKind.Media, "Kuzey Işığı") + item("c1", PaletteKind.Command, "Komut 1")
        assertEquals(listOf("m1"), paletteResults("kuzey", null, twice).hits.map { it.item.id })
        assertEquals(10, paletteResults("kom", PaletteKind.Command, twice).hits.size)
        val recent = paletteResults("", null, twice, recent = listOf("m1", "m1", "s2"))
        assertEquals(listOf("m1", "s2"), recent.groups[0].hits.map { it.item.id })
        val ids = recent.groups.flatMap { group -> group.hits.map { "${group.kind}-${it.item.id}" } }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun whatRanLatelyComesUp() {
        val twins = listOf(item("a", PaletteKind.Command, "Sesi aç"), item("b", PaletteKind.Command, "Sesi aç"))
        assertEquals("a", paletteResults("ses", null, twins).hits.first().item.id)
        assertEquals("b", paletteResults("ses", null, twins, recent = listOf("b")).hits.first().item.id)
    }

    // ------------------------------------------------------------------ 1.0.7: the library's media

    private fun artist(name: String) = PaletteItem("artist:$name", PaletteKind.Media, name, order = 0) {}

    private fun album(name: String, artist: String) = PaletteItem("album:$name", PaletteKind.Media, name, words = artist, order = 1) {}

    private fun track(name: String, artist: String, album: String) = PaletteItem("media:$name", PaletteKind.Media, name, words = "$artist $album", order = 2) {}

    /** Two artists, three albums, five tracks, as the app's items are made: an album found by its artist, a track by its artist and album. */
    private val library = listOf(
        artist("Ada Karan"), artist("Deniz Aral"),
        album("Gece Mavisi", "Ada Karan"), album("Liman", "Ada Karan"), album("Kıyı", "Deniz Aral"),
        track("Sabaha Karşı", "Ada Karan", "Gece Mavisi"), track("Kuzeyden Esen", "Ada Karan", "Gece Mavisi"), track("Fener", "Ada Karan", "Liman"),
        track("Gece Yolcusu", "Deniz Aral", "Kıyı"), track("Şafak", "Deniz Aral", "Kıyı"),
    )

    private fun found(query: String, scope: PaletteKind? = null, recent: List<String> = emptyList()) =
        paletteResults(query, scope, commands + screens + library, recent).groups.firstOrNull { it.kind == PaletteKind.Media }?.hits.orEmpty().map { it.item.label }

    @Test
    fun anArtistsNameFindsTheArtistTheAlbumsAndTheTracks() {
        // the artist by the name; then what is found by it alone: the albums, then the tracks, the shorter title first
        assertEquals(listOf("Ada Karan", "Liman", "Gece Mavisi", "Fener", "Sabaha Karşı", "Kuzeyden Esen"), found("ada karan"))
        assertEquals(listOf("Deniz Aral", "Kıyı", "Şafak", "Gece Yolcusu"), found("ARAL"))
    }

    @Test
    fun anAlbumsNameFindsTheAlbumAndItsTracks() {
        assertEquals(listOf("Liman", "Fener"), found("liman"))
        // "gece": an album and a track by their names, then the album's tracks
        assertEquals(listOf("Gece Mavisi", "Gece Yolcusu", "Sabaha Karşı", "Kuzeyden Esen"), found("gece"))
        assertEquals(listOf("Kıyı", "Şafak", "Gece Yolcusu"), found("kiyi"))
    }

    @Test
    fun theArtistsThenTheAlbumsThenTheTracksEachBestFirst() {
        // "Sabaha Kadar" holds "ada" (in "Kadar"): a better match than the album's, found by its artist alone; the album stays above it
        val items = listOf(
            artist("Ada Karan"), album("Gece Mavisi", "Ada Karan"),
            track("Kuzeyden Esen", "Ada Karan", "Gece Mavisi"), track("Sabaha Kadar", "Ada Karan", "Gece Mavisi"), track("Adalar", "Deniz Aral", "Kıyı"),
        )
        assertEquals(listOf("Ada Karan", "Gece Mavisi", "Sabaha Kadar", "Kuzeyden Esen"), paletteResults("ada karan", null, items).hits.map { it.item.label })
        // among the tracks: the title that begins with the word, the title that holds it, then the artist's
        assertEquals(listOf("Ada Karan", "Gece Mavisi", "Adalar", "Sabaha Kadar", "Kuzeyden Esen"), paletteResults("ada", null, items).hits.map { it.item.label })
    }

    @Test
    fun theWordsOfATitleItsArtistAndItsAlbumInAnyOrder() {
        assertEquals(listOf("Kuzeyden Esen"), found("kuzeyden karan"))
        assertEquals(listOf("Kuzeyden Esen"), found("karan kuzeyden"))
        assertEquals(listOf("Kuzeyden Esen"), found("mavisi esen ada"))
        assertEquals(listOf("Şafak"), found("safak kiyi deniz"))
        // a word that is nowhere: nothing, whatever the others find
        assertEquals(emptyList<String>(), found("kuzeyden aral"))
        // the search page's syntax: a phrase as it is, a word left out
        assertEquals(listOf("Gece Yolcusu"), found("\"gece yolcusu\""))
        assertEquals(listOf("Gece Mavisi", "Sabaha Karşı", "Kuzeyden Esen"), found("gece -yolcusu"))
    }

    @Test
    fun theOtherKindsAreNotFoundByAWordOfTheMedia() {
        // a command, a screen and a setting are found by their label, letter by letter, as before
        val results = paletteResults("komut", null, commands + screens + library)
        assertEquals(listOf(PaletteKind.Command, PaletteKind.Screen), results.groups.map { it.kind })
        // and a track is not found letter by letter any more: "kzy" is in no title, artist or album
        assertEquals(emptyList<String>(), found("kzy"))
    }

    @Test
    fun everyMediaFoundIsListed() {
        val many = (1..64).map { track("Parça $it", "Sanatçı ${it % 4}", "Albüm ${it % 8}") }
        val all = paletteResults("parca", null, commands + screens + many)
        val media = all.groups.single { it.kind == PaletteKind.Media }
        assertEquals("every track found is shown, with every kind searched", 64, media.hits.size)
        assertEquals(64, media.total)
        assertEquals("media asked for: every one", 64, paletteResults("@parca", null, commands + screens + many).hits.size)
        assertEquals("media asked for, no query: every one", 64, paletteResults("", PaletteKind.Media, commands + screens + many).hits.size)
        // by the artist alone: the sixteen of them
        assertEquals(16, paletteResults("sanatci 3", null, many).hits.count { it.item.label.startsWith("Parça") && it.item.words.startsWith("Sanatçı 3") })
    }

    @Test
    fun aKindThatShowsItsBestLeadsToTheRest() {
        val results = paletteResults("kom", null, all)
        // ten commands: six shown, then the line that shows them all; one screen: no such line
        val rows = results.rows.map { row ->
            when (row) {
                is PaletteRow.Header -> "header ${row.group.kind}"
                is PaletteRow.Hit -> row.hit.item.id
                is PaletteRow.More -> "more ${row.group.kind} ${row.group.total}"
            }
        }
        assertEquals(listOf("header Command", "c1", "c2", "c3", "c4", "c5", "c6", "more Command 10", "header Screen", "s1"), rows)
        // the arrows stop at the hits and at that line, top to bottom
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 9), results.rowOfStop.toList())
        assertEquals(8, results.stops.size)
        assertTrue(results.stops[6] is PaletteRow.More)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), results.stops.map { (it as? PaletteRow.Hit)?.stop ?: (it as PaletteRow.More).stop })
        // the kind asked for: every one, and no such line
        val narrowed = paletteResults("kom", PaletteKind.Command, all)
        assertEquals(10, narrowed.hits.size)
        assertTrue(narrowed.rows.none { it is PaletteRow.More })
        // no query: the commands' best, with the line; a kind asked for: every one (fifty until 1.0.7)
        assertTrue(paletteResults("", null, all, perGroup = 3).rows.last() is PaletteRow.More)
        val sixty = (1..60).map { item("x$it", PaletteKind.Screen, "Ekran $it") }
        assertEquals(60, paletteResults("", PaletteKind.Screen, sixty).hits.size)
        assertEquals(60, paletteResults("ekran", PaletteKind.Screen, sixty).hits.size)
    }

    @Test
    fun whatRanLatelyComesFirstAmongMediaThatMatchAlike() {
        assertEquals(listOf("Liman", "Gece Mavisi"), found("karan").filter { it == "Liman" || it == "Gece Mavisi" })
        assertEquals(listOf("Gece Mavisi", "Liman"), found("karan", recent = listOf("album:Gece Mavisi")).filter { it == "Liman" || it == "Gece Mavisi" })
        // a better match stays first: the artist by the name
        assertEquals("Ada Karan", found("karan", recent = listOf("media:Fener")).first())
    }

    @Test
    fun aMediaHitMarksTheWordsInItsTitle() {
        val hits = paletteResults("kuzey ada", null, library).hits
        assertEquals(listOf("Kuzeyden Esen"), hits.map { it.item.label })
        // "kuzey" is in the title; "ada" is the artist's
        assertEquals(listOf(0, 1, 2, 3, 4), hits.single().matched.toList())
        assertEquals(emptyList<Int>(), paletteResults("aral", null, library).hits.first { it.item.label == "Şafak" }.matched.toList())
    }
}
