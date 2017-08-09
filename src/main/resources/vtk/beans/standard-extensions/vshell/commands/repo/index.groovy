package vtk.shell.vshell

import vtk.repository.index.*
import vtk.repository.index.management.IndexOperationManager

class RepoIndexCommand implements VCommand {

    String getDescription() {
        'Reindex the repository system index'
    }

    String getUsage() {
        'repo index reindex [async:boolean]'
    }

    void execute(VShellContext context, Map args, PrintStream out) {
        def opMan = context.get('context').getBean('systemIndexOperationManager')
        reindex(opMan, 'true' == args.async, out)
    }

    void reindex(indexOperationManager, async, out) {
       if (async) {
            indexOperationManager.reindex(true)
            out.println 'Initiated asynchronous reindexing'
       } else {
            out.println 'Starting reindexing ..'
            indexOperationManager.reindex(false)
            def e = indexOperationManager.lastReindexingException
            if (e) {
                out.println "Reindexing failed: ${e.message}"
                e.printStackTrace(out)
            } else {
                def count = indexOperationManager.lastReindexingResourceCount
                out.println "Successfully re-indexed ${count} resources"
            }
       }
    }
}

