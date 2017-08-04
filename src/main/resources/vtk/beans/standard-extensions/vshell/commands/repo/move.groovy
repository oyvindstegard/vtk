package vtk.shell.vshell

class RepoMoveCommand implements VCommand {

    String getDescription() {
        "Moves a resource"
    }

    String getUsage() {
        "repo move <from:path> <to:path>"
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        def repo = context.get("context").getBean("repository")
        def token = context.get("context").getBean("writeAllToken")
        def from = args.from
        def to = args.to

        if (!repo.exists(token, from)) {
            out.println("No such resource: ${from}")
            return
        }

        if (repo.exists(token, to)) {
            out.println("Resource already exists: ${to}")
            return
        }

        try {
            repo.move(token, from, to, false)
        } catch (e) {
            out.println("Unable to move ${from} to ${to}: ${e.getMessage()}")
        }
    }

}
