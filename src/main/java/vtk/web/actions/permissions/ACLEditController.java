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
package vtk.web.actions.permissions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.beans.propertyeditors.StringArrayPropertyEditor;
import org.springframework.validation.BindException;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ModelAndView;

import vtk.repository.Acl;
import vtk.repository.Path;
import vtk.repository.Privilege;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.security.Principal.Type;
import vtk.security.PrincipalFactory;
import vtk.security.PrincipalImpl;
import vtk.security.PrincipalManager;
import vtk.security.roles.RoleManager;
import vtk.util.repository.DocumentPrincipalMetadataRetriever;
import vtk.web.RequestContext;
import vtk.web.SimpleFormController;
import vtk.web.service.Service;

public class ACLEditController extends SimpleFormController<ACLEditCommand> 
    implements InitializingBean {

    private Privilege privilege;
    private PrincipalManager principalManager;
    private PrincipalFactory principalFactory;
    private RoleManager roleManager;
    private LocaleResolver localeResolver;
    private DocumentPrincipalMetadataRetriever documentPrincipalMetadataRetriever;
    private Repository repository;

    private Map<Privilege, List<String>> permissionShortcuts;
    private List<String> shortcuts = null;
    private Map<String, List<String>> permissionShortcutsConfig;
    
    public ACLEditController() {
        setSessionForm(true);
    }

    @Override
    public void afterPropertiesSet() {
        this.shortcuts = this.permissionShortcuts.get(this.privilege);
        if (this.shortcuts == null) {
            this.shortcuts = Collections.emptyList();
        }
        validateShortcuts();
    }

    @Override
    protected void initBinder(HttpServletRequest request, ServletRequestDataBinder binder) throws Exception {
        binder.registerCustomEditor(java.lang.String[].class, new StringArrayPropertyEditor());
    }

    @Override
    protected ServletRequestDataBinder createBinder(HttpServletRequest request, ACLEditCommand command) throws Exception {
        ACLEditBinder binder = new ACLEditBinder(command, getCommandName());
        prepareBinder(binder);
        initBinder(request, binder);
        return binder;
    }

    @Override
    protected ACLEditCommand formBackingObject(HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();
        Resource resource = this.repository.retrieve(token, uri, false);
        Locale preferredLocale = this.localeResolver.resolveLocale(request);
        return getACLEditCommand(resource, resource.getAcl(), requestContext.getPrincipal(), false, false,
                preferredLocale);
    }

    
    @Override
    protected ModelAndView showForm(HttpServletRequest request,
            BindException errors, String viewName, Map<String, Object> model)
            throws Exception {
        request.getSession().setAttribute(getClass().getName() + ".form", errors.getTarget());
        return super.showForm(request, errors, viewName, model);
    }

    private ACLEditCommand getACLEditCommand(Resource resource, Acl acl, Principal principal,
            boolean isCustomPermissions, boolean losingPrivileges, Locale preferredLocale) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Service service = requestContext.getService();

        String submitURL = service.constructLink(resource, principal);
        ACLEditCommand command = new ACLEditCommand(submitURL);

        command.setAcl(acl);
        command.setPrivilege(this.privilege);

        // Load group principals with metadata
        List<Principal> authorizedGroups = Arrays.<Principal>stream(acl.listPrivilegedGroups(this.privilege))
                .<Principal>map(p -> principalFactory.getPrincipal(p.getQualifiedName(), Type.GROUP, true, preferredLocale))
                .collect(Collectors.toList());

        Principal[] privilegedPrincipals = acl.listPrivilegedUsers(this.privilege);

        if (documentPrincipalMetadataRetriever.isDocumentSearchConfigured()) {
            Set<Principal> principalDocuments = documentPrincipalMetadataRetriever.getPrincipalDocuments(
                    Arrays.asList(privilegedPrincipals), preferredLocale);
            for (Principal p : privilegedPrincipals) {
                Principal pd = getPrincipalDoc(p, principalDocuments);
                if (pd != null) {
                    ((PrincipalImpl) p).setURL(pd.getURL());
                }
            }
        }

        List<Principal> authorizedUsers = new ArrayList<Principal>(Arrays.asList(privilegedPrincipals));
        Collections.sort(authorizedUsers, Principal.PRINCIPAL_NAME_COMPARATOR);
        authorizedUsers.addAll(Arrays.asList(acl.listPrivilegedPseudoPrincipals(this.privilege)));

        if (this.shortcuts != null) {
            command.setShortcuts(extractAndCheckShortcuts(authorizedGroups, authorizedUsers, this.shortcuts,
                    this.permissionShortcutsConfig, isCustomPermissions));
        }
        command.setLosingPrivileges(losingPrivileges);
        command.setGroups(authorizedGroups);
        command.setUsers(authorizedUsers);
        return command;
    }

    @Override
    protected ModelAndView processFormSubmission(HttpServletRequest req, HttpServletResponse resp, 
            ACLEditCommand command, BindException errors) throws Exception {
        if (errors.hasErrors()) {
            command.setAddGroupAction(null);
            command.setAddUserAction(null);
            command.setRemoveGroupAction(null);
            command.setRemoveUserAction(null);
            command.setSaveAction(null);
            command.getUserNameEntries().removeAll(command.getUserNameEntries());
            command.setLosingPrivileges(false);
        }
        return super.processFormSubmission(req, resp, command, errors);
    }

    @Override
    protected ModelAndView onSubmit(HttpServletRequest request, HttpServletResponse response, ACLEditCommand command,
            BindException errors) throws Exception {
        Acl acl = command.getAcl();

        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();

        Resource resource = repository.retrieve(token, uri, false);

        // Did the user cancel?
        if (command.getCancelAction() != null) {
            return new ModelAndView(getSuccessView());
        }

        Principal currentPrincipal = requestContext.getPrincipal();
        Locale preferredLocale = this.localeResolver.resolveLocale(request);

        // Has the user asked to save?
        if (command.getSaveAction() != null) {
            acl = updateAclIfShortcut(acl, command, currentPrincipal, errors);
            acl = addToAcl(acl, command.getGroupNames(), Type.GROUP);
            acl = addToAcl(acl, command.getUserNameEntries(), Type.USER, preferredLocale);
            if (errors.hasErrors()) {
                BindException bex = new BindException(getACLEditCommand(resource, acl, currentPrincipal, true, false,
                        preferredLocale), this.getCommandName());
                bex.addAllErrors(errors);
                return showForm(request, errors, getFormView(), errors.getModel());
            }
            resource = repository.storeACL(token, resource.getURI(), acl);
            return new ModelAndView(getSuccessView());
        }

        // Doing remove or add actions
        if (command.getRemoveGroupAction() != null) {
            acl = removeFromAcl(acl, command.getGroupNames(), Type.GROUP, currentPrincipal, errors);
            boolean losingPrivileges = !this.repository.authorize(currentPrincipal, acl, Privilege.ALL);

            BindException bex = new BindException(getACLEditCommand(resource, acl, currentPrincipal, true,
                    losingPrivileges, preferredLocale), this.getCommandName());
            bex.addAllErrors(errors);
            return showForm(request, bex, getFormView(), null);
        } 
        else if (command.getRemoveUserAction() != null) {
            acl = removeFromAcl(acl, command.getUserNames(), Type.USER, currentPrincipal, errors);
            boolean losingPrivileges = !this.repository.authorize(currentPrincipal, acl, Privilege.ALL);
            BindException bex = new BindException(getACLEditCommand(resource, acl, currentPrincipal, true,
                    losingPrivileges, preferredLocale), this.getCommandName());
            bex.addAllErrors(errors);
            return showForm(request, bex, getFormView(), null);
        }
        else if (command.getAddGroupAction() != null) {
            // If not a shortcut and no groups/users in admin, then remove
            // groups/users (typical when coming from a shortcut)
            if (command.getGroups().size() == 0 && command.getUsers().size() == 0) {
                acl = acl.clear(this.privilege);
            }

            acl = addToAcl(acl, command.getGroupNames(), Type.GROUP);
            BindException bex = new BindException(
                    getACLEditCommand(resource, acl, currentPrincipal, true, false, preferredLocale),
                    this.getCommandName());
            return showForm(request, bex, getFormView(), null);
        }
        else if (command.getAddUserAction() != null) {
            // If not a shortcut and no groups/users in admin, then remove
            // groups/users (typical when coming from a shortcut)
            if (command.getGroups().size() == 0 && command.getUsers().size() == 0) {
                acl = acl.clear(this.privilege);
            }
            acl = addToAcl(acl, command.getUserNameEntries(), Type.USER, preferredLocale);
            
            BindException bex = new BindException(
                    getACLEditCommand(resource, acl, currentPrincipal, true, false, preferredLocale),
                    this.getCommandName());
            
            return showForm(request, bex, getFormView(), null);
        }

        return new ModelAndView(getSuccessView());
    }
    


    /**
     * Extracts shortcuts from authorized users and groupse
     * 
     * @param authorizedUsers
     *            the authorized users
     * @param authorizedGroups
     *            the authorized groups
     * @param shortcuts
     *            the configured shortcuts for the privilege
     * @param permissionShortcutsConfig
     *            the users and groups for the shortcuts
     * @return a <code>String[][]</code> object containing checked / not-checked
     *         shortcuts
     */
    protected String[][] extractAndCheckShortcuts(List<Principal> authorizedGroups, List<Principal> authorizedUsers,
            List<String> shortcuts, Map<String, List<String>> permissionShortcutsConfig, boolean isCustomPermissions)
            throws Exception {

        String checkedShortcuts[][] = new String[shortcuts.size()][2];
        int totalACEs = authorizedGroups.size() + authorizedUsers.size();

        // Iterate shortcuts on privilege
        int i = 0;
        for (String shortcut : shortcuts) {
            List<String> shortcutACEs = permissionShortcutsConfig.get(shortcut);
            int numberOfShortcutACEs = shortcutACEs.size();
            int matchedACEs = 0;

            // Find matches in shortcut ACEs
            for (String aceWithPrefix : shortcutACEs) {
                for (Principal group : authorizedGroups) {
                    if ((ACLEditValidationHelper.SHORTCUT_GROUP_PREFIX + group.getName()).equals(aceWithPrefix)) {
                        matchedACEs++;
                    }
                }
                for (Principal user : authorizedUsers) {
                    if ((ACLEditValidationHelper.SHORTCUT_USER_PREFIX + user.getName()).equals(aceWithPrefix)) {
                        matchedACEs++;
                    }
                }
            }

            checkedShortcuts[i][0] = shortcut;

            // If not custom is choosen
            if (!isCustomPermissions) {
                // If matches are exactly the number of authorized groups/users
                // and the number of groups/users in shortcut
                if (matchedACEs == totalACEs && matchedACEs == numberOfShortcutACEs) {
                    checkedShortcuts[i][1] = "checked";
                    // Remove all ACEs (from view)
                    authorizedUsers.clear();
                    authorizedGroups.clear();
                } else {
                    checkedShortcuts[i][1] = "";
                }
            } else {
                checkedShortcuts[i][1] = "";
            }
            i++;
        }

        return checkedShortcuts;
    }

    /**
     * Add and remove ACL entries for updated shortcut
     * 
     * @param acl
     *            the ACL object
     * @param editCommand
     *            the command object
     * @param yourself
     * @param errors
     *            ACL validation errors
     * @return the modified ACL
     */
    private Acl updateAclIfShortcut(Acl acl, ACLEditCommand editCommand, Principal yourself, BindException errors)
            throws Exception {
        String updatedShortcut = editCommand.getUpdatedShortcut();

        if (this.permissionShortcutsConfig.get(updatedShortcut) != null) {
            acl = acl.clear(this.privilege);
            List<String> shortcutACEs = this.permissionShortcutsConfig.get(updatedShortcut);
            for (String principalStr : shortcutACEs) {

                Type type = null;
                if (principalStr.startsWith(ACLEditValidationHelper.SHORTCUT_GROUP_PREFIX)) {
                    type = Type.GROUP;
                    principalStr = principalStr.substring(ACLEditValidationHelper.SHORTCUT_GROUP_PREFIX.length());
                } else if (principalStr.startsWith(ACLEditValidationHelper.SHORTCUT_USER_PREFIX)) {
                    type = Type.USER;
                    principalStr = principalStr.substring(ACLEditValidationHelper.SHORTCUT_USER_PREFIX.length());
                } else {
                    throw new IllegalStateException("Invalid principal string: " + principalStr);
                }
                acl = addToAcl(acl, new String[] { principalStr }, type);
            }
            return acl;
        }

        if (editCommand.getGroups().size() == 0 && editCommand.getUsers().size() == 0) {
            acl = acl.clear(this.privilege);
        }
        return acl;
    }

    /**
     * Remove groups or users from ACL.
     * 
     * @param acl
     *            the ACL object
     * @param values
     *            groups or users to remove
     * @param type
     *            type of ACL (GROUP or USER)
     * @param current
     * @param errors
     *            ACL validation errors
     * @return the modified ACL
     */
    private Acl removeFromAcl(Acl acl, String[] values, Type type, Principal current, BindException errors)
            throws Exception {

        Acl result = acl;

        for (String value : values) {
            Principal userOrGroup = principalFactory.getPrincipal(value,
                    ACLEditValidationHelper.typePseudoUser(type, value));
            Acl potentialAcl = result.removeEntry(this.privilege, userOrGroup);

            if (this.roleManager.hasRole(current, RoleManager.Role.ROOT)) {
                result = potentialAcl;
                continue;
            }
            if (potentialAcl.getPrincipalSet(Privilege.ALL).isEmpty()) {
                String field = userOrGroup.getType() == Type.GROUP ? "groupNames" : "userNames";
                errors.rejectValue(field, "permissions.all.not.empty", "Not possible to remove all admin permissions");
                return result;
            }
            result = potentialAcl;
        }
        return result;
    }

    private Acl addToAcl(Acl acl, String[] values, Type type) throws Exception {
        return addToAcl(acl, Arrays.asList(values), type, null);
    }

    /**
     * Add groups or users to ACL (for getUserNameEntries()).
     * 
     * @param acl
     *            the ACL object
     * @param values
     *            groups or users to remove
     * @param type
     *            type of ACL (GROUP or USER)
     * @return the modified ACL
     */
    private Acl addToAcl(Acl acl, List<String> values, Type type, Locale preferredLocale) throws Exception {

        Set<Principal> principalDocuments = null;
        if (this.documentPrincipalMetadataRetriever.isDocumentSearchConfigured()) {
            principalDocuments = this.documentPrincipalMetadataRetriever.getPrincipalDocumentsByUid(
                    new HashSet<String>(values), preferredLocale);
        }

        for (String value : values) {

            Principal principal = principalFactory.getPrincipal(value,
                    ACLEditValidationHelper.typePseudoUser(type, value));

            if (principal != null && type == Type.USER && principalDocuments != null) {
                for (Principal pd : principalDocuments) {
                    if (principal.equals(pd)) {
                        ((PrincipalImpl) principal).setURL(pd.getURL());
                    }
                }
            }

            if (!acl.hasPrivilege(this.privilege, principal)) {
                acl = acl.addEntry(this.privilege, principal);
            }
        }
        return acl;
    }


    private Principal getPrincipalDoc(Principal p, Set<Principal> principalDocuments) {
        if (principalDocuments != null) {
            for (Principal pd : principalDocuments) {
                if (pd.equals(p)) {
                    return pd;
                }
            }
        }
        return null;
    }

    private void validateShortcuts() {
        for (String shortcut : this.shortcuts) {
            List<String> values = this.permissionShortcutsConfig.get(shortcut);
            for (String value : values) {
                if (value == null || value.trim().equals("")) {
                    continue;
                }
                boolean valid = false;
                if (value.startsWith("user:pseudo:")) {
                    this.principalFactory.getPrincipal(value.substring("user:".length()), Type.PSEUDO);
                    valid = true;

                } else if (value.startsWith("user:")) {
                    Principal principal = this.principalFactory.getPrincipal(value.substring("user:".length()),
                            Type.USER);
                    valid = this.principalManager.validatePrincipal(principal);

                } else if (value.startsWith("group:")) {
                    Principal group = this.principalFactory
                            .getPrincipal(value.substring("group:".length()), Type.GROUP);
                    valid = this.principalManager.validateGroup(group);
                }
                if (!valid) {
                    throw new IllegalStateException("Invalid principal in shortcut: " + value);
                }
            }
        }
    }

    @Required
    public void setPrivilege(Privilege privilege) {
        this.privilege = privilege;
    }

    @Required
    public void setPrincipalFactory(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }

    @Required
    public void setPrincipalManager(PrincipalManager principalManager) {
        this.principalManager = principalManager;
    }

    @Required
    public void setLocaleResolver(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    @Required
    public void setRoleManager(RoleManager roleManager) {
        this.roleManager = roleManager;
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
    public void setDocumentPrincipalMetadataRetriever(
            DocumentPrincipalMetadataRetriever documentPrincipalMetadataRetriever) {
        this.documentPrincipalMetadataRetriever = documentPrincipalMetadataRetriever;
    }

    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

}
