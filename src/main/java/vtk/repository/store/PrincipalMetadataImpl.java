/* Copyright (c) 2009, University of Oslo, Norway
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
package vtk.repository.store;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Basic impl of <code>PrincipalMetadata</code>.
 * 
 */
public class PrincipalMetadataImpl extends MetadataImpl implements PrincipalMetadata {

    public static final String DESCRIPTION_ATTRIBUTE = "description";
    public static final String UID_ATTRIBUTE = "uid";
    public static final String QNAME_ATTRIBUTE = "qname";
    private String qualifiedName;
    private PrincipalMetadataDAO dao;
    private boolean lazyLoaded = false;

    public PrincipalMetadataImpl(String qualifiedName) {
        if (qualifiedName == null) {
            throw new IllegalArgumentException("Qualified name cannot be null");
        }
        this.qualifiedName = qualifiedName;
        addAttributeValue(QNAME_ATTRIBUTE, qualifiedName);
    }
    
    public PrincipalMetadataImpl(String qualifiedName, PrincipalMetadataDAO dao) {
        this(qualifiedName);
        this.dao = dao;
    }

    @Override
    public Set<String> getAttributeNames() {
        maybeLazyLoad();
        return super.getAttributeNames();
    }

    @Override
    public List<Object> getValues(String attributeName) {
        maybeLazyLoad();
        return super.getValues(attributeName);
    }

    @Override
    public Object getValue(String attributeName) {
        maybeLazyLoad();
        return super.getValue(attributeName);
    }
    
    @Override
    public String getQualifiedName() {
        return this.qualifiedName;
    }

    @Override
    public String getUid() {
        maybeLazyLoad();
        String uid = (String) this.getValue(UID_ATTRIBUTE);
        if (uid == null) {
            uid = (String) this.getValue("username");
        }
        return uid;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder(getClass().getName());
        buffer.append("[qualifiedName=").append(qualifiedName).append(']');
        return buffer.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        return this.qualifiedName.equals(((PrincipalMetadataImpl) other).qualifiedName);
    }

    @Override
    public int hashCode() {
        return this.qualifiedName.hashCode();
    }

    @Override
    public Map<String, Object> toMap() {
        maybeLazyLoad();
        Map<String, Object> result = new HashMap<>();
        for (String name: getAttributeNames()) {
            result.put(name, getValues(name));
        }
        return result;
    }
    
    /**
     * Lazy load whatever we can.
     * This method will not overwrite already existing attributes.
     */
    private void maybeLazyLoad() {
        if (dao == null) return;
        
        if (!lazyLoaded) {
            synchronized (this) {
                // No problem if multiple threads queue up before this syncro point.
                try {
                    final PrincipalMetadata pm = dao.getMetadata(qualifiedName, null);
                    if (pm != null) {
                        Set<String> existingAttribs = super.getAttributeNames();
                        for (String loadedAttrib : pm.getAttributeNames()) {
                            if (!existingAttribs.contains(loadedAttrib)) {
                                super.setAttributeValues(loadedAttrib, pm.getValues(loadedAttrib));
                            }
                        }
                    }
                } catch (Throwable e) {}
                lazyLoaded = true;
            }
        }
    }

}
