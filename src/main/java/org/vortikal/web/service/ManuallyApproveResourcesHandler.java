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
package org.vortikal.web.service;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.vortikal.repository.Path;
import org.vortikal.repository.Property;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.repository.search.PropertySortField;
import org.vortikal.repository.search.ResultSet;
import org.vortikal.repository.search.Search;
import org.vortikal.repository.search.SortFieldDirection;
import org.vortikal.repository.search.SortingImpl;
import org.vortikal.repository.search.query.AndQuery;
import org.vortikal.repository.search.query.OrQuery;
import org.vortikal.repository.search.query.Query;
import org.vortikal.repository.search.query.TermOperator;
import org.vortikal.repository.search.query.TypeTermQuery;
import org.vortikal.repository.search.query.UriPrefixQuery;
import org.vortikal.repository.search.query.UriSetQuery;
import org.vortikal.security.SecurityContext;
import org.vortikal.web.RequestContext;
import org.vortikal.web.display.collection.aggregation.AggregationResolver;

public class ManuallyApproveResourcesHandler implements Controller {

    private static final String FOLDERS_PARAM = "folders";

    private Repository repository;
    private PropertyTypeDefinition manuallyApproveFromPropDef;
    private PropertyTypeDefinition manuallyApprovedResourcesPropDef;
    private PropertyTypeDefinition collectionPropDef;
    private PropertyTypeDefinition titlePropDef;
    private PropertyTypeDefinition publishDatePropDef;
    private PropertyTypeDefinition aggregationPropDef;
    private PropertyTypeDefinition recursivePropDef;
    private Map<String, String> listingResourceTypeMappingPointers;
    private AggregationResolver aggregationResolver;
    private Service viewService;

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        Path currentCollectionPath = RequestContext.getRequestContext().getCurrentCollection();
        String token = SecurityContext.getSecurityContext().getToken();
        Resource currentCollection = this.repository.retrieve(token, currentCollectionPath, false);
        Property manuallyApproveFromProp = currentCollection.getProperty(this.manuallyApproveFromPropDef);
        Property manuallyApprovedResourcesProp = currentCollection.getProperty(this.manuallyApprovedResourcesPropDef);
        Property aggregationProp = currentCollection.getProperty(this.aggregationPropDef);
        Property recursiveProp = currentCollection.getProperty(this.recursivePropDef);
        String[] folders = request.getParameterValues(FOLDERS_PARAM);

        // Nothing to work with, need at least one of these
        if (manuallyApproveFromProp == null && manuallyApprovedResourcesProp == null && folders == null) {
            return null;
        }

        List<String> validatedFolders = new ArrayList<String>();

        // Parameter "folders" overrides what's already stored, because user
        // might change content and update service before storing resource
        if (folders != null) {
            for (String folder : folders) {
                if (this.isValidFolder(currentCollectionPath, folder, aggregationProp, recursiveProp)) {
                    validatedFolders.add(folder);
                }
            }
        } else if (manuallyApproveFromProp != null) {
            Value[] manuallyApproveFromValues = manuallyApproveFromProp.getValues();
            for (Value manuallyApproveFromValue : manuallyApproveFromValues) {
                String folder = manuallyApproveFromValue.getStringValue();
                if (this.isValidFolder(currentCollectionPath, folder, aggregationProp, recursiveProp)) {
                    validatedFolders.add(manuallyApproveFromValue.getStringValue());
                }
            }
        }

        // No valid folders to display contents from and no already manually
        // approved resources -> finish
        if (validatedFolders.size() == 0 && manuallyApprovedResourcesProp == null) {
            return null;
        }

        // Step one of headache: resolve aggregation
        Set<String> l = new HashSet<String>();
        for (String folder : validatedFolders) {
            List<Path> aggregatedPaths = this.aggregationResolver.getAggregationPaths(Path.fromString(folder));
            if (aggregatedPaths != null) {
                for (Path aggregatedPath : aggregatedPaths) {
                    l.add(aggregatedPath.toString());
                }
            }
        }
        validatedFolders.addAll(l);

        // Step two of headache: resolve already manually approved resource from
        // folders to manually approve from
        // XXX implement

        OrQuery or = new OrQuery();
        for (String folder : validatedFolders) {
            or.add(new UriPrefixQuery(folder));
        }

        String resourceTypePointer = this.listingResourceTypeMappingPointers.get(currentCollection.getResourceType());
        Query query = null;
        if (resourceTypePointer != null) {
            AndQuery and = new AndQuery();
            and.add(or);
            resourceTypePointer = resourceTypePointer.startsWith("structured.") ? resourceTypePointer.replace(".", "-")
                    : resourceTypePointer;
            and.add(new TypeTermQuery(resourceTypePointer, TermOperator.IN));
            query = and;
        } else {
            query = or;
        }

        if (manuallyApprovedResourcesProp != null) {
            OrQuery q = new OrQuery();
            q.add(query);
            Set<String> uris = new HashSet<String>();
            Value[] manuallyApprovedResources = manuallyApprovedResourcesProp.getValues();
            for (Value manuallyApprovedResource : manuallyApprovedResources) {
                uris.add(manuallyApprovedResource.getStringValue());
            }
            q.add(new UriSetQuery(uris));
            query = q;
        }

