/* Copyright (c) 2009, University of Oslo, Norway
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 
 *  * Neither the name of the University of Oslo nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *      
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.vortikal.repository.systemjob;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.context.BaseContext;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.ResourceNotFoundException;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.search.ResultSet;
import org.vortikal.repository.search.Search;
import org.vortikal.repository.search.Searcher;
import org.vortikal.repository.search.query.Query;
import org.vortikal.security.SecurityContext;

public abstract class SystemJob {

    private Log logger = LogFactory.getLog(SystemJob.class);

    private static final int MAX_LIMIT = 100;

    private String systemJobName;
    private Repository repository;
    private Searcher searcher;
    private int limit = MAX_LIMIT;
    private SecurityContext securityContext;

    /**
     * List of properties to be affected as a result of this job. If none, all
     * properties of the resource in question are to be affected
     */
    private List<PropertyTypeDefinition> affectedProperties;

    protected abstract Query getSearchQuery();

    public synchronized void execute() {

        if (this.repository.isReadOnly()) {
            return;
        }

        try {

            BaseContext.pushContext();
            SecurityContext.setSecurityContext(this.securityContext);
            String token = SecurityContext.getSecurityContext().getToken();

            Query query = getSearchQuery();
            Search search = new Search();
            search.setQuery(query);
            search.setSorting(null);
            search.setLimit(this.limit);
            ResultSet results = this.searcher.execute(token, search);

            for (PropertySet propSet : results.getAllResults()) {
                try {
                    Resource resource = this.repository.retrieve(token, propSet.getURI(), true);
                    if (resource.getLock() == null) {
                        // XXX no, don't just store -> need to store with
                        // only the affected properties altered and
                        // system-job-status updated
                        // this.repository.store(token, resource);
                    }
                } catch (ResourceNotFoundException rnfe) {
                    // Resource is no longer there after search (deleted, moved
                    // or renamed)
                    logger.warn("A resource (" + propSet.getURI()
                            + ") that was to be affected by a systemjob was no longer available: " + rnfe.getMessage());
                }
            }

        } catch (Throwable t) {
            logger.error("An error occured while running job '" + this.systemJobName + "'", t);
        } finally {
            SecurityContext.setSecurityContext(null);
            BaseContext.popContext();
        }

    }

    @Required
    public void setSystemJobName(String systemJobName) {
        this.systemJobName = systemJobName;
    }

    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    @Required
    public void setSearcher(Searcher searcher) {
        this.searcher = searcher;
    }

    public void setLimit(int limit) {
        if (limit < 1) {
            logger.warn("Limit must be > 0, defaulting to " + MAX_LIMIT);
            return;
        }
        this.limit = limit;
    }

    @Required
    public void setSecurityContext(SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    public void setAffectedProperties(List<PropertyTypeDefinition> affectedProperties) {
        this.affectedProperties = affectedProperties;
    }
}
