package app.nagmedar.ui.network

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.nagmedar.ui.NagmedarActivity
import app.nagmedar.ui.R
import app.nagmedar.ui.notify.NotificationIds

/**
 * Classic UI: libVLC asks something (a sign-in, a certificate to accept) while no window of the
 * app is in front: "Nağmedar is asking: …", whose tap opens the window that shows the question.
 * VLC's DialogActivity took these before; it is not started from the background any more. Nothing
 * is posted without the permission; the question then waits for the next window. libVLC's words
 * are said as the window says them ([LibVlcWords]).
 */
internal class PromptNotification(private val context: Context) {
    private val manager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return manager.areNotificationsEnabled()
    }

    /** Says [prompt] waits; replaces the one before (one notification however many wait). */
    fun post(prompt: LibVlcPrompt, waiting: Int) {
        if (!canPost()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.nag_prompt_channel), NotificationManager.IMPORTANCE_HIGH))
        }
        // libVLC writes in English: the words Nağmedar knows are said in the interface's language, as in the window
        val title = LibVlcWords.localized(context.resources, prompt.title)
        val question = LibVlcWords.localized(context.resources, prompt.text)
        val about = title.ifBlank { question }.trim().lineSequence().firstOrNull().orEmpty()
        val open = Intent(context, NagmedarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val text = if (waiting > 1) context.resources.getQuantityString(R.plurals.nag_prompt_waiting, waiting, waiting) else context.getString(R.string.nag_prompt_tap)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(org.videolan.resources.R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(if (about.isEmpty()) context.getString(R.string.nag_prompt_asking) else context.getString(R.string.nag_prompt_asking_about, about))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(question.trim(), text).filter { it.isNotEmpty() }.joinToString("\n")))
            .setContentIntent(PendingIntent.getActivity(context, ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
        runCatching { manager.notify(ID, builder.build()) }
    }

    fun cancel() {
        runCatching { manager.cancel(ID) }
    }

    private companion object {
        const val CHANNEL = "nagmedar_questions"
        const val ID = NotificationIds.PROMPT
    }
}
