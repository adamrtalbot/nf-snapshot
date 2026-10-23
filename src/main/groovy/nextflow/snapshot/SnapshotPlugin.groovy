package nextflow.snapshot

import java.nio.file.Path
import java.nio.file.Paths

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import nextflow.SysEnv
import nextflow.cli.Launcher
import nextflow.cli.PluginExecAware
import nextflow.config.ConfigBuilder
import nextflow.exception.AbortOperationException
import nextflow.file.FileHelper
import nextflow.plugin.BasePlugin
import org.pf4j.PluginWrapper

/**
 * Adds {@code nextflow plugin nf-snapshot:update [--profile <name>] [--outputDir <dir>]}, which rewrites
 * the snapshot from the files under the base directory, without re-running the pipeline.
 *
 * Nextflow 26.04 calls the Launcher overload, later versions call the Map overload.
 * Both build the config from the current directory so the command behaves the same.
 */
@CompileStatic
class SnapshotPlugin extends BasePlugin implements PluginExecAware {

    static final List<String> OPTIONS = ['--profile', '--outputDir']

    static final String USAGE = 'usage: nextflow plugin nf-snapshot:update [--profile <name>] [--outputDir <dir>]'

    SnapshotPlugin(PluginWrapper wrapper) {
        super(wrapper)
    }

    @Override
    int exec(Launcher launcher, String pluginId, String cmd, List<String> args) {
        check(cmd, args)
        final profile = arg(args, '--profile')
        final config = new ConfigBuilder().setBaseDir(cwd()).setProfile(profile).build()
        return update(config, profile, arg(args, '--outputDir'))
    }

    // ponytail: dynamic because ConfigCmdAdapter is not in 26.04, drop when 26.04 support ends
    @CompileDynamic
    int exec(String pluginId, String cmd, List<String> args, Map ignored) {
        check(cmd, args)
        final profile = arg(args, '--profile')
        final adapter = Class.forName('nextflow.config.ConfigCmdAdapter').getDeclaredConstructor().newInstance()
        adapter.builder = new ConfigBuilder().setProfile(profile)
        final config = adapter.setBaseDir(cwd()).build()
        return update(config as Map, profile, arg(args, '--outputDir'))
    }

    int exec(String pluginId, String cmd, List<String> args) {
        return exec(pluginId, cmd, args, [:])
    }

    /** Reject anything but {@code --option value} pairs from {@link #OPTIONS}, so a typo cannot rewrite the snapshot. */
    static void check(String cmd, List<String> args) {
        if( cmd != 'update' )
            throw new AbortOperationException("nf-snapshot: unknown command `${cmd}` - ${USAGE}")
        for( int i = 0; i < args.size(); i += 2 ) {
            if( args[i] !in OPTIONS )
                throw new AbortOperationException("nf-snapshot: unknown argument `${args[i]}` - ${USAGE}")
            if( i + 1 >= args.size() || !args[i + 1] || args[i + 1].startsWith('--') )
                throw new AbortOperationException("nf-snapshot: `${args[i]}` needs a value - ${USAGE}")
        }
    }

    private static String arg(List<String> args, String name) {
        final i = args.indexOf(name)
        return i >= 0 ? args[i + 1] : null
    }

    private static int update(Map config, String profile, String outputDir) {
        final opts = new SnapshotConfig(config.snapshot as Map ?: [:], SysEnv.get())
        if( !opts.file )
            throw new AbortOperationException("nf-snapshot: `snapshot.file` is not set${profile ? " in profile `${profile}`" : ''} - ${USAGE}")
        final file = resolve(opts.file)
        // Resolve paths as Session.outputDir and the observer do, so NXF_FILE_ROOT applies
        final base = FileHelper.toCanonicalPath(outputDir ?: opts.baseDir ?: config.outputDir ?: 'results')
        SnapshotObserver.write(file, SnapshotObserver.capture([base], base, opts.ignore, opts.nameOnly, opts.sizeOnly))
        return 0
    }

    private static Path cwd() {
        return Paths.get('.').toAbsolutePath().normalize()
    }

    private static Path resolve(String path) {
        final result = FileHelper.asPath(path)
        return result.isAbsolute() ? result : cwd().resolve(path)
    }
}
