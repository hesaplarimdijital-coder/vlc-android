package app.nagmedar.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S-305: prefixes, groups in their order, the cut per group, the recent list and its boost. */
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
}
