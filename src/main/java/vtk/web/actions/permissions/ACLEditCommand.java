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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import vtk.repository.Acl;
import vtk.repository.Privilege;
import vtk.security.Principal;
import vtk.web.actions.UpdateCancelCommand;

public class ACLEditCommand extends UpdateCancelCommand implements Serializable {

    private static final long serialVersionUID = 8536210088233742679L;
    private Acl acl;
    private Privilege privilege;

    private String addUserAction = null;
    private String removeUserAction = null;
    private String addGroupAction = null;
    private String removeGroupAction = null;
    private List<Principal> users;
    private List<Principal> groups;
    private String userNames[] = new String[0];
    private String groupNames[] = new String[0];
    private String shortcuts[][] = new String[0][0];
    private String updatedShortcut = "";

    private String saveAction = null;
    
    private boolean losingPrivileges = false;

    private String ac_userNames[] = new String[0];
    private List<String> userNameEntries = new ArrayList<String>();

    public ACLEditCommand(String submitURL) {
        super(submitURL);
    }

    public Acl getAcl() {
        return acl;
    }

    public void setAcl(Acl acl) {
        this.acl = acl;
    }
    
    public Privilege getPrivilege() {
        return privilege;
    }

    public void setPrivilege(Privilege privilege) {
        this.privilege = privilege;
    }
    
    public String getAddUserAction() {
        return this.addUserAction;
    }

    public void setAddUserAction(String addUserAction) {
        this.addUserAction = addUserAction;
    }

    public String getRemoveUserAction() {
        return this.removeUserAction;
    }

    public void setRemoveUserAction(String removeUserAction) {
        this.removeUserAction = removeUserAction;
    }

    public String getAddGroupAction() {
        return this.addGroupAction;
    }

    public void setAddGroupAction(String addGroupAction) {
        this.addGroupAction = addGroupAction;
    }

    public String getRemoveGroupAction() {
        return this.removeGroupAction;
    }

    public void setRemoveGroupAction(String removeGroupAction) {
        this.removeGroupAction = removeGroupAction;
    }

    public List<Principal> getUsers() {
        return this.users;
    }

    public void setUsers(List<Principal> users) {
        this.users = users;
    }

    public List<Principal> getGroups() {
        return this.groups;
    }

    public void setGroups(List<Principal> groups) {
        this.groups = groups;
    }
    
    public String[] getGroupNames() {
        return this.groupNames;
    }

    public void setGroupNames(String[] groupNames) {
        this.groupNames = stripBlanks(groupNames);
    }

    public String[] getUserNames() {
        return this.userNames;
    }

    public void setUserNames(String[] userNames) {
        this.userNames = stripBlanks(userNames);
    }
    
    public String[][] getShortcuts() {
        return shortcuts;
    }

    public void setShortcuts(String[][] shortcuts) {
        this.shortcuts = shortcuts;
    }

    protected String[] stripBlanks(String[] values) {
        List<String> noBlanks = new ArrayList<String>();
        for (String value : values) {
            if (value != null && !value.trim().equals("")) {
                noBlanks.add(value);
            }
        }
        return noBlanks.toArray(new String[noBlanks.size()]);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getName());
        sb.append("[acl=").append(this.acl);
        sb.append(", addUserAction=").append(this.addUserAction);
        sb.append(", removeUserAction=").append(this.removeUserAction);
        sb.append(", addGroupAction=").append(this.addGroupAction);
        sb.append(", removeGroupAction=").append(this.removeGroupAction);
        sb.append(", users=").append(this.users);
        sb.append(", groups=").append(this.groups);
        sb.append(", userNames=").append(java.util.Arrays.asList(this.userNames));
        sb.append(", groupNames=").append(java.util.Arrays.asList(this.groupNames));
        StringBuilder sbS = new StringBuilder();
        for(String[] shortcut : this.shortcuts) {
            sbS.append(shortcut[0] + " " + shortcut[1]);   
        }
        sb.append(", shortcuts=").append(sbS.toString());
        sb.append(", updatedShortcut=").append(this.updatedShortcut);
        sb.append("]");
        return sb.toString();
    }

    public String getSaveAction() {
        return saveAction;
    }

    public void setSaveAction(String saveAction) {
        this.saveAction = saveAction;
    }
    
    public String[] getAc_userNames() {
        return ac_userNames;
    }

    public void setAc_userNames(String[] ac_userNames) {
        this.ac_userNames = ac_userNames;
    }

    public List<String> getUserNameEntries() {
        return userNameEntries;
    }

    public void addUserNameEntry(String userNameEntry) {
        this.userNameEntries.add(userNameEntry.toLowerCase());
    }
    
    public String getUpdatedShortcut() {
        return this.updatedShortcut;
    }

    public void setUpdatedShortcut(String updatedShortcut) {
        this.updatedShortcut = updatedShortcut;
    }

    public boolean isLosingPrivileges() {
        return this.losingPrivileges;
    }

    public void setLosingPrivileges(boolean losingPrivileges) {
        this.losingPrivileges = losingPrivileges;
    }
}
