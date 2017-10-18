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
package vtk.repository;

import java.util.List;
import java.util.Optional;

import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.ConfigurablePropertySelect;
import vtk.repository.search.PropertySelect;

/**
 * A {@code PropertySet} represents a sparse form of the repository
 * {@link Resource} identified by the same URI, but with possibly only a subset
 * of metadata (properties and ACL) available.
 *
 * <p>
 * This interface is primarily used as type for repository
 * {@link Repository#search(java.lang.String, vtk.repository.search.Search) search results},
 * which vary in what data about resources is included, depending on search
 * parameters.
 */
public interface PropertySet extends Iterable<Property> {

    public static final String NAME_IDENTIFIER = "name";
    public static final String TYPE_IDENTIFIER = "type";
    public static final String URI_IDENTIFIER = "uri";

    /**
     * Get a resource identifier. (May not always be available,
     * depending on the backend that produced this property set.)
     * @return an optional resource identifier
     */
    public Optional<ResourceId> getResourceId();

    public Path getURI();

    public String getName();

    public String getResourceType();

    public List<Property> getProperties();

    public Property getProperty(Namespace namespace, String name);

    public Property getProperty(PropertyTypeDefinition type);
    
    public List<Property> getProperties(Namespace namespace);

    public Property getPropertyByPrefix(String prefix, String name);

    /**
     * Get optionally present ACL.
     *
     * <p>
     * When {@code PropertySet} instances are generated from repository index
     * search results, ACLs are only present via this method if selected for
     * inclusion in the search results, using search parameter
     * {@link PropertySelect#ALL} or a {@link ConfigurablePropertySelect} with
     * {@link ConfigurablePropertySelect#setIncludeAcl(boolean)} set to
     * {@code true}.
     *
     * <p>An empty result from this method does not mean the resource does not have an ACL.
     *
     * @return an optional ACL for the resource this property sets represents, always present
     * if selected for inclusion in search results.
     */
    public Optional<Acl> acl();

    /**
     * @return {@code true} if {@link #acl() } is present and is inherited, {@code false} otherwise.
     */
    public boolean isInheritedAcl();
}
