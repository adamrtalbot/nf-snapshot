# nf-snapshot

Nextflow plugin that verifies a run's published files against a committed JSON snapshot.

Read [AGENTS.md](AGENTS.md) for agent workflows and project guardrails.
Find Claude command shortcuts in `.claude/commands/` and workflow guidance
in `.agents/skills/`.

- Source: `src/main/groovy/nextflow/snapshot/`. `SnapshotObserver` (`TraceObserverV2`) handles capture and comparison; use its static `capture`, `hash` and `diff` methods in unit tests.
- The check runs in a session shutdown hook that `SnapshotFactory` registers, not in `onFlowComplete`. Nextflow creates observers just before `WorkflowMetadata`, whose own shutdown hook fixes `workflow.success`; hooks run in order, so the check runs first and Seqera Platform sees the failure. Failing in `onFlowComplete` is too late: the run exits 1 locally but Platform reports success. Nextflow swallows exceptions from shutdown hooks, so `verify()` fails the run with `session.abort(AbortRunException)`. Keep new logic inside `check()`. On Nextflow 26.04 an abort inside a shutdown hook re-runs the shutdown, so observers (Platform included) get `onFlowComplete` twice, both with `success=false`; 26.10 guards the shutdown and sends it once. Don't swap `abort` for `cancel`: it is package-private and sets no `session.error`, so Platform would get no error message.
- `SnapshotPlugin` implements `PluginExecAware` for `nextflow plugin nf-snapshot:update`. Nextflow 26.04 calls the `exec(Launcher, ...)` overload and later versions call `exec(String, String, List, Map)`; keep both. Plugin command args must use `--` (JCommander rejects single-dash `-profile`). Use `nf-snapshot@<version>:cmd` to test plugin commands against a release candidate installed with `make install`; Nextflow 26.04 cannot resolve local plugin commands by version, so those CLI cases run only on latest-stable in CI.
- Build and test: `make test` (Java 17+). Compiles against Nextflow 26.04.6.
- End-to-end: `make e2e` runs `validation/run-tests.sh`, which runs with `NXF_SYNTAX_PARSER=v2` and checks exit code and output for each scenario. Add a `check` line there for new behaviour. Set `NXF_SNAPSHOT_UPDATE=true` to rewrite `validation/snapshot.json`; the validation pipeline must stay deterministic.
- Lint: `pre-commit run --all-files` (Prettier, `nextflow lint`, whitespace).
- Release: run `/release-pr [version]` to open the release PR (bumps `build.gradle` and doc versions, moves `CHANGELOG.md` Unreleased into a version section). Merging it publishes via `.github/workflows/publish.yml` after CI passes on `main`. Add user-facing changes under `## [Unreleased]` as they land.
- New config options go in `SnapshotConfig` (`@ConfigOption` + `@Description`) and the README options table.
