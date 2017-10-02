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

package vtk.repository.index.mapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.lucene.index.IndexableField;
import org.junit.Before;
import org.junit.Test;
import vtk.repository.Acl;
import vtk.repository.Path;
import vtk.repository.PropertySetImpl;
import vtk.security.PrincipalFactory;
import vtk.testing.mocktypes.MockPrincipalFactory;

import static org.junit.Assert.*;
import vtk.repository.Privilege;
import vtk.security.Principal;
import vtk.security.PrincipalImpl;

/**
 *
 */
public class AclFieldsTest {

    private final PrincipalFactory pf = new MockPrincipalFactory();

    private AclFields aclFields;
    
    private PropertySetImpl ps;
    
    @Before
    public void before() {
        aclFields = new AclFields(Locale.getDefault(), pf);
        
        ps = new PropertySetImpl();
        ps.setAclInheritedFrom(1000);
        ps.setNumericId(1001);
        ps.setUri(Path.fromString("/vrtx"));
        ps.setResourceType("collection");
    }
    
    @Test
    public void emptyAcl() {
        
        List<IndexableField> fields = new ArrayList<IndexableField>();
        
        aclFields.addAclFields(fields, ps, Acl.EMPTY_ACL);
        
        assertEquals(1, fields.size());
        IndexableField field = fields.get(0);
        
        assertEquals(AclFields.INHERITED_FROM_FIELD_NAME, field.name());
        assertEquals(String.valueOf(ps.getAclInheritedFrom()), field.stringValue());
        
    }
    
    @Test
    public void readForAll() {

        Acl acl = makeAcl(Privilege.READ, PrincipalFactory.ALL);
        List<IndexableField> fields = new ArrayList<IndexableField>();
        
        aclFields.addAclFields(fields, ps, acl);
        
        assertEquals(3, fields.size());
        IndexableField field = fields.get(0);
        
        assertEquals(AclFields.USERS_PRIV_FIELD_PREFIX + Privilege.READ.getName(), field.name());
        assertEquals(PrincipalFactory.ALL.getQualifiedName(), field.stringValue());

        field = fields.get(1);
        assertEquals(AclFields.AGGREGATED_READ_FIELD_NAME, field.name());
        assertEquals(PrincipalFactory.ALL.getQualifiedName(), field.stringValue());
    }
    
    @Test
    public void vortexDefaultAcl() {
        Acl acl = makeAcl(Privilege.READ, PrincipalFactory.ALL, 
                Privilege.ALL, user("vortex@localhost"));
        
        List<IndexableField> fields = new ArrayList<IndexableField>();
        
        aclFields.addAclFields(fields, ps, acl);
        
        Acl aclFromFields = aclFields.fromFields(fields);
        assertEquals(4, fields.size());
        assertEquals(acl, aclFromFields);
        
    }
    
    @Test
    public void complexAcl() {
        Acl acl = makeAcl(Privilege.READ_PROCESSED, PrincipalFactory.ALL,
                Privilege.ADD_COMMENT, user("commenter@uio.no"), user("commenter2@uio.no"),
                Privilege.ALL, user("root@localhost"),
                Privilege.READ_WRITE, user("foo@uio.no"), user("bar@uio.no"), user("baz@uio.no"),
                group("foogroup@netgroups.uio.no"), group("bargroup@netgroups.uio.no"));
                
        List<IndexableField> fields = new ArrayList<IndexableField>();
        aclFields.addAclFields(fields, ps, acl);
        
        Acl aclFromFields = aclFields.fromFields(fields);
        assertEquals(acl, aclFromFields);
    }
    
