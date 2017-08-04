package vtk.shell.vshell

class ServletServiceCommand implements VCommand {

    String getDescription() {
        'Display information about a specified web service'
    }

    String getUsage() {
        'servlet service <name:string>'
    }

    void execute(VShellContext context, Map args, PrintStream out) {
        def ctx = context.get("context");
        def name = args.get("name");
        def s = ctx.getBean(name);
        out.println("Service: ${s.name}")
        if (s.parent) {
            out.println("Parent: ${s.parent.name}")
        }

        out.println("Assertions:")
        for (a in s.allAssertions) {
            out.println("   ${a}")
        }
        out.println("Handler: ${s.handler}")
    }

}

