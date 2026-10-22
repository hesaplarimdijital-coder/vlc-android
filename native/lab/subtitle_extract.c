// A film's embedded text subtitles written out as a file of their own (SRT, or ASS/SSA kept as
// they are), for the subtitle tools and the offline translation, which work on files.
//
// libavformat reads the container once, from the start to the end, with every other stream
// discarded (an MP4's demuxer then reads only the subtitle samples; a Matroska or MPEG-TS file is
// read through, in the demuxer's buffer). The packets are turned into lines without a decoder:
// SubRip and plain text are already text, WebVTT keeps its italics, bold and underline and drops
// its other tags, MP4 timed text (tx3g) is its UTF-8 or UTF-16 text with its style records, and
// ASS/SSA packets are Matroska's "ReadOrder,Layer,Style,…,Text", written back as Dialogue lines
// under the track's own header. Only the lines are kept in memory (a film's are well under a
// megabyte; [MAX_TEXT_BYTES] at most): a packet larger than what is left of it is refused before
// its text is made, and no text being made grows past it.
//
// The times are the packets' own: the presentation time and the duration, in milliseconds of the
// film (MPEG-TS and programme streams count from the file's start). A line without a duration
// lasts until the next one starts, [OPEN_END_MS] at most. Lines that overlap stay as they are
// (SRT players show both); the very same line twice (some muxers repeat them) is written once.
// Picture subtitles (PGS, VobSub, DVB) are refused: they would need OCR.
#include "subtitle_extract.h"

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/avstring.h>
#include <libavutil/bprint.h>
#include <libavutil/intreadwrite.h>
#include <libavutil/mathematics.h>

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

/** The lines' text kept at most, and their number. */
#define MAX_TEXT_BYTES (64 * 1024 * 1024)
#define MAX_EVENTS 2000000
/** How long a line without a duration lasts when nothing follows it soon. */
#define OPEN_END_MS 5000
/** A long pass over the lines looks at the cancel flag once in this many lines. */
#define CANCEL_EVERY 4096

#define EOL "\r\n"

typedef enum Source { SRC_SUBRIP, SRC_TEXT, SRC_WEBVTT, SRC_MOVTEXT, SRC_ASS, SRC_SSA } Source;

typedef struct Event {
    int64_t start, end; /* milliseconds; end < 0: the packet gave no duration */
    int64_t order;      /* ASS/SSA: the line's place in the original file (ReadOrder) */
    int64_t seq;        /* the order the packets came in */
    char *text;         /* SRT: the cue's text; ASS/SSA: "Layer,Style,Name,MarginL,MarginR,MarginV,Effect,Text" */
} Event;

typedef struct Events {
    Event *items;
    size_t count, cap;
    size_t bytes;
    int utf8; /* every line is valid UTF-8: the file gets a byte order mark */
} Events;

static int interrupted(void *opaque)
{
    const volatile int *cancel = opaque;
    return cancel && *cancel;
}

static const char *error_text(int err, char *buf, size_t size)
{
    av_strerror(err, buf, size);
    return buf;
}

/*
 * Why [bp] is not complete: it reached its size_max (the text left room for: too many lines'
 * text, AVERROR(E2BIG)) or memory ran out (AVERROR(ENOMEM)). Its len and str are not to be used.
 */
static int incomplete(const AVBPrint *bp)
{
    return bp->size >= bp->size_max ? AVERROR(E2BIG) : AVERROR(ENOMEM);
}

static int stopped(const volatile int *cancel)
{
    return cancel && *cancel;
}

static void json_text(AVBPrint *bp, const char *s)
{
    av_bprint_chars(bp, '"', 1);
    for (const unsigned char *p = (const unsigned char *)(s ? s : ""); *p; p++) {
        if (*p == '"' || *p == '\\')
            av_bprintf(bp, "\\%c", *p);
        else if (*p < 0x20 || *p >= 0x80) /* ASCII only: the JNI side hands it over as is */
            av_bprintf(bp, "\\u%04x", *p);
        else
            av_bprint_chars(bp, (char)*p, 1);
    }
    av_bprint_chars(bp, '"', 1);
}

