package vtk.shell.vshell

class RepoIdCommand implements VCommand {
    String getDescription() { "Displays repository ID" }

    String getUsage() { "repo id" }

    void execute(VShellContext context, Map args, PrintStream out) {
        out.println(context.get("context").getBean("repository").getId())
    }
}
