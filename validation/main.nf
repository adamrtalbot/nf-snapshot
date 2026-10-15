params.outdir = 'results'
params.greeting = 'hello'

process SAY {
    publishDir "${params.outdir}/say", mode: 'copy'

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
    "mkdir report && date > report/timestamp.log && cat *.txt > report/all.txt"
}

workflow {
    main:
    txt = SAY(channel.of('alice', 'bob'))
    gz = ZIP(txt)
    report = REPORT(txt.collect())

    publish:
    gz = gz
    report = report
}

output {
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
