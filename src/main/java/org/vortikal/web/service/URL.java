/* Copyright (c) 2004, 2007, University of Oslo, Norway
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
package org.vortikal.web.service;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.vortikal.repository.Path;


/**
 * Class for representing HTTP(s) URLs. Resembles {@link
 * java.net.URL}, except that it has getters/setters for all URL
 * components to achieve easy "on-the-fly" manipulation when
 * constructing URLs.
 */
public class URL {

    private String protocol = null;
    private String host = null;
    private Integer port = null;
    private Path path = null;
    private String characterEncoding = "utf-8";
    private Map<String, List<String>> parameters = new LinkedHashMap<String, List<String>>();
    private String ref = null;
    private boolean pathOnly = false;
    private boolean collection = false;
    
    private static final Integer PORT_80 = Integer.valueOf(80);
    private static final Integer PORT_443 = Integer.valueOf(443);
    
    private static final String PROTOCOL_HTTP = "http";
    private static final String PROTOCOL_HTTPS = "https";
    
    /**
     * Construct a new <code>URL</code> instance that is a copy
     * if the provided original.
     * 
     * @param original The original <code>URL</code> instance to base the new
     *                 instance on.
     */
    public URL(URL original) {
        this.protocol = original.protocol;
        this.host = original.host;
        this.port = original.port;
        this.path = original.path;
        this.characterEncoding = original.characterEncoding;
        this.ref = original.ref;
        this.pathOnly = original.pathOnly;
        this.collection = original.collection;
        
        // Copy parameter map
        for (Map.Entry<String, List<String>> entry: original.parameters.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            
            List<String> copiedValues = new ArrayList<String>(values.size());
            for (String value: values) {
                copiedValues.add(value);
            }
            this.parameters.put(key, copiedValues);
        }
    }

    public URL(String protocol, String host, Path path) {
        if (!(PROTOCOL_HTTP.equals(protocol) || PROTOCOL_HTTPS.equals(protocol))) {
            throw new IllegalArgumentException("Unknown protocol: '" + protocol + "'");
        }
        if (host == null || "".equals(host.trim())) {
            throw new IllegalArgumentException("Invalid hostname: '" + host + "'");
        }
        if (path == null) {
            throw new IllegalArgumentException("Path argument cannot be NULL");
        }

        this.protocol = protocol.trim();
        this.host = host.trim();
        this.path = path;
    }
    

    public String getProtocol() {
        return this.protocol;
    }
    

    public void setProtocol(String protocol) {
        if (protocol != null) {
            protocol = protocol.trim();
        }
        if (!(PROTOCOL_HTTP.equals(protocol) || PROTOCOL_HTTPS.equals(protocol))) {
            throw new IllegalArgumentException("Unknown protocol: '" + protocol + "'");
        }

        this.protocol = protocol.trim();

        if (PROTOCOL_HTTP.equals(protocol) && PORT_443.equals(this.port)) {
            this.port = PORT_80;
        } else if (PROTOCOL_HTTPS.equals(protocol) && PORT_80.equals(this.port)) {
            this.port = PORT_443;
        }
    }

    
    public String getHost() {
        return this.host;
    }
    

    public void setHost(String host) {
        if (host == null || "".equals(host.trim())) {
            throw new IllegalArgumentException(
                "Invalid hostname: '" + host + "'");
        }
        this.host = host;
    }
    

    public Integer getPort() {
        return this.port;
    }
    

    public void setPort(Integer port) {
        if (port != null && port.intValue() <= 0) {
            throw new IllegalArgumentException(
                "Invalid port number: " + port.intValue());
        }
        this.port = port;
    }


    public Path getPath() {
        return this.path;
    }
    

    public void setPathOnly(boolean pathOnly) {
        this.pathOnly = pathOnly;
    }

    public boolean isPathOnly() {
        return this.pathOnly;
    }

