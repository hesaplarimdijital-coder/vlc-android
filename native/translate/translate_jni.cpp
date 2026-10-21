// JNI entry points of libnagmedar_translate.so (app.nagmedar.translate.NativeTranslate, S-710).
//
// A model is a folder with a CTranslate2 conversion of an OPUS-MT (Marian) model: model.bin,
// config.json, shared_vocabulary.json, and the SentencePiece models of its two languages,
// source.spm and target.spm. A line is cut into the source model's pieces, ended with "</s>"
// as Marian wants, translated in a batch with the others, and the target pieces joined again.
// Texts cross JNI as UTF-8 bytes, one line per "\n": JNI's own strings are not quite UTF-8.
#include <jni.h>

#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

#include <ctranslate2/translator.h>
#include <sentencepiece_processor.h>

namespace {

// a subtitle line is short; these keep a broken or a hostile file from running for ever
constexpr size_t kMaxSourcePieces = 400;
constexpr size_t kMaxDecodedPieces = 256;

struct Engine {
    std::unique_ptr<ctranslate2::Translator> translator;
    sentencepiece::SentencePieceProcessor source;
    sentencepiece::SentencePieceProcessor target;
};

void throw_io(JNIEnv *env, const std::string &message) {
    if (env->ExceptionCheck()) return;
    jclass type = env->FindClass("java/io/IOException");
    if (type != nullptr) env->ThrowNew(type, message.c_str());
}

std::string utf(JNIEnv *env, jstring text) {
    if (text == nullptr) return {};
    const char *chars = env->GetStringUTFChars(text, nullptr);
    if (chars == nullptr) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(text, chars);
    return out;
}

std::vector<std::string> split_lines(const std::string &text) {
    std::vector<std::string> lines;
    size_t from = 0;
    while (true) {
        size_t at = text.find('\n', from);
        if (at == std::string::npos) {
            lines.push_back(text.substr(from));
            break;
        }
        lines.push_back(text.substr(from, at - from));
        from = at + 1;
    }
    return lines;
}

std::unique_ptr<ctranslate2::Translator> load(const std::string &folder, size_t threads) {
    ctranslate2::ReplicaPoolConfig config;
    config.num_threads_per_replica = threads;
    try {
        return std::make_unique<ctranslate2::Translator>(folder, ctranslate2::Device::CPU, ctranslate2::ComputeType::INT8,
                                                         std::vector<int>{0}, false, config);
    } catch (const std::invalid_argument &) {
        // a CPU without int8 support: the model's own type
        return std::make_unique<ctranslate2::Translator>(folder, ctranslate2::Device::CPU, ctranslate2::ComputeType::DEFAULT,
                                                         std::vector<int>{0}, false, config);
    }
}

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL Java_app_nagmedar_translate_NativeTranslate_version(JNIEnv *env, jclass) {
    std::string text = std::string("CTranslate2 ") + NAGMEDAR_CT2_VERSION + " · SentencePiece " + NAGMEDAR_SPM_VERSION;
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT jlong JNICALL Java_app_nagmedar_translate_NativeTranslate_open(JNIEnv *env, jclass, jstring jfolder, jint threads) {
    const std::string folder = utf(env, jfolder);
    try {
        auto engine = std::make_unique<Engine>();
        auto status = engine->source.Load(folder + "/source.spm");
        if (!status.ok()) throw std::runtime_error("source.spm: " + status.ToString());
        status = engine->target.Load(folder + "/target.spm");
        if (!status.ok()) throw std::runtime_error("target.spm: " + status.ToString());
        engine->translator = load(folder, threads > 0 ? static_cast<size_t>(threads) : 1);
        return reinterpret_cast<jlong>(engine.release());
    } catch (const std::exception &e) {
        throw_io(env, e.what());
    } catch (...) {
        throw_io(env, "the model could not be loaded");
    }
    return 0;
}

// [input]: UTF-8 lines apart by "\n"; the answer has as many, in the same order (an empty line stays empty)
JNIEXPORT jbyteArray JNICALL Java_app_nagmedar_translate_NativeTranslate_translate(JNIEnv *env, jclass, jlong handle, jbyteArray input, jint beam) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine == nullptr || input == nullptr) {
        throw_io(env, "no model");
        return nullptr;
    }
    std::string text;
    {
        const jsize size = env->GetArrayLength(input);
        text.resize(static_cast<size_t>(size));
        if (size > 0) env->GetByteArrayRegion(input, 0, size, reinterpret_cast<jbyte *>(&text[0]));
        if (env->ExceptionCheck()) return nullptr;
    }
    try {
        const std::vector<std::string> lines = split_lines(text);
        std::vector<std::vector<std::string>> batch;
        std::vector<size_t> where;
        for (size_t i = 0; i < lines.size(); i++) {
            if (lines[i].find_first_not_of(" \t\r") == std::string::npos) continue;
            std::vector<std::string> pieces;
            auto status = engine->source.Encode(lines[i], &pieces);
            if (!status.ok()) throw std::runtime_error(status.ToString());
            if (pieces.size() > kMaxSourcePieces) pieces.resize(kMaxSourcePieces);
            pieces.emplace_back("</s>");
            batch.push_back(std::move(pieces));
            where.push_back(i);
        }
        std::vector<std::string> out(lines.size());
        if (!batch.empty()) {
            ctranslate2::TranslationOptions options;
            options.beam_size = beam > 0 ? static_cast<size_t>(beam) : 1;
            options.max_decoding_length = kMaxDecodedPieces;
            options.max_input_length = kMaxSourcePieces + 1;
            options.num_hypotheses = 1;
            const auto results = engine->translator->translate_batch(batch, options);
            for (size_t n = 0; n < results.size() && n < where.size(); n++) {
                if (results[n].hypotheses.empty()) continue;
                std::string decoded;
                auto status = engine->target.Decode(results[n].hypotheses[0], &decoded);
                if (!status.ok()) throw std::runtime_error(status.ToString());
                // a line break in a translation would be read as another line
                for (char &c : decoded) {
                    if (c == '\n' || c == '\r') c = ' ';
                }
                out[where[n]] = std::move(decoded);
            }
        }
        std::string joined;
        for (size_t i = 0; i < out.size(); i++) {
            if (i > 0) joined.push_back('\n');
            joined += out[i];
        }
        jbyteArray answer = env->NewByteArray(static_cast<jsize>(joined.size()));
        if (answer == nullptr) return nullptr;
        if (!joined.empty()) env->SetByteArrayRegion(answer, 0, static_cast<jsize>(joined.size()), reinterpret_cast<const jbyte *>(joined.data()));
        return answer;
    } catch (const std::exception &e) {
        throw_io(env, e.what());
    } catch (...) {
        throw_io(env, "the translation failed");
    }
    return nullptr;
}

JNIEXPORT void JNICALL Java_app_nagmedar_translate_NativeTranslate_close(JNIEnv *, jclass, jlong handle) {
    delete reinterpret_cast<Engine *>(handle);
}

}  // extern "C"
