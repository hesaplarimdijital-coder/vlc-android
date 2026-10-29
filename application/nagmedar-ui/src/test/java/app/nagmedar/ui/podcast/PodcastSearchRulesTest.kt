package app.nagmedar.ui.podcast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.0.1: "Ara" can be pressed (and looks so) exactly when there is something to search and no search is under way. */
class PodcastSearchRulesTest {

    @Test
    fun theSearchButtonWaitsForWords() {
        assertFalse(searchEnabled("", SearchUi.Idle))
        assertFalse(searchEnabled("   ", SearchUi.Idle))
        assertTrue(searchEnabled("gece", SearchUi.Idle))
    }

    @Test
    fun aSearchUnderWayIsNotStartedAgain() {
        assertFalse(searchEnabled("gece", SearchUi.Searching))
        assertTrue(searchEnabled("gece", SearchUi.Failed("gece", SearchFailure.Offline)))
    }
}
