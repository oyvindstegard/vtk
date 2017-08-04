package vtk.shell.vshell

class ServletStatusCommand implements VCommand {
    
    String getDescription() {
        'Displays status information about the servlet'
    }

    String getUsage() {
        'servlet status'
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        out.println "Application startup: " + new Date(context.get("context").getStartupDate())

        def metrics = context.get("context").getBean("metrics.registry").metrics

        out.println "Total requests: ${metrics.'requests.processed'.count}"
        out.println "Failed requests: ${metrics.'requests.failed'.count}"
        out.println "Active requests: ${metrics.'requests.active'.count}"
    }

}
