// SP-05: on-device speech recognition benchmark with whisper.cpp.
//
// The first audio track of a media file is decoded with libavcodec and resampled to
// 16 kHz mono float, then transcribed. The report gives the time of every stage and
// the real-time factor (inference time / audio time) next to the transcript as SRT,
// so the decision note can weigh speed against accuracy per model.
#include "whisper_lab.h"

#include <whisper.h>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/channel_layout.h>
#include <libavutil/opt.h>
#include <libavutil/time.h>
#include <libswresample/swresample.h>
}

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <thread>
#include <vector>

namespace {

struct Run {
    wl_progress_fn progress;
    void *opaque;
    volatile int *cancel;
};

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

std::string srt_time(int64_t centis)
{
    int64_t ms = centis * 10;
    char buf[32];
    snprintf(buf, sizeof(buf), "%02lld:%02lld:%02lld,%03lld", (long long)(ms / 3600000), (long long)(ms / 60000 % 60),
             (long long)(ms / 1000 % 60), (long long)(ms % 1000));
    return buf;
}

std::string av_error(int err)
{
    char buf[AV_ERROR_MAX_STRING_SIZE] = {0};
    av_strerror(err, buf, sizeof(buf));
    return buf;
}

/* Decodes up to max_seconds of the best audio stream to 16 kHz mono float. */
int decode_audio(const char *input, double max_seconds, std::vector<float> &pcm, std::string &error, Run &run)
{
    AVFormatContext *ic = nullptr;
    AVCodecContext *dec = nullptr;
    SwrContext *swr = nullptr;
    AVPacket *pkt = av_packet_alloc();
    AVFrame *frame = av_frame_alloc();
    int ret = avformat_open_input(&ic, input, nullptr, nullptr);
    int stream = -1;
    const AVCodec *codec = nullptr;
    AVChannelLayout mono = AV_CHANNEL_LAYOUT_MONO;
    size_t limit = max_seconds > 0 ? static_cast<size_t>(max_seconds * WHISPER_SAMPLE_RATE) : SIZE_MAX;
    double total = 0;
    if (ret < 0) {
        error = "open input: " + av_error(ret);
        goto end;
    }
    if ((ret = avformat_find_stream_info(ic, nullptr)) < 0) {
        error = "stream info: " + av_error(ret);
        goto end;
    }
    stream = av_find_best_stream(ic, AVMEDIA_TYPE_AUDIO, -1, -1, &codec, 0);
    if (stream < 0 || !codec) {
        ret = stream < 0 ? stream : AVERROR_DECODER_NOT_FOUND;
        error = "no decodable audio track: " + av_error(ret);
        goto end;
    }
    dec = avcodec_alloc_context3(codec);
    avcodec_parameters_to_context(dec, ic->streams[stream]->codecpar);
    if ((ret = avcodec_open2(dec, codec, nullptr)) < 0) {
        error = "open decoder: " + av_error(ret);
        goto end;
    }
    if ((ret = swr_alloc_set_opts2(&swr, &mono, AV_SAMPLE_FMT_FLT, WHISPER_SAMPLE_RATE, &dec->ch_layout, dec->sample_fmt,
                                   dec->sample_rate, 0, nullptr)) < 0 || (ret = swr_init(swr)) < 0) {
        error = "resampler: " + av_error(ret);
        goto end;
    }
    total = ic->duration > 0 ? ic->duration / (double)AV_TIME_BASE : 0;
    if (max_seconds > 0 && (total == 0 || max_seconds < total))
        total = max_seconds;
    while (pcm.size() < limit && !(run.cancel && *run.cancel)) {
        ret = av_read_frame(ic, pkt);
        bool eof = ret == AVERROR_EOF;
        if (ret < 0 && !eof) {
            error = "read: " + av_error(ret);
            goto end;
        }
        if (!eof && pkt->stream_index != stream) {
            av_packet_unref(pkt);
            continue;
        }
        ret = avcodec_send_packet(dec, eof ? nullptr : pkt);
        av_packet_unref(pkt);
        if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
            /* a damaged packet: skip it */
            continue;
        }
        while ((ret = avcodec_receive_frame(dec, frame)) >= 0) {
            int out_samples = swr_get_out_samples(swr, frame->nb_samples);
            size_t old = pcm.size();
            pcm.resize(old + out_samples);
            uint8_t *out[1] = {reinterpret_cast<uint8_t *>(pcm.data() + old)};
            int got = swr_convert(swr, out, out_samples, const_cast<const uint8_t **>(frame->extended_data), frame->nb_samples);
            pcm.resize(old + (got > 0 ? got : 0));
            av_frame_unref(frame);
        }
        if (run.progress && total > 0)
            run.progress(run.opaque, 0.15 * std::min(1.0, pcm.size() / (total * WHISPER_SAMPLE_RATE)), "decode");
        if (eof)
            break;
    }
    if (pcm.size() > limit)
        pcm.resize(limit);
    ret = 0;
end:
    swr_free(&swr);
    avcodec_free_context(&dec);
    avformat_close_input(&ic);
    av_packet_free(&pkt);
    av_frame_free(&frame);
    return ret;
}

} // namespace

