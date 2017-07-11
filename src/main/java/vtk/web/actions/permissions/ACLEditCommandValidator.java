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

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.validation.Errors;

import vtk.repository.Repository;
import vtk.repository.store.PrincipalSearch.SearchType;
import vtk.security.Principal;
import vtk.security.Principal.Type;
import vtk.security.PrincipalFactory;
import vtk.security.PrincipalManager;
import vtk.web.SimpleFormController;

public class ACLEditCommandValidator implements SimpleFormController.Validator<ACLEditCommand> {

    private PrincipalManager principalManager;
    private PrincipalFactory principalFactory;
    private Repository repository;

    private String notFound;
    private String illegalBlacklisted;
    private String illegal;
    private String tooManyMatchedUsers;

    @Override
    public void validate(HttpServletRequest request, ACLEditCommand editCommand,
            Errors errors) {

        // Don't validate on cancel
        if (editCommand.getCancelAction() != null) {
            return;
        }

        if (editCommand.getSaveAction() != null) {
            validateUserNames(editCommand, errors);
            validateGroupNames(editCommand, errors);
        }

        if (editCommand.getAddGroupAction() != null) {
            String[] groupNames = editCommand.getGroupNames();

            if (groupNames.length == 0) {
                errors.rejectValue("groupNames", "permissions.group.missing.value", "You must type a group name");
            }

            validateGroupNames(editCommand, errors);

        } else if (editCommand.getAddUserAction() != null) {
            String[] userNames = editCommand.getUserNames();

            if (userNames.length == 0) {
                errors.rejectValue("userNames", "permissions.user.missing.value", "You must type a username");
            }

            validateUserNames(editCommand, errors);

        }
    }

    private void validateGroupNames(ACLEditCommand editCommand, Errors errors) {
        String[] groupNames = editCommand.getGroupNames();
        if (groupNames.length > 0) {
            this.notFound = "";
            this.illegalBlacklisted = "";
            this.illegal = "";
            this.tooManyMatchedUsers = "";

            for (String groupName : groupNames) {
                if (!validateGroupOrUserName(Type.GROUP, groupName, editCommand)) {
                    continue;
                }
            }

            // Make sure we get back to custom permissions when exact match for
            // a shortcut, but encounter validation error on new group
            uncheckAllShortcuts(editCommand);

            rejectValues(ACLEditValidationHelper.VALIDATION_ERROR_GROUP_PREFIX,
                    ACLEditValidationHelper.VALIDATION_ERROR_NOT_FOUND, this.notFound, errors);
            rejectValues(ACLEditValidationHelper.VALIDATION_ERROR_GROUP_PREFIX,
                    ACLEditValidationHelper.VALIDATION_ERROR_ILLEGAL_BLACKLISTED, this.illegalBlacklisted, errors);
            rejectValues(ACLEditValidationHelper.VALIDATION_ERROR_GROUP_PREFIX,
                    ACLEditValidationHelper.VALIDATION_ERROR_ILLEGAL, this.illegal, errors);
        }
    }

