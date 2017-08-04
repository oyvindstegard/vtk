package vtk.shell.vshell

class RepoFindCommand implements VCommand {

    String getDescription() {
        "Finds resources below a given path"
    }

    String getUsage() {
        "repo find <uri:path>"
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        def repo = context.get("context").getBean("repository")
        def token = context.get("context").getBean("writeAllToken")

        if (!repo.exists(token, args.uri)) {
            out.println("No such resource: ${args.uri}")
            return
        }
        def resource = repo.retrieve(token, args.uri, true)
        traverse(resource, repo, token, out)
    }

    private void traverse(resource, repo, token, out) {
        out.println(resource.URI)

        if (resource.isCollection()) {
            repo.listChildren(token, resource.URI, true).each {
              traverse(it, repo, token, out)
            }
        }
    }
}
