/* Copyright (c) 2007, University of Oslo, Norway
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
package org.vortikal.web.view.decorating;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

public class URLTemplateSource implements TemplateSource {

    private String url;
    private String characterEncoding;

    public URLTemplateSource() {}
    
    public URLTemplateSource(String url, String characterEncoding) {
        this.url = url;
        this.characterEncoding = characterEncoding;
    }

    public String getID() {
        return this.url;
    }
    
    public long getLastModified() throws Exception {
        if (this.url.startsWith("file://")) {
            URL fileURL = new URL(this.url);
            File file = new File(fileURL.getFile());
            return file.lastModified();
        }
        return -1;
    }
    
    public String getCharacterEncoding() {
        String encoding = (this.characterEncoding != null) ?
                this.characterEncoding : System.getProperty("file.encoding");
        return encoding;
    }
    
    public InputStream getInputStream() throws Exception {
        InputStream is = null;
        if (this.url.startsWith("classpath://")) {
            String actualPath = url.substring("classpath://".length());
            Resource resource = new ClassPathResource(actualPath);
            is = resource.getInputStream();
        } else {
            is = new URL(this.url).openStream();
        }
        return is;
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getName());
        sb.append(": [").append(this.url).append("]");
        return sb.toString();
    }

}

