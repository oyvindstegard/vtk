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
import java.util.List;

import org.springframework.beans.factory.annotation.Required;
import vtk.security.Principal;
import vtk.security.Principal.Type;

public class PrincipalAutoCompleteController extends AutoCompleteController {

    private VocabularyDataProvider<Principal> dataProvider;
    private boolean invert;

    @Override
    protected List<Suggestion> getAutoCompleteSuggestions(String input, CompletionContext context) {

        List<Principal> completions = this.dataProvider.getCompletions(input, context);

        List<Suggestion> suggestions = new ArrayList<Suggestion>(completions.size());
        for (Principal principal : completions) {
            Suggestion suggestion = new Suggestion(3);

            // XXX: Only list uio.no and pseudo users
            if (principal.isUser()) {
                if (!(principal.getDomain().equals(Principal.PRINCIPAL_USER_DOMAIN) || principal.getDomain().equals(
                        "pseudo:"))) {
                    continue;
                }
            }
            // XXX: Special treatment for webid-groups and users
            if (principal.getQualifiedName().contains("webid.uio.no")) {
                if (principal.getType() == Type.GROUP || principal.getType() == Type.PSEUDO) {
                    suggestion.setField(this.invert ? 0 : 1, principal.getUnqualifiedName() + "@webid.uio.no");
                    suggestion.setField(this.invert ? 1 : 0, principal.getDescription());
                    suggestion.setField(2, principal.getURL());
                } else {
                    continue;
                }
            } else {
                suggestion.setField(this.invert ? 0 : 1, principal.getUnqualifiedName());
                suggestion.setField(this.invert ? 1 : 0, principal.getDescription());
                suggestion.setField(2, principal.getURL());
            }

            suggestions.add(suggestion);
        }

        return suggestions;
    }

    @Required
    public void setDataProvider(VocabularyDataProvider<Principal> dataProvider) {
        this.dataProvider = dataProvider;
    }

    public void setInvert(boolean invert) {
        this.invert = invert;
    }

}
