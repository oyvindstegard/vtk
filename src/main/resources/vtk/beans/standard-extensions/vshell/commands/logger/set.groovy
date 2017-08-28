package vtk.shell.vshell

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.LoggerContext

class LoggerSetCommand implements VCommand {

    String getDescription() {
        'Set level of a logger. Levels are: off, fatal, error, warn, info, debug, trace, all'
    }

    String getUsage() {
        'logger set <loggername:string> <level:string>'
    }

    void execute(VShellContext context, Map args, PrintStream out) {
        def loggerContext = LogManager.getContext(false)
        def configuration = loggerContext.getConfiguration()
        def loggerName = args.loggername in ['','root','ROOT'] ? '' : args.loggername

        if (loggerName == '') {
            out.println "This command does not allow manipulation of the ROOT logger level"
            return
        }

        def loggerConfig = configuration.getLoggerConfig(loggerName)
        def effectiveLevel = loggerConfig.getLevel()
        def inheritedFrom = loggerConfig.name != loggerName ? loggerConfig.name : null

        try {
            def newLevel = Level.valueOf(args.level)
            def result
            if (newLevel != effectiveLevel) {
                Configurator.setLevel(loggerName, newLevel)
                result = "Changed level of logger ${formatName(loggerName)} from ${effectiveLevel}"
                if (inheritedFrom) {
                    result += " (inherited from ${formatName(inheritedFrom)})"
                }
                result += " to ${newLevel}"

            } else {
                result = "Logger ${formatName(loggerName)} already has${inheritedFrom?' effective':''}"
                result += " level ${effectiveLevel}"
                if (inheritedFrom) {
                    result += ", inherited from ${inheritedFrom}"
                }
            }
            out.println result
        } catch (e) {
            out.println "An error occured: ${e.message}"
        }
    }

    def formatName(n) {
        n == '' ? 'ROOT' : n
    }
}
