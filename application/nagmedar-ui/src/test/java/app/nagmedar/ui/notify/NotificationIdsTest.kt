package app.nagmedar.ui.notify

import app.nagmedar.ui.i18n.KotlinSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import org.videolan.resources.NotificationIds as VlcNotificationIds

/** The notification ids: no two notifications of the app can take each other's place, nor their taps each other's extras. */
class NotificationIdsTest {

    private val fields = NotificationIds::class.java.declaredFields.filter { Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) }

    /** Every fixed id (and the widget's request code) by its name. */
    private val fixed: Map<String, Int> = fields.filter { it.type == Int::class.javaPrimitiveType }.associate { it.name to it.getInt(null) }

    private val ranges: Map<String, IdRange> = fields.filter { it.type == IdRange::class.java }.associate { it.name to it.get(null) as IdRange }

    @Test
    fun theRegistryIsRead() {
        assertEquals(3, fixed["VLC_PLAYBACK"])
        assertEquals(1301, fixed["PROMPT"])
        assertEquals(20_000, ranges["PODCAST_NEWS"]?.first)
        assertEquals(NotificationIds.NEW_DRIVE, ranges["NEW_DRIVE"])
    }

    @Test
    fun noTwoFixedIdsAreEqual() {
        val twice = fixed.entries.groupBy({ it.value }, { it.key }).filterValues { it.size > 1 }
        assertTrue("ids given twice: $twice", twice.isEmpty())
    }

    @Test
    fun noFixedIdFallsInARange() {
        val inside = fixed.flatMap { (name, id) -> ranges.filterValues { id in it }.keys.map { "$name ($id) in $it" } }
        assertTrue("ids inside a range: $inside", inside.isEmpty())
    }

    @Test
    fun noTwoRangesMeet() {
        val list = ranges.entries.toList()
        val meeting = list.indices.flatMap { i -> (i + 1 until list.size).filter { list[i].value.meets(list[it].value) }.map { "${list[i].key} and ${list[it].key}" } }
        assertTrue("ranges that meet: $meeting", meeting.isEmpty())
    }

    @Test
    fun vlcsIdsAsItsServicesHaveThem() {
        val vlc = fixed.filterKeys { it.startsWith("VLC_") }.mapKeys { it.key.removePrefix("VLC_") }
        val services = VlcNotificationIds.entries.filter { it != VlcNotificationIds.RECOMMENDATIONS }.associate { it.name to it.id }
        assertEquals(services, vlc)
        assertEquals(VlcNotificationIds.RECOMMENDATIONS.id, NotificationIds.VLC_RECOMMENDATIONS.first)
    }

    @Test
    fun anItemsIdStaysInItsRange() {
        val news = NotificationIds.PODCAST_NEWS
        // as before the registry: a show's news keeps its id
        assertEquals(20_049, news.idOf(49L))
        assertEquals(news.idOf(49L), news.idOf(100_049L))
        listOf(0L, 1L, 99_999L, 100_000L, -1L, Long.MIN_VALUE, Long.MAX_VALUE).forEach { key -> assertTrue("$key", news.idOf(key) in news) }
        listOf(0, -1, Int.MIN_VALUE, Int.MAX_VALUE, "/storage/1234-5678".hashCode()).forEach { hash ->
            ranges.values.forEach { range -> assertTrue("$hash in $range", range.idOf(hash) in range) }
        }
    }

    @Test
    fun aShowsNewsAndTheQuestionNoLongerMeet() {
        // libVLC's question was 0x4E51, the id of the show 49's news; the widget's tap, 0x4E57, would be the request code of the show 55's (a news' tap has its id)
        assertFalse(NotificationIds.PROMPT in NotificationIds.PODCAST_NEWS)
        assertFalse(NotificationIds.WIDGET_TAP in NotificationIds.PODCAST_NEWS)
    }

    /** No id is numbered in the code outside the registry: no constant named …ID… set to a number, no id made of a hash. */
    @Test
    fun theIdsAreTakenFromTheRegistry() {
        val root = File("src/main/java")
        assertTrue("run from the module's folder", root.isDirectory)
        val registry = "app/nagmedar/ui/notify/NotificationIds.kt"
        val numbered = Regex("""\bconst\s+val\s+([A-Z][A-Z0-9_]*)\s*(?::\s*Int\s*)?=\s*(?:0[xX][0-9A-Fa-f_]+|[0-9][0-9_]*)\b""")
        val hashed = Regex("""\.notify\(\s*[^,()]*\.hashCode\(\)""")
        val found = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
            val path = file.relativeTo(root).invariantSeparatorsPath
            if (path == registry) return@flatMap emptySequence()
            val source = KotlinSource(path, file.readText())
            val ids = numbered.findAll(source.code).filter { "ID" in it.groupValues[1].split('_') }.map { "$path:${source.lineOf(it.range.first)} ${it.groupValues[1]}" }
            val hashes = hashed.findAll(source.code).map { "$path:${source.lineOf(it.range.first)} ${it.value}" }
            ids + hashes
        }.toList()
        assertTrue("ids numbered outside NotificationIds: $found", found.isEmpty())
    }
}