    @Test
    public void principalTypeIsCorrect() {
        Acl acl = makeAcl(Privilege.ALL, user("root@localhost"), 
                Privilege.READ_WRITE, group("system-users@localhost"));
                
        List<IndexableField> fields = new ArrayList<IndexableField>();
        aclFields.addAclFields(fields, ps, acl);
        
        Acl aclFromFields = aclFields.fromFields(fields);
        
        Set<Principal> allSet = aclFromFields.getPrincipalSet(Privilege.ALL);
        Principal principal = null;
        for (Principal p: allSet) {
            principal = p;
            break;
        }
        assertNotNull(principal);
        
        assertEquals("root@localhost", principal.getQualifiedName());
        assertEquals("Principal type not correct", Principal.Type.USER, principal.getType());
        
        // Group principal
        Set<Principal> readWriteSet = acl.getPrincipalSet(Privilege.READ_WRITE);
        principal = null;
        for (Principal p: readWriteSet) {
            principal = p;
            break;
        }
        assertNotNull(principal);
        
        assertEquals("system-users@localhost", principal.getQualifiedName());
        assertEquals(Principal.Type.GROUP, principal.getType());
    }
    
    @Test
    public void aggregatedReadFieldRestricted() {
        Acl acl = makeAcl(Privilege.ALL, user("vortex@localhost"), user("root@localhost"),
                Privilege.READ_WRITE_UNPUBLISHED, group("writers@netgroups.uio.no"),
                Privilege.READ_WRITE, group("editors@netgroups.uio.no"),
                Privilege.ADD_COMMENT, user("nobody@localhost"));
        
        List<IndexableField> fields = new ArrayList<IndexableField>();
        aclFields.addAclFields(fields, ps, acl);
        assertEquals(acl, aclFields.fromFields(fields));
        
        assertEquals(10, fields.size());
        
        Set<String> principalIds = new HashSet<String>();
        for (IndexableField f: fields) {
            if (f.name().equals(AclFields.AGGREGATED_READ_FIELD_NAME)) {
                principalIds.add(f.stringValue());
            }
        }
        
        assertEquals(4, principalIds.size());
        assertTrue(principalIds.contains("vortex@localhost"));
        assertTrue(principalIds.contains("root@localhost"));
        assertTrue(principalIds.contains("writers@netgroups.uio.no"));
        assertTrue(principalIds.contains("editors@netgroups.uio.no"));
    }
    
    @Test
    public void aggregatedReadFieldAll() {
        Acl acl = makeAcl(Privilege.ALL, user("vortex@localhost"), user("root@localhost"),
                Privilege.READ_WRITE_UNPUBLISHED, group("writers@netgroups.uio.no"),
                Privilege.READ_WRITE, group("editors@netgroups.uio.no"),
                Privilege.ADD_COMMENT, user("nobody@localhost"),
                Privilege.READ, PrincipalFactory.ALL);
        
        List<IndexableField> fields = new ArrayList<IndexableField>();
        aclFields.addAclFields(fields, ps, acl);
        
        assertEquals(acl, aclFields.fromFields(fields));

        Set<String> principalIds = new HashSet<String>();
        for (IndexableField f: fields) {
            if (f.name().equals(AclFields.AGGREGATED_READ_FIELD_NAME)) {
                principalIds.add(f.stringValue());
            }
        }
        
        assertEquals(1, principalIds.size());
        assertTrue(principalIds.contains("pseudo:all"));
    }
    
    private Acl makeAcl(Object... args) {
        Acl acl = Acl.EMPTY_ACL;
        Privilege privilege = null;
        for (Object arg: args) {
            if (arg instanceof Privilege) {
                privilege = (Privilege)arg;
            } else {
                if (privilege == null) {
                    throw new IllegalArgumentException("Privlege instances must precede Principal instances in arguments");
                }
                acl = acl.addEntry(privilege, (Principal)arg);
            }
        }
        return acl;
    }
    
    private Principal user(String uid) {
        return new PrincipalImpl(uid, Principal.Type.USER);
    }
    
    private Principal group(String uid) {
        return new PrincipalImpl(uid, Principal.Type.GROUP);
    }
    
}
