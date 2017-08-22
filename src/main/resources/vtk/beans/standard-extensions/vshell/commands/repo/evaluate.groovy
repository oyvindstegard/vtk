package vtk.shell.vshell

import vtk.repository.*
import vtk.repository.content.*
import vtk.repository.event.*
import vtk.repository.resourcetype.*
import vtk.repository.search.*

class RepoEvaluateCommand implements VCommand {

    String getDescription() {
        'Re-evaluates resources for a given query'
    }

    String getUsage() {
        'repo evaluate [-logfile:string] <query:string> [-properties:string...]'
    }

    void execute(VShellContext context, Map args, PrintStream out) {

        def beanContext = context.get("context")

        def repo = beanContext.getBean("repository")
        def token = beanContext.getBean("writeAllToken")
        def principal = beanContext.getBean("tokenManager").getPrincipal(token)
        def tm = beanContext.getBean("repository.transactionManager")
        def searcher = beanContext.getBean("systemIndexSearcher")
        def parserFactory = beanContext.getBean("queryParserFactory")

        def cache = beanContext.getBean("repository.cache")
        def dao = cache.wrappedAccessor
        def cluster = repo.clusterContext
        def helper = beanContext.getBean("repositoryResourceHelper")
        def contentStore = beanContext.getBean("repository.fsContentStore")
        def contentRegistry = beanContext.getBean("contentRepresentationRegistry")

        def query = parserFactory.getParser().parse(args.query)

        def logFile = args.logfile
        def log = out
        if (logFile) {
            try {
                log = new PrintWriter(new FileOutputStream(logFile))
            } catch (e) {
                out.println "Error: unable to create log file ${logFile}: ${e.message}"
                return
            }
            out.println "Log file: ${logFile}"
        }
        def propList = args.properties

        def search = new vtk.repository.search.Search()
        search.with {
            clearAllFilterFlags()
            setQuery(query)
            setLimit(Integer.MAX_VALUE)
        }

        def before = System.currentTimeMillis()
        def number = 0
        searcher.iterateMatching(token, search, { vtk.repository.PropertySet propset ->
            number++
            def orig = repo.retrieve(token, propset.getURI(), true)
            def origClone = orig.clone()

            log.println propset.URI

            def coll = propset.getProperty(Namespace.DEFAULT_NAMESPACE,
                                               PropertyType.COLLECTION_PROP_NAME).getBooleanValue()

            def content = coll ? null : new ContentImpl(propset.getURI(), contentStore, contentRegistry)

            def evaluated
            try {
                evaluated = helper.contentModification(orig, orig.getOwner(), content)
            }
            catch (Throwable t) {
                log.print "Content modification evaluation error: ${t.message}"
                return true
            }

            def prop = orig.getProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.LASTMODIFIED_PROP_NAME)
            if (prop) evaluated.addProperty((Property) prop.clone())
            prop = orig.getProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.MODIFIEDBY_PROP_NAME)
            if (prop) evaluated.addProperty((Property) prop.clone())
            prop = orig.getProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.CONTENTLASTMODIFIED_PROP_NAME)
            if (prop) evaluated.addProperty((Property) prop.clone())
            prop = orig.getProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.CONTENTMODIFIEDBY_PROP_NAME)
            if (prop) evaluated.addProperty((Property) prop.clone())

            if (propList) {
                def storableProps = []
                for (propName in propList) {
                    def p = propName
                    def prefix = null
                    if (p.contains(":")) {
                        prefix = p.substring(0, p.indexOf(":"))
                        p = p.substring(prefix.length() + 1)
                    }
                    def p2 = evaluated.getPropertyByPrefix(prefix, p)
                    if (p2) {
                        storableProps.add(p2)
                    }
                }

                try {
                    for (origProp in orig) {
                        evaluated.removeProperty(origProp.getDefinition())
                        evaluated.addProperty((Property) origProp.clone())
                    }
                    for (p2 in storableProps) {
                        evaluated.removeProperty(p2.getDefinition())
                        evaluated.addProperty(p2)
                    }
                } catch (Throwable t) {
                    log.println "Error: ${t.message}"
                    return true
                }
            }

            logPropertyModifications(evaluated, origClone, log)

            // store directly to DAO layer:

            // def defTx = new org.springframework.transaction.support.DefaultTransactionDefinition();
            // def defTx.setName("PropertyEvaluationTx");
            // def defTx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRED);
            def defTx
            def status = tm.getTransaction(defTx)
            try {
                //tm.startTransaction()
                dao.store(evaluated)

                // flush resource from cache (and propagate flush message to cluster node(s)):
                cache.flushFromCache(evaluated.getURI(), false)
                if (cluster.isPresent()) {
                    def msg = new vtk.repository.LockingCacheControlRepositoryWrapper.FlushMessage(
                          evaluated.getURI(), false, 'evaluate.groovy')
                    cluster.get().clusterMessage(msg)
                }

                // trigger re-indexing:
                def event = new ResourceModificationEvent(repo, principal, evaluated, origClone)
                beanContext.publishEvent(event)

                tm.commit(status)
            } catch (Throwable t) {
                log.println "Error: ${t.message}"
                tm.rollback(status)
                return true
            } finally {
                //tm.endTransaction();
            }

            log.flush()
            return true
        })

        def duration = System.currentTimeMillis() - before
        //log.println("-- evaluated " + number + " resources in " + duration / 1000 + " s");
        log.println "-- evaluated ${number} resources in ${duration/1000} s"

        log.flush()
        if (logFile) {
            log.close()
        }
    }

    def logPropertyModifications(evaluatedResource, originalResource, log) {
        for (prop in evaluatedResource) {
            def ns = prop.getDefinition().getNamespace()
            def name = prop.getDefinition().getName()
            def multi = prop.getDefinition().isMultiple()
            def old = originalResource.getProperty(ns, name)

            def prefix = ns.prefix ? ns.prefix + ':' : ''
            if (prop.getDefinition()) {
                if (old == null) {
                    def curVal = multi ? prop.getValues().toList() : prop.getValue()
                    log.println "  + ${prefix}${name}: ${curVal}"
                } else if (old != prop) {
                    def prevVal = old.getDefinition().isMultiple() ? old.getValues().toList() : old.getValue()
                    def curVal = multi ? prop.getValues().toList() : prop.getValue()
                    log.println "   m ${prefix}${name}: '${prevVal}' --> '${curVal}'"
                }
            }
        }
    }

}
