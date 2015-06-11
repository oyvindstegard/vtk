/* Copyright (c) 2014, University of Oslo, Norway
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
package vtk.util.web;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;
import org.springframework.beans.factory.FactoryBean;

public class HttpClientFactoryBean implements FactoryBean<HttpClient> {
    
    private int maxTotalConnections = 200;
    private int maxPerRouteConnections = 20;
    private int defaultConnectTimeout = 5000;
    private int defaultReadTimeout = 10000;
    private String socksProxyHost = null;
    private int socksProxyPort = -1;
    private List<Header> defaultHeaders = new ArrayList<Header>();

    public void setMaxTotalConnections(int maxTotalConnections) {
        this.maxTotalConnections = maxTotalConnections;
    }
    
    public void setMaxPerRouteConnections(int maxPerRouteConnections) {
        this.maxPerRouteConnections = maxPerRouteConnections;
    }
    
    public void setDefaultConnectTimeout(int seconds) {
        this.defaultConnectTimeout = 1000 * seconds;
    }
    
    public void setDefaultReadTimeout(int seconds) {
        this.defaultReadTimeout = 1000 * seconds;
    }
    
    public void setSocksProxyHost(String socksProxyHost) {
        if (socksProxyHost != null && !socksProxyHost.trim().equals(""))
            this.socksProxyHost = socksProxyHost;
    }
    
    public void setSocksProxyPort(String socksProxyPort) {
        if (socksProxyPort != null && !socksProxyPort.trim().equals(""))
            this.socksProxyPort = Integer.parseInt(socksProxyPort);
    }
    
    public void setDefaultHeaders(Map<String, String> headers) {
        if (headers == null) return;
        for (Map.Entry<String, String> entry: headers.entrySet())
            defaultHeaders.add(new BasicHeader(entry.getKey(), entry.getValue()));
    }
    
    @Override
    public HttpClient getObject() throws Exception {
        ConnectionSocketFactory plainSocketFactory = socksProxyHost != null ?
                new SocksConnectionSocketFactory(socksProxyHost, socksProxyPort) :
                new PlainConnectionSocketFactory();

        SSLContext sslContext = SSLContexts.custom().build();
        LayeredConnectionSocketFactory sslSocketFactory = socksProxyHost != null ?  
                new SocksSSLConnectionSocketFactory(sslContext, socksProxyHost, socksProxyPort) :
                new SSLConnectionSocketFactory(sslContext);
                
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", plainSocketFactory)
                .register("https", sslSocketFactory)
                .build();

        PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager(registry);

        connMgr.setMaxTotal(maxTotalConnections);
        connMgr.setDefaultMaxPerRoute(maxPerRouteConnections);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(defaultConnectTimeout)
                .setConnectionRequestTimeout(defaultReadTimeout)
                .setAuthenticationEnabled(true)
                .setRedirectsEnabled(false)
                .build();
        
        return HttpClients.custom()
                .setConnectionManager(connMgr)
                .setDefaultRequestConfig(requestConfig)
                .setDefaultHeaders(defaultHeaders)
                .build();
    }

    @Override
    public Class<?> getObjectType() {
        return HttpClient.class;
        
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
    
    private static class SocksSSLConnectionSocketFactory extends SSLConnectionSocketFactory {
        private String proxyHost;
        private int proxyPort;
        
        public SocksSSLConnectionSocketFactory(SSLContext sslContext, String proxyHost, int proxyPort) {
            super(sslContext);
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
        }
        
        @Override
        public Socket createSocket(HttpContext context) {
            InetSocketAddress addr = new InetSocketAddress(proxyHost, proxyPort);
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, addr);
            return new Socket(proxy);
        }
    }
    
    private static class SocksConnectionSocketFactory extends PlainConnectionSocketFactory {
        private String proxyHost;
        private int proxyPort;
        
        public SocksConnectionSocketFactory(String proxyHost, int proxyPort) {
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
        }
        
        @Override
        public Socket createSocket(HttpContext context) {
            InetSocketAddress addr = new InetSocketAddress(proxyHost, proxyPort);
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, addr);
            return new Socket(proxy);
        }
    }

}
