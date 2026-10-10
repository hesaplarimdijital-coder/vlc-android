// Host command line for the SP-05 pipeline: whisper_lab_cli <model> <input> [language] [max-seconds]
// Prints the JSON report; used by buildsystem/nagmedar-native/test-host.sh with whisper.cpp's test model.
#include "whisper_lab.h"

#include <stdio.h>
#include <stdlib.h>

static void progress(void *opaque, double fraction, const char *stage)
{
    (void)opaque;
    fprintf(stderr, "\r%s %3.0f%%", stage, fraction * 100);
}

int main(int argc, char **argv)
{
    if (argc < 3) {
        fprintf(stderr, "usage: %s <model> <input> [language] [max-seconds]\n", argv[0]);
        return 2;
    }
    char *json = whisper_lab_run(argv[1], argv[2], argc > 3 ? argv[3] : "tr", argc > 4 ? atof(argv[4]) : 0, 0, progress, NULL, NULL);
    fprintf(stderr, "\n");
    printf("%s\n", json ? json : "{}");
    free(json);
    return 0;
}
