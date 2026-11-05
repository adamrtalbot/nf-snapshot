package nextflow.snapshot

import java.nio.file.FileSystems
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.Paths
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.exception.AbortRunException
import nextflow.file.FileHelper
import nextflow.trace.TraceObserverV2
import nextflow.trace.event.FilePublishEvent

/**
 * Collects published files and, on completion, writes or verifies their snapshot entries.
 */
@Slf4j
@CompileStatic
class SnapshotObserver implements TraceObserverV2 {

    static enum Difference { MISSING, UNEXPECTED, CHANGED }

    private final SnapshotConfig config
    private final Session session
    private final Set<Path> published = ConcurrentHashMap.newKeySet()

    SnapshotObserver(SnapshotConfig config, Session session) {
        this.config = config
        this.session = session
    }

    // Nextflow calls this from publish pool threads.
    @Override
    void onFilePublish(FilePublishEvent event) {
        published.add(event.target.toAbsolutePath().normalize())
    }

    /**
     * Run as a session shutdown hook (see {@link SnapshotFactory}): publishing has finished but
     * `workflow.success`, which onComplete handlers and Seqera Platform read, is not yet set.
     * Nextflow swallows exceptions from shutdown hooks, so mark the run failed with {@code session.abort}.
     */
    void verify() {
        try {
            check()
        }
        catch( AbortRunException e ) {
            session.abort(e)
        }
        catch( Exception e ) {
            final message = "nf-snapshot: check failed - ${e.message ?: e.class.name}".toString()
            log.error message
            session.abort(new AbortRunException(message))
        }
    }

    private void check() {
        if( !session.success ) {
            log.warn "nf-snapshot: run did not succeed, skipping snapshot check"
            return
        }
        final file = snapshotFile()
        // Strict-mode ParamsMap throws on undeclared params
        final outdir = session.params.containsKey('outdir') ? session.params.get('outdir') : null
        final base = outputRoot(config.baseDir, session.config.get('outputDir'), outdir)
        // ponytail: hashes serially, parallelise if full-size runs are slow
        final actual = capture(published, base, config.ignore, config.nameOnly, config.sizeOnly)

        if( config.update || !Files.exists(file) ) {
            write(file, actual)
            return
        }

        final expected = (new JsonSlurper().parseText(Files.readString(file)) as Map).files as Map<String,String>
        if( expected == null )
            fail("nf-snapshot: ${file} has no `files` key")
        final problems = diff(expected, actual)
        final unexpected = problems.findAll { k, v -> v == Difference.UNEXPECTED }
        final failures = config.failOnUnexpected ? problems : problems.findAll { k, v -> v != Difference.UNEXPECTED }
        if( unexpected && !config.failOnUnexpected )
            log.warn "nf-snapshot: files not in ${file}\n${format(unexpected)}\nSet `snapshot.failOnUnexpected = true` to fail on these"
        if( failures ) {
            log.error "nf-snapshot: outputs differ from ${file}\n${format(failures)}\nRe-run with NXF_SNAPSHOT_UPDATE=true to accept the changes"
            throw new AbortRunException("nf-snapshot: ${failures.size()} difference(s) from snapshot")
        }
        log.info "nf-snapshot: ${expected.size()} files match ${file}"
    }

    /**
     * Root that snapshot paths are relative to: {@code snapshot.baseDir}, then {@code outputDir}, then
     * {@code params.outdir}, then {@code results}. Resolved as Session.outputDir is, so NXF_FILE_ROOT applies.
     * The update command uses the same order.
     */
    static Path outputRoot(String baseDir, Object outputDir, Object outdir) {
        return FileHelper.toCanonicalPath(baseDir ?: outputDir ?: outdir ?: 'results')
    }

    private Path snapshotFile() {
        final path = FileHelper.asPath(config.file)
        return path.isAbsolute() ? path : session.baseDir.resolve(config.file)
    }

    /**
     * Map each published file under {@code base} to its MD5, {@code size:<bytes>} for size-only files,
     * or null for name-only files. Expand published directories into their files.
     */
    static Map<String,String> capture(Collection<Path> targets, Path base, List<String> ignore, List<String> nameOnly, List<String> sizeOnly) {
        final ignoreMatchers = matchers(ignore)
        final nameOnlyMatchers = matchers(nameOnly)
        final sizeOnlyMatchers = matchers(sizeOnly)
        final result = new TreeMap<String,String>()
        // Outputs outside the base dir are not part of the snapshot
        for( Path target : targets.findAll { Path it -> it.startsWith(base) } ) {
            Files.walk(target, FileVisitOption.FOLLOW_LINKS).withCloseable { stream ->
                stream.filter { Path it -> Files.isRegularFile(it) }.forEach { Path it ->
                    final rel = base.relativize(it).toString()
                    if( matches(ignoreMatchers, rel) )
                        return
                    if( matches(nameOnlyMatchers, rel) )
                        result.put(rel, null)
                    else if( matches(sizeOnlyMatchers, rel) )
                        result.put(rel, "size:${Files.size(it)}".toString())
                    else
                        result.put(rel, hash(it))
                }
            }
        }
        if( !result )
            fail("nf-snapshot: no published files found under ${base}. Set `snapshot.baseDir` if outputs are elsewhere")
        return result
    }

    /** Hash file contents with MD5; decompress .gz files to exclude the gzip header timestamp. */
    static String hash(Path file) {
        final md = MessageDigest.getInstance('MD5')
        InputStream input = Files.newInputStream(file)
        if( file.fileName.toString().endsWith('.gz') )
            input = new GZIPInputStream(input)
        input.withCloseable { it.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(), md)) }
        return md.digest().encodeHex().toString()
    }

    /** Map each differing path to how it differs, sorted by path. A null hash on either side means name only. */
    static Map<String,Difference> diff(Map<String,String> expected, Map<String,String> actual) {
        final result = new TreeMap<String,Difference>()
        for( String key : expected.keySet() + actual.keySet() ) {
            if( !actual.containsKey(key) )
                result.put(key, Difference.MISSING)
            else if( !expected.containsKey(key) )
                result.put(key, Difference.UNEXPECTED)
            else if( expected[key] != null && actual[key] != null && expected[key] != actual[key] )
                result.put(key, Difference.CHANGED)
        }
        return result
    }

    static String format(Map<String,Difference> diffs) {
        return diffs.collect { path, kind -> "  ${(kind.name().toLowerCase() + ':').padRight(12)}${path}" }.join('\n')
    }

    static void write(Path file, Map<String,String> files) {
        Files.createDirectories(file.parent)
        Files.writeString(file, toJson(files))
        log.info "nf-snapshot: wrote ${files.size()} files to ${file}"
    }

    /** 2-space indent, matching Prettier, so formatting the file does not change it. */
    static String toJson(Map<String,String> files) {
        final entries = files.collect { k, v -> "    ${JsonOutput.toJson(k)}: ${JsonOutput.toJson(v)}".toString() }
        return '{\n  "files": {\n' + entries.join(',\n') + '\n  }\n}\n'
    }

    private static List<PathMatcher> matchers(List<String> globs) {
        return globs.collect { String it -> FileSystems.default.getPathMatcher("glob:${it}") }
    }

    private static boolean matches(List<PathMatcher> matchers, String rel) {
        final path = Paths.get(rel)
        return matchers.any { PathMatcher it -> it.matches(path) }
    }

    private static void fail(String message) {
        log.error message
        throw new AbortRunException(message)
    }
}
