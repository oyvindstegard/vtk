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

/**
 * Encapsulates authorization details for {@link Repository} operations.
 *
 * XXX Currently not in use and only a proposition.
 */
public class Authorization implements Serializable {

    private static final long serialVersionUID = 2651435267026457462L;

    private final Optional<String> token;
    private final Optional<String> lockToken;

    private Authorization(String token, String lockToken) {
        this.token = Optional.ofNullable(token);
        this.lockToken = Optional.ofNullable(lockToken);
    }

    /**
     *
     * @return an empty Authorization with no associated principal token or lock.
     */
    public static Authorization empty() {
        return new Authorization(null, null);
    }

    /**
     * @param token a unique token identifying a single principal/user
     * @return an Authorization with a principal token
     */
    public static Authorization fromToken(String token) {
        return new Authorization(token, null);
    }

    /**
     * @param token a unique token identifying a single principal/user, or {@code null} for no particular token
     * @param lockToken a token identifying a resource lock, or {@code null} for no particular token
     * @return an Authorization with a principal token and a lock token
     */
    public static Authorization fromTokens(String token, String lockToken) {
        return new Authorization(token, lockToken);
    }

    /**
     * @return the optional principal token
     */
    public Optional<String> token() {
        return token;
    }

    /**
     *
     * @return the optional lock token
     */
    public Optional<String> lockToken() {
        return lockToken;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + Objects.hashCode(this.token);
        hash = 97 * hash + Objects.hashCode(this.lockToken);
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
        final Authorization other = (Authorization) obj;
        if (!Objects.equals(this.token, other.token)) {
            return false;
        }
        if (!Objects.equals(this.lockToken, other.lockToken)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Authorization{" + "token=" + token.orElse(null) + ", lockToken=" + lockToken.orElse(null) + '}';
    }

}
