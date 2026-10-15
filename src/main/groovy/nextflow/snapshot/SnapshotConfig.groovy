package nextflow.snapshot

import groovy.transform.CompileStatic
import nextflow.config.spec.ConfigOption
import nextflow.config.spec.ConfigScope
import nextflow.config.spec.ScopeName
import nextflow.script.dsl.Description

@ScopeName('snapshot')
@Description("""
    The `snapshot` scope verifies the files published by a run against a committed snapshot file.
""")
@CompileStatic
class SnapshotConfig implements ConfigScope {

    @ConfigOption
    @Description("""
        Enable the snapshot check (default: `false`).
    """)
    final boolean enabled

    @ConfigOption
    @Description("""
        Snapshot JSON file. Relative paths resolve against the project directory.
    """)
    final String file

    @ConfigOption
    @Description("""
        Directory that snapshot paths are relative to (default: `outputDir`).
    """)
    final String baseDir

    @ConfigOption
    @Description("""
        Write the snapshot instead of verifying it (default: `false`). Also enabled by `NXF_SNAPSHOT_UPDATE=true`.
    """)
    final boolean update

    @ConfigOption
    @Description("""
        Glob patterns of relative paths to exclude from the snapshot.
    """)
    final List<String> ignore

    @ConfigOption
    @Description("""
        Glob patterns of relative paths that are checked by name only, without a content hash.
    """)
    final List<String> nameOnly

    /* required by extension point -- do not remove */
    SnapshotConfig() {}

    SnapshotConfig(Map opts, Map<String,String> env) {
        this.enabled = opts.enabled as boolean
        this.file = opts.file as String
        this.baseDir = opts.baseDir as String
        this.update = opts.update as boolean || env.get('NXF_SNAPSHOT_UPDATE') == 'true'
        this.ignore = (opts.ignore ?: []) as List<String>
        this.nameOnly = (opts.nameOnly ?: []) as List<String>
    }
}
