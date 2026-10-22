package app.nagmedar.ui.tvlab

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import androidx.compose.runtime.Immutable
import java.io.File
import java.util.Locale

/**
 * The drive a film is on (S-1204): its name ("SAMSUNG T7"), its file system ("exFAT"), its size
 * and free space, whether it is removable, and, for a USB drive, the USB version it says it
 * speaks ("3.20": what it can do, not the port it is in, which its speed tells).
 */
@Immutable
data class DriveInfo(
    val name: String?,
    val filesystem: String?,
    val totalBytes: Long?,
    val freeBytes: Long?,
    val removable: Boolean,
    val usbVersion: String? = null,
)

/** Finds the drive behind a film's URI: a file path, or a document of the external storage provider. */
object DriveInspector {

    fun inspect(context: Context, uri: Uri): DriveInfo? = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        val storage = context.getSystemService(StorageManager::class.java) ?: return null
        val volume = volumeOf(storage, uri) ?: return null
        val directory = directoryOf(volume)
        val stat = directory?.let { runCatching { StatFs(it.path) }.getOrNull() }
        DriveInfo(
            name = volume.getDescription(context),
            filesystem = filesystemOf(volume.uuid, directory?.path, mounts()),
            totalBytes = stat?.totalBytes,
            freeBytes = stat?.availableBytes,
            removable = volume.isRemovable,
            usbVersion = if (volume.isRemovable) usbStorageVersion(context) else null,
        )
    }.getOrNull()

    private fun volumeOf(storage: StorageManager, uri: Uri): StorageVolume? {
        if (uri.scheme == null || uri.scheme == "file") return uri.path?.let { storage.getStorageVolume(File(it)) }
        if (uri.scheme != "content") return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // a media store URI names its volume
            runCatching { storage.getStorageVolume(uri) }.getOrNull()?.let { return it }
        }
        // the external storage provider: "XXXX-XXXX:Filmler/film.mkv", "primary:Movies/film.mkv"
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val volumeId = id.substringBefore(':', "").ifEmpty { return null }
        return storage.storageVolumes.firstOrNull { volume ->
            if (volumeId == "primary") volume.isPrimary else volume.uuid.equals(volumeId, ignoreCase = true)
        }
    }

    private fun directoryOf(volume: StorageVolume): File? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> volume.directory
        volume.isPrimary -> File("/storage/emulated/0")
        volume.uuid != null -> File("/storage/${volume.uuid}")
        else -> null
    }

    private fun mounts(): List<String> = runCatching { File("/proc/self/mounts").readLines() }.getOrDefault(emptyList())

    /**
     * The file system of the volume [uuid] mounted at [directory], from the mount table ([mounts],
     * /proc/self/mounts' lines); else what its id tells: FAT and exFAT drives have "XXXX-XXXX",
     * NTFS ones sixteen hexadecimal digits. Android's own FUSE layer is looked through.
     */
    fun filesystemOf(uuid: String?, directory: String?, mounts: List<String>): String? {
        val entries = mounts.mapNotNull { line ->
            val fields = line.split(' ')
            if (fields.size < 3) null else fields[1] to fields[2]
        }
        val own = entries.filter { (point, type) ->
            type !in LAYERS && ((uuid != null && point.contains(uuid, ignoreCase = true)) || (directory != null && point == directory))
        }
        own.firstNotNullOfOrNull { (_, type) -> NAMES[type.lowercase(Locale.ROOT)] }?.let { return it }
        return when {
            uuid == null -> null
            Regex("^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$").matches(uuid) -> "FAT32/exFAT"
            Regex("^[0-9A-Fa-f]{16}$").matches(uuid) -> "NTFS"
            else -> null
        }
    }

    /** The USB version of the one USB mass storage device plugged in; null for none or several. */
    private fun usbStorageVersion(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val usb = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
        val drives = usb.deviceList.values.filter { device ->
            (0 until device.interfaceCount).any { device.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE }
        }
        return drives.singleOrNull()?.version
    }

    /** Layers over the drive's own file system: Android's FUSE, sdcardfs. */
    private val LAYERS = setOf("fuse", "sdcardfs", "esdfs")

    private val NAMES = mapOf(
        "vfat" to "FAT32",
        "msdos" to "FAT",
        "exfat" to "exFAT",
        "texfat" to "exFAT",
        "sdfat" to "exFAT",
        "ntfs" to "NTFS",
        "ntfs3" to "NTFS",
        "tntfs" to "NTFS",
        "ufsd" to "NTFS",
        "fuseblk" to "NTFS",
        "ext4" to "ext4",
        "f2fs" to "F2FS",
        "btrfs" to "Btrfs",
    )
}
