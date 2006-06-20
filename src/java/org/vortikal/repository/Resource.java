/* Copyright (c) 2006, University of Oslo, Norway
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
package org.vortikal.repository;


import java.util.Date;
import java.util.List;

import org.vortikal.repository.resourcetype.ResourceTypeDefinition;
import org.vortikal.security.Principal;

public interface Resource extends PropertySet {

    public boolean isOfType(ResourceTypeDefinition type);

    public boolean isAuthorized(RepositoryAction privilege, Principal principal);

    public Property createProperty(Namespace namespace, String name);
    
//    public void deleteProperty(Property property);
    
    public void removeProperty(Namespace namespace, String name);
    
    // XXX: What to do about this?! Old client code operates with a divison of know/unknown props.
    public List getOtherProperties();
    
    /**
     * Gets a resource's serial string.  A serial string is a unique
     * string which changes on each write-operation on the resource,
     * and may be used for instance as an ETag on that resource.
     *
     */
    public String getSerial();

    /**
     * 
     * @return The ETag String 
     */
    public String getEtag();
    
    /**
     * Returns the uri of the parent.
     *
     * @return the URI
     */
    public String getParent();

    /**
     * Gets this resource's name.
     *
     * @return the name
     */
    public String getName();
    
    /**
     * Gets the size of a resource.
     *
     * @return the size of the resource's content (in bytes)
     */
    public long getContentLength();
    
    /**
     * Gets the list of children. These are "soft" references, listing
     * the URIs of the children.
     *
     * @return the children's URIs, or <code>null</code> if the
     * resource is not a collection
     */
    public String[] getChildURIs();

    /**
     * Returns a lock for the resource, if one is set.
     *
     * @return a <code>Lock</code> object representing
     * the active lock that are set on the resource
     */
    public Lock getLock();
    
    public Acl getAcl();
    
    /**
     * Determines whether this resource is a collection.
     *
     * @return a <code>true</code> if this resource is a collection,
     * <code>false</code> otherwise
     */
    public boolean isCollection();
    
    /**
     * Gets a resource's owner.
     *
     */
    public Principal getOwner();
    
    public Principal getCreatedBy();

    /**
     * Gets the name of the principal that last modified either the
     * content or the properties of this resource.
     *
     * @return the name of the principal
     */
    public Principal getModifiedBy();
    
    /**
     * Gets the name of the principal that last modified the
     * resource's content.
     */
    public Principal getContentModifiedBy();

    /**
     * Gets the name of the principal that last modified the
     * resource's properties.
     */
    public Principal getPropertiesModifiedBy();
    
    /**
     * Gets a resource's content language.
     *
     * @return the locale string (if it has one, <code>null</code> otherwise)
     */
    public String getContentLanguage();

    /**
     * Gets this resource's display name.
     *
     * @return the display name
     */
    public String getDisplayName();

    /**
     * Gets a resource's content (MIME) type.
     *
     * @return the content type
     */
    public String getContentType();
    
    /**
     * Gets the creation time for this resource.
     *
     * @return the <code>Date</code> object representing the creation
     * time
     */
    public Date getCreationTime();

    /**
     * Gets the date of this resource's last modification. The date
     * returned is either that of the
     * <code>getContentLastModified()</code> or the
     * <code>getPropertiesLastModified()</code> method, depending on
     * which one is the most recent.
     *
     * @return the time of last modification
     */
    public Date getLastModified();

    /**
     * Gets the date of the last property modification.
     */
    public Date getContentLastModified();

    /**
     * Gets the date of the last content modification.
     */
    public Date getPropertiesLastModified();


    /**
     * Gets the user specified character encoding.
     */
    public String getUserSpecifiedCharacterEncoding();

    /**
     * Sets the user specified character encoding.
     */
    public void setUserSpecifiedCharacterEncoding(String characterEncoding);

    /**
     * Gets the evaluated ('guessed') character encoding.
     */
    public String getGuessedCharacterEncoding();

    /**
     * Gets the character encoding. This value is only relevant if the
     * resource type is 'textResource'. It depends on the value of
     * <code>getEvaluatedCharacterEncoding()</code> and
     * <code>getUserSpecifiedCharacterEncoding()</code>.
     */
    public String getCharacterEncoding();
    
    public void setContentLocale(String locale);

    public void setContentType(String string);

    public void setOwner(Principal principal);

    public void setDisplayName(String displayName);
    
}