static char *result(const char *error, const char *detail, const char *codec, const char *format, size_t events, int partial)
{
    AVBPrint bp;
    av_bprint_init(&bp, 0, AV_BPRINT_SIZE_UNLIMITED);
    av_bprintf(&bp, "{\"error\":");
    json_text(&bp, error);
    av_bprintf(&bp, ",\"detail\":");
    json_text(&bp, detail);
    av_bprintf(&bp, ",\"codec\":");
    json_text(&bp, codec);
    av_bprintf(&bp, ",\"format\":");
    json_text(&bp, format);
    av_bprintf(&bp, ",\"events\":%zu,\"partial\":%s}", events, partial ? "true" : "false");
    char *json = av_bprint_is_complete(&bp) ? strdup(bp.str) : NULL;
    av_bprint_finalize(&bp, NULL);
    return json;
}

/* ---- text ----------------------------------------------------------------------------------- */

static int valid_utf8(const unsigned char *s)
{
    while (*s) {
        unsigned c = *s, n;
        if (c < 0x80) {
            s++;
            continue;
        }
        if (c >= 0xC2 && c <= 0xDF)
            n = 1;
        else if (c >= 0xE0 && c <= 0xEF)
            n = 2;
        else if (c >= 0xF0 && c <= 0xF4)
            n = 3;
        else
            return 0;
        for (unsigned i = 1; i <= n; i++)
            if ((s[i] & 0xC0) != 0x80)
                return 0;
        s += n + 1;
    }
    return 1;
}

/*
 * A cue's text as SRT wants it: no carriage returns or NUL bytes, no empty or blank lines (one
 * would end the cue), nothing blank at the start or the end; [limit] bytes at most (with its NUL).
 * NULL when nothing is left (*err 0), when it would be longer (AVERROR(E2BIG)) or when memory ran
 * out (AVERROR(ENOMEM)).
 */
static char *cue_text(const char *s, size_t n, unsigned limit, int *err)
{
    AVBPrint out, line;
    av_bprint_init(&out, 0, limit);
    av_bprint_init(&line, 0, limit);
    *err = 0;
    for (size_t i = 0; i <= n && !*err; i++) {
        char c = i < n ? s[i] : '\n';
        if (c == '\r' || c == 0)
            continue;
        if (c != '\n') {
            av_bprint_chars(&line, c, 1);
            continue;
        }
        /* a line that did not fit: its len and str are not the line's */
        if (!av_bprint_is_complete(&line)) {
            *err = incomplete(&line);
            break;
        }
        /* the line without its trailing blanks; a blank one is left out */
        size_t len = line.len;
        while (len > 0 && (line.str[len - 1] == ' ' || line.str[len - 1] == '\t'))
            len--;
        size_t lead = 0;
        while (lead < len && (line.str[lead] == ' ' || line.str[lead] == '\t'))
            lead++;
        if (lead < len) {
            if (out.len)
                av_bprint_chars(&out, '\n', 1);
            av_bprint_append_data(&out, line.str, (unsigned)len);
            if (!av_bprint_is_complete(&out))
                *err = incomplete(&out);
        }
        av_bprint_clear(&line);
    }
    av_bprint_finalize(&line, NULL);
    char *text = NULL;
    if (!*err && out.len && !(text = strdup(out.str)))
        *err = AVERROR(ENOMEM);
    av_bprint_finalize(&out, NULL);
    return text;
}

/* WebVTT's tags as SRT's: italics, bold and underline stay, classes, voices, languages, ruby and timestamps go. */
static void webvtt_to_srt(const char *s, size_t n, AVBPrint *out)
{
    static const struct { const char *name, *text; } entities[] = {
        {"amp", "&"}, {"lt", "<"}, {"gt", ">"}, {"quot", "\""}, {"apos", "'"},
        {"nbsp", "\xC2\xA0"}, {"lrm", "\xE2\x80\x8E"}, {"rlm", "\xE2\x80\x8F"},
    };
    for (size_t i = 0; i < n; i++) {
        char c = s[i];
        if (c == '<') {
            const char *close = memchr(s + i, '>', n - i);
            if (!close) {
                av_bprint_chars(out, c, 1);
                continue;
            }
            size_t len = (size_t)(close - (s + i)) - 1;
            const char *tag = s + i + 1;
            int end = len > 0 && tag[0] == '/';
            const char *name = tag + end;
            size_t name_len = 0;
            while (name_len < len - end && name[name_len] != '.' && name[name_len] != ' ' && name[name_len] != '\t')
                name_len++;
            if (name_len == 1 && (name[0] == 'i' || name[0] == 'b' || name[0] == 'u'))
                av_bprintf(out, "<%s%c>", end ? "/" : "", name[0]);
            i += len + 1;
            continue;
        }
        if (c == '&') {
            int done = 0;
            for (size_t k = 0; k < sizeof(entities) / sizeof(entities[0]) && !done; k++) {
                size_t len = strlen(entities[k].name);
                if (i + 1 + len < n && !memcmp(s + i + 1, entities[k].name, len) && s[i + 1 + len] == ';') {
                    av_bprintf(out, "%s", entities[k].text);
                    i += len + 1;
                    done = 1;
                }
            }
            if (!done)
                av_bprint_chars(out, c, 1);
            continue;
        }
        av_bprint_chars(out, c, 1);
    }
}

