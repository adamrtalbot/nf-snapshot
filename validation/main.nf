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
    "mkdir report && date > report/timestamp.log && cat *.txt > report/all.txt"
}

workflow {
    main:
    txt = SAY(channel.of('alice', 'bob'))
    gz = ZIP(txt)
    report = REPORT(txt.collect())

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
