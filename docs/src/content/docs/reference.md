---
title: Reference
description: Snapshot configuration, path resolution, glob matching, and the JSON file format.
---

## Configuration

Declare `plugins { id 'nf-snapshot@0.2.0' }` at the top level of `nextflow.config`. Configure these seven options inside `snapshot { ... }`, usually within a test profile.

| Option     | Default     | Meaning                                                                                  |
| ---------- | ----------- | ---------------------------------------------------------------------------------------- |
| `enabled`  | `false`     | Enable the check. Loading the plugin alone does nothing.                                 |
| `file`     | None        | Required when enabled. Path to the snapshot JSON file.                                   |
| `baseDir`  | `outputDir` | Output root used to form relative snapshot paths.                                        |
| `update`   | `false`     | Replace the baseline instead of checking it. Also enabled by `NXF_SNAPSHOT_UPDATE=true`. |
| `ignore`   | `[]`        | List of relative-path glob patterns to exclude.                                          |
| `nameOnly` | `[]`        | List of relative-path glob patterns to compare by filename only.                         |
| `sizeOnly` | `[]`        | List of relative-path glob patterns to compare by file size only.                        |

### Paths

A relative `snapshot.file` resolves against the pipeline project directory, not the shell's working directory. Absolute paths are used directly.

The output root is `snapshot.baseDir` when set, otherwise Nextflow's `outputDir` (set with `-output-dir`, default `results`).

The selected root is resolved using Nextflow's canonical path handling. Every published target must be under this root. Set an explicit absolute path or storage URI when you need to avoid ambiguity.

### Update mode

Update mode is enabled if **either** `snapshot.update` is true **or** `NXF_SNAPSHOT_UPDATE` is exactly the string `true`. Other environment values do not enable it and cannot disable a true configuration value.

If the snapshot is missing, the plugin writes it regardless of update mode. A failed pipeline skips capture and comparison.

### Glob patterns

Patterns use Java `PathMatcher` glob syntax against each file's relative path. They are not regular expressions. On Unix-like systems, examples include:

| Pattern              | Matches                                          |
| -------------------- | ------------------------------------------------ |
| `report.html`        | That file at the output root.                    |
| `*.html`             | HTML files at the output root.                   |
| `**/*.html`          | Nested HTML files; not root-level `report.html`. |
| `{*.html,**/*.html}` | Both root-level and nested HTML files.           |
| `pipeline_info/**`   | Files below `pipeline_info`.                     |

`ignore` is applied first, then `nameOnly`, then `sizeOnly`. If every captured file is ignored, the run fails with an empty-capture error. Output targets outside `baseDir` fail before ignore patterns are considered.

## Update command

```bash
nextflow plugin nf-snapshot:update [--profile <name>] [--outputDir <dir>]
```

Rewrites the snapshot from existing outputs without running the pipeline, applying `ignore`, `nameOnly` and `sizeOnly`. It reads the current directory's `nextflow.config`; use `--profile` when the snapshot settings are inside a profile. `-c` files and CLI params are not read. `snapshot.file` is required, and relative snapshot paths resolve against the current directory. `snapshot.enabled` is ignored.

The output root is `--outputDir`, falling back to `snapshot.baseDir`, the `outputDir` config setting, then `results`. Relative roots resolve against `NXF_FILE_ROOT` when set, otherwise the current directory. Every file under the root is captured, subject to the snapshot patterns.

## Captured files

The plugin records publication events from both workflow `output` blocks and process `publishDir`. It checks only what the current run publishes; unrelated files already in the output directory are not added.

Published directories are expanded recursively into regular files, following symbolic links. Files ending in `.gz` are decompressed before hashing so gzip header timestamps do not cause differences. Other files are hashed as stored. Remote files are downloaded to calculate their hashes. Size-only files are not read; their size comes from file metadata, and for `.gz` files it is the compressed size.

## JSON format

```json title="tests/snapshots/test_full.json"
{
  "files": {
    "multiqc/multiqc_report.html": null,
    "salmon/sample1/quant.sf": "9e107d9d372bb6826bd81d3542a419d6",
    "star/sample1.bam": "size:104857600"
  }
}
```

`files` maps relative filenames to lowercase MD5 hashes of their contents. Size-only entries contain `size:<bytes>`. Name-only entries contain `null`. Entries are written in sorted path order with two-space indentation, compatible with Prettier.

Comparison reports:

| Result       | Meaning                                          |
| ------------ | ------------------------------------------------ |
| `missing`    | A baseline path was not captured in this run.    |
| `unexpected` | A captured path is absent from the baseline.     |
| `changed`    | Both sides have a hash, and those hashes differ. |

A `null` on either side suppresses content comparison for that path. To restore content checking after removing a `nameOnly` pattern, regenerate the baseline.

A mismatch exits with status `1`. See [Troubleshooting](../troubleshooting/) for the current shutdown and run-history caveat.
