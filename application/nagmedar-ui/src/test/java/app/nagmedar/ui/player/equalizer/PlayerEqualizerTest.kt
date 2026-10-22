package app.nagmedar.ui.player.equalizer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** libVLC's flat and rock presets as VLC's database has them, and its ten bands. */
internal val FLAT = VlcEqualizer(1, "Flat", 0, 12f, List(10) { 0f })
internal val ROCK = VlcEqualizer(14, "Rock", 13, 5f, listOf(8f, 4.8f, -5.6f, -8f, -3.2f, 4f, 8.8f, 11.2f, 11.2f, 11.2f))
internal val FREQUENCIES = listOf(60f, 170f, 310f, 600f, 1_000f, 3_000f, 6_000f, 12_000f, 14_000f, 16_000f)

/** VLC's equalisers in memory, and what was written to them, in order. */
internal class FakeEqualizerStore(
    val equalizers: MutableList<VlcEqualizer> = mutableListOf(FLAT, ROCK),
    var currentId: Long = FLAT.id,
    /** VLC's equaliser is on. */
    var on: Boolean = false,
    /** The presets VLC's settings hid: not offered, their names still taken. */
    private val hidden: Set<String> = setOf("Party"),
    private val fails: Boolean = false,
) : PlayerEqualizerStore {
    val writes = mutableListOf<String>()
    private var nextId = 100L

    override suspend fun load(): StoredEqualizers {
        if (fails) error("no database")
        return StoredEqualizers(equalizers.toList(), currentId, on, equalizers.map { it.name }.toSet() + hidden, FREQUENCIES)
    }

    override suspend fun add(equalizer: VlcEqualizer): Long {
        val id = nextId++
        equalizers += equalizer.copy(id = id)
        writes += "add:${equalizer.name}"
        return id
    }

    override fun save(equalizer: VlcEqualizer) {
        equalizers.replaceAll { if (it.id == equalizer.id) equalizer else it }
        writes += "save:${equalizer.name}"
    }

    override fun delete(equalizer: VlcEqualizer) {
        equalizers.removeAll { it.id == equalizer.id }
        writes += "delete:${equalizer.name}"
    }

    override fun setCurrent(id: Long) {
        currentId = id
        writes += "current:$id"
    }

    override fun setEnabled(on: Boolean) {
        this.on = on
        writes += "enabled:$on"
    }
}

/** The film: what it heard of the player's setting, and its own equaliser from the effects tab. */
internal class FakeEqualizerTarget(override var filmChoice: String? = null) : PlayerEqualizerTarget {
    val heard = mutableListOf<VlcEqualizer?>()

    override fun followPlayer() {
        filmChoice = null
    }

    override fun apply(equalizer: VlcEqualizer?) {
        heard += equalizer
    }
}

/**
 * The player's equaliser edits VLC's (what libVLC plays films, radio and streams through): on or
 * off, a preset or the user's own, heard at once and kept where VLC keeps it; libVLC's presets
 * stay as they are and are copied to be changed; names are VLC's keys and stay unique.
 */
class PlayerEqualizerTest {

    private val store = FakeEqualizerStore()
    private val target = FakeEqualizerTarget()

    private fun controller(store: PlayerEqualizerStore = this.store) =
        PlayerEqualizerController(store, target, CoroutineScope(Dispatchers.Unconfined)) { eq -> eq.name + " kopyası" }.also { it.load() }

    @Test
    fun vlcsSettingIsRead() {
        store.currentId = ROCK.id
        store.on = true
        val state = controller().state
        assertFalse(state.loading)
        assertTrue(state.enabled)
        assertEquals(ROCK, state.current)
        assertEquals(listOf(FLAT, ROCK), state.presets)
        assertEquals(FREQUENCIES, state.frequencies)
        // libVLC's presets are not moved
        assertFalse(state.editable)
    }

    @Test
    fun onAndOffAreHeardAndKept() {
        val equalizer = controller()
        equalizer.setEnabled(true)
        equalizer.setEnabled(false)
        assertEquals(listOf(FLAT, null), target.heard)
        assertEquals(listOf("enabled:true", "enabled:false"), store.writes)
    }

    @Test
    fun choosingAPresetTurnsTheEqualizerOn() {
        val equalizer = controller()
        equalizer.choose(ROCK.id)
        assertTrue(equalizer.state.enabled)
        assertEquals(ROCK, equalizer.state.current)
        assertEquals(listOf<VlcEqualizer?>(ROCK), target.heard)
        assertEquals(listOf("current:${ROCK.id}", "enabled:true"), store.writes)
    }

