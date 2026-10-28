package app.nagmedar.ui.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** 1.0: the channels of the removed video features go from the system's settings; the others stay. */
@RunWith(AndroidJUnit4::class)
class RemovedChannelsTest {

    @Test
    fun theVideoFeaturesChannelsGoAndTheOthersStay() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(NotificationManager::class.java)
        (RemovedChannels.ids + "nagmedar_radio_recordings").forEach { id ->
            manager.createNotificationChannel(NotificationChannel(id, id, NotificationManager.IMPORTANCE_LOW))
        }
        RemovedChannels.delete(context)
        RemovedChannels.ids.forEach { assertNull(it, manager.getNotificationChannel(it)) }
        assertNotNull(manager.getNotificationChannel("nagmedar_radio_recordings"))
        // once more: nothing to delete, nothing breaks
        RemovedChannels.delete(context)
    }
}
