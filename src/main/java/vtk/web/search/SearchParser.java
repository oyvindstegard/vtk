/* Copyright (c) 2011, University of Oslo, Norway
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
package vtk.web.search;

import java.util.Objects;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.search.QueryParserFactory;
import vtk.repository.search.Sorting;
import vtk.repository.search.SortingParserFactory;
import vtk.repository.search.preprocessor.QueryStringPreProcessor;
import vtk.repository.search.query.Query;
import vtk.web.RequestContext;

public class SearchParser {
    private QueryParserFactory parserFactory;
    private SortingParserFactory sortingFactory;
    private QueryStringPreProcessor queryProcessor;
    
    public SearchParser(QueryParserFactory parserFactory, SortingParserFactory sortingFactory, 
            QueryStringPreProcessor queryProcessor) {
        this.parserFactory = Objects.requireNonNull(parserFactory);
        this.sortingFactory = Objects.requireNonNull(sortingFactory);
        this.queryProcessor = Objects.requireNonNull(queryProcessor);
    }
    
    public ContextualParser parser(HttpServletRequest request) {
        return new ContextualParser(request, parserFactory, 
                sortingFactory, queryProcessor);
    }
    
    public static final class ContextualParser {
        private HttpServletRequest request;
        private QueryParserFactory parserFactory;
        private SortingParserFactory sortingFactory;
        private QueryStringPreProcessor queryProcessor;
        
        private ContextualParser(HttpServletRequest request, 
                QueryParserFactory parserFactory, SortingParserFactory sortingFactory, 
                QueryStringPreProcessor queryProcessor) {
            this.request = request;
            this.parserFactory = parserFactory;
            this.sortingFactory = sortingFactory;
            this.queryProcessor = queryProcessor;
        }
        
        public Query parse(String query) {
            RequestContext requestContext = RequestContext
                    .getRequestContext(request);
            QueryStringPreProcessor.ProcessorContext ctx = 
                    new QueryStringPreProcessor.ProcessorContext(
                            requestContext.getResourceURI(), 
                            requestContext.getCurrentCollection());
            
            query = queryProcessor.process(query, ctx);
            return parserFactory.getParser().parse(query);
        }

        public Sorting parseSortString(String sorting) {
            return sortingFactory.parser().parse(sorting);
        }
    }
    
}
