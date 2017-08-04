package vtk.shell.vshell

class ServletServicesCommand implements VCommand {

    String getDescription() {
        'Display information about the configured web services'
    }

    String getUsage() {
        'servlet services [-assertions:boolean]'
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        out.print context.get("context")
          .getBean("requestContextInitializer")
          .printServiceTree(args.assertions != null)

    }

}
