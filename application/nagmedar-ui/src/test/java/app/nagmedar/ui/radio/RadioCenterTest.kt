package app.nagmedar.ui.radio

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** S-1105: outside the app (tests, previews) the radio never reaches the network. */
@RunWith(AndroidJUnit4::class)
class RadioCenterTest {

    private suspend fun offline(call: suspend () -> Unit) {
        try {
            call()
            fail("the directory answered")
        } catch (e: RadioFailure.Offline) {
            // as it should
        }
    }

    @Test
    fun theDirectoryIsOfflineUnlessTheAppIsRunning() = runBlocking {
        assertFalse(RadioCenter.active)
        val directory = RadioCenter.directory(RadioCenter.active, "1.0")
        offline { directory.search(StationQuery("jazz")) }
        offline { directory.countries() }
        offline { directory.languages() }
        offline { directory.tags() }
        offline { directory.click("u") }
        offline { directory.vote("u") }
        assertTrue(RadioCenter.directory(true, "1.0") is OkHttpRadioBrowser)
    }
}
