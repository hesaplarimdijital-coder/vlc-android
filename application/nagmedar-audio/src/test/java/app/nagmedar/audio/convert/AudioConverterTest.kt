package app.nagmedar.audio.convert

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** S-1010: the engine's answer read, and the tags put over the source's as the engine takes them. */
@RunWith(AndroidJUnit4::class)
class AudioConverterTest {

    @Test
    fun aFileWritten() {
        val done = ConvertResult.parse("""{"frames":240000,"rate":48000,"channels":2,"bytes":81234,"tags":9,"cover":true,"lufs":-20.171,"gain_db":2.171}""")
        assertEquals(ConvertResult.Done(240_000, 48_000, 2, 81_234, 9, cover = true, gainDb = 2.171), done)
        // no ReplayGain in the source: none measured
        val plain = ConvertResult.parse("""{"frames":1,"rate":44100,"channels":1,"bytes":10,"tags":0,"cover":false,"lufs":null,"gain_db":null}""")
        assertNull((plain as ConvertResult.Done).gainDb)
    }

    @Test
    fun stoppedOrFailed() {
        assertEquals(ConvertResult.Cancelled, ConvertResult.parse("""{"error":"cancelled","cancelled":true}"""))
        assertEquals(
            ConvertResult.Failed(ConvertFailure.Engine, "write: No space left on device"),
            ConvertResult.parse("""{"error":"write: No space left on device","cancelled":false}"""),
        )
        assertEquals(ConvertResult.Failed(ConvertFailure.Engine, "no answer"), ConvertResult.parse(null))
        assertEquals(ConvertResult.Failed(ConvertFailure.Engine, "no answer"), ConvertResult.parse("{"))
    }

    @Test
    fun tagLinesAsTheEngineReadsThem() {
        assertNull(AudioConverter.tagLines(emptyMap()))
        val lines = AudioConverter.tagLines(linkedMapOf("TITLE" to "İkinci bölüm", "COMPOSER" to "", "COMMENT" to "iki\nsatır", "BAD=KEY" to "x"))
        assertEquals("TITLE=İkinci bölüm\nCOMPOSER=\nCOMMENT=iki satır\n", String(lines!!, Charsets.UTF_8))
    }

    @Test
    fun theCodecsFiles() {
        assertEquals(listOf("opus", "mp3", "m4a"), ConvertCodec.entries.map { it.extension })
        assertEquals(listOf(0, 1, 2), ConvertCodec.entries.map { it.native })
    }
}