    private void validateUserNames(ACLEditCommand editCommand, Errors errors) {
        String[] userNames = editCommand.getUserNames();

        if (userNames.length > 0) {
            this.notFound = new String();
            this.illegalBlacklisted = new String();
            this.illegal = new String();
            this.tooManyMatchedUsers = new String();

            for (String userName : userNames) {

                userName = userName.trim();
                String uid = userName;

                // Assume a username and validate it as such
                if (!userName.contains(" ")) {
                    if (!validateGroupOrUserName(Type.USER, userName, editCommand)) {
                        continue; // next userName
                    }
                } else {
                    // Assume a full name and look for a match in ac_userNames
                    // If match found: validate corresponding username If no
                    // match found: assume full name entered without selecting
                    // from autocomplete suggestions i.e. no username provided
                    // -> validate as full name
                    try {
                        String ac_userName = getAc_userName(userName, editCommand.getAc_userNames(),
                                editCommand.getUserNameEntries());

                        // Entered name is selected from autocomplete
                        // suggestions and we have username
                        if (ac_userName != null && !"".equals(ac_userName)) {
                            if (!validateGroupOrUserName(Type.USER, ac_userName, editCommand)) {
                                continue; // next userName
                            }
                            uid = ac_userName;
                        } else {
                            List<Principal> matches = this.principalFactory.search(userName, Type.USER,
                                    SearchType.FULL_USER_SEARCH);
                            if (matches == null || matches.isEmpty()) {
                                this.notFound += toCSV(this.notFound, userName);
                                continue; // next userName
                            } else if (matches.size() > 1) {
                                this.tooManyMatchedUsers += toCSV(this.tooManyMatchedUsers, userName);
                                continue; // next userName
                            }
                            uid = matches.get(0).getName();
                        }
                    } catch (Exception e) {
                        this.notFound += toCSV(this.notFound, userName);
                        continue; // next userName
                    }
                }
                editCommand.addUserNameEntry(uid);
            }

            // Make sure we get back to custom permissions when exact match for
            // a shortcut, but encounter validation error on new user
            uncheckAllShortcuts(editCommand);

            rejectValues(ACLEditValidationHelper.VALIDATION_ERROR_USER_PREFIX,
                    ACLEditValidationHelper.VALIDATION_ERROR_NOT_FOUND, this.notFound, errors);
            rejectValues(ACLEditValidationHelper.VALIDATION_ERROR_USER_PREFIX,
                    ACLEditValidationHelper.VALIDATION_ERROR_ILLEGAL_BLACKLISTED, this.illegalBlacklisted, errors);
            rejectValues(ACLEditValidationHelper.VALIDATION_ERROR_USER_PREFIX,
                    ACLEditValidationHelper.VALIDATION_ERROR_ILLEGAL, this.illegal, errors);
            rejectValues(ACLEditValidationHelper.VALIDATION_ERROR_USER_PREFIX,
                    ACLEditValidationHelper.VALIDATION_ERROR_TOO_MANY_MATCHES, this.tooManyMatchedUsers, errors);
        }
    }

    private boolean validateGroupOrUserName(Type type, String name, ACLEditCommand editCommand) {
        String validationResult = ACLEditValidationHelper.validateGroupOrUserName(type, name,
                editCommand.getPrivilege(), this.principalFactory, this.principalManager, this.repository,
                editCommand.getAcl());

        if (!ACLEditValidationHelper.VALIDATION_ERROR_NONE.equals(validationResult)) {
            if (ACLEditValidationHelper.VALIDATION_ERROR_NOT_FOUND.equals(validationResult)) {
                this.notFound += toCSV(this.notFound, name);
            } else if (ACLEditValidationHelper.VALIDATION_ERROR_ILLEGAL_BLACKLISTED.equals(validationResult)) {
                this.illegalBlacklisted += toCSV(this.illegalBlacklisted, name);
            } else if (ACLEditValidationHelper.VALIDATION_ERROR_ILLEGAL.equals(validationResult)) {
                this.illegal += toCSV(this.illegal, name);
            }
            return false;
        }

        return true;
    }

    private void uncheckAllShortcuts(ACLEditCommand editCommand) {
        String[][] shortcuts = editCommand.getShortcuts();
        for (int i = 0; i < shortcuts.length; i++) {
            shortcuts[i][1] = "";
        }
        editCommand.setShortcuts(shortcuts);
    }

    private void rejectValues(String type, String errorType, String groupsOrUsers, Errors errors) {
        if (!groupsOrUsers.isEmpty()) {
            if (!groupsOrUsers.contains(",")) {
                errors.rejectValue(type + "Names", "permissions." + type + "." + errorType + ".value",
                        new Object[] { groupsOrUsers }, "The " + type + " " + groupsOrUsers
                                + " does not exist, is illegal (possibly blacklisted) or yielded too many matches");
            } else {
                errors.rejectValue(type + "Names", "permissions." + type + "." + errorType + ".values",
                        new Object[] { groupsOrUsers }, "The " + type + "s " + groupsOrUsers
                                + " does not exist, are illegal (possibly blacklisted) or yielded too many matches");
            }
        }
    }

    protected String getAc_userName(String userName, String[] ac_userNames, List<String> userNameEntries) {
        for (String ac_userName : ac_userNames) {
            String[] s = ac_userName.split(";");
            String ac_fullName = s[0].trim();
            String ac_uid = s[1].trim();
            if (!userNameEntries.contains(ac_uid) && userName.equals(ac_fullName)) {
                return ac_uid;
            }
        }
        return null;
    }

    protected String toCSV(String csv, String name) {
        String trimmedName = name.trim();
        if (csv.isEmpty()) {
            return "'" + trimmedName + "'";
        } else {
            return ", '" + trimmedName + "'";
        }
    }

    @Required
    public void setPrincipalManager(PrincipalManager principalManager) {
        this.principalManager = principalManager;
    }

    @Required
    public void setPrincipalFactory(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }

    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

}
