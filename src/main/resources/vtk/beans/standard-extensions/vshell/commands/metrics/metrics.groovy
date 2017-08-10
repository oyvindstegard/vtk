package vtk.shell.vshell

import com.codahale.metrics.*
import java.util.concurrent.TimeUnit

class MetricsCommand implements VCommand {

    String getDescription() {
        'Report application metrics, optionally filter on metric name'
    }

    String getUsage() {
        'metrics [filter:string]'
    }

    void execute(VShellContext context, Map args, PrintStream out) {
        def registry = context.get("context").getBean("metrics.registry")
        def filter = args.filter ? { n, m -> n.contains(args.filter) } : MetricFilter.ALL
        ConsoleReporter.forRegistry(registry)
                       .convertRatesTo(TimeUnit.SECONDS)
                       .convertDurationsTo(TimeUnit.MILLISECONDS)
                       .outputTo(out)
                       .filter(filter)
                       .build().report()
    }

}
