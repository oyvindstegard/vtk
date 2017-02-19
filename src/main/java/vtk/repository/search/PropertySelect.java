/* Copyright (c) 2006–2015, University of Oslo, Norway
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
package vtk.repository.search;

import vtk.repository.resourcetype.PropertyTypeDefinition;

/**
 * TODO consider renaming this field selection interface, since it's not only about
 * resource properties anymore.
 */
public interface PropertySelect {

    /**
     * A selector which selects all properties available for each result.
     * 
     * <p>This selector does not include ACLs.
     */
    public static final PropertySelect ALL_PROPERTIES = new PropertySelect() {
        @Override
        public boolean isIncludedProperty(PropertyTypeDefinition propertyDefinition) {
            return true;
        }
        @Override
        public String toString() {
            return "PropertySelect{ALL_PROPERTIES}";
        }
        @Override
        public boolean isIncludeAcl() {
            return false;
        }
    };
    
    /**
     * A selector which selects all properties available and ACL for each result.
     */
    public static final PropertySelect ALL = new PropertySelect() {
        @Override
        public boolean isIncludedProperty(PropertyTypeDefinition propertyDefinition) {
            return true;
        }
        @Override
        public String toString() {
            return "PropertySelect{ALL}";
        }
        @Override
        public boolean isIncludeAcl() {
            return true;
        }
    };
    
    /**
     * A selector which selects NO properties or ACL.
     */
    public static final PropertySelect NONE = new PropertySelect() {
        @Override
        public boolean isIncludedProperty(PropertyTypeDefinition propertyDefinition) {
            return false;
        }
        @Override
        public String toString() {
            return "PropertySelect{NONE}";
        }
        @Override
        public boolean isIncludeAcl() {
            return false;
        }
    };
    
    /**
     * @param def the property
     * @return <code>true</code> if the given property should be selected for
     * loading in search results.
     */
    public boolean isIncludedProperty(PropertyTypeDefinition def);
    
    /**
     * Should return true if ACL should be included in result.
     * @return 
     */
    public boolean isIncludeAcl();

}

