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
package org.vortikal.web.view.decorating.components;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;

import org.vortikal.util.cache.loaders.URLConnectionCacheLoader;
import org.vortikal.util.io.StreamUtil;
import org.vortikal.util.text.TextUtils;


/**
 * A cache loader that reads the content of a network resource into a
 * string.
 */
public class URLToStringLoader extends URLConnectionCacheLoader {

    private int maxLength = -1;
    private String defaultCharset = "iso-8859-1";
    private String clientIdentifier = "Anonymous URL retriever";

    public void setClientIdentifier(String clientIdentifier) {
        this.clientIdentifier = clientIdentifier;
    }
    
    protected void setConnectionProperties(URLConnection connection) {
        super.setConnectionProperties(connection);
        connection.setRequestProperty("User-Agent", this.clientIdentifier);
        connection.setRequestProperty("Accept", "text/*");
    }

    protected Object handleConnection(URLConnection connection) throws Exception {
        if (!(connection instanceof HttpURLConnection)) {
            throw new IllegalArgumentException("Only HTTP addresses are supported");
        }
        HttpURLConnection urlConnection = (HttpURLConnection) connection;
        if (urlConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IllegalStateException("Got response " + urlConnection.getResponseCode()
                                            + " from server");
        }
        String charset = this.defaultCharset;
        String contentType = urlConnection.getHeaderField("Content-Type");
        if (contentType != null && contentType.indexOf("charset") > 0) {
            charset = TextUtils.extractField(contentType, "charset", ";");
        }

        InputStream stream = connection.getInputStream();
        byte[] buf;
        if (this.maxLength > 0) {
            buf = StreamUtil.readInputStream(stream, this.maxLength);
        } else {
            buf = StreamUtil.readInputStream(stream);
        }
        return new String(buf, charset);
    }
}
