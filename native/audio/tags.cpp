/*
 * Nağmedar Ses Motoru: ReplayGain tags written in place with TagLib (S-907); the lyrics, covers
 * and tags read (S-1003, S-1006); a converted file's tags copied from its source (S-1010).
 *
 * The file comes as a descriptor (a content document's on Android, which cannot be reopened
 * by path), so TagLib reads and writes it through FdStream, an IOStream over pread/pwrite.
 * The tags go through TagLib's property map, which puts them where each format keeps them:
 * TXXX frames in ID3v2 (MP3, AIFF, WAV, DSF), Vorbis comments (FLAC, Ogg), APEv2 (WavPack,
 * Musepack, APE), freeform atoms in MP4. For Opus the R128_* gains (Q7.8, relative to
 * -23 LUFS) are written as well, which is what its players read.
 */
#include "nagaudio.h"

#include <taglib/aifffile.h>
#include <taglib/fileref.h>
#include <taglib/id3v2tag.h>
#include <taglib/mpegfile.h>
#include <taglib/synchronizedlyricsframe.h>
#include <taglib/tiostream.h>
#include <taglib/tpropertymap.h>
#include <taglib/tstring.h>
#include <taglib/tvariant.h>
#include <taglib/wavfile.h>

#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <strings.h>
#include <unistd.h>

namespace {

class FdStream : public TagLib::IOStream {
public:
    FdStream(int fd, const char *name) : fd_(fd), name_(name ? name : "") {}

    TagLib::FileName name() const override { return name_.c_str(); }

    TagLib::ByteVector readBlock(size_t length) override
    {
        TagLib::ByteVector buffer(static_cast<unsigned int>(length));
        size_t done = 0;
        while (done < length) {
            ssize_t n = pread(fd_, buffer.data() + done, length - done, pos_ + static_cast<off_t>(done));
            if (n <= 0)
                break;
            done += static_cast<size_t>(n);
        }
        buffer.resize(static_cast<unsigned int>(done));
        pos_ += static_cast<TagLib::offset_t>(done);
        return buffer;
    }

    void writeBlock(const TagLib::ByteVector &data) override
    {
        size_t done = 0;
        while (done < data.size()) {
            ssize_t n = pwrite(fd_, data.data() + done, data.size() - done, pos_ + static_cast<off_t>(done));
            if (n <= 0) {
                failed_ = true;
                break;
            }
            done += static_cast<size_t>(n);
        }
        pos_ += static_cast<TagLib::offset_t>(done);
    }

    void insert(const TagLib::ByteVector &data, TagLib::offset_t start, size_t replace) override
    {
        if (data.size() == replace) {
            seek(start);
            writeBlock(data);
            return;
        }
        if (data.size() < replace) {
            seek(start);
            writeBlock(data);
            removeBlock(start + static_cast<TagLib::offset_t>(data.size()), replace - data.size());
            return;
        }
        /* as TagLib's FileStream: the file after [start] moves up by chunks longer than the growth */
        size_t bufferLength = kBuffer;
        while (data.size() - replace > bufferLength)
            bufferLength += kBuffer;
        TagLib::offset_t readPosition = start + static_cast<TagLib::offset_t>(replace);
        TagLib::offset_t writePosition = start;
        TagLib::ByteVector buffer = data;
        for (;;) {
            seek(readPosition);
            TagLib::ByteVector aboutToOverwrite = readBlock(bufferLength);
            const size_t bytesRead = aboutToOverwrite.size();
            readPosition += static_cast<TagLib::offset_t>(bufferLength);
            seek(writePosition);
            writeBlock(buffer);
            if (bytesRead == 0)
                break;
            writePosition += static_cast<TagLib::offset_t>(buffer.size());
            buffer = aboutToOverwrite;
        }
    }

    void removeBlock(TagLib::offset_t start, size_t length) override
    {
        TagLib::offset_t readPosition = start + static_cast<TagLib::offset_t>(length);
        TagLib::offset_t writePosition = start;
        for (;;) {
            seek(readPosition);
            TagLib::ByteVector buffer = readBlock(kBuffer);
            const size_t bytesRead = buffer.size();
            readPosition += static_cast<TagLib::offset_t>(bytesRead);
            if (bytesRead == 0)
                break;
            seek(writePosition);
            writeBlock(buffer);
            writePosition += static_cast<TagLib::offset_t>(bytesRead);
        }
        truncate(writePosition);
    }

