package app.nagmedar.ui.tvlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S-1204: a drive's file system from the mount table, through Android's own layers. */
class DriveInspectorTest {

    /** A Galaxy tablet with an exFAT T7: the kernel's mount under vold's FUSE layer. */
    private val galaxy = listOf(
        "/dev/block/dm-48 /data f2fs rw,lazytime,seclabel,nosuid,nodev,noatime 0 0",
        "/dev/fuse /storage/emulated fuse rw,lazytime,nosuid,nodev,noexec,noatime 0 0",
        "/dev/block/vold/public:8,1 /mnt/media_rw/1234-ABCD exfat rw,dirsync,nosuid,nodev,noexec,noatime 0 0",
        "/dev/fuse /mnt/user/0/1234-ABCD fuse rw,lazytime,nosuid,nodev,noexec,noatime 0 0",
        "/dev/fuse /storage/1234-ABCD fuse rw,lazytime,nosuid,nodev,noexec,noatime 0 0",
    )

    @Test
    fun theDrivesOwnFileSystem() {
        assertEquals("exFAT", DriveInspector.filesystemOf("1234-ABCD", "/storage/1234-ABCD", galaxy))
        // Samsung's own exFAT and FAT drivers, the kernel's
        assertEquals("exFAT", DriveInspector.filesystemOf("1234-ABCD", null, listOf("/dev/block/vold/public:8,1 /mnt/media_rw/1234-ABCD sdfat rw 0 0")))
        assertEquals("FAT32", DriveInspector.filesystemOf("1234-ABCD", null, listOf("/dev/block/vold/public:8,1 /mnt/media_rw/1234-ABCD vfat rw 0 0")))
        assertEquals("NTFS", DriveInspector.filesystemOf("0123456789ABCDEF", null, listOf("/dev/block/vold/public:8,1 /mnt/media_rw/0123456789ABCDEF ntfs3 rw 0 0")))
        // ntfs-3g runs in user space: the kernel calls it fuseblk
        assertEquals("NTFS", DriveInspector.filesystemOf("0123456789ABCDEF", null, listOf("/dev/fuse /mnt/media_rw/0123456789ABCDEF fuseblk rw 0 0")))
        assertEquals("ext4", DriveInspector.filesystemOf("5f0c-uuid", null, listOf("/dev/block/vold/public:8,1 /mnt/media_rw/5f0c-uuid ext4 rw 0 0")))
    }

    @Test
    fun withoutTheMountTableTheIdTells() {
        // Android 11 and later hide /mnt/media_rw from apps: only the FUSE layer is listed
        val hidden = galaxy.filterNot { "media_rw" in it }
        assertEquals("FAT32/exFAT", DriveInspector.filesystemOf("1234-ABCD", "/storage/1234-ABCD", hidden))
        assertEquals("NTFS", DriveInspector.filesystemOf("0123456789ABCDEF", "/storage/0123456789ABCDEF", emptyList()))
        assertNull(DriveInspector.filesystemOf("not-an-id", "/storage/not-an-id", emptyList()))
        // the internal storage: no id, only layers
        assertNull(DriveInspector.filesystemOf(null, "/storage/emulated/0", galaxy))
    }
}
