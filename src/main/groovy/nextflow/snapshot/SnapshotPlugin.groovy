package nextflow.snapshot

import groovy.transform.CompileStatic
import nextflow.plugin.BasePlugin
import org.pf4j.PluginWrapper

@CompileStatic
class SnapshotPlugin extends BasePlugin {

    SnapshotPlugin(PluginWrapper wrapper) {
        super(wrapper)
    }
}
