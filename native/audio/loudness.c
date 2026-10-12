/*
 * Nağmedar Ses Motoru: the EBU R128 scanner (S-907), ITU-R BS.1770-4.
 *
 * Integrated loudness: K-weighting (a high shelf and a high-pass, designed for the file's own
 * rate), the mean square of 400 ms blocks every 100 ms, channel weights, the absolute gate at
 * -70 LUFS and the relative gate 10 LU under the mean of what passed it. The blocks' energies
 * are kept, so an album is the gating over all its tracks' blocks. True peak: the signal four
 * times oversampled with the standard's 48-tap FIR (Annex 2), the largest sample of that.
 * ReplayGain 2.0 gains: -18 LUFS reference.
 */
#include "nagaudio.h"
#include "source.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define CHANNELS_MAX 16
#define READ_FRAMES 4096
#define ABSOLUTE_GATE_LUFS (-70.0)
#define RELATIVE_GATE_LU (-10.0)
#define REFERENCE_LUFS (-18.0)

/* BS.1770-4 Annex 2: the 4x oversampling FIR, 12 taps per phase */
static const double interp[4][12] = {
    {0.0017089843750, 0.0109863281250, -0.0196533203125, 0.0332031250000, -0.0594482421875, 0.1373291015625,
     0.9721679687500, -0.1022949218750, 0.0476074218750, -0.0266113281250, 0.0148925781250, -0.0083007812500},
    {-0.0291748046875, 0.0292968750000, -0.0517578125000, 0.0891113281250, -0.1665039062500, 0.4650878906250,
     0.7797851562500, -0.2003173828125, 0.1015625000000, -0.0582275390625, 0.0330810546875, -0.0189208984375},
    {-0.0189208984375, 0.0330810546875, -0.0582275390625, 0.1015625000000, -0.2003173828125, 0.7797851562500,
     0.4650878906250, -0.1665039062500, 0.0891113281250, -0.0517578125000, 0.0292968750000, -0.0291748046875},
    {-0.0083007812500, 0.0148925781250, -0.0266113281250, 0.0476074218750, -0.1022949218750, 0.9721679687500,
     0.1373291015625, -0.0594482421875, 0.0332031250000, -0.0196533203125, 0.0109863281250, 0.0017089843750},
};

typedef struct Biquad {
    double b0, b1, b2, a1, a2;
    double s1[CHANNELS_MAX], s2[CHANNELS_MAX];
} Biquad;

/* The blocks' weighted energies, growable; shared by a track and its album. */
typedef struct Blocks {
    double *z;
    size_t n, cap;
} Blocks;

struct NagLoudnessScan {
    Blocks album;
    double true_peak, sample_peak;
    int64_t frames;
};

static int blocks_add(Blocks *b, double z)
{
    if (b->n == b->cap) {
        size_t cap = b->cap ? b->cap * 2 : 4096;
        double *p = realloc(b->z, cap * sizeof *p);
        if (!p)
            return -1;
        b->z = p;
        b->cap = cap;
    }
    b->z[b->n++] = z;
    return 0;
}

/* The K-weighting for [rate]: the pre-filter (a high shelf) and the RLB high-pass. */
static void k_weighting(Biquad *shelf, Biquad *highpass, double rate)
{
    memset(shelf, 0, sizeof *shelf);
    memset(highpass, 0, sizeof *highpass);
    {
        const double f0 = 1681.974450955533, g = 3.999843853973347, q = 0.7071752369554196;
        double k = tan(M_PI * f0 / rate);
        double vh = pow(10.0, g / 20.0);
        double vb = pow(vh, 0.4996667741545416);
        double a0 = 1.0 + k / q + k * k;
        shelf->b0 = (vh + vb * k / q + k * k) / a0;
        shelf->b1 = 2.0 * (k * k - vh) / a0;
        shelf->b2 = (vh - vb * k / q + k * k) / a0;
        shelf->a1 = 2.0 * (k * k - 1.0) / a0;
        shelf->a2 = (1.0 - k / q + k * k) / a0;
    }
    {
        const double f0 = 38.13547087602444, q = 0.5003270373238773;
        double k = tan(M_PI * f0 / rate);
        double a0 = 1.0 + k / q + k * k;
        highpass->b0 = 1.0;
        highpass->b1 = -2.0;
        highpass->b2 = 1.0;
        highpass->a1 = 2.0 * (k * k - 1.0) / a0;
        highpass->a2 = (1.0 - k / q + k * k) / a0;
    }
}

static inline double biquad_run(Biquad *q, int c, double in)
{
    double out = q->b0 * in + q->s1[c];
    q->s1[c] = q->b1 * in - q->a1 * out + q->s2[c];
    q->s2[c] = q->b2 * in - q->a2 * out;
    return out;
}

/* The gated loudness of [blocks] in LUFS; -HUGE_VAL when nothing passes the gates. */
static double gated_loudness(const Blocks *blocks)
{
    const double absolute = pow(10.0, (ABSOLUTE_GATE_LUFS + 0.691) / 10.0);
    double sum = 0;
    size_t n = 0;
    for (size_t i = 0; i < blocks->n; i++) {
        if (blocks->z[i] > absolute) {
            sum += blocks->z[i];
            n++;
        }
    }
    if (!n)
        return -HUGE_VAL;
    double relative = sum / n * pow(10.0, RELATIVE_GATE_LU / 10.0);
    sum = 0;
    n = 0;
    for (size_t i = 0; i < blocks->n; i++) {
        if (blocks->z[i] > absolute && blocks->z[i] > relative) {
            sum += blocks->z[i];
            n++;
        }
    }
    if (!n)
        return -HUGE_VAL;
    return -0.691 + 10.0 * log10(sum / n);
}