        Search search = new Search();
        search.setQuery(query);
        SortingImpl sorting = new SortingImpl();
        sorting.addSortField(new PropertySortField(this.publishDatePropDef, SortFieldDirection.DESC));
        search.setSorting(sorting);
        search.setLimit(1000);

        ResultSet rs = this.repository.search(token, search);

        // XXX We need to consider the following:
        /*
         * The already manually approved resources must be displayed regardless
         * of the result of search for resources to manually approve, i.e. if an
         * already manually approved resource is not among the search result, it
         * must still be displayed (e.g. for removal).
         * 
         * List of resources to manually approve from (search result) must be
         * marked with contents of already approved resources.
         * 
         * Finally, we must enforce a limit as to how many manually approved
         * resources there can be.
         */

        JSONArray arr = new JSONArray();
        for (PropertySet ps : rs.getAllResults()) {
            Property collectionProp = ps.getProperty(this.collectionPropDef);
            if (!collectionProp.getBooleanValue()) {
                JSONObject obj = new JSONObject();
                obj.put("title", ps.getProperty(this.titlePropDef).getStringValue());
                // XXX Complete uri, with protocoll and host. Wait for now...
                // obj.put("uri",
                // viewService.constructURL(ps.getURI()).toString());
                Path resourcePath = ps.getURI();
                obj.put("uri", resourcePath.toString());
                obj.put("source", resourcePath.getParent().toString());
                obj.put("published", this.getPublishDate(ps.getProperty(this.publishDatePropDef)));
                boolean approved = manuallyApprovedResourcesProp == null ? false : this.isAlreadyApproved(resourcePath
                        .toString(), manuallyApprovedResourcesProp);
                obj.put("approved", approved);
                arr.add(obj);
            }
        }
        response.setContentType("text/plain;charset=utf-8");
        PrintWriter writer = response.getWriter();
        writer.print(arr);
        writer.flush();
        writer.close();

        return null;
    }

    private boolean isValidFolder(Path currentCollectionPath, String folder, Property aggregationProp,
            Property recursiveProp) {
        if (StringUtils.isBlank(folder)) {
            return false;
        }
        // XXX Validate absolute uris (http/www)
        Path folderPath = this.getPath(folder);
        if (folderPath == null || folderPath.equals(currentCollectionPath)) {
            return false;
        }
        if ((recursiveProp != null && recursiveProp.getBooleanValue())
                || currentCollectionPath.isAncestorOf(folderPath)) {
            return false;
        }
        if (aggregationProp != null) {
            Value[] aggregationValues = aggregationProp.getValues();
            for (Value aggregationValue : aggregationValues) {
                Path aggregationPath = this.getPath(aggregationValue.toString());
                if (aggregationPath == null) {
                    continue;
                }
                if (folderPath.equals(aggregationPath) || aggregationPath.isAncestorOf(folderPath)) {
                    return false;
                }
            }
        }
        // XXX Ignore if is child of current collection and recursive
        // search is already selected
        return true;
    }

    private Path getPath(String folder) {
        try {
            return Path.fromString(folder);
        } catch (IllegalArgumentException iae) {
            return null;
        }
    }

    private String getPublishDate(Property publishDateProp) {
        Date publishDate = publishDateProp.getDateValue();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(publishDate);
    }

    private boolean isAlreadyApproved(String uriString, Property manuallyApprovedResourcesProp) {
        for (Value manuallyApprovedValue : manuallyApprovedResourcesProp.getValues()) {
            if (uriString.equals(manuallyApprovedValue.getStringValue())) {
                return true;
            }
        }
        return false;
    }

    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    @Required
    public void setManuallyApproveFromPropDef(PropertyTypeDefinition manuallyApproveFromPropDef) {
        this.manuallyApproveFromPropDef = manuallyApproveFromPropDef;
    }

    @Required
    public void setManuallyApprovedResourcesPropDef(PropertyTypeDefinition manuallyApprovedResourcesPropDef) {
        this.manuallyApprovedResourcesPropDef = manuallyApprovedResourcesPropDef;
    }

    @Required
    public void setCollectionPropDef(PropertyTypeDefinition collectionPropDef) {
        this.collectionPropDef = collectionPropDef;
    }

    @Required
    public void setTitlePropDef(PropertyTypeDefinition titlePropDef) {
        this.titlePropDef = titlePropDef;
    }

    @Required
    public void setPublishDatePropDef(PropertyTypeDefinition publishDatePropDef) {
        this.publishDatePropDef = publishDatePropDef;
    }

    @Required
    public void setAggregationPropDef(PropertyTypeDefinition aggregationPropDef) {
        this.aggregationPropDef = aggregationPropDef;
    }

    @Required
    public void setRecursivePropDef(PropertyTypeDefinition recursivePropDef) {
        this.recursivePropDef = recursivePropDef;
    }

    @Required
    public void setAggregationResolver(AggregationResolver aggregationResolver) {
        this.aggregationResolver = aggregationResolver;
    }

    @Required
    public void setViewService(Service viewService) {
        this.viewService = viewService;
    }

    @Required
    public void setListingResourceTypeMappingPointers(Map<String, String> listingResourceTypeMappingPointers) {
        this.listingResourceTypeMappingPointers = listingResourceTypeMappingPointers;
    }

}
