// Host command line for the embedded subtitles' extraction: subextract <file> <track> <output>
// Prints the extraction's JSON; used by buildsystem/nagmedar-native/test-host.sh.
#include "subtitle_extract.h"

#include <stdio.h>
#include <stdlib.h>

int main(int argc, char **argv)
{
    if (argc < 4) {
        fprintf(stderr, "usage: %s <file> <track> <output>\n", argv[0]);
        return 2;
    }
    volatile int cancel = 0;
    char *json = subtitle_extract(argv[1], atoi(argv[2]), argv[3], NULL, NULL, &cancel);
    if (!json)
        return 1;
    printf("%s\n", json);
    free(json);
    return 0;
}
