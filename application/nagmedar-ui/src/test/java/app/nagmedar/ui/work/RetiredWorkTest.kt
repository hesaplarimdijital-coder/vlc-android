package app.nagmedar.ui.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.NagmedarDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Nağmedar 1.0: what the dropped tables and TV Lab left on an upgraded install goes at the next start, once. */
@RunWith(AndroidJUnit4::class)
class RetiredWorkTest {

    @Test
    fun theDroppedJobsNoteAndTvLabsFilesGoOnce() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val profiles = File(app.filesDir, "tvlab/profiles").apply { mkdirs() }
        File(profiles, "user-1.json").writeText("{}")
        File(app.filesDir, "tvlab/share").apply { mkdirs() }.resolve("chart.png").writeBytes(ByteArray(4))
        val note = NagmedarDatabase.retiredGrants(app).apply { writeText("content://docs/document/film.mkv\ncontent://docs/document/film%20(remux).mkv\n") }
        val kept = File(app.filesDir, "covers").apply { mkdirs() }.resolve("a.user").apply { writeBytes(ByteArray(4)) }

        RetiredWork.onProcessStart(app)
        assertFalse(File(app.filesDir, "tvlab").exists())
        assertFalse(note.exists())
        assertTrue("the app's other files stay", kept.exists())

        // once: what is there at a later start is left alone
        val later = File(app.filesDir, "tvlab").apply { mkdirs() }.resolve("later")
        later.writeText("")
        RetiredWork.onProcessStart(app)
        assertTrue(later.exists())
    }
}
