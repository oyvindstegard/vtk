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
package vtk.web.display.autocomplete;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.Before;
import org.junit.Test;

import vtk.repository.Path;
import vtk.repository.store.PrincipalMetadataImpl;
import vtk.security.Principal;
import vtk.security.Principal.Type;
import vtk.security.PrincipalImpl;
import vtk.web.display.autocomplete.AutoCompleteController.Suggestion;

public class PrincipalAutoCompleteControllerTest {

    // Definition copied from AbstractLdapAccessor in Vortex
    private static final String USER_SCOPED_AFFILIATION_ATTRIBUTE = "uioPersonScopedAffiliation1";

    private PrincipalMetadataImpl fooMeta = getTestPrincipalMetadata("foo@uio.no", "Foo User",
            Arrays.asList("PRIMARY:ANSATT/vitenskapelig@120000,900000", "secondary:TILKNYTTET/bilag@140000,900000"),
            false);

    private PrincipalMetadataImpl barMeta = getTestPrincipalMetadata("bar@uio.no", "Bar User",
            null,
            false);

    private Principal foo = getTestPrincipal(fooMeta);
    private Principal bar = getTestPrincipal(barMeta);

    private CompletionContext context = new CompletionContext() {
        @Override
        public Path getContextUri() {
            return null;
        }
        @Override
        public Locale getPreferredLocale() {
            return null;
        }
        @Override
        public String getToken() {
            return null;
        }
    };

    @Test
    public void testSuggestionsShouldIncludeAreaCodes() {
        VocabularyDataProvider<Principal> dataProvider = new VocabularyDataProvider<Principal>() {
            @Override
            public List<Principal> getCompletions(String input, CompletionContext context) {
                return Arrays.asList(foo, bar);
            };
            @Override
            public List<Principal> getCompletions(CompletionContext context) {
                return null;
            };
        };

        PrincipalAutoCompleteController controller = new PrincipalAutoCompleteController();
        controller.setInvert(false);
        controller.setDataProvider(dataProvider);
        List<Suggestion> suggestions = controller.getAutoCompleteSuggestions("foo", context);
        assertEquals("size", 2, suggestions.size());
        assertEquals("foo", "Foo User;foo;http://www.uio.no?vrtx=person-view&uid=foo;120000,900000", suggestions.get(0).toString());
        assertEquals("bar", "Bar User;bar;http://www.uio.no?vrtx=person-view&uid=bar;", suggestions.get(1).toString());
    }

    private PrincipalImpl getTestPrincipal(PrincipalMetadataImpl meta) {
        PrincipalImpl principal = new PrincipalImpl(meta.getQualifiedName(), Type.USER);
        principal.setMetadata(meta);
        principal.setDescription((String)meta.getValue(PrincipalMetadataImpl.DESCRIPTION_ATTRIBUTE));
        principal.setURL((String)meta.getValue(PrincipalMetadataImpl.URL_ATTRIBUTE));
        return principal;
    }

    private PrincipalMetadataImpl getTestPrincipalMetadata(String qualifiedName, String desc, List<String> affiliations,
            boolean isDoc) {
        PrincipalMetadataImpl testPm = new PrincipalMetadataImpl(qualifiedName);
        String uid = getUid(qualifiedName);
        testPm.addAttributeValue(PrincipalMetadataImpl.DESCRIPTION_ATTRIBUTE, desc);
        testPm.addAttributeValue(PrincipalMetadataImpl.UID_ATTRIBUTE, uid);
        String url = isDoc ? "http://www.uio.no/person/".concat(uid) : "http://www.uio.no?vrtx=person-view&uid="
                .concat(uid);
        testPm.addAttributeValue(PrincipalMetadataImpl.URL_ATTRIBUTE, url);
        if (affiliations != null) {
            List<Object> affs = new ArrayList<Object>();
            affs.addAll(affiliations);
            testPm.setAttributeValues(USER_SCOPED_AFFILIATION_ATTRIBUTE, affs);
        }
        return testPm;
    }

    // Definition copied from QuerySearchUtil.getUid() in Vortex
    private static String getUid(String qualifiedName) {
        int domainDelimiterIdx = qualifiedName.indexOf('@');
        if (domainDelimiterIdx == -1) {
            return qualifiedName;
        }
        return qualifiedName.substring(0, domainDelimiterIdx);
    }
}