    @Test
    fun aPresetStaysAsItIs() {
        val equalizer = controller()
        equalizer.choose(ROCK.id)
        target.heard.clear()
        store.writes.clear()
        equalizer.setBand(0, 2f)
        equalizer.setPreamp(0f)
        assertEquals(ROCK, equalizer.state.current)
        assertEquals(emptyList<VlcEqualizer?>(), target.heard)
        assertEquals(emptyList<String>(), store.writes)
    }

    @Test
    fun aCopyOfAPresetIsTheUsersToChange() {
        val equalizer = controller()
        equalizer.choose(ROCK.id)
        equalizer.copy()
        val copy = checkNotNull(equalizer.state.current)
        assertTrue(copy.own)
        assertEquals("Rock kopyası", copy.name)
        assertEquals(ROCK.bandsDb, copy.bandsDb)
        assertEquals(100L, copy.id)
        assertTrue(equalizer.state.editable)
        assertEquals(listOf(copy), equalizer.state.own)
        equalizer.setBand(2, 6f)
        equalizer.setPreamp(-3.5f)
        val changed = checkNotNull(equalizer.state.current)
        assertEquals(6f, changed.bandsDb[2])
        assertEquals(-3.5f, changed.preampDb)
        assertEquals(changed, target.heard.last())
        assertEquals(
            listOf("current:${ROCK.id}", "enabled:true", "add:Rock kopyası", "current:100", "enabled:true", "save:Rock kopyası", "save:Rock kopyası"),
            store.writes,
        )
        // VLC's preset itself did not change
        assertEquals(ROCK, store.equalizers.first { it.id == ROCK.id })
    }

    @Test
    fun aCopysNameIsNoOtherEqualizers() {
        store.equalizers += VlcEqualizer(50, "Rock kopyası", VlcEqualizer.OWN, 5f, ROCK.bandsDb)
        val equalizer = controller()
        equalizer.choose(ROCK.id)
        equalizer.copy()
        assertEquals("Rock kopyası 2", equalizer.state.current?.name)
    }

    @Test
    fun theUsersOwnIsRenamedUnlessTheNameIsTaken() {
        val equalizer = controller()
        equalizer.copy()
        store.writes.clear()
        // a hidden preset's name is still VLC's key for it
        equalizer.rename("Party")
        assertTrue(equalizer.state.nameTaken)
        assertEquals("Party", equalizer.state.name)
        equalizer.rename("  ")
        assertTrue(equalizer.state.nameTaken)
        assertEquals(emptyList<String>(), store.writes)
        equalizer.rename("Gece")
        assertFalse(equalizer.state.nameTaken)
        assertEquals("Gece", equalizer.state.current?.name)
        assertEquals(listOf("save:Gece"), store.writes)
    }

    @Test
    fun theUsersOwnIsDeletedAndTheFirstPresetTakesItsPlace() {
        val equalizer = controller()
        equalizer.copy()
        val own = checkNotNull(equalizer.state.current)
        store.writes.clear()
        target.heard.clear()
        equalizer.delete()
        assertEquals(FLAT, equalizer.state.current)
        assertEquals(emptyList<VlcEqualizer>(), equalizer.state.own)
        assertEquals(listOf("delete:${own.name}", "current:${FLAT.id}"), store.writes)
        assertEquals(listOf<VlcEqualizer?>(FLAT), target.heard)
    }

    @Test
    fun aPresetIsNotDeleted() {
        val equalizer = controller()
        equalizer.delete()
        assertEquals(FLAT, equalizer.state.current)
        assertEquals(emptyList<String>(), store.writes)
    }

    @Test
    fun aFilmWithItsOwnEqualizerFollowsThePlayersAgain() {
        target.filmChoice = "rock"
        store.on = true
        val equalizer = controller()
        equalizer.followPlayer()
        assertNull(target.filmChoice)
        assertEquals(listOf<VlcEqualizer?>(FLAT), target.heard)
    }

    @Test
    fun aDatabaseThatCannotBeReadIsSaidSo() {
        val equalizer = controller(FakeEqualizerStore(fails = true))
        assertTrue(equalizer.state.failed)
        equalizer.setEnabled(true)
        assertEquals(emptyList<VlcEqualizer?>(), target.heard)
    }
}
