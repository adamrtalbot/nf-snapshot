---
title: Reference
description: Snapshot configuration, path resolution, glob matching, and the JSON file format.
---

## Configuration

Declare `plugins { id 'nf-snapshot@0.2.0' }` at the top level of `nextflow.config`. Configure these seven options inside `snapshot { ... }` in a test profile.

| Option     | Default     | Meaning                                                                                          |
| ---------- | ----------- | ------------------------------------------------------------------------------------------------ |
| `enabled`  | `false`     | Enable the check. Loading the plugin alone does nothing.                                         |
| `file`     | None        | Required when enabled. Path to the snapshot JSON file.                                           |
| `baseDir`  | `outputDir` | Output root used to form relative snapshot paths.                                                |
| `update`   | `false`     | Replace the baseline instead of checking it. Use `NXF_SNAPSHOT_UPDATE=true` for the same effect. |
| `ignore`   | `[]`        | List of relative-path glob patterns to exclude.                                                  |
| `nameOnly` | `[]`        | List of relative-path glob patterns to compare by filename only.                                 |
| `sizeOnly` | `[]`        | List of relative-path glob patterns to compare by file size only.                                |

### Paths

A relative `snapshot.file` resolves against the pipeline project directory, not the shell's working directory. You can use an absolute path to select a fixed location.

The output root is `snapshot.baseDir` when set, otherwise Nextflow's `outputDir` (set with `-output-dir`, default `results`).

The plugin resolves the selected root using Nextflow's canonical path handling. Every published target must be under this root. Set an explicit absolute path or storage URI when you need to avoid ambiguity.

### Update mode

To enable update mode, set `snapshot.update` to true or `NXF_SNAPSHOT_UPDATE` to the string `true`. Other environment values do not enable it and cannot disable a true configuration value.

If the snapshot is missing, the plugin writes it regardless of update mode. If the pipeline fails, the plugin skips capture and comparison.

### Glob patterns

Patterns use Java `PathMatcher` glob syntax against each file's relative path. They are not regular expressions. On Unix-like systems, examples include:

| Pattern              | Matches                                          |
| -------------------- | ------------------------------------------------ |
| `report.html`        | That file at the output root.                    |
| `*.html`             | HTML files at the output root.                   |
| `**/*.html`          | Nested HTML files; not root-level `report.html`. |
| `{*.html,**/*.html}` | Both root-level and nested HTML files.           |
| `pipeline_info/**`   | Files below `pipeline_info`.                     |

The plugin applies `ignore`, then `nameOnly`, then `sizeOnly`. If `ignore` excludes all captured files, the run fails with an empty-capture error. The plugin rejects output targets outside `baseDir` before applying ignore patterns.

## Update command

```bash
nextflow plugin nf-snapshot:update [--profile <name>] [--outputDir <dir>]
```

Use this command to rewrite the snapshot from existing outputs without running the pipeline. It applies `ignore`, `nameOnly` and `sizeOnly` and reads the current directory's `nextflow.config`; use `--profile` for snapshot settings inside a profile. The command ignores `-c` files, CLI params and `snapshot.enabled`. Set `snapshot.file`; relative snapshot paths resolve against the current directory.

The output root is `--outputDir`, falling back to `snapshot.baseDir`, the `outputDir` config setting, then `results`. Relative roots resolve against `NXF_FILE_ROOT` when set, otherwise the current directory. The command captures each file under the root, subject to the snapshot patterns.

## Captured files

The plugin records publication events from both workflow `output` blocks and process `publishDir`. It checks files the current run publishes and excludes unrelated files in the output directory.

For each published directory, the plugin captures the regular files in that directory and its subdirectories, following symbolic links. It decompresses `.gz` files before hashing so gzip header timestamps do not cause differences. It hashes other files as stored and downloads remote files to hash them. For `sizeOnly` files, the plugin reads the size from metadata without reading the contents, using the compressed size for `.gz` files.

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

`files` maps relative filenames to lowercase MD5 hashes of their contents. Size-only entries contain `size:<bytes>`. Name-only entries contain `null`. The plugin writes entries in sorted path order with two-space indentation, compatible with Prettier.

Comparison reports:

| Result       | Meaning                                          |
| ------------ | ------------------------------------------------ |
| `missing`    | The current capture lacks a baseline path.       |
| `unexpected` | A captured path is absent from the baseline.     |
| `changed`    | Both sides have a hash, and those hashes differ. |

A `null` on either side suppresses content comparison for that path. To restore content checking after removing a `nameOnly` pattern, regenerate the baseline.

A mismatch exits with status `1`. See [Troubleshooting](../troubleshooting/) for the current shutdown and run-history caveat.