    bool readOnly() const override { return false; }
    bool isOpen() const override { return fd_ >= 0; }

    void seek(TagLib::offset_t offset, Position p = Beginning) override
    {
        switch (p) {
        case Beginning: pos_ = offset; break;
        case Current: pos_ += offset; break;
        case End: pos_ = length() + offset; break;
        }
        if (pos_ < 0)
            pos_ = 0;
    }

    void clear() override {}
    TagLib::offset_t tell() const override { return pos_; }

    TagLib::offset_t length() override
    {
        off_t end = lseek(fd_, 0, SEEK_END);
        return end < 0 ? 0 : static_cast<TagLib::offset_t>(end);
    }

    void truncate(TagLib::offset_t length) override
    {
        if (ftruncate(fd_, static_cast<off_t>(length)) != 0)
            failed_ = true;
    }

    bool failed() const { return failed_; }

private:
    static constexpr size_t kBuffer = 1024 * 1024;
    int fd_;
    std::string name_;
    TagLib::offset_t pos_ = 0;
    bool failed_ = false;
};

TagLib::String decibels(double db)
{
    char text[32];
    snprintf(text, sizeof text, "%.2f dB", db);
    return TagLib::String(text);
}

TagLib::String peak(double value)
{
    char text[32];
    snprintf(text, sizeof text, "%.6f", value);
    return TagLib::String(text);
}

/* Opus: Q7.8 of the gain to -23 LUFS, clamped to the field */
TagLib::String r128(double lufs)
{
    double gain = -23.0 - lufs;
    long q = lround(gain * 256.0);
    if (q > 32767)
        q = 32767;
    if (q < -32768)
        q = -32768;
    char text[32];
    snprintf(text, sizeof text, "%ld", q);
    return TagLib::String(text);
}

/* JSON's string: quotes, backslashes and control characters escaped, UTF-8 as it is */
std::string quoted(const std::string &text)
{
    std::string out = "\"";
    for (unsigned char c : text) {
        switch (c) {
        case '"': out += "\\\""; break;
        case '\\': out += "\\\\"; break;
        case '\n': out += "\\n"; break;
        case '\r': out += "\\r"; break;
        case '\t': out += "\\t"; break;
        default:
            if (c < 0x20) {
                char escape[8];
                snprintf(escape, sizeof escape, "\\u%04x", c);
                out += escape;
            } else {
                out += static_cast<char>(c);
            }
        }
    }
    out += '"';
    return out;
}

/* the ID3v2 tag of the formats that keep one: MP3, AIFF, WAV */
TagLib::ID3v2::Tag *id3v2(TagLib::File *file)
{
    if (auto *mpeg = dynamic_cast<TagLib::MPEG::File *>(file))
        return mpeg->hasID3v2Tag() ? mpeg->ID3v2Tag() : nullptr;
    if (auto *aiff = dynamic_cast<TagLib::RIFF::AIFF::File *>(file))
        return aiff->hasID3v2Tag() ? aiff->tag() : nullptr;
    if (auto *wav = dynamic_cast<TagLib::RIFF::WAV::File *>(file))
        return wav->hasID3v2Tag() ? wav->ID3v2Tag() : nullptr;
    return nullptr;
}

/* a code point as UTF-8 */
void appendUtf8(std::string &out, unsigned long cp)
{
    if (cp < 0x80) {
        out += static_cast<char>(cp);
    } else if (cp < 0x800) {
        out += static_cast<char>(0xC0 | (cp >> 6));
        out += static_cast<char>(0x80 | (cp & 0x3F));
    } else if (cp < 0x10000) {
        out += static_cast<char>(0xE0 | (cp >> 12));
        out += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
        out += static_cast<char>(0x80 | (cp & 0x3F));
    } else {
        out += static_cast<char>(0xF0 | (cp >> 18));
        out += static_cast<char>(0x80 | ((cp >> 12) & 0x3F));
        out += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
        out += static_cast<char>(0x80 | (cp & 0x3F));
    }
}

/* four hex digits at [p], or -1 */
long hex4(const char *p)
{
    long v = 0;
    for (int i = 0; i < 4; i++) {
        char c = p[i];
        v <<= 4;
        if (c >= '0' && c <= '9') v |= c - '0';
        else if (c >= 'a' && c <= 'f') v |= c - 'a' + 10;
        else if (c >= 'A' && c <= 'F') v |= c - 'A' + 10;
        else return -1;
    }
    return v;
}

/* the JSON string starting after its opening quote at [*p]; false when it is malformed. *p ends after the closing quote. */
bool jsonString(const char *&p, std::string &out)
{
    out.clear();
    for (; *p; p++) {
        unsigned char c = static_cast<unsigned char>(*p);
        if (c == '"') {
            p++;
            return true;
        }
        if (c != '\\') {
            out += static_cast<char>(c);
            continue;
        }
        switch (*++p) {
        case '"': out += '"'; break;
        case '\\': out += '\\'; break;
        case '/': out += '/'; break;
        case 'b': out += '\b'; break;
        case 'f': out += '\f'; break;
        case 'n': out += '\n'; break;
        case 'r': out += '\r'; break;
        case 't': out += '\t'; break;
        case 'u': {
            long cp = hex4(p + 1);
            if (cp < 0)
                return false;
            p += 4;
            if (cp >= 0xD800 && cp < 0xDC00 && p[1] == '\\' && p[2] == 'u') {
                long low = hex4(p + 3);
                if (low >= 0xDC00 && low < 0xE000) {
                    cp = 0x10000 + ((cp - 0xD800) << 10) + (low - 0xDC00);
                    p += 6;
                }
            }
            appendUtf8(out, static_cast<unsigned long>(cp));
            break;
        }
        default: return false;
        }
    }
    return false;
}

void skipSpace(const char *&p)
{
    while (*p == ' ' || *p == '\n' || *p == '\r' || *p == '\t')
        p++;
}

/* the synced lyrics' JSON, [[ms, "line"], ...] as nag_tags_read_lyrics gives it: false when it is malformed */
bool parseSynced(const char *json, TagLib::ID3v2::SynchronizedLyricsFrame::SynchedTextList &lines)
{
    const char *p = json;
    skipSpace(p);
    if (*p++ != '[')
        return false;
    skipSpace(p);
    if (*p == ']')
        return true;
    for (;;) {
        skipSpace(p);
        if (*p++ != '[')
            return false;
        skipSpace(p);
        char *end = nullptr;
        long ms = strtol(p, &end, 10);
        if (end == p || ms < 0)
            return false;
        p = end;
        skipSpace(p);
        if (*p++ != ',')
            return false;
        skipSpace(p);
        if (*p++ != '"')
            return false;
        std::string text;
        if (!jsonString(p, text))
            return false;
        skipSpace(p);
        if (*p++ != ']')
            return false;
        lines.append(TagLib::ID3v2::SynchronizedLyricsFrame::SynchedText(static_cast<unsigned int>(ms), TagLib::String(text, TagLib::String::UTF8)));
        skipSpace(p);
        if (*p == ',') {
            p++;
            continue;
        }
        return *p == ']';
    }
}

} // namespace

