package app.nagmedar.ui.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * "Dosyada göster": the browser opens a file's folder by the way it would take itself, from the
 * drive that holds it; a document's way is read from the external storage's ids when its
 * provider cannot tell.
 */
class DeviceRevealTest {

    private val internal = DeviceRoot("", RootKind.Internal, DeviceLocation.Path("/storage/emulated/0"))
    private val card = DeviceRoot("SD kart", RootKind.Removable, DeviceLocation.Path("/storage/1A2B-3C4D/"))

    @Test
    fun aFileIsShownFromTheDriveThatHoldsIt() {
        val reveal = pathReveal("/storage/emulated/0/Movies/Filmler/Kuzey Işığı.mkv", listOf(card, internal), "Dahili depolama")!!
        assertEquals(listOf("Dahili depolama", "Movies", "Filmler"), reveal.trail.map { it.name })
        assertEquals(
            listOf(internal.location, DeviceLocation.Path("/storage/emulated/0/Movies"), DeviceLocation.Path("/storage/emulated/0/Movies/Filmler")),
            reveal.trail.map { it.location },
        )
        assertEquals(DeviceLocation.Path("/storage/emulated/0/Movies/Filmler/Kuzey Işığı.mkv"), reveal.file)
        // at a drive's top, the drive alone, by its name
        val top = pathReveal("/storage/1A2B-3C4D/Sabaha Kadar.flac", listOf(internal, card), "Dahili depolama")!!
        assertEquals(listOf("SD kart"), top.trail.map { it.name })
        assertEquals(DeviceLocation.Path("/storage/1A2B-3C4D/Sabaha Kadar.flac"), top.file)
    }

    @Test
    fun aFileOnNoDriveIsNotInTheBrowser() {
        // the app's own storage
        assertNull(pathReveal("/data/user/0/app.nagmedar/files/bolum-42.mp3", listOf(internal, card), "Dahili depolama"))
        // a drive whose path starts another's name does not hold it
        assertNull(pathReveal("/storage/emulated/01/Kuzey.mkv", listOf(internal), "Dahili depolama"))
        // a folder picked with the system picker is no drive
        val picked = DeviceRoot("Filmler", RootKind.Picked, DeviceLocation.Path("/storage/emulated/0/Movies"), tree = "content://tree")
        assertNull(pathReveal("/storage/emulated/0/Movies/Kuzey.mkv", listOf(picked), "Dahili depolama"))
    }

    @Test
    fun theExternalStoragesIdsTellTheWay() {
        assertEquals(
            listOf("primary:Filmler", "primary:Filmler/Yeni", "primary:Filmler/Yeni/Kuzey.mkv"),
            storagePath("primary:Filmler", "primary:Filmler/Yeni/Kuzey.mkv"),
        )
        assertEquals(listOf("primary:", "primary:Kuzey.mkv"), storagePath("primary:", "primary:Kuzey.mkv"))
        assertNull(storagePath("primary:Filmler", "primary:Diziler/Kuzey.mkv"))
        assertNull(storagePath("primary:Film", "primary:Filmler/Kuzey.mkv"))
        assertEquals("primary:Filmler", storageParent("primary:Filmler/Kuzey.mkv"))
        assertEquals("primary:", storageParent("primary:Kuzey.mkv"))
        assertNull(storageParent("primary:"))
        assertNull(storageParent("1234"))
    }
}
