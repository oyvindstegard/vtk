/* Copyright (c) 2009, University of Oslo, Norway
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
package vtk.repository.search.query.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.lucene.index.Term;
import org.apache.lucene.queries.TermFilter;
import org.apache.lucene.queries.TermsFilter;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.BytesRef;
import vtk.repository.index.mapping.AclFields;
import vtk.security.Principal;
import vtk.security.PrincipalFactory;

/**
 * Simple query authorization filter factory which generates filters, but does
 * no caching at all.
 */
public class SimpleQueryAuthorizationFilterFactory extends
        AbstractQueryAuthorizationFilterFactory {

    protected static final Filter ACL_READ_FOR_ALL_FILTER = 
            new TermFilter(new Term(AclFields.AGGREGATED_READ_FIELD_NAME, PrincipalFactory.NAME_ALL));
    
    @Override
    public Filter authorizationQueryFilter(String token, IndexSearcher searcher) {

        if (token == null) {
            // Use a filter which only allows read-for-all documents
            return ACL_READ_FOR_ALL_FILTER;
        }

        Principal principal = getPrincipal(token);

        if (principal == null) {
            // Invalid token (no mapping to principal), return read-for-all only filter
            return ACL_READ_FOR_ALL_FILTER;
        }

        if (isAuthorizedByRole(principal)) {
            return null; // No filter (root-level user)
        }
        
        // Get member groups
        Set<Principal> memberGroups = getPrincipalMemberGroups(principal);
        
        // Build filter for principal and member groups
        return buildACLReadFilter(principal, memberGroups);
    }

    @Override
    public Filter readForAllFilter(IndexSearcher searcher) {
        return ACL_READ_FOR_ALL_FILTER;
    }
    
    private Filter buildACLReadFilter(Principal principal, Set<Principal> memberGroups) {
    
        List<BytesRef> termValues = new ArrayList<BytesRef>(memberGroups.size()+2);
        for (Principal group: memberGroups) {
            termValues.add(new BytesRef(group.getQualifiedName()));
        }

        // Add ALL principal
        termValues.add(new BytesRef(PrincipalFactory.ALL.getQualifiedName()));
        
        // Add principal executing the query
        termValues.add(new BytesRef(principal.getQualifiedName()));
        
        return new TermsFilter(AclFields.AGGREGATED_READ_FIELD_NAME, termValues);
    }
    
}
