package app.nagmedar.ui.convert

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
class ConvertProfileTest {

    private val mp4 = ConvertProfiles.presets.single { it.id == -2L }

    @Test
    fun thePresetsAreValidButH265() {
        val invalid = ConvertProfiles.presets.filter { ConvertProfiles.validate(it).isNotEmpty() }
        assertEquals(listOf(-3L), invalid.map { it.id })
        assertEquals(listOf(ProfileProblem.NoEncoder), ConvertProfiles.validate(invalid.single()))
        assertTrue(ConvertProfiles.presets.all { it.builtin && it.nameRes != null })
    }

    @Test
    fun aContainerTakesItsCodecs() {
        assertEquals(listOf(ProfileProblem.VideoNotInContainer), ConvertProfiles.validate(mp4.copy(video = VideoSettings(VideoCodec.Vp8))))
        assertEquals(listOf(ProfileProblem.AudioNotInContainer), ConvertProfiles.validate(mp4.copy(audio = AudioSettings(AudioCodec.Vorbis))))
        val mp3 = ConvertProfiles.presets.single { it.id == -6L }
        assertEquals(listOf(ProfileProblem.VideoNotInContainer), ConvertProfiles.validate(mp3.copy(video = VideoSettings(VideoCodec.H264))))
    }

    @Test
    fun ratesChannelsAndNames() {
        assertEquals(listOf(ProfileProblem.VideoBitrate), ConvertProfiles.validate(mp4.copy(video = mp4.video!!.copy(bitrateKbps = 100))))
        assertEquals(listOf(ProfileProblem.AudioBitrate), ConvertProfiles.validate(mp4.copy(audio = mp4.audio!!.copy(bitrateKbps = 1000))))
        assertEquals(listOf(ProfileProblem.Channels), ConvertProfiles.validate(mp4.copy(container = ConvertContainer.Mkv, audio = AudioSettings(AudioCodec.Mp3, 320, 6))))
        // lossless codecs have no bit rate to check
        assertEquals(emptyList<ProfileProblem>(), ConvertProfiles.validate(ConvertProfiles.presets.single { it.id == -8L }))
        assertEquals(listOf(ProfileProblem.NameMissing), ConvertProfiles.validate(mp4.copy(name = "  ")))
        assertEquals(listOf(ProfileProblem.NameTaken), ConvertProfiles.validate(mp4.copy(name = "Arşiv"), taken = listOf("arşiv ")))
        assertEquals(listOf(ProfileProblem.Empty), ConvertProfiles.validate(mp4.copy(video = null, audio = null)))
    }

    @Test
    fun subtitlesNeedTheirPlace() {
        assertEquals(listOf(ProfileProblem.KeepNeedsMkv), ConvertProfiles.validate(mp4.copy(subtitles = SubtitleMode.Keep)))
        assertEquals(emptyList<ProfileProblem>(), ConvertProfiles.validate(mp4.copy(subtitles = SubtitleMode.Burn)))
        assertEquals(listOf(ProfileProblem.BurnNeedsVideo), ConvertProfiles.validate(mp4.copy(video = VideoSettings(VideoCodec.Copy), subtitles = SubtitleMode.Burn)))
    }

    @Test
    fun aProfileFitsAnotherContainer() {
        // VP8 and Vorbis in MKV with its subtitles kept, made an MP4: H.264 and AAC, no subtitle tracks
        val vp8 = ConvertProfiles.presets.single { it.id == -4L }.copy(subtitles = SubtitleMode.Keep)
        val asMp4 = ConvertProfiles.fitTo(vp8, ConvertContainer.Mp4)
        assertEquals(VideoCodec.H264, asMp4.video!!.codec)
        assertEquals(3000, asMp4.video!!.bitrateKbps)
        assertEquals(AudioCodec.Aac, asMp4.audio!!.codec)
        assertEquals(SubtitleMode.None, asMp4.subtitles)
        // burnt-in subtitles go with the picture
        assertEquals(SubtitleMode.Burn, ConvertProfiles.fitTo(mp4.copy(subtitles = SubtitleMode.Burn), ConvertContainer.Ts).subtitles)
        val flac = ConvertProfiles.fitTo(mp4, ConvertContainer.Flac)
        assertNull(flac.video)
        assertEquals(AudioCodec.Flac, flac.audio!!.codec)
        assertEquals(SubtitleMode.None, flac.subtitles)
        assertEquals(emptyList<ProfileProblem>(), ConvertProfiles.validate(flac))
        // back to a video container: a video the encoders have
        val again = ConvertProfiles.fitTo(flac, ConvertContainer.Mkv)
        assertEquals(VideoCodec.H264, again.video!!.codec)
    }

    @Test
    fun aProfileComesBackFromJson() {
        val custom = ConvertProfile(
            id = 12,
            name = "Arşiv · \"1080p\"",
            container = ConvertContainer.Mkv,
            video = VideoSettings(VideoCodec.H264, 6500, 1080, 25),
            audio = AudioSettings(AudioCodec.Ac3, 448, 6, 48_000),
            subtitles = SubtitleMode.Keep,
        )
        assertEquals(custom, ConvertProfile.parse(custom.toJson(), id = 12))
        val audio = ConvertProfiles.presets.single { it.id == -7L }.copy(id = 0, nameRes = null)
        assertEquals(audio, ConvertProfile.parse(audio.toJson()))
        assertNull(ConvertProfile.parse("{}"))
        assertNull(ConvertProfile.parse("{\"name\":\"x\",\"container\":\"Avi\"}"))
    }
}
