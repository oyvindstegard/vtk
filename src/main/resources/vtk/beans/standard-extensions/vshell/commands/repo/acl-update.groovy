package vtk.shell.vshell

class RepoAclUpdateCommand implements VCommand {

    String getDescription() {
        'Grants or revokes permissions on a resource'
    }

    String getUsage() {
        'repo acl update <uri:path> <operation:string> <permission:string> <principals:string...>'
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        def repo = context.get('context').getBean('repository')
        def token = context.get('context').getBean('writeAllToken')
        def principalFactory = context.get('context').getBean('principalFactory')

        def operation = args.operation

        if (!('grant' == operation || 'revoke' == operation)) {
            out.println "Invalid operation: ${operation}, valid operations are 'grant' or 'revoke'"
            return
        }

        def perm = args.permission
        def uri = args.uri

        if (!repo.exists(token, uri)) {
            out.println "No such resource: ${uri}"
            return
        }

        def r = repo.retrieve(token, uri, true)

        def newPrincipals = []
        for (p in args.principals) {
            if (p.startsWith("g:")) {
                newPrincipals << principalFactory.getPrincipal(p.substring(2), vtk.security.Principal.Type.GROUP)
            } else if (p.startsWith("pseudo:")) {
                newPrincipals << principalFactory.getPseudoPrincipal(p)
            } else {
                newPrincipals << principalFactory.getPrincipal(p, vtk.security.Principal.Type.USER)
            }
         }

        def action = vtk.repository.Privilege.forName(perm)
        try {
            def acl = r.acl

            for (p in newPrincipals) {
                if ('grant' == operation) {
                    acl = acl.addEntry(action, p)
                } else {
                    acl = acl.removeEntry(action, p)
                }
            }

            r = repo.storeACL(token, null, r.URI, acl)
            out.println "Stored ACL on ${r.URI}: ${r.acl}"
        } catch (e) {
            out.println "Error: ${e.message}"
        }
    }
}
