// S-709: subtitles from a film's speech with whisper.cpp, on the device.
//
// The audio track chosen is decoded with libavcodec and resampled to 16 kHz mono float five
// minutes at a time; each piece is transcribed and its lines handed out with their times in the
// film. The last line of a piece may be cut in the middle of a word, so it is not handed out:
// the next piece starts where the line before it ended. Memory stays the same whatever the
// film's length: a piece of audio (19 MB) and whisper's own buffers for it.
#include "whisper_subs.h"

#include <whisper.h>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/channel_layout.h>
#include <libswresample/swresample.h>
}

#include <algorithm>
#include <cstddef>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <thread>
#include <vector>

namespace {

constexpr int RATE = WHISPER_SAMPLE_RATE;
/** Five minutes of audio transcribed at a time. */
constexpr size_t PIECE = 300 * static_cast<size_t>(RATE);
/** Less than half a second left at the end is not worth a transcription. */
constexpr size_t MIN_TAIL = RATE / 2;
/** whisper's times are in hundredths of a second. */
constexpr int64_t SAMPLES_PER_CENTI = RATE / 100;
/** A line at most this long (bytes of UTF-8): two lines of a subtitle, about 42 characters each. */
constexpr int MAX_LINE_BYTES = 84;

std::string json_escape(const std::string &s)
{
    std::string out;
    out.reserve(s.size() + 16);
    for (unsigned char c : s) {
        switch (c) {
        case '"': out += "\\\""; break;
        case '\\': out += "\\\\"; break;
        case '\n': out += "\\n"; break;
        case '\r': out += "\\r"; break;
        case '\t': out += "\\t"; break;
        default:
            if (c < 0x20) {
                char buf[8];
                snprintf(buf, sizeof(buf), "\\u%04x", c);
                out += buf;
            } else {
                out += static_cast<char>(c);
            }
        }
    }
    return out;
}

std::string av_error(int err)
{
    char buf[AV_ERROR_MAX_STRING_SIZE] = {0};
    av_strerror(err, buf, sizeof(buf));
    return buf;
}

/** The film's audio track as it is read: the demuxer, the decoder and the resampler to 16 kHz mono. */
struct Source {
    AVFormatContext *ic = nullptr;
    AVCodecContext *dec = nullptr;
    SwrContext *swr = nullptr;
    AVPacket *pkt = nullptr;
    AVFrame *frame = nullptr;
    int stream = -1;
    /** Every packet read: the decoder is giving out what it still holds. */
    bool draining = false;
    /** The decoder and the resampler are empty: the track is over. */
    bool eof = false;
    /** The resampler's input as it was set up; a decoder may change it on the way. */
    AVChannelLayout layout{};
    int format = -1;
    int rate = 0;
    /** The film's length in seconds; 0 when it does not say. */
    double total = 0;
    /** Where the track starts in the film, milliseconds. */
    int64_t lead_ms = 0;

    Source() = default;
    Source(const Source &) = delete;
    Source &operator=(const Source &) = delete;

