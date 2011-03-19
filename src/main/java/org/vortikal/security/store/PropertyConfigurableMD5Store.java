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
package org.vortikal.security.store;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.vortikal.security.AuthenticationException;
import org.vortikal.security.Principal;
import org.vortikal.util.codec.MD5;

/**
 * An implementation of MD5PasswordPrincipalManager that stores its
 * user and group information in-memory.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>principals</code> - a {@link Properties} object
 *   containing names and encoded passwords of the principals. This map
 *   is assumed to contain entries of type <code>(username,
 *   md5hash)</code>, and <code>md5hash</code> is in turn assumed to be
 *   a hashed value of the string <code>username:realm:password</code>.
 *   <li><code>groups</code> - This {@link Map} is assumed to contain
 *   entries of type <code>(group, memberlist)</code>, where
 *   <code>group</code> is a group prinbcipal and <code>memberlist</code> 
 *   is a <code>java.util.List</code> of strings which represent the names of 
 *   those principals that are members of the group.
 *   <li><code>realm</code> - the authentication realm
 *   <li><code>domain</code> - the domain of the principals in this
 *   store (may be <code>null</code>)
 *   <li><code>order</code> - the order returned in {@link Order#getOrder}
 * </ul>
 */
public class PropertyConfigurableMD5Store implements MD5PasswordStore, Ordered {

    private static Log logger = LogFactory.getLog(PropertyConfigurableMD5Store.class);

    private Properties principals;
    private Map<Principal, Collection<String>> groups;
    private String realm;

    private int order = Integer.MAX_VALUE;

    // XXX: this overloading of setPrincipals is problematic in conjunction
    //      with Spring dep-injection. Spring chooses the wrong-setter for
    //      Properties-backed principals and fails...
    //      Maybe just remove the repositoryPrincipalStore-extension..

    /**
     * Sets the principals as a properties mapping of the format
     * <code>(username@domain, password-md5)</code>
     * @param principals
     */
    public void setPrincipals(Properties principals) {
        this.principals = principals;
    }
    
    /**
     * Sets the principals as a list of strings of the format 
     * <code>username@domain:password-md5</code>.
     * @param principals
     */
    public void setPrincipals(Collection<String> principals) {
        this.principals = new Properties();
        for (String s: principals) {
            if (s == null) {
                continue;
            }
            int idx = s.indexOf(":");
            if (idx == -1) {
                continue;
            }
            String username = s.substring(0, idx);
            String hash = s.substring(idx + 1);
            username = username.trim();
            hash = hash.trim();
            if ("".equals(username)) {
                continue;
            }
            this.principals.setProperty(username, hash);
        }
    }

    public void setGroups(Map<Principal, Collection<String>> groups) {
        this.groups = groups;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public int getOrder() {
        return this.order;
    }

    public String getRealm() {
        return this.realm;
    }
    
    public void setRealm(String realm) {
        this.realm = realm;
    }

    public boolean validatePrincipal(Principal principal) {
        if (principal == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Validate principal: " + principal + ": false");
            }
            return false;
        }
        
        boolean hit = this.principals.getProperty(principal.getQualifiedName()) != null;
        if (logger.isDebugEnabled()) {
            logger.debug("Validate principal: " + principal + ": " + hit);
        }
        return hit;
    }

    public boolean validateGroup(Principal group) {
        if (this.groups == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Validate group: " + group + ": false");
            }
            return false;
        }
        boolean hit = this.groups.containsKey(group);
        if (logger.isDebugEnabled()) {
            logger.debug("Validate group: " + group.getQualifiedName() + ": " + hit);
        }
        return hit;
    }
    
    public String getMD5HashString(Principal principal) {
        return this.principals.getProperty(principal.getQualifiedName());
    }
    
    public boolean isMember(Principal principal, Principal group) {
        if (!this.groups.containsKey(group)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Check membership for principal " + principal
                        + ", group: " + group.getQualifiedName() + ": unknown group");
            }
            return false;
        }

        Collection<String> members = this.groups.get(group);
        boolean hit = members.contains(principal.getQualifiedName());
        if (logger.isDebugEnabled()) {
            logger.debug("Check membership for principal " + principal
                         + ", group: " + group.getQualifiedName() + ": " + hit);
        }
        return hit;
    }

    public void authenticate(Principal principal, String password)
        throws AuthenticationException {
        
        String hash = getMD5HashString(principal);
        String clientHash = 
            MD5.md5sum(principal.getQualifiedName() + ":" + this.realm + ":" + password); 

        if (hash == null || !hash.equals(clientHash)) {
            throw new AuthenticationException(
                "Authentication failed for principal " + principal.getQualifiedName()
                + ", " + "wrong credentials.");
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Successfully authenticated principal: " + principal);
        }

    }

    public Set<Principal> getMemberGroups(Principal principal) {
        Set<Principal> pGroups = new HashSet<Principal>();
        for (Map.Entry<Principal, Collection<String>> entry: this.groups.entrySet()) {
            Principal group = entry.getKey();
            Collection<String> members = entry.getValue();
            if (members.contains(principal.getQualifiedName())) {
                pGroups.add(group);      
            }
        }
        return pGroups;
    }
}

