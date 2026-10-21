package app.nagmedar.ui.transcode

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S-1303: a profile's rules: the codecs its container takes, the encoders this build has, the bit
 * rates and channels, the subtitles; the presets that can be used; a profile fitted to another
 * container; its JSON round trip (a job keeps the profile it ran with).
 */
@RunWith(AndroidJUnit4::class)
class TranscodeProfileTest {

    private val mp4 = TranscodeProfiles.presets.single { it.id == -2L }

    @Test
    fun thePresetsAreValidButH265() {
        val invalid = TranscodeProfiles.presets.filter { TranscodeProfiles.validate(it).isNotEmpty() }
        assertEquals(listOf(-3L), invalid.map { it.id })
        assertEquals(listOf(ProfileProblem.NoEncoder), TranscodeProfiles.validate(invalid.single()))
        assertTrue(TranscodeProfiles.presets.all { it.builtin && it.nameRes != null })
    }

    @Test
    fun aContainerTakesItsCodecs() {
        assertEquals(listOf(ProfileProblem.VideoNotInContainer), TranscodeProfiles.validate(mp4.copy(video = VideoSettings(VideoCodec.Vp8))))
        assertEquals(listOf(ProfileProblem.AudioNotInContainer), TranscodeProfiles.validate(mp4.copy(audio = AudioSettings(AudioCodec.Vorbis))))
        val mp3 = TranscodeProfiles.presets.single { it.id == -6L }
        assertEquals(listOf(ProfileProblem.VideoNotInContainer), TranscodeProfiles.validate(mp3.copy(video = VideoSettings(VideoCodec.H264))))
    }

    @Test
    fun ratesChannelsAndNames() {
        assertEquals(listOf(ProfileProblem.VideoBitrate), TranscodeProfiles.validate(mp4.copy(video = mp4.video!!.copy(bitrateKbps = 100))))
        assertEquals(listOf(ProfileProblem.AudioBitrate), TranscodeProfiles.validate(mp4.copy(audio = mp4.audio!!.copy(bitrateKbps = 1000))))
        assertEquals(listOf(ProfileProblem.Channels), TranscodeProfiles.validate(mp4.copy(container = TranscodeContainer.Mkv, audio = AudioSettings(AudioCodec.Mp3, 320, 6))))
        // lossless codecs have no bit rate to check
        assertEquals(emptyList<ProfileProblem>(), TranscodeProfiles.validate(TranscodeProfiles.presets.single { it.id == -8L }))
        assertEquals(listOf(ProfileProblem.NameMissing), TranscodeProfiles.validate(mp4.copy(name = "  ")))
        assertEquals(listOf(ProfileProblem.NameTaken), TranscodeProfiles.validate(mp4.copy(name = "Arşiv"), taken = listOf("arşiv ")))
        assertEquals(listOf(ProfileProblem.Empty), TranscodeProfiles.validate(mp4.copy(video = null, audio = null)))
    }

    @Test
    fun subtitlesNeedTheirPlace() {
        assertEquals(listOf(ProfileProblem.KeepNeedsMkv), TranscodeProfiles.validate(mp4.copy(subtitles = SubtitleMode.Keep)))
        assertEquals(emptyList<ProfileProblem>(), TranscodeProfiles.validate(mp4.copy(subtitles = SubtitleMode.Burn)))
        assertEquals(listOf(ProfileProblem.BurnNeedsVideo), TranscodeProfiles.validate(mp4.copy(video = VideoSettings(VideoCodec.Copy), subtitles = SubtitleMode.Burn)))
    }

    @Test
    fun aProfileFitsAnotherContainer() {
        // VP8 and Vorbis in MKV with its subtitles kept, made an MP4: H.264 and AAC, no subtitle tracks
        val vp8 = TranscodeProfiles.presets.single { it.id == -4L }.copy(subtitles = SubtitleMode.Keep)
        val asMp4 = TranscodeProfiles.fitTo(vp8, TranscodeContainer.Mp4)
        assertEquals(VideoCodec.H264, asMp4.video!!.codec)
        assertEquals(3000, asMp4.video!!.bitrateKbps)
        assertEquals(AudioCodec.Aac, asMp4.audio!!.codec)
        assertEquals(SubtitleMode.None, asMp4.subtitles)
        // burnt-in subtitles go with the picture
        assertEquals(SubtitleMode.Burn, TranscodeProfiles.fitTo(mp4.copy(subtitles = SubtitleMode.Burn), TranscodeContainer.Ts).subtitles)
        val flac = TranscodeProfiles.fitTo(mp4, TranscodeContainer.Flac)
        assertNull(flac.video)
        assertEquals(AudioCodec.Flac, flac.audio!!.codec)
        assertEquals(SubtitleMode.None, flac.subtitles)
        assertEquals(emptyList<ProfileProblem>(), TranscodeProfiles.validate(flac))
        // back to a video container: a video the encoders have
        val again = TranscodeProfiles.fitTo(flac, TranscodeContainer.Mkv)
        assertEquals(VideoCodec.H264, again.video!!.codec)
    }

    @Test
    fun aProfileComesBackFromJson() {
        val custom = TranscodeProfile(
            id = 12,
            name = "Arşiv · \"1080p\"",
            container = TranscodeContainer.Mkv,
            video = VideoSettings(VideoCodec.H264, 6500, 1080, 25),
            audio = AudioSettings(AudioCodec.Ac3, 448, 6, 48_000),
            subtitles = SubtitleMode.Keep,
        )
        assertEquals(custom, TranscodeProfile.parse(custom.toJson(), id = 12))
        val audio = TranscodeProfiles.presets.single { it.id == -7L }.copy(id = 0, nameRes = null)
        assertEquals(audio, TranscodeProfile.parse(audio.toJson()))
        assertNull(TranscodeProfile.parse("{}"))
        assertNull(TranscodeProfile.parse("{\"name\":\"x\",\"container\":\"Avi\"}"))
    }
}