    ~Source()
    {
        swr_free(&swr);
        avcodec_free_context(&dec);
        avformat_close_input(&ic);
        av_packet_free(&pkt);
        av_frame_free(&frame);
        av_channel_layout_uninit(&layout);
    }
};

/** The [track]-th audio stream (0 first), or FFmpeg's best one for -1; a negative AVERROR when there is none. */
int pick_stream(AVFormatContext *ic, int track)
{
    if (track < 0)
        return av_find_best_stream(ic, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);
    int n = 0;
    for (unsigned i = 0; i < ic->nb_streams; i++) {
        if (ic->streams[i]->codecpar->codec_type != AVMEDIA_TYPE_AUDIO)
            continue;
        if (n++ == track)
            return static_cast<int>(i);
    }
    return AVERROR_STREAM_NOT_FOUND;
}

bool open_source(Source &s, const char *input, int track, std::string &code, std::string &detail)
{
    int ret = avformat_open_input(&s.ic, input, nullptr, nullptr);
    if (ret < 0 || (ret = avformat_find_stream_info(s.ic, nullptr)) < 0) {
        code = "open";
        detail = av_error(ret);
        return false;
    }
    s.stream = pick_stream(s.ic, track);
    if (s.stream < 0) {
        code = "no_audio";
        detail = av_error(s.stream);
        return false;
    }
    AVStream *st = s.ic->streams[s.stream];
    const AVCodec *codec = avcodec_find_decoder(st->codecpar->codec_id);
    if (!codec) {
        code = "decoder";
        detail = avcodec_get_name(st->codecpar->codec_id);
        return false;
    }
    s.dec = avcodec_alloc_context3(codec);
    s.pkt = av_packet_alloc();
    s.frame = av_frame_alloc();
    if (!s.dec || !s.pkt || !s.frame) {
        code = "decoder";
        detail = "out of memory";
        return false;
    }
    if ((ret = avcodec_parameters_to_context(s.dec, st->codecpar)) < 0 || (ret = avcodec_open2(s.dec, codec, nullptr)) < 0) {
        code = "decoder";
        detail = std::string(codec->name) + ": " + av_error(ret);
        return false;
    }
    // the picture and the other tracks are not read
    for (unsigned i = 0; i < s.ic->nb_streams; i++)
        if (static_cast<int>(i) != s.stream)
            s.ic->streams[i]->discard = AVDISCARD_ALL;
    s.total = s.ic->duration > 0 ? s.ic->duration / static_cast<double>(AV_TIME_BASE) : 0;
    if (st->start_time != AV_NOPTS_VALUE && s.ic->start_time != AV_NOPTS_VALUE) {
        int64_t start = av_rescale_q(st->start_time, st->time_base, AV_TIME_BASE_Q);
        s.lead_ms = std::max<int64_t>(0, (start - s.ic->start_time) / 1000);
    }
    return true;
}

/** [f] resampled onto the end of [pcm]; null for what the resampler still holds. */
bool resample(Source &s, std::vector<float> &pcm, const AVFrame *f, std::string &detail)
{
    if (f) {
        AVChannelLayout in{};
        if (f->ch_layout.order == AV_CHANNEL_ORDER_UNSPEC || f->ch_layout.nb_channels <= 0) {
            int channels = f->ch_layout.nb_channels > 0 ? f->ch_layout.nb_channels : s.dec->ch_layout.nb_channels;
            av_channel_layout_default(&in, std::max(1, channels));
        } else if (av_channel_layout_copy(&in, &f->ch_layout) < 0) {
            detail = "resampler: out of memory";
            return false;
        }
        bool same = s.swr && f->format == s.format && f->sample_rate == s.rate && av_channel_layout_compare(&in, &s.layout) == 0;
        if (same) {
            av_channel_layout_uninit(&in);
        } else {
            swr_free(&s.swr);
            AVChannelLayout mono = AV_CHANNEL_LAYOUT_MONO;
            int ret = swr_alloc_set_opts2(&s.swr, &mono, AV_SAMPLE_FMT_FLT, RATE, &in, static_cast<AVSampleFormat>(f->format),
                                          f->sample_rate, 0, nullptr);
            if (ret >= 0)
                ret = swr_init(s.swr);
            if (ret < 0) {
                swr_free(&s.swr);
                av_channel_layout_uninit(&in);
                detail = "resampler: " + av_error(ret);
                return false;
            }
            av_channel_layout_uninit(&s.layout);
            s.layout = in;
            s.format = f->format;
            s.rate = f->sample_rate;
        }
    }
    if (!s.swr)
        return true;
    int room = swr_get_out_samples(s.swr, f ? f->nb_samples : 0);
    if (room <= 0)
        return true;
    size_t old = pcm.size();
    pcm.resize(old + room);
    uint8_t *out[1] = {reinterpret_cast<uint8_t *>(pcm.data() + old)};
    int got = swr_convert(s.swr, out, room, f ? const_cast<const uint8_t **>(f->extended_data) : nullptr, f ? f->nb_samples : 0);
    pcm.resize(old + (got > 0 ? got : 0));
    return true;
}

/** Decodes onto [pcm] until it holds [want] samples, the track ends or [cancel] is set; false when the audio cannot be resampled. */
bool decode_more(Source &s, std::vector<float> &pcm, size_t want, volatile int *cancel, std::string &detail)
{
    while (pcm.size() < want && !s.eof) {
        if (cancel && *cancel)
            return true;
        int ret = avcodec_receive_frame(s.dec, s.frame);
        if (ret >= 0) {
            bool ok = resample(s, pcm, s.frame, detail);
            av_frame_unref(s.frame);
            if (!ok)
                return false;
            continue;
        }
        if (ret == AVERROR_EOF || (s.draining && ret != AVERROR(EAGAIN))) {
            resample(s, pcm, nullptr, detail);
            s.eof = true;
            break;
        }
        // the decoder wants a packet (or failed on one: the next one goes on)
        ret = av_read_frame(s.ic, s.pkt);
        if (ret < 0) {
            // the end of the file, or a damaged end: what was read is what there is
            avcodec_send_packet(s.dec, nullptr);
            s.draining = true;
            continue;
        }
        if (s.pkt->stream_index == s.stream)
            avcodec_send_packet(s.dec, s.pkt);
        av_packet_unref(s.pkt);
    }
    return true;
}

/** What whisper's callbacks need: where the piece is in the film, and the cancel flag. */
struct Run {
    const WsCallbacks *cb;
    volatile int *cancel;
    double before;
    double length;
    double total;
};

} // namespace

