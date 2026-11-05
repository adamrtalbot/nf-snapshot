package nextflow.snapshot

import groovy.transform.CompileStatic
import nextflow.Session
import nextflow.SysEnv
import nextflow.exception.AbortOperationException
import nextflow.trace.TraceObserverFactoryV2
import nextflow.trace.TraceObserverV2

@CompileStatic
class SnapshotFactory implements TraceObserverFactoryV2 {

    @Override
    Collection<TraceObserverV2> create(Session session) {
        final config = new SnapshotConfig(session.config.snapshot as Map ?: [:], SysEnv.get())
        if( !config.enabled )
            return []
        if( !config.file )
            throw new AbortOperationException("nf-snapshot: `snapshot.file` must be set when `snapshot.enabled = true`")
        final observer = new SnapshotObserver(config, session)
        // Nextflow creates observers just before WorkflowMetadata, whose shutdown hook sets `workflow.success`.
        // Shutdown hooks run in order, so this check runs first and a mismatch fails the run on Seqera Platform too.
        session.onShutdown { observer.verify() }
        return List.<TraceObserverV2>of(observer)
    }
}
