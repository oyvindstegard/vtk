/* Copyright (c) 2008, University of Oslo, Norway
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
package vtk.web.templates;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.PropertySet;
import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.ResourceTypeDefinition;
import vtk.repository.search.ConfigurablePropertySelect;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.Searcher;
import vtk.repository.search.Sorting;
import vtk.repository.search.TypedSortField;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.Query;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.TypeTermQuery;
import vtk.repository.search.query.UriDepthQuery;
import vtk.repository.search.query.UriPrefixQuery;

/**
 * Template locator which uses repository search mechanism to locate templates. 
 *
 */
public class RepositorySearchResourceTemplateLocator implements ResourceTemplateLocator {
    
    // Repository searcher used to locate templates.
    private Searcher searcher;
    
    private ResourceTypeTree resourceTypeTree; 
    
    /**
     * @see ResourceTemplateLocator#findTemplates(java.lang.String, java.util.Set, java.util.Set) 
     */
    @Override
    public List<ResourceTemplate> findTemplates(String token, 
                                                Set<Path> baseUris,
                                                Set<ResourceTypeDefinition> resourceTypes) {
        
        return findTemplatesInternal(token, baseUris, resourceTypes, true);
    }
    
    /**
     * @see ResourceTemplateLocator#findTemplatesNonRecursively(java.lang.String, java.util.Set, java.util.Set) 
     */
    @Override
    public List<ResourceTemplate> findTemplatesNonRecursively(String token,
                                                      Set<Path> baseUris, 
                                                      Set<ResourceTypeDefinition> resourceTypes) {
        
        return findTemplatesInternal(token, baseUris, resourceTypes, false);
        
    }
    
    private List<ResourceTemplate> findTemplatesInternal(String token, 
                                                Set<Path> baseUris,
                                                Set<ResourceTypeDefinition> resourceTypes,
                                                boolean recursive) {
        
        List<ResourceTemplate> templates = new ArrayList<ResourceTemplate>();
        
        Search search = new Search();
        search.clearAllFilterFlags();

        Query query = getQuery(baseUris, resourceTypes, recursive);
        
        search.setQuery(query);
        
        // Restrict what properties are loaded from search index (optimization)
        ConfigurablePropertySelect select = new ConfigurablePropertySelect();
        
        PropertyTypeDefinition titlePropDef = 
            this.resourceTypeTree.getPropertyTypeDefinition(
                                Namespace.DEFAULT_NAMESPACE, "title");
        
        // Only title is necessary ..
        select.addPropertyDefinition(titlePropDef);
        
        search.setPropertySelect(select);
        
        Sorting sorting = new Sorting();
        sorting.addSortField(new TypedSortField(PropertySet.NAME_IDENTIFIER));
        search.setSorting(sorting); 
        
        // Do repository search
        ResultSet results = searcher.execute(token, search);
        
        // Iterate results and create list of resource template beans
        for (PropertySet propSet: results.getAllResults()) {
            Path uri = propSet.getURI();
            String name = propSet.getName();
            String title = propSet.getProperty(titlePropDef).getStringValue();
            String resourceType = propSet.getResourceType();

            ResourceTemplate template = new ResourceTemplate(uri, title, name, resourceType);
            templates.add(template);
        }
        return templates;
    }
    
    private Query getQuery(Set<Path> baseUris, 
                           Set<ResourceTypeDefinition> resourceTypes, 
                           boolean recursive) {
        
        if (baseUris == null || baseUris.isEmpty()) {
            throw new IllegalArgumentException("No base URIs provided");
        }
        
        AndQuery query = new AndQuery(); // Top node of query tree
        
        // Add base URIs constraint
        query.add(getBaseUrisQueryNode(baseUris, recursive));
        
        // Add types criteria
        if (resourceTypes != null && resourceTypes.size() > 0) {
            OrQuery typeCriteria = new OrQuery();
            for (ResourceTypeDefinition def: resourceTypes) {
                typeCriteria.add(new TypeTermQuery(def.getName(), TermOperator.IN));
            }
            query.add(typeCriteria);
        }        

        return query;
    }
    
    private Query getBaseUrisQueryNode(Set<Path> baseUris, 
                                       boolean recursive) {
        OrQuery orQuery = new OrQuery();

        if (recursive) {
            for (Path uri: baseUris) {
                orQuery.add(new UriPrefixQuery(uri.toString()));
            }
        } else {
            // Non-recursive, add depth-constraint.
            for (Path uri: baseUris) {
                AndQuery uriPrefixAndDepth = new AndQuery();
                int depth = uri.getDepth() + 1; // Only children
                
                uriPrefixAndDepth.add(new UriPrefixQuery(uri.toString()));
                uriPrefixAndDepth.add(new UriDepthQuery(depth));
                
                orQuery.add(uriPrefixAndDepth);
            }
        }
        
        return orQuery;
    }
    
    @Required
    public void setSearcher(Searcher searcher) {
        this.searcher = searcher;
    }
    
    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }
}
