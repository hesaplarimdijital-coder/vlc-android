package app.nagmedar.lab

/**
 * S-1207: a compatibility track to add: the audio track [source] (the probe's [track]) a device
 * cannot play, and how it is encoded beside it.
 */
data class CompatibilityTrack(val source: StreamRef, val track: AudioTech, val spec: EncodeSpec)

/**
 * S-1207: which audio tracks of a film need a compatibility track for a device, and of what kind.
 * A device that plays none of TrueHD and DTS-HD (the LG OLED B4 from a USB drive) gets E-AC-3
 * beside every track it cannot play: 5.1 at 640 kbps (stereo at 256 for a stereo source), the
 * one beside the film's main track (its default, or its first) made default, so that the film
 * starts with sound. Commentary tracks are left alone; the planner (S-1205) can still add one.
 */
object CompatibilityTracks {
    /** E-AC-3 for [track]: 640 kbps for 5.1 and 7.1, 448 for 3 or 4 channels, 256 for stereo, 128 for mono. */
    fun eac3For(track: AudioTech, makeDefault: Boolean): EncodeSpec = EncodeSpec(
        codec = EncodeCodec.EAC3,
        bitrateKbps = when {
            track.channels >= 5 -> 640
            track.channels >= 3 -> 448
            track.channels == 2 -> 256
            else -> 128
        },
        makeDefault = makeDefault,
    )

    /** LPCM: every channel up to 7.1, lossless as decoded. */
    fun lpcmFor(makeDefault: Boolean): EncodeSpec = EncodeSpec(EncodeCodec.LPCM, makeDefault = makeDefault)

    /**
     * The tracks to add to [tech] for a device that plays the audio tracks [plays] accepts (a
     * device profile's audio list, see the TV Lab's DeviceProfile.allowsAudio); none when it plays
     * them all.
     */
    fun suggest(tech: MediaTech, plays: (AudioTech) -> Boolean): List<CompatibilityTrack> {
        val main = tech.audio.indexOfFirst { it.isDefault }.takeIf { it >= 0 } ?: 0
        return tech.audio.mapIndexedNotNull { i, track ->
            if (plays(track) || (track.commentary && i != main)) return@mapIndexedNotNull null
            // the main track unplayable: its compatibility track is the one the device starts with
            CompatibilityTrack(StreamRef(StreamType.Audio, i), track, eac3For(track, makeDefault = i == main))
        }
    }
}
