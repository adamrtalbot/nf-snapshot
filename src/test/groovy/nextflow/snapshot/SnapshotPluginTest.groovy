package nextflow.snapshot

import nextflow.exception.AbortOperationException
import spock.lang.Specification

class SnapshotPluginTest extends Specification {

    def 'should accept known options'() {
        when:
        SnapshotPlugin.check('update', args)

        then:
        noExceptionThrown()

        where:
        args << [[], ['--profile', 'test'], ['--outputDir', 'out', '--profile', 'test']]
    }

    def 'should reject unknown commands and arguments'() {
        when:
        SnapshotPlugin.check(cmd, args)

        then:
        def e = thrown(AbortOperationException)
        e.message.contains(message)

        where:
        cmd      | args                                  | message
        'bogus'  | []                                    | 'unknown command `bogus`'
        'update' | ['--proflie', 'test']                 | 'unknown argument `--proflie`'
        'update' | ['--dry-run', '']                     | 'unknown argument `--dry-run`'
        'update' | ['--profile']                         | '`--profile` needs a value'
        'update' | ['--profile', '--outputDir', 'out']   | '`--profile` needs a value'
        'update' | ['--outputDir', '']                   | '`--outputDir` needs a value'
    }
}