/*
 * S-1003: the lyrics a file carries, as JSON: {"plain": "...", "synced": [[ms, "line"], ...]},
 * either missing when the file has none. Plain: ID3v2's USLT, the Vorbis comments' LYRICS (or
 * UNSYNCEDLYRICS), MP4's ©lyr, APE's Lyrics, all through TagLib's property map; their text may
 * itself be LRC, which the app reads. Synced: ID3v2's SYLT in milliseconds. Null when the file
 * cannot be read or has no lyrics; the caller frees the text.
 */
extern "C" char *nag_tags_read_lyrics(int fd, const char *name)
{
    if (fd < 0)
        return nullptr;
    FdStream stream(fd, name);
    TagLib::FileRef file(&stream, false);
    if (file.isNull())
        return nullptr;
    std::string plain;
    const TagLib::PropertyMap properties = file.properties();
    /* "USLT": a TXXX by that name, as ffmpeg writes "lyrics" to ID3v2 */
    for (const char *key : {"LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS", "USLT"}) {
        auto found = properties.find(key);
        if (found != properties.end() && !found->second.isEmpty()) {
            plain = found->second.front().to8Bit(true);
            break;
        }
    }
    if (plain.empty()) {
        /* a USLT with a description: "LYRICS:<description>" */
        for (const auto &entry : properties) {
            if (entry.first.startsWith("LYRICS") && !entry.second.isEmpty()) {
                plain = entry.second.front().to8Bit(true);
                break;
            }
        }
    }
    std::string synced;
    if (TagLib::ID3v2::Tag *tag = id3v2(file.file())) {
        for (TagLib::ID3v2::Frame *frame : tag->frameList("SYLT")) {
            auto *sylt = dynamic_cast<TagLib::ID3v2::SynchronizedLyricsFrame *>(frame);
            if (!sylt || sylt->timestampFormat() != TagLib::ID3v2::SynchronizedLyricsFrame::AbsoluteMilliseconds)
                continue;
            const auto lines = sylt->synchedText();
            if (lines.isEmpty())
                continue;
            for (const auto &line : lines) {
                if (!synced.empty())
                    synced += ',';
                synced += '[' + std::to_string(line.time) + ',' + quoted(line.text.to8Bit(true)) + ']';
            }
            break;
        }
    }
    if (plain.empty() && synced.empty())
        return nullptr;
    std::string out = "{";
    if (!plain.empty())
        out += "\"plain\":" + quoted(plain);
    if (!synced.empty()) {
        if (!plain.empty())
            out += ',';
        out += "\"synced\":[" + synced + ']';
    }
    out += '}';
    return strdup(out.c_str());
}

