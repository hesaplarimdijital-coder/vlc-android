package app.nagmedar.ui.network

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.R
import app.nagmedar.ui.notify.NotificationIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** S-1301: libVLC's question with no window in front, as a notification: libVLC's words said as the window says them. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr")
class PromptNotificationTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun libVlcsWordsAreSaidInTheInterfacesLanguage() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val text = "You attempted to reach example.org. However, the security certificate presented by the server is unknown " +
            "and could not be authenticated by any trusted Certificate Authority."
        val prompt = LibVlcPrompt(PromptKind.Question, "Insecure site", text, action1 = "Accept permanently", reply = RecordingReply(mutableListOf()))

        PromptNotification(app).post(prompt, waiting = 1)

        val posted = requireNotNull(shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(NotificationIds.PROMPT)) { "nothing posted" }
        val title = app.getString(R.string.nag_prompt_asking_about, app.getString(R.string.nag_cert_insecure_site))
        assertEquals(title, posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        val question = app.getString(R.string.nag_cert_unknown, "example.org").trim()
        assertTrue(posted.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().startsWith(question))
    }
}
