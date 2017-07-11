/* Copyright (c) 2004, University of Oslo, Norway
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
package vtk.web.referencedata.provider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.LocaleResolver;

import vtk.repository.Acl;
import vtk.repository.Path;
import vtk.repository.Privilege;
import vtk.repository.Repository;
import vtk.repository.RepositoryAction;
import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.security.PrincipalFactory;
import vtk.security.PrincipalImpl;
import vtk.util.repository.DocumentPrincipalMetadataRetriever;
import vtk.web.RequestContext;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.service.Service;
import vtk.web.service.URL;

/**
 * Model builder that retrieves various Access Control List (ACL) information
 * for the current resource. The information is made available in the model as a
 * sub model with name <code>aclInfo</code>.
 * 
 * Configurable properties:
 * <ul>
 * <li><code>repository</code> - the {@link Repository} is required
 * <li> <code>aclInheritanceService</code> - service for editing the 'inherited
 * property' of the ACL for a resource
 * <li> <code>aclEditServices</code> - map from privileges to editing services
 * <li> <code>modelName</code> - name of the sub-model provided
 * </ul>
 * 
 * Model data provided in the sub-model:
 * <ul>
 * inheritance editing service
 * <li><code>aclEditURLs</code> - map from {@link RepositoryAction actions} to
 * edit URLs
 * <li><code>privileges</code> - map from {@link Privilege#getName privilege
 * names} to {@link Privilege privilege objects}
 * <li><code>inherited</code> - whether or not the ACL of the current resource
 * is inherited
 * <li><code>privilegedPseudoPrincipals</code> - map from privileges to a list
 * of pseudo principals (from the ACL)
 * <li><code>privilegedUsers</code> - map from privileges to a list of user
 * principals (from the ACL)
 * <li><code>privilegedGroups</code> - map from privileges to a list of groups
 * (from the ACL)
 * </ul>
 */
public class ACLProvider implements ReferenceDataProvider {

    private static final String GROUP_PREFIX = "group:";
    private static final String USER_PREFIX = "user:";
    private static final String MODEL_NAME = "aclInfo";

