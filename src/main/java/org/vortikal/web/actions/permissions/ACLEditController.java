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
package org.vortikal.web.actions.permissions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.beans.propertyeditors.StringArrayPropertyEditor;
import org.springframework.validation.BindException;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.SimpleFormController;
import org.vortikal.repository.Acl;
import org.vortikal.repository.Path;
import org.vortikal.repository.Privilege;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.security.Principal;
import org.vortikal.security.PrincipalFactory;
import org.vortikal.security.PrincipalManager;
import org.vortikal.security.Principal.Type;
import org.vortikal.security.roles.RoleManager;
import org.vortikal.web.RequestContext;
import org.vortikal.web.service.Service;

public class ACLEditController extends SimpleFormController {

    private Privilege privilege;
    private PrincipalManager principalManager;
    private PrincipalFactory principalFactory;
    private RoleManager roleManager;

    private Map<Privilege, List<String>> permissionShortcuts;
    private Map<String, List<String>> permissionShortcutsConfig;
    
    private List<String> shortcuts;
    
    private boolean yourselfStillAdmin;


    public ACLEditController() {
        setSessionForm(true);
    }


    @Override
    protected void initBinder(HttpServletRequest request, ServletRequestDataBinder binder) throws Exception {
        binder.registerCustomEditor(java.lang.String[].class, new StringArrayPropertyEditor());
    }


    @Override
    protected ServletRequestDataBinder createBinder(HttpServletRequest request, Object command) throws Exception {
        ACLEditBinder binder = new ACLEditBinder(command, getCommandName());
        prepareBinder(binder);
        initBinder(request, binder);
        return binder;
    }


    @Override
    protected Object formBackingObject(HttpServletRequest request) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Path uri = requestContext.getResourceURI();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Resource resource = repository.retrieve(token, uri, false);

        this.shortcuts = this.permissionShortcuts.get(this.privilege);
        
        if (this.shortcuts != null) {
            this.shortcuts = validateShortcuts(this.shortcuts, this.permissionShortcutsConfig, repository, resource.getAcl());
        } 
        
        this.yourselfStillAdmin = true;

