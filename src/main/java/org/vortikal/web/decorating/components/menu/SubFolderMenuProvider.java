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
package org.vortikal.web.decorating.components.menu;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.Path;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.ResourceTypeTree;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.search.ResultSet;
import org.vortikal.repository.search.Search;
import org.vortikal.repository.search.query.AndQuery;
import org.vortikal.repository.search.query.TermOperator;
import org.vortikal.repository.search.query.TypeTermQuery;
import org.vortikal.repository.search.query.UriDepthQuery;
import org.vortikal.repository.search.query.UriPrefixQuery;
import org.vortikal.web.RequestContext;
import org.vortikal.web.view.components.menu.ListMenu;

public class SubFolderMenuProvider {

    private MenuGenerator menuGenerator;
    private PropertyTypeDefinition sortPropDef;
    private ResourceTypeTree resourceTypeTree;
    private int collectionDisplayLimit = 1000;

    public Map<String, Object> getSubfolderMenuWithGeneratedResultSets(Resource collection, HttpServletRequest request) {
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        ResultSet rs = listCollections(collection.getURI(), requestContext);

        Locale locale = new org.springframework.web.servlet.support.RequestContext(request).getLocale();

        int resultSets = 1;
        if (rs.getSize() > 15) {
            resultSets = 4;
        } else if (rs.getSize() > 8) {
            resultSets = 3;
        } else if (rs.getSize() > 3) {
            resultSets = 2;
        }

        PropertyTypeDefinition sortProperty = getSearchSorting(collection);

        return getSubfolderMenu(rs, collection, token, locale, resultSets, sortProperty);
    }

    public Map<String, Object> getSubfolderMenu(ResultSet rs, Resource collection, String token, Locale locale,
            int resultSets, PropertyTypeDefinition sortProperty) {
        String title = null;
        boolean ascendingSort = true;
        boolean sortByName = false;
        int groupResultSetsBy = 0;
        int freezeAtLevel = 0;
        int depth = 2;
        int displayFromLevel = -1;
        int maxNumberOfChildren = Integer.MAX_VALUE;
        String display = "";
        ArrayList<Path> includeURIs = new ArrayList<Path>();
        int searchLimit = Integer.MAX_VALUE;

        if (sortProperty != null && "name".equals(sortProperty.getName().toString())) {
            sortByName = true;
        }

        MenuRequest menuRequest = this.menuGenerator.getMenuRequest(collection.getURI(), title, sortProperty,
                ascendingSort, sortByName, resultSets, groupResultSetsBy, freezeAtLevel, depth, displayFromLevel,
                maxNumberOfChildren, display, locale, token, searchLimit, includeURIs);

        ListMenu<PropertySet> menu = this.menuGenerator.buildListMenu(rs, menuRequest, null);
        return this.menuGenerator.buildMenuModel(menu, menuRequest);
    }

    protected ResultSet listCollections(Path uri, RequestContext requestContext) {
        AndQuery query = new AndQuery();
        query.add(new UriPrefixQuery(uri.toString()));
        query.add(new UriDepthQuery(uri.getDepth() + 1));
        query.add(new TypeTermQuery("collection", TermOperator.IN));

        Search search = new Search();
        search.setLimit(this.collectionDisplayLimit);
        search.setQuery(query);

        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        ResultSet result = repository.search(token, search);
        return result;
    }

    private PropertyTypeDefinition getSearchSorting(Resource collection) {
        PropertyTypeDefinition sortProp = null;
        if (this.sortPropDef != null && collection.getProperty(this.sortPropDef) != null) {
            String sortString = collection.getProperty(this.sortPropDef).getStringValue();
            sortProp = resourceTypeTree.getPropertyTypeDefinition(Namespace.DEFAULT_NAMESPACE, sortString);
            if (sortProp == null) {
                sortProp = resourceTypeTree.getPropertyTypeDefinition(Namespace.STRUCTURED_RESOURCE_NAMESPACE,
                        sortString);
            }
        }
        return sortProp;
    }

    @Required
    public void setMenuGenerator(MenuGenerator menuGenerator) {
        this.menuGenerator = menuGenerator;
    }

    public void setSortPropDef(PropertyTypeDefinition sortPropDef) {
        this.sortPropDef = sortPropDef;
    }

    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

}
