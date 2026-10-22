// Host command line for the S-1202 bitrate analysis: bitrate <file> [max_seconds]
// Prints the analysis' JSON; used by buildsystem/nagmedar-native/test-host.sh.
#include "bitrate.h"

#include <stdio.h>
#include <stdlib.h>

static int progress(void *opaque, double fraction)
{
    (void)opaque;
    fprintf(stderr, "\r%.0f %%", fraction * 100);
    return 0;
}

int main(int argc, char **argv)
{
    if (argc < 2) {
        fprintf(stderr, "usage: %s <file> [max_seconds]\n", argv[0]);
        return 2;
    }
    double max_seconds = argc > 2 ? atof(argv[2]) : 0;
    char *json = media_bitrate(argv[1], max_seconds, progress, NULL, NULL);
    fprintf(stderr, "\n");
    if (!json)
        return 1;
    printf("%s\n", json);
    free(json);
    return 0;
}
