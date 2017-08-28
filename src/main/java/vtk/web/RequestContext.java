/* Copyright (c) 2004,2013, University of Oslo, Norway
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.function.Supplier;

import javax.servlet.http.HttpServletRequest;

import org.springframework.web.servlet.support.RequestContextUtils;

import vtk.repository.Acl;
import vtk.repository.AuthorizationException;
import vtk.repository.Comment;
import vtk.repository.ContentInputSource;
import vtk.repository.FailedDependencyException;
import vtk.repository.IllegalOperationException;
import vtk.repository.Lock;
import vtk.repository.Path;
import vtk.repository.ReadOnlyException;
import vtk.repository.RecoverableResource;
import vtk.repository.Repository;
import vtk.repository.RepositoryException;
import vtk.repository.Resource;
import vtk.repository.ResourceLockedException;
import vtk.repository.ResourceNotFoundException;
import vtk.repository.ResourceOverwriteException;
import vtk.repository.Revision;
import vtk.repository.Revision.Type;
import vtk.repository.StoreContext;
import vtk.repository.store.PrincipalMetadata;
import vtk.repository.store.PrincipalMetadataDAO;
import vtk.security.AuthenticationException;
import vtk.security.Principal;
import vtk.security.SecurityContext;
import vtk.util.repository.RepositoryTraversal;
import vtk.util.repository.RepositoryWrapper;
import vtk.web.service.Service;
import vtk.web.service.ServiceResolver;
import vtk.web.service.URL;

/**
 * Request context. Lives throughout one request, and contains the servlet
 * request, the current {@link Service} and the requested resource URI. The
 * request context can be obtained from application code in the following way:
 * 
 * <pre>
 * RequestContext requestContext = RequestContext.getRequestContext(request);
 * </pre>
 * 
 */
public class RequestContext {
    private static final String REQUEST_ATTRIBUTE = 
            RequestContext.class.getName() + ".requestAttribute";
    
    public static final String PREVIEW_UNPUBLISHED_PARAM_NAME = "vrtxPreviewUnpublished";
    public static final String PREVIEW_UNPUBLISHED_PARAM_VALUE = "true";
    private static final String HTTP_REFERER = "Referer";

    private final SecurityContext securityContext;
    private PrincipalMetadata cachedPrincipalMetadata = null;
    private final PrincipalMetadataDAO principalLookup;
    private final boolean inRepository;
    private final Repository repository;
    private final Service service;
    private final ServiceResolver serviceResolver;
    private final Path resourceURI;
    private final Acl resourceAcl;
    private final URL requestURL;
    private final Path currentCollection;
    private final Path indexFileURI;
    private final Supplier<Locale> locale;
    private final boolean isIndexFile;
    private final boolean viewUnauthenticated;
    private final boolean previewUnpublished;
    private final String revisionParameter;
    private List<Message> infoMessages = new ArrayList<>(0);
    private List<Message> errorMessages = new ArrayList<>(0);
    
    // Set on first invocation (otherwise JUnit tests fail):
    private RevisionWrapper revisionWrapper = null;

    

    /**
     * Creates a new request context.
     * 
     * @param servletRequest
     *            the current servlet request
     * @param service
     *            the resolved service
     * @param resource
     *            the current resource (may be null)
     * @param uri
     *            the URI of the current resource
     * @param indexFileURI
     *            the URI of the current index file
     * @param viewUnauthenticated
     *            (<code>null</code> if no index file exists)
     */
    public RequestContext(HttpServletRequest servletRequest, SecurityContext securityContext, Service service,
            ServiceResolver serviceResolver, Resource resource, Path uri, Path indexFileURI, 
            boolean isIndexFile, boolean viewUnauthenticated,
            boolean inRepository, Repository repository, PrincipalMetadataDAO principalLookup) {
        this.securityContext = securityContext;
        this.indexFileURI = indexFileURI;
        this.service = service;
        this.locale = () -> RequestContextUtils.getLocale(servletRequest);
        this.serviceResolver = serviceResolver;
        this.isIndexFile = isIndexFile;
        this.viewUnauthenticated = viewUnauthenticated;
        this.repository = repository;
        this.inRepository = inRepository;
        this.requestURL = URL.create(servletRequest).setImmutable();
        if (resource != null) {
            this.resourceURI = resource.getURI();
            this.resourceAcl = resource.getAcl();
            if (resource.isCollection()) {
                this.currentCollection = resource.getURI();
            }
            else {
                this.currentCollection = resource.getURI().getParent();
            }
        }
        else {
            this.resourceURI = uri;
            this.resourceAcl = null;
            this.currentCollection = null;
        }
        if (principalLookup == null) {
            throw new NullPointerException("principalLookup");
        }        
        this.principalLookup = principalLookup;
        
        boolean previewUnpublished = false;
        if (servletRequest != null) {
            previewUnpublished = servletRequest.getParameter(PREVIEW_UNPUBLISHED_PARAM_NAME) != null;
            if (!previewUnpublished) {
                String referer = servletRequest.getHeader(HTTP_REFERER);
                if (referer != null) {
                    try {
                        URL refererUrl = URL.parse(referer);
                        previewUnpublished = refererUrl.getParameter(PREVIEW_UNPUBLISHED_PARAM_NAME) != null;
                    }
                    catch (Exception e) {
                        previewUnpublished = false;
                    }
                }
            }
        }
        this.previewUnpublished = previewUnpublished;
        
        this.revisionParameter = (servletRequest != null && servletRequest.getParameter("revision") != null 
                && previewUnpublished) ? servletRequest.getParameter("revision") : null;
    }
    