extern "C" char *whisper_lab_run(const char *model, const char *input, const char *language, double max_seconds, int threads,
                                 wl_progress_fn progress, void *opaque, volatile int *cancel)
{
    Run run{progress, opaque, cancel};
    std::string error;
    std::vector<float> pcm;
    int64_t t0 = av_gettime_relative();
    decode_audio(input, max_seconds, pcm, error, run);
    int64_t t1 = av_gettime_relative();
    double audio_seconds = pcm.size() / (double)WHISPER_SAMPLE_RATE;
    double load_seconds = 0, inference_seconds = 0;
    std::string srt, text;
    int segments = 0;
    if (threads <= 0)
        threads = (int)std::max(1u, std::min(8u, std::thread::hardware_concurrency()));

    if (error.empty() && pcm.empty())
        error = "no audio decoded";
    if (error.empty()) {
        whisper_context_params cparams = whisper_context_default_params();
        cparams.use_gpu = false;
        whisper_context *ctx = whisper_init_from_file_with_params(model, cparams);
        int64_t t2 = av_gettime_relative();
        load_seconds = (t2 - t1) / 1e6;
        if (!ctx) {
            error = "cannot load the model";
        } else {
            whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
            params.language = language && *language ? language : "auto";
            params.translate = false;
            params.n_threads = threads;
            params.print_progress = false;
            params.print_realtime = false;
            params.print_timestamps = false;
            params.print_special = false;
            params.progress_callback = [](whisper_context *, whisper_state *, int p, void *user) {
                auto *r = static_cast<Run *>(user);
                if (r->progress)
                    r->progress(r->opaque, 0.15 + 0.85 * p / 100.0, "transcribe");
            };
            params.progress_callback_user_data = &run;
            params.abort_callback = [](void *user) -> bool {
                auto *r = static_cast<Run *>(user);
                return r->cancel && *r->cancel;
            };
            params.abort_callback_user_data = &run;
            int rc = whisper_full(ctx, params, pcm.data(), (int)pcm.size());
            inference_seconds = (av_gettime_relative() - t2) / 1e6;
            if (rc != 0) {
                error = cancel && *cancel ? "cancelled" : "whisper_full failed (" + std::to_string(rc) + ")";
            }
            segments = whisper_full_n_segments(ctx);
            for (int i = 0; i < segments; i++) {
                const char *seg = whisper_full_get_segment_text(ctx, i);
                srt += std::to_string(i + 1) + "\n" + srt_time(whisper_full_get_segment_t0(ctx, i)) + " --> " +
                       srt_time(whisper_full_get_segment_t1(ctx, i)) + "\n" + (seg ? seg : "") + "\n\n";
                text += seg ? seg : "";
            }
            whisper_free(ctx);
        }
    }
    double rtf = audio_seconds > 0 ? inference_seconds / audio_seconds : 0;
    std::string json = "{\"model\":\"" + json_escape(model) + "\",\"input\":\"" + json_escape(input) + "\",\"language\":\"" +
                       json_escape(language ? language : "") + "\",\"threads\":" + std::to_string(threads) +
                       ",\"audio_seconds\":" + std::to_string(audio_seconds) + ",\"decode_seconds\":" +
                       std::to_string((t1 - t0) / 1e6) + ",\"load_seconds\":" + std::to_string(load_seconds) +
                       ",\"inference_seconds\":" + std::to_string(inference_seconds) + ",\"real_time_factor\":" +
                       std::to_string(rtf) + ",\"segments\":" + std::to_string(segments) + ",\"system\":\"" +
                       json_escape(whisper_print_system_info()) + "\",\"error\":\"" + json_escape(error) + "\",\"text\":\"" +
                       json_escape(text) + "\",\"srt\":\"" + json_escape(srt) + "\"}";
    char *out = static_cast<char *>(malloc(json.size() + 1));
    if (out)
        memcpy(out, json.c_str(), json.size() + 1);
    return out;
}
