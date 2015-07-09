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
package vtk.web.servlet;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;

import vtk.util.web.HttpUtil;
import vtk.web.service.URL;


/**
 * A servlet request wrapper that intercepts most calls before
 * invoking the wrapped request, allowing configurable behavior in
 * some aspects including request method, authentication and headers.
 */
public class ConfigurableRequestWrapper extends HttpServletRequestWrapper {

    private String method = "GET";
    private URL url;
    private URL wrappedURL;
    private boolean anonymous = false;
    private Map<String, Set<String>> headers;
    private Map<String, Object> attributes = new HashMap<String, Object>();
    
    /**
     * Creates a new request wrapper.
     *
     * @param request the wrapped request
     */
    public ConfigurableRequestWrapper(HttpServletRequest request) {
        super(request);
        this.url = URL.create(request);
        this.wrappedURL = new URL(this.url);
        initHeaders();
    }
    
    /**
     * Creates a new request wrapper from a request and a {@link URL}.
     *
     * @param request the wrapped request
     * @param url the URL
     */
    public ConfigurableRequestWrapper(HttpServletRequest request, URL url) {
        super(request);
        this.url = new URL(url);
        this.wrappedURL = URL.create(request);
        initHeaders();
    }

    /**
     * Sets the HTTP method. The default is <code>GET</code>.
     */
    public void setMethod(String method) {
        this.method = method;
    }
    
    public String getMethod() {
        return this.method;
    }

    /**
     * Specifies whether this request wrapper should act as if it were
     * an "anonymous" request. Setting this flag causes authorization
     * headers to be thrown away and the <code>getSession()</code>
     * call to always return <code>null</code>.
     */
    public void setAnonymous(boolean anonymous) {
        this.anonymous = anonymous;
    }

    /**
     * Sets a header, replacing any existing values.
     */
    public void setHeader(String name, String value) {
        Set<String> values = new HashSet<String>();
        values.add(value);
        this.headers.put(name, values);
    }

    /**
     * Adds a header. If the header has existing values, this value is
     * added to that set.
     */
    public void addHeader(String name, String value) {
        Set<String> values = this.headers.get(name);
        if (values == null) {
            values = new HashSet<String>();
            this.headers.put(name, values);
        }
        values.add(value);
    }

    /**
     * Clears all previously set headers.
     */
    public void clearHeaders() {
        this.headers = new HashMap<String, Set<String>>();
    }
    
    @Override
    public String getScheme() {
        return this.url.getProtocol();
    }

    @Override
    public String getServerName() {
        return this.url.getHost();
    }
    
    @Override
    public int getServerPort() {
        return this.url.getPort();
    }

    @Override
    public boolean isSecure() {
        return "https".equals(this.url.getProtocol());
    }

    @Override
    public String getContextPath() {
        return "";
    }

    @Override
    public String getPathInfo() {
        return this.url.getPath().toString();
    }

    @Override
    public String getServletPath() {
        return "";
    }

    @Override
    public String getPathTranslated() {
        return null;
    }

    @Override
    public String getRealPath(String path) {
        return null;
    }

    @Override
    public String getQueryString() {
        return this.url.getQueryString();
    }

    @Override
    public String getRequestURI() {
        URL u = new URL(this.url).clearParameters();
        return u.getPathRepresentation();
    }
    
    @Override
    public StringBuffer getRequestURL() {
        URL url = new URL(this.url).clearParameters();
        return new StringBuffer(url.toString());
    }

    @Override
    public String getParameter(String name) {
        String val = this.url.getParameter(name);
        if (val != null) {
            return val;
        }
        if ("POST".equals(getMethod()) && this.url.equals(this.wrappedURL)) {
            return super.getParameter(name);
        }
        return null;
    }

