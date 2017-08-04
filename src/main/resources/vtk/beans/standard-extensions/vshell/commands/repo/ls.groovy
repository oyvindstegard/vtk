package vtk.shell.vshell

class RepoLsCommand implements VCommand {

    String getDescription() {
        'Lists resources in a collection'
    }

    String getUsage() {
        'repo ls <uri:path>'
    }

    void execute(VShellContext context, Map args, PrintStream out) {
        def repo = context.get('context').getBean('repository')
        def token = context.get('context').getBean('writeAllToken')

        def uri = context.get('cwd') ?: args.get('uri')

        repo.listChildren(token, uri, true).each { out.println(it.name) }
    }

}
