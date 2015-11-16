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
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import vtk.repository.Path;
import vtk.web.RequestContext;
import vtk.web.service.ServiceUrlProvider;
import vtk.web.service.URL;


public class LinkConstructorImpl implements LinkConstructor {
    private static final Log logger = LogFactory.getLog(LinkConstructorImpl.class);
    
	private final ServiceUrlProvider serviceUrlProvider;

    public LinkConstructorImpl(ServiceUrlProvider serviceUrlProvider) {
        this.serviceUrlProvider = serviceUrlProvider;
    }

    public URL construct(String resourceUri, String parametersCSV, String serviceName) {
        try {
            if (resourceUri != null && resourceUri.contains("://")) {
                return getUrlFromUrl(resourceUri);
            }

            Path uri = RequestContext.getRequestContext().getResourceURI();
            if (isSet(resourceUri)) {
                uri = RequestContext.getRequestContext().getCurrentCollection();

                if (resourceUri.startsWith("/")) {
                    uri = Path.ROOT.expand(resourceUri.substring(1));
                } else {
                    uri = uri.expand(resourceUri);
                }
            }

            ServiceUrlProvider.ServiceUrlBuilder urlBuilder;
            if (isSet(serviceName)) {
                urlBuilder = serviceUrlProvider.builder(serviceName);
            } else {
                urlBuilder = serviceUrlProvider.builder(RequestContext.getRequestContext().getService());
            }
            return urlBuilder.withPath(uri).withParameters(getParametersMap(parametersCSV)).build();

		} catch (Exception e) {
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
