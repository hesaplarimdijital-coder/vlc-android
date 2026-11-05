package app.nagmedar.ui.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.videolan.vlc.media.MediaSessionBrowser
import java.io.File

/**
 * 1.0.3: Android Auto's root is the same in every build. In 1.0–1.0.2 it was a constant built from the build type's
 * application id, inlined by each module as it was compiled: the personal APK's catalog (nagmedar-ui has no personal
 * build type) made ids under "//app.nagmedar/r" while the service gave Auto "//app.nagmedar.debug/r", so Auto showed
 * no tabs, only its search.
 */
class CarRootTest {

    @Test
    fun theRootNamesNoBuildType() {
        assertEquals("//app.nagmedar/r", MediaSessionBrowser.ID_ROOT)
        assertEquals(MediaSessionBrowser.ID_ROOT, CarIds.ROOT)
        assertEquals("${CarIds.ROOT}/music", CarIds.MUSIC)
        assertEquals(MediaSessionBrowser.ID_NO_MEDIA, CarIds.NOTHING)
    }

    @Test
    fun idsOfEarlierBuildsAreReadAsToday() {
        assertEquals(CarIds.ROOT, MediaSessionBrowser.canonical("//app.nagmedar.debug/r"))
        assertEquals(CarIds.ALBUMS, MediaSessionBrowser.canonical("//app.nagmedar.debug/r/music/albums"))
        assertEquals(CarIds.episode(7), MediaSessionBrowser.canonical("//app.nagmedar.debug/r/podcasts/episode/7"))
        assertEquals("${MediaSessionBrowser.ID_MEDIA}/5", MediaSessionBrowser.canonical("//app.nagmedar.benchmark/r/media/5"))
        assertEquals(CarIds.page(CarIds.ALBUMS, 2), MediaSessionBrowser.canonical("//app.nagmedar.debug/r/music/albums?p=2"))
        // the root the personal build gave Auto now opens the tree
        assertNotNull(CarIds.parse(MediaSessionBrowser.canonical("//app.nagmedar.debug/r")))
        assertTrue(MediaSessionBrowser.isNoMedia("//app.nagmedar.debug/r/error/media?t=Empty"))
    }

    @Test
    fun otherIdsAreLeftAsTheyAre() {
        assertEquals(CarIds.ALBUMS, MediaSessionBrowser.canonical(CarIds.ALBUMS))
        assertEquals("//app.nagmedar.debugger/r/x", MediaSessionBrowser.canonical("//app.nagmedar.debugger/r/x"))
        assertEquals("//other.app/r/music", MediaSessionBrowser.canonical("//other.app/r/music"))
        assertEquals("", MediaSessionBrowser.canonical(""))
    }

    /**
     * No public constant of the modules the others compile against is built from the application id: a module without
     * the build type (nagmedar-ui, television) would inline another build's value, as Auto's root once was.
     */
    @Test
    fun noSharedConstantCarriesTheBuildTypesApplicationId() {
        val roots = listOf("../vlc-android/src", "../resources/src", "../tools/src").map(::File).filter { it.isDirectory }
        assertTrue("the shared modules' sources are found from ${File(".").absolutePath}", roots.isNotEmpty())
        val offenders = roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> CONST_FROM_APP_ID.containsMatchIn(line) && !line.trimStart().startsWith("private") }
                    .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
            }
        }
        assertTrue("constants built from BuildConfig.APP_ID:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    private companion object {
        val CONST_FROM_APP_ID = Regex("""\bconst\s+val\b.*BuildConfig\.APP_ID""")
    }
}