/*
 * S-1006: the front cover the file's tags carry (ID3v2's APIC, FLAC's and the Vorbis comments'
 * PICTURE, MP4's covr, APE's and ASF's pictures, through TagLib's PICTURE property): the front
 * cover's bytes, else the first picture's; NULL when there is none. *size is set; the caller
 * frees the bytes.
 */
extern "C" unsigned char *nag_tags_read_cover(int fd, const char *name, size_t *size)
{
    if (!size)
        return nullptr;
    *size = 0;
    if (fd < 0)
        return nullptr;
    FdStream stream(fd, name);
    TagLib::FileRef file(&stream, false);
    if (file.isNull())
        return nullptr;
    TagLib::ByteVector chosen;
    for (const auto &picture : file.complexProperties("PICTURE")) {
        const TagLib::ByteVector data = picture.value("data").value<TagLib::ByteVector>();
        if (data.isEmpty())
            continue;
        const bool front = picture.value("pictureType").value<TagLib::String>() == "Front Cover";
        if (chosen.isEmpty() || front)
            chosen = data;
        if (front)
            break;
    }
    if (chosen.isEmpty())
        return nullptr;
    auto *out = static_cast<unsigned char *>(malloc(chosen.size()));
    if (!out)
        return nullptr;
    memcpy(out, chosen.data(), chosen.size());
    *size = chosen.size();
    return out;
}

/*
 * S-1006: the first value of the file's tag [key], by TagLib's property name
 * ("MUSICBRAINZ_ALBUMID", "MUSICBRAINZ_RELEASEGROUPID"), UTF-8; NULL when it has none. The caller
 * frees the text.
 */
extern "C" char *nag_tags_read_property(int fd, const char *name, const char *key)
{
    if (fd < 0 || !key)
        return nullptr;
    FdStream stream(fd, name);
    TagLib::FileRef file(&stream, false);
    if (file.isNull())
        return nullptr;
    const TagLib::PropertyMap properties = file.properties();
    auto found = properties.find(TagLib::String(key, TagLib::String::UTF8).upper());
    if (found == properties.end() || found->second.isEmpty())
        return nullptr;
    const std::string value = found->second.front().to8Bit(true);
    return value.empty() ? nullptr : strdup(value.c_str());
}

