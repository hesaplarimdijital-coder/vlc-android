// S-802: what a media file holds, for the library's technical facts.
//
// libavformat reads the container and the codec parameters (the audio decoders tell Atmos and
// DTS:X apart through the profile). Nağmedar 1.0 plays sound only: a file's video stream is
// neither read nor reported.
#include "mediaprobe.h"

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/bprint.h>
#include <libavutil/channel_layout.h>

#include <stdlib.h>
#include <string.h>

/* ---- JSON ----------------------------------------------------------------------------------- */

static void json_text(AVBPrint *bp, const char *s)
{
    av_bprint_chars(bp, '"', 1);
    for (const unsigned char *p = (const unsigned char *)(s ? s : ""); *p; p++) {
        if (*p == '"' || *p == '\\')
            av_bprintf(bp, "\\%c", *p);
        else if (*p < 0x20)
            av_bprintf(bp, "\\u%04x", *p);
        else
            av_bprint_chars(bp, (char)*p, 1);
    }
    av_bprint_chars(bp, '"', 1);
}

static void key(AVBPrint *bp, int *first, const char *name)
{
    if (!*first)
        av_bprint_chars(bp, ',', 1);
    *first = 0;
    av_bprintf(bp, "\"%s\":", name);
}

static void put_text(AVBPrint *bp, int *first, const char *name, const char *value)
{
    key(bp, first, name);
    json_text(bp, value);
}

static void put_int(AVBPrint *bp, int *first, const char *name, long long value)
{
    key(bp, first, name);
    av_bprintf(bp, "%lld", value);
}

static void put_bool(AVBPrint *bp, int *first, const char *name, int value)
{
    key(bp, first, name);
    av_bprintf(bp, "%s", value ? "true" : "false");
}

static const char *tag(const AVDictionary *metadata, const char *name)
{
    const AVDictionaryEntry *e = av_dict_get(metadata, name, NULL, 0);
    return e ? e->value : "";
}

static void write_audio(AVBPrint *bp, const AVStream *st)
{
    const AVCodecParameters *par = st->codecpar;
    int first = 1;
    char layout[64] = "";
    if (av_channel_layout_describe(&par->ch_layout, layout, sizeof(layout)) < 0)
        layout[0] = 0;
    av_bprint_chars(bp, '{', 1);
    put_text(bp, &first, "codec", avcodec_get_name(par->codec_id));
    put_text(bp, &first, "profile", avcodec_profile_name(par->codec_id, par->profile));
    put_int(bp, &first, "channels", par->ch_layout.nb_channels);
    put_text(bp, &first, "layout", layout);
    put_int(bp, &first, "sample_rate", par->sample_rate);
    put_int(bp, &first, "bits", par->bits_per_raw_sample);
    put_int(bp, &first, "bitrate", par->bit_rate);
    put_text(bp, &first, "language", tag(st->metadata, "language"));
    put_text(bp, &first, "title", tag(st->metadata, "title"));
    put_bool(bp, &first, "default", st->disposition & AV_DISPOSITION_DEFAULT);
    put_bool(bp, &first, "forced", st->disposition & AV_DISPOSITION_FORCED);
    put_bool(bp, &first, "commentary", st->disposition & AV_DISPOSITION_COMMENT);
    av_bprint_chars(bp, '}', 1);
}

static void write_subtitle(AVBPrint *bp, const AVStream *st)
{
    int first = 1;
    av_bprint_chars(bp, '{', 1);
    put_text(bp, &first, "codec", avcodec_get_name(st->codecpar->codec_id));
    put_text(bp, &first, "language", tag(st->metadata, "language"));
    put_text(bp, &first, "title", tag(st->metadata, "title"));
    put_bool(bp, &first, "default", st->disposition & AV_DISPOSITION_DEFAULT);
    put_bool(bp, &first, "forced", st->disposition & AV_DISPOSITION_FORCED);
    put_bool(bp, &first, "hearing_impaired", st->disposition & AV_DISPOSITION_HEARING_IMPAIRED);
    av_bprint_chars(bp, '}', 1);
}

