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

import java.util.HashSet;
import java.util.Set;

import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.Query;
import vtk.repository.search.query.UriDepthQuery;
import vtk.repository.search.query.UriPrefixQuery;

public class ListingUriQueryBuilder {

    private PropertyTypeDefinition recursivePropDef;
    private boolean defaultRecursive;
    private PropertyTypeDefinition subfolderPropDef;

    public Query build(Resource collection) {

        Path collectionUri = collection.getURI();
        Query query = null;

        // The default query, simple uri match on the current resource
        UriPrefixQuery uriPrefixQuery = new UriPrefixQuery(collectionUri.toString());

        Property recursiveProp = recursivePropDef != null ? 
                collection.getProperty(this.recursivePropDef) : null;

        // If explicit subfolders to retrieve from are defined
        Property subfolderProp = null;
        if (this.subfolderPropDef != null) {
            subfolderProp = collection.getProperty(this.subfolderPropDef);
        }
        if (subfolderProp != null && recursiveProp == null) {
            Set<String> set = new HashSet<String>();
            for (Value value : subfolderProp.getValues()) {
                try {
                    String subfolder = value.getStringValue();
                    if (subfolder.startsWith("/")) {
                        // Absolute paths are not allowed!!!
                        continue;
                    }
                    subfolder = subfolder.endsWith("/") ? subfolder.substring(0, subfolder.lastIndexOf("/"))
                            : subfolder;
                    Path subfolderPath = collectionUri.expand(subfolder);
                    subfolder = subfolderPath.toString().concat("/");
                    set.add(subfolder);
                } catch (IllegalArgumentException iae) {
                    // Just continue
                }
            }
            if (set.size() > 0) {
                if (set.size() == 1) {
                    query = new UriPrefixQuery(set.iterator().next());
                } else {
                    OrQuery or = new OrQuery();
                    for (String s : set) {
                        or.add(new UriPrefixQuery(s));
                    }
                    query = or;
                }
            }
        } else {
            // If no recursion is defined, supplement the default query with
            // limited depth when searching
            if (!this.defaultRecursive || (recursiveProp != null && !recursiveProp.getBooleanValue())) {
                AndQuery and = new AndQuery();
                UriDepthQuery uriDepthQuery = new UriDepthQuery(collectionUri.getDepth() + 1);
                and.add(uriPrefixQuery);
                and.add(uriDepthQuery);
                query = and;
            }
        }

        if (query == null) {
            query = uriPrefixQuery;
        }

        return query;
    }

    public void setRecursivePropDef(PropertyTypeDefinition recursivePropDef) {
        this.recursivePropDef = recursivePropDef;
    }

    public void setDefaultRecursive(boolean defaultRecursive) {
        this.defaultRecursive = defaultRecursive;
    }

    public void setSubfolderPropDef(PropertyTypeDefinition subfolderPropDef) {
        this.subfolderPropDef = subfolderPropDef;
    }

}
