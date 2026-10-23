# Changelog

## [Unreleased]

## [0.2.1]

- Clarify setup, configuration, and snapshot guidance in the README and documentation.
- Use HTTP/1.1 for documentation deployment fetches to avoid HTTP/2 failures.
- Simplify the release PR workflow.

## [0.2.0]

- Add `nextflow plugin nf-snapshot:update` to rewrite snapshots from existing outputs, with optional `--profile` and `--outputDir`.
- Add `snapshot.sizeOnly` to check matching files by size instead of content, so large or remote files are not read.

## [0.1.0]

- Verify published files against a committed JSON snapshot and fail the run on mismatch.
- Snapshot paths are relative to `outputDir` unless `snapshot.baseDir` is set.
- Write or update the snapshot with `snapshot.update = true` or `NXF_SNAPSHOT_UPDATE=true`.
