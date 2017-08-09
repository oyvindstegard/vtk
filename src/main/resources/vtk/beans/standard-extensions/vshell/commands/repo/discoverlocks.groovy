package vtk.shell.vshell

class RepoDiscoverlocks implements VCommand {

    String getDescription() { 'Discover locks' }

    String getUsage() { 'repo discoverlocks <uri:path>' }

    void execute(VShellContext context, Map args, PrintStream out) {

        def repo = context.get('context').getBean('repository')
        def token = context.get('context').getBean('writeAllToken')
        def cache = context.get('context').getBean('repository.cache')

        for (path in cache.discoverLocks(args.uri)) {
            def resource = repo.retrieve(token, path, true)
            if (resource.lock) {
                out.println resource.uri
                out.println "  ${resource.lock}"
                out.println()
            }
        }
    }
}
