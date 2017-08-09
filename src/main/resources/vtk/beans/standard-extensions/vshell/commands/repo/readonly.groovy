package vtk.shell.vshell

class RepoReadonlyCommand implements VCommand {

    String getDescription() {
        'Set the repository read-only status'
    }

    String getUsage() {
        'repo readonly [value:boolean]'
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        def repo = context.get("context").getBean("repository")
        def token = context.get("context").getBean("writeAllToken")

        if ("true" == args.value) {
            repo.setReadOnly(token, true)
        } else if ("false" == args.value) {
            repo.setReadOnly(token, false)
        }

        out.println "Repository read-only: ${repo.readOnly}"
    }

}
