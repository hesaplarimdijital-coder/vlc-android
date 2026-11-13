#!/bin/sh
# Compiles the pure search sources of a worktree (argument 1) with the harness stubs and the given tests, then runs them.
# usage: build.sh <worktree> [TestClass ...]      (extra sources: $EXTRA, extra tests: tests/*.kt of the harness)
set -e
unset JAVA_TOOL_OPTIONS
H=$(cd "$(dirname "$0")" && pwd)
W=$1/application; shift
L=/opt/gradle-8.14.3/lib
CP=$L/kotlin-stdlib-2.0.21.jar:$L/kotlinx-coroutines-core-jvm-1.6.4.jar:$L/junit-4.13.2.jar:$L/hamcrest-core-1.3.jar
KC="java -Xmx1500m -cp $L/kotlin-compiler-embeddable-2.0.21.jar:$L/kotlin-stdlib-2.0.21.jar:$L/kotlin-script-runtime-2.0.21.jar:$L/kotlin-reflect-2.0.21.jar:$L/kotlin-daemon-embeddable-2.0.21.jar:$L/trove4j-1.0.20200330.jar:$L/annotations-24.0.1.jar:$L/kotlinx-coroutines-core-jvm-1.6.4.jar org.jetbrains.kotlin.cli.jvm.K2JVMCompiler"
U=$W/nagmedar-ui/src/main/java/app/nagmedar/ui
T=$W/nagmedar-ui/src/test/java/app/nagmedar/ui
SRC="$H/stubs/*.kt $U/search/FuzzyMatch.kt $U/search/LibrarySearch.kt $U/search/PaletteResults.kt $U/music/MusicLibrary.kt $EXTRA"
# 1.0.7: the search's artists and albums, where the worktree has them
[ -f $U/search/SearchGroups.kt ] && SRC="$SRC $H/stubs2/*.kt $U/search/SearchGroups.kt"
OUT=$H/out; rm -rf $OUT && mkdir -p $OUT
$KC -nowarn -no-stdlib -cp $CP -d $OUT $SRC 2>&1 | grep -v "^warning" || true
[ -f $OUT/app/nagmedar/ui/search/FuzzyMatcher.class ] || { echo "main compile failed"; exit 1; }
TESTS="$T/search/FuzzyMatchTest.kt $T/search/LibrarySearchTest.kt $T/search/PaletteResultsTest.kt $(ls $H/tests/*.kt 2>/dev/null || true) $TESTEXTRA"
[ -f $T/search/SearchGroupsTest.kt ] && TESTS="$TESTS $T/search/SearchGroupsTest.kt"
$KC -nowarn -no-stdlib -cp $CP:$OUT -d $OUT $TESTS 2>&1 | grep -v "^warning" || true
[ -f $OUT/app/nagmedar/ui/search/PaletteResultsTest.class ] || { echo "test compile failed"; exit 1; }
if [ $# -eq 0 ]; then set -- app.nagmedar.ui.search.FuzzyMatchTest app.nagmedar.ui.search.LibrarySearchTest app.nagmedar.ui.search.PaletteResultsTest; fi
java -cp $CP:$OUT org.junit.runner.JUnitCore "$@" 2>&1 | grep -v "^\s*at org.junit\|^\s*at java\.\|^\s*at sun\.\|^\s*at kotlin\.\|^\s*at jdk\." | tail -60