/* One UTF-16 big-endian text as UTF-8 (a lone surrogate becomes U+FFFD). */
static void utf16be_to_utf8(const uint8_t *p, size_t n, AVBPrint *out)
{
    for (size_t i = 0; i + 1 < n; i += 2) {
        uint32_t c = AV_RB16(p + i);
        if (c >= 0xD800 && c <= 0xDBFF && i + 3 < n) {
            uint32_t low = AV_RB16(p + i + 2);
            if (low >= 0xDC00 && low <= 0xDFFF) {
                c = 0x10000 + ((c - 0xD800) << 10) + (low - 0xDC00);
                i += 2;
            } else {
                c = 0xFFFD;
            }
        } else if (c >= 0xD800 && c <= 0xDFFF) {
            c = 0xFFFD;
        }
        uint8_t buf[4];
        int len;
        if (c < 0x80) {
            buf[0] = (uint8_t)c;
            len = 1;
        } else if (c < 0x800) {
            buf[0] = (uint8_t)(0xC0 | (c >> 6));
            buf[1] = (uint8_t)(0x80 | (c & 0x3F));
            len = 2;
        } else if (c < 0x10000) {
            buf[0] = (uint8_t)(0xE0 | (c >> 12));
            buf[1] = (uint8_t)(0x80 | ((c >> 6) & 0x3F));
            buf[2] = (uint8_t)(0x80 | (c & 0x3F));
            len = 3;
        } else {
            buf[0] = (uint8_t)(0xF0 | (c >> 18));
            buf[1] = (uint8_t)(0x80 | ((c >> 12) & 0x3F));
            buf[2] = (uint8_t)(0x80 | ((c >> 6) & 0x3F));
            buf[3] = (uint8_t)(0x80 | (c & 0x3F));
            len = 4;
        }
        av_bprint_append_data(out, (const char *)buf, len);
    }
}

#define FACE_BOLD 1
#define FACE_ITALIC 2
#define FACE_UNDERLINE 4

static void close_faces(AVBPrint *out, int faces)
{
    if (faces & FACE_UNDERLINE)
        av_bprintf(out, "</u>");
    if (faces & FACE_ITALIC)
        av_bprintf(out, "</i>");
    if (faces & FACE_BOLD)
        av_bprintf(out, "</b>");
}

static void open_faces(AVBPrint *out, int faces)
{
    if (faces & FACE_BOLD)
        av_bprintf(out, "<b>");
    if (faces & FACE_ITALIC)
        av_bprintf(out, "<i>");
    if (faces & FACE_UNDERLINE)
        av_bprintf(out, "<u>");
}

/*
 * An MP4 timed text sample (3GPP TS 26.245): a 16-bit length, the text (UTF-8, or UTF-16 after
 * its byte order mark), then boxes; a 'styl' box's records make bold, italic and underlined
 * stretches of characters. 0, or why the text could not be made ([incomplete]): its own text
 * takes [limit] bytes at most, as [out] does.
 */
