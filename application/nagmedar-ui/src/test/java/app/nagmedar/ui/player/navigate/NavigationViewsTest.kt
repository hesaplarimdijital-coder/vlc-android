package app.nagmedar.ui.player.navigate

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.player.ChapterMark
import app.nagmedar.ui.player.FakePreviews
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal val sampleChapters = listOf(
    ChapterMark(0, "Açılış"),
    ChapterMark(1_200_000, "Kuzey"),
    ChapterMark(4_000_000, "Fırtına"),
    ChapterMark(7_460_000, "Şafak"),
)

internal val sampleBookmarks = listOf(
    BookmarkMark(1_385_000, "Gölde ilk ışık"),
    BookmarkMark(4_212_000, "Kuzey ışıkları sahnesi"),
    BookmarkMark(6_930_000, "Yer imi 3"),
)

/**
 * S-608: "Jump to time" goes where the typed time says and tells what it reads; a bookmark is
 * named in its dialog; the bookmarks tab goes to one, renames and removes it, and adds one; the
 * Info tab's chapters go to their starts.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class NavigationViewsTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun jump(now: Long = 4_305_000, length: Long = 8_290_000) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                JumpSurface(now, length, sampleChapters, onJump = { calls += "jump:$it" }, onDismiss = { calls += "close" })
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theTimePlayingIsThereToTypeOver() {
        jump()
        compose.onNodeWithTag("jump-field").assertTextEquals("1:11:45")
        compose.onNodeWithTag("jump-outcome").assertTextEquals("→ 1:11:45 · Bölüm 3 — Fırtına")
        compose.onNodeWithText("/ 2:18:10").assertExists()
        compose.onNodeWithTag("jump-field").performTextReplacement("1:12:33")
        compose.onNodeWithTag("jump-outcome").assertTextEquals("→ 1:12:33 · Bölüm 3 — Fırtına")
        compose.onNodeWithTag("jump-field").performImeAction()
        assertEquals(listOf("jump:4353000", "close"), calls)
    }

    @Test
    fun fromNowAndIntoAnotherChapter() {
        jump()
        compose.onNodeWithTag("jump-field").performTextReplacement("−1 sa")
        compose.onNodeWithTag("jump-outcome").assertTextEquals("→ 11:45 · Bölüm 1 — Açılış")
        compose.onNodeWithTag("jump-go").performClick()
        assertEquals(listOf("jump:705000", "close"), calls)
    }

    @Test
    fun whatCannotBeReadOrIsPastTheEndIsSaid() {
        jump()
        compose.onNodeWithTag("jump-field").performTextReplacement("3:00:00")
        compose.onNodeWithTag("jump-outcome").assertTextEquals("Film 2:18:10 sürüyor.")
        compose.onNodeWithTag("jump-go").assertIsNotEnabled()
        compose.onNodeWithTag("jump-field").performTextReplacement("yarım")
        compose.onNodeWithTag("jump-outcome").assertTextEquals("Bir zaman okunamadı: örneğin 1:12:33, 72:33 ya da +30 yazın.")
        compose.onNodeWithTag("jump-field").performImeAction()
        assertEquals(emptyList<String>(), calls)
        compose.onNodeWithTag("jump-field").performTextReplacement("%50")
        compose.onNodeWithTag("jump-go").assertIsEnabled().performClick()
        assertEquals(listOf("jump:4145000", "close"), calls)
    }

    @Test
    fun aBookmarkIsNamed() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                RenameSurface(sampleBookmarks[2], onRename = { calls += "name:$it" }, onDismiss = { calls += "close" })
            }
        }
        compose.onNodeWithTag("rename-field").assertTextEquals("Yer imi 3")
        compose.onNodeWithText("1:55:30").assertExists()
        compose.onNodeWithTag("rename-field").performTextReplacement("  ")
        compose.onNodeWithTag("rename-save").assertIsNotEnabled()
        compose.onNodeWithTag("rename-field").performTextReplacement("Şafak öncesi ")
        compose.onNodeWithTag("rename-field").performImeAction()
        assertEquals(listOf("name:Şafak öncesi", "close"), calls)
    }

    private fun bookmarks(marks: List<BookmarkMark>, refused: Boolean = false) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                BookmarksPanel(
                    marks = marks,
                    refused = refused,
                    previews = FakePreviews(),
                    onAdd = { calls += "add" },
                    onSeek = { calls += "seek:$it" },
                    onRename = { calls += "rename:${it.name}" },
                    onRemove = { calls += "remove:${it.name}" },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theBookmarksTabGoesToOneRenamesAndRemovesIt() {
        bookmarks(sampleBookmarks)
        compose.onNodeWithText("YER İMLERİ · 3").assertExists()
        assertEquals(3, compose.onAllNodesWithTag("bookmark-row").fetchSemanticsNodes().size)
        compose.onNodeWithText("Kuzey ışıkları sahnesi").performClick()
        compose.onNode(hasContentDescription("Gölde ilk ışık için daha fazla")).performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Yeniden adlandır…") and hasClickAction()).performClick()
        compose.waitForIdle()
        // a right click on a row shows the same
        compose.onAllNodesWithTag("bookmark-row").onFirst().performMouseInput {
            moveTo(center)
            press(MouseButton.Secondary)
            release(MouseButton.Secondary)
        }
        compose.waitForIdle()
        compose.onNode(hasText("Sil") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bookmarks-add").performClick()
        assertEquals(listOf("seek:4212000", "rename:Gölde ilk ışık", "remove:Gölde ilk ışık", "add"), calls)
    }

    @Test
    fun noBookmarksYetSaysHowToAddOne() {
        bookmarks(emptyList(), refused = true)
        compose.onNodeWithText("YER İMLERİ · 0").assertExists()
        compose.onNodeWithTag("bookmarks-empty").assertTextEquals("Henüz yer imi yok. Ctrl+D çalan anı işaretler.")
        compose.onNodeWithTag("bookmarks-refused").assertExists()
    }

    @Test
    fun theChaptersGoToTheirStarts() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                ChaptersSection(sampleChapters, nowMs = 4_305_000, previews = FakePreviews(), onSeek = { calls += "seek:$it" })
            }
        }
        compose.onNodeWithText("BÖLÜMLER · 4").assertExists()
        compose.onNodeWithText("Shift+N").assertExists()
        compose.onNodeWithText("Shift+P").assertExists()
        assertEquals(4, compose.onAllNodesWithTag("chapter-row").fetchSemanticsNodes().size)
        compose.onNodeWithText("2 · Kuzey").performClick()
        compose.onNodeWithText("4 · Şafak").performClick()
        assertEquals(listOf("seek:1200000", "seek:7460000"), calls)
    }
}
