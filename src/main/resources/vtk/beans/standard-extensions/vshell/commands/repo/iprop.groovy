package vtk.shell.vshell

import vtk.repository.*
import vtk.repository.resourcetype.*

class RepoIPropCommand implements VCommand {

    String getDescription() {
        'Store an inheritable property on a resource'
    }

    String getUsage() {
        'repo iprop <command:string> <uri:path> <prop:string> [values:string...]'
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        def repo = context.get('context').getBean('repository')
        def token = context.get('context').getBean('writeAllToken')
        def typeTree = context.get('context').getBean('resourceTypeTree')

        def command = args.command
        def uri = args.uri

        if (!repo.exists(token, uri)) {
            out.println "No such resource: ${uri}"
            return
        }

        def prop = args.prop
        def prefix = null
        if (prop.contains(":")) {
            prefix = prop.substring(0, prop.indexOf(":"));
            prop = prop.substring(prefix.length() + 1);
        }
        def propDef = typeTree.getPropertyDefinitionByPrefix(prefix, prop)
        if (!propDef) {
            out.println "Error: definition for property ${prefix}:${prop} not found"
            return
        }
        if (!propDef.isInheritable()) {
            out.println "Error: not an inheritable property: ${propDef}"
            return
        }

        def r = repo.retrieve(token, uri, false);

        if ("store" == command) {
            def valueargs = args.values
            if (!valueargs) {
                out.println 'Must provide at least one property value when storing'
                return
            }

            def property = propDef.createProperty()
            if (propDef.isMultiple()) {
                def values = valueargs.collect { val -> new Value(val, propDef.type) } as Value[]
                property.setValues(values)
            } else {
                property.setValue(new Value(valueargs.get(0), propDef.type))
            }
            r.addProperty(property)
        }
        else if ("del" == command || "delete" == command) {
            r.removeProperty(propDef)
        }
        else {
            out.println "Must provide command: 'store' or 'delete'"
            return
        }

        def storeContext = new InheritablePropertiesStoreContext([propDef])
        repo.store(token, r, storeContext)
    }

}

