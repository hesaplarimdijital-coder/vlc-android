package app.nagmedar.ui.components

import android.net.Uri
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.1: the dense table's own keys for its lazy list. A key a Bundle cannot hold (a plain data
 * class) or a key used twice ended the app as the rows composed; the table now gives the list keys
 * it can keep, one each, while what is selected still goes by the caller's keys.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class DenseTableKeysTest {

    private data class Place(val path: String)

    private enum class Shelf { Recent, Liked }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun keysABundleHoldsStayAsTheyAre() {
        val uri = Uri.parse("file:///storage/emulated/0/Music")
        val keys = listOf<Any>("a", 7, 9_000_000_000L, Shelf.Liked, uri, true, 'x')
        val lazy = lazyKeys(keys)
        assertEquals(keys, lazy)
        assertSame(uri, lazy[4])
    }

    @Test
    fun anotherKeyBecomesItsTextTheSameEachTime() {
        val lazy = lazyKeys(listOf(Place("/storage/emulated/0/Music"), Place("/storage/emulated/0/Podcasts")))
        assertEquals(listOf("\u0001Place(path=/storage/emulated/0/Music)", "\u0001Place(path=/storage/emulated/0/Podcasts)"), lazy)
        assertEquals(lazy, lazyKeys(listOf(Place("/storage/emulated/0/Music"), Place("/storage/emulated/0/Podcasts"))))
    }

    @Test
    fun aKeyUsedAgainIsMadeUniqueInOrder() {
        assertEquals(listOf("a", "\u00022\u0002a", "b", "\u00023\u0002a"), lazyKeys(listOf("a", "a", "b", "a")))
        val places = lazyKeys(listOf(Place("/x"), Place("/x")))
        assertEquals(listOf("\u0001Place(path=/x)", "\u00022\u0002\u0001Place(path=/x)"), places)
        // the same number and its text are two keys already
        assertEquals(listOf(1, "1"), lazyKeys(listOf(1, "1")))
    }

    @Test
    fun aMadeKeyNeverMeetsACallersKey() {
        val lazy = lazyKeys(listOf("a", "\u00022\u0002a", "a"))
        assertEquals(3, lazy.toSet().size)
        assertEquals("\u00023\u0002a", lazy[2])
    }

    @Test
    fun everyKeyIsOneABundleHolds() {
        val lazy = lazyKeys(listOf(Place("/x"), Place("/x"), "a", "a", 3, 3, Shelf.Recent, Shelf.Recent))
        assertEquals(lazy.size, lazy.toSet().size)
        assertTrue(lazy.all { it is String || it is Number || it is Enum<*> || it is Parcelable })
    }

    private val rows = listOf(Place("/Music"), Place("/Podcasts"), Place("/Music"))

    @Test
    fun aTableOfDataClassKeysAndARepeatedOneShowsAndSelectsByTheCallersKey() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                DenseTable(
                    columns = listOf(TableColumn<Place>("Ad", ColumnWidth.Weight(1f)) { it.path }, TableColumn<Place>("Tür", ColumnWidth.Fixed(80.dp)) { "klasör" }),
                    rows = rows,
                    key = { it },
                    selectedKey = Place("/Podcasts"),
                    onRowClick = { _, _ -> },
                )
            }
        }
        compose.onNodeWithText("/Podcasts").assertIsSelected()
        assertEquals(2, compose.onAllNodesWithText("/Music").fetchSemanticsNodes().size)
        compose.onAllNodesWithText("/Music")[0].assertIsNotSelected()
        // the window saves its state and restores it: the rows' keys go into it
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("/Podcasts").assertIsSelected()
        assertNotEquals(0, compose.onAllNodesWithText("/Music").fetchSemanticsNodes().size)
    }
}