    private Service aclInheritanceService = null;
    private Map<Privilege, Service> aclEditServices;
    private Map<Privilege, List<String>> permissionShortcuts;
    private Map<String, List<String>> permissionShortcutsConfig;
    private LocaleResolver localeResolver;
    private DocumentPrincipalMetadataRetriever documentPrincipalMetadataRetriever;
    private PrincipalFactory principalFactory;

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) {

        Map<String, Object> aclModel = new HashMap<>();

        RequestContext requestContext = RequestContext.getRequestContext(request);
        Repository repository = requestContext.getRepository();
        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();

        try {
            Resource resource = repository.retrieve(token, uri, false);
            Acl acl = resource.getAcl();
            Map<String, String> editURLs = new HashMap<>();

            final Locale preferredLocale = this.localeResolver.resolveLocale(request);

            if (!resource.isInheritedAcl()) {
                for (Privilege action : this.aclEditServices.keySet()) {
                    String privilegeName = action.getName();
                    Service editService = this.aclEditServices.get(action);
                    try {
                        URL url = editService.urlConstructor(URL.create(request))
                                .withResource(resource)
                                .withPrincipal(requestContext.getPrincipal())
                                .constructURL();
                        
                        editURLs.put(privilegeName, url.toString());
                    }
                    catch (Exception e) { }
                }
            }

            try {
                if (this.aclInheritanceService != null) {
                    URL url = aclInheritanceService.urlConstructor(URL.create(request))
                            .withResource(resource)
                            .withPrincipal(requestContext.getPrincipal())
                            .constructURL();
                    editURLs.put("inheritance", url.toString());
                }
            }
            catch (Exception e) { }
            Map<String, Privilege> privileges = new HashMap<>();
            Map<String, Principal[]> privilegedUsers = new HashMap<>();
            Map<String, Principal[]> privilegedGroups = new HashMap<>();
            Map<String, List<Principal>> privilegedPseudoPrincipals = new HashMap<>();
            Map<String, String> viewShortcuts = new HashMap<>();

            // Check if exact match with a shortcut
            // TODO: refactor with some of code in ACLEditController ->
            // extractAndCheckShortcuts()
            for (Privilege action : Privilege.values()) {
                String actionName = action.getName();

                // Load group principals with metadata
                Principal[] groupPrincipals = Arrays.stream(acl.listPrivilegedGroups(action))
                        .map(p -> principalFactory.getPrincipal(p.getQualifiedName(), Principal.Type.GROUP, true, preferredLocale))
                        .toArray(Principal[]::new);
                Principal[] userPrincipals = acl.listPrivilegedUsers(action);
                Principal[] pseudoUserPrincipals = acl.listPrivilegedPseudoPrincipals(action);

                int totalACEs = groupPrincipals.length + userPrincipals.length + pseudoUserPrincipals.length;

                List<String> shortcuts = permissionShortcuts.get(action);
                String shortcutMatch = "";

                if (shortcuts != null) {
                    for (String shortcut : shortcuts) {
                        List<String> shortcutACEs = permissionShortcutsConfig.get(shortcut);
                        int numberOfShortcutACEs = shortcutACEs.size();
                        int matchedACEs = 0;

                        for (String aceWithPrefix : shortcutACEs) {
                            if (aceWithPrefix.startsWith(GROUP_PREFIX)) {
                                for (Principal group : groupPrincipals) {
                                    if ((GROUP_PREFIX + group.getName()).equals(aceWithPrefix)) {
                                        matchedACEs++;
                                        break;
                                    }
                                }
                            } else if (aceWithPrefix.startsWith(USER_PREFIX)) {
                                for (Principal user : userPrincipals) {
                                    if ((USER_PREFIX + user.getName()).equals(aceWithPrefix)) {
                                        matchedACEs++;
                                        break;
                                    }
                                }
                                for (Principal pseudoUser : pseudoUserPrincipals) {
                                    if ((USER_PREFIX + pseudoUser.getName()).equals(aceWithPrefix)) {
                                        matchedACEs++;
                                        break;
                                    }
                                }
                            }

                        }
                        if (matchedACEs == totalACEs && matchedACEs == numberOfShortcutACEs) {
                            shortcutMatch = shortcut;
                            break;
                        }
                    }
                }

                privilegedGroups.put(actionName, groupPrincipals);

                // Add document urls to principals where available
                if (this.documentPrincipalMetadataRetriever.isDocumentSearchConfigured()) {
                    Set<Principal> principalDocuments = this.documentPrincipalMetadataRetriever.getPrincipalDocuments(
                            Arrays.asList(userPrincipals), preferredLocale);
                    if (principalDocuments != null) {
                        for (Principal p : userPrincipals) {
                            for (Principal pd : principalDocuments) {
                                if (pd.equals(p)) {
                                    ((PrincipalImpl) p).setURL(pd.getURL());
                                }
                            }
                        }
                    }
                }

                List<Principal> principals = Arrays.asList(userPrincipals);
                Collections.sort(principals, Principal.PRINCIPAL_NAME_COMPARATOR);

                Principal[] userPrincipalsWithDocs = new Principal[principals.size()];
                privilegedUsers.put(actionName, principals.toArray(userPrincipalsWithDocs));

                privilegedPseudoPrincipals.put(actionName, new ArrayList<>(Arrays.asList(pseudoUserPrincipals)));
                viewShortcuts.put(actionName, shortcutMatch);

                privileges.put(actionName, action);
            }

            aclModel.put("aclEditURLs", editURLs);
            aclModel.put("privileges", privileges);
            aclModel.put("inherited", resource.isInheritedAcl());
            aclModel.put("privilegedGroups", privilegedGroups);
            aclModel.put("privilegedUsers", privilegedUsers);
            aclModel.put("privilegedPseudoPrincipals", privilegedPseudoPrincipals);
            aclModel.put("shortcuts", viewShortcuts);

            model.put(MODEL_NAME, aclModel);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    @Required
    public void setAclInheritanceService(Service aclInheritanceService) {
        this.aclInheritanceService = aclInheritanceService;
    }

    @Required
    public void setAclEditServices(Map<Privilege, Service> aclEditServices) {
        this.aclEditServices = aclEditServices;
    }

    @Required
    public void setPermissionShortcuts(Map<Privilege, List<String>> permissionShortcuts) {
        this.permissionShortcuts = permissionShortcuts;
    }

    @Required
    public void setPermissionShortcutsConfig(Map<String, List<String>> permissionShortcutsConfig) {
        this.permissionShortcutsConfig = permissionShortcutsConfig;
    }

    @Required
    public void setLocaleResolver(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    @Required
    public void setDocumentPrincipalMetadataRetriever(
            DocumentPrincipalMetadataRetriever documentPrincipalMetadataRetriever) {
        this.documentPrincipalMetadataRetriever = documentPrincipalMetadataRetriever;
    }

    @Required
    public void setPrincipalFactory(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }

}
