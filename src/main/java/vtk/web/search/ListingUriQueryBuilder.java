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
        Property recursiveProp = (recursivePropDef != null) ?
                collection.getProperty(this.recursivePropDef) : null;
        Property subfolderProp = (subfolderPropDef != null) ?
                collection.getProperty(this.subfolderPropDef) : null;

        RecursionType recursionType = getRecursionType(recursiveProp);

        Path collectionUri = collection.getURI();
        // The default query, simple uri match on the current resource
        UriPrefixQuery uriPrefixQuery = new UriPrefixQuery(collectionUri.toString());
        Query query = uriPrefixQuery;
        if (recursionType. equals(RecursionType.SELECTED) && subfolderProp != null) {
            Set<String> set = new HashSet<>();
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
                    OrQuery orQuery = new OrQuery();
                    for (String s : set) {
                        orQuery.add(new UriPrefixQuery(s));
                    }
                    query = orQuery;
                }
            }
        } else if (recursionType.equals(RecursionType.SELF)) {
            // Only show content from this collection
            AndQuery andQuery = new AndQuery();
            UriDepthQuery uriDepthQuery = new UriDepthQuery(collectionUri.getDepth() + 1);
            andQuery.add(uriPrefixQuery);
            andQuery.add(uriDepthQuery);
            query = andQuery;
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

    protected RecursionType getRecursionType(Property recursiveProp) {
        RecursionType type = (defaultRecursive) ? RecursionType.RECURSION : RecursionType.SELF;
        if (recursiveProp != null) {
            switch (recursiveProp.getStringValue()) {
                case "selected":
                    type = RecursionType.SELECTED;
                    break;
                case "true":
                    type = RecursionType.RECURSION;
                    break;
                case "false":
                    type = RecursionType.SELF;
                    break;
            }
        }
        return type;
    }

    protected enum RecursionType {
        RECURSION, SELF, SELECTED
    }

}
