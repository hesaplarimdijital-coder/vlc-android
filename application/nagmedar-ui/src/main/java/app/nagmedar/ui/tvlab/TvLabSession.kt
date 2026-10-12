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
    /** Whether this build has the native lab tools. */
    val available: Boolean

    fun describe(uri: Uri): TvLabFile?

    suspend fun probe(uri: Uri): MediaTech?

    /** The bitrate analysis, or its error's text. */
    suspend fun bitrate(uri: Uri, progress: (Float) -> Unit): Result<BitrateAnalysis>

    fun drive(uri: Uri): DriveInfo?

    /** The drive's sequential read, from a part of the film [sizeBytes] long. */
    suspend fun diskSpeed(uri: Uri, sizeBytes: Long, progress: (Float, Double) -> Unit): Result<DiskSpeed>

    fun cancel()
}

/** The device's: the media probe through the library's cache, the native bitrate and disk tests. */
class NativeTvLabEngine(private val context: Context) : TvLabEngine {
    private val cacheDir = File(context.filesDir, "tvlab/bitrate")

    override val available: Boolean get() = NativeLab.available

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

    override fun cancel() = NativeLab.cancel()

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
 * Lives as long as the app does, so that a long analysis goes on while other screens show.
 */
@Stable
class TvLabController(
    private val engine: TvLabEngine,
    private val profiles: ProfileStore,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val available: Boolean get() = engine.available

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

    var profileList by mutableStateOf(profiles.all())
        private set
    var profile by mutableStateOf(profileList.firstOrNull { it.id == profiles.selected } ?: profileList.first())
        private set

    private var job: Job? = null

    /**
     * Analyses the film at [uri]: its streams, its drive, then its bitrate. What ran for the
     * film before stops; the job is returned for the tests.
     */
    fun open(uri: Uri): Job? {
        job?.cancel()
        engine.cancel()
        val described = engine.describe(uri)
        file = described ?: TvLabFile(uri.toString(), uri.lastPathSegment.orEmpty(), 0)
        tech = null
        bitrate = null
        disk = null
        drive = null
        elapsedMs = null
        bitrateStep = Step.Idle
        diskStep = Step.Idle
        if (!engine.available) {
            probe = Step.Failed(NO_TOOLS)
            return null
        }
        probe = Step.Running(null)
        val started = now()
        return scope.launch {
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
        engine.cancel()
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
        profiles.save(edited)
        profileList = profiles.all()
        choose(edited.id)
    }

    /** A user's profile removed, an app's back to as it came; the chosen one stays if it can. */
    fun removeProfile(id: String) {
        profiles.remove(id)
        profileList = profiles.all()
        profile = profileList.firstOrNull { it.id == profile.id } ?: profileList.first()
        profiles.selected = profile.id
    }

    /** An id for a new profile named [name]. */
    fun newId(name: String): String = FileProfileStore.newId(name, profileList.map { it.id })

    /** Whether the profile [id] is one the user saved: theirs, or an app's they changed. */
    fun saved(id: String): Boolean = profiles.saved(id)

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

    fun get(context: Context): TvLabController = controller ?: synchronized(this) {
        controller ?: TvLabController(
            NativeTvLabEngine(context.applicationContext),
            FileProfileStore(File(context.applicationContext.filesDir, "tvlab/profiles"), Settings.getInstance(context.applicationContext)),
            CoroutineScope(SupervisorJob() + Dispatchers.Main),
        ).also { controller = it }
    }
}
