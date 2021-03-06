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
package vtk.web.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerInterceptor;

import vtk.repository.Path;
import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.security.web.AuthenticationChallenge;

/**
 * A service is an abstraction added to the thin web layer in web
 * applications to facilitate two purposes that ordinary web
 * frameworks usually miss:
 * <ul>
 *   <li>Instead of mapping requests to controllers by looking at the
 *       URI and the URI only, it lets you map requests based on
 *       anything you like in a hierarchical way. The mechanism is
 *       made flexible by having Assertions evaluated without
 *       arguments, instead relying on relevant contexts to be
 *       supplied by way of e.g. thread local.
 *   <li>In addition to mapping requests, it's also makes it possible
 *       to dynamically construct request URLs to desired services on
 *       any level by looking at the assertions type
 * </ul>
 * 
 * @see vtk.web.service.ServiceHandlerMapping
 */
public interface Service extends Ordered {

    /**
     * Creates a URL constructor for this service. Typical usage:
     * <code>
     *   URL base = requestContext.getRequestURL(); // starting point
     *   URL serviceURL = service.urlConstructor(base)
     *      .resource(resource)
     *      .principal(principal)
     *      .constructURL();
     * </code>
     * @param base the starting point for the URL construction. The request parameters 
     * are cleared, and the path is set to {@code /}.
     * @return the URL constructor
     */
    public URLConstructor urlConstructor(URL base);
    
    /**
     * A class that constructs URLs to a service based on
     * various information provided by the caller, such as  
     * {@link Path URI}, {@link Resource resource}, or 
     * {@link Principal principal}. The URL may or may not be constructed
     * based on the {@link WebAssertion assertions} 
     * that are configured on the service.
     */
    public static class URLConstructor {
        private Service service;
        private URL base;
        private Path uri;
        private Resource resource;
        private Principal principal;
        private Map<String, List<String>> parameters;
        private Optional<List<BiFunction<URL, Optional<Resource>, URL>>> urlProcessors;
        
        URLConstructor(URL base, Service service) {
            this.base = new URL(base)
                    .clearParameters()
                    .setPath(Path.ROOT)
                    .setCollection(false);
            this.service = service;
            this.urlProcessors = service.urlProcessors()
                    .map(list -> list.stream()
                            .map(factory -> factory.urlProcessor(service, base))
                            .collect(Collectors.toList()));
        }

        /**
         * Specifies the {@link Path path} part of the URL. 
         * Assertions will not be able to perform resource matching.
         * @param uri the path of the URL
         * @return this URL constructor
         */
        public URLConstructor withURI(Path uri) {
            this.uri = uri;
            return this;
        }

        /**
         * Specifies the {@link Path path} part of the URL, as well as enabling assertions
         * to perform matching.
         * @param resource the resource object whose URI will constitute the path of the URL
         * @return this URL constructor
         */
        public URLConstructor withResource(Resource resource) {
            this.resource = resource;
            return this;
        }

        /**
         * Specifies the {@link Principal principal} for which the URL is constructed. If no 
         * principal (or the value {@code null}) is specified, the URL construction is 
         * considered to be anonymous.
         * @param principal the principal
         * @return this URL constructor
         */
        public URLConstructor withPrincipal(Principal principal) {
            this.principal = principal;
            return this;
        }

        /**
         * Specifies a query string parameter to add to the constructed URL
         * @param name the name of the query string parameter
         * @param value the value of the query string parameter
         * @return this URL constructor
         */
        public URLConstructor withParameter(String name, String value) {
            if (this.parameters == null) {
                this.parameters = new HashMap<>();
            }
            parameters.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            return this;
        }
        
        /**
         * Specifies map of a query string parameters to add to the constructed URL
         * @param parameters the query string parameter map
         * @return this URL constructor
         */
        public URLConstructor withParameters(Map<String, List<String>> parameters) {
            if (parameters != null) {
                this.parameters = new HashMap<>(parameters);
            }
            return this;
        }

