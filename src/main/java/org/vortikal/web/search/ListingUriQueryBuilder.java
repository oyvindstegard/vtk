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
package org.vortikal.web.search;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.Path;
import org.vortikal.repository.Property;
import org.vortikal.repository.Resource;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.repository.search.query.AndQuery;
import org.vortikal.repository.search.query.OrQuery;
import org.vortikal.repository.search.query.Query;
import org.vortikal.repository.search.query.UriDepthQuery;
import org.vortikal.repository.search.query.UriPrefixQuery;
import org.vortikal.repository.search.query.UriSetQuery;
import org.vortikal.web.display.collection.aggregation.AggregationResolver;
import org.vortikal.web.service.URL;

public class ListingUriQueryBuilder implements QueryBuilder {

    private PropertyTypeDefinition recursivePropDef;
    private AggregationResolver aggregationResolver;
    private PropertyTypeDefinition manuallyApprovedResourcesPropDef;
    private boolean defaultRecursive;

    @Override
    public Query build(Resource collection, HttpServletRequest request) {

        Path collectionUri = collection.getURI();
        Query baseQuery = null;

        // The default query, simple uri match on the current resource
        UriPrefixQuery uriPrefixQuery = new UriPrefixQuery(collectionUri.toString());

        // If no recursion is defined, supplement the default query with limited
        // depth when searching
        Property recursiveProp = collection.getProperty(this.recursivePropDef);
        if (!this.defaultRecursive || (recursiveProp != null && !recursiveProp.getBooleanValue())) {
            AndQuery and = new AndQuery();
            UriDepthQuery uriDepthQuery = new UriDepthQuery(collectionUri.getDepth() + 1);
            and.add(uriPrefixQuery);
            and.add(uriDepthQuery);
            baseQuery = and;
        } else {
            baseQuery = uriPrefixQuery;
        }

        // Now check for aggregation and extend the query if aggregation exists
        List<Path> aggregationPaths = this.aggregationResolver.getAggregationPaths(collectionUri);
        if (aggregationPaths != null && aggregationPaths.size() > 0) {
            OrQuery aggregateUriPrefixQuery = new OrQuery();
            for (Path aggregationPath : aggregationPaths) {
                aggregateUriPrefixQuery.add(new UriPrefixQuery(aggregationPath.toString()));
            }
            OrQuery or = new OrQuery();
            or.add(baseQuery);
            or.add(aggregateUriPrefixQuery);
            baseQuery = or;
        }

        // Any manually approved resources? Well then add those as well
        Property manuallyApprovedProp = collection.getProperty(this.manuallyApprovedResourcesPropDef);
        if (manuallyApprovedProp != null) {
            Value[] values = manuallyApprovedProp.getValues();
            Set<String> uriSet = new HashSet<String>();
            for (Value val : values) {
                String uri = val.getStringValue();
                if (uri.startsWith("http")) {
                    URL url = URL.parse(uri);
                    uri = url.getPathRepresentation();
                }
                uriSet.add(uri);
            }
            UriSetQuery uriSetQuery = new UriSetQuery(uriSet);
            OrQuery or = new OrQuery();
            or.add(baseQuery);
            or.add(uriSetQuery);
            baseQuery = or;
        }

        return baseQuery;
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
    public void setManuallyApprovedResourcesPropDef(PropertyTypeDefinition manuallyApprovedResourcesPropDef) {
        this.manuallyApprovedResourcesPropDef = manuallyApprovedResourcesPropDef;
    }

    public void setDefaultRecursive(boolean defaultRecursive) {
        this.defaultRecursive = defaultRecursive;
    }

}
