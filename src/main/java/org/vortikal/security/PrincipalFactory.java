/* Copyright (c) 2008, University of Oslo, Norway
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
package org.vortikal.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.vortikal.repository.store.PrincipalMetadata;
import org.vortikal.repository.store.PrincipalMetadataDAO;
import org.vortikal.repository.store.PrincipalSearch;
import org.vortikal.repository.store.PrincipalSearch.SearchType;
import org.vortikal.repository.store.PrincipalSearchImpl;
import org.vortikal.repository.store.UnsupportedPrincipalDomainException;
import org.vortikal.security.Principal.Type;

public class PrincipalFactory {

    private final Log logger = LogFactory.getLog(PrincipalFactory.class);

    public static final String NAME_ALL = "pseudo:all";

    public static Principal ALL = new PrincipalImpl(NAME_ALL);

    // These daos will only be used if configured.
    private PrincipalMetadataDAO principalMetadataDao;
    private PrincipalMetadataDAO personDocumentPrincipalMetadataDao;

    public Principal getPrincipal(String id, Type type) throws InvalidPrincipalException {
        return this.getPrincipal(id, type, true);
    }

    public Principal getPrincipal(String id, Type type, boolean includeMetadata) {
        return this.getPrincipal(id, type, includeMetadata, null);
    }

    public Principal getPrincipal(String id, Type type, boolean includeMetadata, Locale preferredLocale)
            throws InvalidPrincipalException {

        if (type == null) {
            throw new InvalidPrincipalException("Principal must have a type");
        }

        if (type == Type.PSEUDO) {
            return getPseudoPrincipal(id);
        }

        if (id == null) {
            throw new InvalidPrincipalException("Tried to get null principal");
        }

        id = id.trim();

        if (id.equals(""))
            throw new InvalidPrincipalException("Tried to get \"\" (empty string) principal");

        PrincipalImpl principal = new PrincipalImpl(id, type);
        if (principal.getType() == Type.USER && includeMetadata && this.principalMetadataDao != null) {
            // Set metadata for principal if requested and we are able to fetch
            // it
            try {
                PrincipalMetadata metadata = this.principalMetadataDao.getMetadata(principal, preferredLocale);
                if (metadata != null) {
                    principal.setDescription((String) metadata.getValue(PrincipalMetadata.DESCRIPTION_ATTRIBUTE));
                    principal.setURL((String) metadata.getValue(PrincipalMetadata.URL_ATTRIBUTE));
                    principal.setMetadata(metadata);
                }
            } catch (UnsupportedPrincipalDomainException d) {
                // Ignore
            } catch (Exception e) {
                logger.warn("Exception while fetching principal metadata", e);
            }
        }

        return principal;
    }

    public List<Principal> search(final String filter, final Type type) {
        return this.search(filter, type, null);
    }

    public List<Principal> search(final String filter, final Type type, final SearchType searchType) {
        List<Principal> retval = null;

        if (this.principalMetadataDao != null) {

            PrincipalSearch search = null;
            if (searchType == null) {
                search = new PrincipalSearchImpl(type, filter);
            } else {
                search = new PrincipalSearchImpl(type, filter, null, searchType);
            }

            try {
                List<PrincipalMetadata> results = this.principalMetadataDao.search(search, null);
                if (results != null) {
                    retval = new ArrayList<Principal>(results.size());
                    for (PrincipalMetadata metadata : results) {
                        PrincipalImpl principal = new PrincipalImpl(metadata.getQualifiedName());
                        principal.setType(type);
                        principal.setDescription((String) metadata.getValue(PrincipalMetadata.DESCRIPTION_ATTRIBUTE));
                        principal.setURL((String) metadata.getValue(PrincipalMetadata.URL_ATTRIBUTE));
                        principal.setMetadata(metadata);
                        retval.add(principal);
                    }
                }
            } catch (Exception e) {
            } // Just keep old behaviour of not propagating
              // any size limit exceeded exceptions from this method ...
              // XXX remove/refactor this method or fixup client code to handle
              // it.
        }

        return retval;
    }

    // XXX [rezam] Separate implementation because I'm to scared to mess with
    // existing setup. So scared...
    public Principal getPrincipalDocument(String id, Locale preferredLocale) {
        if (this.personDocumentPrincipalMetadataDao != null) {
            PrincipalImpl principal = new PrincipalImpl(id, Type.USER);
            PrincipalMetadata metadata = this.personDocumentPrincipalMetadataDao
                    .getMetadata(principal, preferredLocale);
            if (metadata != null) {
                Object descriptionObj = metadata.getValue(PrincipalMetadata.DESCRIPTION_ATTRIBUTE);
                if (descriptionObj != null) {
                    principal.setDescription(descriptionObj.toString());
                }
                Object urlObj = metadata.getValue(PrincipalMetadata.URL_ATTRIBUTE);
                if (urlObj != null) {
                    principal.setURL(urlObj.toString());
                }
                principal.setMetadata(metadata);
                return principal;
            }
        }
        // Not configured to search for documents, or no document for principal
        // found
        return null;
    }

    private Principal getPseudoPrincipal(String name) throws InvalidPrincipalException {
        if (NAME_ALL.equals(name))
            return ALL;
        throw new InvalidPrincipalException("Pseudo principal with name '" + name + "' doesn't exist");
    }

    public void setPrincipalMetadataDao(PrincipalMetadataDAO principalMetadataDao) {
        this.principalMetadataDao = principalMetadataDao;
    }

    public void setPersonDocumentPrincipalMetadataDao(PrincipalMetadataDAO personDocumentPrincipalMetadataDao) {
        this.personDocumentPrincipalMetadataDao = personDocumentPrincipalMetadataDao;
    }

}