static int movtext_to_srt(const uint8_t *p, int size, AVBPrint *out, unsigned limit)
{
    if (size < 2)
        return 0;
    int len = AV_RB16(p);
    if (len > size - 2)
        len = size - 2;
    AVBPrint text;
    av_bprint_init(&text, 0, limit);
    if (len >= 2 && p[2] == 0xFE && p[3] == 0xFF)
        utf16be_to_utf8(p + 4, (size_t)len - 2, &text);
    else
        av_bprint_append_data(&text, (const char *)p + 2, len);
    if (!av_bprint_is_complete(&text)) {
        int err = incomplete(&text);
        av_bprint_finalize(&text, NULL);
        return err;
    }

    /* the characters (UTF-8 sequences) of the text and the faces each one has */
    size_t chars = 0;
    for (size_t i = 0; i < text.len; i++)
        if (((unsigned char)text.str[i] & 0xC0) != 0x80)
            chars++;
    unsigned char *faces = chars ? calloc(chars, 1) : NULL;
    int styled = 0;
    const uint8_t *box = p + 2 + len, *end = p + size;
    while (faces && end - box >= 8) {
        uint32_t box_size = AV_RB32(box);
        if (box_size < 8 || box_size > (uint32_t)(end - box))
            break;
        if (!memcmp(box + 4, "styl", 4) && box_size >= 10) {
            unsigned count = AV_RB16(box + 8);
            const uint8_t *entry = box + 10;
            for (unsigned k = 0; k < count && entry + 12 <= box + box_size; k++, entry += 12) {
                unsigned from = AV_RB16(entry), to = AV_RB16(entry + 2);
                int face = entry[6] & (FACE_BOLD | FACE_ITALIC | FACE_UNDERLINE);
                for (unsigned c = from; c < to && c < chars; c++) {
                    faces[c] |= (unsigned char)face;
                    styled |= face;
                }
            }
        }
        box += box_size;
    }

    if (!styled) {
        av_bprint_append_data(out, text.str, text.len);
    } else {
        int current = 0;
        size_t c = 0;
        for (size_t i = 0; i < text.len; i++) {
            if (((unsigned char)text.str[i] & 0xC0) != 0x80) {
                int want = c < chars ? faces[c] : 0;
                if (want != current) {
                    close_faces(out, current);
                    open_faces(out, want);
                    current = want;
                }
                c++;
            }
            av_bprint_chars(out, text.str[i], 1);
        }
        close_faces(out, current);
    }
    free(faces);
    av_bprint_finalize(&text, NULL);
    return 0;
}

/* ---- events --------------------------------------------------------------------------------- */

/* Takes [text] (malloc'd); 0, or a negative AVERROR when there are too many lines. */
static int add_event(Events *e, int64_t start, int64_t end, int64_t order, char *text)
{
    size_t len = strlen(text);
    if (e->count >= MAX_EVENTS || e->bytes + len > MAX_TEXT_BYTES) {
        free(text);
        return AVERROR(E2BIG);
    }
    if (e->count == e->cap) {
        size_t cap = e->cap ? e->cap * 2 : 1024;
        Event *items = realloc(e->items, cap * sizeof(Event));
        if (!items) {
            free(text);
            return AVERROR(ENOMEM);
        }
        e->items = items;
        e->cap = cap;
    }
    if (e->utf8 && !valid_utf8((const unsigned char *)text))
        e->utf8 = 0;
    e->items[e->count] = (Event){start, end, order, (int64_t)e->count, text};
    e->count++;
    e->bytes += len;
    return 0;
}

static void free_events(Events *e)
{
    for (size_t i = 0; i < e->count; i++)
        free(e->items[i].text);
    free(e->items);
    e->items = NULL;
    e->count = e->cap = 0;
}

static int by_start(const void *a, const void *b)
{
    const Event *x = a, *y = b;
    if (x->start != y->start)
        return x->start < y->start ? -1 : 1;
    return x->seq < y->seq ? -1 : x->seq > y->seq;
}

static int by_order(const void *a, const void *b)
{
    const Event *x = a, *y = b;
    if (x->order != y->order)
        return x->order < y->order ? -1 : 1;
    return by_start(a, b);
}

/* The same line (start, end and text) side by side, the one that came first first. */
static int by_line(const void *a, const void *b)
{
    const Event *x = a, *y = b;
    if (x->start != y->start)
        return x->start < y->start ? -1 : 1;
    if (x->end != y->end)
        return x->end < y->end ? -1 : 1;
    int text = strcmp(x->text, y->text);
    if (text)
        return text;
    return x->seq < y->seq ? -1 : x->seq > y->seq;
}