static char *finish(AVBPrint *bp)
{
    char *json = av_bprint_is_complete(bp) ? strdup(bp->str) : NULL;
    av_bprint_finalize(bp, NULL);
    return json;
}

static char *error_json(const char *what, int err)
{
    char msg[AV_ERROR_MAX_STRING_SIZE] = {0}, text[AV_ERROR_MAX_STRING_SIZE + 64];
    av_strerror(err, msg, sizeof(msg));
    snprintf(text, sizeof(text), "%s: %s", what, msg);
    AVBPrint bp;
    av_bprint_init(&bp, 0, AV_BPRINT_SIZE_UNLIMITED);
    int first = 1;
    av_bprint_chars(&bp, '{', 1);
    put_int(&bp, &first, "version", MEDIA_PROBE_VERSION);
    put_text(&bp, &first, "error", text);
    av_bprint_chars(&bp, '}', 1);
    return finish(&bp);
}

char *media_probe(const char *input, volatile int *cancel)
{
    AVFormatContext *ic = NULL;
    AVDictionary *opts = NULL;
    int ret;
    /* enough for the stream info of a Blu-ray copy, without reading whole minutes of it */
    av_dict_set(&opts, "probesize", "20000000", 0);
    av_dict_set(&opts, "analyzeduration", "10000000", 0);
    ret = avformat_open_input(&ic, input, NULL, &opts);
    av_dict_free(&opts);
    if (ret < 0)
        return error_json("open", ret);
    if ((ret = avformat_find_stream_info(ic, NULL)) < 0) {
        avformat_close_input(&ic);
        return error_json("stream info", ret);
    }

    /* the stream info is all a probe reads: [cancel] has nothing longer to stop */
    (void)cancel;

    AVBPrint bp;
    av_bprint_init(&bp, 0, AV_BPRINT_SIZE_UNLIMITED);
    int first = 1;
    av_bprint_chars(&bp, '{', 1);
    put_int(&bp, &first, "version", MEDIA_PROBE_VERSION);
    put_text(&bp, &first, "error", "");
    put_text(&bp, &first, "format", ic->iformat ? ic->iformat->name : "");
    put_text(&bp, &first, "brand", tag(ic->metadata, "major_brand"));
    put_int(&bp, &first, "duration_ms", ic->duration != AV_NOPTS_VALUE ? ic->duration / 1000 : 0);
    put_int(&bp, &first, "size", ic->pb ? avio_size(ic->pb) : 0);
    put_int(&bp, &first, "bitrate", ic->bit_rate);
    put_int(&bp, &first, "chapters", ic->nb_chapters);
    key(&bp, &first, "audio");
    av_bprint_chars(&bp, '[', 1);
    int n = 0;
    for (unsigned i = 0; i < ic->nb_streams; i++) {
        if (ic->streams[i]->codecpar->codec_type != AVMEDIA_TYPE_AUDIO)
            continue;
        if (n++)
            av_bprint_chars(&bp, ',', 1);
        write_audio(&bp, ic->streams[i]);
    }
    av_bprint_chars(&bp, ']', 1);
    key(&bp, &first, "subtitles");
    av_bprint_chars(&bp, '[', 1);
    n = 0;
    for (unsigned i = 0; i < ic->nb_streams; i++) {
        if (ic->streams[i]->codecpar->codec_type != AVMEDIA_TYPE_SUBTITLE)
            continue;
        if (n++)
            av_bprint_chars(&bp, ',', 1);
        write_subtitle(&bp, ic->streams[i]);
    }
    av_bprint_chars(&bp, ']', 1);
    av_bprint_chars(&bp, '}', 1);
    avformat_close_input(&ic);
    return finish(&bp);
}