        return getACLEditCommand(resource, resource.getAcl(), requestContext.getPrincipal(), false);
    }


    private ACLEditCommand getACLEditCommand(Resource resource, Acl acl, Principal principal, boolean isCustomPermissions) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Service service = requestContext.getService();

        String submitURL = service.constructLink(resource, principal);
        ACLEditCommand command = new ACLEditCommand(submitURL);

        command.setAcl(acl);
        command.setPrivilege(this.privilege);

        List<Principal> authorizedGroups = new ArrayList<Principal>(Arrays.asList(acl
                .listPrivilegedGroups(this.privilege)));
        List<Principal> authorizedUsers = new ArrayList<Principal>(Arrays.asList(acl
                .listPrivilegedUsers(this.privilege)));
        authorizedUsers.addAll(Arrays.asList(acl.listPrivilegedPseudoPrincipals(this.privilege)));

        if (this.shortcuts != null) {
            command.setShortcuts(extractAndCheckShortcuts(authorizedGroups, authorizedUsers,
                    this.shortcuts, this.permissionShortcutsConfig, isCustomPermissions));
        }
        
        if(!this.yourselfStillAdmin) {
            command.setYourselfStillAdmin(this.yourselfStillAdmin);
        }

        command.setGroups(authorizedGroups);
        command.setUsers(authorizedUsers);

        return command;
    }


    @Override
    protected ModelAndView processFormSubmission(HttpServletRequest req, HttpServletResponse resp, Object command,
            BindException errors) throws Exception {
        if (errors.hasErrors()) {
            ACLEditCommand editCommand = (ACLEditCommand) command;
            editCommand.setAddGroupAction(null);
            editCommand.setAddUserAction(null);
            editCommand.setRemoveGroupAction(null);
            editCommand.setRemoveUserAction(null);
            editCommand.setSaveAction(null);
            editCommand.getUserNameEntries().removeAll(editCommand.getUserNameEntries());
            editCommand.setYourselfStillAdmin(true);
        }
        return super.processFormSubmission(req, resp, command, errors);
    }


    @Override
    protected ModelAndView onSubmit(HttpServletRequest request, HttpServletResponse response, Object command,
            BindException errors) throws Exception {
        
        ACLEditCommand editCommand = (ACLEditCommand) command;

        Acl acl = editCommand.getAcl();

        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();

        Resource resource = repository.retrieve(token, uri, false);

        // Did the user cancel?
        if (editCommand.getCancelAction() != null) {
            return new ModelAndView(getSuccessView());
        }

        Principal yourself = requestContext.getPrincipal();
        this.yourselfStillAdmin = true;
        
        // Has the user asked to save?
        if (editCommand.getSaveAction() != null) {
            acl = updateAclIfShortcut(acl, editCommand, yourself, errors);
            acl = addToAcl(acl, editCommand.getGroupNames(), Type.GROUP, yourself);
            acl = addToAcl(acl, editCommand.getUserNameEntries(), Type.USER, yourself);
            if (errors.hasErrors()) {
                BindException bex = new BindException(getACLEditCommand(resource, acl, yourself, true), this.getCommandName());
                bex.addAllErrors(errors);
                return showForm(request, response, errors);
            }
            resource = repository.storeACL(token, resource.getURI(), acl);
            return new ModelAndView(getSuccessView());
        }

        // Doing remove or add actions
        if (editCommand.getRemoveGroupAction() != null) {
            acl = removeFromAcl(acl, editCommand.getGroupNames(), Type.GROUP, yourself, errors);
            BindException bex = new BindException(getACLEditCommand(resource, acl, yourself, true), this.getCommandName());
            bex.addAllErrors(errors);
            return showForm(request, response, bex);

        } else if (editCommand.getRemoveUserAction() != null) {
            acl = removeFromAcl(acl, editCommand.getUserNames(), Type.USER, yourself, errors);
            BindException bex = new BindException(getACLEditCommand(resource, acl, yourself, true), this.getCommandName());
            bex.addAllErrors(errors);
            return showForm(request, response, bex);

        } else if (editCommand.getAddGroupAction() != null) {
            // If not a shortcut and no groups/users in admin, then remove groups/users (typical when coming from a shortcut)
            if (editCommand.getGroups().size() == 0 && editCommand.getUsers().size() == 0) {
              acl = acl.clear(this.privilege); 
            }
            
            acl = addToAcl(acl, editCommand.getGroupNames(), Type.GROUP, yourself);
            return showForm(request, response, new BindException(getACLEditCommand(resource, acl, yourself, true), this
                    .getCommandName()));

        } else if (editCommand.getAddUserAction() != null) {
            // If not a shortcut and no groups/users in admin, then remove groups/users (typical when coming from a shortcut)
            if (editCommand.getGroups().size() == 0 && editCommand.getUsers().size() == 0) {
              acl = acl.clear(this.privilege); 
            }
            acl = addToAcl(acl, editCommand.getUserNameEntries(), Type.USER, yourself);
            return showForm(request, response, new BindException(getACLEditCommand(resource, acl, yourself, true), this
                    .getCommandName()));
        }

        return new ModelAndView(getSuccessView());
    }
    
    /**
     * Count valid shortcuts (all users and groups should have GROUP or USER prefix)
     * 
     * TODO: validate shortcuts (with something like validateGroupOrUserName())
     *
     * @param shortcuts the configured shortcuts for the privilege
     * @param permissionShortcutsConfig the users and groups for the shortcuts
     * @return number of valid shortcuts
     */
    protected List<String> validateShortcuts(List<String> shortcuts, Map<String, List<String>> permissionShortcutsConfig, Repository repository, Acl acl) throws Exception {
        int counter = 0;
        Iterator<String> it = shortcuts.iterator();
        while (it.hasNext()) {
            String shortcut = it.next();
            if (!permissionShortcutsConfig.containsKey(shortcut)) {
                it.remove();
                continue; // next shortcut
            }
            int validGroupsUsers = 0;
            List<String> groupsUsersPrShortcut = permissionShortcutsConfig.get(shortcut);
            for (String groupOrUser : groupsUsersPrShortcut) {
                if (groupOrUser.startsWith(ACLEditValidationHelper.SHORTCUT_GROUP_PREFIX)
                 || groupOrUser.startsWith(ACLEditValidationHelper.SHORTCUT_USER_PREFIX)) {
                    if (repository != null) {
                        String groupOrUserUnformatted[] = new String[1];
                        Type type = unformatGroupOrUserAndSetType(groupOrUser, groupOrUserUnformatted);
                        String validationResult = ACLEditValidationHelper.validateGroupOrUserName(type,
                                groupOrUserUnformatted[0], this.privilege, this.principalFactory,
                                this.principalManager, repository, acl);

                        if (ACLEditValidationHelper.VALIDATION_ERROR_NONE.equals(validationResult)) {
                            validGroupsUsers++;
                        }
                    } else { // testcase
                        validGroupsUsers++;
                    }
                }
            }
            if (groupsUsersPrShortcut.size() != validGroupsUsers) {
                it.remove();
            }
            counter++;
        }
        return shortcuts;
    }
    
    
    /**
     * Extracts shortcuts from authorized users and groupse
     *
     * @param authorizedUsers the authorized users
     * @param authorizedGroups the authorized groups
     * @param precounted valid shortcuts
     * @param shortcuts the configured shortcuts for the privilege
     * @param permissionShortcutsConfig the users and groups for the shortcuts
     * @return a <code>String[][]</code> object containing checked / not-checked shortcuts
     */
    protected String[][] extractAndCheckShortcuts(List<Principal> authorizedGroups, List<Principal> authorizedUsers,
            List<String> shortcuts, Map<String, List<String>> permissionShortcutsConfig, boolean isCustomPermissions) throws Exception {
        
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
            if(!isCustomPermissions) {
                // If matches are exactly the number of authorized groups/users and the number of groups/users in shortcut
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
     * @param acl the ACL object
     * @param editCommand the command object
     * @param yourself
     * @param errors ACL validation errors
     * @return the modified ACL
     */
    private Acl updateAclIfShortcut(Acl acl, ACLEditCommand editCommand, Principal yourself, BindException errors) throws Exception {    
        String updatedShortcut = editCommand.getUpdatedShortcut();

        if (this.permissionShortcutsConfig.get(updatedShortcut) != null) {

            // First: remove all ACEs on privilege
            acl = acl.clear(this.privilege);
            
            // Then: add ACEs from updated shortcut
            List<String> shortcutACEs = this.permissionShortcutsConfig.get(updatedShortcut);
            for (String aceWithPrefix : shortcutACEs) {
                String groupOrUserUnformatted[] = new String[1];
                Type type = unformatGroupOrUserAndSetType(aceWithPrefix, groupOrUserUnformatted);
                acl = addToAcl(acl, groupOrUserUnformatted, type, yourself);
            }
        } else {
          // If not a shortcut and no groups/users in admin, then remove groups/users (typical when coming from a shortcut)
          if (editCommand.getGroups().size() == 0 && editCommand.getUsers().size() == 0) {
            acl = acl.clear(this.privilege); 
          }
        }

        return acl;
    }
    
    
    /**
     * Remove groups or users from ACL.
     *
     * @param acl the ACL object
     * @param values groups or users to remove
     * @param type type of ACL (GROUP or USER)
     * @param yourself
     * @param errors ACL validation errors
     * @return the modified ACL
     */
    private Acl removeFromAcl(Acl acl, String[] values, Type type, Principal yourself, BindException errors) throws Exception {
        for (String value : values) {
            Principal userOrGroup = principalFactory.getPrincipal(value, ACLEditValidationHelper.typePseudoUser(type, value));
            Acl potentialAcl = acl.removeEntry(this.privilege, userOrGroup);
            if (this.privilege.equals(Privilege.ALL) && !this.roleManager.hasRole(yourself, RoleManager.Role.ROOT)) {
                boolean yourselfNotInAdmin = !potentialAcl.containsEntry(this.privilege, yourself);
                if (yourselfNotInAdmin) {
                    potentialAcl = checkIfNotEmptyAdminAcl(acl, potentialAcl, userOrGroup, errors);
                    if(errors.hasErrors()) { // if has errors use the original ACL
                      acl = checkIfYourselfIsStillInAdminPrivilegedGroups(acl, userOrGroup, yourself);
                    } else {
                      acl = checkIfYourselfIsStillInAdminPrivilegedGroups(potentialAcl, userOrGroup, yourself);
                    }
                } else {
                    acl = checkIfNotEmptyAdminAcl(acl, potentialAcl, userOrGroup, errors);
                }
            } else {
                acl = potentialAcl;
            }
        }
        return acl;
    }
    

    /**
     * Check if yourself is still in privileged groups for admin after removal
     *
     * @param acl the ACL object
     * @param userOrGroup the user or group
     * @param yourself
     * @return the modified ACL
     */
    private Acl checkIfYourselfIsStillInAdminPrivilegedGroups(Acl acl, Principal userOrGroup, Principal yourself) throws Exception {
        
        Set<Principal> memberGroups = principalManager.getMemberGroups(yourself);
        Principal[] privilegedGroups = acl.listPrivilegedGroups(Privilege.ALL);
        
        this.yourselfStillAdmin = false;
        for (Principal privilegedGroup : privilegedGroups) {
            for (Principal memberGroup : memberGroups) {
                if (memberGroup.equals(privilegedGroup)) {
                    this.yourselfStillAdmin = true;
                    break;
                }
            }
        }
        
        return acl;
    }

    /**
     * Check if not empty admin Acl
     *
     * @param acl the ACL object
     * @param potentialAcl the potential ACL object
     * @param userOrGroup the user or group
     * @param errors ACL validation errors
     * @return the modified ACL
     */
    private Acl checkIfNotEmptyAdminAcl(Acl acl, Acl potentialAcl, Principal userOrGroup, BindException errors) throws Exception {
        if (potentialAcl.getPrincipalSet(Privilege.ALL).size() == 0) {
            String prefixType = (userOrGroup.getType().equals(Type.GROUP)) ? "group" : "user"; // pseudo is user
            errors.rejectValue(prefixType + "Names", "permissions.all.not.empty",
                    "Not possible to remove all admin permissions");
            return acl;
        } else {
            return potentialAcl;
        }
    }


    /**
     * Add group or user to ACL.
     *
     * @param acl the ACL object
     * @param value group or user to remove
     * @param type type of ACL (GROUP or USER)
     * @return the modified ACL
     */
    private Acl addToAcl(Acl acl, String[] values, Type type, Principal yourself) throws Exception {
        for (String value : values) {
            Principal principal = principalFactory.getPrincipal(value, ACLEditValidationHelper.typePseudoUser(type, value));
            if (!acl.containsEntry(this.privilege, principal)) {
              acl = acl.addEntry(this.privilege, principal);
              if (this.privilege.equals(Privilege.ALL) && !this.roleManager.hasRole(yourself, RoleManager.Role.ROOT)) {
                boolean yourselfNotInAdmin = !acl.containsEntry(this.privilege, yourself);
                if (yourselfNotInAdmin) {
                  acl = checkIfYourselfIsStillInAdminPrivilegedGroups(acl, principal, yourself);
                }
              }
            }
        }
        return acl;
    }


    /**
     * Add groups or users to ACL (for getUserNameEntries()).
     *
     * @param acl the ACL object
     * @param values groups or users to remove
     * @param type type of ACL (GROUP or USER)
     * @return the modified ACL
     */
    private Acl addToAcl(Acl acl, List<String> values, Type type, Principal yourself) throws Exception {
        for (String value : values) {
            Principal principal = principalFactory.getPrincipal(value, ACLEditValidationHelper.typePseudoUser(type, value));
            if (!acl.containsEntry(this.privilege, principal)) {
              acl = acl.addEntry(this.privilege, principal);
              if (this.privilege.equals(Privilege.ALL) && !this.roleManager.hasRole(yourself, RoleManager.Role.ROOT)) {
                boolean yourselfNotInAdmin = !acl.containsEntry(this.privilege, yourself);
                if (yourselfNotInAdmin) {
                  acl = checkIfYourselfIsStillInAdminPrivilegedGroups(acl, principal, yourself);
                }
              }
            }
        }
        return acl;
    }


    /**
     * Unformat group or user in shortcut and set type to GROUP or USER
     *
     * @param groupOrUser formatted shortcut
     * @param groupOrUserUnformatted unformatted shortcut (return by reference)
     * @return type of ACL (GROUP or USER)
     */
    private Type unformatGroupOrUserAndSetType(String groupOrUser, String[] groupOrUserUnformatted) throws Exception {
        Type type = null;
        if (groupOrUser.startsWith(ACLEditValidationHelper.SHORTCUT_GROUP_PREFIX)) {
            groupOrUserUnformatted[0] = groupOrUser.substring(ACLEditValidationHelper.SHORTCUT_GROUP_PREFIX.length());
            type = Type.GROUP;
        } else if (groupOrUser.startsWith(ACLEditValidationHelper.SHORTCUT_USER_PREFIX)) {
            groupOrUserUnformatted[0] = groupOrUser.substring(ACLEditValidationHelper.SHORTCUT_USER_PREFIX.length());
            type = Type.USER;
        }
        return type;
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

}
