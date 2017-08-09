package vtk.shell.vshell

class RepoAclDiscoverCommand implements VCommand {

    String getDescription() {
        'Discovers ACLs below a resource'
    }

    String getUsage() {
        'repo acl discover <uri:path>'
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        def backend = context.get("context").getBean("repository.cache");

        backend.discoverACLs(args.uri).each { out.println it }
    }

}
