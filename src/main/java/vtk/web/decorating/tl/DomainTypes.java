/* Copyright (c) 2015, University of Oslo, Norway
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
package vtk.web.decorating.tl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.web.servlet.support.RequestContextUtils;

import vtk.repository.Acl;
import vtk.repository.Path;
import vtk.repository.Privilege;
import vtk.repository.store.PrincipalMetadata;
import vtk.security.Principal;
import vtk.security.PrincipalFactory;
import vtk.web.RequestContext;
import vtk.web.service.URL;

final class DomainTypes {

    static abstract class Result<T> extends DomainMap {
        public Result(Object... mappings) {
            super(mappings);
        }
        public abstract boolean isSuccess();
        public Success<T> asSuccess() {
            if (isSuccess()) return (Success<T>) this;
            throw new IllegalStateException("Failure");
        }
        public Failure<T> asFailure() {
            if (!isSuccess()) return (Failure<T>) this;
            throw new IllegalStateException("Success");
        }
    }
    
    public static final class Success<T> extends Result<T> {
        public Success(T object) {
            super(
                "success", true,
                "value",   object
            );
        }
        @Override
        public boolean isSuccess() { return true; }
        public T value() { return (T) get("value"); }
    }
    
    public static final class Failure<T> extends Result<T> {
        public Failure(Throwable t) {
            super(
                "success", false,
                "error",   t.getMessage()
            );
        }
        public Failure(String msg) {
            super(
                "success", false, 
                "error",   msg
             );
        }
        @Override
        public boolean isSuccess() { return false; }
        public String error() { return (String) get("error"); }
    }
    

    public static final class RequestContextType extends DomainMap {
        private RequestContext requestContext;
        
        private static Map<String, Object> principalMetadata(RequestContext requestContext) {
            Locale locale = RequestContextUtils.getLocale(requestContext.getServletRequest());
            PrincipalMetadata metadata = requestContext.principalMetadata(locale);
            if (metadata == null) return null;
            return metadata.toMap();
        }

        public RequestContextType(RequestContext requestContext) {
            super(
               "current-collection",   requestContext.getCurrentCollection(),
               "index-file",           requestContext.isIndexFile(),
               "resource-uri",         requestContext.getResourceURI(),
               "resource-acl",         aclToMap(requestContext.getResourceAcl()),
               "read-restricted",      isReadRestricted(requestContext.getResourceAcl()),
               "collection",           requestContext.getCurrentCollection().equals(
                                           requestContext.getResourceURI()),
               "request-url",          new URLType(URL.create(requestContext.getServletRequest())),
               "headers",              headersToMap(requestContext.getServletRequest()),
               "principal",            requestContext.getPrincipal(),
               "view-unauthenticated", requestContext.isViewUnauthenticated(),
               "principal-metadata",   principalMetadata(requestContext)
             );
            this.requestContext = requestContext;
        }
        public Path currentCollection() { return (Path) get("current-collection"); }
        public Boolean indexFile()      { return (Boolean) get("index-file"); }
        public Path resourceURI()       { return (Path) get("resource-uri"); }
        public Map<String,Object> resourceAcl()
            { return (Map<String, Object>) get("resource-acl"); }
        public Boolean readRestricted() { return (Boolean) get("read-restricted"); }
        public URLType requestURL()     { return (URLType) get("request-url"); }
        public Principal principal()    { return (Principal) get("principal"); }
        public Boolean viewUnathenticated()
            { return (Boolean) get("view-unauthenticated"); }
        public Map<String, Object> headers()
            { return (Map<String, Object>) get("headers"); }
        RequestContext requestContext () { return requestContext; }
    }


    public static final class URLType extends DomainMap {
        public URLType(URL url) {
            super(
                "base",       url.getBase(),
                "protocol",   url.getProtocol(),
                "host",       url.getHost(),
                "port",       url.getPort(),
                "path",       url.getPath(),
                "parameters", params(url),
                "full",       url.toString()
             );
        }
        
        public String base() { return (String) get("base"); }
        public Integer protocol() { return (Integer) get("protocol"); }
        public String host() { return (String) get("host"); }
        public Path path() { return (Path) get("path"); }
        public Map<String, Object> parameters() 
            { return (Map<String, Object>) get("parameters"); }
        public String fullURL() { return (String) get("full"); }
        
        private static Map<String, Object> params(URL url) {
            Map<String, Object> parameters = new HashMap<>();
            for (String name: url.getParameterNames()) {
                parameters.put(name, url.getParameter(name));
            }
            return Collections.unmodifiableMap(parameters);
        }
    }
    
    
    public static Result<Path> toPath(Object ref, RequestContext requestContext) {
        if (ref instanceof Path) {
            return new Success<>((Path) ref);
        }
        if (ref instanceof URLType) {
            return new Success<>(((URLType) ref).path());
        }
        String s = ref.toString();
        if (s.startsWith("/")) {
            try {
                return new Success<>(Path.fromString(s));
            } catch (Throwable t) { return new Failure<>(t); }
        }
        try {
            Path uri = requestContext.getCurrentCollection().expand(s);
            return new Success<>(uri);
        } catch (Throwable t) { return new Failure<>(t); } 
    }
    
    
    @SuppressWarnings("unchecked")
    private static Map<String, Object> headersToMap(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        for (Enumeration<String> names = request.getHeaderNames(); names.hasMoreElements();) {
            String name = names.nextElement();
            Enumeration<String> values = request.getHeaders(name);
            List<String> valueList = new ArrayList<>();
            while (values.hasMoreElements()) valueList.add(values.nextElement());
            
            Map<String, Object> entry = new HashMap<>();
            if (!valueList.isEmpty()) {
                entry.put("values", valueList);
                entry.put("value", valueList.get(0));
            }
            result.put(name, entry);
        }
        return Collections.unmodifiableMap(result);
    }

    private static boolean isReadRestricted(Acl acl) {
        return ! (acl.hasPrivilege(Privilege.READ, PrincipalFactory.ALL)
		  || acl.hasPrivilege(Privilege.READ_PROCESSED, PrincipalFactory.ALL));
    }

    private static Map<String, Object> aclToMap(Acl acl) {
        Map<String, Object> result = new HashMap<>();
        for (Privilege privilege: acl.getActions()) {
            List<String> principals = new ArrayList<>();
            for (Principal principal: acl.getPrincipalSet(privilege)) {
                principals.add(principal.toString());
            }
            result.put(privilege.toString(), principals);
        }
        return result;
    }

}
