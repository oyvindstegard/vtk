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
package org.vortikal.web.filter;

import java.nio.charset.Charset;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;

import org.vortikal.util.web.URLUtil;


/**
 * Request processor that URL decodes the URI.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>characterEncoding</code> - the encoding used when URL decoding
 * </ul>
 */
public class RequestURLDecoder implements RequestFilter, InitializingBean {

    private Log logger = LogFactory.getLog(this.getClass());

    private String characterEncoding;
    
    private int order = Integer.MAX_VALUE;
    

    public void setCharacterEncoding(String characterEncoding) {
        this.characterEncoding = characterEncoding;
    }

    public void setOrder(int order) {
        this.order = order;
    }
    
    public int getOrder() {
        return this.order;
    }
    

    public void afterPropertiesSet() throws Exception {
        if (this.characterEncoding == null) {
            throw new BeanInitializationException(
                "JavaBean property 'characterEncoding' not specified");
        }
        Charset.forName(this.characterEncoding);
    }
    
    public HttpServletRequest filterRequest(HttpServletRequest request) {
        return new URLDecodingRequestWrapper(request, this.characterEncoding);
    }
    
    private class URLDecodingRequestWrapper extends HttpServletRequestWrapper {

        private HttpServletRequest request;
        private String characterEncoding;

        public URLDecodingRequestWrapper(HttpServletRequest request,
                                         String characterEncoding) {
            super(request);
            this.request = request;
            this.characterEncoding = characterEncoding;
        }
        
        public String getRequestURI() {
            
            String uri = this.request.getRequestURI();
            boolean appendSlash = false;
            if (uri == null || uri.endsWith("/")) {
                appendSlash = true;
            }

            try {
                uri = URLUtil.urlDecode(uri, this.characterEncoding);
            } catch (Exception e) {
                
            }
            if (appendSlash) {
                uri += "/";
            }
            return uri;
        }
    }
    


}
