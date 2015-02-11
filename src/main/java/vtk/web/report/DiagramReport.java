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
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

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
    private static final String[] webpageTypes = {"structured-article", "structured-event", "person",
        "structured-project", "structured-master", "research-group", "organizational-unit", "contact-supervisor",
        "frontpage", "structured-message", "managed-xml", "html", "php"};

    /*
     * Webpage file types to be listed together as "other".
     */
    private static final String[] otherWebpageTypes = {"apt-resource", "php", "html", "managed-xml", "json-resource"};

    /*
     * File types listed as separate items.
     * "Webpage" and "other" file types will be added as first and last item of this list during runtime.
     */
    private static final String[] fileTypes = {"image", "audio", "video", "pdf", "doc", "ppt", "xls", "text"};

    @Override
    public Map<String, Object> getReportContent(String token, Resource resource, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put(REPORT_NAME, this.getName());
        int total, totalWebpages, files;

        /* Create base URL. */
        Principal p = SecurityContext.getSecurityContext().getPrincipal();
        Service service = RequestContext.getRequestContext().getService();
        URL baseURL = new URL(service.constructURL(resource, p));

        /* Get count and URL for file and folder. */
        try {
            files = doSearch("file", TermOperator.IN, token, resource);
            result.put("files", files);
            result.put("filesURL", new URL(baseURL).addParameter(REPORT_TYPE_PARAM, "fileReporter"));

            int folders = doSearch("collection", TermOperator.IN, token, resource);
            result.put("folders", folders);
            result.put("foldersURL", new URL(baseURL).addParameter(REPORT_TYPE_PARAM, "folderReporter"));

            result.put("firsttotal", files + folders);
        } catch (Exception e) {
            return result;
        }

        /*
         * Get filetypes count and add URL to new search listing up the
         * filetype.
         */
        try {
            total = 0;

            String[] fileTypesAppended = new String[fileTypes.length + 2];
            fileTypesAppended[0] = "webpage";
            System.arraycopy(fileTypes, 0, fileTypesAppended, 1, fileTypes.length);
            fileTypesAppended[fileTypesAppended.length - 1] = "other";
            TermOperator[] t = {null, TermOperator.IN, TermOperator.IN, TermOperator.IN, TermOperator.IN,
                TermOperator.IN, TermOperator.IN, TermOperator.IN, TermOperator.EQ, null};
            int typeCount[] = new int[fileTypesAppended.length];
            URL typeURL[] = new URL[fileTypesAppended.length];

            /*
             * Web pages needs to be handled alone since the search is
             * different.
             */
            typeCount[0] = totalWebpages = webSearch(token, resource);
            typeURL[0] = new URL(baseURL).addParameter(REPORT_TYPE_PARAM, fileTypesAppended[0] + "Reporter");
            total += typeCount[0];

            /*
             * Starting on i = 1 since we have already done webpage and ending
             * on types.length - 1 since we will handle other unique.
             */
            for (int i = 1; i < fileTypesAppended.length - 1; i++) {
                typeCount[i] = doSearch(fileTypesAppended[i], t[i], token, resource);
                typeURL[i] = new URL(baseURL).addParameter(REPORT_TYPE_PARAM, fileTypesAppended[i] + "Reporter");
                total += typeCount[i];
            }

            /* Other is handled unique as we do not need to search for it. */
            typeCount[fileTypesAppended.length - 1] = files - total;
            typeURL[fileTypesAppended.length - 1] = new URL(baseURL).addParameter(REPORT_TYPE_PARAM,
                    fileTypesAppended[fileTypesAppended.length - 1] + "Reporter");
            total += typeCount[fileTypesAppended.length - 1];

            result.put("types", fileTypesAppended);
            result.put("typeCount", typeCount);
            result.put("typeURL", typeURL);

            result.put("secondtotal", total);
        } catch (Exception e) {
            return result;
        }

        /*
         * Get web page types count and add URL to new search listing up the
         * specific type.
         */
        try {
            total = 0;

            String[] webpageTypesAppended = new String[webpageTypes.length + 1];
            System.arraycopy(webpageTypes, 0, webpageTypesAppended, 0, webpageTypes.length);
            webpageTypesAppended[webpageTypesAppended.length - 1] = "webOther";
            int typeCount[] = new int[webpageTypesAppended.length];
            URL typeURL[] = new URL[webpageTypesAppended.length];

            for (int i = 0; i < webpageTypesAppended.length - 1; i++) {
                typeCount[i] = doSearch(webpageTypesAppended[i], TermOperator.IN, token, resource);
                typeURL[i] = new URL(baseURL).addParameter(REPORT_TYPE_PARAM, webpageTypesAppended[i] + "Reporter");
                total += typeCount[i];
            }

            /* webOther is handled unique as we do not need to search for it. */
            typeCount[webpageTypesAppended.length - 1] = totalWebpages - total;
            typeURL[webpageTypesAppended.length - 1] = new URL(baseURL).addParameter(REPORT_TYPE_PARAM,
                    webpageTypesAppended[webpageTypesAppended.length - 1] + "Reporter");
            total += typeCount[webpageTypesAppended.length - 1];

            result.put("webTypes", webpageTypesAppended);
            result.put("webTypeCount", typeCount);
            result.put("webTypeURL", typeURL);

            result.put("thirdtotal", total);
        } catch (Exception e) {
        }

        return result;
    }

    private int webSearch(String token, Resource resource) {
        Search search = new Search();
        AndQuery mainQuery = new AndQuery();

        OrQuery orPart = new OrQuery();
        for (String type : otherWebpageTypes) {
            orPart.add(new TypeTermQuery(type, TermOperator.IN));

        }

        mainQuery.add(orPart);

        /* In current resource but not in /vrtx. */
        mainQuery.add(new UriPrefixQuery(resource.getURI().toString(), false));
        mainQuery.add(new UriPrefixQuery("/vrtx", true));

        /* Include unpublished */
        search.clearAllFilterFlags();

        search.setQuery(mainQuery);
        search.setLimit(1);

        return this.searcher.execute(token, search).getTotalHits();
    }

    private int doSearch(String type, TermOperator t, String token, Resource resource) {
        Search search = new Search();
        AndQuery mainQuery = new AndQuery();

        /* In current resource but not in /vrtx. */
        UriPrefixQuery upq = new UriPrefixQuery(resource.getURI().toString(), false);
        upq.setIncludeSelf(false);

        mainQuery.add(new TypeTermQuery(type, t));
        mainQuery.add(upq);
        mainQuery.add(new UriPrefixQuery("/vrtx", true));

        /* Include unpublished */
        search.clearAllFilterFlags();

        search.setQuery(mainQuery);
        search.setLimit(1);

        return this.searcher.execute(token, search).getTotalHits();
    }

}