    private static class RequestContextHolder {
        public Optional<RequestContext> ctx = Optional.empty();
    }
    
    static void push(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        Stack<RequestContextHolder> stack = (Stack<RequestContextHolder>) 
                request.getAttribute(REQUEST_ATTRIBUTE);
        if (stack == null) {
            stack = new Stack<>();
            request.setAttribute(REQUEST_ATTRIBUTE, stack);
        }
        stack.push(new RequestContextHolder());
    }
    
    static void pop(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        Stack<RequestContextHolder> stack = (Stack<RequestContextHolder>) 
                request.getAttribute(REQUEST_ATTRIBUTE);
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.pop();
    }

    public static void setRequestContext(RequestContext requestContext, HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        Stack<RequestContextHolder> stack = 
                (Stack<RequestContextHolder>) request.getAttribute(REQUEST_ATTRIBUTE);
        if (stack == null) {
            stack = new Stack<>();
            request.setAttribute(REQUEST_ATTRIBUTE, stack);
        }
        if (stack.isEmpty()) {
            stack.push(new RequestContextHolder());
        }
        RequestContextHolder holder = stack.peek();
        holder.ctx = Optional.ofNullable(requestContext);
    }

    public static boolean exists(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        Stack<RequestContextHolder> stack = 
                (Stack<RequestContextHolder>) request.getAttribute(REQUEST_ATTRIBUTE);
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        RequestContextHolder holder = stack.peek();
        return holder.ctx.isPresent();
     }

    /**
     * Gets the current request context.
     * 
     */
    public static RequestContext getRequestContext(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        Stack<RequestContextHolder> stack = 
                (Stack<RequestContextHolder>) request.getAttribute(REQUEST_ATTRIBUTE);
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        RequestContextHolder holder = stack.peek();
        return holder.ctx.orElse(null);
    }

    /**
     * Gets the request URL
     */
    public URL getRequestURL() {
        return requestURL;
    }

    /**
     * Gets the current {@link Service} that this request executes under.
     * 
     * @return the service, or <code>null</code> if there is no current service.
     */
    public Service getService() {
        return this.service;
    }
    
    /**
     * Looks up a service by name
     */
    public Optional<Service> service(String name) {
        return serviceResolver.service(name);
    }


    /**
     * Gets the {@link vtk.repository.Resource#getURI URI} that the
     * current request maps to.
     *
     * @return the URI of the requested resource.
     */
    public Path getResourceURI() {
        return this.resourceURI;
    }

    /**
     * Gets the {@link Acl ACL} of the current resource.
     *
     * @return the ACL, or <code>null</code> if there is no current resource
     */
    public Acl getResourceAcl() {
        return this.resourceAcl;
    }

    /**
     * Gets the URI of the current collection. If the request is for a
     * collection, the current collection and {@link #getResourceURI resource
     * URI} are the same, otherwise the current collection is the nearest
     * collection towards the root.
     */
    public Path getCurrentCollection() {
        return this.currentCollection;
    }

    /**
     * Gets the index file URI.
     * 
     * @return the index file URI, or <code>null</code> if this is not an index
     *         file request.
     */
    public Path getIndexFileURI() {
        return this.indexFileURI;
    }