/*
 * The lines in the order of their start; a line without a duration ends where the next one
 * starts (OPEN_END_MS at most); one that would end before it starts lasts a millisecond. For SRT
 * the very same line twice (the same start, end and text) is kept once. Sorted, then one pass
 * each, so many lines at the same time take no longer than as many others. 0, or AVERROR_EXIT
 * when [cancel] was set meanwhile.
 */
static int settle(Events *e, int dedupe, const volatile int *cancel)
{
    if (!e->count)
        return 0;
    qsort(e->items, e->count, sizeof(Event), by_start);
    /* from the last line back, the start of the first line that starts later */
    int has_next = 0;
    int64_t next = 0;
    for (size_t i = e->count; i-- > 0;) {
        if (i % CANCEL_EVERY == 0 && stopped(cancel))
            return AVERROR_EXIT;
        Event *ev = &e->items[i];
        if (i + 1 < e->count && e->items[i + 1].start > ev->start) {
            next = e->items[i + 1].start;
            has_next = 1;
        }
        if (ev->end < 0)
            ev->end = has_next && next < ev->start + OPEN_END_MS ? next : ev->start + OPEN_END_MS;
        if (ev->end <= ev->start)
            ev->end = ev->start + 1;
    }
    if (!dedupe)
        return 0;
    qsort(e->items, e->count, sizeof(Event), by_line);
    if (stopped(cancel))
        return AVERROR_EXIT;
    /* the lines are owned once: a line twice gives its text back, the others move down */
    size_t kept = 0;
    for (size_t i = 0; i < e->count; i++) {
        Event *ev = &e->items[i];
        const Event *before = kept ? &e->items[kept - 1] : NULL;
        if (before && before->start == ev->start && before->end == ev->end && !strcmp(before->text, ev->text)) {
            free(ev->text);
            continue;
        }
        e->items[kept++] = *ev;
    }
    e->count = kept;
    qsort(e->items, e->count, sizeof(Event), by_start);
    return stopped(cancel) ? AVERROR_EXIT : 0;
}

/* ---- output --------------------------------------------------------------------------------- */

/* [s] with its line breaks as EOL. */
static int put_lines(FILE *f, const char *s, size_t n)
{
    size_t from = 0;
    for (size_t i = 0; i < n; i++) {
        if (s[i] == '\r') {
            if (i > from && fwrite(s + from, 1, i - from, f) != i - from)
                return -1;
            from = i + 1;
        } else if (s[i] == '\n') {
            if (i > from && fwrite(s + from, 1, i - from, f) != i - from)
                return -1;
            if (fputs(EOL, f) < 0)
                return -1;
            from = i + 1;
        }
    }
    if (n > from && fwrite(s + from, 1, n - from, f) != n - from)
        return -1;
    return 0;
}

static void srt_time(char *buf, size_t size, int64_t ms)
{
    snprintf(buf, size, "%02lld:%02lld:%02lld,%03lld", (long long)(ms / 3600000), (long long)(ms / 60000 % 60),
             (long long)(ms / 1000 % 60), (long long)(ms % 1000));
}

/* ASS counts hundredths of a second: H:MM:SS.cc */
static void ass_time(char *buf, size_t size, int64_t cs)
{
    snprintf(buf, size, "%lld:%02lld:%02lld.%02lld", (long long)(cs / 360000), (long long)(cs / 6000 % 60),
             (long long)(cs / 100 % 60), (long long)(cs % 100));
}

static int write_srt(FILE *f, const Events *e, const volatile int *cancel)
{
    char a[32], b[32];
    for (size_t i = 0; i < e->count; i++) {
        if (i % CANCEL_EVERY == 0 && stopped(cancel))
            return -1;
        const Event *ev = &e->items[i];
        srt_time(a, sizeof(a), ev->start);
        srt_time(b, sizeof(b), ev->end);
        if (fprintf(f, "%zu" EOL "%s --> %s" EOL, i + 1, a, b) < 0)
            return -1;
        if (put_lines(f, ev->text, strlen(ev->text)) < 0 || fputs(EOL EOL, f) < 0)
            return -1;
    }
    return 0;
}

