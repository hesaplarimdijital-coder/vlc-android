package app.nagmedar.ui.walkman

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.walkman.model.SyncState
import app.nagmedar.walkman.model.TransferOutcome
import app.nagmedar.walkman.model.TransferSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.5.0: "Walkman'e aktar", Walkman Sync's page: the cards to choose, what the scan found new listed with its check
 * boxes and copied, the copy's end; the page following the work from one view to the next.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h900dp-mdpi")
class WalkmanScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theCardsAreChosenFirstAndAPlanFoundListsWhatIsNewToCopy() {
        val controller = FakeWalkmanController()
        val model = WalkmanViewModel(controller, SavedStateHandle())
        compose.setContent { NagmedarTheme { WalkmanScreen(model) } }
        compose.waitForIdle()
        compose.onNodeWithTag("walkman-cards").assertExists()
        compose.onNodeWithText("Kaynak kart").assertExists()
        compose.onNodeWithText("Hedef kart").assertExists()

        // the scan found two new files and one already copied: the page goes to its list, both checked
        controller.state.value = SyncState.PlanReady(sampleWalkmanPlan())
        compose.waitForIdle()
        compose.onNodeWithTag("walkman-plan").assertExists()
        compose.onNodeWithText("Garip").assertExists()
        compose.onNodeWithText("Haliç").assertExists()
        compose.onNodeWithTag("walkman-selected").assertTextContains("2 seçili", substring = true)

        compose.onNodeWithTag("walkman-transfer").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(listOf(setOf("Download/Garip.flac", "Download/Haliç.mp3")), controller.transfers)
    }

    /** The page drawn again (the tablet turned, a DeX window made again) keeps the view chosen; the work's next step moves it. */
    @Test
    fun theViewChosenStaysWhenThePageIsDrawnAgain() {
        val controller = FakeWalkmanController()
        controller.state.value = SyncState.PlanReady(sampleWalkmanPlan())
        val model = WalkmanViewModel(controller, SavedStateHandle())
        val restoration = StateRestorationTester(compose)
        restoration.setContent { NagmedarTheme { WalkmanScreen(model) } }
        compose.waitForIdle()
        // opened with a list made: the list
        compose.onNodeWithTag("walkman-plan").assertExists()
        compose.onNodeWithText("Kartlar").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("walkman-cards").assertExists()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithTag("walkman-cards").assertExists()
        compose.onNodeWithTag("walkman-plan").assertDoesNotExist()

        // the work moves on (a copy ended): its view
        controller.state.value = SyncState.Completed(sampleSummary())
        compose.waitForIdle()
        compose.onNodeWithTag("walkman-summary").assertExists()
    }

    /**
     * As in Walkman Sync, rows are highlighted apart from what is checked and then left out or checked together: by
     * touch the row's menu's "Seç" starts it and taps add rows; the bar over the table and the row's menu act on them.
     */
    @Test
    fun highlightedRowsAreLeftOutAndCheckedTogether() {
        val controller = FakeWalkmanController()
        controller.state.value = SyncState.PlanReady(sampleWalkmanPlan())
        val model = WalkmanViewModel(controller, SavedStateHandle())
        compose.setContent { NagmedarTheme(inputMode = InputMode.Touch) { WalkmanScreen(model) } }
        checked(2)

        rowMenu("Garip")
        compose.onNodeWithText("Seç").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Haliç").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("2 satır vurgulu").assertExists()

        // the bar: both left out, both checked again
        compose.onNodeWithTag("walkman-highlight-exclude").performClick()
        checked(0)
        compose.onNodeWithTag("walkman-highlight-include").performClick()
        checked(2)

        // a highlighted row's menu acts on all of them
        rowMenu("Haliç")
        compose.onNodeWithText("Vurgulu 2 satırı hariç tut").performClick()
        checked(0)
        compose.waitUntil(5_000) { model.diff.value.highlight == setOf("Download/Garip.flac", "Download/Haliç.mp3") }
    }

    /** The bar under the table says [count] files are checked (the list is made off the main thread). */
    private fun checked(count: Int) {
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("walkman-selected").assertTextContains("$count seçili", substring = true) }.isSuccess
        }
    }

    /** Opens the menu of the table's row showing [text], as a screen reader does. */
    private fun rowMenu(text: String) {
        val row = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions) and hasAnyDescendant(hasText(text)))
        val actions = row.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.first { it.label == "Satır işlemleri" }.action() }
        compose.waitForIdle()
    }

    @Test
    fun theCopysEndIsSaidAndDoneGoesBackToTheCards() {
        val controller = FakeWalkmanController()
        val model = WalkmanViewModel(controller, SavedStateHandle())
        compose.setContent { NagmedarTheme { WalkmanScreen(model) } }
        compose.waitForIdle()
        controller.state.value = SyncState.Completed(sampleSummary())
        compose.waitForIdle()
        compose.onNodeWithTag("walkman-summary").assertExists()
        compose.onNodeWithText("Aktarım tamamlandı").assertExists()
        compose.onNodeWithTag("walkman-done").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("walkman-cards").assertExists()
    }

    /** Both new files copied in 13 seconds. */
    private fun sampleSummary() = TransferSummary(
        outcome = TransferOutcome.COMPLETED,
        totalItems = 2,
        copiedItems = 2,
        copiedBytes = 39_000_000,
        failures = emptyList(),
        notAttempted = 0,
        startedAtMillis = 1_700_000_000_000L,
        finishedAtMillis = 1_700_000_013_000L,
        indexSaved = true,
    )
}
