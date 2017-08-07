/*
 * Simple hack that allows discovery of changes to the view definition
 * or component definitions for a "structured" resource type
 * definition. This file will be removed once support for reloading
 * resource type definitions is in place.
 *
 * MISSING:
 *
 *  o Checking for added or removed component definitions (currently
 *    only supports reloading of existing definitions)
 *  o Component parameters are ignored
 *  o Interrupt handling on container shutdown
 *  o ...
 */
package vtk

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware

class RecompileStructuredResources implements ApplicationContextAware {

    void setApplicationContext(ApplicationContext context) {
        def thread = new Thread(null, {
            while (true) {
                def parser = context.getBean('structuredResource.parser')
                parser.parseAllAndRefresh()

                try {
                    Thread.sleep(2000)
                } catch (e) {
                    break
                }
            }
        }, 'structured-resources-refresh-thread')
        thread.daemon = true
        thread.start()
    }

}