extern "C" int nag_tags_write_replaygain(int fd, const char *name, const NagReplayGainTags *tags, char *error, size_t error_size)
{
    if (fd < 0 || !tags) {
        snprintf(error, error_size, "no file");
        return -1;
    }
    FdStream stream(fd, name);
    TagLib::FileRef file(&stream, false);
    if (file.isNull()) {
        snprintf(error, error_size, "the file's format is not one TagLib writes");
        return -1;
    }
    TagLib::PropertyMap properties = file.properties();
    const bool opus = name && strlen(name) > 5 && strcasecmp(name + strlen(name) - 5, ".opus") == 0;
    if (!std::isnan(tags->track_gain_db))
        properties.replace("REPLAYGAIN_TRACK_GAIN", TagLib::StringList(decibels(tags->track_gain_db)));
    if (!std::isnan(tags->track_peak))
        properties.replace("REPLAYGAIN_TRACK_PEAK", TagLib::StringList(peak(tags->track_peak)));
    if (!std::isnan(tags->album_gain_db))
        properties.replace("REPLAYGAIN_ALBUM_GAIN", TagLib::StringList(decibels(tags->album_gain_db)));
    if (!std::isnan(tags->album_peak))
        properties.replace("REPLAYGAIN_ALBUM_PEAK", TagLib::StringList(peak(tags->album_peak)));
    if (opus) {
        if (!std::isnan(tags->track_lufs))
            properties.replace("R128_TRACK_GAIN", TagLib::StringList(r128(tags->track_lufs)));
        if (!std::isnan(tags->album_lufs))
            properties.replace("R128_ALBUM_GAIN", TagLib::StringList(r128(tags->album_lufs)));
    }
    TagLib::PropertyMap rejected = file.setProperties(properties);
    if (!rejected.isEmpty()) {
        snprintf(error, error_size, "%s cannot hold %s", name ? name : "the file", rejected.begin()->first.toCString(true));
        return -1;
    }
    if (!file.save() || stream.failed()) {
        snprintf(error, error_size, "writing the tags failed");
        return -1;
    }
    return 0;
}

namespace {

/* "-6.54 dB", "0.988" → the number; NAN for none */
double tag_number(const TagLib::PropertyMap &properties, const char *key)
{
    auto found = properties.find(key);
    if (found == properties.end() || found->second.isEmpty())
        return NAN;
    const std::string text = found->second.front().to8Bit(true);
    char *end = nullptr;
    double value = strtod(text.c_str(), &end);
    return end == text.c_str() ? NAN : value;
}

/* what says how the source was made, or what only the source's audio was */
bool left_behind(const TagLib::String &key)
{
    static const char *const keys[] = {
        "ENCODER", "ENCODING", "ENCODERSETTINGS", "ENCODER_OPTIONS", "CUESHEET", "LENGTH",
        "ITUNSMPB", "ITUNNORM", "ITUNPGAP", "WAVEFORMATEXTENSIBLE_CHANNEL_MASK",
    };
    for (const char *k : keys)
        if (key == k)
            return true;
    return key.startsWith("REPLAYGAIN_") || key.startsWith("R128_");
}

bool ends_with(const char *name, const char *suffix)
{
    if (!name)
        return false;
    size_t n = strlen(name), m = strlen(suffix);
    return n >= m && strcasecmp(name + n - m, suffix) == 0;
}

} // namespace

/*
 * S-1010: the ReplayGain the file carries: REPLAYGAIN_* (ID3v2's TXXX, Vorbis comments, MP4's
 * freeform atoms, APE), else Opus' R128_* (Q7.8 to -23 LUFS) as ReplayGain's dB (to -18 LUFS).
 */
