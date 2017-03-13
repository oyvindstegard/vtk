/* Copyright (c) 2011, University of Oslo, Norway
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
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletRequest;

import org.springframework.web.bind.ServletRequestDataBinder;

public class ACLEditBinder extends ServletRequestDataBinder {
    
    private static final String REMOVE_GROUP_PREFIX = "removeGroup.";
    private static final String REMOVE_USER_PREFIX = "removeUser.";

    public ACLEditBinder(ACLEditCommand target, String objectName) {
        super(target, objectName);
    }

    @Override
    public void bind(ServletRequest request) {
        super.bind(request);
        ACLEditCommand command = (ACLEditCommand) getTarget();
        
        List<String> removedUsers = new ArrayList<String>();
        List<String> removedGroups = new ArrayList<String>();
        List<String> params = Collections.list(request.getParameterNames());
        
        extractGroupsAndUsersForRemoval(removedUsers, removedGroups, params);
        if (!removedGroups.isEmpty()) {
            String[] groupList = removedGroups.toArray(new String[]{});
            command.setRemoveGroupAction("removeGroupAction");
            command.setGroupNames(groupList); 
        }
        if (!removedUsers.isEmpty()) {
            String[] userList = removedUsers.toArray(new String[]{});
            command.setRemoveUserAction("removeUserAction");
            command.setUserNames(userList); 
        }
    }
    
    /**
     * Extracts groups and users for removal (from request parameters)
     * 
     * @param removedUsers
     *            list to add users for removal
     * @param removedGroups
     *            list to add groups for removal
     * @param params
     *            request parameters
     */
    protected void extractGroupsAndUsersForRemoval(List<String> removedUsers, List<String> removedGroups, List<String> params) {
        for (String param : params) {
            if (param.startsWith(REMOVE_GROUP_PREFIX)) {
                removedGroups.add(param.substring(REMOVE_GROUP_PREFIX.length()));
            } 
            else if (param.startsWith(REMOVE_USER_PREFIX)) {
                removedUsers.add(param.substring(REMOVE_USER_PREFIX.length()));
            }
        }
    }
}
