/* Copyright (c) 2008, University of Oslo, Norway
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
package vtk.security;

import vtk.repository.store.Metadata;
import vtk.repository.store.PrincipalMetadata;
import vtk.repository.store.PrincipalMetadataImpl;

public class PrincipalImpl implements Principal {

    private static final long serialVersionUID = -8377523842714329076L;

    private static final String DOMAIN_DELIMITER = "@";

    private String name;
    private String qualifiedName;
    private String domain;
    private String url;
    private Type type;
    private String description;
    private PrincipalMetadata metadata;

    PrincipalImpl(String name) {
        this.name = name;
        this.qualifiedName = name;
        this.domain = "pseudo:";
        this.type = Type.PSEUDO;
    }
    
    public PrincipalImpl(String id, Type type) throws InvalidPrincipalException {

        this.type = type;

        if (id.startsWith(DOMAIN_DELIMITER)) {
            throw new InvalidPrincipalException("Invalid principal id: " + id + ": "
                    + "must not start with delimiter: '" + DOMAIN_DELIMITER + "'");
        }
        if (id.endsWith(DOMAIN_DELIMITER)) {
            throw new InvalidPrincipalException("Invalid principal id: " + id + ": " + "must not end with delimiter: '"
                    + DOMAIN_DELIMITER + "'");
        }

        if (id.indexOf(DOMAIN_DELIMITER) != id.lastIndexOf(DOMAIN_DELIMITER)) {
            throw new InvalidPrincipalException("Invalid principal id: " + id + ": "
                    + "must not contain more that one delimiter: '" + DOMAIN_DELIMITER + "'");
        }

        /*
         * Initialize name, domain and qualifiedName to default values matching
         * a setup "without" domains:
         */
        this.name = id;
        this.qualifiedName = id;

        String defDomain = (type == Principal.Type.GROUP) ? Principal.PRINCIPAL_GROUP_DOMAIN
                : Principal.PRINCIPAL_USER_DOMAIN;

        if (id.indexOf(DOMAIN_DELIMITER) > 0) {

            /* id is a fully qualified principal with a domain part: */
            this.domain = id.substring(id.indexOf(DOMAIN_DELIMITER) + 1);

            if (defDomain != null && defDomain.equals(domain)) {
                /*
                 * In cases where domain equals default domain, strip the domain
                 * part off the name:
                 */
                name = id.substring(0, id.indexOf(DOMAIN_DELIMITER));
            }

        } else if (defDomain != null) {

            /*
             * id is not a fully qualified principal, but since we have a
             * default domain, we append it:
             */
            domain = defDomain;
            qualifiedName = name + DOMAIN_DELIMITER + domain;
        }

    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final PrincipalImpl other = (PrincipalImpl) obj;
        return this.qualifiedName.equals(other.qualifiedName);
    }

    @Override
    public int hashCode() {
        return this.qualifiedName.hashCode();
    }

    /**
     * Gets the name of the principal. Cannot be <code>null</code>.
     * 
     * @return If the domain equals the principalManager's defaultDomain it
     *         returns the unqualified name, otherwise it returns the qualified
     *         name
     */
    @Override
    public String getName() {
        return this.name;
    }

    /**
     * Gets the fully qualified name of the principal. If domain is null, just
     * the user name, otherwise 'user@domain'
     * 
     * @return the fully qualified name of the principal
     */
    @Override
    public String getQualifiedName() {
        return this.qualifiedName;
    }

    /**
     * Gets the domain of the principal. May be <code>null</code>.
     * 
     * @return the domain of the principal, or <code>null</code> if it has none
     */
    @Override
    public String getDomain() {
        return this.domain;
    }

    @Override
    public String toString() {
        return this.qualifiedName;
    }

    /**
     * Gets the unqualified name of the principal, stripped of domain
     * 
     * @return the unqualified name of the principal
     */
    @Override
    public String getUnqualifiedName() {
        if (this.domain == null)
            return this.name;
        // FIXME: principalmanager's delimiter shouldn't be here!
        return this.qualifiedName.substring(0, this.qualifiedName.indexOf("@"));
    }

    @Override
    public boolean isUser() {
        return this.type == Principal.Type.USER;
    }

    @Override
    public Type getType() {
        return this.type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    @Override
    public int compareTo(Principal other) {
        if (other == null) {
            throw new IllegalArgumentException("Cannot compare to a null value");
        } else if (this.type != other.getType()) {
            throw new IllegalArgumentException("Connot compare to a different principal type");
        }
        return this.qualifiedName.compareTo(other.getQualifiedName());
    }

    @Override
    public String getDescription() {
        if (description == null) {
            // Try looking in configured metadata
            if (metadata != null) {
                String descAttr = (String)metadata.getValue(PrincipalMetadataImpl.DESCRIPTION_ATTRIBUTE);
                if (descAttr != null) {
                    description = descAttr;
                }
            }
        }
        
        if (description != null) {
            return description;
        }
        
        return name;
    }

    @Override
    public String getURL() {
        if (url == null) {
            // Try looking in configured metadata
            if (metadata != null) {
                String urlAttr = (String)metadata.getValue(Metadata.URL_ATTRIBUTE);
                if (urlAttr != null) {
                    url = urlAttr;
                }
            }
        }
        
        return url;
    }

    /**
     * @return the metadata
     */
    @Override
    public PrincipalMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(PrincipalMetadata metadata) {
        this.metadata = metadata;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setURL(String url) {
        this.url = url;
    }
}