extern "C" int nag_tags_read_replaygain(int fd, const char *name, NagReplayGainTags *gain)
{
    if (!gain)
        return 0;
    gain->track_gain_db = gain->track_peak = gain->album_gain_db = gain->album_peak = NAN;
    gain->track_lufs = gain->album_lufs = NAN;
    if (fd < 0)
        return 0;
    FdStream stream(fd, name);
    TagLib::FileRef file(&stream, false);
    if (file.isNull())
        return 0;
    const TagLib::PropertyMap properties = file.properties();
    gain->track_gain_db = tag_number(properties, "REPLAYGAIN_TRACK_GAIN");
    gain->track_peak = tag_number(properties, "REPLAYGAIN_TRACK_PEAK");
    gain->album_gain_db = tag_number(properties, "REPLAYGAIN_ALBUM_GAIN");
    gain->album_peak = tag_number(properties, "REPLAYGAIN_ALBUM_PEAK");
    if (std::isnan(gain->track_gain_db)) {
        double q = tag_number(properties, "R128_TRACK_GAIN");
        if (!std::isnan(q))
            gain->track_gain_db = q / 256.0 + 5.0;
    }
    if (std::isnan(gain->album_gain_db)) {
        double q = tag_number(properties, "R128_ALBUM_GAIN");
        if (!std::isnan(q))
            gain->album_gain_db = q / 256.0 + 5.0;
    }
    if (!std::isnan(gain->track_gain_db))
        gain->track_lufs = -18.0 - gain->track_gain_db;
    if (!std::isnan(gain->album_gain_db))
        gain->album_lufs = -18.0 - gain->album_gain_db;
    int count = 0;
    for (double v : {gain->track_gain_db, gain->track_peak, gain->album_gain_db, gain->album_peak})
        count += std::isnan(v) ? 0 : 1;
    return count;
}

/*
 * S-1010: the source's tags through TagLib's property map onto the converted file, which puts
 * them where its format keeps them (TagLib 2 writes ID3v2.4 by default, its text UTF-8; a key
 * MP4 has no atom for becomes a freeform iTunes atom), and the front cover (else the first
 * picture) through the PICTURE property: APIC, METADATA_BLOCK_PICTURE, covr. The lyrics go
 * along as the LYRICS property: USLT, LYRICS, ©lyr.
 */
extern "C" int nag_tags_copy(int src_fd, const char *src_name, int dst_fd, const char *dst_name, const char *overrides,
                             const NagReplayGainTags *gain, int *written, int *cover, char *error, size_t error_size)
{
    if (written)
        *written = 0;
    if (cover)
        *cover = 0;
    TagLib::PropertyMap properties;
    TagLib::List<TagLib::VariantMap> pictures;
    if (src_fd >= 0) {
        FdStream source(src_fd, src_name);
        TagLib::FileRef file(&source, false);
        if (!file.isNull()) {
            properties = file.properties();
            for (const auto &picture : file.complexProperties("PICTURE")) {
                if (picture.value("data").value<TagLib::ByteVector>().isEmpty())
                    continue;
                const bool front = picture.value("pictureType").value<TagLib::String>() == "Front Cover";
                if (pictures.isEmpty() || front) {
                    pictures.clear();
                    pictures.append(picture);
                }
                if (front)
                    break;
            }
        }
    }
    TagLib::PropertyMap kept;
    for (const auto &entry : properties)
        if (!left_behind(entry.first) && !entry.second.isEmpty())
            kept.insert(entry.first, entry.second);
    /* the caller's, over the source's: "KEY=value" per line */
    if (overrides) {
        const char *line = overrides;
        while (*line) {
            const char *end = strchr(line, '\n');
            const size_t length = end ? static_cast<size_t>(end - line) : strlen(line);
            const std::string text(line, length);
            const size_t eq = text.find('=');
            if (eq != std::string::npos && eq > 0) {
                const TagLib::String key = TagLib::String(text.substr(0, eq), TagLib::String::UTF8).upper();
                const TagLib::String value(text.substr(eq + 1), TagLib::String::UTF8);
                if (value.isEmpty())
                    kept.erase(key);
                else
                    kept.replace(key, TagLib::StringList(value));
            }
            line += length + (end ? 1 : 0);
        }
    }
    const bool opus = ends_with(dst_name, ".opus") || ends_with(dst_name, ".ogg");
    if (gain) {
        if (opus) {
            /* RFC 7845: the gains to -23 LUFS in Q7.8, never REPLAYGAIN_* */
            if (!std::isnan(gain->track_lufs))
                kept.replace("R128_TRACK_GAIN", TagLib::StringList(r128(gain->track_lufs)));
            if (!std::isnan(gain->album_lufs))
                kept.replace("R128_ALBUM_GAIN", TagLib::StringList(r128(gain->album_lufs)));
        } else {
            if (!std::isnan(gain->track_gain_db))
                kept.replace("REPLAYGAIN_TRACK_GAIN", TagLib::StringList(decibels(gain->track_gain_db)));
            if (!std::isnan(gain->track_peak))
                kept.replace("REPLAYGAIN_TRACK_PEAK", TagLib::StringList(peak(gain->track_peak)));
            if (!std::isnan(gain->album_gain_db))
                kept.replace("REPLAYGAIN_ALBUM_GAIN", TagLib::StringList(decibels(gain->album_gain_db)));
            if (!std::isnan(gain->album_peak))
                kept.replace("REPLAYGAIN_ALBUM_PEAK", TagLib::StringList(peak(gain->album_peak)));
        }
    }
    FdStream stream(dst_fd, dst_name);
    TagLib::FileRef file(&stream, false);
    if (file.isNull()) {
        snprintf(error, error_size, "the converted file is not one TagLib reads");
        return -1;
    }
    const TagLib::PropertyMap rejected = file.setProperties(kept);
    bool with_cover = false;
    if (!pictures.isEmpty())
        with_cover = file.setComplexProperties("PICTURE", pictures);
    if (!file.save() || stream.failed()) {
        snprintf(error, error_size, "writing the tags failed");
        return -1;
    }
    if (written)
        *written = static_cast<int>(kept.size() - rejected.size());
    if (cover)
        *cover = with_cover ? 1 : 0;
    return 0;
}

