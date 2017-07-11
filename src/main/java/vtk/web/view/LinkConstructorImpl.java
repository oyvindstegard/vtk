/* Copyright (c) 2004, University of Oslo, Norway
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
package vtk.web.view;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.repository.Path;
import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.ServiceUnlinkableException;
import vtk.web.service.URL;


public class LinkConstructorImpl implements LinkConstructor {
    private static final Logger logger = LoggerFactory.getLogger(LinkConstructorImpl.class);

    public URL construct(HttpServletRequest request, Object arg, String parametersCSV, String serviceName) {
        if (arg == null) return null;
        try {
            Path uri = null;
            Resource resource = null;
            String strUri = null;

            if (arg instanceof Resource) {
                resource = (Resource) arg;
                uri = resource.getURI();
            }
            else if (arg instanceof Path) {
                uri = (Path) arg;
            }
            else if (arg instanceof String) {
                strUri = (String) arg;
                if (strUri.contains("://")) {
                    return getUrlFromUrl(strUri);
                }
                uri = RequestContext.getRequestContext(request).getResourceURI();

                if (isSet(strUri)) {
                    uri = RequestContext.getRequestContext(request).getCurrentCollection();

                    if (strUri.startsWith("/")) {
                        uri = Path.ROOT.expand(strUri.substring(1));
                    }
                    else {
                        uri = uri.expand(strUri);
                    }
                }
            }
            else {
                throw new IllegalArgumentException("Unsupported argument type: " + arg);
            }

            Service.URLConstructor urlBuilder = null;

            if (isSet(serviceName)) {
                Optional<Service> service = RequestContext.getRequestContext(request).service(serviceName);
                if (service.isPresent()) {
                    urlBuilder = service.get().urlConstructor(RequestContext.getRequestContext(request).getRequestURL());
                }
            }

            if (urlBuilder == null) {

                urlBuilder = RequestContext.getRequestContext(request).getService().urlConstructor( 
                        RequestContext.getRequestContext(request).getRequestURL());
            }
            Principal principal = RequestContext.getRequestContext(request).getPrincipal();
            if (resource != null) {
                return urlBuilder.withResource(resource)
                        .withPrincipal(principal)
                        .withParameters(getParametersMap(parametersCSV))
                        .constructURL();
            }
            return urlBuilder
                    .withURI(uri)
                    .withPrincipal(principal)
                    .withParameters(getParametersMap(parametersCSV))
                    .constructURL();

        }
        catch (ServiceUnlinkableException e) {
            return null;
        }
        catch (Exception e) {
            logger.info("Caught exception on link construction", e);
            return null;
        }
    }

    private URL getUrlFromUrl(String url)
            throws UnsupportedEncodingException {
        if (URL.isEncoded(url)) { 
            url = URL.decode(url);
        }
        return URL.parse(url);
    }

    private boolean isSet(String value) {
        return value != null && !value.trim().equals("");
    }

    private Map<String, List<String>> getParametersMap(String parametersCSV) {
        if (parametersCSV == null || parametersCSV.trim().equals(""))
            return null;

        Map<String, List<String>> parameters = new LinkedHashMap<>();

        for (String mapping: parametersCSV.split(",")) {
            if (!mapping.contains("=")) {
                throw new IllegalArgumentException(
                        "Each entry in the parameters string must be in the format "
                                + "'<paramname>=<paramvalue>'");
            }	

            String parameterName = mapping.substring(0, mapping.indexOf("=")).trim();

            List<String> parameterValues = Collections.singletonList(
                    mapping.substring(mapping.lastIndexOf("=") + 1).trim()
                    );
            parameters.put(parameterName, parameterValues);
        }
        return parameters;
    }
}
