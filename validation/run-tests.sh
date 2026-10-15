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

echo
if (( FAILED )); then
    echo "$FAILED test(s) failed"
    exit 1
fi
echo "All tests passed"
