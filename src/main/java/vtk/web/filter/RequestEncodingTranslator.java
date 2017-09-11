/* Copyright (c) 2005, University of Oslo, Norway
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
package vtk.web.filter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.repository.Path;
import vtk.web.service.URL;
import vtk.web.servlet.AbstractServletFilter;

/**
 * Request URI processor that translates the URI (and optionally headers)
 * from one encoding to another.
 *
 * <p>Constructor arguments:
 * <ul>
 *   <li><code>fromEncoding</code> - the encoding to translate from</li>
 *   <li><code>toEncoding</code> - the encoding to translate to</li>
 *   <li><code>translatedHeaders</code> - a set of header names. 
 *     If <code>*</code> is included in the set, all headers will 
 *     be translated</li>
 *   <li><code>urlReplacements</code> - a map of regular expression replacements</li>
 * </ul>
 * </p>
 */
public class RequestEncodingTranslator extends AbstractServletFilter {
    private Charset fromEncoding;
    private Charset toEncoding;
    private Set<String> translatedHeaders = new HashSet<>();
    private static Logger logger = LoggerFactory.getLogger(RequestEncodingTranslator.class);
    private Map<Pattern, String> urlReplacements;
    
    public RequestEncodingTranslator(String fromEncoding, String toEncoding, 
            Set<String> translatedHeaders, Map<String, String> urlReplacements) {
        this.fromEncoding = Charset.forName(
                Objects.requireNonNull(fromEncoding, "fromEncoding cannot be null"));
        this.toEncoding = Charset.forName(
                Objects.requireNonNull(toEncoding, "toEncoding cannot be null"));
        this.translatedHeaders = new HashSet<>(
                Objects.requireNonNull(translatedHeaders, "translatedHeaders cannot be null"));
        Objects.requireNonNull(urlReplacements, "urlReplacements cannot be null");
        this.urlReplacements = new LinkedHashMap<>();
        for (String key : urlReplacements.keySet()) {
            Pattern pattern = Pattern.compile(key);
            String replacement = urlReplacements.get(key);
            this.urlReplacements.put(pattern, replacement);
        }
    }
    
    @Override
    protected void doFilter(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        chain.doFilter(new TranslatingRequestWrapper(request, fromEncoding, toEncoding), response);
    }
    
    private class TranslatingRequestWrapper extends HttpServletRequestWrapper {
    	private URL requestURL;
    	
        public TranslatingRequestWrapper(HttpServletRequest request,
                                         Charset fromEncoding, Charset toEncoding) {
            super(request);
            try {
                String originalURL = request.getRequestURL().toString();
                String parseString = originalURL;
                
                if (urlReplacements != null) {
                    for (Pattern pattern : urlReplacements.keySet()) {
                        String replacement = urlReplacements.get(pattern);
                        parseString = pattern.matcher(parseString).replaceAll(replacement);
                    }
                }
                
            	URL url = URL.parse(parseString);
            	if (!URL.isEncoded(originalURL)) {
            	    Path p = URL.encode(url.getPath(), fromEncoding);
            	    url.setPath(URL.decode(p, toEncoding));
                }
                this.requestURL = url;
                if (logger.isDebugEnabled()) {
                    logger.debug("Translated requestURL from '" + request.getRequestURL() 
                            + "' to '" + this.requestURL);
                }
            	
            }
            catch (Exception e) {
                logger.warn("Unable to translate uri: " + request.getRequestURI(), e);
                this.requestURL = URL.parse(request.getRequestURL().toString());
            }
        }
        
        @Override
        public String getRequestURI() {
            return new URL(this.requestURL).clearParameters().getPathRepresentation();
        }
        
        @Override
        public StringBuffer getRequestURL() {
            return new StringBuffer(this.requestURL.toString());
        }
        
        @Override
        public String getHeader(String name) {
            String header = super.getHeader(name);
            if (!translatedHeaders.contains(name) 
                    && !translatedHeaders.contains("*")) {
                return header;
            }
            if (header == null) {
                return null;
            }
            try {
                header = new String(header.getBytes(fromEncoding), toEncoding);
            } catch (Exception e) {
                logger.warn("Unable to translate header: " + header, e);
            }
            return header;
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            Enumeration<String> e = super.getHeaders(name);
            if (!translatedHeaders.contains(name) 
                    && !translatedHeaders.contains("*")) {
                return e;
            }
            List<String> headers = new ArrayList<>();
            while (e.hasMoreElements()) {
                String header = e.nextElement();
                try {
                    header = new String(header.getBytes(fromEncoding), toEncoding);
                } catch (Exception ex) {
                    logger.warn("Unable to translate header: " + header, ex);
                }
                headers.add(header);
            }
            final List<String> result = headers;
            return new Enumeration<String>() {
                int i = 0;
                public boolean hasMoreElements() {
                    return i < result.size();
                }
                public String nextElement() {
                    if (hasMoreElements()) {
                        return result.get(i++);
                    }
                    throw new NoSuchElementException();
                }
            };
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + requestURL + ")";
        }
    }

}
