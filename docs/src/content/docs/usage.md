---
title: Usage
description: Update baselines deliberately, handle unstable files, and check outputs in CI.
---

## Accept an intentional change

Run the same profile with update mode enabled:

```bash
NXF_SNAPSHOT_UPDATE=true nextflow run . -profile test_full
git diff -- tests/snapshots/test_full.json
```

Review both the pipeline change and snapshot diff, then commit the baseline with the change it describes. Update mode replaces the snapshot with the current run's captured files.

You can also set `snapshot.update = true` in configuration. Keep that setting out of your usual verification profile: it would accept every run's outputs. The environment variable enables update mode only when its value is exactly `true`; setting it to `false` does not override `snapshot.update = true`.

## Ignore outputs

Ignore files that should not be part of the check:

```groovy title="nextflow.config"
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

:::caution[Restore content checking explicitly]
A `null` hash in either the baseline or the current capture means name-only comparison. Removing a pattern from `nameOnly` does not restore content checking for entries already stored as `null`: regenerate and commit the baseline to store their hashes.
:::

## Choose the output root

Usually `params.outdir` already identifies the published output directory. If your pipeline uses another parameter, set `baseDir` explicitly:

```groovy title="nextflow.config"
snapshot {
    enabled = true
    file = 'tests/snapshots/test_full.json'
    baseDir = params.results_dir
}
```

All published targets must be under that directory, including targets matched by `ignore`. Files elsewhere cause a failure. See [path resolution](../reference/#paths) for the default precedence.

## Run in CI

Commit a baseline created from the same inputs and profile used by CI. After checking out the repository and setting up Nextflow and the pipeline's execution environment, run:

```bash
test -f tests/snapshots/test_full.json
nextflow run . -profile test_full
```

The first command makes a missing baseline fail explicitly; nf-snapshot itself would create one. Do not enable update mode in the CI verification job. A snapshot mismatch exits with status `1`.

## Resume carefully

With `-resume`, `publishDir` does not overwrite existing output files for cached tasks. Use an empty output directory so the check compares the current run's published files. Avoid reusing an output directory with stale files.

Remote published outputs, such as `s3://` paths, are downloaded for hashing. Allow for that transfer when choosing the run size and execution environment.