static const char DEFAULT_ASS_HEADER[] =
    "[Script Info]\n"
    "ScriptType: v4.00+\n"
    "PlayResX: 384\n"
    "PlayResY: 288\n"
    "ScaledBorderAndShadow: yes\n"
    "\n"
    "[V4+ Styles]\n"
    "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, "
    "Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, "
    "MarginR, MarginV, Encoding\n"
    "Style: Default,Arial,16,&Hffffff,&Hffffff,&H0,&H0,0,0,0,0,100,100,0,0,1,1,0,2,10,10,10,0\n"
    "\n"
    "[Events]\n"
    "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n";

/*
 * The track's header (its extradata: script info and styles, up to the events' Format line) and
 * every line as a Dialogue line, in the original file's order.
 */
static int write_ass(FILE *f, const Events *e, const AVCodecParameters *par, int ssa, const volatile int *cancel)
{
    const char *header = DEFAULT_ASS_HEADER;
    size_t header_len = sizeof(DEFAULT_ASS_HEADER) - 1;
    if (par->extradata && par->extradata_size > 0) {
        header = (const char *)par->extradata;
        header_len = strnlen(header, (size_t)par->extradata_size);
    }
    while (header_len > 0 && (header[header_len - 1] == '\n' || header[header_len - 1] == '\r' || header[header_len - 1] == ' '))
        header_len--;
    if (put_lines(f, header, header_len) < 0 || fputs(EOL, f) < 0)
        return -1;
    /* a header without the events' section: the one ASS (or SSA) defines */
    char *events = av_strnstr(header, "[Events]", header_len);
    if (!events) {
        const char *format = ssa ? "Marked, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"
                                 : "Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text";
        if (fprintf(f, EOL "[Events]" EOL "Format: %s" EOL, format) < 0)
            return -1;
    }
    char a[32], b[32];
    for (size_t i = 0; i < e->count; i++) {
        if (i % CANCEL_EVERY == 0 && stopped(cancel))
            return -1;
        const Event *ev = &e->items[i];
        int64_t start = (ev->start + 5) / 10, end = (ev->end + 5) / 10;
        if (end <= start)
            end = start + 1;
        ass_time(a, sizeof(a), start);
        ass_time(b, sizeof(b), end);
        /* "Layer,Style,…": the times go after the layer */
        const char *comma = strchr(ev->text, ',');
        size_t layer = comma ? (size_t)(comma - ev->text) : strlen(ev->text);
        if (fprintf(f, "Dialogue: %.*s,%s,%s,%s" EOL, (int)layer, ev->text, a, b, comma ? comma + 1 : ",,0,0,0,,") < 0)
            return -1;
    }
    return 0;
}

/* ---- the track ------------------------------------------------------------------------------ */

static int source_of(enum AVCodecID id, Source *source)
{
    switch (id) {
    case AV_CODEC_ID_SUBRIP: *source = SRC_SUBRIP; return 1;
    case AV_CODEC_ID_TEXT: *source = SRC_TEXT; return 1;
    case AV_CODEC_ID_WEBVTT: *source = SRC_WEBVTT; return 1;
    case AV_CODEC_ID_MOV_TEXT: *source = SRC_MOVTEXT; return 1;
    case AV_CODEC_ID_ASS: *source = SRC_ASS; return 1;
    case AV_CODEC_ID_SSA: *source = SRC_SSA; return 1;
    default: return 0;
    }
}

static int is_picture(enum AVCodecID id)
{
    const AVCodecDescriptor *d = avcodec_descriptor_get(id);
    if (d && (d->props & AV_CODEC_PROP_BITMAP_SUB) && !(d->props & AV_CODEC_PROP_TEXT_SUB))
        return 1;
    return id == AV_CODEC_ID_HDMV_PGS_SUBTITLE || id == AV_CODEC_ID_DVD_SUBTITLE || id == AV_CODEC_ID_DVB_SUBTITLE || id == AV_CODEC_ID_XSUB;
}

/* The [track]-th subtitle stream (0 first), as the media probe counts them; -1 when there is none. */
static int pick_stream(const AVFormatContext *ic, int track)
{
    int n = 0;
    for (unsigned i = 0; i < ic->nb_streams; i++) {
        if (ic->streams[i]->codecpar->codec_type != AVMEDIA_TYPE_SUBTITLE)
            continue;
        if (n++ == track)
            return (int)i;
    }
    return -1;
}

/*
 * One packet as a line; 0 when it holds none, a negative AVERROR when the lines' text would be
 * too much (E2BIG) or memory ran out (ENOMEM).
 */
