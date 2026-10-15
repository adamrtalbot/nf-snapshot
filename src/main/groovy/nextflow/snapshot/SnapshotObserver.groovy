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
 * Collects every published file and, on completion, writes it to
 * or verifies it against the snapshot file.
 */
@Slf4j
@CompileStatic
class SnapshotObserver implements TraceObserverV2 {

    private final SnapshotConfig config
    private final Set<Path> published = ConcurrentHashMap.newKeySet()
    private Session session

    SnapshotObserver(SnapshotConfig config) {
        this.config = config
    }

    @Override
    void onFlowCreate(Session session) {
        this.session = session
    }

    // called from publish pool threads
    @Override
    void onFilePublish(FilePublishEvent event) {
        published.add(event.target.toAbsolutePath().normalize())
    }

    @Override
    void onFlowComplete() {
        // Nextflow swallows any observer exception except AbortRunException, which would pass the check silently
        try {
            check()
        }
        catch( AbortRunException e ) {
            throw e
        }
        catch( Exception e ) {
            fail("nf-snapshot: check failed - ${e.message ?: e.class.name}")
        }
    }

    private void check() {
        if( !session.success ) {
            log.warn "nf-snapshot: run did not succeed, skipping snapshot check"
            return
        }
        final file = snapshotFile()
        final base = FileHelper.toCanonicalPath(config.baseDir ?: session.params.outdir ?: session.outputDir)
        // ponytail: hashes serially, parallelise if full-size runs are slow
        final actual = capture(published, base, config.ignore, config.nameOnly)

        if( config.update || !Files.exists(file) ) {
            Files.createDirectories(file.parent)
            Files.writeString(file, toJson(actual))
            log.info "nf-snapshot: wrote ${actual.size()} files to ${file}"
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
     * Map each published file under {@code base} to its MD5, or null for name-only files.
     * Published directories are expanded into their files.
     */
    static Map<String,String> capture(Collection<Path> targets, Path base, List<String> ignore, List<String> nameOnly) {
        final outside = targets.findAll { Path it -> !it.startsWith(base) }
        if( outside )
            fail("nf-snapshot: ${outside.size()} published file(s) outside snapshot base dir ${base}, e.g. ${outside.take(3).join(', ')}. Set `snapshot.baseDir`")

        final ignoreMatchers = matchers(ignore)
        final nameOnlyMatchers = matchers(nameOnly)
        final result = new TreeMap<String,String>()
        for( Path target : targets ) {
            Files.walk(target, FileVisitOption.FOLLOW_LINKS).withCloseable { stream ->
                stream.filter { Path it -> Files.isRegularFile(it) }.forEach { Path it ->
                    final rel = base.relativize(it).toString()
                    if( !matches(ignoreMatchers, rel) )
                        result.put(rel, matches(nameOnlyMatchers, rel) ? null : hash(it))
                }
            }
        }
        if( !result )
            fail("nf-snapshot: no published files found under ${base}")
        return result
    }

    /** MD5 hex of the file content; .gz files are hashed decompressed because the gzip header holds a timestamp. */
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
