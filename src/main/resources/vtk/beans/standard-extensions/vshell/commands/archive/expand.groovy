package vtk.shell.vshell

class ArchiveExpandCommand implements VCommand {

    String getDescription() {
        'Expands a JAR archive'
    }

    String getUsage() {
        'archive expand <file:string> <uri:path>'
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        def repo = context.get('context').getBean('repository')
        def token = context.get('context').getBean('writeAllToken')
        def archiver = context.get('context').getBean('repository.archiver')

        def file = args.file
        def uri = args.uri

        def listener = new vtk.util.repository.ResourceArchiver.EventListener() {
            void archived(vtk.repository.Path p) {
               out.println "add: ${p}"
            }
            void expanded(vtk.repository.Path p) {
                out.println "exp: ${p}"
            }
            void warn(vtk.repository.Path p, String msg) {
                out.println "warn: ${p}: ${msg}"
            }
        }

        if (!new File(file).exists()) {
            out.println "No such file: ${file}"
            return
        }

        def stream
        try {
            stream = new FileInputStream(file)
        } catch (e) {
            out.println "Unable to open archive file: ${file}: ${e.message}"
            return
        }

        def props = [:]
        // props.put("ignored", [])

        try {
            archiver.expandArchive(token, stream, uri, props, listener)
        } catch (e) {
            out.println "Unable to expand archive: ${file}: ${e.message}"
            return
        }
    }
}
