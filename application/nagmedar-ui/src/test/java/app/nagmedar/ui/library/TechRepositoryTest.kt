package app.nagmedar.ui.library

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.MediaTechEntry
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.lab.MediaProbe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * S-802: a cached probe describes the file as it was probed; a file replaced in place (another
 * size or modification time) is not answered with it: its tracks may be others (the embedded
 * subtitles' extraction, the TV Lab and the Dolby Vision decoding ask by index).
 */
@RunWith(AndroidJUnit4::class)
class TechRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: NagmedarDatabase
    private lateinit var repository: TechRepository
    private lateinit var film: File
    private lateinit var key: String

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, NagmedarDatabase::class.java).allowMainThreadQueries().build()
        repository = TechRepository(context, db.mediaTech())
        film = File(context.cacheDir, "kuzey.mkv").apply { writeBytes(ByteArray(1_000)) }
        film.setLastModified(1_700_000_000_000)
        key = Uri.fromFile(film).toString()
        db.mediaTech().save(MediaTechEntry(key, film.length(), film.lastModified(), MediaProbe.VERSION, PROBE, 1))
    }

    @After
    fun tearDown() {
        db.close()
        film.delete()
    }

    @Test
    fun theProbeOfTheFileAsItWas() = runBlocking {
        assertEquals("tur", repository.cached(key)?.subtitles?.single()?.language)
        assertNotNull(repository.probeNow(key))
    }

    @Test
    fun aFileReplacedInPlaceIsNotAnsweredWithTheOldProbe() = runBlocking {
        film.writeBytes(ByteArray(2_000))
        film.setLastModified(1_700_000_100_000)
        assertNull(repository.cached(key))
        // without the native library here it cannot be probed again: no answer rather than the old one
        assertNull(repository.probeNow(key))
    }

    @Test
    fun theSameSizeAtAnotherTimeIsAnotherFile() = runBlocking {
        film.setLastModified(1_700_000_200_000)
        assertNull(repository.probeNow(key))
    }

    @Test
    fun anOlderProbesResultIsNotUsed() = runBlocking {
        db.mediaTech().save(MediaTechEntry(key, film.length(), film.lastModified(), MediaProbe.VERSION - 1, PROBE, 1))
        assertNull(repository.cached(key))
    }

    private companion object {
        val PROBE = """{"version":${MediaProbe.VERSION},"error":"","format":"matroska,webm","brand":"","duration_ms":11029,""" +
            """"size":1000,"bitrate":0,"chapters":0,"audio":[],"subtitles":[{"codec":"subrip","language":"tur","title":"",""" +
            """"default":false,"forced":false,"hearing_impaired":false}]}"""
    }
}
