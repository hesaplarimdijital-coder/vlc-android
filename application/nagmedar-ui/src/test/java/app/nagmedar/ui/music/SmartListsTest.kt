package app.nagmedar.ui.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** S-805: what the smart lists take from the library, in which order, and how they are kept. */
class SmartListsTest {

    private val turkish = Locale.forLanguageTag("tr")
    private val tracks = MusicSamples.tracks
    private val stats = MusicSamples.stats

    /** The samples' clock: a day after the first one was added. */
    private val now = 1_760_000_000_000L

    private fun titles(list: SmartList): List<String> = list.select(tracks, stats, now, turkish).map { it.title }

    @Test
    fun theTemplates() {
        assertEquals(listOf("Segâh Tekbîr", "Gece Yolcusu", "Neva Kâr"), titles(SmartTemplate.Favourites.build("En sevilenler")))
        assertEquals(listOf("Gece Yolcusu", "Yağmur", "Segâh Tekbîr"), titles(SmartTemplate.MostPlayed.build("En çok çalınanlar").copy(limit = 3)))
        assertEquals(listOf("Kuzey Işığı", "Liman", "İsimsiz"), titles(SmartTemplate.NeverPlayed.build("Hiç çalınmayanlar")))
        // 24 bit, DSD or 88.2 kHz and up: any of them
        assertEquals(
            listOf("Segâh Tekbîr", "Neva Kâr", "Nihâvend Saz Semâîsi", "Uzak", "Kuzey Işığı", "Ada"),
            titles(SmartTemplate.HiRes.build("Yüksek çözünürlük")),
        )
    }

    @Test
    fun textIsComparedTheTurkishWay() {
        assertEquals(3, titles(SmartList(name = "Klasik", rules = listOf(SmartRule(SmartField.Genre, SmartOp.Contains, "klasik")))).size)
        assertEquals(listOf("Uzak", "Kuzey Işığı"), titles(SmartList(name = "Çağla", rules = listOf(SmartRule(SmartField.Artist, SmartOp.Is, "cagla akin")))))
        assertEquals(listOf("Segâh Tekbîr"), titles(SmartList(name = "S", rules = listOf(SmartRule(SmartField.Title, SmartOp.StartsWith, "SEGAH")))))
        assertEquals(8, titles(SmartList(name = "Itrî'siz", rules = listOf(SmartRule(SmartField.Album, SmartOp.NotContains, "itri")))).size)
    }

    @Test
    fun numbersAndDates() {
        // added within three days: the album added last
        assertEquals(
            listOf("Segâh Tekbîr", "Neva Kâr", "Nihâvend Saz Semâîsi"),
            titles(SmartList(name = "Yeni", rules = listOf(SmartRule(SmartField.Added, SmartOp.InLast, "3")))),
        )
        assertEquals(listOf("Gece Yolcusu", "Yağmur"), titles(SmartList(name = "Çok", rules = listOf(SmartRule(SmartField.Plays, SmartOp.AtLeast, "20")))))
        // both rules: a 2016 rock track played at least 20 times, of at most 4 minutes 10 seconds
        assertEquals(
            listOf("Yağmur"),
            titles(
                SmartList(
                    name = "Kısa",
                    rules = listOf(SmartRule(SmartField.Plays, SmartOp.AtLeast, "20"), SmartRule(SmartField.Duration, SmartOp.AtMost, "4,1")),
                ),
            ),
        )
        assertEquals(listOf("Uzak", "Kuzey Işığı"), titles(SmartList(name = "192", rules = listOf(SmartRule(SmartField.Rate, SmartOp.Is, "192")))))
        assertEquals(10, titles(SmartList(name = "Bir yıl hariç", rules = listOf(SmartRule(SmartField.Year, SmartOp.IsNot, "2023")))).size)
    }

    @Test
    fun aRuleWithoutItsValueAsksNothing() {
        val unfinished = SmartRule(SmartField.Year, SmartOp.AtLeast, "")
        assertFalse(unfinished.complete)
        assertEquals(tracks.size, titles(SmartList(name = "Yarım", rules = listOf(unfinished))).size)
        assertTrue(SmartRule(SmartField.LastPlayed, SmartOp.Never).complete)
    }

    @Test
    fun keptAsText() {
        val list = SmartList(
            name = "Karışık",
            rules = listOf(
                SmartRule(SmartField.Title, SmartOp.Contains, "a|b\nc; %20"),
                SmartRule(SmartField.Depth, SmartOp.IsNot, Depth.Lossy.name),
                SmartRule(SmartField.LastPlayed, SmartOp.Never),
            ),
            sort = MusicSort(MusicColumn.LastPlayed, true),
        )
        assertEquals(list.rules, SmartList.decodeRules(list.encodeRules()))
        assertEquals(list.sort, SmartList.decodeSort(list.encodeSort()))
        assertEquals(null, SmartList.decodeSort(null))
        // an operator the field does not take falls back to its first; an unknown field is left out
        assertEquals(listOf(SmartRule(SmartField.Year, SmartOp.AtLeast, "2000")), SmartList.decodeRules("year|contains|2000\nmood|is|calm"))
    }
}