static double replaygain_db(double lufs)
{
    return lufs > -HUGE_VAL ? REFERENCE_LUFS - lufs : 0.0;
}

NagLoudnessScan *nag_loudness_scan_new(void)
{
    return calloc(1, sizeof(NagLoudnessScan));
}

void nag_loudness_scan_free(NagLoudnessScan *scan)
{
    if (!scan)
        return;
    free(scan->album.z);
    free(scan);
}

int nag_loudness_scan_file(NagLoudnessScan *album, const char *url, NagLoudnessResult *result, const int *cancel, char *error, size_t error_size)
{
    memset(result, 0, sizeof *result);
    result->loudness_lufs = -HUGE_VAL;
    NagSource *src = NULL;
    int ret = nag_source_open(&src, url, 0, error, error_size);
    if (ret < 0)
        return ret;
    const NagSourceInfo *info = nag_source_info(src);
    const int channels = nag_source_channels(src);
    const double rate = info->rate;
    if (channels > CHANNELS_MAX || rate <= 0) {
        snprintf(error, error_size, "%d channels at %d Hz", channels, info->rate);
        nag_source_close(src);
        return -1;
    }
    double weights[CHANNELS_MAX];
    nag_source_channel_weights(src, weights, CHANNELS_MAX);
    Biquad shelf, highpass;
    k_weighting(&shelf, &highpass, rate);
    /* 100 ms sub-blocks; a block is the last four */
    const int64_t sub_frames = (int64_t)lrint(rate / 10.0);
    double sub[4] = {0, 0, 0, 0};
    int subs_done = 0;
    double sub_energy[CHANNELS_MAX] = {0};
    int64_t sub_count = 0;
    /* true peak: the last 11 samples of each channel, for the FIR */
    double history[CHANNELS_MAX][12] = {{0}};
    double true_peak = 0, sample_peak = 0;
    Blocks blocks = {0};
    double *buf = malloc(sizeof(double) * READ_FRAMES * channels);
    if (!buf) {
        nag_source_close(src);
        return -1;
    }
    int64_t frames = 0;
    for (;;) {
        if (cancel && __atomic_load_n(cancel, __ATOMIC_RELAXED)) {
            ret = -2;
            break;
        }
        int n = nag_source_read(src, buf, READ_FRAMES);
        if (n < 0) {
            snprintf(error, error_size, "decoding failed");
            ret = n;
            break;
        }
        if (n == 0)
            break;
        for (int i = 0; i < n; i++) {
            const double *frame = buf + (size_t)i * channels;
            for (int c = 0; c < channels; c++) {
                double x = frame[c];
                double a = fabs(x);
                if (a > sample_peak)
                    sample_peak = a;
                /* the oversampled peak: four phases of the FIR over this and the last 11 samples */
                double *h = history[c];
                memmove(h, h + 1, 11 * sizeof(double));
                h[11] = x;
                for (int p = 0; p < 4; p++) {
                    double y = 0;
                    for (int t = 0; t < 12; t++)
                        y += interp[p][t] * h[11 - t];
                    double ay = fabs(y);
                    if (ay > true_peak)
                        true_peak = ay;
                }
                /* K-weighted energy */
                double k = biquad_run(&highpass, c, biquad_run(&shelf, c, x));
                sub_energy[c] += k * k;
            }
            if (++sub_count == sub_frames) {
                double z = 0;
                for (int c = 0; c < channels; c++) {
                    z += weights[c] * sub_energy[c] / sub_frames;
                    sub_energy[c] = 0;
                }
                sub_count = 0;
                sub[subs_done % 4] = z;
                subs_done++;
                if (subs_done >= 4) {
                    double block = (sub[0] + sub[1] + sub[2] + sub[3]) / 4.0;
                    if (blocks_add(&blocks, block) < 0 || (album && blocks_add(&album->album, block) < 0)) {
                        ret = -1;
                        break;
                    }
                }
            }
        }
        if (ret < 0)
            break;
        frames += n;
    }
    free(buf);
    nag_source_close(src);
    if (ret >= 0) {
        result->frames = frames;
        result->rate = info->rate;
        result->loudness_lufs = gated_loudness(&blocks);
        result->sample_peak = sample_peak;
        result->true_peak = true_peak > sample_peak ? true_peak : sample_peak;
        result->gain_db = replaygain_db(result->loudness_lufs);
        if (album) {
            album->frames += frames;
            if (result->true_peak > album->true_peak)
                album->true_peak = result->true_peak;
            if (result->sample_peak > album->sample_peak)
                album->sample_peak = result->sample_peak;
        }
        ret = 0;
    }
    free(blocks.z);
    return ret;
}

int nag_loudness_scan_result(const NagLoudnessScan *album, NagLoudnessResult *result)
{
    memset(result, 0, sizeof *result);
    result->frames = album->frames;
    result->loudness_lufs = gated_loudness(&album->album);
    result->sample_peak = album->sample_peak;
    result->true_peak = album->true_peak;
    result->gain_db = replaygain_db(result->loudness_lufs);
    return album->album.n ? 0 : -1;
}
