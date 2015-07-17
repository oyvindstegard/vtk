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

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerInterceptor;

import vtk.context.Categorizable;
import vtk.repository.Path;
import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.security.web.AuthenticationChallenge;
import vtk.web.filter.ServiceFilter;

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
 *  
 */
public interface Service extends Ordered, Categorizable {

    /**
     * Gets this service's list of assertions.
     *
     * @return a <code>List</code> of {@link Assertion} objects.
     * @see vtk.web.service.ServiceHandlerMapping
     */
    public List<Assertion> getAssertions();

    /**
     * Gets this service's list of assertions, including ancestor assertions.
     *
     * @return a <code>List</code> of {@link Assertion} objects.
     */
    public List<Assertion> getAllAssertions();

    
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
     * Constructs a link (URL) for this service to a given resource
     * and a principal.
     *
     * @param resource the resource to construct the URL for
     * @param principal the current principal
     * @return the constructed URL
     * @exception ServiceUnlinkableException if at least one the
     * assertions for this service (or any of the ancestors) fail to
     * match for the resource or principal.
     */
    public String constructLink(Resource resource, Principal principal)
        throws ServiceUnlinkableException;
	

    public URL constructURL(Resource resource, Principal principal)
        throws ServiceUnlinkableException;


    /**
     * Constructs a link (URL) for this service to a given resource
     * and a principal.
     *
     * @param resource the resource to construct the URL for
     * @param principal the current principal
     * @return the constructed URL
     * @param matchAssertions determines whether all assertions must
     * match in order for the link to be constructed
     * @exception ServiceUnlinkableException if
     * <code>matchAssertions</code> is <code>true</code> and at least
     * one of the assertions for this service (or any of the
     * ancestors) fail to match for the resource or principal.
     */
    public String constructLink(Resource resource, Principal principal,
                                boolean matchAssertions)
        throws ServiceUnlinkableException;

    public URL constructURL(Resource resource, Principal principal,
                            boolean matchAssertions)
        throws ServiceUnlinkableException;

    /**
     * Constructs a link (URL) for this service to a given resource
     * and a principal, and a map of extra parameters that will go in
     * the query string.
     *
     * @param resource the resource to construct the URL for
     * @param principal the current principal
     * @param parameters a <code>Map</code> of (key, value) pairs that
     * will be appended to the query string.
     * @return the constructed URL
     * @exception ServiceUnlinkableException if at least one of the
     * assertions for this service (or any of the ancestors) fail to
     * match for the resource or principal.
     */
    public String constructLink(Resource resource, Principal principal, Map<String, String> parameters)
        throws ServiceUnlinkableException;

    public URL constructURL(Resource resource, Principal principal, Map<String, String> parameters)
        throws ServiceUnlinkableException;

    /**
     * Constructs a link (URL) for this service to a given resource
     * and a principal, and a map of extra parameters that will go in
     * the query string. 
     *
     * @param resource the resource to construct the URL for
     * @param principal the current principal
     * @param parameters a map of (key, value) pairs that
     * will be appended to the query string.
     * @param matchAssertions determines whether all assertions must
     * match in order for the link to be constructed
     * @return the constructed URL
     * @exception ServiceUnlinkableException if
     * <code>matchAssertions</code> is <code>true</code> and at least
     * one of the assertions for this service (or any of the
     * ancestors) fail to match for the resource or principal.
     */
    public String constructLink(Resource resource, Principal principal, Map<String, String> parameters,
                                boolean matchAssertions)
        throws ServiceUnlinkableException;
	
    public URL constructURL(Resource resource, Principal principal, Map<String, String> parameters,
                            boolean matchAssertions)
        throws ServiceUnlinkableException;

    public String constructLink(Path uri);

    public URL constructURL(Path uri);
    
    public URL constructURL(Resource resource);

    public String constructLink(Path uri, Map<String, String> parameters);

    public URL constructURL(Path uri, Map<String, String> parameters);

    /**
     * Gets the list of handler interceptors for this service, if any.
     *
     * @return a <code>List</code> of {@link
     * org.springframework.web.servlet.HandlerInterceptor} objects.
     */
    public List<HandlerInterceptor> getHandlerInterceptors();
    

    /**
     * Gets the list of service filters for this service, if any.
     *
     * @return a <code>List</code> of {@link ServiceFilter} objects, 
     * or <code>null</code> if none configured.
     */
    public List<ServiceFilter> getServiceFilters();
    
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

}
