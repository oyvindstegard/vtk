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
package vtk.web.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.Resource;
import vtk.repository.search.QueryParserFactory;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.Query;
import vtk.util.text.TextUtils;

public class FacetsQueryBuilder implements SearchComponentQueryBuilder {
    private Map<String, String> templates;
    private Map<String, String> tokenizedTemplates;
    private QueryParserFactory parserFactory;
    
    public FacetsQueryBuilder(QueryParserFactory parserFactory, Map<String, String> templates, 
            Map<String, String> tokenizedTemplates) {
        this.parserFactory = Objects.requireNonNull(parserFactory);
        this.templates = new HashMap<>(Objects.requireNonNull(templates));
        this.tokenizedTemplates = new HashMap<>(Objects.requireNonNull(tokenizedTemplates));
    }

    @Override
    public Query build(Resource base, HttpServletRequest request) {
        List<Query> queries = new ArrayList<>();
        for (String key: templates.keySet()) {
           String[] input = request.getParameterValues(key);
           if (input == null) {
               continue;
           }
           String template = templates.get(key);
           for (String value: input) {
               if ("".equals(value.trim())) continue;
               value = escape(escape(value, ' ', '\\'), ' ', '\\');
               String subQuery = template.replaceAll("\\{" + key + "\\}", value);
               queries.add(parserFactory.getParser().parse(subQuery));
           }
        }
        for (String key: tokenizedTemplates.keySet()) {
            String[] input = request.getParameterValues(key);
            if (input == null) {
                continue;
            }
            String template = tokenizedTemplates.get(key);
            for (String value: input) {
                if ("".equals(value.trim())) continue;
                
                for (String token: TextUtils.tokenizeWithPhrases(value)) {
                    token = escape(escape(token, ' ', '\\'), ' ', '\\');
                    String subQuery = template.replaceAll("\\{" + key + "\\}", token);
                    queries.add(parserFactory.getParser().parse(subQuery));
                    
                }
            }
         }
        if (queries.isEmpty()) {
            return null;
        }
        AndQuery andQuery = new AndQuery();
        queries.forEach(q -> andQuery.add(q));
        return andQuery;
    }
    
    private static String escape(String value, char separator, char escape) {
        StringBuilder escapedValue = new StringBuilder(value.length());
        
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == separator || c == escape) {
                escapedValue.append(escape).append(c);
            }
            else if (c == '\n') {
                escapedValue.append('\\').append('n');
            }
            else {
                escapedValue.append(c);
            }
        }

        return escapedValue.toString();
    }
}
