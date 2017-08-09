/* Copyright (c) 2012, University of Oslo, Norway
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

package vtk.repository.systemjob;


import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.PropertySet;
import vtk.repository.Repository;
import vtk.repository.ResourceTypeTree;
import vtk.repository.SystemChangeContext;
import vtk.repository.search.PropertySelect;
import vtk.repository.search.QueryParserFactory;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.Searcher;
import vtk.repository.search.Sorting;
import vtk.repository.search.SortingParserFactory;
import vtk.repository.search.preprocessor.QueryStringPreProcessor;
import vtk.repository.search.preprocessor.QueryStringPreProcessor.ProcessorContext;
import vtk.repository.search.query.Query;

/**
 *
 */
public class IndexQueryPathSelector implements PathSelector {

    private final Logger logger = LoggerFactory.getLogger(IndexQueryPathSelector.class);
    
    protected Searcher searcher;
    protected QueryParserFactory parser;
    protected QueryStringPreProcessor queryProcessor;
    protected SortingParserFactory sortingParser;
    protected ResourceTypeTree resourceTypeTree;
    
    private String queryString;
    private String sortString;
    private boolean useDefaultExcludes = false;
    private boolean waitForPendingUpdates = false;
    
    private int limit = 2000;

    @Override
    public void selectWithCallback(Repository repository, SystemChangeContext context,
                PathSelectCallback callback) throws Exception {

        final String token = context.getSecurityContext().getToken();
                
        Query query = getQuery(context);
        Sorting sort = getSorting(context);
        Search search = new Search();
        if (waitForPendingUpdates) {
            search.setWaitForPendingUpdates(Instant.now(), Duration.ofSeconds(30));
        }
        search.setQuery(query);
        search.setSorting(sort);
        search.setLimit(this.limit);
        if (!useDefaultExcludes){
            search.clearAllFilterFlags();
        }
        search.setPropertySelect(PropertySelect.NONE);
        ResultSet results = searcher.execute(token, search);

        if (logger.isDebugEnabled()) {
            logger.debug("Ran query " + query + (sort != null ? ", sorting: " + sort + "," : "")
                    + " with " + results.getSize()
                    + " results of total " + results.getTotalHits());
            
        }

        callback.beginBatch(results.getSize());
        for (PropertySet result: results) {
            callback.select(result.getURI());
        }
    }
    
    protected Query getQuery(SystemChangeContext context) {
        if (queryString == null) {
            throw new IllegalStateException("No query string configured");
        }
        if (queryProcessor != null) {
            ProcessorContext queryProcessorContext = 
                    new QueryStringPreProcessor.ProcessorContext(Path.ROOT, Path.ROOT);
            queryString = queryProcessor.process(queryString, queryProcessorContext);
        }
        return parser.getParser().parse(queryString);
    }
    
    protected Sorting getSorting(SystemChangeContext context) {
        return sortingParser.parser().parse(sortString);
    }
    
    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }
    
    public void setSortString(String sortString) {
        this.sortString = sortString;
    }
    
    public void setParser(QueryParserFactory parser) {
        this.parser = parser;
    }
    
    public void setQueryProcessor(QueryStringPreProcessor queryProcessor) {
        this.queryProcessor = queryProcessor;
    }
    
    public void setSortingParser(SortingParserFactory sortingParser) {
        this.sortingParser = sortingParser;
    }

    public boolean isUseDefaultExcludes() {
        return useDefaultExcludes;
    }

    public void setLimit(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be >= 1");
        }
        this.limit = limit;
    }

    @Required
    public void setSearcher(Searcher searcher) {
        this.searcher = searcher;
    }
    
    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    public void setUseDefaultExcludes(boolean useDefaultExcludes) {
        this.useDefaultExcludes = useDefaultExcludes;
    }
    
    public void setWaitForPendingUpdates(boolean waitForPendingUpdates) {
        this.waitForPendingUpdates = waitForPendingUpdates;
    }

}
