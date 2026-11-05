package nextflow.snapshot

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream

import groovy.json.JsonSlurper
import nextflow.NF
import nextflow.NextflowMeta
import nextflow.Session
import nextflow.SysEnv
import nextflow.exception.AbortRunException
import nextflow.script.ScriptBinding.ParamsMap
import nextflow.trace.event.FilePublishEvent
import spock.lang.Specification
import spock.lang.TempDir

import static nextflow.snapshot.SnapshotObserver.Difference.*

class SnapshotObserverTest extends Specification {

    @TempDir
    Path tmp

    // --- hash

    def 'should hash file content as md5'() {
        given:
        def file = tmp.resolve('a.txt'); file.text = 'hello'

        expect:
        SnapshotObserver.hash(file) == '5d41402abc4b2a76b9719d911017c592'
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

    // --- capture

    def 'should capture files under base, expanding dirs and applying globs'() {
        given:
        def dir = Files.createDirectories(tmp.resolve('out/report'))
        dir.resolve('all.txt').text = 'x'
        dir.resolve('run.log').text = 'y'
        def single = tmp.resolve('out/a.txt'); single.text = 'z'
        def skip = tmp.resolve('out/skip.csv'); skip.text = 'w'

        when:
        def result = SnapshotObserver.capture([dir, single, skip], tmp.resolve('out'), ['*.csv'], ['**/*.log'], [])

        then:
        result.keySet() as List == ['a.txt', 'report/all.txt', 'report/run.log']
        result['report/run.log'] == null
        result['a.txt'] == SnapshotObserver.hash(single)
    }

    def 'should record size for size-only files, with name-only taking precedence'() {
        given:
        def out = Files.createDirectories(tmp.resolve('out'))
        def bam = out.resolve('a.bam'); bam.text = 'hello'
        def log = out.resolve('a.log'); log.text = 'world'

        expect:
        SnapshotObserver.capture([bam, log], out, [], ['*.log'], ['*.bam', '*.log']) == ['a.bam': 'size:5', 'a.log': null]
    }

    def 'should follow symlinked directories'() {
        given:
        def real = Files.createDirectories(tmp.resolve('work/report'))
        real.resolve('all.txt').text = 'x'
        def link = Files.createSymbolicLink(Files.createDirectories(tmp.resolve('out')).resolve('report'), real)

        expect:
        SnapshotObserver.capture([link], tmp.resolve('out'), [], [], []).keySet() as List == ['report/all.txt']
    }

    def 'should ignore files outside base and fail when nothing is captured'() {
        given:
        def outside = tmp.resolve('a.txt'); outside.text = 'z'
        def inside = tmp.resolve('out/b.txt'); Files.createDirectories(inside.parent); inside.text = 'y'

        expect:
        SnapshotObserver.capture([outside, inside], tmp.resolve('out'), [], [], []).keySet() as List == ['b.txt']

        when:
        SnapshotObserver.capture([outside], tmp.resolve('out'), [], [], [])
        then:
        def e = thrown(AbortRunException)
        e.message.contains('no published files')
    }

    // --- diff

    def 'should diff snapshots'() {
        expect:
        SnapshotObserver.diff(
            [same: '1', changed: '1', gone: '1', nameOnly: null],
            [same: '1', changed: '2', added: '1', nameOnly: '9']
        ) == [added: UNEXPECTED, changed: CHANGED, gone: MISSING]
    }

    def 'should format differences as aligned lines'() {
        expect:
        SnapshotObserver.format([added: UNEXPECTED, changed: CHANGED, gone: MISSING]) ==
            '  unexpected: added\n  changed:    changed\n  missing:    gone'
    }

    def 'should report no difference for equal snapshots'() {
        expect:
        SnapshotObserver.diff([a: '1', b: null], [a: '1', b: null]) == [:]
    }

    // --- verify

    def 'should write snapshot when file is missing'() {
        given:
        def out = publish('a.txt': 'hello', 'b/c.txt': 'world')
        def snap = tmp.resolve('snap/test.json')

        when:
        runVerify(out, [file: snap.toString()])

        then:
        Files.exists(snap)
        new JsonSlurper().parse(snap.toFile()) == [files: ['a.txt': '5d41402abc4b2a76b9719d911017c592', 'b/c.txt': '7d793037a0760186574b0282f2f435e7']]
        snap.text == '''\
            {
              "files": {
                "a.txt": "5d41402abc4b2a76b9719d911017c592",
                "b/c.txt": "7d793037a0760186574b0282f2f435e7"
              }
            }
            '''.stripIndent()
    }

    def 'should pass when outputs match'() {
        given:
        def out = publish('a.txt': 'hello')
        def snap = tmp.resolve('test.json')
        runVerify(out, [file: snap.toString()])

        expect:
        runVerify(out, [file: snap.toString()]) == null
    }

    def 'should resolve base dir as baseDir, then outputDir, then params.outdir'() {
        given:
        def out = publish('a.txt': 'hello')
        def other = tmp.resolve('other').toString()
        def snap = tmp.resolve('test.json').toString()

        expect:
        runVerify(out, [file: snap, baseDir: null], true, [outdir: out.toString()]) == null
        runVerify(out, [file: snap, baseDir: null], true, [outdir: other], [outputDir: out.toString()]) == null
        runVerify(out, [file: snap], true, [outdir: other], [outputDir: other]) == null
    }

    def 'should fall back to results with omitted outdir in strict mode'() {
        given:
        def strict = NF.isStrictMode()
        NextflowMeta.instance.strictMode(true)
        SysEnv.push([NXF_FILE_ROOT: tmp.toString()])
        def out = Files.createDirectories(tmp.resolve('results'))
        out.resolve('a.txt').text = 'hello'
        def snap = tmp.resolve('test.json')

        expect:
        NF.isStrictMode()
        runVerify(out, [file: snap.toString(), baseDir: null]) == null
        new JsonSlurper().parse(snap.toFile()) == [files: ['a.txt': '5d41402abc4b2a76b9719d911017c592']]
        runVerify(out, [file: snap.toString(), baseDir: null], true, [:], [outputDir: out.toString()]) == null
        runVerify(out, [file: snap.toString()]) == null

        cleanup:
        SysEnv.pop()
        NextflowMeta.instance.strictMode(strict)
    }

    def 'should fail when no outputs are under the resolved base dir'() {
        given:
        def out = publish('a.txt': 'hello')
        def snap = tmp.resolve('test.json').toString()

        expect:
        runVerify(out, [file: snap, baseDir: null], true, [outdir: out.toString()], [outputDir: tmp.resolve('other').toString()])
            .message.contains('no published files')
        runVerify(out, [file: snap, baseDir: null]).message.contains('no published files')
    }

    def 'should fail when outputs differ'() {
        given:
        def snap = tmp.resolve('test.json')
        snap.text = '{"files": {"a.txt": "0"}}'

        when:
        def e = runVerify(publish('a.txt': 'hello'), [file: snap.toString()])

        then:
        e instanceof AbortRunException
        e.message.contains('1 difference')
    }

    def 'should warn on unexpected files unless failOnUnexpected is set'() {
        given:
        def out = publish('a.txt': 'hello', 'extra.txt': 'x')
        def snap = tmp.resolve('test.json')
        snap.text = '{"files": {"a.txt": "5d41402abc4b2a76b9719d911017c592"}}'

        expect:
        runVerify(out, [file: snap.toString()]) == null
        runVerify(out, [file: snap.toString(), failOnUnexpected: true]).message.contains('1 difference')
    }

    def 'should overwrite snapshot in update mode'() {
        given:
        def snap = tmp.resolve('test.json')
        snap.text = '{"files": {"a.txt": "0"}}'

        when:
        runVerify(publish('a.txt': 'hello'), [file: snap.toString(), update: true])

        then:
        new JsonSlurper().parse(snap.toFile()) == [files: ['a.txt': '5d41402abc4b2a76b9719d911017c592']]
    }

    def 'should skip check when run failed'() {
        given:
        def snap = tmp.resolve('test.json')

        when:
        runVerify(publish('a.txt': 'hello'), [file: snap.toString()], false)

        then:
        !Files.exists(snap)
    }

    def 'should resolve relative snapshot file against project dir'() {
        when:
        runVerify(publish('a.txt': 'hello'), [file: 'tests/test.json'])

        then:
        Files.exists(tmp.resolve('project/tests/test.json'))
    }

    def 'should fail on unreadable snapshot instead of passing'() {
        given:
        def snap = tmp.resolve('test.json')
        snap.text = content

        when:
        def e = runVerify(publish('a.txt': 'hello'), [file: snap.toString()])

        then:
        e instanceof AbortRunException
        e.message.contains(message)

        where:
        content          | message
        '{'              | 'check failed'
        '{"other": {}}'  | 'no `files` key'
    }

    // --- helpers

    private Path publish(Map<String,String> files) {
        def out = tmp.resolve('out')
        files.each { name, text ->
            def file = out.resolve(name)
            Files.createDirectories(file.parent)
            file.text = text
        }
        return out
    }

    /** Run the check and return the error passed to {@code session.abort}, or null. */
    private Throwable runVerify(Path out, Map opts, boolean success = true, Map params = [:], Map config = [:]) {
        Throwable aborted = null
        def session = Mock(Session) {
            isSuccess() >> success
            getBaseDir() >> tmp.resolve('project')
            getConfig() >> config
            getParams() >> new ParamsMap(params)
            abort(_) >> { aborted = it[0] }
        }
        def observer = new SnapshotObserver(new SnapshotConfig([enabled: true, baseDir: out.toString()] + opts, [:]), session)
        Files.walk(out).filter { Files.isRegularFile(it) }.each { observer.onFilePublish(new FilePublishEvent(null, it, null)) }
        observer.verify()
        return aborted
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
