package vtk.shell.vshell

class RepoResourcetypesCommand implements VCommand {

    String getDescription() {
        'Display information about resource types'
    }

    String getUsage() {
        'repo resourcetypes'
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        def rtt = context.get('context').getBean('resourceTypeTree')
        out.print rtt.getResourceTypeTreeAsString()
    }

}

