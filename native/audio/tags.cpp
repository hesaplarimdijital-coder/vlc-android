/*
 * Nağmedar Ses Motoru: ReplayGain tags written in place with TagLib (S-907).
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
#include <cstring>
#include <string>
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
