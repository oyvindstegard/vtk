/* Copyright (c) 2017, University of Oslo, Norway
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

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import vtk.repository.resourcetype.PropertyType;

/**
 * A stable and immutable identifier for a single resource in a single repository.
 *
 * <p>The ID is globally unique across different VTK repositories if a {@link #repositoryId() repository
 * ID} is part of it.
 *
 */
public final class ResourceId implements Serializable {

    private static final long serialVersionUID = -3070500759810734554L;

    private static final Pattern ID_PATTERN = Pattern.compile("(([^_]+)_)?([0-9]+)");

    private final Optional<String> repositoryId;
    private final int numericId;

    /**
     * Attempts to create an id from a string possibly prefixed with a repository id.
     *
     * <p>The expected format of this string is the same as present in property
     * {@link PropertyType#EXTERNAL_ID_PROP_NAME externalId}, however, the repository id
     * prefix may be omitted.
     *
     * <p>Examples of valid IDs:
     * <ul>
     *   <li>{@code vtkframework.org_1000}
     *   <li>{@code 1000}
     * </ul>
     *
     * @param id
     * @return a resource id
     * @throws IllegalArgumentException if the ID string has wrong format
     * @throws NullPointerException if the ID string is {@code null}
     */
    public static ResourceId fromString(String id) throws IllegalArgumentException {
        Matcher m = ID_PATTERN.matcher(id);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid resource ID: " + id);
        }
        return new ResourceId(m.group(2), Integer.parseInt(m.group(3)));
    }

    public Optional<String> repositoryId() {
        return repositoryId;
    }

    public int numericId() {
        return numericId;
    }

    /**
     * Construct a resource id consisting of a numeric resource identifier.
     *
     * @param numericId number &gt;= 0
     */
    public ResourceId(int numericId) {
        this(null, numericId);
    }

    /**
     * Construct a resource id consisting of a repository id and a numeric resource
     * identifier.
     *
     * @param repositoryId repository id, or {@code null} if not present
     * @param numericId number &gt;= 0
     */
    public ResourceId(String repositoryId, int numericId) {
        if (numericId < 0) {
            throw new IllegalArgumentException("Illegal negative numeric id: " + numericId);
        }
        this.repositoryId = Optional.ofNullable(repositoryId);
        this.numericId = numericId;
    }

    @Override
    public String toString() {
        return repositoryId.isPresent() ? repositoryId.get() + "_" + numericId : String.valueOf(numericId);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.repositoryId);
        hash = 41 * hash + this.numericId;
        return hash;
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
        final ResourceId other = (ResourceId) obj;
        if (this.numericId != other.numericId) {
            return false;
        }
        if (!Objects.equals(this.repositoryId, other.repositoryId)) {
            return false;
        }
        return true;
    }

}