extern "C" char *whisper_subs_run(const char *model, const char *input, int audio_track, const char *language, int translate, int threads,
                                  const WsCallbacks *cb, volatile int *cancel)
{
    std::string code, detail;
    std::string lang = language && *language ? language : "auto";
    double audio_seconds = 0;
    int segments = 0;
    Source s;
    whisper_context *ctx = nullptr;
    if (open_source(s, input, audio_track, code, detail)) {
        whisper_context_params cparams = whisper_context_default_params();
        cparams.use_gpu = false;
        ctx = whisper_init_from_file_with_params(model, cparams);
        if (!ctx) {
            code = "model";
            detail = "the model cannot be read";
        }
    }
    if (code.empty()) {
        if (threads <= 0)
            threads = static_cast<int>(std::max(1u, std::min(4u, std::thread::hardware_concurrency())));
        Run run{cb, cancel, 0, 0, s.total};
        bool detect = lang == "auto";
        std::vector<float> pcm;
        pcm.reserve(PIECE + RATE);
        /* samples of the track before pcm[0] */
        int64_t offset = 0;
        while (true) {
            if (!decode_more(s, pcm, PIECE, cancel, detail)) {
                code = "decoder";
                break;
            }
            if (cancel && *cancel) {
                code = "cancelled";
                break;
            }
            if (s.eof && pcm.size() < MIN_TAIL) {
                audio_seconds = (offset + static_cast<int64_t>(pcm.size())) / static_cast<double>(RATE);
                break;
            }
            whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
            p.language = lang.c_str();
            p.translate = translate != 0;
            p.n_threads = threads;
            p.print_progress = false;
            p.print_realtime = false;
            p.print_timestamps = false;
            p.print_special = false;
            // lines of a subtitle's length, cut between words, timed by their words
            p.token_timestamps = true;
            p.max_len = MAX_LINE_BYTES;
            p.split_on_word = true;
            run.before = offset / static_cast<double>(RATE);
            run.length = pcm.size() / static_cast<double>(RATE);
            p.progress_callback = [](whisper_context *, whisper_state *, int percent, void *user) {
                auto *r = static_cast<Run *>(user);
                if (r->cb && r->cb->progress)
                    r->cb->progress(r->cb->opaque, r->before + r->length * percent / 100.0, r->total);
            };
            p.progress_callback_user_data = &run;
            p.abort_callback = [](void *user) -> bool {
                auto *r = static_cast<Run *>(user);
                return r->cancel && *r->cancel;
            };
            p.abort_callback_user_data = &run;
            if (cb && cb->progress)
                cb->progress(cb->opaque, run.before, s.total);
            int rc = whisper_full(ctx, p, pcm.data(), static_cast<int>(pcm.size()));
            if (cancel && *cancel) {
                code = "cancelled";
                break;
            }
            if (rc != 0) {
                code = "whisper";
                detail = "whisper_full: " + std::to_string(rc);
                break;
            }
            int n = whisper_full_n_segments(ctx);
            if (detect && n > 0) {
                const char *heard = whisper_lang_str(whisper_full_lang_id(ctx));
                if (heard) {
                    // the rest of the film in the language its first lines were heard in
                    lang = heard;
                    detect = false;
                    if (cb && cb->language)
                        cb->language(cb->opaque, heard);
                }
            }
            // the last line may be cut at the piece's end: the next piece starts where the one before it ended
            int keep = n;
            size_t cut = pcm.size();
            if (!s.eof && n >= 2) {
                int64_t end = std::max<int64_t>(0, whisper_full_get_segment_t1(ctx, n - 2)) * SAMPLES_PER_CENTI;
                if (end >= static_cast<int64_t>(PIECE / 4) && end < static_cast<int64_t>(pcm.size())) {
                    keep = n - 1;
                    cut = static_cast<size_t>(end);
                }
            }
            int64_t base_ms = offset * 1000 / RATE + s.lead_ms;
            int64_t limit_ms = static_cast<int64_t>(cut) * 1000 / RATE;
            for (int i = 0; i < keep; i++) {
                const char *text = whisper_full_get_segment_text(ctx, i);
                int64_t t0 = std::clamp<int64_t>(whisper_full_get_segment_t0(ctx, i) * 10, 0, limit_ms);
                int64_t t1 = std::clamp<int64_t>(whisper_full_get_segment_t1(ctx, i) * 10, t0, limit_ms);
                if (text && cb && cb->segment)
                    cb->segment(cb->opaque, base_ms + t0, base_ms + t1, text);
                segments++;
            }
            offset += static_cast<int64_t>(cut);
            pcm.erase(pcm.begin(), pcm.begin() + static_cast<std::ptrdiff_t>(cut));
            audio_seconds = offset / static_cast<double>(RATE);
            if (cb && cb->progress)
                cb->progress(cb->opaque, audio_seconds, s.total);
            if (s.eof && pcm.empty())
                break;
        }
    }
    if (ctx)
        whisper_free(ctx);
    std::string json = "{\"error\":\"" + json_escape(code) + "\",\"detail\":\"" + json_escape(detail) + "\",\"language\":\"" +
                       json_escape(lang) + "\",\"audio_seconds\":" + std::to_string(audio_seconds) +
                       ",\"segments\":" + std::to_string(segments) + "}";
    char *out = static_cast<char *>(malloc(json.size() + 1));
    if (out)
        memcpy(out, json.c_str(), json.size() + 1);
    return out;
}
