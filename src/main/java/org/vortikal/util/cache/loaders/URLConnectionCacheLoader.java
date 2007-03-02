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
package org.vortikal.util.cache.loaders;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import org.vortikal.util.cache.ContentCacheLoader;


/**
 * Abstract utility cache loader that loads content from a URL.
 * Clients have to implement the <code>handleContentStream()</code>
 * method in order to build a cacheable object.
 */
public abstract class URLConnectionCacheLoader implements ContentCacheLoader {

    private int readTimeout = -1;
    private int connectTimeout = -1;

    /**
     * Sets the connection timeout.
     *
     * @param connectTimeout the timeout value (in seconds).
     */
    public void setConnectTimeoutSeconds(int connectTimeout) {
        this.connectTimeout = connectTimeout * 1000;
    }

    /**
     * Sets the read timeout.
     *
     * @param readTimeout the timeout value (in seconds).
     */
    public void setReadTimeoutSeconds(int readTimeout) {
        this.readTimeout = readTimeout * 1000;
    }
    

    /**
     * Loads an object. The identifier parameter is interpreted as a
     * URL, and a connection is opened to that URL. 
     *
     * @param url the URL to load
     */
    public final Object load(Object url) throws Exception {
        String address = url.toString();
        URLConnection connection = new URL(address).openConnection();
        setConnectionProperties(connection);
        return handleConnection(connection);        
    }


    /**
     * Sets connection timeouts if available. In addition,
     * <code>connection.setUseCaches(true)</code> is invoked.
     *
     * @param connection the URL connection 
     */
    protected void setConnectionProperties(URLConnection connection) {
        // XXX: In Java 1.5 timeouts can (and should be) be specified
        // on a URLConnection. Coding these using reflection until we
        // are officially on 1.5:

        try {
            java.lang.reflect.Method setConnectTimeout = connection.getClass().getMethod(
                "setConnectTimeout", new Class[]{int.class});
            java.lang.reflect.Method setReadTimeout = connection.getClass().getMethod(
                "setReadTimeout", new Class[]{int.class});
            if (this.connectTimeout > 0) {
                setConnectTimeout.invoke(connection, new java.lang.Object[]{
                        new Integer(this.connectTimeout)});
            }
            if (this.readTimeout > 0) {
                setReadTimeout.invoke(connection, new java.lang.Object[]{
                        new Integer(this.readTimeout)});
            }
            
        } catch (Throwable t) {
            // Connection timeouts not available
        }
        connection.setUseCaches(true);
    }


    protected abstract Object handleConnection(URLConnection connection) throws Exception;
    
}
