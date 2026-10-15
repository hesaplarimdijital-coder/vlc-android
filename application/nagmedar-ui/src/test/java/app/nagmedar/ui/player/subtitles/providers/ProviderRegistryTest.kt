package app.nagmedar.ui.player.subtitles.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-707: providers turned on and off and put in order, and the choice kept. */
class ProviderRegistryTest {

    private val os = FakeProvider("opensubtitles")
    private val local = FakeProvider("local-folder")
    private val third = FakeProvider("third")
    private val store = MemoryProviderStore()

    private fun registry() = ProviderRegistry(listOf(os, local, third), store, offByDefault = setOf("local-folder"))

    @Test
    fun theirOwnOrderAndTheirDefaults() {
        val registry = registry()
        assertEquals(listOf("opensubtitles", "local-folder", "third"), registry.ordered.map { it.id })
        // the folder starts off: it needs something of the user's
        assertEquals(listOf("opensubtitles", "third"), registry.enabled().map { it.id })
        assertFalse(registry.isEnabled("local-folder"))
        assertSame(os, registry.byId("opensubtitles"))
        assertNull(registry.byId("nothing"))
    }

    @Test
    fun turnedOnAndOff() {
        val registry = registry()
        registry.setEnabled("local-folder", true)
        registry.setEnabled("opensubtitles", false)
        assertEquals(listOf("local-folder", "third"), registry.enabled().map { it.id })
        registry.setEnabled("local-folder", false)
        registry.setEnabled("opensubtitles", true)
        assertEquals(listOf("opensubtitles", "third"), registry.enabled().map { it.id })
        // one that is not there is left alone
        registry.setEnabled("nothing", false)
        assertEquals(ProviderSettings(order = emptyList(), off = emptySet(), on = emptySet()), registry.settings)
    }

    @Test
    fun movedEarlierAndLaterButNotOffTheList() {
        val registry = registry()
        registry.move("third", -1)
        assertEquals(listOf("opensubtitles", "third", "local-folder"), registry.ordered.map { it.id })
        registry.move("third", -5)
        assertEquals(listOf("third", "opensubtitles", "local-folder"), registry.ordered.map { it.id })
        registry.move("third", 1)
        registry.move("opensubtitles", 9)
        assertEquals(listOf("third", "local-folder", "opensubtitles"), registry.ordered.map { it.id })
        // the order of the ones turned on follows
        registry.setEnabled("local-folder", true)
        assertEquals(listOf("third", "local-folder", "opensubtitles"), registry.enabled().map { it.id })
    }

    @Test
    fun theChoiceIsKept() {
        registry().apply {
            setEnabled("local-folder", true)
            setEnabled("third", false)
            move("local-folder", -1)
        }
        val again = registry()
        assertEquals(listOf("local-folder", "opensubtitles"), again.enabled().map { it.id })
        assertTrue(again.isEnabled("local-folder"))
        assertFalse(again.isEnabled("third"))
    }

    @Test
    fun aProviderAddedLaterComesAfterTheOnesInTheOrderKept() {
        store.kept = ProviderSettings(order = listOf("third", "opensubtitles"))
        assertEquals(listOf("third", "opensubtitles", "local-folder"), registry().ordered.map { it.id })
    }
}
