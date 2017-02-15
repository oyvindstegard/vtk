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
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexableField;

import vtk.repository.Acl;
import vtk.repository.AuthorizationManager;
import vtk.repository.Privilege;
import vtk.repository.PropertySetImpl;
import vtk.security.Principal;
import vtk.security.PrincipalFactory;

/**
 * ACL field support.
 * 
 * <p>Mapping to/from {@link Acl acl objects} and related attributes.
 * 
 */
public class AclFields extends Fields {

    public static final String ACL_FIELD_PREFIX = "acl_";
    public static final String GROUPS_PRIV_FIELD_PREFIX = ACL_FIELD_PREFIX + "g_";
    public static final String USERS_PRIV_FIELD_PREFIX = ACL_FIELD_PREFIX + "u_";
    
    public static final String AGGREGATED_READ_FIELD_NAME = ACL_FIELD_PREFIX + "read_aggregate";
    public static final String INHERITED_FROM_FIELD_NAME = ACL_FIELD_PREFIX + "inherited_from";
        
    private final PrincipalFactory principalFactory;
    
    AclFields(Locale locale, PrincipalFactory pf) {
        super(locale);
        this.principalFactory = pf;
    }
    
    void addAclFields(final List<IndexableField> fields, PropertySetImpl propSet, Acl acl) {
        for (Privilege action: acl.getActions()) {
            Set<Principal> principals = acl.getPrincipalSet(action);
            for (Principal p: principals) {
                String fieldName = aceFieldName(action, p.getType());
                fields.add(new StringField(fieldName, p.getQualifiedName(), Field.Store.YES));
            }
        }
        
        // Aggregate read field used for security filtering
        for (Principal p: aggregatePrincipalsForRead(acl)) {
            fields.add(new StringField(AGGREGATED_READ_FIELD_NAME, p.getQualifiedName(), Field.Store.NO));
        }
        
        // ACL inherited from field, index as string identifier
        fields.add(new StringField(INHERITED_FROM_FIELD_NAME, 
                Integer.toString(propSet.getAclInheritedFrom()), Field.Store.YES));
    }

    public Acl fromDocument(Document doc) {
        return fromFields(doc.getFields());
    }
    
    public Acl fromFields(List<IndexableField> fields) {
        final Map<Privilege, Set<Principal>> privileges = 
                new EnumMap<Privilege, Set<Principal>>(Privilege.class);
        
        for (IndexableField f : fields) {
            if (f.name().startsWith(USERS_PRIV_FIELD_PREFIX)
                    || f.name().startsWith(GROUPS_PRIV_FIELD_PREFIX)) {
                addEntry(f, privileges);
            }
        }
        
        return new Acl(privileges);
    }
    
    public static int aclInheritedFrom(List<IndexableField> fields) {
        for (IndexableField f: fields) {
            if (f.name().equals(INHERITED_FROM_FIELD_NAME)) {
                return Integer.parseInt(f.stringValue());
            }
        }

        throw new IllegalArgumentException("Required field " + INHERITED_FROM_FIELD_NAME + ": not found in field list: " + fields);
    }
    
    public static int aclInheritedFrom(Document doc) {
        return aclInheritedFrom(doc.getFields());
    }
    
    public static boolean isAclField(String fieldName) {
        return fieldName.startsWith(ACL_FIELD_PREFIX);
    }
    
    public static String aceFieldName(Privilege action, Principal.Type principalType) {
        if (principalType == Principal.Type.USER || principalType == Principal.Type.PSEUDO) {
            return USERS_PRIV_FIELD_PREFIX + action.getName();
        } else {
            return GROUPS_PRIV_FIELD_PREFIX + action.getName();
        }
    }
    
    private void addEntry(IndexableField field, Map<Privilege, Set<Principal>> actionSets) {
        Principal.Type type;
        final int offset;
        if (field.name().startsWith(USERS_PRIV_FIELD_PREFIX)) {
            type = Principal.Type.USER;
            offset = USERS_PRIV_FIELD_PREFIX.length();
        } else if (field.name().startsWith(GROUPS_PRIV_FIELD_PREFIX)) {
            type = Principal.Type.GROUP;
            offset =  GROUPS_PRIV_FIELD_PREFIX.length();
        } else {
            throw new IllegalArgumentException("Not an ACL entry field: " + field);
        }
        
        final Privilege action = Privilege.forName(field.name().substring(offset));
        final String id;
        if (field.stringValue() != null) {
            id = field.stringValue();
        } else {
            throw new IllegalArgumentException("Field has no stored string value: " + field);
        }
        
        if (id.startsWith("pseudo:")) {
            type = Principal.Type.PSEUDO;
        }

        actionSets.computeIfAbsent(action, a -> new HashSet<>())
                .add(principalFactory.getPrincipal(id, type, false));
    }
    
    /**
     * Extract set of principals in ACL which through suitable privileges
     * are able to READ the resource. If the {@link PrincipalFactory#ALL ALL user} is
     * in the final read set, then only this principal will be returned.
     * 
     * @param acl the ACL
     * @return a set of <code>Principal</code>
     */
    private Set<Principal> aggregatePrincipalsForRead(Acl acl) {
        
        Set<Principal> readSet = new HashSet<Principal>();
        for (Privilege p: AuthorizationManager.superPrivilegesOf(Privilege.READ_PROCESSED)) {
            readSet.addAll(acl.getPrincipalSet(p));
        }
        
        if (readSet.contains(PrincipalFactory.ALL)) {
            return new HashSet<Principal>(Arrays.asList(new Principal[]{PrincipalFactory.ALL}));
        }
        
        return readSet;
    }

}
