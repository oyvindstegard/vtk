package vtk.shell.vshell

class RepoDeleteCommand implements VCommand {

    String getDescription() {
        'Deletes a resource'
    }

    String getUsage() {
        'repo delete <uri:path>'
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        def repo = context.get('context').getBean('repository')
        def token = context.get('context').getBean('writeAllToken')

        if (!repo.exists(token, args.uri)) {
            out.println "No such resource: ${args.uri}"
            return
        }

        try {
            repo.delete(token, null, args.uri, true)
        } catch (e) {
            out.println "Unable to delete ${args.uri}: ${e.message}"
        }
    }

}