    public void addInfoMessage(Message msg) {
        if (msg == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        this.infoMessages.add(msg);
    }

    public List<Message> getInfoMessages() {
        return Collections.unmodifiableList(this.infoMessages);
    }

    public void addErrorMessage(Message msg) {
        if (msg == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        this.errorMessages.add(msg);
    }

    public List<Message> getErrorMessages() {
        return Collections.unmodifiableList(this.errorMessages);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getName());
        sb.append(": [");
        sb.append("resourceURI = ").append(this.resourceURI);
        sb.append(", service = ").append(this.service.getName());
        sb.append("]");
        return sb.toString();
    }

    public boolean isIndexFile() {
        return isIndexFile;
    }

    /**
     * This flag will be set to <code>true</code> if request should be processed
     * for viewing as unauthenticated principal.
     */
    public boolean isViewUnauthenticated() {
        return this.viewUnauthenticated;
    }

    public boolean isInRepository() {
        return this.inRepository;
    }

    public Repository getRepository() {
        if (revisionWrapper != null) return revisionWrapper;
        if (revisionParameter != null) {
            this.revisionWrapper = new RevisionWrapper(
                    this.repository, resourceURI, revisionParameter);
            return this.revisionWrapper;
        }
        return this.repository;
    }

    public String getSecurityToken() {
        return this.securityContext.getToken();
    }

    public Principal getPrincipal() {
        return this.securityContext.getPrincipal();
    }

    public PrincipalMetadata principalMetadata(Locale locale) {
        Principal principal = getPrincipal();
        if (principal == null) return null;

        if (cachedPrincipalMetadata != null) {
            return cachedPrincipalMetadata;
        }

        cachedPrincipalMetadata = principalLookup.getMetadata(principal, locale);
        return cachedPrincipalMetadata;
    }

    /**
     * Gets the request locale, as resolved by the {@link LocaleResolver}
     */
    public Locale getLocale() {
        return locale.get();
    }

    public RepositoryTraversal rootTraversal(String token, Path uri) {
        return new RepositoryTraversal(this.repository, token, uri);
    }

    public static RepositoryTraversal rootTraversal(Repository repository, String token, Path uri) {
        return new RepositoryTraversal(repository, token, uri);
    }

    public boolean isPreviewUnpublished() {
        return previewUnpublished;
    }
    
    private static class RevisionWrapper extends RepositoryWrapper {
        private Path uri;
        private String revision;
        private Map<String, Revision> cache = new HashMap<>();
        
        public RevisionWrapper(Repository repository, Path uri, String revision) {
            super(repository);
            this.uri = uri;
            this.revision = revision;
        }
        
        @Override
        public Resource retrieve(String token, Path uri, boolean forProcessing)
                throws ResourceNotFoundException, AuthorizationException,
                AuthenticationException, IOException {
            if (this.uri.equals(uri)) {
                Revision rev = findRevision(token);
                if (rev != null) {
                    return retrieve(token, uri, forProcessing, rev);
                }
            }
            return super.retrieve(token, uri, forProcessing);
        }

        @Override
        public InputStream getInputStream(String token, Path uri,
                boolean forProcessing) throws ResourceNotFoundException,
                AuthorizationException, AuthenticationException, IOException {
            
            if (this.uri.equals(uri)) {
                Revision rev = findRevision(token);
                if (rev != null) {
                    return getInputStream(token, uri, forProcessing, rev);
                }
            }
            return super.getInputStream(token, uri, forProcessing);
        }
        
        @Override
        public Resource store(String token, String lockToken, Resource resource)
                throws ResourceNotFoundException, AuthorizationException,
                AuthenticationException, ResourceLockedException,
                IllegalOperationException, ReadOnlyException, IOException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public Resource store(String token, String lockToken, Resource resource,
                StoreContext storeContext) throws ResourceNotFoundException,
                AuthorizationException, AuthenticationException,
                ResourceLockedException, IllegalOperationException,
                ReadOnlyException, IOException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public Resource storeContent(String token, String lockToken, Path uri, ContentInputSource content)
                throws AuthorizationException, AuthenticationException,
                ResourceNotFoundException, ResourceLockedException,
                IllegalOperationException, ReadOnlyException, IOException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public Resource storeContent(String token, String lockToken, Path uri,
                ContentInputSource content, Revision revision)
                throws AuthorizationException, AuthenticationException,
                ResourceNotFoundException, ResourceLockedException,
                IllegalOperationException, ReadOnlyException, IOException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public Resource createDocument(String token, String lockToken, Path uri,
                ContentInputSource content) throws IllegalOperationException,
                AuthorizationException, AuthenticationException,
                ResourceLockedException, ReadOnlyException, IOException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public Resource createCollection(String token, String lockToken, Path uri)
                throws AuthorizationException, AuthenticationException,
                IllegalOperationException, ResourceLockedException,
                ReadOnlyException, IOException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public void copy(String token, String lockToken, Path srcUri, Path destUri,
                boolean overwrite, boolean preserveACL)
                throws IllegalOperationException, AuthorizationException,
                AuthenticationException, FailedDependencyException,
                ResourceOverwriteException, ResourceLockedException,
                ResourceNotFoundException, ReadOnlyException, IOException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public void move(String token, String lockToken, Path srcUri, Path destUri,
                boolean overwrite) throws IllegalOperationException,
                AuthorizationException, AuthenticationException,
                FailedDependencyException, ResourceOverwriteException,
                ResourceLockedException, ResourceNotFoundException,
                ReadOnlyException, IOException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public void delete(String token, String lockToken, Path uri, boolean restoreable)
                throws IllegalOperationException, AuthorizationException,
                AuthenticationException, ResourceNotFoundException,
                ResourceLockedException, FailedDependencyException,
                ReadOnlyException, IOException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public void recover(String token, String lockToken, Path parentUri,
                RecoverableResource recoverableResource)
                throws ResourceNotFoundException, AuthorizationException,
                AuthenticationException, IOException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public void deleteRecoverable(String token, String lockToken, Path parentUri,
                RecoverableResource recoverableResource) throws IOException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public Resource lock(String token, Path uri, String ownerInfo,
                Depth depth, int requestedTimoutSeconds, String lockToken, Lock.Type lockType)
                throws ResourceNotFoundException, AuthorizationException,
                AuthenticationException, FailedDependencyException,
                ResourceLockedException, IllegalOperationException,
                ReadOnlyException, IOException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public void unlock(String token, Path uri, String lockToken)
                throws ResourceNotFoundException, AuthorizationException,
                AuthenticationException, ResourceLockedException,
                ReadOnlyException, IOException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public Resource storeACL(String token, String lockToken, Path uri, Acl acl)
                throws ResourceNotFoundException, AuthorizationException,
                AuthenticationException, IllegalOperationException,
                ReadOnlyException, IOException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public Resource storeACL(String token, String lockToken, Path uri, Acl acl,
                boolean validateAcl) throws ResourceNotFoundException,
                AuthorizationException, AuthenticationException,
                IllegalOperationException, ReadOnlyException, IOException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public Resource deleteACL(String token, String lockToken, Path uri)
                throws ResourceNotFoundException, AuthorizationException,
                AuthenticationException, IllegalOperationException,
                ReadOnlyException, IOException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public Revision createRevision(String token, String lockToken, Path uri, Type type)
                throws AuthorizationException, ResourceNotFoundException,
                AuthenticationException, IOException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public void deleteRevision(String token, String lockToken, Path uri, Revision revision)
                throws ResourceNotFoundException, AuthorizationException,
                AuthenticationException, IOException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public Comment addComment(String token, String lockToken, Resource resource,
                String title, String text) throws RepositoryException,
                AuthenticationException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public Comment addComment(String token, String lockToken, Comment comment)
                throws AuthenticationException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public void deleteComment(String token, String lockToken, Resource resource,
                Comment comment) throws RepositoryException,
                AuthenticationException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public void deleteAllComments(String token, String lockToken, Resource resource)
                throws RepositoryException, AuthenticationException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public Comment updateComment(String token, String lockToken, Resource resource,
                Comment comment) throws RepositoryException,
                AuthenticationException {
            throw new UnsupportedOperationException("Not supported");
        }

        private Revision findRevision(String token) throws IOException {
            if (cache.containsKey(token)) return cache.get(token);

            for (Revision r: getRevisions(token, uri)) {
                if (r.getName().equals(this.revision)) {
                    cache.put(token, r);
                    return r;
                }
            }
            cache.put(token,  null);
            return null;
        }
    }

}
