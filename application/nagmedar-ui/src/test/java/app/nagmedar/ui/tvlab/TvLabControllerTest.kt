package app.nagmedar.ui.tvlab

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.lab.BitrateAnalysis
import app.nagmedar.lab.DiskSpeed
import app.nagmedar.lab.MediaTech
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S-1201 … S-1204: the TV Lab's steps in order (the streams, the drive, its speed, the bitrate),
 * how they fail and stop, and the profiles chosen and kept. Robolectric for [Uri].
 */
@RunWith(AndroidJUnit4::class)
class TvLabControllerTest {

    private class FakeEngine(
        override val available: Boolean = true,
        var tech: MediaTech? = sampleTech(),
    ) : TvLabEngine {
        val calls = mutableListOf<String>()
        var bitrateGate: CompletableDeferred<Unit>? = null
        var lastProgress: ((Float) -> Unit)? = null

        override fun describe(uri: Uri) = sampleFile

        override suspend fun probe(uri: Uri): MediaTech? {
            calls += "probe"
            return tech
        }

        override suspend fun bitrate(uri: Uri, progress: (Float) -> Unit): Result<BitrateAnalysis> {
            calls += "bitrate"
            lastProgress = progress
            progress(0.5f)
            bitrateGate?.await()
            return Result.success(sampleBitrate(60))
        }

        override fun drive(uri: Uri): DriveInfo {
            calls += "drive"
            return sampleDrive
        }

        override suspend fun diskSpeed(uri: Uri, sizeBytes: Long, progress: (Float, Double) -> Unit): Result<DiskSpeed> {
            calls += "disk:$sizeBytes"
            progress(0.5f, 380.0)
            return Result.success(sampleDisk)
        }

        override fun cancel() {
            calls += "cancel"
        }
    }

    private class MemoryStore : ProfileStore {
        val saved = linkedMapOf<String, DeviceProfile>()

        override fun all() = BuiltInProfiles.all.map { saved[it.id]?.copy(builtIn = true) ?: it } + saved.values.filter { BuiltInProfiles.of(it.id) == null }

        override fun save(profile: DeviceProfile) {
            saved[profile.id] = profile
        }

        override fun remove(id: String) {
            saved.remove(id)
            if (selected == id && BuiltInProfiles.of(id) == null) selected = null
        }

        override fun saved(id: String) = id in saved

        override var selected: String? = null
    }

    private val uri = Uri.parse(sampleFile.uri)
    private var clock = 1_000L

    private fun controller(engine: TvLabEngine, store: ProfileStore = MemoryStore()) =
        TvLabController(engine, store, CoroutineScope(Dispatchers.Unconfined)) { clock.also { clock += 42_000 } }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "waited for $what" }
            Thread.sleep(10)
        }
    }

    @Test
    fun theStepsInOrder() {
        val engine = FakeEngine()
        val lab = controller(engine)
        runBlocking { lab.open(uri)!!.join() }
        assertEquals(listOf("cancel", "probe", "drive", "disk:${sampleFile.sizeBytes}", "bitrate"), engine.calls)
        assertEquals(sampleFile, lab.file)
        assertEquals(Step.Done, lab.probe)
        assertEquals(Step.Done, lab.diskStep)
        assertEquals(Step.Done, lab.bitrateStep)
        assertEquals(sampleDrive, lab.drive)
        assertEquals(sampleDisk, lab.disk)
        assertEquals(60, lab.bitrate?.seconds)
        assertEquals(42_000L, lab.elapsedMs)
        assertEquals(sampleTech(), lab.view().tech)
    }

    @Test
    fun aFileThatIsNotMediaIsNotCounted() {
        val engine = FakeEngine(tech = null)
        val lab = controller(engine)
        runBlocking { lab.open(uri)!!.join() }
        assertEquals(Step.Failed(TvLabController.UNREADABLE), lab.probe)
        // its drive is measured all the same; its packets are not read
        assertEquals(Step.Done, lab.diskStep)
        assertEquals(Step.Idle, lab.bitrateStep)
        assertTrue("bitrate" !in engine.calls)
    }

    @Test
    fun withoutTheNativeToolsNothingRuns() {
        val engine = FakeEngine(available = false)
        val lab = controller(engine)
        assertNull(lab.open(uri))
        assertEquals(Step.Failed(TvLabController.NO_TOOLS), lab.probe)
        assertEquals(listOf("cancel"), engine.calls)
        assertEquals(sampleFile, lab.file)
    }

    @Test
    fun aStopIsNotUndoneByALateReport() {
        val engine = FakeEngine().apply { bitrateGate = CompletableDeferred() }
        val lab = controller(engine)
        val job = lab.open(uri)!!
        waitFor("the bitrate half read") { lab.bitrateStep == Step.Running(0.5f) }
        lab.cancel()
        assertEquals(Step.Failed(TvLabController.CANCELLED), lab.bitrateStep)
        // the native side reports once more before it sees the stop
        engine.lastProgress!!.invoke(0.9f)
        runBlocking { job.join() }
        assertEquals(Step.Failed(TvLabController.CANCELLED), lab.bitrateStep)
        // what was found stays
        assertEquals(sampleDisk, lab.disk)
        assertEquals(Step.Done, lab.probe)
        // and the bitrate can be read again
        engine.bitrateGate = null
        lab.analyseBitrate()
        waitFor("the bitrate again") { lab.bitrateStep == Step.Done }
        assertEquals(60, lab.bitrate?.seconds)
    }

    @Test
    fun theDriveMeasuredAgain() {
        val engine = FakeEngine()
        val lab = controller(engine)
        runBlocking { lab.open(uri)!!.join() }
        engine.calls.clear()
        lab.measureDiskAgain()
        waitFor("the disk") { lab.diskStep == Step.Done && engine.calls.isNotEmpty() }
        assertEquals(listOf("disk:${sampleFile.sizeBytes}"), engine.calls)
    }

    @Test
    fun profilesChosenSavedAndRemoved() {
        val store = MemoryStore()
        val lab = controller(FakeEngine(), store)
        assertEquals(BuiltInProfiles.lgB4Usb, lab.profile)
        lab.choose(BuiltInProfiles.tabS11Ultra.id)
        assertEquals(BuiltInProfiles.tabS11Ultra, lab.profile)
        assertEquals(BuiltInProfiles.tabS11Ultra.id, store.selected)
        lab.choose("nothing")
        assertEquals(BuiltInProfiles.tabS11Ultra, lab.profile)

        val bravia = BuiltInProfiles.lgB4Usb.copy(id = lab.newId("Sony Bravia 8"), name = "Sony Bravia 8", builtIn = false)
        assertEquals("sony-bravia-8", bravia.id)
        lab.saveProfile(bravia)
        assertEquals(bravia, lab.profile)
        assertEquals(3, lab.profileList.size)
        assertTrue(lab.saved(bravia.id))
        assertEquals("sony-bravia-8-2", lab.newId("Sony Bravia 8"))

        // removed, the first profile is chosen
        lab.removeProfile(bravia.id)
        assertEquals(BuiltInProfiles.lgB4Usb, lab.profile)
        assertEquals(BuiltInProfiles.lgB4Usb.id, store.selected)
        assertEquals(2, lab.profileList.size)

        // a store that remembers a profile chooses it at once
        val remembered = MemoryStore().apply { selected = BuiltInProfiles.tabS11Ultra.id }
        assertEquals(BuiltInProfiles.tabS11Ultra, controller(FakeEngine(), remembered).profile)
    }
}
