# nf-snapshot

Nextflow plugin that verifies a run's published files against a committed JSON snapshot.

- Source: `src/main/groovy/nextflow/snapshot/`. All logic lives in `SnapshotObserver` (`TraceObserverV2`); `capture`, `hash` and `diff` are static so they can be unit tested.
- Mismatches fail the run by throwing `AbortRunException` from `onFlowComplete`. This is the only exception Nextflow rethrows from observers; anything else is logged at debug and swallowed, so `onFlowComplete` converts every other exception into `AbortRunException`. Keep new logic inside `check()`.
- Build and test: `make test` (Java 17+). Compiles against Nextflow 26.04.6.
- End-to-end: `make e2e` runs `validation/run-tests.sh`, which runs with `NXF_SYNTAX_PARSER=v2` and checks exit code and output for each scenario. Add a `check` line there for new behaviour. Set `NXF_SNAPSHOT_UPDATE=true` to rewrite `validation/snapshot.json`; the validation pipeline must stay deterministic.
- Lint: `pre-commit run --all-files` (Prettier, `nextflow lint`, whitespace).
- Release: bump `version` in `build.gradle` and add a `CHANGELOG.md` section; `.github/workflows/publish.yml` publishes after CI passes on `main`.
- New config options go in `SnapshotConfig` (`@ConfigOption` + `@Description`) and the README options table.
