package vtk.shell.vshell

import vtk.shell.ss.ShellSession
import vtk.shell.groovy.GroovyShellSessionFactory

class GroovyEvalCommand implements VCommand {

    String getDescription() {
        'Evaluates a Groovy expression using a GroovyShellSession'
    }

    String getUsage() {
        'groovy eval <expression:string>'
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        def shell = getShellSession(context, out)
        shell.evaluate((String)args.expression, out)

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
                printWelcomeMessage = false
            }

            shellSession = factory.newSession(out)
            vshContext.set('groovyShell', shellSession)
        }
        return shellSession
    }

}