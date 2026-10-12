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

#include <taglib/fileref.h>
#include <taglib/tiostream.h>
#include <taglib/tpropertymap.h>
#include <taglib/tstring.h>

#include <cmath>
#include <cstdio>
#include <cstring>
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

} // namespace

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
