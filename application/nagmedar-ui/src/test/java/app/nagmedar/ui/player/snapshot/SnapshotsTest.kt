package app.nagmedar.ui.player.snapshot

import android.content.Context
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.library.TechSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S-606: a screenshot's name, folder and settings; which films are HDR and how; where the picture
 * and the subtitles go in it.
 */
@RunWith(AndroidJUnit4::class)
class SnapshotsTest {

    @Test
    fun theFileIsNamedAfterTheFilmAndTheTimeInIt() {
        assertEquals("Kuzey Işığı 01-11-45.png", snapshotFileName("Kuzey Işığı", 4_305_400L, SnapshotFormat.Png, "Nağmedar"))
        assertEquals("Mavi Kanal 00-00-07.webp", snapshotFileName("Mavi Kanal", 7_999L, SnapshotFormat.WebP, "Nağmedar"))
        // what FAT and exFAT drives refuse
        assertEquals("Ne_ Nerede_ _1_2_ 00-01-00.png", snapshotFileName("Ne? Nerede: <1/2>", 60_000L, SnapshotFormat.Png, "Nağmedar"))
        // no title, or one made of dots
        assertEquals("Nağmedar 00-00-00.png", snapshotFileName("  ", 0L, SnapshotFormat.Png, "Nağmedar"))
        assertEquals("Nağmedar 00-00-00.png", snapshotFileName("...", -5L, SnapshotFormat.Png, "Nağmedar"))
        // a long title is cut, the time stays
        val long = snapshotFileName("A".repeat(300), 3_600_000L, SnapshotFormat.Png, "Nağmedar")
        assertEquals(96 + " 01-00-00.png".length, long.length)
        // films longer than a day keep counting hours
        assertEquals("Uzun 25-00-00.png", snapshotFileName("Uzun", 90_000_000L, SnapshotFormat.Png, "Nağmedar"))
    }

    @Test
    fun theFolderIsNamedAsAFileManagerNamesIt() {
        val default = defaultSnapshotFolder("Nağmedar")
        assertEquals("Pictures/Nağmedar", default)
        assertEquals(default, snapshotFolderLabel(null, default))
        assertEquals(
            "Pictures/Screens",
            snapshotFolderLabel("content://com.android.externalstorage.documents/tree/primary%3APictures%2FScreens", default),
        )
        assertEquals("1234-ABCD", snapshotFolderLabel("content://com.android.externalstorage.documents/tree/1234-ABCD%3A", default))
        assertEquals("Shots", snapshotFolderLabel("content://com.android.externalstorage.documents/tree/1234-ABCD%3AShots", default))
    }

