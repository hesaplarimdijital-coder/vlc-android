package app.nagmedar.ui.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which files the music shows by its folders (Settings › Library): without folders everything but
 * a folder left out, with folders only what is under them.
 */
class LibraryScopesTest {

    private val root = "/storage/emulated/0"

    private fun rules(vararg paths: String) = ScopeRules(
        paths.fold(LibraryScopes.Empty) { scopes, path -> scopes.plus(LibrarySection.Music, SourceFolder(path.removePrefix("-"), exclude = path.startsWith("-"))) },
    ) { Locations.ofPath(it) }

    private fun shows(rules: ScopeRules, path: String?) = rules.inScope(LibrarySection.Music, path?.let { Locations.ofPath("$root/$it") })

    @Test
    fun withoutFoldersEverythingButAFolderLeftOut() {
        val all = rules()
        assertTrue(shows(all, "Music/Ada Karan/Sabaha Kadar.flac"))
        assertTrue(shows(all, null))
        val withoutRecordings = rules("-$root/Recordings")
        assertTrue(shows(withoutRecordings, "Music/Ada Karan/Sabaha Kadar.flac"))
        assertFalse(shows(withoutRecordings, "Recordings/Toplantı.m4a"))
    }

    @Test
    fun withFoldersOnlyWhatIsUnderThem() {
        val music = rules("$root/Music", "-$root/Music/Sesli kitaplar")
        assertTrue(shows(music, "Music/Ada Karan/Sabaha Kadar.flac"))
        // the case is not the file system's on shared storage
        assertTrue(shows(music, "music/Kadri Erdem/Neva Kâr.flac"))
        assertFalse(shows(music, "Music/Sesli kitaplar/Bölüm 1.mp3"))
        assertFalse(shows(music, "Download/Gece Yolcusu.mp3"))
        // a file of no known place only by a section without folders
        assertFalse(shows(music, null))
    }
}
