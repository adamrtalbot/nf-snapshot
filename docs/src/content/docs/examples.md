---
title: Examples
description: A complete pipeline using process publication, workflow outputs, gzip files, and directory outputs.
---

## A small full-run snapshot

This example adapts the repository's validation pipeline. The workflow publishes the text files, their compressed copies and a report directory through an `output` block. It is valid under `NXF_SYNTAX_PARSER=v2`.

Save this as `main.nf` in a new directory. You need `gzip`, `cat`, and `date` in your local environment.

```groovy title="main.nf"
params {
    greeting: String = 'hello'
}

process SAY {
    input:
    val name

    output:
    path "${name}.txt"

    script:
    "echo '${params.greeting} ${name}' > ${name}.txt"
}

process ZIP {
    input:
    path txt

    output:
    path "${txt}.gz"

    script:
    "gzip -c ${txt} > ${txt}.gz"
}

process REPORT {
    input:
    path files

    output:
    path 'report'

    script:
    def ordered = files.collect { it.name }.sort().join(' ')
    """
    mkdir report
    date > report/timestamp.log
    cat ${ordered} > report/all.txt
    """
}

workflow {
    main:
    txt = SAY(channel.of('alice', 'bob'))
    gz = ZIP(txt)
    report = REPORT(txt.collect().map { files -> files.sort { it.name } })

    publish:
    say = txt
    gz = gz
    report = report
}

output {
    say {
        path 'say'
    }
    gz {
        path 'gz'
        index {
            path 'gz/index.csv'
        }
    }
    report {
        path '.'
    }
}
```

Concurrent tasks can finish in any order. Sort the collected paths and concatenate the files in that order to keep `report/all.txt` deterministic. Its contents remain part of the snapshot check.

Configure the plugin in the same directory:

```groovy title="nextflow.config"
plugins { id 'nf-snapshot@0.2.0' }

profiles {
    test_full {
        snapshot {
            enabled = true
            file = 'tests/snapshots/test_full.json'
            ignore = ['gz/index.csv'] // [!annotate] The publication index is not part of this baseline.
            nameOnly = ['report/timestamp.log'] // [!annotate] Keep an existence check for the changing timestamp.
        }
    }
}
```

If you are using an unreleased plugin version, run `make install` from the nf-snapshot checkout first. See [Getting started](../getting-started/) for installation details.

## Create and verify

Run from the example directory:

```bash
NXF_SNAPSHOT_UPDATE=true nextflow run . -profile test_full
nextflow run . -profile test_full
```

The snapshot contains six files:

```text
say/alice.txt
say/bob.txt
gz/alice.txt.gz
gz/bob.txt.gz
report/all.txt
report/timestamp.log
```

The timestamp entry is `null`; the other entries have content hashes. The gzip hashes reflect decompressed text, so a different gzip header timestamp does not change the result. The plugin captures the two files in the report directory.

## Detect a change

Change the greeting for one run:

```bash
nextflow run . -profile test_full --greeting goodbye
```

The run exits with status `1` and reports five changed files. The timestamp still passes its name-only check:

```text
  changed:    gz/alice.txt.gz
  changed:    gz/bob.txt.gz
  changed:    report/all.txt
  changed:    say/alice.txt
  changed:    say/bob.txt
```

To accept that greeting as the new expected result:

```bash
NXF_SNAPSHOT_UPDATE=true nextflow run . -profile test_full --greeting goodbye
git diff -- tests/snapshots/test_full.json
nextflow run . -profile test_full --greeting goodbye
```

Review and commit the new baseline with the corresponding pipeline change. These commands rerun tasks without `-resume`; for cached tasks, read the [resume guidance](../usage/#resume-carefully).
