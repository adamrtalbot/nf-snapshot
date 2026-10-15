package nextflow.snapshot

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream

import nextflow.Session
import nextflow.exception.AbortRunException
import spock.lang.Specification
import spock.lang.TempDir

class SnapshotObserverTest extends Specification {

    @TempDir
    Path tmp

    def 'should create observer only when enabled'() {
        given:
        def session = Mock(Session) { getConfig() >> [snapshot: opts] }

        expect:
        new SnapshotFactory().create(session).size() == count

        where:
        opts                                 | count
        [:]                                  | 0
        [enabled: true, file: 'snap.json']   | 1
    }

    def 'should hash gz content ignoring header timestamp'() {
        given:
        def a = gzip(tmp.resolve('a.txt.gz'), 'hello', 1000)
        def b = gzip(tmp.resolve('b.txt.gz'), 'hello', 2000)
        def plain = tmp.resolve('c.txt'); plain.text = 'hello'

        expect:
        Files.readAllBytes(a) != Files.readAllBytes(b)
        SnapshotObserver.hash(a) == SnapshotObserver.hash(b)
        SnapshotObserver.hash(a) == SnapshotObserver.hash(plain)
    }

    def 'should capture files under base, expanding dirs and applying globs'() {
        given:
        def dir = Files.createDirectories(tmp.resolve('out/report'))
        dir.resolve('all.txt').text = 'x'
        dir.resolve('run.log').text = 'y'
        def single = tmp.resolve('out/a.txt'); single.text = 'z'
        def skip = tmp.resolve('out/skip.csv'); skip.text = 'w'

        when:
        def result = SnapshotObserver.capture([dir, single, skip], tmp.resolve('out'), ['*.csv'], ['**/*.log'])

        then:
        result.keySet() as List == ['a.txt', 'report/all.txt', 'report/run.log']
        result['report/run.log'] == null
        result['a.txt'] == SnapshotObserver.hash(single)
    }

    def 'should fail on files outside base or nothing captured'() {
        given:
        def file = tmp.resolve('a.txt'); file.text = 'z'

        when:
        SnapshotObserver.capture([file], tmp.resolve('out'), [], [])
        then:
        thrown(AbortRunException)

        when:
        SnapshotObserver.capture([], tmp, [], [])
        then:
        thrown(AbortRunException)
    }

    def 'should diff snapshots'() {
        expect:
        SnapshotObserver.diff(
            [same: '1', changed: '1', gone: '1', nameOnly: null],
            [same: '1', changed: '2', added: '1', nameOnly: '9']
        ) == ['  unexpected: added', '  changed:    changed', '  missing:    gone']
    }

    private static Path gzip(Path path, String text, long mtime) {
        def out = new GZIPOutputStream(Files.newOutputStream(path))
        out.withCloseable { it.write(text.bytes) }
        // gzip header bytes 4-7 hold the mtime
        def bytes = Files.readAllBytes(path)
        bytes[4] = (byte) mtime; bytes[5] = (byte) (mtime >> 8)
        Files.write(path, bytes)
        return path
    }
}
