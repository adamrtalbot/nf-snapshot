# nf-snapshot

Use nf-snapshot to compare a full Nextflow run's published files against a JSON snapshot you commit to the pipeline repository. If the outputs differ, the run exits with status 1.

**[Documentation](https://adamrtalbot.github.io/nf-snapshot/latest/)** - **[Development guide](https://adamrtalbot.github.io/nf-snapshot/dev/)**

## Usage

Declare the plugin at the top level of `nextflow.config`, then enable it in a profile for each test that has a snapshot. Nextflow 26.10.0 did not load nf-snapshot from a profile. Set `snapshot.enabled = true` to enable the check.

```groovy
plugins { id 'nf-snapshot@0.2.1' }

profiles {
    test_full {
        snapshot {
            enabled  = true
            file     = 'tests/snapshots/test_full.json'
            ignore   = ['pipeline_info/**']
            nameOnly = ['**/*.html', '**/*.log']
        }
    }
}
```

Create the snapshot, then commit it:

```bash
NXF_SNAPSHOT_UPDATE=true nextflow run . -profile test_full
```

Later runs verify against it:

```bash
nextflow run . -profile test_full
```

If the outputs differ, the run prints a diff and fails:

```
nf-snapshot: outputs differ from /path/to/tests/snapshots/test_full.json
  missing:    salmon/sample2/quant.sf
  changed:    multiqc/multiqc_data/multiqc_general_stats.txt
  unexpected: salmon/sample3/quant.sf
```

If the snapshot file does not exist yet, the plugin writes it and does not fail. If the run itself fails, the plugin skips the check.

To accept existing outputs without re-running, run from the project directory:

```bash
nextflow plugin nf-snapshot:update --profile test_full --outputDir results
```

Use a directory containing only that run's outputs: the command captures every file, subject to snapshot patterns. See the [command reference](https://adamrtalbot.github.io/nf-snapshot/dev/reference/#update-command) for configuration and path resolution.

## Options

| Option     | Default     | Description                                                                            |
| ---------- | ----------- | -------------------------------------------------------------------------------------- |
| `enabled`  | `false`     | Enable the check.                                                                      |
| `file`     |             | Snapshot JSON file. Relative paths resolve against the project directory.              |
| `baseDir`  | `outputDir` | Directory that snapshot paths are relative to.                                         |
| `update`   | `false`     | Write the snapshot instead of verifying it. `NXF_SNAPSHOT_UPDATE=true` does the same.  |
| `ignore`   | `[]`        | Glob patterns of relative paths to leave out of the snapshot.                          |
| `nameOnly` | `[]`        | Glob patterns of relative paths to check by filename, without a content hash.          |
| `sizeOnly` | `[]`        | Glob patterns of relative paths to check by file size, without reading their contents. |

Globs use Java `PathMatcher` syntax, so `**/*.html` does not match `report.html` at the top level. Use `{*.html,**/*.html}` to match both.

## Snapshot format

```json
{
  "files": {
    "multiqc/multiqc_report.html": null,
    "salmon/sample1/quant.sf": "9e107d9d372bb6826bd81d3542a419d6",
    "star/sample1.bam": "size:104857600"
  }
}
```

Each entry holds the MD5 of the file content, `size:<bytes>` for size-only files, or `null` for name-only files. `nameOnly` takes precedence over `sizeOnly`. The plugin hashes `.gz` files after decompressing them, because the gzip header contains a timestamp. It expands published directories into the files they contain.

## Notes

- The plugin captures only the files the current run publishes, through the workflow `output` block or `publishDir`. It ignores other files in the output directory.
- Every published file must be under `baseDir`. If a file is outside `baseDir`, or the run publishes no files, the check fails.
- With `-resume`, `publishDir` does not overwrite files that already exist for cached tasks. Use an empty output directory, or the check will compare files left over from earlier runs.
- Outputs must be deterministic. For example, files built from `collect()` can change order between runs, so sort them first or mark them `nameOnly`.
- The plugin writes JSON with 2-space indentation, so Prettier leaves the file unchanged.
- The plugin downloads remote outputs (for example `s3://`) to hash them. Mark large remote files `sizeOnly` to check their size from metadata instead.
- A failed check skips part of Nextflow's shutdown, and the run history records the run as successful. `-resume` still reuses cached tasks.

## Development

```bash
make test       # unit tests
make e2e        # install the plugin and run validation/run-tests.sh
pre-commit run --all-files
```

CI runs lint, unit tests on Java 17 and 21, and end-to-end tests on Nextflow 26.04 and the latest stable release. After CI passes on `main`, the publish workflow checks for a tag matching the version in `build.gradle`. If none exists, it publishes to the Nextflow registry, tags the release, and creates a GitHub release from the matching `CHANGELOG.md` section. It needs the `NF_PLUGIN_PUBLISH_TOKEN` secret.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
