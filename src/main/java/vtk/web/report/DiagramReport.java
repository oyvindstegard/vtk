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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Resource;
import vtk.repository.search.Search;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.TypeTermQuery;
import vtk.repository.search.query.UriPrefixQuery;
import vtk.security.Principal;
import vtk.security.SecurityContext;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class DiagramReport extends AbstractReporter {

    /*
     * Webpage file types listed as separate items.
     * "Other" webpage types will be added as last item during runtime.
     */
    private LinkedHashMap<String, TermOperator> webpageTypes;

    /* Webpage file types to be listed together as "other". */
    private LinkedHashMap<String, TermOperator> baseWebpageTypes;

    /*
     * File types listed as separate items.
     * "Webpage" and "other" file types will be added as first and last item of this list during runtime.
     */
    private LinkedHashMap<String, TermOperator> fileTypes;

    @Override
    public Map<String, Object> getReportContent(HttpServletRequest request, String token, Resource resource) {
        Map<String, Object> result = new HashMap<>();
        result.put(REPORT_NAME, this.getName());
        int total, totalWebpages, files;

        /* Create base URL. */
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Principal p = SecurityContext.getSecurityContext().getPrincipal();

        Service service = RequestContext.getRequestContext(request).getService();
        
        URL baseURL = service.urlConstructor(requestContext.getRequestURL())
                .withResource(resource)
                .withPrincipal(p)
                .constructURL();

        /* Get count and URL for file and folder. */
        try {
            files = doSearch("file", TermOperator.IN, token, resource);
            result.put("files", files);
            result.put("filesURL", new URL(baseURL).addParameter(REPORT_TYPE_PARAM, "fileReporter"));

            int folders = doSearch("collection", TermOperator.IN, token, resource);
            result.put("folders", folders);
            result.put("foldersURL", new URL(baseURL).addParameter(REPORT_TYPE_PARAM, "folderReporter"));

            result.put("firsttotal", files + folders);
        }
        catch (Exception e) {
            return result;
        }

        /* Get filetypes count and add URL to new search listing up the filetype. */
        try {
            total = 0;

            String[] fileTypesArray = new String[fileTypes.size() + 2];
            int typeCount[] = new int[fileTypesArray.length];
            URL typeURL[] = new URL[fileTypesArray.length];

            /* Webpages needs to be handled alone since the search is different. */
            fileTypesArray[0] = "webpage";
            typeCount[0] = totalWebpages = webSearch(token, resource);
            typeURL[0] = new URL(baseURL).addParameter(REPORT_TYPE_PARAM, fileTypesArray[0] + "Reporter");
            total += typeCount[0];

            /* Starting on i = 1 since we have already done webpage as first item. */
            int i = 1;
            for (Entry<String, TermOperator> entry : fileTypes.entrySet()) {
                fileTypesArray[i] = entry.getKey();
                typeCount[i] = doSearch(entry.getKey(), entry.getValue(), token, resource);
                typeURL[i] = new URL(baseURL).addParameter(REPORT_TYPE_PARAM, entry.getKey() + "Reporter");
                total += typeCount[i++];
            }

            /* Other is appended as last item and handled uniquely as we do not need to search for it. */
            fileTypesArray[fileTypesArray.length - 1] = "other";
            typeCount[fileTypesArray.length - 1] = files - total;
            typeURL[fileTypesArray.length - 1] = new URL(baseURL).addParameter(REPORT_TYPE_PARAM,
                    fileTypesArray[fileTypesArray.length - 1] + "Reporter");
            total += typeCount[fileTypesArray.length - 1];

            result.put("types", fileTypesArray);
            result.put("typeCount", typeCount);
            result.put("typeURL", typeURL);

            result.put("secondtotal", total);
        }
        catch (Exception e) {
            return result;
        }

        /* Get web page types count and add URL to new search listing up the specific type. */
        try {
            total = 0;

            String[] webpageTypesArray = new String[webpageTypes.size() + 1];
            int typeCount[] = new int[webpageTypesArray.length];
            URL typeURL[] = new URL[webpageTypesArray.length];

            int i = 0;
            for (Entry<String, TermOperator> entry : webpageTypes.entrySet()) {
                webpageTypesArray[i] = entry.getKey();
                typeCount[i] = doSearch(entry.getKey(), entry.getValue(), token, resource);
                typeURL[i] = new URL(baseURL).addParameter(REPORT_TYPE_PARAM, entry.getKey() + "Reporter");
                total += typeCount[i++];
            }

            /* webOther is handled unique as we do not need to search for it. */
            webpageTypesArray[webpageTypesArray.length - 1] = "webOther";
            typeCount[webpageTypesArray.length - 1] = totalWebpages - total;
            typeURL[webpageTypesArray.length - 1] = new URL(baseURL).addParameter(REPORT_TYPE_PARAM,
                    webpageTypesArray[webpageTypesArray.length - 1] + "Reporter");
            total += typeCount[webpageTypesArray.length - 1];

            result.put("webTypes", webpageTypesArray);
            result.put("webTypeCount", typeCount);
            result.put("webTypeURL", typeURL);

            result.put("thirdtotal", total);
        }
        catch (Exception e) { }

        return result;
    }

    private int webSearch(String token, Resource resource) {
        Search search = new Search();
        AndQuery mainQuery = new AndQuery();

        OrQuery orPart = new OrQuery();
        for (Entry<String, TermOperator> entry : baseWebpageTypes.entrySet()) {
            orPart.add(new TypeTermQuery(entry.getKey(), entry.getValue()));
        }

        mainQuery.add(orPart);

        /* In current resource but not in /vrtx. */
        mainQuery.add(new UriPrefixQuery(resource.getURI().toString(), false));
        mainQuery.add(new UriPrefixQuery("/vrtx", true));

        /* Include unpublished */
        search.clearAllFilterFlags();
        search.setSorting(null);
        search.setQuery(mainQuery);
        search.setLimit(0);

        return this.searcher.execute(token, search).getTotalHits();
    }

    private int doSearch(String type, TermOperator t, String token, Resource resource) {
        Search search = new Search();
        AndQuery mainQuery = new AndQuery();

        /* In current resource but not in /vrtx. */
        UriPrefixQuery upq = new UriPrefixQuery(resource.getURI().toString(), false, false);

        mainQuery.add(new TypeTermQuery(type, t));
        mainQuery.add(upq);
        mainQuery.add(new UriPrefixQuery("/vrtx", true));

        /* Include unpublished */
        search.clearAllFilterFlags();
        search.setSorting(null);
        search.setQuery(mainQuery);
        search.setLimit(0);

        return this.searcher.execute(token, search).getTotalHits();
    }

    @Required
    public void setWebpageTypes(LinkedHashMap<String, TermOperator> webpageTypes) {
        this.webpageTypes = webpageTypes;
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
