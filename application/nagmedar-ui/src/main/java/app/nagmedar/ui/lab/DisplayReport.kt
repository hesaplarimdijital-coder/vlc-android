package app.nagmedar.ui.lab

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.View
import androidx.annotation.RequiresApi
import java.util.Locale

/**
 * SP-04: what each connected display reports (modes, refresh rates, HDR types, the sink's EDID
 * product info) and whether a 23.976 / 24 Hz mode can be requested on the external display that
 * USB-C → HDMI provides (LG B4).
 */
object DisplayReport {

    fun build(context: Context): String = buildString {
        val manager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        appendLine("Nağmedar ekran raporu (SP-04)")
        appendLine(AudioReport.deviceLine())
        appendLine()
        manager.displays.forEach { display -> appendDisplay(display) }
        if (manager.displays.size < 2) appendLine("Harici ekran bağlı değil: USB-C → HDMI ile TV'yi bağlayıp raporu yeniden oluşturun.")
    }

    private fun StringBuilder.appendDisplay(display: Display) {
        appendLine("■ Ekran ${display.displayId}: ${display.name}${if (display.displayId == Display.DEFAULT_DISPLAY) " (dahili)" else ""}")
        appendLine("  Durum: ${stateName(display.state)}, bayraklar: 0x${Integer.toHexString(display.flags)}")
        val mode = display.mode
        appendLine("  Geçerli mod: ${modeLine(mode)}")
        appendLine("  Modlar:")
        display.supportedModes.sortedWith(compareBy({ -it.physicalWidth }, { -it.refreshRate })).forEach { appendLine("    ${modeLine(it)}") }
        if (Build.VERSION.SDK_INT >= 24) {
            val hdr = display.hdrCapabilities
            val types = hdr?.supportedHdrTypes?.joinToString { hdrName(it) }.orEmpty()
            appendLine("  HDR türleri: ${types.ifEmpty { "yok" }}")
            hdr?.let { appendLine("  Parlaklık: en çok %.0f nit, ortalama %.0f, en az %.3f".format(Locale.ROOT, it.desiredMaxLuminance, it.desiredMaxAverageLuminance, it.desiredMinLuminance)) }
        }
        if (Build.VERSION.SDK_INT >= 26) appendLine("  HDR açık: ${display.isHdr}, geniş renk: ${display.isWideColorGamut}")
        if (Build.VERSION.SDK_INT >= 30) appendLine("  Düşük gecikme (ALLM/GAME) desteği: ${display.isMinimalPostProcessingSupported}")
        if (Build.VERSION.SDK_INT >= 31) appendProductInfo(display)
        appendLine()
    }

    @RequiresApi(31)
    private fun StringBuilder.appendProductInfo(display: Display) {
        val info = display.deviceProductInfo ?: return
        appendLine("  EDID: ${info.name ?: "?"}, üretici ${info.manufacturerPnpId}, ürün ${info.productId}, yıl ${info.modelYear}, bağlantı ${info.connectionToSinkType}")
    }

    private fun modeLine(mode: Display.Mode): String {
        val alternatives = if (Build.VERSION.SDK_INT >= 31) alternativeRates(mode) else ""
        return "#${mode.modeId} ${mode.physicalWidth}×${mode.physicalHeight} @ %.3f Hz%s".format(Locale.ROOT, mode.refreshRate, alternatives)
    }

    @RequiresApi(31)
    private fun alternativeRates(mode: Display.Mode): String {
        val rates = mode.alternativeRefreshRates
        return if (rates.isEmpty()) "" else " (sorunsuz geçiş: ${rates.joinToString { "%.3f".format(Locale.ROOT, it) }})"
    }

    private fun stateName(state: Int) = when (state) {
        Display.STATE_ON -> "açık"
        Display.STATE_OFF -> "kapalı"
        Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND -> "bekleme"
        else -> "durum $state"
    }

    private fun hdrName(type: Int) = when (type) {
        1 -> "Dolby Vision"
        2 -> "HDR10"
        3 -> "HLG"
        4 -> "HDR10+"
        else -> "tür $type"
    }

    /**
     * Opens a black presentation on the first external display, asks for the mode closest to
     * [targetHz] at the current resolution and reports the mode the display actually switched to.
     */
    fun tryRefreshRate(activity: Activity, targetHz: Float = 23.976f, onResult: (String) -> Unit) {
        val manager = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val external = manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
        if (external == null) {
            onResult("Harici ekran yok: TV'yi USB-C → HDMI ile bağlayın.")
            return
        }
        val current = external.mode
        val candidates = external.supportedModes.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        val target = candidates.minByOrNull { kotlin.math.abs(it.refreshRate - targetHz) }
        if (target == null) {
            onResult("Bu çözünürlükte mod listesi boş.")
            return
        }
        val presentation = object : Presentation(activity, external) {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(View(context).apply { setBackgroundColor(Color.BLACK) })
                window?.let { w -> w.attributes = w.attributes.apply { preferredDisplayModeId = target.modeId } }
            }
        }
        presentation.show()
        Handler(Looper.getMainLooper()).postDelayed({
            val after = external.mode
            onResult(
                "İstenen: ${modeLine(target)}\nŞimdiki: ${modeLine(after)}\nSonuç: " +
                    if (after.modeId == target.modeId) "TV istenen moda geçti." else "TV moda geçmedi; ekran ${"%.3f".format(Locale.ROOT, after.refreshRate)} Hz'de kaldı.",
            )
            presentation.dismiss()
        }, 4000)
    }
}
