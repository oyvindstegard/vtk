/* Copyright (c) 2010, University of Oslo, Norway
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

import java.util.LinkedHashMap;
import java.util.Map.Entry;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.PropertySortField;
import vtk.repository.search.Search;
import vtk.repository.search.SortField;
import vtk.repository.search.Sorting;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.TypeTermQuery;
import vtk.repository.search.query.UriPrefixQuery;

public class OtherReporter extends DocumentReporter {

    private PropertyTypeDefinition titlePropDef;
    private PropertyTypeDefinition sortPropDef;
    private SortField.Direction sortOrder;
    private LinkedHashMap<String, TermOperator> baseWebpageTypes;
    private LinkedHashMap<String, TermOperator> fileTypes;

    @Override
    protected Search getSearch(String token, Resource currentResource, HttpServletRequest request) {
        AndQuery q = new AndQuery();

        q.add(new TypeTermQuery("file", TermOperator.IN));

        for (Entry<String, TermOperator> entry : fileTypes.entrySet()) {
            q.add(new TypeTermQuery(entry.getKey(), oppositeTermOperator(entry.getValue())));
        }
        for (Entry<String, TermOperator> entry : baseWebpageTypes.entrySet()) {
            q.add(new TypeTermQuery(entry.getKey(), oppositeTermOperator(entry.getValue())));
        }

        /* In current resource but not in /vrtx. */
        UriPrefixQuery upq = new UriPrefixQuery(currentResource.getURI().toString(), false);
        upq.setIncludeSelf(false);
        q.add(upq);
        q.add(new UriPrefixQuery("/vrtx", true));

        Search search = new Search();
        Sorting sorting = new Sorting();
        sorting.addSortField(new PropertySortField(sortPropDef, sortOrder));
        search.setSorting(sorting);
        search.setQuery(q);

        /* Include unpublished */
        search.clearAllFilterFlags();

        return search;
    }

    private TermOperator oppositeTermOperator(TermOperator termOperator) {
        if (termOperator.equals(TermOperator.IN)) {
            return TermOperator.NI;
        } else if (termOperator.equals(TermOperator.EQ)) {
            return TermOperator.NE;
        } else {
            return termOperator;
        }
    }

    public void setTitlePropDef(PropertyTypeDefinition titlePropDef) {
        this.titlePropDef = titlePropDef;
    }

    public PropertyTypeDefinition getTitlePropDef() {
        return titlePropDef;
    }

    @Required
    public void setSortPropDef(PropertyTypeDefinition sortPropDef) {
        this.sortPropDef = sortPropDef;
    }

    @Required
    public void setSortOrder(SortField.Direction sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Required
    public void setFileTypes(LinkedHashMap<String, TermOperator> fileTypes) {
        this.fileTypes = fileTypes;
    }

    @Required
    public void setBaseWebpageTypes(LinkedHashMap<String, TermOperator> baseWebpageTypes) {
        this.baseWebpageTypes = baseWebpageTypes;
    }

}
