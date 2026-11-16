/*
 * Nağmedar 1.5.0: from Walkman Sync (app/src/main/kotlin/com/walkmansync/storage/VolumeEventReceiver.kt), the owner's own app.
 */
package app.nagmedar.walkman.storage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import app.nagmedar.walkman.Walkman

/**
 * Kart takma/çıkarma ve USB okuyucu olayları. Olayın kendisine güvenilmez (kısıtlı/gecikmeli olabilir);
 * yalnızca kayıt defterini yeniden okutur, asıl doğrulama her açılışta ve her G/Ç hatasında yapılır.
 * Nağmedar'da yalnızca [SafVolumeRegistry] tarafından dinamik olarak (MEDIA_* + USB_*) kaydedilir; manifestte değil
 * (her kart takılışında Nağmedar'ın süreci başlamasın diye).
 */
class VolumeEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val registry = Walkman.graph(context).volumeRegistry
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED,
            UsbManager.ACTION_USB_DEVICE_DETACHED -> registry.refresh(withRetries = true)
            Intent.ACTION_MEDIA_MOUNTED,
            Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_EJECT,
            Intent.ACTION_MEDIA_REMOVED,
            Intent.ACTION_MEDIA_BAD_REMOVAL -> registry.refresh()
            else -> Unit
        }
    }
}
