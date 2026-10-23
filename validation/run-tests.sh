#!/usr/bin/env bash
# End-to-end tests: run the validation pipeline with the installed plugin and check exit code and output.
# Usage: validation/run-tests.sh (after `make install`)
set -uo pipefail
export NXF_SYNTAX_PARSER=v2
cd "$(dirname "$0")"

VERSION=$(sed -n "s/^version = '\(.*\)'/\1/p" ../build.gradle)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP" results' EXIT
FAILED=0

# check <name> <expected exit> <expected output> [nextflow run args...]
check() {
    local name=$1 want=$2 text=$3
    shift 3
    rm -rf results
    nextflow run . -plugins "nf-snapshot@$VERSION" -resume "$@" > "$TMP/out.log" 2>&1
    local got=$?
    if [[ $got == "$want" ]] && grep -qF -- "$text" "$TMP/out.log"; then
        echo "PASS  $name"
    else
        echo "FAIL  $name: exit $got (want $want), expected output '$text'"
        cat "$TMP/out.log"
        FAILED=$((FAILED + 1))
    fi
}

# config <text>: write a config file and print its path
config() {
    local file="$TMP/$RANDOM.config"
    echo "$1" > "$file"
    echo "$file"
}

sed 's#say/bob.txt#say/carol.txt#' snapshot.json > "$TMP/renamed.json"
echo '{' > "$TMP/broken.json"

check "outputs match"             0 "6 files match"                 -profile snap
# Nextflow 26.04 cannot run a local PluginExecAware command by version; latest-stable covers these cases.
if nextflow -version | grep -q 'version 26\.04\.'; then
    echo "SKIP  plugin command tests on Nextflow 26.04 (local plugin command lookup is unsupported)"
else
# update command rewrites the snapshot from the outputs the previous check left in results/
cp snapshot.json "$TMP/committed.json"
echo '{}' > snapshot.json
nextflow plugin "nf-snapshot@$VERSION:update" --profile snap > "$TMP/out.log" 2>&1
got=$?
if [[ $got == 0 ]] && grep -qF "wrote 6 files" "$TMP/out.log" && cmp -s snapshot.json "$TMP/committed.json"; then
    echo "PASS  update command"
else
    echo "FAIL  update command: exit $got (want 0), snapshot.json differs or 'wrote 6 files' missing"
    cat "$TMP/out.log"
    FAILED=$((FAILED + 1))
fi
# --outputDir points the command at outputs outside the configured base dir
mv results "$TMP/moved"
echo '{}' > snapshot.json
nextflow plugin "nf-snapshot@$VERSION:update" --profile snap --outputDir "$TMP/moved" > "$TMP/out.log" 2>&1
got=$?
if [[ $got == 0 ]] && grep -qF "wrote 6 files" "$TMP/out.log" && cmp -s snapshot.json "$TMP/committed.json"; then
    echo "PASS  update command --outputDir"
else
    echo "FAIL  update command --outputDir: exit $got (want 0), snapshot.json differs or 'wrote 6 files' missing"
    cat "$TMP/out.log"
    FAILED=$((FAILED + 1))
fi
cp "$TMP/committed.json" snapshot.json
# unknown arguments fail without touching the snapshot
nextflow plugin "nf-snapshot@$VERSION:update" --proflie snap > "$TMP/out.log" 2>&1
got=$?
if [[ $got != 0 ]] && grep -qF "unknown argument" "$TMP/out.log" && cmp -s snapshot.json "$TMP/committed.json"; then
    echo "PASS  update command rejects unknown argument"
else
    echo "FAIL  update command rejects unknown argument: exit $got (want non-zero), snapshot changed or message missing"
    cat "$TMP/out.log"
    FAILED=$((FAILED + 1))
fi
# a relative output dir resolves against NXF_FILE_ROOT, as it does for the run
mkdir -p "$TMP/root" results
mv "$TMP/moved" "$TMP/root/results"
echo stale > results/stale.txt
echo '{}' > snapshot.json
NXF_FILE_ROOT="$TMP/root" nextflow plugin "nf-snapshot@$VERSION:update" --profile snap > "$TMP/out.log" 2>&1
got=$?
if [[ $got == 0 ]] && cmp -s snapshot.json "$TMP/committed.json"; then
    echo "PASS  update command honours NXF_FILE_ROOT"
else
    echo "FAIL  update command honours NXF_FILE_ROOT: exit $got (want 0), snapshot.json differs"
    cat "$TMP/out.log" snapshot.json
    FAILED=$((FAILED + 1))
fi
cp "$TMP/committed.json" snapshot.json
fi
check "content changed"           1 "changed:    say/alice.txt"     -profile snap --greeting hi
check "file missing"              1 "missing:    say/carol.txt"     -profile snap -c "$(config "snapshot.file = '$TMP/renamed.json'")"
check "file unexpected"           1 "unexpected: say/bob.txt"       -profile snap -c "$(config "snapshot.file = '$TMP/renamed.json'")"
check "broken snapshot fails"     1 "check failed"                  -profile snap -c "$(config "snapshot.file = '$TMP/broken.json'")"
check "output outside base dir"   1 "outside snapshot base dir"     -profile snap -c "$(config "snapshot.baseDir = '$TMP/elsewhere'")"
check "file option required"      1 "snapshot.file"                 -profile snap -c "$(config "snapshot.file = null")"
check "disabled by default"       0 ""
if grep -q "nf-snapshot:" "$TMP/out.log"; then
    echo "FAIL  disabled by default: plugin ran a check"
    FAILED=$((FAILED + 1))
fi
check "creates missing snapshot"  0 "wrote 6 files"                 -profile snap -c "$(config "snapshot.file = '$TMP/new.json'")"
check "verifies created snapshot" 0 "6 files match"                 -profile snap -c "$(config "snapshot.file = '$TMP/new.json'")"
check "update option rewrites"    0 "wrote 6 files"                 -profile snap -c "$(config "snapshot { file = '$TMP/new.json'; update = true }")" --greeting hi
check "rewritten snapshot used"   0 "6 files match"                 -profile snap -c "$(config "snapshot.file = '$TMP/new.json'")" --greeting hi
export NXF_SNAPSHOT_UPDATE=true
check "update env var rewrites"   0 "wrote 6 files"                 -profile snap -c "$(config "snapshot.file = '$TMP/new.json'")"
unset NXF_SNAPSHOT_UPDATE
check "env-rewritten snapshot"    0 "6 files match"                 -profile snap -c "$(config "snapshot.file = '$TMP/new.json'")"
SIZE=$(config "snapshot { file = '$TMP/size.json'; ignore = ['gz/**']; sizeOnly = ['**/*.txt'] }")
check "size-only writes size"     0 "wrote 4 files"                 -profile snap -c "$SIZE"
check "size-only same size"       0 "4 files match"                 -profile snap -c "$SIZE" --greeting hallo
check "size-only size changed"    1 "changed:    say/alice.txt"     -profile snap -c "$SIZE" --greeting hi

echo
if (( FAILED )); then
    echo "$FAILED test(s) failed"
    exit 1
fi
echo "All tests passed"
