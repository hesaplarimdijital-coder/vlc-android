package app.nagmedar.ui.tags

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.tags.TagField
import app.nagmedar.ui.theme.NagmedarTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 1.2.0: the tag editor on screen: a field typed and saved, values one by one, several files, a tag added. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h900dp-mdpi")
class TagEditorScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val fake = FakeTagFiles(scope = scope)
    private val tags = fake.tags

    @After
    fun tearDown() {
        TagSessions.forget()
        scope.cancel()
    }

    private fun show(vararg names: String) {
        tags.put(sampleRef("01.flac"), "TITLE" to listOf("Bir Sana Bir Bana"), "ARTIST" to listOf("Moğollar"), "ALBUM" to listOf("Moğollar"), "MOOD" to listOf("Hüzünlü"))
        tags.put(sampleRef("02.flac"), "TITLE" to listOf("Garip"), "ARTIST" to listOf("Moğollar"), "ALBUM" to listOf("Moğollar"))
        compose.setContent {
            NagmedarTheme {
                CompositionLocalProvider(LocalTagFiles provides fake.files) {
                    TagEditorPane(names.map { sampleTarget(it) })
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aFieldTypedIsSavedAndTheFootSaysHowManyChangesWait() {
        show("01.flac")
        compose.onNodeWithTag("tag-head-title").assertTextContains("Bir Sana Bir Bana")
        compose.onNodeWithTag("tag-save").assertIsNotEnabled()
        compose.onNodeWithTag("tag-field-TITLE").performTextReplacement("Bir Sana Bir Bana (1971)")
        compose.onNodeWithTag("tag-count").assertTextContains("1 değişiklik")
        compose.onNodeWithTag("tag-save").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(listOf("Bir Sana Bir Bana (1971)"), tags.tags(sampleRef("01.flac"))["TITLE"])
        // the tag the editor has no name for stays
        assertEquals(listOf("Hüzünlü"), tags.tags(sampleRef("01.flac"))["MOOD"])
        compose.onNodeWithTag("tag-count").assertTextContains("Değişiklik yok")
    }

    @Test
    fun aValueTypedIntoAFieldOfSeveralIsOneOfThem() {
        show("01.flac")
        compose.onNodeWithTag("tag-input-ARTIST").performTextInput("Cahit Berkay")
        compose.onNodeWithTag("tag-input-ARTIST").performImeAction()
        compose.waitForIdle()
        compose.onNodeWithText("Cahit Berkay").assertExists()
        compose.onNodeWithTag("tag-save").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Moğollar", "Cahit Berkay"), tags.tags(sampleRef("01.flac"))[TagField.Artist.key])
    }

    @Test
    fun severalFilesTakeWhatIsSetForAll() {
        show("01.flac", "02.flac")
        compose.onNodeWithTag("tag-head-title").assertTextContains("2 parça")
        compose.onNodeWithTag("tag-field-ALBUM").performTextReplacement("Moğollar (1971)")
        compose.onNodeWithTag("tag-save").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Moğollar (1971)"), tags.tags(sampleRef("01.flac"))["ALBUM"])
        assertEquals(listOf("Moğollar (1971)"), tags.tags(sampleRef("02.flac"))["ALBUM"])
        assertEquals(listOf("Garip"), tags.tags(sampleRef("02.flac"))["TITLE"])
    }

    @Test
    fun aTagIsAddedByItsName() {
        show("01.flac")
        compose.onNodeWithTag("tag-add-field").performScrollTo().performClick()
        compose.onNodeWithTag("tag-add-key").performTextInput("rating mood")
        compose.onNodeWithTag("tag-add-value").performTextInput("5")
        compose.onNodeWithTag("tag-add-confirm").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("tag-save").performClick()
        compose.waitForIdle()
        assertEquals(listOf("5"), tags.tags(sampleRef("01.flac"))["RATING_MOOD"])
    }

    @Test
    fun cancelLeavesTheEditorWithChangesOrWithout() {
        var left = 0
        tags.put(sampleRef("01.flac"), "TITLE" to listOf("Bir Sana Bir Bana"))
        compose.setContent {
            NagmedarTheme {
                CompositionLocalProvider(LocalTagFiles provides fake.files) {
                    TagEditorPane(listOf(sampleTarget("01.flac")), onCancel = { left++ })
                }
            }
        }
        compose.waitForIdle()
        // 1.3.0 (the owner: "bir şey düzenlenmemişse vazgeç butonu çalışmıyor"): nothing typed, it leaves all the same
        compose.onNodeWithTag("tag-discard").assertIsEnabled().performClick()
        assertEquals(1, left)
        compose.onNodeWithTag("tag-field-TITLE").performTextReplacement("Başka")
        compose.onNodeWithTag("tag-discard").performClick()
        compose.waitForIdle()
        assertEquals(2, left)
        // what was typed is dropped, and nothing was written
        compose.onNodeWithTag("tag-count").assertTextContains("Değişiklik yok")
        assertEquals(listOf("Bir Sana Bir Bana"), tags.tags(sampleRef("01.flac"))["TITLE"])
    }

    @Test
    fun aSongsDetailsFoundAreChosenThenSavedWithTheRest() {
        tags.put(sampleRef("01.flac"), "TITLE" to listOf("Bir Sana Bir Bana"), "ARTIST" to listOf("Moğollar", "Cahit Berkay"), "ALBUM" to listOf("Moğollar"))
        compose.setContent {
            NagmedarTheme {
                CompositionLocalProvider(LocalTagFiles provides fake.files, LocalTagLookups provides fakeTagLookups(musicBrainzMatch())) {
                    TagEditorPane(listOf(sampleTarget("01.flac")))
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tag-find-info").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("tag-lookup-result-0").performClick()
        compose.waitForIdle()
        // what adds to the file is checked; one artist for the file's two would take one away: offered, not checked
        compose.onNodeWithTag("tag-lookup-field-DATE").assertIsOn()
        compose.onNodeWithTag("tag-lookup-field-GENRE").assertIsOn()
        compose.onNodeWithTag("tag-lookup-field-ARTIST").assertIsOff()
        compose.onNodeWithText("MusicBrainz kaydı").assertExists()
        compose.onNodeWithTag("tag-lookup-field-GENRE").performClick()
        compose.onNodeWithTag("tag-lookup-apply").performClick()
        compose.waitForIdle()
        // chosen, not written: they wait with the rest for Kaydet
        assertEquals(emptyList<Any>(), tags.written)
        compose.onNodeWithTag("tag-save").assertIsEnabled().performClick()
        compose.waitForIdle()
        val saved = tags.tags(sampleRef("01.flac"))
        assertEquals(listOf("1971-03-01"), saved["DATE"])
        assertEquals(emptyList<String>(), saved["GENRE"])
        assertEquals(listOf("Moğollar", "Cahit Berkay"), saved["ARTIST"])
        assertEquals(listOf("1"), saved["TRACKNUMBER"])
        assertEquals(listOf("8f1c2d3e-0000-4000-8000-000000000002"), saved["MUSICBRAINZ_TRACKID"])
    }

    @Test
    fun nothingSelectedIsSaid() {
        compose.setContent {
            NagmedarTheme {
                CompositionLocalProvider(LocalTagFiles provides fake.files) { TagEditorPane(emptyList()) }
            }
        }
        compose.onNodeWithTag("tag-editor-note").assertExists()
    }
}
