package vtk.shell.vshell

import vtk.shell.ss.*

class GroovyEvalCommand implements VCommand {

    String getDescription() {
        'Evaluates a Groovy expression using a GroovyShellSession'
    }

    String getUsage() {
        'groovy eval <expression:string>'
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        def shell = getShellSession(context, out)
        shell.evaluate((String)args.expression)

    }

    // A Groovy shell session is started lazily on first evaluation and is bound to VShell context
    ShellSession getShellSession(VShellContext vshContext, PrintStream out) {
        def shellSession = vshContext.get('groovyShell')
        if (!shellSession) {
            GroovyShellSessionFactory factory = new GroovyShellSessionFactory()
            factory.with {
                applicationContext = vshContext.get('context')
                resourceLoader = vshContext.get('resourceLoader')
                prompt = ""
            }

            shellSession = factory.newSession(new BufferedReader(new StringReader('')), out)
            vshContext.set('groovyShell', shellSession)
        }
        return shellSession
    }

}