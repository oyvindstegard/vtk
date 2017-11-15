/* Copyright (c) 2006-2012, University of Oslo, Norway
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
package vtk.repository;

/**
 * Repository actions are in general more fine grained than
 * ACL {@link vtk.repository.Privilege privileges}. There exists
 * one canonical <code>RepositoryAction</code> for each ACL <code>Privilege</code>.
 * 
 * Additional repository actions require ACL privileges and/or roles according to security
 * model. The rules are enforced by code in {@link AuthorizationManager}.
 * 
 * Repository actions cannot be directly added to resource ACLs.
 */
public enum RepositoryAction {
    
    READ_PROCESSED("read-processed"),
    CREATE("create"),
    CREATE_WITH_ACL("create-with-acl"),
    READ("read"),
    READ_WRITE("read-write"),
    WRITE("write"),
    WRITE_ACL("write-acl"),
    UNLOCK("unlock"),
    DELETE("delete"),
    COPY("copy"),
    MOVE("move"),
    ALL("all"),
    
    READ_WRITE_UNPUBLISHED("read-write-unpublished"),
    CREATE_UNPUBLISHED("create-unpublished"),
    PUBLISH_UNPUBLISH("publish-unpublish"),
    DELETE_UNPUBLISHED("delete-unpublished"),

    ADD_COMMENT("add-comment"),
    EDIT_COMMENT("edit-comment"),
    UNEDITABLE_ACTION("property-edit-uneditable-action"),
    REPOSITORY_ADMIN_ROLE_ACTION("property-edit-admin-role"),
    REPOSITORY_ROOT_ROLE_ACTION("property-edit-root-role");

    private String name;
    
    private RepositoryAction(String name) {
        this.name = name;
    }
    
    public String getName() {
        return this.name;
    }
    
    @Override
    public String toString() {
        return this.name;
    }
}