/*
 * S-1005: the lyrics written into the file's tags, the way nag_tags_read_lyrics reads them: [text]
 * as the plain lyrics (ID3v2's USLT, the Vorbis comments' LYRICS, MP4's ©lyr, APE's Lyrics), its
 * text LRC when the lines are timed; [synced] as ID3v2's SYLT in milliseconds ([[ms, "line"], ...])
 * for the formats that keep one. NULL or empty removes what the file had of the kind. Nothing else
 * in the tags is touched.
 */
extern "C" int nag_tags_write_lyrics(int fd, const char *name, const char *text, const char *synced, char *error, size_t error_size)
{
    if (fd < 0) {
        snprintf(error, error_size, "no file");
        return -1;
    }
    TagLib::ID3v2::SynchronizedLyricsFrame::SynchedTextList lines;
    if (synced && *synced && !parseSynced(synced, lines)) {
        snprintf(error, error_size, "the synced lyrics are not [[ms, \"line\"], ...]");
        return -1;
    }
    FdStream stream(fd, name);
    TagLib::FileRef file(&stream, false);
    if (file.isNull()) {
        snprintf(error, error_size, "the file's format is not one TagLib writes");
        return -1;
    }
    TagLib::PropertyMap properties = file.properties();
    /* the names nag_tags_read_lyrics looks for, so that no old text outranks the new one */
    for (const char *key : {"LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS", "USLT"})
        properties.erase(key);
    /* and a USLT with a description, which comes as "LYRICS:<description>" */
    TagLib::StringList described;
    for (const auto &entry : properties) {
        if (entry.first.startsWith("LYRICS"))
            described.append(entry.first);
    }
    for (const auto &key : described)
        properties.erase(key);
    if (text && *text)
        properties.replace("LYRICS", TagLib::StringList(TagLib::String(text, TagLib::String::UTF8)));
    TagLib::PropertyMap rejected = file.setProperties(properties);
    if (!rejected.isEmpty()) {
        snprintf(error, error_size, "%s cannot hold %s", name ? name : "the file", rejected.begin()->first.toCString(true));
        return -1;
    }
    if (TagLib::ID3v2::Tag *tag = id3v2(file.file())) {
        tag->removeFrames("SYLT");
        if (!lines.isEmpty()) {
            auto *frame = new TagLib::ID3v2::SynchronizedLyricsFrame(TagLib::String::UTF8);
            frame->setTimestampFormat(TagLib::ID3v2::SynchronizedLyricsFrame::AbsoluteMilliseconds);
            frame->setType(TagLib::ID3v2::SynchronizedLyricsFrame::Lyrics);
            frame->setLanguage("XXX");
            frame->setSynchedText(lines);
            tag->addFrame(frame);
        }
    }
    if (!file.save() || stream.failed()) {
        snprintf(error, error_size, "writing the tags failed");
        return -1;
    }
    return 0;
}
