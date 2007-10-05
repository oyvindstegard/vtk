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
package org.vortikal.web.view;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.vortikal.repository.Repository;
import org.vortikal.security.SecurityContext;
import org.vortikal.util.repository.URIUtil;
import org.vortikal.web.RequestContext;
import org.vortikal.web.service.Service;

public class LinkConstructorImpl implements LinkConstructor, ApplicationContextAware {

    private static Log logger = LogFactory.getLog(LinkConstructorImpl.class);
    
	private Repository repository;
	private ApplicationContext context;
	
    public String construct(String resourceUri, String parametersCSV, String serviceName) {
        try {
            if (resourceUri != null && URIUtil.isUrl(resourceUri)) {
                return getUrlFromUrl(resourceUri);
            }

            String uri = RequestContext.getRequestContext().getResourceURI();

            if (isSet(resourceUri)) {
                uri = URIUtil.getAbsolutePath(resourceUri, uri);
            }
            
            Service service = RequestContext.getRequestContext().getService();

            if (isSet(serviceName))
                service = getService(serviceName);

            return createUrl(service, uri, getParametersMap(parametersCSV));
		
		} catch (Exception e) {
            logger.info("Caught exception on link construction", e);
            return "";
		}
	}

    private String getUrlFromUrl(String url)
            throws UnsupportedEncodingException {
        if (URIUtil.isEscaped(url)) 
            return url;
        
        return URLEncoder.encode(url, "UTF-8");
    }

    private String createUrl(Service service, String uri,
            Map<String, String> parameters) throws IOException {
        
//        String token = SecurityContext.getSecurityContext().getToken();
//
//        this.repository.retrieve(token, uri, true);

        return service.constructLink(uri, parameters);
    }

    private boolean isSet(String value) {
        return value != null && !value.trim().equals("");
    }

	private Map<String, String> getParametersMap(String parametersCSV) {
	    if (parametersCSV == null || parametersCSV.trim().equals(""))
	        return null;
	    
	    Map<String, String> parameters = new LinkedHashMap<String, String>();

	    for (String mapping: parametersCSV.split(",")) {
			if (mapping.indexOf("=") == -1) {
				throw new IllegalArgumentException(
				        "Each entry in the parameters string must be in the format "
						+ "'<paramname>=<paramvalue>'");
			}	

			String parameterName = mapping.substring(0, mapping.indexOf("=")).trim();
			
			String parameterValue = mapping.substring(mapping.lastIndexOf("=") + 1).trim();
			parameters.put(parameterName, parameterValue);
		}
		return parameters;
	}

    private Service getService(String serviceName) {
        return (Service) this.context.getBean(serviceName, Service.class);
    }

	@Required
	public void setRepository(Repository repository) {
		this.repository = repository;
	}
	
	@Required
	public void setApplicationContext(ApplicationContext context) throws BeansException {
	    this.context = context;
        }


}
