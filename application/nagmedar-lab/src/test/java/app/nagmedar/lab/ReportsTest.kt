package app.nagmedar.lab

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reports read the JSON that native/lab writes. The sample below is a real output of the
 * USB tone on a DAC; a renamed key on either side shows up here.
 */
class ReportsTest {

    private val tone = """{"uac":2,"speed":"high","requested_rate":96000,"requested_bits":24,"rate_before":48000,"rate_after":96000,
        "clock":41,"seconds":10.002,"frames":960192,"expected_frames":960192,"packets":80016,"transfer_errors":0,
        "packet_errors":0,"feedback_rate":96000.012,"feedback_updates":1250,"format":{"interface":1,"alt":2,"channels":2,
        "subslot_bytes":4,"bits":24,"endpoint":1,"max_packet":104,"interval":1,"sync":"async","feedback_endpoint":129,
        "implicit_feedback":false,"clock":41,"rates":[[44100,44100,0],[96000,96000,0],[192000,192000,0]]},"error":""}"""

    @Test
    fun usbReportsReadTheNativeJson() {
        val text = UsbAudioLab.formatTone(JSONObject(tone), 96000, 24, 10.0)
        assertTrue(text, text.contains("önce 48000 Hz, sonra 96000 Hz"))
        assertTrue(text, text.contains("senkron async"))
        assertTrue(text, text.contains("44100, 96000, 192000"))
        assertTrue(text, text.contains("+0.000 %"))
        val description = UsbAudioLab.formatDescription(
            JSONObject("""{"uac":2,"speed":"high","control_interface":0,"control_claimed":true,"playback":[${JSONObject(tone).getJSONObject("format")}],"error":""}"""),
        )
        assertTrue(description, description.contains("USB Audio Class 2"))
        assertTrue(description, description.contains("Çalma biçimleri (1)"))
    }
}
