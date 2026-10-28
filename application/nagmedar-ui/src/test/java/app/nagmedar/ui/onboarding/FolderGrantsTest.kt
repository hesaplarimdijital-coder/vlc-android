package app.nagmedar.ui.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.convert.ConvertOptions
import app.nagmedar.ui.library.LibrarySection
import app.nagmedar.ui.library.LibrarySources
import app.nagmedar.ui.player.record.KEY_RECORDING_FOLDER
import app.nagmedar.ui.player.snapshot.SnapshotSettings
import app.nagmedar.ui.player.subtitles.providers.SubtitleFolderStore
import app.nagmedar.ui.podcast.PodcastSettingsStore
import app.nagmedar.ui.radio.record.RecordingPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.tools.NAGMEDAR_SAF_FOLDERS

/**
 * A persisted folder permission goes only as far as nothing in the app needs it: every holder of a picked folder is
 * read, and reading stays while any of them keeps the folder, writing while one that writes does.
 */
@RunWith(AndroidJUnit4::class)
class FolderGrantsTest {

    private fun tree(name: String) = "content://com.android.externalstorage.documents/tree/primary%3A$name"

    private val read = FolderGrants.READ
    private val write = FolderGrants.WRITE
    private val both = read or write

    @Test
    fun everyHolderOfTheSettingsIsRead() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("folder-grants-test", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putString(LibrarySources.key(LibrarySection.Movies), "+" + tree("Filmler"))
            .putStringSet(NAGMEDAR_SAF_FOLDERS, setOf(tree("Secilen")))
            .putString(SubtitleFolderStore.KEY, tree("Altyazilar"))
            .putString(ConvertOptions.KEY_FOLDER, tree("Donusturulen"))
            .putString(KEY_RECORDING_FOLDER, tree("Kayitlar"))
            .putString(SnapshotSettings.KEY_FOLDER, tree("Ekran"))
            .putString(RecordingPrefs.KEY_FOLDER, tree("Radyo"))
            .putString(PodcastSettingsStore.KEY_FOLDER, tree("Podcast"))
            .putStringSet(FolderGrants.keptKey(GrantHolder.Lyrics), setOf(tree("Sozler")))
            .commit()
        val uses = FolderGrants.preferenceUses(prefs)
        val expected = mapOf(
            GrantHolder.LibrarySections to "Filmler",
            GrantHolder.PickedFolders to "Secilen",
            GrantHolder.SubtitleFolder to "Altyazilar",
            GrantHolder.Converter to "Donusturulen",
            GrantHolder.VideoRecordings to "Kayitlar",
            GrantHolder.Screenshots to "Ekran",
            GrantHolder.RadioRecordings to "Radyo",
            GrantHolder.PodcastDownloads to "Podcast",
            GrantHolder.Lyrics to "Sozler",
        )
        assertEquals(expected.mapValues { setOf(tree(it.value)) }, uses)
        // the converter's unfinished jobs are read from their files, with the database's holders, in uses()
        assertEquals(GrantHolder.entries.toSet() - GrantHolder.ConvertJobs, uses.keys)
        // a podcast's default folder ("") holds nothing
        prefs.edit().putString(PodcastSettingsStore.KEY_FOLDER, "").commit()
        assertTrue(FolderGrants.preferenceUses(prefs).getValue(GrantHolder.PodcastDownloads).isEmpty())
    }

    @Test
    fun aFolderNobodyKeepsLetsGoOfBoth() {
        val uses = mapOf(GrantHolder.RadioRecordings to setOf(tree("Radyo")))
        assertEquals(both, FolderGrants.releasable(tree("Eski"), both, uses))
        assertEquals(read, FolderGrants.releasable(tree("Eski"), read, uses))
    }

    @Test
    fun aReaderKeepsReadingAndAWriterKeepsBoth() {
        val folder = tree("Ortak")
        // a library section reads it: only the writing goes
        assertEquals(write, FolderGrants.releasable(folder, both, mapOf(GrantHolder.LibrarySections to setOf(folder))))
        // the screenshots are written there: nothing goes, nor the S-309 folder's reading
        assertEquals(0, FolderGrants.releasable(folder, both, mapOf(GrantHolder.Screenshots to setOf(folder))))
        assertEquals(0, FolderGrants.releasable(folder, read, mapOf(GrantHolder.Screenshots to setOf(folder))))
        assertEquals(setOf(GrantHolder.LibrarySections, GrantHolder.Screenshots),
            FolderGrants.holdersOf(folder, mapOf(GrantHolder.LibrarySections to setOf(folder), GrantHolder.Screenshots to setOf(folder), GrantHolder.Lyrics to setOf(tree("Sozler")))))
    }

    @Test
    fun aFileInsideTheFolderKeepsIt() {
        val folder = tree("Podcast")
        val episode = "$folder/document/primary%3APodcast%2Fbolum-12.mp3"
        // the episodes downloaded there before the folder changed: their folder keeps its permission
        assertEquals(0, FolderGrants.releasable(folder, both, mapOf(GrantHolder.PodcastDownloads to setOf(episode))))
        // a radio recording kept in a folder of its own
        assertEquals(0, FolderGrants.releasable(tree("Kayit"), read, mapOf(GrantHolder.RadioRecordings to setOf("${tree("Kayit")}/document/primary%3AKayit%2Fyayin.mp3"))))
        // another folder that only starts with the same name is not inside it
        assertEquals(both, FolderGrants.releasable(tree("Pod"), both, mapOf(GrantHolder.PodcastDownloads to setOf(episode))))
    }
}
