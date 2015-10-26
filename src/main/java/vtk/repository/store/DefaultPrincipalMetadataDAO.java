/* Copyright (c) 2015, University of Oslo, Norway
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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import vtk.security.Principal;

public class DefaultPrincipalMetadataDAO implements PrincipalMetadataDAO {

    @Override
    public PrincipalMetadata getMetadata(String qualifiedNameOrUid,
            Locale preferredLocale) {
        return PrincipalMetadata.defaultPrincipalMetadata(qualifiedNameOrUid);
    }

    @Override
    public PrincipalMetadata getMetadata(Principal principal,
            Locale preferredLocale) {
        return PrincipalMetadata.defaultPrincipalMetadata(principal);
    }

    @Override
    public List<PrincipalMetadata> search(PrincipalSearch search) {
        return Collections.emptyList();
    }

    @Override
    public List<PrincipalMetadata> getMetadata(Set<String> qualifiedNamesOrUids,
            Locale preferredLocale) {
        return qualifiedNamesOrUids.stream()
                .map(qname -> new PrincipalMetadataImpl(qname))
                .collect(Collectors.toList());
    }

    private static Set<String> domains;
    static {
        Set<String> tmp = new HashSet<>();
        tmp.add(Principal.PRINCIPAL_GROUP_DOMAIN);
        tmp.add(Principal.PRINCIPAL_LOCALHOST_DOMAIN);
        tmp.add(Principal.PRINCIPAL_USER_DOMAIN);
        tmp.add(Principal.PRINCIPAL_WEBID_DOMAIN);
        domains = Collections.unmodifiableSet(tmp);
    }

    @Override
    public Set<String> getSupportedPrincipalDomains() {
        return domains;
    }

}
