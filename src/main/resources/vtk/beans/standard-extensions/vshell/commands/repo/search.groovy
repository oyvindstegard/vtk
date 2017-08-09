package vtk.shell.vshell

import vtk.repository.search.*

class RepoSearchCommand implements VCommand {

    String getDescription() {
        'Searches the repository'
    }


    String getUsage() {
        'repo search [-limit:number] [-sort:string] <query:string> [-properties:string...]'
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        def repo = context.get('context').getBean('repository')
        def token = context.get('context').getBean('writeAllToken')
        def indexSearcher = context.get('context').getBean('systemIndexSearcher')
        def queryParserFactory = context.get('context').getBean('queryParserFactory')
        def rtt = context.get('context').getBean('resourceTypeTree')

        def query
        try {
            query = queryParserFactory.getParser().parse(args.query)
        } catch (t) {
            out.println(t.message)
            return
        }

        def search = new Search()
        search.with {
            clearAllFilterFlags()
            setQuery(query)
            if (args.limit >= 0) {
                setLimit(args.limit)
            }
            setSorting(parseSorting(args.sort, rtt))
            setPropertySelect(parseSelect(args.properties, rtt))
        }
        def displayResourceType = args.properties?.contains('type') || args.properties?.contains('*')

        def rs = indexSearcher.execute(token, search)
        for (r in rs) {
            out.println(r.URI)
            if (displayResourceType) {
                out.println("  resourcetype: ${r.resourceType}")
            }
            for (p in r) {
                def field = null
                if (p.type == vtk.repository.resourcetype.PropertyType.Type.JSON) {
                    field = getJsonFieldSelector(p.definition.namespace.prefix,
                                                 p.definition.name, args.properties)
                }
                printProp(p, field, out)
            }
            if (r.acl().present) {
                printAcl(r.acl().get(), r.inheritedAcl, out)
            }
            if (r != rs.last()) {
                out.println()
            }
        }
        out.println()
        out.println("[Total results fetched: ${rs.size}, total hits: ${rs.totalHits}")

    }

    def getJsonFieldSelector(prefix, name, propertiesArg) {
        if (!propertiesArg) return null

        def propSpec = prefix ? "${prefix}:${name}" : name

        for (propSelect in propertiesArg) {
            if (propSelect.startsWith(propSpec + "@")) {
                def fieldSelector = propSelect.substring(propSelect.indexOf("@"))
                if (fieldSelector.length() > 1) {
                    return fieldSelector.substring(1)
                }
            }
        }
    }

    def parseSorting(sortArg, rtt) {
        if (!sortArg) return null

        def sorting = new Sorting()
        def direction = SortField.Direction.ASC
        if (sortArg.trim().contains(" ")) {
            def dir = sortArg.substring(sortArg.indexOf(" ") + 1)
            sortArg = sortArg.substring(0, sortArg.length() - dir.length() - 1)
            direction = ("desc" == dir || "DESC" == dir) ? SortField.Direction.DESC : SortField.Direction.ASC
        }

        def sf
        if (sortArg in ['type', 'name', 'uri']) {
            sf = new ResourceSortField(sortArg, direction)
        }
        else {
            def prefix
            def name
            if (sortArg.contains(":")) {
                prefix = sortArg.substring(0, sortArg.indexOf(":"))
                name = sortArg.substring(prefix.length() + 1)
            } else {
                prefix = null
                name = sortArg
            }
            def propDef = rtt.getPropertyDefinitionByPrefix(prefix, name)
            if (propDef) {
                sf = new PropertySortField(propDef, direction)
            }
        }
        if (sf) {
            sorting.addSortField(sf)
            return sorting
        }
    }

    def parseSelect(propList, rtt) {
        if (!propList) return PropertySelect.NONE

        if (propList.contains("*")) return PropertySelect.ALL

        def propertySelect = new ConfigurablePropertySelect()

        for (propName in propList) {
            if ("acl" == propName) {
                propertySelect.setIncludeAcl(true)
                continue
            }

            def field
            if (propName.contains("@")) {
                field = propName.substring(propName.indexOf("@") + 1)
                propName = propName.substring(0, propName.indexOf("@"))
            }
            def p = propName
            def prefix

            if (p.contains(":")) {
                prefix = p.substring(0, p.indexOf(":"))
                p = p.substring(prefix.length() + 1)
            }
            def definition = rtt.getPropertyDefinitionByPrefix(prefix, p)
            if (!definition) continue

            propertySelect.addPropertyDefinition(definition)
        }

        return propertySelect
    }

    def printProp(prop, field, out) {
        if (prop.type == vtk.repository.resourcetype.PropertyType.Type.BINARY) {
            out.println("  " + prop.getDefinition().getName() + ": [binary]")
        }
        else {
            def value
            if (prop.definition.multiple) {
                value = prop.values.collect { format(prop, it, field) }
            } else {
                value = format(prop, prop.value, field)
            }
            if (field) {
                out.println("  " + prop.getDefinition().getName() + "@" + field + ": " + value)
            } else {
                out.println("  " + prop.getDefinition().getName() + ": " + value)
            }
        }
    }

    def format(prop, value, field) {
        if (field && prop.type == vtk.repository.resourcetype.PropertyType.Type.JSON) {
            return vtk.util.text.Json.select(value.JSONValue, field)
        }
        return value
    }

    def printAcl(acl, inherited, out) {
        out.println("  " + acl.toString() + (inherited ? " (inherited)" : " (set on this resource)"))
    }

}
