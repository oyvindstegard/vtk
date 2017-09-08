/* Copyright (c) 2014, University of Oslo, Norway
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

package vtk.repository.search.query.builders;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import vtk.repository.AuthorizationManager;
import vtk.repository.Privilege;
import vtk.repository.index.mapping.AclFields;
import vtk.repository.index.mapping.Fields;
import vtk.repository.search.query.AclPrivilegeQuery;
import vtk.repository.search.query.LuceneQueryBuilder;
import vtk.repository.search.query.QueryBuilder;
import vtk.repository.search.query.QueryBuilderException;
import vtk.security.Principal;

/**
 * Build Lucene query nodes for {@link AclPrivilegeQuery} instances.
 */
public class AclPrivilegeQueryBuilder implements QueryBuilder {

    private final AclPrivilegeQuery query;
    
    public AclPrivilegeQueryBuilder(AclPrivilegeQuery query) {
        this.query = query;
    }
    
    @Override
    public Query buildQuery() throws QueryBuilderException {

        final Privilege action = query.getPrivilege();
        final String uid = query.getQualifiedName();
        
        Query q;
        final List<String> searchFields = fieldsFor(action, query.isIncludeSuperPrivileges());
        if (uid != null) {
            final BooleanClause.Occur occur = query.isInverted() ? BooleanClause.Occur.MUST_NOT : BooleanClause.Occur.SHOULD;
            // Search for specific uid in all privilege fields
            BytesRef termValue = new BytesRef(uid);
            BooleanQuery.Builder b = new BooleanQuery.Builder();
            for (String field: searchFields) {
                b.add(new TermQuery(new Term(field, termValue)), occur);
            }
            q = b.build();

        } else {
            // Wildcard uid, exists for set of privilege fields
            Collection<BytesRef> terms = searchFields.stream().map(s -> new BytesRef(s)).collect(Collectors.toList());
            q = new TermInSetQuery(Fields.FIELD_NAMES_METAFIELD, terms);
            if (query.isInverted()) {
                q = LuceneQueryBuilder.invert(q);
            }
        }
        
        return q;
    }
    
    // Provide list of search fields for a privilege or null-wildcard 
    private List<String> fieldsFor(Privilege privilege, boolean includeSuperPrivileges) {
        List<String> fieldNames = new ArrayList<String>();
        if (privilege == null) {
            // Any privilege
            for (Privilege p: Privilege.values()) {
                fieldNames.add(AclFields.aceFieldName(p, Principal.Type.USER));
                fieldNames.add(AclFields.aceFieldName(p, Principal.Type.GROUP));
            }
        } else {
            // One or more specific privileges
            if (includeSuperPrivileges) {
                for (Privilege p: AuthorizationManager.superPrivilegesOf(privilege)) {
                    fieldNames.add(AclFields.aceFieldName(p, Principal.Type.USER));
                    fieldNames.add(AclFields.aceFieldName(p, Principal.Type.GROUP));
                }
            } else {
                fieldNames.add(AclFields.aceFieldName(privilege, Principal.Type.USER));
                fieldNames.add(AclFields.aceFieldName(privilege, Principal.Type.GROUP));
            }
        }
        return fieldNames;        
    }
    
}
