# nf-snapshot

Nextflow plugin that verifies a run's published files against a committed JSON snapshot.

- Source: `src/main/groovy/nextflow/snapshot/`. All logic lives in `SnapshotObserver` (`TraceObserverV2`); `capture`, `hash` and `diff` are static so they can be unit tested.
- Mismatches fail the run by throwing `AbortRunException` from `onFlowComplete`. This is the only exception Nextflow rethrows from observers; anything else is logged at debug and swallowed, so `onFlowComplete` converts every other exception into `AbortRunException`. Keep new logic inside `check()`.
- Build and test: `./gradlew assemble test install` (Java 17). Compiles against Nextflow 26.04.6.
- End-to-end: `cd validation && rm -rf results && nextflow run . -profile snap`. Delete `results` between runs, because `publishDir` keeps stale files with `-resume`. Set `NXF_SNAPSHOT_UPDATE=true` to rewrite `validation/snapshot.json`.
- New config options go in `SnapshotConfig` (`@ConfigOption` + `@Description`) and the README options table.
