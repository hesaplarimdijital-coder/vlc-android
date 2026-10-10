// Host command line for the SP-03 converter: dvconvert <input> <output> [--max-seconds N] [--mp4]
// Prints the stats as JSON; used by buildsystem/nagmedar-native/test-host.sh.
#include "dvconvert.h"

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void progress(void *opaque, double done, double total, int64_t bytes)
{
    (void)opaque;
    fprintf(stderr, "\r%.1f / %.1f s, %.1f MB", done, total, (double)bytes / 1e6);
}

int main(int argc, char **argv)
{
    if (argc < 3) {
        fprintf(stderr, "usage: %s <input> <output> [--max-seconds N] [--mp4]\n", argv[0]);
        return 2;
    }
    double max_seconds = 0;
    int mp4 = 0;
    for (int i = 3; i < argc; i++) {
        if (!strcmp(argv[i], "--max-seconds") && i + 1 < argc)
            max_seconds = atof(argv[++i]);
        else if (!strcmp(argv[i], "--mp4"))
            mp4 = 1;
    }
    DvcStats stats;
    int ret = dvc_convert(argv[1], argv[2], max_seconds, mp4, NULL, progress, NULL, &stats);
    fprintf(stderr, "\n");
    char *json = dvc_stats_json(&stats, argv[1], argv[2]);
    printf("%s\n", json ? json : "{}");
    free(json);
    return ret < 0 ? 1 : 0;
}
