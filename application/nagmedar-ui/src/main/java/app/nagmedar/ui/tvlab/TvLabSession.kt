package app.nagmedar.ui.tvlab

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.lab.BitrateAnalysis
import app.nagmedar.lab.DiskSpeed
import app.nagmedar.lab.MediaTech
import app.nagmedar.lab.NativeLab
import app.nagmedar.lab.displayName
import app.nagmedar.ui.library.TechRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.Settings
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.CoroutineContext

/** The film being analysed: where it is, its name and size. */
@Immutable
data class TvLabFile(val uri: String, val name: String, val sizeBytes: Long)

/**
 * A step of the analysis: not started, running (how far, when known; the disk test's rate so far,
 * MB/s), done, or failed with a reason.
 */
@Immutable
sealed interface Step {
    data object Idle : Step

    data class Running(val fraction: Float?, val rate: Double? = null) : Step

    data object Done : Step

    data class Failed(val reason: String) : Step
}

/** What the analysis needs from the device: the probe, the bitrate, the drive and its speed (native/lab). */
interface TvLabEngine {
    /** Whether this build has the native lab tools. The first ask may load their library: off the main thread. */
    val available: Boolean

    /** [available] as far as it is known without loading anything; null before the library's load. */
    val loaded: Boolean? get() = available

    fun describe(uri: Uri): TvLabFile?

    suspend fun probe(uri: Uri): MediaTech?

    /** The bitrate analysis, or its error's text. */
    suspend fun bitrate(uri: Uri, progress: (Float) -> Unit): Result<BitrateAnalysis>

    fun drive(uri: Uri): DriveInfo?

    /** The drive's sequential read, from a part of the film [sizeBytes] long. */
    suspend fun diskSpeed(uri: Uri, sizeBytes: Long, progress: (Float, Double) -> Unit): Result<DiskSpeed>

    /** Stops what runs; asked only once [available] said so, which is what loads the native library. */
    fun cancel()
}

/** The device's: the media probe through the library's cache, the native bitrate and disk tests. */
class NativeTvLabEngine(private val context: Context) : TvLabEngine {
    /** Asked for when a bitrate is read, off the main thread: the app's filesDir looks at the disk. */
    private val cacheDir by lazy { File(context.filesDir, "tvlab/bitrate") }

    override val available: Boolean get() = NativeLab.available

    override val loaded: Boolean? get() = NativeLab.loaded