    @Override
    public String[] getParameterValues(String name) {
        List<String> values = this.url.getParameters(name);
        if (values != null) {
            String[] result = new String[values.size()];
            return values.toArray(result);
        }
        if ("POST".equals(getMethod()) && this.url.equals(this.wrappedURL)) {
            return super.getParameterValues(name);
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> params;
        if ("POST".equals(getMethod()) && this.url.equals(this.wrappedURL)) {
            params = new HashMap<String, String[]>(super.getParameterMap());
        } else {
            params = new HashMap<String, String[]>();
        }
        
        List<String> queryNames = this.url.getParameterNames();
        for (String queryParam: queryNames) {
            if (params.containsKey(queryParam)) {
                String[] oldValues = params.get(queryParam);
                List<String> newValues = new ArrayList<String>(Arrays.asList(oldValues));
                List<String> queryValues = this.url.getParameters(queryParam);
                if (queryValues != null) {
                    for (String queryValue: queryValues) {
                        newValues.add(queryValue);
                    }
                }
                String[] resultValues = newValues.<String>toArray(new String[newValues.size()]);
                params.put(queryParam, resultValues);
            } else {
                List<String> values = this.url.getParameters(queryParam);
                if (values != null) {
                    params.put(queryParam, values.toArray(new String[values.size()]));
                }
            }
        }
        return params;
    }
        
    @Override
    public Enumeration<String> getParameterNames() {
        Map<String, String[]> params = getParameterMap();
        return Collections.enumeration(params.keySet());
    }
    
    @Override
    public String getHeader(String name) {
        Set<String> values = this.headers.get(name);
        if (values == null) {
            return null;
        }
        return values.iterator().next();
    }
    
    @Override
    public long getDateHeader(String name) {
        Set<String> values = this.headers.get(name);
        if (values == null) {
            return -1;
        }
        String value = values.iterator().next();
        Date d = HttpUtil.parseHttpDate(value);
        if (d == null) {
            return -1;
        }
        return d.getTime();
    }
    
    @Override
    public int getIntHeader(String name) {
        Set<String> values = this.headers.get(name);
        if (values == null) {
            return -1;
        }
        String value = values.iterator().next();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
    

    @Override
    public Enumeration<String> getHeaders(String name) {
        Set<String> values = this.headers.get(name);
        if (values == null) {
            return null;
        }
        return Collections.<String>enumeration(values);
    }
    
    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> values = this.headers.keySet();
        return Collections.enumeration(values);
    }
    
    // XXX: if create is specified, should return dummy session?
    @Override
    public HttpSession getSession(boolean create) {
        if (this.anonymous && !create) {
            return null;
        }
        return super.getSession(create);
    }
    
    @Override
    public HttpSession getSession() {
        if (this.anonymous) {
            return null;
        }
        return super.getSession();
    }
    
    @Override
    public String getAuthType() {
        if (this.anonymous) {
            return null;
        }
        return super.getAuthType();
    }
    

    @Override
    public String getRemoteUser() {
        if (this.anonymous) {
            return null;
        }
        return super.getRemoteUser();
    }

    @Override
    public boolean isUserInRole(String role) {
        if (this.anonymous) {
            return false;
        }
        return super.isUserInRole(role);
    }

    @Override
    public Principal getUserPrincipal() {
        if (this.anonymous) {
            return null;
        }
        return super.getUserPrincipal();
    }

    @Override
    public String getRequestedSessionId() {
        if (this.anonymous) {
            return null;
        }
        return super.getRequestedSessionId();
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        if (this.anonymous) {
            return false;
        }
        return super.isRequestedSessionIdValid();
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        if (this.anonymous) {
            return true;
        }
        return super.isRequestedSessionIdFromCookie();
    }
    
    @Override
    public boolean isRequestedSessionIdFromURL() {
        if (this.anonymous) {
            return false;
        }
        return super.isRequestedSessionIdFromURL();
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        return super.isRequestedSessionIdFromUrl();
    }
    
    @Override
    public Cookie[] getCookies() {
        if (this.anonymous) {
            return filterAnonymousCookies();
        }
        return super.getCookies();
    }
    
    @Override
    public String toString() {
        return this.getClass().getName() + "[" + super.toString() + "]";
    }
    

    @Override
    public Object getAttribute(String name) {
        if (this.attributes.containsKey(name)) {
            return this.attributes.get(name);
        }
        return super.getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        Set<String> names = new HashSet<String>();
        Enumeration<String> e = super.getAttributeNames();
        while (e.hasMoreElements()) {
            names.add(e.nextElement());
        }
        names.addAll(this.attributes.keySet());
        return Collections.enumeration(names);
    }

    @Override
    public void setAttribute(String name, Object o) {
        this.attributes.put(name, o);
    }

    private Cookie[] filterAnonymousCookies() {
        List<Cookie> result = new ArrayList<Cookie>();
        Cookie[] cookies = super.getCookies();
        String sessionID = null;
        HttpSession session = super.getSession(false);
        if (session != null) {
            sessionID = session.getId();
        }
        for (Cookie cookie: cookies) {
            if (sessionID != null && sessionID.equalsIgnoreCase(cookie.getName())) {
                continue;
            }
            result.add(cookie);
        }
        return result.<Cookie>toArray(new Cookie[result.size()]);
    }

    private void initHeaders() {
        this.headers = new HashMap<String, Set<String>>();
        Enumeration<?> headerNames = super.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = (String) headerNames.nextElement();
            Set<String> values = this.headers.get(name);
            if (values == null) {
                values = new HashSet<String>();
                this.headers.put(name, values);
            }
            Enumeration<?> headerValues = super.getHeaders(name);
            while (headerValues.hasMoreElements()) {
                String value = (String) headerValues.nextElement();
                values.add(value);
            }
        }
    }
    
}
