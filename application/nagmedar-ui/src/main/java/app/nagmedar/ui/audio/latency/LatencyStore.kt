package app.nagmedar.ui.audio.latency

import android.content.Context
import android.media.AudioDeviceInfo
import android.os.Build
import app.nagmedar.audio.latency.LatencyProfile
import app.nagmedar.audio.latency.LatencyProfiles
import app.nagmedar.audio.latency.OutputDeviceKey
import app.nagmedar.data.LatencyProfileDao
import app.nagmedar.data.LatencyProfileEntry
import app.nagmedar.data.NagmedarDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** S-613: where the latency profiles are kept; the Room one in the app, a list in the tests. */
interface LatencyProfileStore {
    /** Every device seen with its profile, in the list's order. */
    fun observe(): Flow<List<LatencyProfile>>

    suspend fun save(profile: LatencyProfile)

    /** [device] has a row from now on, at 0 unless it has one. */
    suspend fun seen(device: OutputDeviceKey)

    suspend fun delete(device: OutputDeviceKey)
}

/** S-613: the profiles in Nağmedar's database (ADR-08: per-device data in Room). */
class RoomLatencyStore(context: Context) : LatencyProfileStore {
    private val dao: LatencyProfileDao = NagmedarDatabase.get(context).latencyProfiles()

    override fun observe(): Flow<List<LatencyProfile>> = dao.observe().map { rows -> rows.map(LatencyCodec::decode) }

    override suspend fun save(profile: LatencyProfile) = withContext(Dispatchers.IO) { dao.save(LatencyCodec.encode(profile)) }

    override suspend fun seen(device: OutputDeviceKey) = withContext(Dispatchers.IO) { dao.seen(LatencyCodec.encode(LatencyProfile(device, 0))) }

    override suspend fun delete(device: OutputDeviceKey) = withContext(Dispatchers.IO) { dao.delete(device.type, device.name, device.address) }
}

/** S-613: the profiles as a list, for the tests and the screenshots. */
class MemoryLatencyStore(initial: List<LatencyProfile> = emptyList()) : LatencyProfileStore {
    val profiles = MutableStateFlow(initial)

    override fun observe(): Flow<List<LatencyProfile>> = profiles

    override suspend fun save(profile: LatencyProfile) = profiles.update { list -> list.filterNot { it.device == profile.device } + profile }

    override suspend fun seen(device: OutputDeviceKey) = profiles.update { list -> if (list.any { it.device == device }) list else list + LatencyProfile(device, 0) }

    override suspend fun delete(device: OutputDeviceKey) = profiles.update { list -> list.filterNot { it.device == device } }
}

/** S-613: a profile as its database row, and back. */
object LatencyCodec {
    fun encode(profile: LatencyProfile) = LatencyProfileEntry(
        deviceType = profile.device.type,
        deviceName = profile.device.name,
        deviceAddress = profile.device.address,
        delayMs = profile.delayMs,
        measured = profile.measured,
        spreadMs = profile.spreadMs,
        updatedAt = profile.updatedAt,
        calibrationMs = profile.calibrationMs,
    )

    fun decode(row: LatencyProfileEntry) = LatencyProfile(
        device = OutputDeviceKey(row.deviceType, row.deviceName, row.deviceAddress),
        delayMs = row.delayMs.coerceIn(LatencyProfiles.MIN_MS, LatencyProfiles.MAX_MS),
        measured = row.measured,
        spreadMs = row.spreadMs,
        updatedAt = row.updatedAt,
        calibrationMs = row.calibrationMs,
    )
}

/** S-613: [device] as a profile knows it: its type and product name, and its address where that stays the same. */
fun OutputDeviceKey.Companion.of(device: AudioDeviceInfo): OutputDeviceKey = OutputDeviceKey(
    type = device.type,
    name = device.productName?.toString()?.trim().orEmpty(),
    address = if (Build.VERSION.SDK_INT >= 28 && LatencyProfiles.stableAddress(device.type)) device.address.orEmpty() else "",
)
