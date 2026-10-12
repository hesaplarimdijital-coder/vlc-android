// Host command line for the S-1204 disk test: disktest <file> [max_mb] [max_seconds]
#include "disktest.h"

#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

int main(int argc, char **argv)
{
    if (argc < 2) {
        fprintf(stderr, "usage: %s <file> [max_mb] [max_seconds]\n", argv[0]);
        return 2;
    }
    int fd = open(argv[1], O_RDONLY);
    if (fd < 0) {
        perror("open");
        return 1;
    }
    long long max_bytes = argc > 2 ? atoll(argv[2]) * 1000 * 1000 : 0;
    double max_seconds = argc > 3 ? atof(argv[3]) : 0;
    char *json = disk_read_speed(fd, 0, max_bytes, max_seconds, NULL, NULL, NULL);
    close(fd);
    if (!json)
        return 1;
    printf("%s\n", json);
    free(json);
    return 0;
}
