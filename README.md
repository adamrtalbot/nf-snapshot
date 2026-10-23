# nf-snapshot

nf-snapshot checks the files a Nextflow run publishes against a snapshot file committed to the pipeline repository, the way nf-test snapshots work, but for one full-size run. If the outputs differ, the run exits with status 1.

Documentation: **[Development guide](https://adamrtalbot.github.io/nf-snapshot/dev/)** · **[Versioned documentation](https://adamrtalbot.github.io/nf-snapshot/latest/)**

## Usage

Declare the plugin at the top level of `nextflow.config`, then enable it in a profile for each test that has a snapshot. Nextflow 26.10.0 did not load the plugin when it was declared inside a profile. The plugin does nothing unless `snapshot.enabled` is set.

```groovy
plugins { id 'nf-snapshot@0.2.0' }

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

| Option     | Default     | Description                                                                           |
| ---------- | ----------- | ------------------------------------------------------------------------------------- |
| `enabled`  | `false`     | Enable the check.                                                                     |
| `file`     |             | Snapshot JSON file. Relative paths resolve against the project directory.             |
| `baseDir`  | `outputDir` | Directory that snapshot paths are relative to.                                        |
| `update`   | `false`     | Write the snapshot instead of verifying it. `NXF_SNAPSHOT_UPDATE=true` does the same. |
| `ignore`   | `[]`        | Glob patterns of relative paths to leave out of the snapshot.                         |
| `nameOnly` | `[]`        | Glob patterns of relative paths that are checked by name only.                        |
| `sizeOnly` | `[]`        | Glob patterns of relative paths that are checked by file size, without reading them.  |

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
- Every published file must be under `baseDir`. If one is not, or nothing was published, the run fails instead of passing against an empty snapshot.
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

CI runs lint, unit tests on Java 17 and 21, and the end-to-end tests on Nextflow 26.04 and the latest stable release. When CI passes on `main` and no tag exists for the version in `build.gradle`, the publish workflow releases the plugin to the Nextflow registry, then tags and creates a GitHub release using the matching `CHANGELOG.md` section. It needs the `NF_PLUGIN_PUBLISH_TOKEN` secret.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
