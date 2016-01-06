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
package vtk.web.service;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import vtk.repository.Path;
import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.util.repository.LocaleHelper;

import java.util.*;
import vtk.web.RequestContext;

/**
 *  Tries to locate a VTK Service with the given name and use that to construct
 *  a URL to that Service. If the Service is not found it falls back to look up
 *  the service name in a map of default URLs.
 *
 *  <pre>{@code
 *  var serviceUrl1 = serviceUrlProvider.builder("service-name")
 *      .withResource(aResource)
 *      .build()
 *  var serviceUrl2 = serviceUrlProvider.builder("other-name")
 *      .withPath(Path.fromString("/vrtx"))
 *      .build()
 *  }</pre>
 *
 *  @see vtk.web.service.Service
 */
public class ServiceUrlProvider implements ApplicationContextAware {
    private static final String NO_SUCH_SERVICE_EXCEPTION_TEMPLATE = "Service with name '%s' not found";
    private ApplicationContext context;
    private Map<String, String> defaultUrlMap;

    /**
     *
     * @param serviceName unique name of the service
     * @return the ServiceUrlBuilder
     */
    public ServiceUrlBuilder builder(String serviceName) {
        /* XXX service URL construction requires a valid RequestContext on the current
           thread, but this is not always the case (misc background threads not originating
           from request layer, etc.)
           And since this code can be inovked at a low level, due to being integrated
           into principal metadata, we must take this into account. Otherwise, only
           request threads will be able to load from repo without ugly errors (IllegalStateException)
           occuring in PrincipalFactory.
           This problem will likely go away to a great degree when Principal refactoring
           removes metadata concept from repository/DAO level.
         */
        if (! RequestContext.exists()) {
            // Cannot use Service URL construction facility, do best effort ..
            String serviceUrl = defaultUrlMap.get(serviceName);
            if (serviceUrl == null)
                throw new RuntimeException("Service URL cannot be constructed without request context, and no default URL found: "  
                        + serviceName);
            
            return new ServiceUrlBuilder(serviceUrl);
        }

        try {
            return builder(context.getBean(serviceName, Service.class));
        } catch (NoSuchBeanDefinitionException e) {
            String serviceUrl = defaultUrlMap.get(serviceName);
            if (serviceUrl == null)
                throw new NoSuchServiceException(String.format(NO_SUCH_SERVICE_EXCEPTION_TEMPLATE, serviceName));;
            return new ServiceUrlBuilder(serviceUrl);
        } catch (BeanNotOfRequiredTypeException e) {
            throw new NoSuchServiceException(String.format(NO_SUCH_SERVICE_EXCEPTION_TEMPLATE, serviceName));
        }
    }
    
    public ServiceUrlBuilder builder(Service service) {
        return new ServiceUrlBuilder(service);
    }

    public void setDefaultUrlMap(Map<String, String> defaultUrlMap) {
        this.defaultUrlMap = defaultUrlMap;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    public static class ServiceUrlBuilder {
        private final Service service;
        private URL url;
        private Resource resource = null;
        private Principal principal = null;
        private Map<String, List<String>> parameters = new HashMap<>();
        private boolean matchAssertions = true;
        private Path path = Path.ROOT;
        private Locale locale = null;
        private String localeKey = "lang";

        private ServiceUrlBuilder(Service service) {
            this.service = service;
            this.url = null;
        }

        private ServiceUrlBuilder(String serviceUrl) {
            this.service = null;
            this.url = URL.parse(serviceUrl);
        }

        public ServiceUrlBuilder withResource(Resource resource) {
            this.resource = resource;
            return this;
        }

        public ServiceUrlBuilder withPrincipal(Principal principal) {
            this.principal = principal;
            return this;
        }

        public ServiceUrlBuilder withParameters(Map<String, List<String>> parameters) {
            if (parameters != null) {
                this.parameters.putAll(parameters);
            }
            return this;
        }

        public ServiceUrlBuilder withParameter(String key, String ...values) {
            this.parameters.put(key, Arrays.asList(values));
            return this;
        }

        public ServiceUrlBuilder withLocaleParameter(String key, Locale locale) {
            this.locale = locale;
            this.localeKey = key;
            return this;
        }

        public ServiceUrlBuilder withMatchAssertions(boolean matchAssertions) {
            this.matchAssertions = matchAssertions;
            return this;
        }

        public ServiceUrlBuilder withPath(Path path) {
            this.path = path;
            return this;
        }

        public URL build() {
            if (url == null) {
                if (resource == null)
                    url = service.constructURL(path);
                else
                    url = service.constructURL(resource, principal, matchAssertions);
            }
            for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
                for (String value : entry.getValue()) {
                    url.addParameter(entry.getKey(), value);
                }
            }
            if (locale != null) {
                String preferredLang = LocaleHelper.getPreferredLang(locale);
                if (preferredLang != null) {
                    url.addParameter(this.localeKey, preferredLang);
                }
            }
            return url;
        }
    }
}
