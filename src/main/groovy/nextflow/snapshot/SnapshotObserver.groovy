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
        // Same order as the update command; 'results' resolves as Session.outputDir does
        final base = FileHelper.toCanonicalPath(config.baseDir ?: session.config.get('outputDir') ?: session.params.get('outdir') ?: 'results')
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
        if( !problems ) {
            log.info "nf-snapshot: ${actual.size()} files match ${file}"
            return
        }
        log.error "nf-snapshot: outputs differ from ${file}\n${problems.join('\n')}\nRe-run with NXF_SNAPSHOT_UPDATE=true to accept the changes"
        throw new AbortRunException("nf-snapshot: ${problems.size()} difference(s) from snapshot")
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

    /** A null hash on either side means name only. */
    static List<String> diff(Map<String,String> expected, Map<String,String> actual) {
        final result = new ArrayList<String>()
        for( String key : new TreeSet<String>(expected.keySet() + actual.keySet()) ) {
            if( !actual.containsKey(key) )
                result.add("  missing:    ${key}".toString())
            else if( !expected.containsKey(key) )
                result.add("  unexpected: ${key}".toString())
            else if( expected[key] != null && actual[key] != null && expected[key] != actual[key] )
                result.add("  changed:    ${key}".toString())
        }
        return result
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
