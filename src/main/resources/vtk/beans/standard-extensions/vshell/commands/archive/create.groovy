package vtk.shell.vshell

class ArchiveCreateCommand implements VCommand {

    String getDescription() {
        'Creates a JAR archive'
    }

    String getUsage() {
        'archive create [-pipe:boolean] [-file:string] <uri:path>'
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        def repo = context.get('context').getBean('repository')
        def token = context.get('context').getBean('writeAllToken')
        def archiver = context.get('context').getBean('repository.archiver')

        def file = args.file
        def pipe = 'true' == args.pipe
        def uri = args.uri

        def stream = out
        if (!pipe) {
            if (!file) {
                out.println "A -file:string must be specified when not using -pipe:boolean"
                return
            }

            try {
                stream = new FileOutputStream(file)
            } catch (e) {
                out.println "Unable to create destination file '${file}': ${e.message}"
                return
            }
        }

        if (!repo.exists(token, uri)) {
            out.println "No such resource: ${uri}"
            return
        }

        def resource = repo.retrieve(token, uri, true)
        if (!resource.isCollection()) {
            out.println "Resource ${uri} is not a collection"
            return
        }

        def listener = new vtk.util.repository.ResourceArchiver.EventListener() {
            void archived(vtk.repository.Path p) {
                if (!pipe) {
                    out.println "add: ${p}"
                }
            }
            void expanded(vtk.repository.Path p) {
                if (!pipe) {
                    out.println "exp: ${p}"
                }
            }
            void warn(vtk.repository.Path p, String msg) {
                if (!pipe) {
                    out.println "warn: ${p}: ${msg}"
                }
            }
        }

        def props = [:]
        // props.put("ignored", [])

        try {
            archiver.createArchive(token, resource, stream, props, listener)
        } catch (e) {
            out.println "Unable to create archive: ${file}: ${e.message}"
        }
    }

}

