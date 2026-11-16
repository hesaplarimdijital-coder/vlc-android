/*
 * Nağmedar 1.5.0: from Walkman Sync (app/src/main/kotlin/com/walkmansync/service/SyncServiceContract.kt), the owner's own app.
 */
package app.nagmedar.walkman.service

/**
 * `SyncForegroundService` (B1) sözleşmesi. Servis bir "canlı tutma kabuğudur": işi yürütmez, yalnızca
 * süreç ön plandayken coordinator'ın (uygulama kapsamında çalışan) işinin kesilmemesini sağlar ve
 * bildirimi günceller.
 *
 * Yaşam döngüsü:
 *  - ACTION_START: `onStartCommand` içinde 5 sn dolmadan
 *    `ServiceCompat.startForeground(this, NOTIFICATION_ID_PROGRESS, n, FOREGROUND_SERVICE_TYPE_DATA_SYNC)`.
 *    `SyncController.state` toplanır; bildirim ≤ 1/sn güncellenir. `Transferring` süresince kısmi WakeLock
 *    (etiket [WAKE_LOCK_TAG], zaman aşımı [WAKE_LOCK_TIMEOUT_MS]) tutulur, Paused/bitişte bırakılır.
 *  - state busy olmaktan çıkınca: Completed/Failed ise CHANNEL_RESULT'a sonuç bildirimi (NOTIFICATION_ID_RESULT),
 *    sonra `stopForeground(STOP_FOREGROUND_REMOVE)` + `stopSelf()`.
 *  - ACTION_PAUSE / ACTION_RESUME / ACTION_CANCEL: bildirim düğmelerinden; SyncController'a iletilir. Servis
 *    çalışmıyorken gelirse yalnızca iletilir, ön plana geçilmez (startService ile değil PendingIntent.getService).
 *  - `onTimeout(startId, fgsType)` (API 35 dataSync süre sınırı): cancel() + stopSelf().
 *  - START_NOT_STICKY: süreç ölürse servis yeniden başlatılmaz (yarım .part sonraki taramada temizlenir).
 */
object SyncServiceContract {
    const val ACTION_START = "app.nagmedar.walkman.action.START"
    const val ACTION_PAUSE = "app.nagmedar.walkman.action.PAUSE"
    const val ACTION_RESUME = "app.nagmedar.walkman.action.RESUME"
    const val ACTION_CANCEL = "app.nagmedar.walkman.action.CANCEL"

    /** Süren işlem (düşük önem, sessiz). */
    const val CHANNEL_PROGRESS = "walkman_progress"

    /** Tamamlandı / hata (varsayılan önem). */
    const val CHANNEL_RESULT = "walkman_result"

    // Nağmedar 1.5.0: names and numbers of their own beside the player's
    const val NOTIFICATION_ID_PROGRESS = 7101
    const val NOTIFICATION_ID_RESULT = 7102

    /** PendingIntent istek kodları (benzersiz). */
    const val REQUEST_OPEN_APP = 10
    const val REQUEST_PAUSE = 11
    const val REQUEST_RESUME = 12
    const val REQUEST_CANCEL = 13

    const val WAKE_LOCK_TAG = "Nagmedar:walkman"
    const val WAKE_LOCK_TIMEOUT_MS = 6L * 60 * 60 * 1000
}