    @Test
    fun theSettingsAreKept() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("snapshots", Context.MODE_PRIVATE)
        assertEquals(SnapshotSettings(), SnapshotSettings.read(prefs))
        val chosen = SnapshotSettings(SnapshotFormat.WebP, subtitles = false, hdr = HdrSnapshot.Keep, folder = "content://tree/x")
        chosen.write(prefs.edit()).commit()
        assertEquals(chosen, SnapshotSettings.read(prefs))
        // back to the default folder
        chosen.copy(folder = null).write(prefs.edit()).commit()
        assertNull(SnapshotSettings.read(prefs).folder)
        // what a later version might write is read as the default
        prefs.edit().putString(SnapshotSettings.KEY_FORMAT, "Avif").putString(SnapshotSettings.KEY_HDR, "Ultra").commit()
        assertEquals(SnapshotFormat.Png, SnapshotSettings.read(prefs).format)
        assertEquals(HdrSnapshot.ToneMap, SnapshotSettings.read(prefs).hdr)
    }

    @Test
    fun hdrFilmsByTheirTransfer() {
        assertNull(hdrTransferOf(null))
        assertNull(hdrTransferOf(TechSamples.maviKanal.video))
        assertEquals(HdrTransfer.Pq, hdrTransferOf(TechSamples.sessizLiman.video))
        assertEquals(HdrTransfer.Pq, hdrTransferOf(TechSamples.kirikPusula.video))
        // Dolby Vision shows its HDR10 base layer, profile 5 its own PQ
        assertEquals(HdrTransfer.Pq, hdrTransferOf(TechSamples.kuzeyIsigi.video))
        assertEquals(HdrTransfer.Pq, hdrTransferOf(TechSamples.camSehir.video))
        assertEquals(HdrTransfer.Hlg, hdrTransferOf(TechSamples.video(transfer = "arib-std-b67", maxLuminance = 0.0)))
        assertEquals(HdrTransfer.Hlg, hdrTransferOf(TechSamples.video(dvProfile = 8, dvCompatibility = 4, transfer = "arib-std-b67")))
        // an SDR base layer of Dolby Vision (8.2)
        assertNull(hdrTransferOf(TechSamples.video(dvProfile = 8, dvCompatibility = 2, transfer = "bt709", maxLuminance = 0.0)))
    }

    @Test
    fun aPictureAloneKeepsItsOwnSize() {
        // a 4K film in a 1280 dp window, letterboxed
        val picture = IntRect(0, 140, 1280, 860)
        val layout = snapshotLayout(picture, IntSize(3840, 2160), subtitlesView = null, subtitleContent = null)
        assertEquals(SnapshotLayout(3840, 2160, IntRect(0, 0, 3840, 2160), null), layout)
    }

    @Test
    fun subtitlesOverThePictureScaleWithIt() {
        val picture = IntRect(0, 140, 1280, 860)
        val subtitles = IntRect(0, 0, 1280, 1000)
        val layout = snapshotLayout(picture, IntSize(3840, 2160), subtitles, IntRect(400, 780, 880, 840))
        assertEquals(3840, layout.width)
        assertEquals(2160, layout.height)
        assertEquals(IntRect(0, 0, 3840, 2160), layout.video)
        assertEquals(IntRect(0, -420, 3840, 2580), layout.subtitles)
    }

    @Test
    fun subtitlesInTheBarsWidenThePicture() {
        val picture = IntRect(0, 140, 1280, 860)
        val subtitles = IntRect(0, 0, 1280, 1000)
        // under the picture, in the black bar
        val layout = snapshotLayout(picture, IntSize(3840, 2160), subtitles, IntRect(400, 900, 880, 960))
        assertEquals(3840, layout.width)
        assertEquals(2460, layout.height)
        assertEquals(IntRect(0, 0, 3840, 2160), layout.video)
        assertEquals(IntRect(0, -420, 3840, 2580), layout.subtitles)
    }

    @Test
    fun aPictureFitsItsView() {
        assertEquals(IntRect(0, 140, 1280, 860), fitPicture(IntSize(1280, 1000), 16 / 9.0))
        assertEquals(IntRect(160, 0, 1120, 720), fitPicture(IntSize(1280, 720), 4 / 3.0))
        assertEquals(IntRect(0, 0, 1280, 720), fitPicture(IntSize(1280, 720), 0.0))
    }

    @Test
    fun theDrawnPartOfALayer() {
        val clear = IntArray(6 * 4)
        assertNull(drawnBounds(clear, 6, 4))
        val drawn = clear.copyOf().apply {
            this[1 * 6 + 2] = 0xFF_FFFFFF.toInt()
            this[2 * 6 + 4] = 0x40_000000
        }
        assertEquals(IntRect(2, 1, 5, 3), drawnBounds(drawn, 6, 4))
    }

    @Test
    fun theDisplaySizeOfAPicture() {
        assertEquals(IntSize(3840, 2160), displaySize(3840, 2160, 1, 1, turned = false))
        // a 16:9 PAL DVD
        assertEquals(IntSize(1024, 576), displaySize(720, 576, 64, 45, turned = false))
        // a phone's video, filmed upright
        assertEquals(IntSize(1080, 1920), displaySize(1920, 1080, 0, 0, turned = true))
    }
}
