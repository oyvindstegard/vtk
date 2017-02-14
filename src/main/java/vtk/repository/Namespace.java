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

/**
 * Represents a namespace with a prefix and URI.
 *
 * <p>Instances of this class are immutable.
 *
 * <p>Equality between instances is solely based on URI.
 */
public class Namespace {

    public static final Namespace DEFAULT_NAMESPACE = new Namespace(null, null);

    /**
     * @deprecated This namespace will be removed.
     */
    public static final Namespace CUSTOM_NAMESPACE = new Namespace("custom",
            "http://www.uio.no/vortex/custom-properties");

    public static final Namespace STRUCTURED_RESOURCE_NAMESPACE = new Namespace("resource",
            "http://www.uio.no/vrtx/__vrtx/ns/structured-resources");

    private final String prefix;
    private final String uri;

    public Namespace(String prefix, String uri) {
        this.prefix = prefix;
        this.uri = uri;
    }

    public Namespace(String uri) {
        this.prefix = uri;
        this.uri = uri;
    }

    public static Namespace getNamespace(String uri) {
        if (uri == null)
            return DEFAULT_NAMESPACE;
        if (uri.equals(STRUCTURED_RESOURCE_NAMESPACE.getUri()))
            return STRUCTURED_RESOURCE_NAMESPACE;
        if (uri.equals(CUSTOM_NAMESPACE.getUri()))
            return CUSTOM_NAMESPACE;

        return new Namespace(uri);
    }

    public static Namespace getNamespaceFromPrefix(String prefix) {
        if (prefix == null) {
            return DEFAULT_NAMESPACE;
        }
        if ("resource".equals(prefix)) {
            return STRUCTURED_RESOURCE_NAMESPACE;
        }
        if ("custom".equals(prefix)) {
            return CUSTOM_NAMESPACE;
        }

        return new Namespace(prefix); // XXX: unknown prefix
    }

    public String getPrefix() {
        return this.prefix;
    }

    public String getUri() {
        return this.uri;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Namespace))
            return false;
        Namespace ns = (Namespace) obj;

        String thisUri = (this.uri == null) ? "" : this.uri;
        String thatUri = (ns.getUri() == null) ? "" : ns.getUri();
        return thisUri.equals(thatUri);
    }

    @Override
    public int hashCode() {
        return this.uri == null ? 0 : this.uri.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (this.prefix != null) {
            sb.append(this.prefix).append(":");
        }

        if (this.uri == null) {
            sb.append("DEFAULT");
        } else {
            sb.append(this.uri);
        }
        return sb.toString();
    }

}
