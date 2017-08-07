package vtk.shell.vshell

class RepoPropCommand implements VCommand {

    static final BINARY_VALUE_MAX_DISPLAY_LENGTH = 200000

    String getDescription() {
        'Displays a specified property value for a given resource'
    }

    String getUsage() {
        'repo prop <uri:path> <prop:string> [display-binary:boolean]'
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        def repo = context.get("context").getBean("repository")
        def token = context.get("context").getBean("writeAllToken")

        def uri = args.uri

        if (!repo.exists(token, uri)) {
            out.println "No such resource: ${uri}"
            return
        }

        def prop = args.prop
        def displayBinary = ('true' == args.'display-binary')

        def r = repo.retrieve(token, uri, true)
        def prefix = null

        if (prop.contains(':')) {
            prefix = prop.substring(0, prop.indexOf(':'))
            prop = prop.substring(prefix.length() + 1)
        }

        def p = r.getPropertyByPrefix(prefix, prop)
        if (!p) {
            out.println "No such property: ${prefix}:${prop}"
            return
        }

        out.println "Type: ${p.type} ${p.inherited ? '[inherited]':''}"
        if (p.type == vtk.repository.resourcetype.PropertyType.Type.BINARY) {
            p.getBinaryStream().withCloseable { stream ->
                def length = stream.length()
                if (!displayBinary) {
                    out.println "{binary data: ${p.binaryContentType}, length: ${length}}"
                } else {
                    if (length <= BINARY_VALUE_MAX_DISPLAY_LENGTH) {
                        try {
                            def textContent = stream.getText('UTF-8')
                            out.println(textContent)
                        } catch (e) {
                            out.println "Error decoding binary property value as UTF-8: ${e.message}"
                        }
                    } else {
                        out.println "Error: too large content (over ${BINARY_VALUE_MAX_DISPLAY_LENGTH} bytes) to display in shell"
                    }
                }
            }
        } else {
            out.println(p.getFormattedValue())
        }
    }


}

