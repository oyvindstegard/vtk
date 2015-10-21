/* Copyright (c) 2010, University of Oslo, Norway
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
package vtk.web.display.linkcheck;

import java.io.IOException;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import vtk.web.service.URL;

public class LinkChecker {
    
    private static Log logger = LogFactory.getLog(LinkChecker.class); 
    
    private Ehcache cache;
    private int connectTimeout = 5000;
    private int readTimeout = 5000;
    private String userAgent = "Link checker";
    
    public static final class LinkCheckResult implements Serializable {
        private static final long serialVersionUID = -7574234857037932804L;
        private String link;
        private Status status;
        private String reason;
        public LinkCheckResult(String link, Status status) {
            this(link, status, null);
        }
        public LinkCheckResult(String link, Status status, String reason) {
            this.link = link;
            this.status = status;
            this.reason = reason;
        }
        public String getLink() {
            return link;
        }
        public Status getStatus() {
            return status;
        }
        public String getReason() {
            return reason;
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("link: ").append(link)
            .append(", status: ").append(status);
            if (reason != null) {
                sb.append(", reason: ").append(reason);
            }
            sb.append("}");
            return sb.toString();
        }
    }
    
    public enum Status {
        OK,
        NOT_FOUND,
        TIMEOUT,
        MALFORMED_URL,
        ERROR;
    }

    /**
     * Request validation of reference with a base URL.
     * 
     * @param href Reference, may be absolute or relative to base.
     * @param base Absolute base URL.
     * 
     * @return A <code>LinkCheckResult</code> with result of link check.
     */
    public LinkCheckResult validate(String href, URL base) {
        return validate(href, base, false);
    }
    
    /**
     * Request validation of reference with a base URL, optionally sending
     * base URL as an HTTP Referer(sic) header when doing the request.
     * 
     * @param href Reference, may be absolute or relative to base.
     * @param base Absolute base URL.
     * @param sendReferrer Set to <code>true</code> to add a Referer header
     *                     to HTTP request when doing link validation. Base URL
     *                     will be used as the value.
     * 
     * @return A {@link LinkCheckResult} with result of link check.
     */
    public LinkCheckResult validate(String href, URL base, boolean sendReferrer) {
        LinkCheckResult result = validateInternal(href, base, sendReferrer);
        logger.info("Validate: href='" + href + "', base='" + base + "': " + result);
        return result;
    }
    
    private boolean isASCII(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0x7f) return false;
        }
        return true;
    }
    
    private java.net.URL escape(java.net.URL url) 
            throws MalformedURLException {
        
        // Hostname may need IDN-encoding
        if (!isASCII(url.getHost())) {
            String frag = url.getRef() != null ? "#" + url.getRef() : "";
            String host = java.net.IDN.toASCII(url.getHost());
            url = new java.net.URL(url.getProtocol(), host, url.getPort(), url.getFile() + frag);
        }
        
        // URL may already be encoded, or it may not, at this point.
        URI uri;
        try {
            uri = url.toURI(); // This validates URI syntax of URL
        }
        catch (URISyntaxException e) {
            // Something is not proper, build new URI which does encoding of each component
            try {
                uri = new URI(url.getProtocol(), null, url.getHost(), url.getPort(), 
                          url.getPath(), url.getQuery(), url.getRef());
            }
            catch (URISyntaxException ue) {
                throw new MalformedURLException(ue.getMessage());
            }
        }

        // This step encodes any non-ASCII chars
        return new java.net.URL(uri.toASCIIString());
    }
    
    private LinkCheckResult validateInternal(final String href, URL base, boolean sendReferrer) {
        if (href == null) {
            throw new IllegalArgumentException("Link argument cannot be NULL");
        }
        if (base == null) {
            throw new IllegalArgumentException("Base argument cannot be NULL");
        }
        
        String absolute = href;
        if (URL.isRelativeURL(href)) {
            try {
                absolute = base.relativeURL(href).toString();
            }
            catch (Throwable t) {
                return new LinkCheckResult(href, Status.MALFORMED_URL, t.getMessage());
            }
        }

        java.net.URL urlToCheck = null;
        try {
            urlToCheck = escape(new java.net.URL(absolute));
        }
        catch (MalformedURLException e) {
            return new LinkCheckResult(href, Status.MALFORMED_URL, e.getMessage());
        }
        
        final String cacheKey = urlToCheck.toString();
        Element cached = cache.get(cacheKey);
        if (cached != null) {
            LinkCheckResult r = (LinkCheckResult) cached.getObjectValue();
            // Multiple input hrefs can map to the same URL:
            if (r.link.equals(href)) {
                return r;
            }
            return new LinkCheckResult(href, r.status, r.reason);
        }
        Status status;
        String reason = null;
        try {
            status = validateURL(urlToCheck, sendReferrer ? base : null, "HEAD");
            if (status == Status.NOT_FOUND) {
                // Some broken servers return different result codes based on HEAD versus GET, so we retry...
                logger.info("Validate (HEAD returned NOT_FOUND, retrying with GET): href='" + urlToCheck + "'");
                status = validateURL(urlToCheck, sendReferrer ? base : null, "GET");
            }
        }
        catch (Throwable t) {
            status = Status.ERROR;
            reason = t.getMessage();
        }
        // Any status other than explicit NOT_FOUND is considered
        // to be ok. This to reduce unnecessary noise produced by generic/random
        // errors and timeouts.
        if (status != Status.NOT_FOUND) {
            status = Status.OK;
            reason = null;
        }
        
        LinkCheckResult result = new LinkCheckResult(href, status, reason);
        cache.put(new Element(cacheKey, result));
        return result;
    }
    
    private Status validateURL(java.net.URL url, URL referrer, String method) {
        HttpURLConnection urlConnection = null;
        try {
            urlConnection = createRequest(url, referrer, method);
            urlConnection.connect();
            int  httpResponseCode = urlConnection.getResponseCode();
            if (httpResponseCode == HttpURLConnection.HTTP_MOVED_PERM
                    || httpResponseCode == HttpURLConnection.HTTP_MOVED_TEMP
                    || httpResponseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                httpResponseCode = checkMoved(urlConnection, httpResponseCode, referrer, method);
            }
            if (httpResponseCode == HttpURLConnection.HTTP_NOT_FOUND 
                || httpResponseCode == HttpURLConnection.HTTP_GONE) {
                return Status.NOT_FOUND;
            }
            return Status.OK;
        }
        catch (SocketTimeoutException e) {
            return Status.TIMEOUT;
        }
        catch (UnknownHostException e) {
            return Status.NOT_FOUND;
        }
        catch (Exception e) {
            return Status.ERROR;
        }
        finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private int checkMoved(HttpURLConnection urlConnection, int responseCode, URL referrer, String method) throws IOException {
        int retry = 0;
        // try a maximum of three times
        while (retry < 3
                && (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP)) {
            String location = urlConnection.getHeaderField("Location");
            urlConnection.disconnect();
            if (location == null) {
                return responseCode;
            }
            urlConnection = createRequest(escape(new java.net.URL(location)), referrer, method);
            urlConnection.connect();
            responseCode = urlConnection.getResponseCode();
            retry++;
        }
        return responseCode;
    }
    
    private HttpURLConnection createRequest(java.net.URL url, URL referrer, String method) throws IOException {
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod(method);
        urlConnection.setConnectTimeout(connectTimeout);
        urlConnection.setReadTimeout(readTimeout);
        urlConnection.setRequestProperty("User-Agent", userAgent);
        if (referrer != null) {
            urlConnection.setRequestProperty("Referer", referrer.toString());
        }
        
        return urlConnection;
    }

    @Required
    public void setCache(Ehcache cache) {
        this.cache = cache;
    }
    
    public void setConnectTimeout(int connectTimeout) {
        if (connectTimeout < 0) {
            throw new IllegalArgumentException("Connect timeout must be an integer >= 0");
        }
        this.connectTimeout = connectTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        if (readTimeout < 0) {
            throw new IllegalArgumentException("Read timeout must be an integer >= 0");
        }
        this.readTimeout = readTimeout;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
