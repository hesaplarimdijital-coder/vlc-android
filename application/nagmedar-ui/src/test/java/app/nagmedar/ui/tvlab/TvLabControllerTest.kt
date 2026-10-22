package app.nagmedar.ui.tvlab

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.Collections
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

    private val uri = Uri.parse(sampleFile.uri)
    private var clock = 1_000L

    private fun controller(engine: TvLabEngine, store: ProfileStore = MemoryProfileStore()) =
        TvLabController(engine, store, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined) { clock.also { clock += 42_000 } }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "waited for $what" }
            Thread.sleep(10)
        }
    }

    @Test
    fun theStepsInOrder() {
        val engine = FakeTvLabEngine()
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
        val engine = FakeTvLabEngine(tech = null)
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
        val engine = FakeTvLabEngine(available = false)
        val lab = controller(engine)
        assertNull(lab.open(uri))
        assertEquals(Step.Failed(TvLabController.NO_TOOLS), lab.probe)
        // not even the cancel: the native library is not there to load
        assertEquals(emptyList<String>(), engine.calls)
        lab.cancel()
        assertEquals(emptyList<String>(), engine.calls)
        assertEquals(sampleFile, lab.file)
    }

    /** The film's provider and the profiles' files are read on the controller's IO context, never on the caller's thread. */
    @Test
    fun theProviderAndTheProfilesAreReadOffTheCallersThread() {
        val caller = Thread.currentThread()
        val readers: MutableList<Thread> = Collections.synchronizedList(mutableListOf())
        val engine = object : TvLabEngine by FakeTvLabEngine() {
            override fun describe(uri: Uri): TvLabFile {
                readers += Thread.currentThread()
                return sampleFile
            }
        }
        val store = object : ProfileStore by MemoryProfileStore() {
            override fun all(): List<DeviceProfile> {
                readers += Thread.currentThread()
                return BuiltInProfiles.all
            }
        }
        val lab = TvLabController(engine, store, CoroutineScope(Dispatchers.Unconfined), Dispatchers.IO)
        runBlocking { lab.open(uri)!!.join() }
        waitFor("the profiles") { readers.size >= 2 }
        assertTrue("read on $readers", readers.none { it === caller })
        assertEquals(sampleFile, lab.file)
        assertEquals(BuiltInProfiles.lgB4Usb, lab.profile)
    }

    @Test
    fun aStopIsNotUndoneByALateReport() {
        val engine = FakeTvLabEngine().apply { bitrateGate = CompletableDeferred() }
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
        val engine = FakeTvLabEngine()
        val lab = controller(engine)
        runBlocking { lab.open(uri)!!.join() }
        engine.calls.clear()
        lab.measureDiskAgain()
        waitFor("the disk") { lab.diskStep == Step.Done && engine.calls.isNotEmpty() }
        assertEquals(listOf("disk:${sampleFile.sizeBytes}"), engine.calls)
    }

    @Test
    fun profilesChosenSavedAndRemoved() {
        val store = MemoryProfileStore()
        val lab = controller(FakeTvLabEngine(), store)
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
        val remembered = MemoryProfileStore().apply { selected = BuiltInProfiles.tabS11Ultra.id }
        assertEquals(BuiltInProfiles.tabS11Ultra, controller(FakeTvLabEngine(), remembered).profile)
    }

    @Test
    fun theProfilesFilesAreReadOffTheMainThreadNotWhenTheLabIsMade() {
        // the TV Lab is made on the main thread at the app's start: it shows the app's profiles, and reads the files later
        val bravia = BuiltInProfiles.lgB4Usb.copy(id = "sony-bravia-8", name = "Sony Bravia 8", builtIn = false)
        val store = CountingProfileStore().apply {
            save(bravia)
            selected = bravia.id
        }
        val lab = controller(FakeTvLabEngine(), store)
        assertEquals(0, store.reads)
        assertEquals(BuiltInProfiles.all, lab.profileList)
        assertEquals(BuiltInProfiles.lgB4Usb, lab.profile)

        runBlocking { lab.reloadProfiles() }
        assertEquals(1, store.reads)
        assertEquals(3, lab.profileList.size)
        assertEquals(bravia, lab.profile)
    }

    /** A store that counts how often its profiles are read. */
    private class CountingProfileStore(private val inner: MemoryProfileStore = MemoryProfileStore()) : ProfileStore by inner {
        var reads = 0

        override fun all(): List<DeviceProfile> {
            reads++
            return inner.all()
        }
    }

    @Test
    fun profilesReadAgainAsARestoreLeftThem() {
        val store = MemoryProfileStore()
        val lab = controller(FakeTvLabEngine(), store)
        // a restore (S-1502) writes the store behind the open TV Lab's back
        val bravia = BuiltInProfiles.lgB4Usb.copy(id = "sony-bravia-8", name = "Sony Bravia 8", builtIn = false)
        store.save(bravia)
        store.selected = bravia.id
        assertEquals(2, lab.profileList.size)

        runBlocking { lab.reloadProfiles() }
        assertEquals(3, lab.profileList.size)
        assertEquals(bravia, lab.profile)

        // a choice that is not among them: the one shown stays
        lab.choose(BuiltInProfiles.tabS11Ultra.id)
        store.selected = "nothing"
        runBlocking { lab.reloadProfiles() }
        assertEquals(BuiltInProfiles.tabS11Ultra, lab.profile)
    }
}
