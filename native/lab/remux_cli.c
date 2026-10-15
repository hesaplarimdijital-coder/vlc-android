// Host command line for the remux engine (S-1206, S-1209), used by test-host.sh:
//   remux run <input> <output> <plan-file>      the plan as remux.h describes it ("-" copies every stream)
//   remux verify <output> <scope> <hash> <packets> [dv-profile]
// Prints the stats or the verification as JSON.
#include "remux.h"

#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static int progress(void *opaque, double done, double total, int64_t bytes)
{
    (void)opaque;
    fprintf(stderr, "\r%.1f / %.1f s, %.1f MB", done, total, (double)bytes / 1e6);
    return 0;
}

static char *read_file(const char *path)
{
    FILE *f = fopen(path, "rb");
    if (!f)
        return NULL;
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    char *text = malloc((size_t)size + 1);
    if (text && fread(text, 1, (size_t)size, f) == (size_t)size)
        text[size] = 0;
    else
        free(text), text = NULL;
    fclose(f);
    return text;
}

static int run(int argc, char **argv)
{
    if (argc < 5) {
        fprintf(stderr, "usage: remux run <input> <output> <plan-file|->\n");
        return 2;
    }
    char *text = strcmp(argv[4], "-") ? read_file(argv[4]) : strdup("stream\tv:0\n");
    if (!text) {
        fprintf(stderr, "cannot read the plan %s\n", argv[4]);
        return 2;
    }
    RemuxPlan plan;
    char err[256];
    if (remux_plan_parse(text, &plan, err, sizeof(err)) < 0) {
        fprintf(stderr, "bad plan: %s\n", err);
        free(text);
        return 2;
    }
    free(text);
    int in = open(argv[2], O_RDONLY);
    int out = open(argv[3], O_RDWR | O_CREAT, 0644);
    if (in < 0 || out < 0) {
        perror("open");
        return 2;
    }
    RemuxStats stats;
    int ret = remux_run(in, out, &plan, NULL, progress, NULL, &stats);
    fprintf(stderr, "\n");
    char *json = remux_stats_json(&stats);
    printf("%s\n", json ? json : "{}");
    free(json);
    close(in);
    close(out);
    return ret < 0 ? 1 : 0;
}

static int verify(int argc, char **argv)
{
    if (argc < 6) {
        fprintf(stderr, "usage: remux verify <output> <scope> <hash> <packets> [dv-profile]\n");
        return 2;
    }
    int scope = remux_hash_scope_of(argv[3]);
    if (scope < 0) {
        fprintf(stderr, "unknown scope %s\n", argv[3]);
        return 2;
    }
    int fd = open(argv[2], O_RDONLY);
    if (fd < 0) {
        perror("open");
        return 2;
    }
    RemuxVerification result;
    int ret = remux_verify(fd, scope, argv[4], atoll(argv[5]), argc > 6 ? atoi(argv[6]) : 0, NULL, progress, NULL, &result);
    fprintf(stderr, "\n");
    char *json = remux_verification_json(&result);
    printf("%s\n", json ? json : "{}");
    free(json);
    close(fd);
    return ret < 0 ? 1 : (result.ok ? 0 : 3);
}

int main(int argc, char **argv)
{
    if (argc >= 2 && !strcmp(argv[1], "run"))
        return run(argc, argv);
    if (argc >= 2 && !strcmp(argv[1], "verify"))
        return verify(argc, argv);
    fprintf(stderr, "usage: remux run <input> <output> <plan-file|->\n       remux verify <output> <scope> <hash> <packets> [dv-profile]\n");
    return 2;
}
