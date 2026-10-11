// Host command line for the S-802 media probe: mediaprobe <file>
// Prints the probe's JSON; used by buildsystem/nagmedar-native/test-host.sh.
#include "mediaprobe.h"

#include <stdio.h>
#include <stdlib.h>

int main(int argc, char **argv)
{
    if (argc < 2) {
        fprintf(stderr, "usage: %s <file>\n", argv[0]);
        return 2;
    }
    char *json = media_probe(argv[1], NULL);
    if (!json)
        return 1;
    printf("%s\n", json);
    free(json);
    return 0;
}
