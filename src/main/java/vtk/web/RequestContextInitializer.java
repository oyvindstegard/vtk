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
package vtk.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.core.OrderComparator;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.RepositoryException;
import vtk.repository.Resource;
import vtk.repository.ResourceLockedException;
import vtk.repository.ResourceNotFoundException;
import vtk.repository.store.PrincipalMetadataDAO;
import vtk.security.AuthenticationException;
import vtk.security.SecurityContext;
import vtk.security.token.TokenManager;
import vtk.util.text.TreePrinter;
import vtk.web.service.Service;
import vtk.web.service.ServiceResolver;
import vtk.web.service.URL;
import vtk.web.service.WebAssertion;

/**
 * Request context initializer. On every request the {@link Service}
 * tree(s) are traversed and assertions are tested until a match is
 * found. The matched service is placed in the {@link RequestContext},
 * which is associated with the request using a thread local.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>repository</code> - required {@link Repository content
 *   repository}
 *   <li><code>trustedToken</code> - required token used for initial
 *   resource retrieval. This token should be able to read
 *   every resource (see {@link TokenManager#getRegisteredToken(vtk.security.Principal)} and
 *   {@link vtk.security.roles.RoleManager.Role#READ_EVERYTHING})
 *   <li><code>services</code> - a list of {@link Service services}
 *   to construct a service tree of.
 *   <li><code>indexFileResolver</code> - an optional {@link IndexFileResolver}
 * </ul>
 *
 */
public class RequestContextInitializer implements ContextInitializer, ServiceResolver {
    // For service lookup:
    private final Map<String, Service> services = new HashMap<>();
    
    // Map containing parent -> children mapping, effectively representing top-down graph of the service trees.
    private final Map<Service, List<Service>> childServices = new HashMap<>();

    // List of all root services
    private final List<Service> rootServices = new ArrayList<>();
    
    private IndexFileResolver indexFileResolver;
    private PrincipalMetadataDAO principalMetadataDAO;
    private static Logger logger = LoggerFactory.getLogger(RequestContextInitializer.class);

    private String trustedToken;
    private Repository repository;
    
    private Set<String> nonRepositoryRoots = new HashSet<>();
    
