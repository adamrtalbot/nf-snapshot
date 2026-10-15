package nextflow.snapshot

import nextflow.Session
import nextflow.exception.AbortOperationException
import spock.lang.Specification

class SnapshotConfigTest extends Specification {

    def 'should use defaults'() {
        when:
        def config = new SnapshotConfig([:], [:])

        then:
        !config.enabled
        !config.update
        config.file == null
        config.baseDir == null
        config.ignore == []
        config.nameOnly == []
    }

    def 'should read options'() {
        when:
        def config = new SnapshotConfig([enabled: true, file: 'a.json', baseDir: '/out', update: true, ignore: ['x'], nameOnly: ['y']], [:])

        then:
        config.enabled
        config.update
        config.file == 'a.json'
        config.baseDir == '/out'
        config.ignore == ['x']
        config.nameOnly == ['y']
    }

    def 'should enable update from environment'() {
        expect:
        new SnapshotConfig([:], [NXF_SNAPSHOT_UPDATE: value]).update == expected

        where:
        value   | expected
        'true'  | true
        'false' | false
        null    | false
    }

    def 'should create observer only when enabled'() {
        given:
        def session = Mock(Session) { getConfig() >> [snapshot: opts] }

        expect:
        new SnapshotFactory().create(session).size() == count

        where:
        opts                               | count
        null                               | 0
        [:]                                | 0
        [enabled: false, file: 'a.json']   | 0
        [enabled: true, file: 'a.json']    | 1
    }

    def 'should require a file when enabled'() {
        given:
        def session = Mock(Session) { getConfig() >> [snapshot: [enabled: true]] }

        when:
        new SnapshotFactory().create(session)

        then:
        def e = thrown(AbortOperationException)
        e.message.contains('snapshot.file')
    }
}
