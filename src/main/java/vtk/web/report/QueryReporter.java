/* Copyright (c) 2014, University of Oslo, Norway
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
package vtk.web.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.Resource;
import vtk.repository.search.QueryParser;
import vtk.repository.search.Search;
import vtk.repository.search.Sorting;
import vtk.repository.search.query.Query;
import vtk.web.RequestContext;
import vtk.web.service.URL;

public class QueryReporter extends DocumentReporter {
    
    private QueryParser parser;
    private static String DEFAULT_QUERY = "type IN resource";
    
    public QueryReporter(QueryParser parser) {
        super();
        this.parser = parser;
    }
    
    @Override
    public Map<String, Object> getReportContent(String token, Resource resource, HttpServletRequest request) {
        Map<String, Object> model = super.getReportContent(token, resource, request);
        model.put("specificCollectionReporter", true);

        Map<String, Object> form = new HashMap<String, Object>();
        URL action = new URL(RequestContext.getRequestContext().getRequestURL());
        
        String query = action.getParameter("query");
        if (query == null || "".equals(query.trim())) query = DEFAULT_QUERY;
        
        List<Object> inputs = new ArrayList<Object>();
        inputs.add(formElement("query", query, "text"));
        
        for (String name: action.getParameterNames()) {
            if (!"query".equals(name) && !"page".equals(name)) {
                inputs.add(formElement(name, action.getParameter(name), "hidden"));
            }
        }
        inputs.add(formElement("submit", "Update", "submit"));
        form.put("action", action);
        form.put("inputs", inputs);
        model.put("form", form);
        return model;
    }

    @Override
    protected Search getSearch(String token, Resource currentResource,
            HttpServletRequest request) {
        String queryStr = request.getParameter("query");
        if (queryStr == null || "".equals(queryStr.trim())) queryStr = DEFAULT_QUERY;
        
        Search search = new Search();
        Sorting sorting = new Sorting();
        //sorting.addSortField(new PropertySortField(this.sortPropDef, this.sortOrder));
        //search.setSorting(sorting);
        
        Query query = parser.parse(queryStr);
        search.setQuery(query);
        return search;
    }

    private Object formElement(String name, String value, String type) {
        Map<String, Object> element = new HashMap<String, Object>();
        element.put("name", name);
        element.put("value", value);
        element.put("type", type);
        return element;
    }
    
}
