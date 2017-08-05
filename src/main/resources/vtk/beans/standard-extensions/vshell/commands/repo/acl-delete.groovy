package vtk.shell.vshell

class RepoResourcetypesCommand implements VCommand {

    String getDescription() {
        'Deletes the ACL on a resource'
    }

    String getUsage() {
        'repo acl delete <uri:path>'
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        def repo = context.get('context').getBean('repository')
        def token = context.get('context').getBean('writeAllToken')

        def r = repo.retrieve(token, args.uri, true)
        if (r.inheritedAcl) {
            out.println "ACL is already inherited for resource ${args.uri}"
            return
        }

        try {
            repo.deleteACL(token, args.uri)
            out.println 'ACL deleted'
        } catch (e) {
            out.println "Error: ${e.message}"
        }
    }

}