    override fun describe(uri: Uri): TvLabFile? = runCatching {
        val size = when (uri.scheme) {
            null, "file" -> File(uri.path ?: return null).length()
            else -> context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
            } ?: 0L
        }
        TvLabFile(uri.toString(), context.displayName(uri), size)
    }.getOrNull()

    override suspend fun probe(uri: Uri): MediaTech? = TechRepository.get(context).probeNow(uri.toString())

    override suspend fun bitrate(uri: Uri, progress: (Float) -> Unit): Result<BitrateAnalysis> = withContext(Dispatchers.IO) {
        val size = describe(uri)?.sizeBytes ?: 0L
        // a large film takes minutes: its analysis is kept, read again while the file stays the same
        val cache = File(cacheDir, key(uri.toString(), size) + ".json")
        cache.takeIf { it.isFile }?.readText()?.let(BitrateAnalysis::parse)?.takeIf { it.complete }?.let { return@withContext Result.success(it) }
        val json = withFd(uri) { path -> NativeLab.bitrate(path, 0.0) { fraction, _ -> progress(fraction.toFloat()) } }
            ?: return@withContext Result.failure(IllegalStateException("open"))
        val analysis = BitrateAnalysis.parse(json) ?: return@withContext Result.failure(IllegalStateException(BitrateAnalysis.error(json) ?: "?"))
        if (analysis.complete) runCatching { cacheDir.mkdirs(); cache.writeText(json) }
        Result.success(analysis)
    }

    override fun drive(uri: Uri): DriveInfo? = DriveInspector.inspect(context, uri)

    override suspend fun diskSpeed(uri: Uri, sizeBytes: Long, progress: (Float, Double) -> Unit): Result<DiskSpeed> = withContext(Dispatchers.IO) {
        val json = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                // the middle of the film: not the start the probe has just read
                NativeLab.diskSpeed(descriptor.fd, sizeBytes / 3, DISK_BYTES, DISK_SECONDS) { fraction, message ->
                    progress(fraction.toFloat(), message.substringBefore(' ').toDoubleOrNull() ?: 0.0)
                }
            }
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("open"))
        DiskSpeed.parse(json)?.let { Result.success(it) } ?: Result.failure(IllegalStateException(DiskSpeed.error(json) ?: "?"))
    }

    /** Nothing of the library's runs before it is loaded: then nothing is stopped, and nothing loads it on the main thread. */
    override fun cancel() {
        if (NativeLab.loaded == true) NativeLab.cancel()
    }

    /** [block] with a path the native side opens: the file's own, or /proc/self/fd/N of the document. */
    private fun <T> withFd(uri: Uri, block: (String) -> T): T? = runCatching {
        when (uri.scheme) {
            null, "file" -> uri.path?.let(block)
            else -> context.contentResolver.openFileDescriptor(uri, "r")?.use { block("/proc/self/fd/${it.fd}") }
        }
    }.getOrNull()

    private companion object {
        const val DISK_BYTES = 1L shl 30
        const val DISK_SECONDS = 8.0

        fun key(uri: String, size: Long): String =
            MessageDigest.getInstance("SHA-1").digest("$uri|$size".toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/**
 * The TV Lab (S-1201 … S-1204): a film opened, probed (its streams, HDR and Dolby Vision), its
 * drive measured, its bitrate read from every packet; and checked against the profile chosen.
 * Lives as long as the app does, so that a long analysis goes on while other screens show. The
 * film's name and size and the profiles' files are read on [io], never on the main thread: the
 * app's own profiles show until the store has been read.
 */
@Stable
class TvLabController(
    private val engine: TvLabEngine,
    private val profiles: ProfileStore,
    private val scope: CoroutineScope,
    private val io: CoroutineContext = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * Whether this build has the native tools: as far as the engine knows without loading their
     * library, then as [checkTools] or an analysis found out, off the main thread.
     */
    var available by mutableStateOf(engine.loaded != false)
        private set

    var file by mutableStateOf<TvLabFile?>(null)
        private set
    var tech by mutableStateOf<MediaTech?>(null)
        private set
    var probe by mutableStateOf<Step>(Step.Idle)
        private set
    var bitrate by mutableStateOf<BitrateAnalysis?>(null)
        private set
    var bitrateStep by mutableStateOf<Step>(Step.Idle)
        private set
    var drive by mutableStateOf<DriveInfo?>(null)
        private set
    var disk by mutableStateOf<DiskSpeed?>(null)
        private set
    var diskStep by mutableStateOf<Step>(Step.Idle)
        private set

    /** How long the analysis took, once it is done. */
    var elapsedMs by mutableStateOf<Long?>(null)
        private set

    /** The app's profiles as they came, until [reloadProfiles] has read the files (off the main thread). */
    var profileList by mutableStateOf(BuiltInProfiles.all)
        private set
    var profile by mutableStateOf(BuiltInProfiles.all.first())
        private set

    /** The profiles kept as the user saved them, read with the list. */
    private var savedIds by mutableStateOf(emptySet<String>())

    private var job: Job? = null

    init {
        scope.launch { load { list, selected -> list.firstOrNull { it.id == selected } } }
    }

    /** Asks the engine whether the tools are there, off the main thread: the first ask loads their library. */
    suspend fun checkTools() {
        available = withContext(Dispatchers.IO) { engine.available }
    }

    /**
     * Analyses the film at [uri]: its streams, its drive, then its bitrate. What ran for the
     * film before stops; without the native tools nothing runs. The job is returned for the tests.
     */
    fun open(uri: Uri): Job {
        job?.cancel()
        // the engine stops what runs only once its library is loaded: nothing loads it here, on the main thread
        engine.cancel()
        // its name and size come from its provider (a drive that may be asleep), off the main thread
        file = TvLabFile(uri.toString(), uri.lastPathSegment.orEmpty(), 0)
        tech = null
        bitrate = null
        disk = null
        drive = null
        elapsedMs = null
        bitrateStep = Step.Idle
        diskStep = Step.Idle
        probe = Step.Running(null)
        val started = now()
        return scope.launch {
            describe(uri)
            checkTools()
            if (!available) {
                probe = Step.Failed(NO_TOOLS)
                return@launch
            }
            tech = engine.probe(uri)
            probe = if (tech != null) Step.Done else Step.Failed(UNREADABLE)
            drive = withContext(Dispatchers.IO) { engine.drive(uri) }
            measureDisk(uri)
            // a file the probe could not read has no packets to count either
            if (tech != null) readBitrate(uri)
            elapsedMs = now() - started
        }.also { job = it }
    }

    /** The bitrate read again, after it was stopped. */
    fun analyseBitrate() {
        val uri = file?.uri?.let(Uri::parse) ?: return
        if (tech == null || bitrateStep is Step.Running || diskStep is Step.Running) return
        job = scope.launch { readBitrate(uri) }
    }

    /** The drive measured again. */
    fun measureDiskAgain() {
        val uri = file?.uri?.let(Uri::parse) ?: return
        if (diskStep is Step.Running || bitrateStep is Step.Running) return
        job = scope.launch { measureDisk(uri) }
    }

    /** Stops what runs; what was found stays. */
    fun cancel() {
        if (engine.available) engine.cancel()
        job?.cancel()
        if (bitrateStep is Step.Running) bitrateStep = Step.Failed(CANCELLED)
        if (diskStep is Step.Running) diskStep = Step.Failed(CANCELLED)
        if (probe is Step.Running) probe = Step.Failed(CANCELLED)
    }

    fun choose(id: String) {
        val chosen = profileList.firstOrNull { it.id == id } ?: return
        profile = chosen
        profiles.selected = id
    }

    /** [edited] kept, and chosen. */
    fun saveProfile(edited: DeviceProfile) {
        scope.launch {
            withContext(io) { profiles.save(edited) }
            load { list, _ -> list.firstOrNull { it.id == edited.id } }
            choose(edited.id)
        }
    }

    /** A user's profile removed, an app's back to as it came; the chosen one stays if it can. */
    fun removeProfile(id: String) {
        scope.launch {
            withContext(io) { profiles.remove(id) }
            load { _, _ -> null }
            profiles.selected = profile.id
        }
    }

    /**
     * The profiles read from their files, when the TV Lab is made and as a restore left them
     * (S-1502): the files off the main thread, then shown; the chosen one kept's, else the one shown.
     */
    suspend fun reloadProfiles() {
        load { list, selected -> list.firstOrNull { it.id == selected } }
    }

    /** An id for a new profile named [name]. */
    fun newId(name: String): String = FileProfileStore.newId(name, profileList.map { it.id })

    /** Whether the profile [id] is one the user saved: theirs, or an app's they changed. */
    fun saved(id: String): Boolean = id in savedIds

    /**
     * The profiles, the choice and which are saved, read on [io]; the profile shown is the one
     * [pick] finds in the list (given the store's choice), else the one shown so far, else the first.
     */
    private suspend fun load(pick: (List<DeviceProfile>, String?) -> DeviceProfile?) {
        val (list, selected, saved) = withContext(io) {
            val all = profiles.all()
            Triple(all, profiles.selected, all.map { it.id }.filter(profiles::saved).toSet())
        }
        profileList = list
        savedIds = saved
        profile = pick(list, selected) ?: list.firstOrNull { it.id == profile.id } ?: list.first()
    }

    /** The film's name and size from its provider, on [io]; kept as opened when it does not say. */
    private suspend fun describe(uri: Uri) {
        withContext(io) { engine.describe(uri) }?.let { file = it }
    }

    private suspend fun measureDisk(uri: Uri) {
        val size = file?.sizeBytes ?: 0L
        val running = currentCoroutineContext()[Job]
        diskStep = Step.Running(0f)
        // a last report from the native side after a stop does not undo it
        diskStep = engine.diskSpeed(uri, size) { fraction, mbps -> if (running?.isActive != false) diskStep = Step.Running(fraction, mbps) }.fold(
            onSuccess = {
                disk = it
                Step.Done
            },
            onFailure = { Step.Failed(it.message ?: "?") },
        )
    }

    private suspend fun readBitrate(uri: Uri) {
        val running = currentCoroutineContext()[Job]
        bitrateStep = Step.Running(0f)
        bitrateStep = engine.bitrate(uri) { fraction -> if (running?.isActive != false) bitrateStep = Step.Running(fraction) }.fold(
            onSuccess = {
                bitrate = it
                if (it.complete) Step.Done else Step.Failed(CANCELLED)
            },
            onFailure = { Step.Failed(it.message ?: "?") },
        )
    }

    companion object {
        /** Why a step failed: stopped by the user, a file that is not media, a build without the native tools. */
        const val CANCELLED = "cancelled"
        const val UNREADABLE = "unreadable"
        const val NO_TOOLS = "no-tools"
    }
}

/** The app's one TV Lab, which outlives its screen. */
object TvLabSession {
    @Volatile
    private var controller: TvLabController? = null

    /** Made on the main thread at the app's start (the sidebar shows an analysis): it reads no file there. */
    fun get(context: Context): TvLabController = controller ?: synchronized(this) {
        // made in the caller's snapshot: NagmedarApp asks inside its composition and reads it there,
        // which a state made in Snapshot.global, newer than that composition's snapshot, would not allow
        controller ?: TvLabController(
            NativeTvLabEngine(context.applicationContext),
            profileStore(context),
            CoroutineScope(SupervisorJob() + Dispatchers.Main),
        ).also { controller = it }
    }

    /** The TV Lab when it was opened; null before. */
    fun opened(): TvLabController? = controller

    /** Where the profiles are kept: a JSON file each in the app's folder, the chosen one in the preferences. */
    fun profileStore(context: Context): FileProfileStore {
        val app = context.applicationContext
        return FileProfileStore({ File(app.filesDir, "tvlab/profiles") }, Settings.getInstance(app))
    }
}
