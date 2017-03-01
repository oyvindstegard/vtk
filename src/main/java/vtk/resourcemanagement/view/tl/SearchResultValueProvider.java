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
package vtk.resourcemanagement.view.tl;

import org.springframework.beans.factory.annotation.Required;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.Parser;
import vtk.repository.search.PropertySortField;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.Searcher;
import vtk.repository.search.SortField;
import vtk.repository.search.Sorting;
import vtk.repository.search.query.Query;
import vtk.text.tl.Context;
import vtk.text.tl.Symbol;
import vtk.text.tl.expr.Function;
import vtk.web.RequestContext;

public class SearchResultValueProvider extends Function {

    private Parser searchParser;
    // private QueryParserFactory queryParserFactory;
    private Searcher searcher;
    private PropertyTypeDefinition titlePropDef;

    public SearchResultValueProvider(Symbol symbol,
    // QueryParserFactory queryParserFactory,
            Parser searchParser, Searcher searcher) {
        super(symbol, 2);
        // this.queryParserFactory = queryParserFactory;
        this.searchParser = searchParser;
        this.searcher = searcher;
    }

    @Override
    public Object eval(Context ctx, Object... args) {
        Object arg = args[0];
        String queryString = arg.toString();
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        // Query query = queryParserFactory.getParser().parse(queryString);
        Query query = this.searchParser.parse(queryString);
        Search search = new Search();
        search.setLimit(100);
        search.setQuery(query);

        if (args[1] != null && args[1].toString().equals("title")) {
            Sorting sorting = new Sorting();
            sorting.addSortField(new PropertySortField(titlePropDef, SortField.Direction.ASC));
            search.setSorting(sorting);
        }

        ResultSet resultSet = searcher.execute(token, search);
        return resultSet.iterator();
    }

    @Required
    public void setTitlePropDef(PropertyTypeDefinition titlePropDef) {
        this.titlePropDef = titlePropDef;
    }

}
