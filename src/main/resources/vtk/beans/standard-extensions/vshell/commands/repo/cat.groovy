package vtk.shell.vshell

class RepoCatCommand implements VCommand {

    String getDescription() {
        "Displays the contents of a file"
    }

    String getUsage() {
        "repo cat [-revision:string] [-out:string] <uri:path>"
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        def repo = context.get("context").getBean("repository")
        def token = context.get("context").getBean("writeAllToken")

        if (!repo.exists(token, args.uri)) {
            out.println("No such resource: ${args.uri}")
            return
        }

        def revision = repo.getRevisions(token, args.uri).find({it.name == args.revision})
        if (args.revision && !revision) {
            out.println("No such revision: ${args.revision}")
            return
        }

        def resource = revision ? repo.retrieve(token, args.uri, true, revision)
                                : repo.retrieve(token, args.uri, true)

        if (resource.isCollection()) {
            out.println("Resource is a collection")
            return
        }

        def istream = revision ? repo.getInputStream(token, args.uri, true, revision)
                               : repo.getInputStream(token, args.uri, true)

        if (args.out) {
            try {
                new File(args.out).withOutputStream { ostream -> ostream << istream }
            } catch (e) {
                out.println("Error writing file ${args.out}: ${e.getMessage()}")
            }
        } else {
            istream.eachLine (resource.getCharacterEncoding() ?: "UTF-8") { line ->
                out.println(line)
            }
        }
    }
}
