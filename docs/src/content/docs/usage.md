---
title: Usage
description: Review baseline updates, handle unstable files, and check outputs in CI.
---

## Accept an intentional change

Run the same profile with update mode enabled:

```bash
NXF_SNAPSHOT_UPDATE=true nextflow run . -profile test_full
git diff -- tests/snapshots/test_full.json
```

Review both the pipeline change and snapshot diff, then commit the baseline with the change it describes. Update mode replaces the snapshot with the current run's captured files.

You can set `snapshot.update = true` in configuration. Keep that setting out of your usual verification profile: it would accept every run's outputs. Set the environment variable to `true` to enable update mode. Other values do not enable it or override `snapshot.update = true`.

## Update without re-running

Rewrite the snapshot from existing outputs:

```bash
nextflow plugin nf-snapshot:update --profile test_full --outputDir results
git diff -- tests/snapshots/test_full.json
```

Run from the project directory. The command reads `nextflow.config` and the selected profile, ignoring `-c` files and CLI params. Use `--outputDir` to select the run's output directory.

It captures every file there, subject to snapshot patterns. Use a directory containing only that run's outputs. See the [command reference](../reference/#update-command) for options and defaults.

## Ignore outputs

Ignore files that should not be part of the check:

```groovy title="nextflow.config" focus={6}
profiles {
    test_full {
        snapshot {
            enabled = true
            file = 'tests/snapshots/test_full.json'
            ignore = ['pipeline_info/**', 'multiqc/multiqc_data/**']
        }
    }
}
```

Patterns apply to paths relative to `snapshot.baseDir`. Ignored files have no baseline entry. Regenerate the snapshot after changing these patterns so old entries do not appear as missing.

## Check filenames without contents

For files with timestamps or other unavoidable variation, retain an existence check:

```groovy title="nextflow.config"
profiles {
    test_full {
        snapshot {
            enabled = true
            file = 'tests/snapshots/test_full.json'
            nameOnly = ['{*.html,**/*.html}', '{*.log,**/*.log}']
        }
    }
}
```

Prefer deterministic outputs where possible. For example, sort items collected from concurrent tasks before concatenating their contents. Checking only filenames cannot detect a content regression.

:::caution[Regenerate hashes to restore content checking]
A `null` hash in either the baseline or the current capture means name-only comparison. Removing a pattern from `nameOnly` does not restore content checking for entries already stored as `null`: regenerate and commit the baseline to store their hashes.
:::

## Check large files by size

To hash a file, the plugin reads its contents, downloading remote outputs such as `s3://` files to the head node. For large files, compare the size from file metadata:

```groovy title="nextflow.config"
snapshot {
    sizeOnly = ['**/*.bam', '**/*.cram']
}
```

A size check misses content changes that keep the same number of bytes. Changing a file between `sizeOnly` and a content hash reports it as `changed` until you regenerate the baseline.

## Choose the output root

Snapshot paths are relative to Nextflow's `outputDir`, which is where the workflow `output` block publishes. If your pipeline publishes elsewhere, for example with `publishDir` and `params.outdir`, set `baseDir`:

```groovy title="nextflow.config"
snapshot {
    enabled = true
    file = 'tests/snapshots/test_full.json'
    baseDir = params.outdir
}
```

All published targets must be under that directory, including targets matched by `ignore`. Files elsewhere cause a failure. See [path resolution](../reference/#paths) for details.

## Run in CI

Create and commit a baseline using the same inputs and profile as CI. After checking out the repository and setting up Nextflow and the pipeline's execution environment, run:

```bash
test -f tests/snapshots/test_full.json
nextflow run . -profile test_full
```

The first command fails if the baseline is missing; nf-snapshot would create one. Do not enable update mode in the CI verification job. A snapshot mismatch exits with status `1`.

## Resume carefully

With `-resume`, `publishDir` does not overwrite existing output files for cached tasks. Use an empty output directory so the check compares the current run's published files. Avoid reusing an output directory with stale files.

Remote published outputs, such as `s3://` paths, are downloaded for hashing. Allow for that transfer when choosing the run size and execution environment.
