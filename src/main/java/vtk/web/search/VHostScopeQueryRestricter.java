/* Copyright (c) 2013, University of Oslo, Norway
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

import java.util.List;

import vtk.repository.Namespace;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.PropertyTypeDefinitionImpl;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.PropertyTermQuery;
import vtk.repository.search.query.Query;
import vtk.repository.search.query.TermOperator;
import vtk.web.service.URL;

public class VHostScopeQueryRestricter {

    public static final PropertyTypeDefinition vHostPropDef =
            PropertyTypeDefinitionImpl.createDefault(Namespace.DEFAULT_NAMESPACE, "vhost", false);

    /**
     * 
     * Restrict a query to a vhost, given by param "vhost"
     */
    public static Query vhostRestrictedQuery(Query original, URL vhost) {
        return vhostRestrictedQuery(original, vhost.getHost());
    }

    public static Query vhostRestrictedQuery(Query original, String vhost) {
        Query vhostQuery = new PropertyTermQuery(vHostPropDef, vhost, TermOperator.EQ);

        if (original instanceof AndQuery) {
            AndQuery and = (AndQuery) original;
            and.add(vhostQuery);
            return and;
        }

        AndQuery and = new AndQuery();
        and.add(original);
        and.add(vhostQuery);
        return and;
    }

    /**
     * 
     * Restrict a query to a given list of vhosts
     */
    public static Query vhostRestrictedQuery(Query original, List<String> vhosts) {

        if (vhosts.size() == 1) {
            return VHostScopeQueryRestricter.vhostRestrictedQuery(original, vhosts.get(0));
        }

        OrQuery vHostOr = new OrQuery();
        for (String vhost : vhosts) {
            vHostOr.add(new PropertyTermQuery(VHostScopeQueryRestricter.vHostPropDef, vhost, TermOperator.EQ));
        }
        AndQuery and = new AndQuery();
        and.add(original);
        and.add(vHostOr);
        return and;

    }

    /**
     * 
     * Check whether or not a list of vhosts contains other hosts than a given
     * one (repositoryId)
     * 
     * @param vhosts
     *            The list of vhosts to check
     * @param repositoryId
     *            The host to check against
     */
    public static boolean isMultiHostSearchRequired(List<String> vhosts, String repositoryId) {
        if (vhosts != null && !vhosts.isEmpty()) {
            if (vhosts.size() == 1) {
                String vhost = vhosts.get(0);
                if (repositoryId != null && repositoryId.equals(vhost)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

}
