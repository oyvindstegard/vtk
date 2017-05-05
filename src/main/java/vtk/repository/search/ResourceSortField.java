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
 *  * Neither the field of the University of Oslo nor the names of its
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

import java.util.Locale;
import java.util.Objects;
import static vtk.repository.PropertySet.NAME_IDENTIFIER;
import static vtk.repository.PropertySet.TYPE_IDENTIFIER;
import static vtk.repository.PropertySet.URI_IDENTIFIER;
import vtk.repository.search.SortField.Direction;

/**
 * Sort on aspects of <code>Resource</code> which are not properties.
 */
public class ResourceSortField extends SortField {

    private static final long serialVersionUID = -6578826489822379082L;

    private final String identifier;
    
    public ResourceSortField(String id) {
        validateField(id);
        this.identifier = id;
    }
    
    public ResourceSortField(String id, Direction direction) {
        super(direction);
        validateField(id);
        this.identifier = id;
    }
    
    public ResourceSortField(String id, Direction direction, Locale locale) {
        super(direction, locale);
        validateField(id);
        this.identifier = id;
    }

    private void validateField(String type) throws IllegalArgumentException {
        if (!(NAME_IDENTIFIER.equals(type)
                || URI_IDENTIFIER.equals(type)
                || TYPE_IDENTIFIER.equals(type))) {
            throw new IllegalArgumentException("Identifier must be one of "
                    + NAME_IDENTIFIER + ", "
                    + URI_IDENTIFIER + ", "
                    + TYPE_IDENTIFIER);
        }
    }

    public String getIdentifier() {
        return this.identifier;
    }

    @Override
    public String toString() {
        return "ResourceSortField{identifier=" + identifier + ", direction=" + super.getDirection()
                + ", locale=" + super.getLocale() + "}";
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 97 * hash + Objects.hashCode(this.identifier);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ResourceSortField other = (ResourceSortField) obj;
        if (!Objects.equals(this.identifier, other.identifier)) {
            return false;
        }
        return true;
    }


    
}
