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
package vtk.security.web.clientaddr;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Pattern;

import vtk.repository.Path;

public final class ClientAddrAuthSpec {
    public final Pattern net;
    public final Path uri;
    public final String uid;
    public final Optional<Instant> validFrom;
    public final Optional<Instant> validTo;
    
    public static Builder builder() {
        return new Builder();
    }
    
    private ClientAddrAuthSpec(Pattern net, Path uri, String uid, 
            Optional<Instant> validFrom, Optional<Instant> validTo) {
        this.net = net;
        this.uri = uri;
        this.uid = uid;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "("
                + net + ", " + uri + ", " + uid + ", "
                + validFrom + ", " + validTo + ")";
    }
    
    public static final class Builder {
        private Optional<Pattern> net = Optional.empty();
        private Optional<Path> uri = Optional.empty();
        private Optional<String> uid = Optional.empty();
        private Optional<Instant> validFrom = Optional.empty();
        private Optional<Instant> validTo = Optional.empty();
        
        public Builder net(String net) {
            this.net = Optional.of(Pattern.compile(net));
            return this;
        }
        public Builder uri(String uri) {
            this.uri = Optional.of(Path.fromString(uri));
            return this;
        }
        public Builder uid(String uid) {
            this.uid = Optional.of(uid);
            return this;
        }
        public Builder validFrom(String validFrom) {
            ZonedDateTime time = ZonedDateTime.parse(
                    validFrom, DateTimeFormatter.ISO_ZONED_DATE_TIME);
            this.validFrom = Optional.of(time.toInstant());
            return this;
        }
        public Builder validTo(String validTo) {
            ZonedDateTime time = ZonedDateTime.parse(
                    validTo, DateTimeFormatter.ISO_ZONED_DATE_TIME);
            this.validTo= Optional.of(time.toInstant());
            return this;
        }

        public ClientAddrAuthSpec build() {
            if (!net.isPresent()) {
                throw new IllegalStateException("Field 'net' is required");
            }
            if (!uri.isPresent()) {
                throw new IllegalStateException("Field 'uri' is required");
            }
            if (!uid.isPresent()) {
                throw new IllegalStateException("Field 'uid' is required");
            }
            return new ClientAddrAuthSpec(net.get(), uri.get(), 
                    uid.get(), validFrom, validTo);
        }
        
    }
}