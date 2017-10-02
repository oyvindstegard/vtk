package vtk.shell.vshell

class RepoInfoCommand implements VCommand {
  
    String getDescription() {
        "Displays information about a resource"
    }

    String getUsage() {
        "repo info [-revision:string] <uri:path>"
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        def repo = context.get("context").getBean("repository")
        def token = context.get("context").getBean("writeAllToken")
        def uri = args.uri

        if (!repo.exists(token, uri)) {
            out.println("No such resource")
            return
        }

        def revision = repo.getRevisions(token, uri).find({it.name == args.revision})
        if (args.revision && !revision) {
            out.println("No such revision: ${args.revision}")
            return
        }

        def r = revision ? repo.retrieve(token, uri, true, revision) :
            repo.retrieve(token, uri, true)
        out.println("id: ${r.resourceId}")
        out.println("type: ${r.resourceType}")

        out.println()
        out.println('props:')
        Set namespaces = []
        for (p in r) {
            def ns = p.definition.namespace
            namespaces << ns
            out.print("${ns.prefix?:ns}: ${p.definition.name}: ")
            if (p.type == vtk.repository.resourcetype.PropertyType.Type.BINARY) {
                out.println('[binary]' + (p.inherited ? '  (inherited)':''))
            } else {
                out.println(p.formattedValue + (p.inherited ? '  (inherited)':''))
            }
        }

        out.println()
        out.println('prop namespaces:')
        for (ns in namespaces) {
            out.println(ns)
        }

        out.println()
        out.println("acl:")
        out.println("inherited: ${r.inheritedAcl}")
        for (action in r.acl.actions) {
            out.print("${action}: ")
            def principals = r.acl.listPrivilegedUsers(action) +
                             r.acl.listPrivilegedGroups(action) +
                             r.acl.listPrivilegedPseudoPrincipals(action)
            out.println(principals.join(", "))
        }

        if (r.lock) {
            out.println()
            out.println "lock:"
            out.println "type: ${r.lock.type}"
            out.println "token: ${r.lock.lockToken}"
            out.println "principal: ${r.lock.principal}"
            out.println "timeout: ${r.lock.timeout}"
            out.println "owner info: ${r.lock.ownerInfo}"
        }
    }
}