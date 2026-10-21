package app.nagmedar.ui.transcode

/**
 * S-1303: libVLC's stream output for a conversion and for the HLS stream: the chain
 * (`#transcode{…}:std{…}`) and the media's options that go with it.
 *
 * A value in a chain is quoted and its quotes and backslashes escaped, as VLC's config chain
 * reads them (src/config/chain.c); a brace cannot be escaped there (it ends or opens a block
 * whatever the quotes), and neither may a control character be in a path: such a value is
 * refused. The paths are the app's own (its storage, a job's folder), so they never hold one.
 */
object SoutChain {

    /** The media options of converting into [destination] with [profile]: `:sout=…` first, then what leaves streams out. */
    fun convert(profile: TranscodeProfile, destination: String): List<String> {
        val output = "std{access=file,mux=${profile.container.mux},dst=${quote(destination)}}"
        return listOf(":sout=#" + chain(profile, output)) + leftOut(profile)
    }

    /**
     * The media options of the HLS stream: segments of [segmentSeconds] in [directory], a playlist
     * named [playlist] listing them by their names (relative to it, so any address serves them), a
     * window of [windowSegments] segments, the older deleted; paced by the input (the muxer's rate
     * control), so a file streams as it plays.
     */
    fun hls(profile: TranscodeProfile, directory: String, playlist: String = HLS_PLAYLIST, segmentSeconds: Int = 6, windowSegments: Int = 300): List<String> {
        require(segmentSeconds in 1..60 && windowSegments in 3..10_000)
        val dir = directory.trimEnd('/')
        val access = "livehttp{seglen=$segmentSeconds,delsegs=true,numsegs=$windowSegments,ratecontrol=true," +
            "index=${quote("$dir/$playlist")},index-url=${quote(HLS_SEGMENT)}}"
        val output = "std{access=$access,mux=ts{use-key-frames},dst=${quote("$dir/$HLS_SEGMENT")}}"
        return listOf(":sout=#" + chain(profile, output)) + leftOut(profile)
    }

    /** `transcode{…}:` before [output] when a stream is encoded; [output] alone when every stream is copied. */
    internal fun chain(profile: TranscodeProfile, output: String): String {
        val parts = mutableListOf<String>()
        profile.video?.takeIf { it.codec != VideoCodec.Copy }?.let { v ->
            parts += "vcodec=${v.codec.fourcc}"
            parts += "vb=${v.bitrateKbps}"
            v.maxHeight?.let { parts += "maxheight=$it" }
            v.fps?.let { parts += "fps=$it" }
        }
        profile.audio?.takeIf { it.codec != AudioCodec.Copy }?.let { a ->
            parts += "acodec=${a.codec.fourcc}"
            if (!a.codec.lossless) parts += "ab=${a.bitrateKbps}"
            parts += "channels=${a.channels}"
            // Opus encodes at 48 kHz only
            val rate = if (a.codec == AudioCodec.Opus) 48_000 else a.sampleRate
            rate?.let { parts += "samplerate=$it" }
        }
        if (profile.subtitles == SubtitleMode.Burn && profile.video != null && profile.video.codec != VideoCodec.Copy) parts += "soverlay"
        return if (parts.isEmpty()) output else "transcode{${parts.joinToString(",")}}:$output"
    }

    /** The options that keep the streams the profile has no place for out of the output. */
    private fun leftOut(profile: TranscodeProfile): List<String> = buildList {
        if (profile.video == null) add(":no-sout-video")
        if (profile.audio == null) add(":no-sout-audio")
        if (profile.subtitles != SubtitleMode.Keep) add(":no-sout-spu")
    }

    /** [value] quoted for a config chain; a brace or a control character is refused. */
    fun quote(value: String): String {
        require(value.none { it == '{' || it == '}' || it < ' ' || it == '\u007f' }) { "not a value a chain can hold" }
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"") + "'"
    }

    /**
     * [value] read back as VLC's chain reads a quoted value (config_StringUnescape): the tests'
     * check that a quoted value comes back as it went.
     */
    fun unquote(quoted: String): String {
        require(quoted.length >= 2 && quoted.first() == '\'' && quoted.last() == '\'')
        val inner = quoted.substring(1, quoted.length - 1)
        val out = StringBuilder()
        var i = 0
        while (i < inner.length) {
            val c = inner[i]
            if (c == '\\' && i + 1 < inner.length && inner[i + 1] in "'\"\\") {
                out.append(inner[i + 1])
                i += 2
            } else {
                // an unescaped quote of the same kind would have ended the value
                require(c != '\'') { "unescaped quote" }
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    /** The HLS playlist's name in the session's folder. */
    const val HLS_PLAYLIST = "stream.m3u8"

    /** The segments' names: livehttp puts the number for the #s. */
    const val HLS_SEGMENT = "seg-########.ts"
}
