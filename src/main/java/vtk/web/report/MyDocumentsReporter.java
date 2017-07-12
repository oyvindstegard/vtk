/* Copyright (c) 2010,2013,2014, University of Oslo, Norway
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
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.PropertySortField;
import vtk.repository.search.Search;
import vtk.repository.search.SortField;
import vtk.repository.search.Sorting;
import vtk.repository.search.query.AclExistsQuery;
import vtk.repository.search.query.AclPrivilegeQuery;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.PropertyTermQuery;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.UriPrefixQuery;
import vtk.security.Principal;
import vtk.security.SecurityContext;
import vtk.web.service.URL;

public class MyDocumentsReporter extends DocumentReporter {

    public enum ReportSubTypeEnum {
        CreatedBy,
        DirectAcl
    }

    public class ReportSubType {
        private boolean isActive;
        private String name;
        private String url;

        public boolean isActive() {
            return isActive;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }

        private void setActive(boolean isActive) {
            this.isActive = isActive;
        }

        private void setName(String name) {
            this.name = name;
        }

        private void setUrl(String url) {
            this.url = url;
        }
    }

    public static final ReportSubTypeEnum STANDARD_REPORT_SUB_TYPE = ReportSubTypeEnum.CreatedBy;
    public static final String REPORT_SUB_TYPE_REQ_ARG = "report-subtype";
    public static final String REPORT_SUB_TYPE_MAP_KEY = "subtype";

    private PropertyTypeDefinition createdByPropDef;
    private PropertyTypeDefinition sortPropDef;
    private SortField.Direction sortOrder;

    @Override
    protected Search getSearch(String token, Resource currentResource, HttpServletRequest request) {

        Principal currentUser = SecurityContext.getSecurityContext().getPrincipal();
        if (currentUser == null) {
            throw new IllegalStateException("Current user cannot be null");
        }

        Search search = new Search();
        AndQuery query = new AndQuery();
        query.add(new UriPrefixQuery(currentResource.getURI().toString()));
        switch (guessSubType(request)) {
        case CreatedBy:
            query.add(new PropertyTermQuery(this.createdByPropDef, currentUser.getQualifiedName(), TermOperator.EQ));
            break;
        case DirectAcl:
            query.add(new AclExistsQuery());
            query.add(new AclPrivilegeQuery(null, currentUser));
            break;
        default:
            throw new IllegalStateException("Unknown sub type");
        }
        Sorting sorting = new Sorting();
        sorting.addSortField(new PropertySortField(this.sortPropDef, this.sortOrder));

        /* Include unpublished */
        search.clearAllFilterFlags();

        search.setSorting(sorting);
        search.setQuery(query);
        search.setLimit(DEFAULT_SEARCH_LIMIT);
        return search;
    }

    //made public for testing
    public ReportSubTypeEnum guessSubType(HttpServletRequest request) {
        ReportSubTypeEnum result = STANDARD_REPORT_SUB_TYPE;
        String subTypeParam= request.getParameter(MyDocumentsReporter.REPORT_SUB_TYPE_REQ_ARG);
        if (subTypeParam != null) {
            for (ReportSubTypeEnum subType: ReportSubTypeEnum.values())
            {
                if (subTypeParam.equalsIgnoreCase(subType.name())) {
                    result = subType;
                }
            }
        }
        return result;
    }

    //made public for testing
    //Pass the existing map as a parameter to avoid the overhead of creating a new map and merge
    public void addToMap(Map<String, Object> map, HttpServletRequest request) {
        ArrayList<ReportSubType> reportSubTypes = new ArrayList<>();
        ReportSubTypeEnum currentSubType = guessSubType(request);
        for (ReportSubTypeEnum subType: ReportSubTypeEnum.values())
        {
            ReportSubType reportSubType = new ReportSubType();
            reportSubType.setActive(subType == currentSubType);
            reportSubType.setName(subType.name().toLowerCase());
            reportSubType.setUrl(generateUrl(request, reportSubType.getName()));
            reportSubTypes.add(reportSubType);
        }
        map.put(REPORT_SUB_TYPE_MAP_KEY, reportSubTypes);
    }

    //made public for testing
    public String generateUrl(HttpServletRequest request, String reportSubTypeName) {
        return URL.create(request).setParameter(REPORT_SUB_TYPE_REQ_ARG, reportSubTypeName).toString();
    }

    @Override
    public Map<String, Object> getReportContent(HttpServletRequest request, String token, Resource resource) {
        Map<String, Object> map = super.getReportContent(request, token, resource);
        addToMap(map, request);
        return map;
    }

    @Required
    public void setCreatedByPropDef(PropertyTypeDefinition createdByPropDef) {
        this.createdByPropDef = createdByPropDef;
    }

    @Required
    public void setSortPropDef(PropertyTypeDefinition sortPropDef) {
        this.sortPropDef = sortPropDef;
    }

    @Required
    public void setSortOrder(SortField.Direction sortOrder) {
        this.sortOrder = sortOrder;
    }

}