static int take_packet(Events *e, Source source, const AVPacket *pkt, const AVStream *st, int64_t offset_ms)
{
    int64_t ts = pkt->pts != AV_NOPTS_VALUE ? pkt->pts : pkt->dts;
    if (ts == AV_NOPTS_VALUE || pkt->size <= 0)
        return 0;
    const AVRational ms = {1, 1000};
    int64_t start = av_rescale_q(ts, st->time_base, ms) - offset_ms;
    int64_t end = -1;
    if (pkt->duration > 0) {
        end = av_rescale_q(ts + pkt->duration, st->time_base, ms) - offset_ms;
        if (end <= 0)
            return 0; /* over before the film starts */
    }
    if (start < 0)
        start = 0;

    const char *data = (const char *)pkt->data;
    size_t size = (size_t)pkt->size;
    /* no line of a packet larger than the text left room for would fit: refused before its text is made */
    size_t room = e->bytes < MAX_TEXT_BYTES ? MAX_TEXT_BYTES - e->bytes : 0;
    if (size > room)
        return AVERROR(E2BIG);
    /* and no text being made grows past that room (ASS's line breaks, MP4's styles make it longer than its packet) */
    unsigned limit = (unsigned)room + 1;
    char *text = NULL;
    int64_t order = 0;
    int err = 0;
    if (source == SRC_ASS || source == SRC_SSA) {
        /* "ReadOrder,Layer,Style,Name,MarginL,MarginR,MarginV,Effect,Text" */
        const char *comma = memchr(data, ',', size);
        if (!comma)
            return 0;
        order = strtoll(data, NULL, 10);
        size_t rest = size - (size_t)(comma + 1 - data);
        AVBPrint bp;
        av_bprint_init(&bp, 0, limit);
        for (size_t i = 0; i < rest; i++) {
            char c = comma[1 + i];
            if (c == '\r' || c == 0)
                continue;
            if (c == '\n')
                av_bprint_append_data(&bp, "\\N", 2);
            else
                av_bprint_chars(&bp, c, 1);
        }
        if (!av_bprint_is_complete(&bp)) {
            err = incomplete(&bp);
        } else {
            /* a line break the muxer left at the end is not the line's */
            while (bp.len >= 2 && !strcmp(bp.str + bp.len - 2, "\\N"))
                bp.str[bp.len -= 2] = 0;
            if (bp.len && strchr(bp.str, ',') && !(text = strdup(bp.str)))
                err = AVERROR(ENOMEM);
        }
        av_bprint_finalize(&bp, NULL);
    } else if (source == SRC_WEBVTT || source == SRC_MOVTEXT) {
        AVBPrint bp;
        av_bprint_init(&bp, 0, limit);
        if (source == SRC_WEBVTT)
            webvtt_to_srt(data, size, &bp);
        else
            err = movtext_to_srt(pkt->data, pkt->size, &bp, limit);
        if (!err && !av_bprint_is_complete(&bp))
            err = incomplete(&bp);
        if (!err)
            text = cue_text(bp.str, bp.len, limit, &err);
        av_bprint_finalize(&bp, NULL);
    } else {
        text = cue_text(data, size, limit, &err);
    }
    if (!text)
        return err;
    return add_event(e, start, end, order, text);
}

