package vtk.shell.vshell

import org.apache.logging.log4j.LogManager

class LoggerShowCommand implements VCommand {

    String getDescription() {
        'Show all current logger configuration levels, optionally matching substrings in names'
    }

    String getUsage() {
        'logger show [match:string...]'
    }

    void execute(VShellContext context, Map args, PrintStream out) {
        def loggerContext = LogManager.getContext(false)
        def configuration = loggerContext.getConfiguration()
        def match = (args.match?:[]).collect { it.toLowerCase() }
        configuration.getLoggers()
          .findAll({ name, config ->
            match.every { name.toLowerCase().contains(it) }})
          .sort()
          .each { name, config ->
              out.println "${name == '' ? 'ROOT' : name} has level ${config.level}" }
    }

}
