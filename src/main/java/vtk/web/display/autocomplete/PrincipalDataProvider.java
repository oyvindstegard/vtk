/* Copyright (c) 2009, University of Oslo, Norway
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
package vtk.web.display.autocomplete;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.store.PrincipalSearch.SearchType;
import vtk.security.Principal;
import vtk.security.Principal.Type;
import vtk.security.PrincipalFactory;
import vtk.security.PrincipalManager;

public class PrincipalDataProvider implements VocabularyDataProvider<Principal> {

    private static final Logger logger = LoggerFactory.getLogger(PrincipalDataProvider.class);

    private Type type;
    private PrincipalFactory principalFactory;
    private PrincipalManager principalManager;

    public List<Principal> getCompletions(CompletionContext context) {
        return null; // We require input
    }

    public List<Principal> getCompletions(String input, CompletionContext context) {

        Set<Principal> result = new HashSet<Principal>(0);

        try {
            SearchType searchType = null;
            if (Type.USER.equals(type)) {
                Principal singleUser = this.principalFactory.getPrincipal(input, type);
                if (this.principalManager.validatePrincipal(singleUser)) {
                    result.add(singleUser);
                }
                searchType = SearchType.FULL_USER_SEARCH;
            }
            List<Principal> searchResult = principalFactory.search(input, type, searchType);
            if (searchResult != null && searchResult.size() > 0) {
                result.addAll(searchResult);
            }
        } catch (Exception e) {
            logger.error("An error occured while getting prefixcompilations", e);
        }

        List<Principal> l = new ArrayList<Principal>(result);
        Collections.sort(l, new PrincipalComparator());
        return l;
    }

    private final class PrincipalComparator implements Comparator<Principal> {

        public int compare(Principal p1, Principal p2) {
            if (Type.USER.equals(type)) {
                return p1.getDescription().compareToIgnoreCase(p2.getDescription());
            }
            return p1.getUnqualifiedName().compareToIgnoreCase(p2.getUnqualifiedName());
        }

    }

    @Required
    public void setType(Principal.Type type) {
        this.type = type;
    }

    @Required
    public void setPrincipalFactory(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }

    public void setPrincipalManager(PrincipalManager principalManager) {
        this.principalManager = principalManager;
    }

}
