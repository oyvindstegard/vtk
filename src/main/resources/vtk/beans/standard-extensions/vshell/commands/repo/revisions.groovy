package vtk.shell.vshell

class RepoRevisionsCommand implements VCommand {

    String getDescription() {
        "Lists revisions on a resource"
    }

    String getUsage() {
        "repo revisions <uri:path>"
    }

    void execute(VShellContext context, Map args, PrintStream out) {
        def repo = context.get("context").getBean("repository")
        def token = context.get("context").getBean("writeAllToken")

        if (!repo.exists(token, args.uri)) {
            out.println("No such resource: ${args.uri}")
            return
        }

        repo.getRevisions(token, args.uri).each { out.println(it) }
    }

}
