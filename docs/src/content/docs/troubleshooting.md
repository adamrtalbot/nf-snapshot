---
title: Troubleshooting
description: Diagnose output mismatches, invalid baselines, empty captures, and resumed runs.
---

## The plugin does not run

Declare the plugin at the top level of `nextflow.config`, then enable `snapshot.enabled` in the profile passed to `-profile`. Nextflow `26.10.0` did not load a plugin declared inside a profile.

Set `snapshot.enabled = true` to activate the check. If the pipeline itself fails, the plugin logs that it is skipping the snapshot check.

## Missing, changed, or unexpected files

```text
nf-snapshot: outputs differ from /path/to/tests/snapshots/test_full.json
  missing:    salmon/sample2/quant.sf
  changed:    multiqc/multiqc_data/multiqc_general_stats.txt
  unexpected: salmon/sample3/quant.sf
```

- **Missing:** check whether the current run published the file, whether `baseDir` changed, or whether a new ignore pattern excluded a baseline entry.
- **Changed:** inspect the output content. Check for timestamps, random seeds, software changes, or ordering differences in aggregated results.
- **Unexpected:** inspect newly published files and confirm that the run used the intended inputs and profile.

For intentional changes, [update and review the baseline](../usage/#accept-an-intentional-change). Investigate unexplained failures before accepting new outputs.

## Files change between identical runs

Sort collections before concatenating them. The arrival order of outputs from concurrent tasks is not deterministic, and `collect()` preserves that arrival order. The [example pipeline](../examples/) orders both collected paths and their concatenated contents.

For unavoidable variation, use `nameOnly` to keep an existence check or `ignore` to omit a file. The plugin decompresses gzip files before hashing, so header timestamps do not affect the check. Changes to decompressed content cause a mismatch.

## A missing baseline passes

If the snapshot is missing, the plugin creates it and passes the check. Commit the baseline before using a run to check for regressions. In CI, add an explicit file-existence check as shown in [Usage](../usage/#run-in-ci).

## An output is outside the base directory

```text
nf-snapshot: 1 published file(s) outside snapshot base dir ...
```

Set `snapshot.baseDir` to a common parent of all published targets. Its default is Nextflow's `outputDir`. Ignore patterns do not exempt targets from this check.

## No published files were found

The plugin fails if it captures no files, including when `ignore` excludes everything. Confirm that the run publishes regular files, that the selected profile executes the expected workflow, and that your ignore patterns leave something to check.

Only publication events from the current run count. Existing files in an output directory do not create a snapshot by themselves.

## The snapshot is invalid

Set `snapshot.file` when enabling the check. Provide readable JSON with a top-level `files` map, using the [documented format](../reference/#json-format).

Check the configured path and any manual edits. A missing `files` key, malformed JSON, or unreadable file fails the check. To regenerate a damaged baseline, use update mode and review the replacement before committing it.

## Removing name-only patterns does not catch changes

An existing `null` baseline entry continues to mean name-only checking even after you remove its pattern. Regenerate the snapshot to replace those entries with content hashes.

## A resumed run checks stale output

With `-resume`, `publishDir` does not overwrite existing published files for cached tasks. Use an empty output directory when resuming so nf-snapshot can inspect the files published for the current run.

## The exit status and run history disagree

A snapshot mismatch exits with status `1`, but the failure occurs during observer completion. It skips part of Nextflow's shutdown, and Nextflow run history records the run as successful. Use the command's exit status and nf-snapshot messages to assess the check. `-resume` still reuses cached tasks.