    private String viewUnauthenticatedParameter = null;
    
    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }
 

    @Required
    public void setServices(List<Service> services) {

        if (services == null) {
            throw new IllegalArgumentException("Property 'services' cannot be null");
        }
        
        for (Service service : services) {
            this.services.put(service.getName(), service);
            
            Service parent = service.getParent();
            if (parent == null) {
                rootServices.add(service);
            }
            else {
                List<Service> children = childServices.get(parent);
                if (children == null) {
                    children = new ArrayList<>();
                    childServices.put(parent, children);
                }
                if (!children.contains(service)) {
                    children.add(service);
                }
            }
        }

        if (rootServices.isEmpty()) {
            throw new BeanInitializationException(
                    "No services defined in context.");
        }
        
        Collections.sort(rootServices, new OrderComparator());

        OrderComparator orderComparator = new OrderComparator();
        for (List<Service> children: childServices.values()) {
            Collections.sort(children, orderComparator);
        }

        for (Service root: rootServices) {
            List<Service> children = childServices.get(root);
            if (children != null) {
                List<WebAssertion> assertions = root.getAssertions();
                for (Service child : children) {
                    validateAssertions(child, assertions);
                }
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info("Registered service tree root services in the following order: " 
                        + rootServices);
            logger.info("Service tree:");
            logger.info("\n" + getServiceTreeAsString(true));
        }
    }
    
    @Required
    public void setTrustedToken(String trustedToken) {
        this.trustedToken = trustedToken;
    }

    public void setNonRepositoryRoots(Set<String> nonRepositoryRoots) {
        if (nonRepositoryRoots != null) {
            this.nonRepositoryRoots = nonRepositoryRoots;
        }
    }

    @Override
    public Optional<Service> service(String name) {
        return Optional.ofNullable(services.get(Objects.requireNonNull(name)));
    }

    @Override
    public void createContext(HttpServletRequest request) {
        
        RequestContext.push(request);

        SecurityContext securityContext = SecurityContext.getSecurityContext(request);
        if (securityContext == null) {
            throw new IllegalStateException(
                    "Cannot initialize request context, no security context exists on request " 
                            + request);
        }
        Repository repository = RequestLocalRepository.create(request, this.repository);
        
    	URL url;
    	try {
    	    url = URL.create(request);
    	    // Provoke URL parsing errors in an early stage:
    	    if (request.getQueryString() != null)
    	        request.getParameterMap();
    	}
    	catch (Throwable t) {
    	    String msg = request.getRequestURL() + 
    	            (request.getQueryString() != null ? request.getQueryString() : "");
    	    throw new InvalidRequestException("Invalid request: " + msg);
    	}
    	Path uri = url.getPath();
        Resource resource = null;

        boolean inRepository = true;
        // Avoid doing repository retrievals if we know that this URI 
        // does not exist in the repository:
        for (String prefix : nonRepositoryRoots) {
            if (uri.toString().startsWith(prefix)) {
                inRepository = false;
                break;
            }
        }
        
        try {
            if (inRepository) {
                resource = repository.retrieve(trustedToken, uri, false);
            }
        }
        catch (ResourceNotFoundException e) {
            // Ignore, this is not an error
        }
        catch (ResourceLockedException e) {
            // Ignore, this is not an error
        }
        catch (RepositoryException e) {
            throw e;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        
        
        Path indexFileUri = null;
        boolean isIndexFile = false;
        if (indexFileResolver != null && resource != null) {
            if (resource.isCollection()) {
                indexFileUri = indexFileResolver.getIndexFile(resource);
            }
            else {
                try {
                    Resource parent = repository.retrieve(trustedToken, resource.getURI().getParent(), false);
                    isIndexFile = uri.equals(indexFileResolver.getIndexFile(parent));
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
        final boolean viewUnauthenticated = isViewUnauthenticated(request);
        
        for (Service service: rootServices) {

            // Set an initial request context (with the resource, but
            // without the matched service)
            RequestContext.setRequestContext(
                new RequestContext(request, securityContext, service, this, resource, 
                        uri, indexFileUri, isIndexFile, viewUnauthenticated,
                        inRepository, repository, principalMetadataDAO), request);
            
            // Resolve the request to a service:
            if (resolveService(service, request, resource, securityContext)) {
                break;
            }
             
            RequestContext.setRequestContext(null, request);
        }

        if (RequestContext.getRequestContext(request) == null) {
            String requestURL = request.getRequestURL() + 
                    (request.getQueryString() != null ? request.getQueryString() : "");

            throw new UnmappableRequestException("Unable to map request " 
                    + requestURL + " to a valid service", request);
        }
    }

    public void destroyContext(HttpServletRequest request) {
        RequestContext.pop(request);
    }


    /**
     * Resolves a request recursively to a service and creates the
     * request context.
     * 
     * @param service the currently matched service. Should be set to
     * the root service initially.
     * @param request the <code>HttpServletRequest</code>
     * @param resource the resource (may be null)
     * @return If the service doesn't match the context,
     * <code>false</code> is returned. Otherwise the service' children
     * are queried for matches (recursivly) and the first match
     * returns <code>true</code>. If no children matches, this service
     * returns with <code>true</code>.  If there are assertions and
     * one (or more) assertions doesn't match, return null. Else
     * return this Service or the first matching child's result.
     * 
     */
    private boolean resolveService(Service service, HttpServletRequest request,
            Resource resource, SecurityContext securityContext) {
		
        if (logger.isTraceEnabled()) {
            logger.trace("Matching for service " + service.getName() +
                         ", having assertions: " + service.getAssertions());
        }
        RequestContext requestContext = RequestContext.getRequestContext(request);

        try {
            for (WebAssertion assertion: service.getAssertions()) {

                if (!assertion.matches(request,resource,securityContext.getPrincipal())) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Unmatched assertion: " + assertion +
                            " for service " + service.getName());
                    }
                    return false;
                }
                if (logger.isTraceEnabled()) {
                    logger.trace("Matched assertion: " + assertion +
                            " for service " + service.getName());
                } 
            }
        }
        catch (AuthenticationException e) {
            RequestContext.setRequestContext(
                new RequestContext(request, securityContext, service, this, resource,
                                   requestContext.getResourceURI(),
                                   requestContext.getIndexFileURI(), 
                                   requestContext.isIndexFile(),
                                   requestContext.isViewUnauthenticated(),
                                   requestContext.isInRepository(),
                                   requestContext.getRepository(), 
                                   principalMetadataDAO), 
                request);
            throw(e);
        }

        List<Service> children = childServices.get(service);
        if (children != null) {
            if (logger.isTraceEnabled()) {
                logger.trace("Currently matched service: " + service.getName() +
                        ", will check for child services: " + children);
            }

            for (Service child : children) {
                if (resolveService(child, request, resource, securityContext)) {
                    return true;
                }
            }
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("Service matching produced result: " + service.getName());
        }

        RequestContext.setRequestContext(
            new RequestContext(request, securityContext, service, this, resource,
                               requestContext.getResourceURI(),
                               requestContext.getIndexFileURI(), 
                               requestContext.isIndexFile(),
                               requestContext.isViewUnauthenticated(),
                               requestContext.isInRepository(),
                               requestContext.getRepository(), 
                               principalMetadataDAO), request);
        return true;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName());
        sb.append(": ").append(System.identityHashCode(this));
        return sb.toString();
    }
    

    public String getServiceTreeAsString(boolean printAssertions) {
        TreePrinter.ModelBuilder builder = TreePrinter.newModelBuilder();
        for (Service root: rootServices) {
            addServiceToTreeModel(root, printAssertions, true, builder);
        }
        TreePrinter tp = new TreePrinter(new TreePrinter.Format(){
            @Override
            public String nodeNamePrefix(boolean siblingsFollowing) {
                return siblingsFollowing ? "\u251c\u2500>" : "\u2514\u2500>";
            }
            @Override
            public char verticalTreeLine() {
                return '\u2502';
            }
            @Override
            public String rootNodeNamePrefix() {
                return "\u2500>";
            }
            @Override
            public int maxLevelIndentation() {
                return 3;
            }
        });
        return tp.render(builder.getModel());
    }

    private void addServiceToTreeModel(Service service, boolean printAssertions,
            boolean root, TreePrinter.ModelBuilder b) {

        StringBuilder sb = new StringBuilder();
        sb.append(service.getName());
        if (service.getOrder() == Integer.MAX_VALUE) {
            sb.append(" (*)");
        } else {
            sb.append(" (").append(service.getOrder()).append(")");
        }

        if (root) {
            b.add(sb.toString());
        } else {
            b.addChild(sb.toString());
        }

        if (printAssertions) {
            for (WebAssertion assertion : service.getAssertions()) {
                b.addAttribute(assertion);
            }
        }

        List<Service> children = childServices.get(service);
        if (children != null) {
            for (Service child: children) {
                addServiceToTreeModel(child, printAssertions, false, b);
            }
        }

        if (!root) {
            b.toParent();
        }
    }

    public void setIndexFileResolver(IndexFileResolver indexFileResolver) {
        this.indexFileResolver = indexFileResolver;
    }
    
    public void setPrincipalMetadataDAO(PrincipalMetadataDAO principalMetadataDao) {
        this.principalMetadataDAO = principalMetadataDao;
    }
    
    private void validateAssertions(Service child, 
            List<WebAssertion> parentAssertions) throws BeanInitializationException {

        for (WebAssertion assertion : child.getAssertions()) {
        
            for (WebAssertion parentAssertion : parentAssertions) {
            
                if (assertion.conflicts(parentAssertion)) {
                    throw new BeanInitializationException(
                        "Assertion " +  assertion + " for service " +
                        child.getName() + " is conflicting with assertion " +
                        parentAssertion + " in parent node list:" + getParentNames(child));
                }
            }
        }

        List<Service> myChildren = childServices.get(child);
        if (myChildren == null) {
            return;
        }
        
        List<WebAssertion> assertions = new ArrayList<>(parentAssertions);
        assertions.addAll(child.getAssertions());
        for (Service myChild : myChildren) {
            validateAssertions(myChild, assertions);
        }
        
    }


    private String getParentNames(Service service) {
        String parents = "";
        Service s = service;
        while ((s = s.getParent()) != null) {
            parents += " " + s.getName();
        }
        return parents;
    }
    
    private boolean isViewUnauthenticated(HttpServletRequest request) {
        return viewUnauthenticatedParameter != null && 
                request.getParameter(viewUnauthenticatedParameter) != null;
    }
    
    public void setViewUnauthenticatedParameter(String viewUnauthenticatedParameter) {
        this.viewUnauthenticatedParameter = viewUnauthenticatedParameter;
    }
}
