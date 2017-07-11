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
package vtk.web.display.autocomplete;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.Path;
import vtk.web.RequestContext;

public abstract class AutoCompleteController implements Controller {

    protected static final char SUGGESTION_DELIMITER = '\n';
    protected static final char FIELD_SEPARATOR = ';';
    protected static final char FIELD_SEPARATOR_ESCAPE = '\\';
    protected static final String PARAM_QUERY = "q";
    protected static final String PARAM_SCOPE_URI_OVERRIDE = "scope";
    protected static final String PARAM_PREFERRED_LANG = "lang";
    protected static final String RESPONSE_CONTENT_TYPE = "text/plain;charset=utf-8";

    private boolean useRootContext;

    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        String query = request.getParameter(PARAM_QUERY);
        if (query == null) {
            return null; // Global policy: No query results in no auto-complete
            // suggestions
        }

        CompletionContext context = getCompletionContext(request);

        List<Suggestion> suggestions = this.getAutoCompleteSuggestions(query, context);

        if (suggestions != null) {
            writeSuggestions(suggestions, response);
        }

        return null;
    }

    protected void writeSuggestions(List<Suggestion> suggestions, HttpServletResponse response) throws IOException {
        response.setContentType(RESPONSE_CONTENT_TYPE);
        PrintWriter writer = response.getWriter();
        for (Suggestion suggestion : suggestions) {
            writer.print(suggestion);
            writer.print(SUGGESTION_DELIMITER);
        }
        writer.flush();
        writer.close();
    }

    protected CompletionContext getCompletionContext(HttpServletRequest request) {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        String token = requestContext.getSecurityToken();
        Path contextUri = getContextUri(request);
        Locale preferredLocale = getPreferredLocale(request);
        return new CompletionContextImpl(request, contextUri, preferredLocale, token);
    }

    private Path getContextUri(HttpServletRequest request) {
        Path contextUri = null;

        if (this.useRootContext) {
            return Path.ROOT;
        }

        String contextParam = request.getParameter(PARAM_SCOPE_URI_OVERRIDE);

        // Check if it's a complete url
        try {
            URL url = new URL(contextParam);
            contextParam = url.getPath();
        } catch (MalformedURLException e) {
            // Ignore
        }

        if (contextParam != null) {
            // Remove parameters
            if (contextParam.contains("?")) {
                contextParam = contextParam.substring(0, contextParam.indexOf("?"));
            }
            // Be gentle and snip trailing slash if necessary
            if (contextParam.endsWith("/") && contextParam.length() > 1) {
                contextParam = contextParam.substring(0, contextParam.length() - 1);
            }
        }

        try {
            contextUri = Path.fromString(contextParam);
        } catch (IllegalArgumentException ie) {
            // Ignore.
        }

        if (contextUri == null) {
            // No luck (invalid path or missing override param), try request
            // context ..
            RequestContext requestContext = RequestContext.getRequestContext(request);
            contextUri = requestContext.getResourceURI();
        }

        return contextUri;
    }

    private Locale getPreferredLocale(HttpServletRequest request) {
        // Try preferred language param
        String lang = request.getParameter(PARAM_PREFERRED_LANG);
        if (lang != null && lang.length() == 2) {
            // Construct locale from two-letter lang code
            return new Locale(lang.toLowerCase());
        }

        return null; // No preference
    }

    /**
     * Optionally implemented by subclasses to provide autocomplete suggestions.
     * Only useful if handleRequest is *not* overridden.
     */
    protected abstract List<Suggestion> getAutoCompleteSuggestions(String query, CompletionContext context);

    /**
     * Wrap som completion-context concerns into a class..
     */
    private static final class CompletionContextImpl implements CompletionContext {
        private Path contextUri;
        private Locale preferredLocale;
        private String token;
        private HttpServletRequest request;

        private CompletionContextImpl(HttpServletRequest request, Path contextUri, 
                Locale preferredLocale, String token) {
            this.request = request;
            this.contextUri = contextUri;
            this.preferredLocale = preferredLocale;
            this.token = token;
        }

        @Override
        public Locale getPreferredLocale() {
            return this.preferredLocale;
        }

        @Override
        public Path getContextUri() {
            return this.contextUri;
        }

        @Override
        public String getToken() {
            return this.token;
        }

        @Override
        public HttpServletRequest request() {
            return request;
        }
    }

    /**
     * Class representing one single suggestion composed of a list of fields.
     */
    protected static final class Suggestion {
        private Object[] fields;

        public Suggestion(int fields) {
            this.fields = new Object[fields];
        }

        public void setField(int field, Object value) {
            this.fields[field] = value;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < this.fields.length; i++) {
                // Render null as empty string instead of "null"
                if (this.fields[i] != null) {
                    builder.append(escapeFieldValue(this.fields[i].toString()));
                }

                if (i < this.fields.length - 1) {
                    builder.append(FIELD_SEPARATOR);
                }
            }
            return builder.toString();
        }

        private String escapeFieldValue(String value) {
            StringBuilder escapedValue = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                case FIELD_SEPARATOR:
                case FIELD_SEPARATOR_ESCAPE:
                    escapedValue.append(FIELD_SEPARATOR_ESCAPE);
                default:
                    escapedValue.append(c);
                }
            }

            return escapedValue.toString();
        }
    }

    public void setUseRootContext(boolean useRootContext) {
        this.useRootContext = useRootContext;
    }

}
