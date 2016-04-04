/* Copyright (c) 2006, University of Oslo, Norway
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A request filter that normalizes the URI of an <code>HttpServletRequest</code>
 * by removing dot segments ('.' or '..').
 * 
 * <p>Configurable properties:
 * <ul>
 *  <li><code>normalizeSlashes</code> - Multiple consecutive slashes 
 *  (e.g. '/foo///bar///too//many//slashes') are, by default, not normalized 
 *  into a single slash. To also enable this kind of normalization, set this
 *  property to <code>true</code>.
 *  </li>
 * </ul>
 * </p>
 * 
 * @author oyviste
 *
 */
public class URISegmentNormalizationRequestFilter extends AbstractRequestFilter {

    Logger logger = LoggerFactory.getLogger(URISegmentNormalizationRequestFilter.class);
    
    private boolean normalizeSlashes = false;
    
    public HttpServletRequest filterRequest(HttpServletRequest request) {
        return new URISegmentNormalizationRequestWrapper(request);
    }

    private class URISegmentNormalizationRequestWrapper extends HttpServletRequestWrapper {
        
        private String normalizedURI;
        
        public String getRequestURI() {
            return this.normalizedURI;
        }
        
        public URISegmentNormalizationRequestWrapper(HttpServletRequest request) {
            super(request);
            
            this.normalizedURI = removeDotSegments(request.getRequestURI());

            if (URISegmentNormalizationRequestFilter.this.normalizeSlashes) {
                this.normalizedURI = this.normalizedURI.replaceAll("[/]+", "/");
            }
            
            if (URISegmentNormalizationRequestFilter.this.logger.isDebugEnabled()) {
                URISegmentNormalizationRequestFilter.this.logger.debug("Request URI: '" + request.getRequestURI() + 
                        "', normalized URI: '" + this.normalizedURI + "'");
            }
        }

        /**
         * This method should normalize most combinations of dots and slashes in
         * URIs.
         * TODO: Put in <code>vtk.util.web.URLUtil</code> ?
         * 
         * @param uri The URI to be normalized.
         * @return The normalized URI. 
         */
        private String removeDotSegments(String uri) {
            StringBuilder input = new StringBuilder(uri);
            StringBuilder output = new StringBuilder();

            while (input.length() > 0) {
                if (input.length() >= 3 && "../".equals(input.substring(0,3))) {
                    input.delete(0,3);
                } else if (input.length() >= 2 && "./".equals(input.substring(0,2))) {
                    input.delete(0,2);
                } else if (input.length() >= 3 && "/./".equals(input.substring(0,3))) {
                    input.replace(0,3, "/");
                } else if (input.length() == 2 && "/.".equals(input.substring(0,2))) {
                    input.replace(0,2, "/");
                } else if (input.length() >= 4 && "/../".equals(input.substring(0,4))) {
                    input.replace(0,4, "/");
                    removeLastSegment(output);
                } else if (input.length() >= 3 && "/..".equals(input.substring(0,3))) {
                    if (input.length() > 3 && input.charAt(3) != '/') {
                        moveNextSegment(input, output);
                    } else {
                        input.replace(0,3, "/");
                        removeLastSegment(output);
                    }
                } else if (".".equals(input.toString()) || "..".equals(input.toString())) {
                    break;
                } else {
                    moveNextSegment(input, output);
                }
            }
            
            return output.toString();
        }

        private void moveNextSegment(StringBuilder input, StringBuilder output) {
            int next = input.indexOf("/", 1);
            if (next != -1) {
                output.append(input.substring(0, next));
                input.delete(0, next);
            } else {
                output.append(input.toString());
                input.delete(0, input.length());
            }
        }
        
        private void removeLastSegment(StringBuilder uri) {
            int last = uri.lastIndexOf("/");
            if (last != -1) {
                uri.delete(last, uri.length());
            } else {
                uri.delete(0, uri.length());
            }
        }  
        
    }

    public boolean isNormalizeSlashes() {
        return this.normalizeSlashes;
    }

    public void setNormalizeSlashes(boolean normalizeSlashes) {
        this.normalizeSlashes = normalizeSlashes;
    }

}