char *subtitle_extract(const char *input, int track, const char *output, SeProgress progress, void *opaque, volatile int *cancel)
{
    char err[AV_ERROR_MAX_STRING_SIZE] = {0};
    AVFormatContext *ic = avformat_alloc_context();
    if (!ic)
        return result("memory", "", "", "", 0, 0);
    ic->interrupt_callback.callback = interrupted;
    ic->interrupt_callback.opaque = (void *)cancel;
    AVDictionary *opts = NULL;
    /* as the media probe opens it, so its tracks are counted the same */
    av_dict_set(&opts, "probesize", "20000000", 0);
    av_dict_set(&opts, "analyzeduration", "10000000", 0);
    int ret = avformat_open_input(&ic, input, NULL, &opts);
    av_dict_free(&opts);
    if (ret < 0)
        return result(stopped(cancel) ? "cancelled" : "open", error_text(ret, err, sizeof(err)), "", "", 0, 0);
    if ((ret = avformat_find_stream_info(ic, NULL)) < 0) {
        avformat_close_input(&ic);
        return result(stopped(cancel) ? "cancelled" : "open", error_text(ret, err, sizeof(err)), "", "", 0, 0);
    }
    int stream = pick_stream(ic, track);
    if (stream < 0) {
        avformat_close_input(&ic);
        return result("no_stream", "", "", "", 0, 0);
    }
    AVStream *st = ic->streams[stream];
    const char *codec = avcodec_get_name(st->codecpar->codec_id);
    Source source;
    if (!source_of(st->codecpar->codec_id, &source)) {
        int picture = is_picture(st->codecpar->codec_id);
        avformat_close_input(&ic);
        return result(picture ? "image" : "unsupported", "", codec, "", 0, 0);
    }
    const char *format = source == SRC_ASS ? "ass" : source == SRC_SSA ? "ssa" : "srt";
    /* only the subtitles are read: an MP4's demuxer then goes from one subtitle sample to the next */
    for (unsigned i = 0; i < ic->nb_streams; i++)
        if ((int)i != stream)
            ic->streams[i]->discard = AVDISCARD_ALL;
    /* MPEG-TS and programme streams count from wherever the recording began; the film from its start */
    int64_t offset_ms = 0;
    if ((ic->iformat->flags & AVFMT_TS_DISCONT) && ic->start_time != AV_NOPTS_VALUE)
        offset_ms = ic->start_time / 1000;

    Events events = {0};
    events.utf8 = 1;
    AVPacket *pkt = av_packet_alloc();
    int64_t size = ic->pb ? avio_size(ic->pb) : -1;
    int told = -1, partial = 0, failure = 0;
    while (pkt && !stopped(cancel)) {
        ret = av_read_frame(ic, pkt);
        if (ret == AVERROR(EAGAIN))
            continue;
        if (ret < 0) {
            /* a file that ends early (a download cut short) gives what it has */
            if (ret != AVERROR_EOF && !stopped(cancel))
                partial = 1;
            break;
        }
        if (pkt->stream_index == stream && (failure = take_packet(&events, source, pkt, st, offset_ms)) < 0) {
            av_packet_unref(pkt);
            break;
        }
        av_packet_unref(pkt);
        if (progress && size > 0) {
            int64_t at = avio_tell(ic->pb);
            int permille = at > 0 ? (int)(at * 1000 / size) : 0;
            if (permille > 1000)
                permille = 1000;
            if (permille > told) {
                told = permille;
                progress(opaque, permille / 1000.0);
            }
        }
    }
    char *json;
    int styled = source == SRC_ASS || source == SRC_SSA;
    if (!pkt) {
        json = result("memory", "", codec, format, 0, 0);
    } else if (stopped(cancel)) {
        json = result("cancelled", "", codec, format, 0, 0);
    } else if (failure == AVERROR(E2BIG)) {
        json = result("too_large", "", codec, format, events.count, 0);
    } else if (failure < 0) {
        json = result("memory", "", codec, format, 0, 0);
    } else if (!events.count) {
        json = result("empty", partial ? error_text(ret, err, sizeof(err)) : "", codec, format, 0, partial);
    } else if (settle(&events, !styled, cancel) < 0) {
        json = result("cancelled", "", codec, format, 0, 0);
    } else {
        if (styled)
            qsort(events.items, events.count, sizeof(Event), by_order);
        /* a new file only: whatever is there already stays as it is */
        FILE *f = fopen(output, "wbx");
        int written = f != NULL;
        if (written && events.utf8)
            written = fwrite("\xEF\xBB\xBF", 1, 3, f) == 3;
        if (written)
            written = (styled ? write_ass(f, &events, st->codecpar, source == SRC_SSA, cancel) : write_srt(f, &events, cancel)) == 0;
        int saved_errno = errno;
        if (f && fclose(f) != 0)
            written = 0;
        if (!written) {
            if (f)
                unlink(output);
            json = stopped(cancel) ? result("cancelled", "", codec, format, 0, 0) : result("write", strerror(saved_errno), codec, format, 0, 0);
        } else {
            json = result("", partial ? error_text(ret, err, sizeof(err)) : "", codec, format, events.count, partial);
        }
    }
    if (progress && !stopped(cancel) && json && told < 1000)
        progress(opaque, 1.0);
    free_events(&events);
    av_packet_free(&pkt);
    avformat_close_input(&ic);
    return json;
}