       /**
        * Attempts to construct the URL. All assertions of this service (and its ancestors)
        * are given an opportunity to contribute to the URL construction. If {@link #resource}
        * is specified, {@link WebAssertion#processURL(URL, Resource, Principal)} is called for each 
        * assertion, otherwise {@link WebAssertion#processURL(URL)} is called.
        * 
        * If the URL cannot be constructed (i.e. at least one assertion fails to match),
        * {@link ServiceUnlinkableException} is thrown.
        * 
        * @return the URL as constructed by the service
        * @throws ServiceUnlinkableException if at least one assertion fails to match
        */
        public URL constructURL() throws ServiceUnlinkableException {
            if (resource == null && uri == null) {
                throw new IllegalStateException(
                        "Either 'resource' or 'uri' must be specified");
            }
            URL urlObject = new URL(this.base);
            if (resource != null) {
                urlObject.setPath(resource.getURI());
            }
            else {
                urlObject.setPath(uri);
            }
            
            if (resource != null && resource.isCollection()) {
                urlObject.setCollection(true);
            }
            
            if (parameters != null) {
                for (Map.Entry<String, List<String>> entry: parameters.entrySet()) {
                    for (String value: entry.getValue()) {
                        urlObject.addParameter(entry.getKey(), value);
                    }
                }
            }
            
            for (WebAssertion assertion: service.getAllAssertions()) {
                Optional<URL> url = Optional.empty();
                if (resource != null) {
                    try {
                        url = assertion.processURL(urlObject, resource, principal);
                    }
                    catch (Exception e) { }

                    if (!url.isPresent()) {
                        throw new ServiceUnlinkableException(
                                "Unable to construct URL to service " + service.getName() 
                                + " for resource " + resource.getURI() + ". "
                                + "Assertion " + assertion + " failed to match");
                    }
                }
                else {
                    url = Optional.ofNullable(assertion.processURL(urlObject));
                }
                if (url.isPresent()) {
                    urlObject = url.get();
                }
            }
            if (urlProcessors.isPresent()) {
                for (BiFunction<URL, Optional<Resource>, URL> processor: urlProcessors.get()) {
                    urlObject = processor.apply(urlObject, Optional.ofNullable(resource));
                }
            }
            return urlObject;
        }
    }

    /**
     * Gets this service's list of assertions.
     *
     * @return a <code>List</code> of {@link WebAssertion} objects.
     * @see vtk.web.service.ServiceHandlerMapping
     */
    public List<WebAssertion> getAssertions();

    /**
     * Gets this service's list of assertions, including ancestor assertions.
     *
     * @return a <code>List</code> of {@link WebAssertion} objects.
     */
    public List<WebAssertion> getAllAssertions();

    
    /**
     * Gets this service's controller, if it has one.
     *
     * @return a {@link
     * org.springframework.web.servlet.mvc.Controller} object, or
     * <code>null</code> if no controller exists for this service.
     */
    public Object getHandler();


    /**
     * Gets the name of this service.
     *
     * @return a <code>String</code>
     */
    public String getName();
	

    /**
     * Gets a named attribute.
     *
     * @param name the name of the attribute
     * @return the attribute value.
     */
    public Object getAttribute(String name);
    
    /**
     * Gets this service's parent service.
     *
     * @return the parent service, or <code>null</code> if this is the
     * root service.
     */
    public Service getParent();
	
    /**
     * Checks whether this service is a descendant of another service.
     *
     * @param service - the service in question
     * @return <code>true</code> if this service is a descendant of
     * the other service, <code>false</code> otherwise.
     */
    public boolean isDescendantOf(Service service);

    /**
     * Gets the list of handler interceptors for this service, if any.
     *
     * @return a <code>List</code> of {@link
     * org.springframework.web.servlet.HandlerInterceptor} objects.
     */
    public List<HandlerInterceptor> getHandlerInterceptors();
    
    /**
     * Gets the list of servlet filters for this service, if any.
     *
     * @return a <code>List</code> of {@link Filter} objects, 
     * or <code>null</code> if none configured.
     */
    public List<Filter> getServletFilters();

    /**
     * Gets this service's authentication challenge. 
     *
     * @return a {@link AuthenticationChallenge}, or
     * <code>null</code> if none has been defined.
     */
    public AuthenticationChallenge getAuthenticationChallenge();

    /**
     * Adds this service to the children of another service.
     *
     * @param service the service to become the new parent of this
     * service.
     */
    public void setParent(Service service);

    /**
     * 
     * @return A localized name for this service
     */
    public String getLocalizedName(Resource resource, HttpServletRequest request);

    /**
     * Gets an optional list of {@link URLPostProcessor URL post processors} 
     * for this service
     * @return the (optional) list of URL post processors
     */
    public Optional<List<URLPostProcessor>> urlProcessors();
}
