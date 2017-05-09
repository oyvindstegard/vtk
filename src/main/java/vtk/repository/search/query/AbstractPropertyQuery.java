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
package vtk.repository.search.query;

import java.util.Objects;
import java.util.Optional;
import vtk.repository.Namespace;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;

public abstract class AbstractPropertyQuery implements PropertyQuery {

    private static final long serialVersionUID = 1421068352049823236L;

    private final String name;
    private final Namespace namespace;
    private final PropertyType.Type type;
    private final Optional<String> cva;

    public AbstractPropertyQuery(PropertyTypeDefinition propDef) {
        this(propDef.getName(), propDef.getNamespace(), propDef.getType());
    }
    public AbstractPropertyQuery(PropertyTypeDefinition propDef, String complexValueAttributeSpecifier) {
        this(propDef.getName(), propDef.getNamespace(), propDef.getType(), complexValueAttributeSpecifier);
    }
    public AbstractPropertyQuery(String name, Namespace ns, PropertyType.Type type) {
        this(name, ns, type, null);
    }
    public AbstractPropertyQuery(String name, Namespace ns, PropertyType.Type type, String complexValueAttributeSpecifier) {
        this.name = Objects.requireNonNull(name);
        this.namespace = Objects.requireNonNull(ns);
        this.type = Objects.requireNonNull(type);
        this.cva = Optional.ofNullable(complexValueAttributeSpecifier);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Namespace namespace() {
        return namespace;
    }

    @Override
    public PropertyType.Type type() {
        return type;
    }

    @Override
    public Optional<String> complexValueAttributeSpecifier() {
        return this.cva;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 79 * hash + Objects.hashCode(this.name);
        hash = 79 * hash + Objects.hashCode(this.namespace);
        hash = 79 * hash + Objects.hashCode(this.type);
        hash = 79 * hash + Objects.hashCode(this.cva);
        return hash;
    }

    protected String fieldsToString() {
        StringBuilder b = new StringBuilder();
        b.append("name=").append(name);
        b.append(", namespace=").append(namespace);
        b.append(", type=").append(type);
        if (cva.isPresent()) {
            b.append(", complexValueAttributeSpecifier=").append(cva.get());
        }
        return b.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AbstractPropertyQuery other = (AbstractPropertyQuery) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.namespace, other.namespace)) {
            return false;
        }
        if (this.type != other.type) {
            return false;
        }
        if (!Objects.equals(this.cva, other.cva)) {
            return false;
        }
        return true;
    }



}
