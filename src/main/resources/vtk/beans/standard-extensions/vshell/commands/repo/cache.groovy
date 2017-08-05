package vtk.shell.vshell

class RepoCacheCommand implements VCommand {
    String getDescription() {
        "Operate on the resource cache"
    }

    String getUsage() {
        "repo cache <command:string>"
    }

    void execute(VShellContext context, Map args, PrintStream out) {
        def cache = context.get("context").getBean("repository.cache")

        switch (args.command) {
          case "clear":
            cache.clear()
            break
          case "size":
            out.println(cache.size())
            break
          case "dump":
            cache.dump(out)
            break
          default:
            out.println("Unknown cache command: ${args.command}, available commands: clear, size, dump")
        }
    }
}
