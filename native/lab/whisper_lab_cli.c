// Host command line for the SP-05 pipeline: whisper_lab_cli <model> <input> [language] [max-seconds] [threads] [cpu]
// Prints the JSON report; used by buildsystem/nagmedar-native/test-host.sh with whisper.cpp's test model.
// The GPU is asked for unless "cpu" is given; a build without one runs on the processor and says so.
#include "whisper_lab.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void progress(void *opaque, double fraction, const char *stage)
{
    (void)opaque;
    fprintf(stderr, "\r%s %3.0f%%", stage, fraction * 100);
}

int main(int argc, char **argv)
{
    if (argc < 3) {
        fprintf(stderr, "usage: %s <model> <input> [language] [max-seconds] [threads] [cpu]\n", argv[0]);
        return 2;
    }
    int gpu = !(argc > 6 && strcmp(argv[6], "cpu") == 0);
    char *json = whisper_lab_run(argv[1], argv[2], argc > 3 ? argv[3] : "tr", argc > 4 ? atof(argv[4]) : 0, argc > 5 ? atoi(argv[5]) : 0, gpu,
                                 progress, NULL, NULL);
    fprintf(stderr, "\n");
    printf("%s\n", json ? json : "{}");
    free(json);
    return 0;
}
