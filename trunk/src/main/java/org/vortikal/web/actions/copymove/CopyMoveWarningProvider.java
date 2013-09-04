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
package org.vortikal.web.actions.copymove;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.Acl;
import org.vortikal.repository.Path;
import org.vortikal.repository.Privilege;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.search.PropertySelect;
import org.vortikal.repository.search.ResultSet;
import org.vortikal.repository.search.Search;
import org.vortikal.repository.search.Searcher;
import org.vortikal.repository.search.query.ACLExistsQuery;
import org.vortikal.repository.search.query.ACLInheritedFromQuery;
import org.vortikal.repository.search.query.ACLQuery;
import org.vortikal.repository.search.query.AndQuery;
import org.vortikal.repository.search.query.OrQuery;
import org.vortikal.repository.search.query.UriPrefixQuery;
import org.vortikal.security.PrincipalFactory;
import org.vortikal.web.RequestContext;
import org.vortikal.web.referencedata.ReferenceDataProvider;
import org.vortikal.web.service.Service;
import org.vortikal.web.service.URL;

public class CopyMoveWarningProvider implements ReferenceDataProvider {

    private Service confirmationService;
    private Searcher searcher;

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();

        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        CopyMoveSessionBean sessionBean = (CopyMoveSessionBean) session
                .getAttribute(CopyMoveToSelectedFolderController.COPYMOVE_SESSION_ATTRIBUTE);
        if (sessionBean == null) {
            return;
        }

        Path destinationUri = requestContext.getCurrentCollection();
        Path sourceParentUri = findSourceParentUri(sessionBean);
        if (sourceParentUri == null) {
            return;
        }

        Resource srcAclResource = findNearestAcl(requestContext, sourceParentUri);
        Resource destAclResource = findNearestAcl(requestContext, destinationUri);
        Acl srcAcl = srcAclResource.getAcl();
        Acl destAcl = destAclResource.getAcl();
        
        URL confirmURL = this.confirmationService.constructURL(destinationUri);
        
        if ("copy-resources".equals(sessionBean.getAction())) {
            if (!(destAcl.hasPrivilege(Privilege.READ, PrincipalFactory.ALL) || destAcl.hasPrivilege(
                    Privilege.READ_PROCESSED, PrincipalFactory.ALL))) {
                return;
            }
            // XXX index search can be optimized to avoid iterating resourceset
            ResultSet rs = indexAclSearch(sessionBean, token, new ACLExistsQuery(), false);
            if (rs.getSize() > 0) {
                for (PropertySet ps : rs.getAllResults()) {
                    Resource resource = repository.retrieve(token, ps.getURI(), false);
                    Acl acl = resource.getAcl();
                    if (!(acl.hasPrivilege(Privilege.READ, PrincipalFactory.ALL) || acl.hasPrivilege(
                            Privilege.READ_PROCESSED, PrincipalFactory.ALL))) {
                        addWarning(model, confirmURL, sessionBean);
                        break;
                    }
                }
            }
        }

        if (sourceParentUri.equals(destinationUri)) {
            // Copying/moving within same folder
            return;
        }

        if (srcAcl.hasPrivilege(Privilege.READ, PrincipalFactory.ALL)
                || srcAcl.hasPrivilege(Privilege.READ_PROCESSED, PrincipalFactory.ALL)) {
            return;
        }

        if (!(destAcl.hasPrivilege(Privilege.READ, PrincipalFactory.ALL) || destAcl.hasPrivilege(
                Privilege.READ_PROCESSED, PrincipalFactory.ALL))) {
            return;
        }

        if (srcAclResource.getURI().equals(destAclResource.getURI())) {
            return;
        }

        ResultSet rs = indexAclSearch(sessionBean, token, new ACLInheritedFromQuery(srcAclResource.getURI()), true);
        if (rs.getSize() > 0) {
            addWarning(model, confirmURL, sessionBean);
        }

    }

    private void addWarning(Map<String, Object> model, URL url, CopyMoveSessionBean sessionBean) {
        model.put("resourcesDisclosed", Boolean.TRUE);
        model.put("warningDialogURL", url);
        model.put("action", sessionBean.getAction());
    }

    private ResultSet indexAclSearch(CopyMoveSessionBean sessionBean, String token, ACLQuery aclTypeQuery,
            boolean setLimit) {
        OrQuery orQuery = new OrQuery();
        for (String uri : sessionBean.getFilesToBeCopied()) {
            orQuery.add(new UriPrefixQuery(uri));
        }

        AndQuery andQuery = new AndQuery();
        andQuery.add(orQuery);
        andQuery.add(aclTypeQuery);

        Search search = new Search();
        search.setSorting(null);
        search.setQuery(andQuery);
        if (setLimit) {
            search.setLimit(1);
        }
        search.setPropertySelect(PropertySelect.NONE);
        return this.searcher.execute(token, search);
    }

    private Resource findNearestAcl(RequestContext requestContext, Path uri) throws Exception {
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Resource resource = repository.retrieve(token, uri, false);
        if (resource.isInheritedAcl()) {
            return findNearestAcl(requestContext, uri.getParent());
        }
        return resource;
    }

    private Path findSourceParentUri(CopyMoveSessionBean sessionBean) {
        List<String> filesToBeCopied = sessionBean.getFilesToBeCopied();
        if (filesToBeCopied == null || filesToBeCopied.isEmpty()) {
            return null;
        }
        return Path.fromString(filesToBeCopied.get(0)).getParent();
    }

    @Required
    public void setConfirmationService(Service confirmationService) {
        this.confirmationService = confirmationService;
    }

    @Required
    public void setSearcher(Searcher searcher) {
        this.searcher = searcher;
    }
}
