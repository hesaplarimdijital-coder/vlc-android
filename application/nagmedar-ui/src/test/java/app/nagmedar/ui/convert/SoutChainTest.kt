package app.nagmedar.ui.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-1303: the stream output's chain for each kind of profile, as VLC's config chain reads it:
 * the values quoted and escaped, the streams the profile leaves out kept out, the HLS session's
 * playlist and segments where the server looks for them.
 */
class SoutChainTest {

    private fun preset(id: Long) = ConvertProfiles.presets.single { it.id == id }

    @Test
    fun aVideoProfileTranscodesBoth() {
        assertEquals(
            listOf(
                ":sout=#transcode{vcodec=h264,vb=4000,maxheight=720,acodec=mp4a,ab=160,channels=2}:std{access=file,mux=mp4,dst='/data/convert/7/out.mp4'}",
                ":no-sout-spu",
            ),
            SoutChain.convert(preset(-2), "/data/convert/7/out.mp4"),
        )
    }

    @Test
    fun copyingIsARemux() {
        // every stream copied: no transcode, the subtitles kept as tracks in Matroska
        assertEquals(
            listOf(":sout=#std{access=file,mux=avformat{mux=matroska},dst='/x/a.mkv'}"),
            SoutChain.convert(preset(-5), "/x/a.mkv"),
        )
    }

    @Test
    fun audioAloneLeavesTheVideoOut() {
        assertEquals(
            listOf(":sout=#transcode{acodec=mp3,ab=320,channels=2}:std{access=file,mux=raw,dst='/x/a.mp3'}", ":no-sout-video", ":no-sout-spu"),
            SoutChain.convert(preset(-6), "/x/a.mp3"),
        )
        // Opus at 48 kHz whatever is asked; FLAC without a bit rate
        assertTrue(SoutChain.convert(preset(-7), "/x/a.opus")[0].contains("acodec=opus,ab=160,channels=2,samplerate=48000}:std{access=file,mux=ogg,"))
        assertTrue(SoutChain.convert(preset(-8), "/x/a.flac")[0].startsWith(":sout=#transcode{acodec=flac,channels=2}:std{access=file,mux=avformat{mux=flac},"))
    }

    @Test
    fun subtitlesBurnIntoTheEncodedPicture() {
        val burn = preset(-1).copy(subtitles = SubtitleMode.Burn)
        val options = SoutChain.convert(burn, "/x/a.mp4")
        assertTrue(options[0], options[0].contains("channels=2,soverlay}:std{"))
        assertEquals(":no-sout-spu", options[1])
        // a video copied cannot carry them: no overlay
        val copied = burn.copy(video = VideoSettings(VideoCodec.Copy))
        assertTrue(!SoutChain.convert(copied, "/x/a.mp4")[0].contains("soverlay"))
        // without audio the audio stream is left out
        assertEquals(listOf(":no-sout-audio", ":no-sout-spu"), SoutChain.convert(preset(-1).copy(audio = null), "/x/v.mp4").drop(1))
    }

    @Test
    fun pathsAreQuotedAndEscaped() {
        val path = """/storage/Filmler/Kuzey Işığı, "yönetmen" 'kurgu' \ son.mkv"""
        val quoted = SoutChain.quote(path)
        assertEquals("""'/storage/Filmler/Kuzey Işığı, \"yönetmen\" \'kurgu\' \\ son.mkv'""", quoted)
        assertEquals(path, SoutChain.unquote(quoted))
        // in a chain: the comma and the spaces stay inside the quotes
        val chain = SoutChain.convert(preset(-6), path)[0]
        assertTrue(chain.endsWith("dst=$quoted}"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aBraceCannotBeInAPath() {
        SoutChain.quote("/x/{evil}.mp4")
    }

    @Test(expected = IllegalArgumentException::class)
    fun aNewLineCannotBeInAPath() {
        SoutChain.quote("/x/a\n:sout=#std{access=file,dst=/sdcard/x}")
    }

    @Test
    fun theHlsSessionWritesWhereTheServerLooks() {
        val hls = HlsQuality.P720.profile
        val options = SoutChain.hls(hls, "/data/hls/1700000000000/")
        assertEquals(
            ":sout=#transcode{vcodec=h264,vb=3000,maxheight=720,acodec=mp4a,ab=160,channels=2,samplerate=48000}" +
                ":std{access=livehttp{seglen=6,delsegs=true,numsegs=300,ratecontrol=true," +
                "index='/data/hls/1700000000000/stream.m3u8',index-url='seg-########.ts'}," +
                "mux=ts{use-key-frames},dst='/data/hls/1700000000000/seg-########.ts'}",
            options[0],
        )
        assertEquals(listOf(":no-sout-spu"), options.drop(1))
        assertTrue(SoutChain.hls(HlsQuality.Audio.profile, "/d")[0].contains("transcode{acodec=mp4a,ab=160,channels=2,samplerate=48000}"))
        assertEquals(listOf(":no-sout-video", ":no-sout-spu"), SoutChain.hls(HlsQuality.Audio.profile, "/d").drop(1))
    }
}