    public void setPath(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be NULL");
        }
        this.path = path;
    }
    
    public boolean isCollection() {
        return this.collection;
    }
    
    public void setCollection(boolean collection) {
        this.collection = collection;
    }
    
    public void addParameter(String name, String value) {
        List<String> values = this.parameters.get(name);
        if (values == null) {
            values = new ArrayList<String>();
            this.parameters.put(name, values);
        }
        values.add(value);
    }
    
    public void setParameter(String name, String value) {
        if (this.parameters.containsKey(name)) {
            this.parameters.remove(name);
        }
        List<String> values = new ArrayList<String>();
        values.add(value);
        this.parameters.put(name, values);
    }

    public void removeParameter(String name) {
        this.parameters.remove(name);
    }
    

    public void clearParameters() {
        this.parameters = new LinkedHashMap<String, List<String>>();
    }
    

    public String getParameter(String parameterName) {
        List<String> values = this.parameters.get(parameterName);
        if (values == null || values.size() == 0) {
            return null;
        }
        return values.get(0);
    }
    
    public List<String> getParameters(String parameterName) {
        List<String> values = this.parameters.get(parameterName);
        if (values == null || values.size() == 0) {
            return null;
        }
        return Collections.list(Collections.enumeration(values));
    }
    

    public List<String> getParameterNames() {
        return Collections.list(Collections.enumeration(this.parameters.keySet()));
    }

    public String getQueryString() {
        if (this.parameters.isEmpty()) {
            return null;
        }
        StringBuilder qs = new StringBuilder();
        for (Iterator<String> i = this.parameters.keySet().iterator(); i.hasNext();) {
            String param = i.next();
            String encodedParam = encode(param);
            List<String> values = this.parameters.get(param);
            for (Iterator<String> j = values.iterator(); j.hasNext();) {
                String val = j.next();
                val = encode(val);
                if ("".equals(val)) {
                    qs.append(encodedParam);
                } else {
                    qs.append(encodedParam).append("=").append(val);    
                }
                if (j.hasNext()) {
                    qs.append("&");
                }
            }
            if (i.hasNext()) {
                qs.append("&");
            }
        }
        return qs.toString();
    }
    
    public String getRef() {
        return this.ref;
    }
    
    public void setRef(String ref) {
        this.ref = ref;
    }
    
    /**
     * Sets the character encoding used when URL-encoding the path.
     */
    public void setCharacterEncoding(String characterEncoding) {
        if (characterEncoding == null) {
            throw new IllegalArgumentException("Character encoding must be specified");
        }
        java.nio.charset.Charset.forName(characterEncoding);
        this.characterEncoding = characterEncoding;
    }

    @Override
    public String toString() {
        if (this.pathOnly) {
            return this.getPathRepresentation();
        }
        
        StringBuilder url = new StringBuilder();
        
        url.append(this.protocol).append("://");
        url.append(this.host);
        if (this.port != null) {
            if (!(this.port.equals(PORT_80) && PROTOCOL_HTTP.equals(this.protocol)
                  || (this.port.equals(PORT_443) && PROTOCOL_HTTPS.equals(this.protocol)))) {
                url.append(":").append(this.port.intValue());
            }
        }

        url.append(getPathRepresentation());  // Includes encoded /path, ?query parameters and #ref.

        return url.toString();
    }    
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime
                * result
                + ((characterEncoding == null) ? 0 : characterEncoding
                        .hashCode());
        result = prime * result + (collection ? 1231 : 1237);
        result = prime * result + ((host == null) ? 0 : host.hashCode());
        result = prime * result
                + ((parameters == null) ? 0 : parameters.hashCode());
        result = prime * result + ((path == null) ? 0 : path.hashCode());
        result = prime * result + (pathOnly ? 1231 : 1237);
        result = prime * result + ((port == null) ? 0 : port.hashCode());
        result = prime * result
                + ((protocol == null) ? 0 : protocol.hashCode());
        result = prime * result + ((ref == null) ? 0 : ref.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        URL other = (URL) obj;
        if (characterEncoding == null) {
            if (other.characterEncoding != null)
                return false;
        } else if (!characterEncoding.equals(other.characterEncoding))
            return false;
        if (collection != other.collection)
            return false;
        if (host == null) {
            if (other.host != null)
                return false;
        } else if (!host.equals(other.host))
            return false;
        if (parameters == null) {
            if (other.parameters != null)
                return false;
        } else if (!parameters.equals(other.parameters))
            return false;
        if (path == null) {
            if (other.path != null)
                return false;
        } else if (!path.equals(other.path))
            return false;
        if (pathOnly != other.pathOnly)
            return false;
        if (port == null) {
            if (other.port != null)
                return false;
        } else if (!port.equals(other.port))
            return false;
        if (protocol == null) {
            if (other.protocol != null)
                return false;
        } else if (!protocol.equals(other.protocol))
            return false;
        if (ref == null) {
            if (other.ref != null)
                return false;
        } else if (!ref.equals(other.ref))
            return false;
        return true;
    }

    /**
     * Generates a string representation of this URL, including
     * protocol, hostname and port information, excluding query string
     * parameters.
     */
    public String getBase() {
        StringBuilder url = new StringBuilder();
        if (!this.pathOnly) {
            url.append(this.protocol).append("://");
            url.append(this.host);
            if (this.port != null) {
                if (!(this.port.equals(PORT_80) && PROTOCOL_HTTP.equals(this.protocol)
                      || (this.port.equals(PORT_443) && PROTOCOL_HTTPS.equals(this.protocol)))) {
                    url.append(":").append(this.port.intValue());
                }
            }
        }
        try {
            Path encodedPath = encode(this.path, this.characterEncoding);
            url.append(encodedPath);
        } catch (java.io.UnsupportedEncodingException e) {
            // Ignore, this.characterEncoding is supposed to be valid.
        }
        if (this.collection && !this.path.isRoot()) {
            url.append("/");
        }
        return url.toString();
    }

    public String getPathEncoded() {
        try {
            StringBuilder result = new StringBuilder();
            result.append(encode(this.path, this.characterEncoding).toString());
            if (this.collection && !this.path.isRoot()) {
                result.append("/");
            }
            return result.toString();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("Character encoding " 
                    + this.characterEncoding 
                    + " is not supported on this system");
        }
    }
    
    /**
     * Generates an "absolute path" representation of this URL (a
     * string starting with '/', without protocol, hostname and port
     * information), but including query string parameters.
     */
    public String getPathRepresentation() {
        StringBuilder sb = new StringBuilder();
        try {
            Path encodedPath = encode(this.path, this.characterEncoding);
            sb.append(encodedPath);
        } catch (java.io.UnsupportedEncodingException e) {
            // Ignore, this.characterEncoding is supposed to be valid.
        }
      
        if (this.collection && !this.path.isRoot()) {
            sb.append("/");
        }
  
        String qs = getQueryString();
        if (qs != null) {
            sb.append("?");
            sb.append(qs);
        }

        if (this.ref != null) {
            sb.append("#").append(this.ref);
        }

        return sb.toString();
    }

    /**
     * Utility method to create a URL from an existing URL. The newly
     * created URL will contain the exact same data as the existing
     * one.
     *
     * @param url the existing URL
     * @return the generated URL
     */
    public static URL create(URL url) {
        URL newURL = new URL(url.getProtocol(), url.getHost(), url.getPath());
        newURL.port = url.port;
        newURL.characterEncoding = url.characterEncoding;
        newURL.parameters = new LinkedHashMap<String, List<String>>(url.parameters);
        newURL.collection = url.collection;
        newURL.ref = url.ref;
        return newURL;
    }


    /**
     * Utility method to create a URL from a servlet request. 
     * Decodes the path and query string parameters using the 
     * supplied encoding.
     *
     * @param request the servlet request
     * @param encoding the character encoding to use
     * @return the generated URL
     * @throws UnsupportedEncodingException if the specified 
     * character encoding is not supported on this system
     */
    public static URL create(HttpServletRequest request, String encoding) 
    throws UnsupportedEncodingException {
        String path = request.getRequestURI();
        if (path == null || "".equals(path)) path = "/";

        boolean collection = false;
        if (path.endsWith("/")) {
            collection = true;
            if (!path.equals("/")) {
                path = path.substring(0, path.length() - 1);
            }
        }
        
        String host = request.getServerName();
        int port = request.getServerPort();

        Path uri = Path.fromString(path);
        uri = decode(uri, encoding);
        
        URL url = new URL(PROTOCOL_HTTP, host, uri);
        url.setPort(new Integer(port));
        if (request.isSecure()) {
            url.setProtocol(PROTOCOL_HTTPS);
        }
        url.setCollection(collection);
        Map<String, String[]> queryStringMap = splitQueryString(
                request.getQueryString());

        for (String key: queryStringMap.keySet()) {
            String[] values = queryStringMap.get(key);
            key = decode(key, encoding);
            for (String value: values) {
                url.addParameter(key, decode(value));
            }
        }
        url.setCharacterEncoding(encoding);
        return url;
    }
    
    
    /**
     * Utility method to create a URL from a servlet request. 
     * Decodes the uri and query string parameters using UTF-8 encoding.
     *
     * @param request the servlet request
     * @return the generated URL
     */
    public static URL create(HttpServletRequest request) {
        try {
            return create(request, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(
                    "UTF-8 encoding not supported on this system");
        }
    }

    private static enum ParseState {
        PROTOCOL,
        HOST,
        PORT,
        PATH,
        QUERY,
        REF
    }

    /**
     * Parses a URL from a string representation. 
     * Also attempts to decode the URL.
     * @param url the string representation
     * @return the parsed URL
     */
    public static URL parse(String url) {
        if (url == null || "".equals(url.trim())) {
            throw new IllegalArgumentException("Malformed URL: " + url);
        }

        StringBuilder protocol = new StringBuilder();
        StringBuilder host = new StringBuilder();
        StringBuilder port = new StringBuilder();
        StringBuilder path = new StringBuilder();
        StringBuilder query = new StringBuilder();
        StringBuilder ref = new StringBuilder();

        ParseState state = ParseState.PROTOCOL;
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            switch (state) {
            case PROTOCOL:
                if (c == ':') {
                    state = ParseState.HOST;
                    i += 2;
                } else if (!(c >= 'a' && c <= 'z')) {
                    throw new IllegalArgumentException("Malformed URL: " + url 
                            + " illegal character in protocol: " + c);
                } else {
                    protocol.append(c);
                }
                break;
            case HOST:
                if (c == ':') {
                    state = ParseState.PORT;
                } else if (c == '/') {
                    state = ParseState.PATH;
                    i--;
                } else if (c == '?') {
                    state = ParseState.QUERY;
                } else if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9') 
                        && c != '.' && c != '-' && c != '_') {
                    throw new IllegalArgumentException("Malformed URL: " + url 
                            + ": illegal character in host name: " + c);
                } else {
                    host.append(c);
                }
                break;
            case PORT:
                if (c == '/') {
                    state = ParseState.PATH;
                    i--;
                } else if (c == '?') {
                    state = ParseState.QUERY;
                } else if (c == '#') {
                    state = ParseState.REF;
                } else if (!(c >= '0' && c <= '9')) {
                    throw new IllegalArgumentException("Malformed URL: " + url 
                            + " illegal port number character: " + c);
                } else {
                    port.append(c);
                }
                break;
            case PATH:
                if (c == '?') {
                    state = ParseState.QUERY;
                } else if (c == '#') {
                    state = ParseState.REF;
                } else {
                    if (c == '/') {
                        int len = path.length();
                        if (len == 0 || (len > 0 && path.charAt(len - 1) != '/')) {
                            // Allow double slashes:
                            path.append(c);
                        }
                    } else {
                        path.append(c);
                    }
                }
                break;
            case QUERY:
                if (c == '#') {
                    state = ParseState.REF;
                } else {
                    query.append(c);
                }
                break;
            case REF:
                ref.append(c);
                break;
            default:
                break;
            }
        }
        if (!(PROTOCOL_HTTP.equals(protocol.toString()) || PROTOCOL_HTTPS.equals(protocol.toString()))) {
            throw new IllegalArgumentException("Malformed URL: " + url);
        }
        if (host.length() == 0) {
            throw new IllegalArgumentException("Malformed URL: " + url 
                    + ": contains no hostname");
        }
        boolean collection = false;
        Path p = null;
        if (path.length() == 0) {
            p = Path.ROOT;
            collection = true;
        } else {
            int length = path.length();
            if (path.charAt(length - 1) == '/') {
                collection = true;
                if (length > 1) {
                    path.delete(length -1, length);
                }
            }
            
            try {
                p = Path.fromString(path.toString());
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Malformed URL: " + url + ": " + e.getMessage());
            }
        }
        Path resultPath = Path.ROOT;
        for (String elem : p.getElements()) {
            if (!"/".equals(elem)) {
                String decoded = decode(elem);
                resultPath = resultPath.expand(decoded);
            }
        }
        Integer portNumber = null;
        if (port.length() > 0) {
            try {
                portNumber = Integer.parseInt(port.toString());
            } catch (Exception e) {
                throw new IllegalArgumentException("Malformed URL: " + e.getMessage());
            }
        }
        URL resultURL = new URL(protocol.toString(), host.toString(), resultPath);
        if (portNumber != null) {
            resultURL.setPort(portNumber);
        } else {
            if (resultURL.getProtocol().equals(PROTOCOL_HTTP)) {
                resultURL.setPort(PORT_80);
            } else {
                resultURL.setPort(PORT_443);
            }
        }

        Map<String, String[]> queryParams = new LinkedHashMap<String, String[]>();
        if (query.length() > 0) {
            queryParams = splitQueryString(query.toString());
        }
        for (String param: queryParams.keySet()) {
            String[] values = queryParams.get(param);
            for (String value: values) {
                resultURL.addParameter(param, decode(value));
            }
        }
        if (ref.length() > 0) {
            resultURL.setRef(ref.toString());
        }
        resultURL.setCollection(collection);
        return resultURL;
    }
    
    public URL relativeURL(String rel) {
        if (rel == null) {
            throw new IllegalArgumentException("Malformed URL: " + rel);
        }
        if ("".equals(rel.trim())) {
            return new URL(this);
        }
        if (rel.startsWith(PROTOCOL_HTTP + ":") || rel.startsWith(PROTOCOL_HTTPS + ":")) {
            return parse(rel);
        }
        String host = this.host;
        if (rel.startsWith("//")) {
            rel = rel.substring(2);
            int i = rel.indexOf("/");
            if (i == -1) i = rel.indexOf("?");
            if (i == -1) i = rel.indexOf("#");
            if (i == -1) i = rel.length();
            host = rel.substring(0, i);
            rel = rel.substring(i);
            if ("".equals(rel.trim())) {
                URL url = new URL(this);
                url.setHost(host);
                url.setPath(Path.ROOT);
                url.setCollection(false);
                url.clearParameters();
                url.setRef(null);
                return url;
            }
        }
        int idx = rel.indexOf("#");
        String anchor = null;
        if (idx != -1) {
            anchor = rel.substring(idx + 1);
            rel = rel.substring(0, idx);
        }
        idx = rel.indexOf("?");
        Map<String, String[]> query = null;
        if (idx == -1 && anchor != null) {
            query = splitQueryString(getQueryString());
        } else if (idx != -1) {
            query = splitQueryString(rel.substring(idx));
            rel = rel.substring(0, idx);
        }
        Path path = this.path;
        boolean coll = rel.endsWith("/") 
            || rel.endsWith(".") 
            || rel.endsWith("..");
        
        if (rel.startsWith("/")) {
            if (coll && !"/".equals(rel)) { 
                rel = rel.substring(0, rel.length() - 1);
            }
            path = Path.fromString(rel);
        } else if (!"".equals(rel)) {
            if (!path.isRoot()) {
                path = path.getParent();
            }
            path = path.expand(rel);
        }
        URL url = new URL(this);
        url.setHost(host);
        url.setCollection(coll);
        url.setPath(path);
        url.clearParameters();
        if (query != null) {
            for (String s: query.keySet()) {
                String[] v = query.get(s);
                if (v == null) {
                    url.addParameter(s, null);
                } else {
                    for (String val: v) {
                        url.addParameter(s, val);
                    }
                }
            }
        }
        url.setRef(null);
        if (anchor != null) {
            url.setRef(anchor);
        }
        return url;
    }
    
     /**
      * Splits a query string into a map of (String, String[]). 
      * Note: the values are not URL decoded.
      */
     public static Map<String, String[]> splitQueryString(String queryString) {
         Map<String, String[]> queryMap = new LinkedHashMap<String, String[]>();
         if (queryString != null) {
             if (queryString.startsWith("?")) { 
                 queryString = queryString.substring(1);
             }
             String[] pairs = queryString.split("&");
             for (int i = 0; i < pairs.length; i++) {
                 if (pairs[i].length() == 0) {
                     continue;
                 }
                 int equalsIdx = pairs[i].indexOf("=");
                 if (equalsIdx == -1) {
                     String[] existing = queryMap.get(pairs[i]);
                     if (existing == null) {
                         queryMap.put(pairs[i], new String[]{""});
                     } else {
                         String[] newVal = new String[existing.length + 1];
                         System.arraycopy(existing, 0, newVal, 0, existing.length);
                         newVal[existing.length] = "";
                         queryMap.put(pairs[i], newVal);
                     }
                 } else {
                     String key = pairs[i].substring(0, equalsIdx);
                     String value = pairs[i].substring(equalsIdx + 1);
                     String[] existing = queryMap.get(key);
                     if (existing == null) {
                         queryMap.put(key, new String[]{value});
                     } else {
                         String[] newVal = new String[existing.length + 1];
                         System.arraycopy(existing, 0, newVal, 0, existing.length);
                         newVal[existing.length] = value;
                         queryMap.put(key, newVal);
                     }
                 }
             }
         }
         return queryMap;
     }

     /**
      * URL encodes the elements of a path using encoding UTF-8.
      *
      * @param path the path to encode
      * @return the encoded path
      */
     public static Path encode(Path path) {
         try {
             return encode(path, "utf-8");
         } catch (UnsupportedEncodingException e) {
             throw new IllegalStateException(
             "UTF-8 encoding not supported on this system");
         }
     }

     /**
      * URL encodes the elements of a path using a 
      * specified character encoding.
      *
      * @param path the path to encode
      * @return the encoded path
      * @throws UnsupportedEncodingException if the specified 
      * encoding is not supported on this system
      */
     public static Path encode(Path path, String encoding) throws UnsupportedEncodingException {
         Path result = Path.ROOT;
         for (String elem : path.getElements()) {
             if (!elem.equals("/")) {
                 result = result.extend(encode(elem, encoding));
             }
         }
         return result;
     }
     
     /**
      * URL encodes a string using encoding UTF-8.
      *
      * @param value a <code>String</code> value
      * @return a <code>String</code>
      */
     public static String encode(String value) {
         try {
             return encode(value, "utf-8");
         } catch (UnsupportedEncodingException e) {
             throw new IllegalStateException(
             "UTF-8 encoding not supported on this system");
         }
     }

     /**
      * URL encodes a string using a specified character encoding.
      *
      * @param value a <code>String</code> value
      * @return a <code>String</code>
      * @throws UnsupportedEncodingException if the specified 
      * character encoding is not supported on this system
      */
     public static String encode(String value, String encoding) throws UnsupportedEncodingException {
         String encoded = URLEncoder.encode(value, encoding);
         // Force hex '%20' instead of '+' as space representation:
         return encoded.replaceAll("\\+", "%20");
     }

     /**
      * URL decodes the elements of a path using encoding UTF-8.
      *
      * @param path the path to encode
      * @return the encoded path
      */
     public static Path decode(Path path) {
         try {
             return decode(path, "utf-8");
         } catch (UnsupportedEncodingException e) {
             throw new IllegalStateException(
             "UTF-8 encoding not supported on this system");
         }
     }
     
     /**
      * URL decodes the elements of a path using a 
      * specified character encoding.
      *
      * @param value a string
      * @return the decoded string
      * @throws UnsupportedEncodingException if the specified 
      * character encoding is not supported on this system
      */
     public static Path decode(Path path, String encoding) 
     throws UnsupportedEncodingException {
         Path result = Path.ROOT;
         for (String elem : path.getElements()) {
             if (!elem.equals("/")) {
                 result = result.extend(decode(elem, encoding));
             }
         }
         return result;
     }
     
     /**
      * URL decodes a string using encoding UTF-8.
      *
      * @param value a string
      * @return the decoded string
      */
     public static String decode(String value) {
         try {
             return decode(value, "utf-8");
         } catch (UnsupportedEncodingException e) {
             throw new IllegalStateException(
             "UTF-8 encoding not supported on this system");
         }
     }

     /**
      * URL decodes a string using a specified character encoding.
      *
      * @param value a string
      * @return the decoded string
      * @throws UnsupportedEncodingException if the specified 
      * character encoding is not supported on this system
      */
     public static String decode(String value, String encoding) throws UnsupportedEncodingException {
       return URLDecoder.decode(value, encoding);
     }
     
     /**
      * Verify whether a given string is URL encoded or not
      *
      * @param original given characters
      * @return true if the given character array is 7 bit ASCII-compatible.
      */
     public static boolean isEncoded(String value) {
         char[] original = value.toCharArray();
         for (int i = 0; i < original.length; i++) {
             int c = original[i];
             if (c > 128) {
                 return false;
             } else if (c == ' ') {
                 return false;
             } else if (c == '%') {
                 if (Character.digit(original[++i], 16) == -1 
                         || Character.digit(original[++i], 16) == -1) {
                     return false;
                 }
             }
         }
         return true;
     }
}
