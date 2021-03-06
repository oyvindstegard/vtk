/* Copyright (c) 2004, 2005, 2006, 2007, University of Oslo, Norway
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
package vtk.repository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import vtk.cluster.ClusterAware;
import vtk.cluster.ClusterRole;
import vtk.repository.Revision.Type;
import vtk.repository.content.ContentImpl;
import vtk.repository.content.ContentRepresentationRegistry;
import vtk.repository.event.ACLModificationEvent;
import vtk.repository.event.ContentModificationEvent;
import vtk.repository.event.InheritablePropertiesModificationEvent;
import vtk.repository.event.RepositoryInitEvent;
import vtk.repository.event.ResourceCreationEvent;
import vtk.repository.event.ResourceDeletionEvent;
import vtk.repository.event.ResourceModificationEvent;
import vtk.repository.event.ResourceMovedEvent;
import vtk.repository.hooks.TypeHandlerHookException;
import vtk.repository.hooks.TypeHandlerHooks;
import vtk.repository.hooks.TypeHandlerHooksRegistry;
import vtk.repository.hooks.UnsupportedContentException;
import vtk.repository.resourcetype.Content;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.search.QueryException;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.Searcher;
import vtk.repository.store.CommentDAO;
import vtk.repository.store.ContentStore;
import vtk.repository.store.DataAccessException;
import vtk.repository.store.DataAccessor;
import vtk.repository.store.RevisionStore;
import vtk.repository.store.Revisions;
import vtk.security.AuthenticationException;
import vtk.security.InvalidPrincipalException;
import vtk.security.Principal;
import vtk.security.token.TokenManager;
import vtk.util.codec.Digest;
import vtk.util.io.IO;
import vtk.util.repository.MimeHelper;

/**
 * A semi-transactional implementation of the
 * <code>vtk.repository.Repository</code> interface. (A
 * transaction-aware content-store implementation is needed to make repository
 * fully transactional.)
 * <p>
 * Any operation that modifies the content store cannot be rolled back, so most
 * checks/verifications and DAO operations should be done before any
 * content-store operation.
 * <p>
 * XXX: Evaluate exception practice, handling and propagation 
 * XXX: make content store participate in transactions 
 * XXX: externalize caching 
 * XXX: duplication of owner and inherited between resource and acl.
 * XXX: too big.
 */
public class RepositoryImpl implements Repository, ApplicationContextAware, 
    ApplicationListener<ContextRefreshedEvent>, ClusterAware {

    private ApplicationContext context;
    private DataAccessor dao;
    private CommentDAO commentDAO;
    private RevisionStore revisionStore;
    private ContentStore contentStore;
    private ContentRepresentationRegistry contentRepresentationRegistry;
    private TokenManager tokenManager;
    private ResourceTypeTree resourceTypeTree;
    private RepositoryResourceHelper resourceHelper;
    private AuthorizationManager authorizationManager;
    private Searcher searcher;
    private String id;
    private int maxComments = 1000;
    private int maxResourceChildren = 3000;
    private File tempDir;

    // Registered type handler hook extensions
    private TypeHandlerHooksRegistry typeHandlerHooksRegistry;

    // Default value of 60 days before recoverable resources are purged from
    // trash can
    private int permanentDeleteOverdueLimitInDays = 60;

    // TODO switch to java.time.Duration:
    public final static int LOCK_DEFAULT_TIMEOUT_SECONDS = 30 * 60; // 30 minutes
    public final static int LOCK_MAX_TIMEOUT_SECONDS = LOCK_DEFAULT_TIMEOUT_SECONDS * 2; // 60 minutes

    // TODO switch to java.time.Duration:
    private int lockDefaultTimeoutSeconds = LOCK_DEFAULT_TIMEOUT_SECONDS;
    private int lockMaxTimeoutSeconds = LOCK_DEFAULT_TIMEOUT_SECONDS;

    private static final int FILE_COPY_BUF_SIZE = 122880;
    
    static final int TOKEN_REFRESH_PROGRESS_INTERVAL = 128*1024*1024;

    private final Logger searchLogger = LoggerFactory.getLogger(RepositoryImpl.class.getName() + ".Search");
    private final Logger trashLogger = LoggerFactory.getLogger(RepositoryImpl.class.getName() + ".Trash");
    
    @Override
    public boolean isReadOnly() {
        return this.authorizationManager.isReadOnly();
    }
    
    @Override
    public boolean isReadOnly(Path path, boolean forDelete) {
        return this.authorizationManager.isReadOnly(path, forDelete);
    }
    
    @Override
    public List<Path> getReadOnlyRoots() {
        return this.authorizationManager.getReadOnlyRoots();
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Transactional(readOnly=true)
    @OpLog
    @Override
    public boolean exists(@OpLogParam(name = "token") String token, @OpLogParam Path uri) throws IOException {

        return this.dao.load(uri) != null;

    }

    @Transactional(readOnly=true)
    @OpLog
    @Override
    public Resource retrieveById(@OpLogParam(name = "token") String token,
            @OpLogParam(name = "id") ResourceId id, boolean forProcessing)
            throws RepositoryException, AuthenticationException, AuthorizationException, IOException {

        final Path uri = dao.getResourcePath(id.numericId());
        if (uri == null) {
            throw new ResourceNotFoundException(id);
        }
        
        return retrieve(token, uri, forProcessing);
    }

    @Transactional(readOnly=true)
    @OpLog
    @Override
    public Resource retrieve(@OpLogParam(name = "token") String token, 
                             @OpLogParam Path uri, boolean forProcessing) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IOException {
        if (uri == null) {
            throw new IllegalArgumentException("URI is NULL");
        }

        Principal principal = getPrincipal(token);

        ResourceImpl resource = this.dao.load(uri);

        if (resource == null) {
            throw new ResourceNotFoundException(uri);
        }

        if (forProcessing) {
            this.authorizationManager.authorizeReadProcessed(uri, principal);
        } else {
            this.authorizationManager.authorizeRead(uri, principal);
        }
        
        try {
            ResourceImpl toReturn = (ResourceImpl)resource.clone();
            TypeHandlerHooks hooks = typeHandlerHooksRegistry.getTypeHandlerHooks(toReturn);
            if (hooks != null) {
                try {
                    return hooks.onRetrieve(toReturn);
                } catch (Exception e){
                    throw new TypeHandlerHookException("Failed in onRetrieve hook: " + e.getMessage(), e);
                }
            }
            
            return toReturn;
        } catch (CloneNotSupportedException e) {
            throw new IOException("Failed to clone object", e);
        }
    }

    @Transactional(readOnly=true)
    @OpLog
    @Override
    public Resource retrieve(@OpLogParam(name = "token") String token,
            @OpLogParam Path uri, boolean forProcessing, @OpLogParam Revision revision)
            throws ResourceNotFoundException, AuthorizationException, AuthenticationException, IOException {

        if (uri == null) {
            throw new IllegalArgumentException("URI is NULL");
        }
        if (revision == null) {
            throw new IllegalArgumentException("Revision is NULL");
        }
        Principal principal = getPrincipal(token);

        ResourceImpl resource = this.dao.load(uri);

        if (resource == null) {
            throw new ResourceNotFoundException(uri);
        }

        if (forProcessing) {
            this.authorizationManager.authorizeReadProcessed(uri, principal);
        } else {
            this.authorizationManager.authorizeRead(uri, principal);
        }

        List<Revision> revisions = this.revisionStore.list(resource);
        boolean found = false;
        for (Revision r : revisions) {
            if (r.getID() == revision.getID()) {
                found = true;
                revision = r;
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("No such revision: " + revision.getID());
        }

        // Permissions on WORKING_COPY are the same as on the 
        // resource itself. Otherwise, authorize for the given revision:
        if (revision.getType() != Revision.Type.WORKING_COPY) {
            if (!this.authorizationManager.authorize(principal, revision.getAcl(), Privilege.READ)) {
                throw new AuthorizationException(
                        "Principal " + principal + " not authorized by revision ACL for privilege READ");
            }
        }

        // Evaluate revision content (as content-modification)
        Content content = getContent(resource, revision);
        
        ResourceImpl result = this.resourceHelper.loadRevision(resource, principal, content, revision);

        // Add inherited properties (not loaded by revision store):
        for (Property p: resource) {
            if (p.isInherited()) {
                if (result.getProperty(p.getDefinition().getNamespace(), 
                        p.getDefinition().getName()) == null) {
                    result.addProperty(p);
                }
            }
        }
        result.setAcl(resource.getAcl());
        return result;
    }

    @Override
    public TypeInfo getTypeInfo(Resource resource) {
        return new TypeInfo(this.resourceTypeTree, resource.getResourceType());
    }
    
    @Override
    public TypeInfo getTypeInfo(String name) {
        return new TypeInfo(this.resourceTypeTree, name);
    }

    @Transactional(readOnly=true)
    @OpLog
    @Override
    public InputStream getInputStream(@OpLogParam(name = "token") String token, @OpLogParam Path uri, boolean forProcessing) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, ResourceLockedException, IOException {

        Principal principal = getPrincipal(token);
        ResourceImpl r = this.dao.load(uri);

        if (r == null) {
            throw new ResourceNotFoundException(uri);
        } else if (r.isCollection()) {
            throw new IllegalOperationException("Resource is collection");
        }

        if (forProcessing) {
            this.authorizationManager.authorizeReadProcessed(uri, principal);
        } else {
            this.authorizationManager.authorizeRead(uri, principal);
        }
        
        TypeHandlerHooks hooks = typeHandlerHooksRegistry.getTypeHandlerHooks(r);
        if (hooks != null) {
            try {
                return hooks.getInputStream(r);
            } catch (Exception e) {
                throw new TypeHandlerHookException("failed in onGetInputStream hook: " + e.getMessage(), e);
            }
        }
        
        return this.contentStore.getInputStream(uri);
    }
    

    @Transactional(readOnly=true)
    @OpLog
    @Override
    public InputStream getInputStream(@OpLogParam(name = "token") String token, @OpLogParam Path uri, 
            boolean forProcessing, @OpLogParam Revision revision)
            throws ResourceNotFoundException, AuthorizationException, AuthenticationException, ResourceLockedException,
            IOException {

        Principal principal = getPrincipal(token);
        ResourceImpl r = this.dao.load(uri);

        if (r == null) {
            throw new ResourceNotFoundException(uri);
        } else if (r.isCollection()) {
            throw new IllegalOperationException("Resource is collection");
        }

        if (forProcessing) {
            this.authorizationManager.authorizeReadProcessed(uri, principal);
        } else {
            this.authorizationManager.authorizeRead(uri, principal);
        }

        List<Revision> revisions = this.revisionStore.list(r);
        boolean found = false;
        for (Revision rev : revisions) {
            if (rev.getID() == revision.getID()) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalOperationException("No such revision: " + revision);
        }
        if (revision.getType() == Type.WORKING_COPY) {
            this.authorizationManager.authorizeRead(uri, principal);
        } else {
            if (!this.authorizationManager.authorize(principal, revision.getAcl(), Privilege.READ)) {
                throw new AuthorizationException("Principal " + principal + " not authorized by revision ACL for privilege READ");
            }
        }
        return this.revisionStore.getContent(r, revision);
    }

    @Transactional(readOnly=true)
    @OpLog
    @Override
    public InputStream getAlternativeInputStream(@OpLogParam(name = "token") String token, @OpLogParam Path uri, boolean forProcessing,
            @OpLogParam(name = "contentId") String contentIdentifier) 
            throws NoSuchContentException, ResourceNotFoundException, AuthorizationException, AuthenticationException, IOException {

        if (contentIdentifier == null) {
            throw new IllegalArgumentException("Content identifier null");
        }
        
        Principal principal = getPrincipal(token);
        ResourceImpl r = this.dao.load(uri);

        if (r == null) {
            throw new ResourceNotFoundException(uri);
        } else if (r.isCollection()) {
            throw new IllegalOperationException("Resource is collection");
        }

        if (forProcessing) {
            this.authorizationManager.authorizeReadProcessed(uri, principal);
        } else {
            this.authorizationManager.authorizeRead(uri, principal);
        }

        TypeHandlerHooks hooks = typeHandlerHooksRegistry.getTypeHandlerHooks(r);
        if (hooks != null) {
            try {
                return hooks.onGetAlternativeInputStream(r, contentIdentifier);
            } catch (Exception e) {
                throw new TypeHandlerHookException("failed in onGetAlternativeContentStream hook: " + e.getMessage(), e);
            }
        }

        // Since alternative content streams can only be available through
        // extensions, we fail if no hooks match the type and can provide it.
        throw new NoSuchContentException("No content with identifier " + contentIdentifier + " available for resource at " + uri);
    }
    
    @Transactional(readOnly=true)
    @OpLog
    @Override
    public Resource[] listChildren(@OpLogParam(name = "token") String token, @OpLogParam Path uri, boolean forProcessing) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IOException {

        Principal principal = getPrincipal(token);
        ResourceImpl collection = this.dao.load(uri);

        if (collection == null) {
            throw new ResourceNotFoundException(uri);
        } else if (!collection.isCollection()) {
            throw new IllegalOperationException("Can't list children for non-collection resources");
        }

        if (forProcessing) {
            this.authorizationManager.authorizeReadProcessed(uri, principal);
        } else {
            this.authorizationManager.authorizeRead(uri, principal);
        }
        ResourceImpl[] list = this.dao.loadChildren(collection);
        Resource[] children = new Resource[list.length];

        for (int i = 0; i < list.length; i++) {
            try {
                children[i] = (Resource) list[i].clone();

            } catch (CloneNotSupportedException e) {
                throw new IOException("Failed to clone object", e);
            }
        }
        
        TypeHandlerHooks hooks = typeHandlerHooksRegistry.getTypeHandlerHooks(collection);
        if (hooks != null) {
            try {
                return hooks.onListChildren((ResourceImpl)collection.clone(), list);
            } catch (Exception e){
                throw new TypeHandlerHookException("failed in onListChildren hook: " + e.getMessage(), e);
            }
        }

        return children;
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public Resource createCollection(@OpLogParam(name="token") String token, String lockToken, 
            @OpLogParam Path uri, AclMode aclMode) 
            throws IllegalOperationException, AuthorizationException,
            AuthenticationException, ResourceLockedException, ReadOnlyException, IOException {
        Objects.requireNonNull(uri, "Argument 'uri' is NULL");
        Objects.requireNonNull(aclMode, "Argument 'aclMode' is NULL");
        Principal principal = getPrincipal(token);
        ResourceImpl resource = this.dao.load(uri);
        if (resource != null) {
            throw new ResourceOverwriteException(uri);
        }
        ResourceImpl parent = this.dao.load(uri.getParent());
        if ((parent == null) || !parent.isCollection()) {
            throw new IllegalOperationException("Either parent doesn't exist or parent is document");
        }

        checkLock(parent, principal, lockToken);
        
        if (!aclMode.inherited()) {
            validateNewAcl(aclMode.acl().get());
            this.authorizationManager.authorizeCreateWithAcl(parent.getURI(), principal);
        }
        else {
            // Allow if principal has at least CREATE_UNPUBLISHED on parent
            this.authorizationManager.authorizeCreateUnpublished(parent.getURI(), principal);
        }
        
        checkMaxChildren(parent);

        try {
            // Content modification on parent for bind
            final ResourceImpl originalParent = (ResourceImpl) parent.clone();
            parent.addChildURI(uri);

            Content content = getContent(parent);
            parent = this.resourceHelper.contentModification(parent, principal, content);
            parent = this.dao.store(parent);
            this.context.publishEvent(new ContentModificationEvent(this, principal, (Resource) parent.clone(), originalParent));

            // Create on new collection resource
            ResourceImpl newResource = new ResourceImpl(uri);
            newResource.setChildURIs(new ArrayList<>());
            content = getContent(newResource);
                        
            TypeHandlerHooks hooks = typeHandlerHooksRegistry.getTypeHandlerHooksForCreateCollection();
            if (hooks != null) {
                try {
                    hooks.onCreateCollection(newResource);
                }
                catch (Exception e) {
                    throw new TypeHandlerHookException("failed in onCreateCollection hook: " + e.getMessage(), e);
                }
            }
            
            // Set initial (inherited) permissions on new resource:
            newResource.setAcl(parent.getAcl());
            newResource.setInheritedAcl(true);
            int aclIneritedFrom = parent.isInheritedAcl() ? parent.getAclInheritedFrom() : parent.getNumericId();
            newResource.setAclInheritedFrom(aclIneritedFrom);

            newResource = this.resourceHelper.create(principal, newResource, true, content);
            
            // Store new collection resource
            newResource = this.dao.store(newResource);
            
            // Set permissions on new collection if passed by client:
            if (!aclMode.inherited()) {
                Acl acl = aclMode.acl().get();
                validateNewAcl(acl);
                newResource.setAcl(acl);
                newResource.setInheritedAcl(false);
                newResource.setAclInheritedFrom(PropertySetImpl.NULL_RESOURCE_ID);
                newResource = this.dao.storeACL(newResource);
            }
            
            this.contentStore.createResource(newResource.getURI(), true);

            this.context.publishEvent(new ResourceCreationEvent(this, principal, (Resource) newResource.clone()));
            return (Resource) newResource.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new IOException("Failed to clone object", e);
        }
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public void copy(@OpLogParam(name = "token") String token, String lockToken, @OpLogParam Path srcUri, 
            @OpLogParam Path destUri, boolean overwrite, boolean copyAcls)
            throws IllegalOperationException, AuthorizationException, AuthenticationException,
            FailedDependencyException, ResourceOverwriteException, ResourceLockedException, ResourceNotFoundException,
            ReadOnlyException, IOException {
        
        Objects.requireNonNull(srcUri);
        Objects.requireNonNull(destUri);
        
        Principal principal = getPrincipal(token);
        validateCopyURIs(srcUri, destUri);

        ResourceImpl src = this.dao.load(srcUri);
        if (src == null) {
            throw new ResourceNotFoundException(srcUri);
        }

        ResourceImpl dest = this.dao.load(destUri);

        if (dest == null) {
            overwrite = false;
        } else if (!overwrite) {
            throw new ResourceOverwriteException(destUri);
        }

        Path destParentUri = destUri.getParent();
        ResourceImpl destParent = this.dao.load(destParentUri);
        if ((destParent == null) || !destParent.isCollection()) {
            throw new IllegalOperationException("destination is either a document or does not exist");
        }

        if (dest != null) {
            checkLock(dest, principal, lockToken);
        }
        checkLock(destParent, principal, lockToken);
        this.authorizationManager.authorizeCopy(srcUri, destUri, principal, overwrite);

        checkMaxChildren(destParent);

        if (dest != null) {
            this.dao.delete(dest);
            this.contentStore.deleteResource(dest.getURI());
            this.context.publishEvent(new ResourceDeletionEvent(this, principal, dest));
        }

        try {
            PropertySetImpl fixedProps = this.resourceHelper.getFixedCopyProperties(src, principal, destUri);
            ResourceImpl newResource = src.createCopy(destUri, copyAcls ? src.getAcl() : destParent.getAcl());
            Content content = getContent(src);
            
            TypeHandlerHooks hooks = typeHandlerHooksRegistry.getTypeHandlerHooks(src);
            if (hooks != null) {
                try {
                    hooks.onCopy(src, dest);
                }
                catch (Exception e) {
                    throw new TypeHandlerHookException("failed in onCopy hook: " + e.getMessage(), e);
                }
            }
            
            // XXX: why nameChange() on copy?
            newResource = this.resourceHelper.nameChange(src, newResource, principal, content);

            final ResourceImpl destParentOriginal = (ResourceImpl) destParent.clone();
            destParent.addChildURI(destUri);
            content = getContent(destParent);
            destParent = this.resourceHelper.contentModification(destParent, principal, content);

            // Both new resource and destParent are stored in DAO copy call
            // Probably better to not touch destparent in DAO copy code and
            // explicitly store it here instead (for better clarity).

            // TODO recursive unpublish in case of only CREATE_UNPUBLISHED permissions at destination.
            // A concept of forced properties must be introduced in DAO.copy(), in addition to
            // the already existing "uncopyableProperties" argument. Forced properties must then be applied
            // recursively by DAO.copy() (published=false), in addition to deletion of publish-date.
            // For now, do a simplified unpublish of non-collection resources:
            Set<String> uncopyableProperties = new HashSet<>(PropertyType.UNCOPYABLE_PROPERTIES);
            if (!src.isCollection() 
                    && src.hasPublishDate()
                    && !this.authorizationManager.authorize(principal, destParent.getAcl(), Privilege.READ_WRITE)) {
                uncopyableProperties.add(PropertyType.PUBLISH_DATE_PROP_NAME);
                Property publishedProp = (Property) src.getProperty(Namespace.DEFAULT_NAMESPACE,
                                                    PropertyType.PUBLISHED_PROP_NAME).clone();
                publishedProp.setBooleanValue(false);
                fixedProps.addProperty(publishedProp);
            }
            newResource = this.dao.copy(src, destParent, newResource, copyAcls, fixedProps, uncopyableProperties);
            this.contentStore.copy(src.getURI(), newResource.getURI());

            this.context.publishEvent(new ResourceCreationEvent(this, principal, (Resource) newResource.clone()));
            this.context.publishEvent(new ContentModificationEvent(this, principal, destParent, destParentOriginal));
        }
        catch (CloneNotSupportedException e) {
            throw new IOException("Failed to clone object", e);
        }
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public void move(@OpLogParam(name = "token") String token, String lockToken,
            @OpLogParam Path srcUri, @OpLogParam Path destUri, boolean overwrite) throws IllegalOperationException,
            AuthorizationException, AuthenticationException, FailedDependencyException, ResourceOverwriteException,
            ResourceLockedException, ResourceNotFoundException, ReadOnlyException, IOException {

        Principal principal = getPrincipal(token);
        validateCopyURIs(srcUri, destUri);

        // Loading and checking source resource
        final ResourceImpl src = this.dao.load(srcUri);
        ResourceImpl srcParent = this.dao.load(srcUri.getParent());

        if (src == null) {
            throw new ResourceNotFoundException(srcUri);
        }

        // Checking dest
        final ResourceImpl dest = this.dao.load(destUri);
        if (dest == null) {
            overwrite = false;
        } else if (!overwrite) {
            throw new ResourceOverwriteException(destUri);
        }
        
        // checking destParent
        ResourceImpl destParent = this.dao.load(destUri.getParent());
        if ((destParent == null) || !destParent.isCollection()) {
            throw new IllegalOperationException("Invalid destination resource");
        }

        checkLock(src, principal, lockToken);
        checkLock(srcParent, principal, lockToken);
        if (dest != null) {
            checkLock(dest, principal, lockToken);
        }
        checkLock(destParent, principal, lockToken);

        this.authorizationManager.authorizeMove(srcUri, destUri, principal, overwrite);
        checkMaxChildren(destParent);

        // Performing delete operation if destination exists (overwrite).
        if (dest != null) {
            this.dao.delete(dest);
            this.contentStore.deleteResource(dest.getURI());
            this.context.publishEvent(new ResourceDeletionEvent(this, principal, dest));
        }

        try {
            // Process parent collections
            if (destParent.getURI().equals(srcParent.getURI())) {
                destParent.removeChildURI(src.getURI());
            } else {
                final ResourceImpl srcParentOriginal = (ResourceImpl) srcParent.clone();
                srcParent.removeChildURI(src.getURI());
                Content content = getContent(srcParent);
                srcParent = this.resourceHelper.contentModification(srcParent, principal, content);
                this.dao.store(srcParent);
                this.context.publishEvent(new ContentModificationEvent(this, principal, (Resource) srcParent.clone(),
                        srcParentOriginal));
            }

            final ResourceImpl destParentOriginal = (ResourceImpl) destParent.clone();
            destParent.addChildURI(destUri);
            Content content = getContent(destParent);
            destParent = this.resourceHelper.contentModification(destParent, principal, content);
            this.dao.store(destParent);
            this.context.publishEvent(new ContentModificationEvent(this, principal, (Resource) destParent.clone(),
                    destParentOriginal));

            // Process move
            ResourceImpl newResource = src.createCopy(destUri, src.isInheritedAcl() ? destParent.getAcl() : src.getAcl());
            newResource.setInheritedAcl(src.isInheritedAcl());
            newResource.setAclInheritedFrom(src.getAclInheritedFrom());
            content = getContent(src);

            // Let hooks process move
            TypeHandlerHooks hooks = typeHandlerHooksRegistry.getTypeHandlerHooks(src);
            if (hooks != null) {
                try {
                    hooks.onMove(src, newResource);
                } catch (Exception e) {
                    throw new TypeHandlerHookException("failed in onMove hook: " + e.getMessage(), e);
                }
            }
            
            Resource srcClone = (Resource) src.clone();
            
            newResource = this.resourceHelper.nameChange(src, newResource, principal, content);
            newResource = this.dao.move(src, newResource);
            this.contentStore.move(src.getURI(), newResource.getURI());

            this.context.publishEvent(new ResourceMovedEvent(
                    this, principal, (Resource) newResource.clone(), srcClone));
            
        } catch (CloneNotSupportedException e) {
            throw new IOException("Failed to clone object", e);
        }
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public void delete(@OpLogParam(name = "token") String token, String lockToken, @OpLogParam Path uri, @OpLogParam boolean restorable) throws IllegalOperationException,
            AuthorizationException, AuthenticationException, ResourceNotFoundException, ResourceLockedException,
            FailedDependencyException, ReadOnlyException, IOException {

        Principal principal = getPrincipal(token);

        if (uri.isRoot()) {
            throw new IllegalOperationException("Cannot delete the root resource ('/')");
        }

        final ResourceImpl resourceToDelete = this.dao.load(uri);

        if (resourceToDelete == null) {
            throw new ResourceNotFoundException(uri);
        }

        // Store parent collection first to avoid dead-lock between Cache
        // locking and database inter-transaction synchronization (which leads
        // to "11-iterations"-problem)
        ResourceImpl parentCollection = this.dao.load(uri.getParent());
        if (resourceToDelete.hasPublishDate()) {
            this.authorizationManager.authorizeDelete(uri, principal);
        } else {
            this.authorizationManager.authorizeDeleteUnpublished(uri, principal);
        }
        checkLock(parentCollection, principal, lockToken);
        checkLock(resourceToDelete, principal, lockToken);
        
        try {

            final ResourceImpl originalParent = (ResourceImpl) parentCollection.clone();
            parentCollection.removeChildURI(uri);
            Content content = getContent(parentCollection);
            parentCollection = this.resourceHelper.contentModification(parentCollection, principal, content);
            parentCollection = this.dao.store(parentCollection);
            this.context.publishEvent(new ContentModificationEvent(this, principal, (Resource) parentCollection.clone(),
                    originalParent));

            TypeHandlerHooks hooks = typeHandlerHooksRegistry.getTypeHandlerHooks(resourceToDelete);
            if (hooks != null) {
                try {
                    hooks.onDelete(resourceToDelete, restorable);
                } catch (Exception e) {
                    throw new TypeHandlerHookException("failed in onDelete hook: " + e.getMessage(), e);
                }
            }

            if (restorable) {
                // Check ACL before moving to trash can,
                // if inherited, take snapshot of current ACL
                if (resourceToDelete.isInheritedAcl()) {
                    ResourceImpl clone = (ResourceImpl) resourceToDelete.clone();
                    clone.setInheritedAcl(false);
                    this.dao.storeACL(clone);
                }

                final String trashID = "trash-" + resourceToDelete.getNumericId();
                this.dao.markDeleted(resourceToDelete, parentCollection, principal, trashID);
                this.contentStore.moveToTrash(resourceToDelete.getURI(), trashID);

            } else {
                this.dao.delete(resourceToDelete);
                this.contentStore.deleteResource(resourceToDelete.getURI());
            }

            ResourceDeletionEvent event = new ResourceDeletionEvent(this, principal, resourceToDelete);
            this.context.publishEvent(event);
        
        } catch (CloneNotSupportedException e) {
            throw new IOException("Failed to clone object", e);
        }

    }

    @Transactional(readOnly=true)
    @OpLog
    @Override
    public List<RecoverableResource> getRecoverableResources(@OpLogParam(name = "token") String token, @OpLogParam Path uri) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IOException {

        Principal principal = getPrincipal(token);
        ResourceImpl resource = this.dao.load(uri);
        this.authorizationManager.authorizeRead(uri, principal);

        if (resource == null) {
            throw new ResourceNotFoundException(uri);
        }
        return this.dao.getRecoverableResources(resource.getNumericId());
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public void recover(@OpLogParam(name = "token") String token, String lockToken, @OpLogParam Path parentUri, @OpLogParam RecoverableResource recoverableResource)
            throws ResourceNotFoundException, AuthorizationException, AuthenticationException, IOException {

        Principal principal = getPrincipal(token);
        ResourceImpl parent = this.dao.load(parentUri);

        if (parent == null) {
            throw new ResourceNotFoundException(parentUri);
        }

        checkLock(parent, principal, lockToken);
        this.authorizationManager.authorizeReadWrite(parentUri, principal);

        try {
            ResourceImpl recovered = this.dao.recover(parentUri, recoverableResource);
            this.contentStore.recover(parentUri, recoverableResource);
            this.context.publishEvent(new ResourceCreationEvent(this, principal, (Resource) recovered.clone()));

            // Content modification on parent
            final ResourceImpl originalParent = (ResourceImpl) parent.clone();
            parent.addChildURI(recovered.getURI());
            Content content = getContent(parent);
            parent = this.resourceHelper.contentModification(parent, principal, content);
            parent = this.dao.store(parent);
            this.context.publishEvent(new ContentModificationEvent(this, principal, (Resource) parent.clone(), originalParent));

        } catch (CloneNotSupportedException c) {
            throw new IOException("Failed to clone object", c);
        }
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public void deleteRecoverable(@OpLogParam(name = "token") String token, String lockToken, @OpLogParam Path parentUri, @OpLogParam RecoverableResource recoverableResource)
            throws IOException {

        ResourceImpl parent = this.dao.load(parentUri);

        if (parent == null) {
            throw new ResourceNotFoundException(parentUri);
        }

        this.dao.deleteRecoverable(recoverableResource);
        this.contentStore.deleteRecoverable(recoverableResource);
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public Resource lock(@OpLogParam(name = "token") String token, @OpLogParam Path uri, String ownerInfo, Repository.Depth depth, int requestedTimeoutSeconds,
            String lockToken, Lock.Type lockType) throws ResourceNotFoundException, AuthorizationException, AuthenticationException,
            FailedDependencyException, ResourceLockedException, IllegalOperationException, ReadOnlyException,
            IOException {

        Principal principal = getPrincipal(token);
        if (depth == Depth.ONE || depth == Depth.INF) {
            throw new IllegalOperationException("Unsupported depth parameter: " + depth);
        }
        ResourceImpl r = this.dao.load(uri);
        if (r == null) {
            throw new ResourceNotFoundException(uri);
        }

        if (lockToken != null) {
            if (r.getLock() == null) {
                throw new IllegalOperationException("Invalid lock refresh request: lock token '" + lockToken
                        + "' does not exists on resource " + r.getURI());
            }
            if (!r.getLock().getLockToken().equals(lockToken)) {
                throw new IllegalOperationException("Invalid lock refresh request: lock token '" + lockToken
                        + "' does not match existing lock token on resource " + uri);
            }
        }

        checkLock(r, principal, lockToken);

        // Principal with at least privelege READ_WRITE_UNPUBLISHED required to lock resource:
        this.authorizationManager.authorizeReadWriteUnpublished(uri, principal);

        boolean refresh = lockToken != null;

        if (!refresh) {
            r.setLock(null);
        }

        int timeoutSeconds = requestedTimeoutSeconds > 0 ? requestedTimeoutSeconds : lockDefaultTimeoutSeconds;
        timeoutSeconds = Math.min(lockMaxTimeoutSeconds, timeoutSeconds);
        Date timeout = new Date(System.currentTimeMillis() + timeoutSeconds*1000);
        if (r.getLock() == null) {
            String newLockToken = "opaquelocktoken:" + UUID.randomUUID().toString();

            Lock lock = new Lock(newLockToken, principal, ownerInfo, depth, timeout, lockType);
            r.setLock(lock);
        } else {
            r.setLock(new Lock(r.getLock().getLockToken(), principal, ownerInfo, depth, timeout, r.getLock().getType()));
        }

        ResourceImpl newResource = this.dao.storeLock(r);
        try {
            return (Resource) newResource.clone();
        } catch (CloneNotSupportedException c) {
            throw new IOException("Failed to clone resource");
        }
    }

    private void checkLock(Resource resource, Principal principal, String lockToken) throws ResourceLockedException, IOException,
            AuthenticationException {
        Lock lock = resource.getLock();
        if (lock == null) {
            return;
        }
        if (principal == null) {
            throw new AuthenticationException();
        }
        switch (lock.getType()) {
            case EXCLUSIVE:
                if (!lock.getPrincipal().equals(principal)) {
                    throw new ResourceLockedException("Resource locked by " + lock.getPrincipal());
                }
                return;

            case SHARED_ACL_WRITE:
                if (!lock.getLockToken().equals(lockToken)) {
                    throw new ResourceLockedException("Shared lock present, token mismatch");
                }
        }
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public void unlock(@OpLogParam(name = "token") String token, @OpLogParam Path uri, String lockToken) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, ResourceLockedException, ReadOnlyException, IOException {
        Principal principal = getPrincipal(token);
        ResourceImpl r = this.dao.load(uri);
        if (r == null) {
            throw new ResourceNotFoundException(uri);
        }

        this.authorizationManager.authorizeUnlock(uri, principal, lockToken);

        if (r.getLock() != null) {
            r.setLock(null);
            this.dao.storeLock(r);
        }
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public Resource store(@OpLogParam(name = "token") String token, String lockToken, @OpLogParam Resource resource) throws ResourceNotFoundException, AuthorizationException,
            ResourceLockedException, AuthenticationException, IllegalOperationException, ReadOnlyException, IOException {
        return store(token, lockToken, resource, null);
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public Resource store(@OpLogParam(name = "token") String token, String lockToken, @OpLogParam Resource resource, @OpLogParam StoreContext storeContext) throws ResourceNotFoundException,
            AuthorizationException, ResourceLockedException, AuthenticationException, IllegalOperationException,
            ReadOnlyException, IOException {

        final Principal principal = getPrincipal(token);

        if (resource == null) {
            throw new IllegalOperationException("Can't store nothingness.");
        }

        if (!(resource instanceof ResourceImpl)) {
            throw new IllegalOperationException("Can't store unknown implementation of Resource..");
        }
        final Path uri = resource.getURI();

        final ResourceImpl original = this.dao.load(uri);
        if (original == null) {
            throw new ResourceNotFoundException(uri);
        }

        // Check if specialized store context:
        // System change
        if (storeContext != null) {
            if (storeContext instanceof SystemChangeContext) {
                return storeSystemChange(uri, resource, original, principal, lockToken, (SystemChangeContext) storeContext);
            }

            // Inheritable props store
            if (storeContext instanceof InheritablePropertiesStoreContext) {
                return storeInheritableProps(uri, resource, original, principal, lockToken,
                        (InheritablePropertiesStoreContext) storeContext);
            }

            throw new IllegalArgumentException("Unknown store context impl: " + storeContext);
        }

        // Regular store
        checkLock(original, principal, lockToken);

        if (original.hasPublishDate()) {
            this.authorizationManager.authorizeReadWrite(uri, principal);
        } else {
            this.authorizationManager.authorizeReadWriteUnpublished(uri, principal);
        }

        try {
            ResourceImpl originalClone = (ResourceImpl) original.clone();
            
            ResourceImpl suppliedResource = (ResourceImpl) resource;

            TypeHandlerHooks hooks = typeHandlerHooksRegistry.getTypeHandlerHooks(original);
            if (hooks != null) {
                try {
                    suppliedResource = hooks.onStore(suppliedResource);
                } catch (Exception e) {
                    throw new TypeHandlerHookException("failed in onStore hook: " + e.getMessage(), e);
                }
            }
            
            ResourceImpl newResource;
            Content content = getContent(original);

            newResource = this.resourceHelper.propertiesChange(original, principal, suppliedResource, content);

            newResource = this.dao.store(newResource);

            ResourceModificationEvent event = new ResourceModificationEvent(this, principal, (Resource) newResource.clone(),
                    originalClone);
            this.context.publishEvent(event);

            return (Resource) newResource.clone();
        } catch (CloneNotSupportedException e) {
            throw new IOException("Failed to clone object", e);
        }
    }

    private Resource storeSystemChange(Path uri, Resource resource, ResourceImpl original, Principal principal, String lockToken,
            SystemChangeContext context) throws IOException {
        // Require root role for system change
        this.authorizationManager.authorizeRootRoleAction(principal);

        // System change stores can choose to ignore resource locking
        if (!context.isIgnoreLocking()) {
            checkLock(original, principal, lockToken);
        }

        try {
            ResourceImpl originalClone = (ResourceImpl) original.clone();

            ResourceImpl suppliedResource = (ResourceImpl) resource;

            TypeHandlerHooks hooks = typeHandlerHooksRegistry.getTypeHandlerHooks(original);
            if (hooks != null) {
                try {
                    suppliedResource = hooks.onStoreSystemChange(suppliedResource, context);
                } catch (Exception e) {
                    throw new TypeHandlerHookException("failed in onStoreSystemChange hook: " + e.getMessage(), e);
                }
            }
            
            ResourceImpl newResource;
            Content content = getContent(original);

            newResource = this.resourceHelper.systemChange(original, principal, suppliedResource, content, context);

            newResource = this.dao.store(newResource);

            ResourceModificationEvent event = new ResourceModificationEvent(this, principal, (Resource) newResource.clone(),
                    originalClone);
            this.context.publishEvent(event);

            return (Resource) newResource.clone();
        } catch (CloneNotSupportedException e) {
            throw new IOException("Failed to clone object", e);
        }
    }

    private Resource storeInheritableProps(Path uri, Resource resource, ResourceImpl original, Principal principal, String lockToken,
            InheritablePropertiesStoreContext context) throws IOException {

        // Lock checking is delegated to specialized store context handling methods
        // so make sure we check it first.
        checkLock(original, principal, lockToken);
        
        // Normal write privilege required
        if (original.hasPublishDate()) {
            this.authorizationManager.authorizeReadWrite(uri, principal);
        } else {
            this.authorizationManager.authorizeReadWriteUnpublished(uri, principal);
        }

        try {
            ResourceImpl originalClone = (ResourceImpl) original.clone();

            ResourceImpl suppliedResource = (ResourceImpl) resource;

            TypeHandlerHooks hooks = typeHandlerHooksRegistry.getTypeHandlerHooks(original);
            if (hooks != null) {
                try {
                    suppliedResource = hooks.onStoreInheritableProps(suppliedResource, context);
                } catch (Exception e) {
                    throw new TypeHandlerHookException("failed in onStoreInheritableProps hook: " + e.getMessage(), e);
                }
            }
            
            ResourceImpl newResource;
            Content content = getContent(original);

            newResource = this.resourceHelper.inheritablePropertiesChange(original, principal, suppliedResource,
                    content, context);

            newResource = this.dao.store(newResource);

            InheritablePropertiesModificationEvent event = new InheritablePropertiesModificationEvent(this,
                    principal, (Resource) newResource.clone(), originalClone);
            this.context.publishEvent(event);

            return (Resource) newResource.clone();
        } catch (CloneNotSupportedException e) {
            throw new IOException("Failed to clone object", e);
        }
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public Resource createDocument(@OpLogParam(name = "token") String token, String lockToken, 
            @OpLogParam Path uri, ContentInputSource inputContent, AclMode aclMode) 
            throws IllegalOperationException, AuthorizationException, AuthenticationException, 
            ResourceLockedException, ReadOnlyException, IOException {
        Objects.requireNonNull(uri, "Argument 'uri' is NULL");
        Objects.requireNonNull(inputContent, "Argument 'inputContent' is NULL");
        Objects.requireNonNull(aclMode, "Argument 'aclMode' is NULL");

        Principal principal = getPrincipal(token);
        if (this.dao.load(uri) != null) {
            throw new ResourceOverwriteException(uri);
        }
        ResourceImpl parent = this.dao.load(uri.getParent());
        if ((parent == null) || !parent.isCollection()) {
            throw new IllegalOperationException("Either parent doesn't exist or parent is document");
        }

        checkLock(parent, principal, lockToken);

        if (!aclMode.inherited()) {
            validateNewAcl(aclMode.acl().get());
            this.authorizationManager.authorizeCreateWithAcl(parent.getURI(), principal);
        }
        else {
            // Allow if principal has at least privileges to create unpublished resource
            this.authorizationManager.authorizeCreateUnpublished(parent.getURI(), principal);
        }
        
        checkMaxChildren(parent);

        try {
            // Store content modification on parent
            final ResourceImpl originalParent = (ResourceImpl) parent.clone();
            parent.addChildURI(uri);
            parent = this.resourceHelper.contentModification(parent, principal, getContent(parent));
            parent = this.dao.store(parent);
            this.context.publishEvent(new ContentModificationEvent(this, principal, (Resource) parent.clone(), originalParent));

            // Set up new resource
            ResourceImpl newResource = new ResourceImpl(uri);
            
            // Set initial (inherited) permissions on new resource:
            newResource.setAcl(parent.getAcl());
            newResource.setInheritedAcl(true);
            int aclIneritedFrom = parent.isInheritedAcl() ? parent.getAclInheritedFrom() : parent.getNumericId();
            newResource.setAclInheritedFrom(aclIneritedFrom);
            
            
            // Check if contentType has interceptor for storage
            String contentType = MimeHelper.map(uri.getName());
            TypeHandlerHooks hooks = typeHandlerHooksRegistry.getTypeHandlerHooks(contentType);
            if (hooks != null) {
                try {
                    newResource = hooks.storeContentOnCreate(newResource, inputContent, contentType,
                                                             p -> getPrincipal(token), TOKEN_REFRESH_PROGRESS_INTERVAL);
                    Content content = hooks.getContentForEvaluation(newResource, getDefaultContent(newResource));
                    newResource = this.resourceHelper.create(principal, newResource, false, content);
                }
                catch (UnsupportedContentException uce) {
                    // Fallback-process the content, as typehandler did not accept it.
                    this.contentStore.storeContent(uri, uce.getContentInputSource(),
                                                   p -> getPrincipal(token), TOKEN_REFRESH_PROGRESS_INTERVAL);
                    // Run through type evaluation
                    newResource = this.resourceHelper.create(principal, newResource, false, getDefaultContent(newResource));
                }
                catch (Exception e) {
                    throw new TypeHandlerHookException("failed in onCreateDocument hook: " + e.getMessage(), e);
                }
            }
            else {
                this.contentStore.storeContent(uri, inputContent,
                                               p -> getPrincipal(token), TOKEN_REFRESH_PROGRESS_INTERVAL);
                // Run through type evaluation
                newResource = this.resourceHelper.create(principal, newResource, false, getDefaultContent(newResource));
            }
            
            // Store new resource
            newResource = this.dao.store(newResource);

            // Store ACL if passed by client: 
            if (!aclMode.inherited()) {
                Acl acl = aclMode.acl().get();
                newResource.setAcl(acl);
                newResource.setInheritedAcl(false);
                newResource.setAclInheritedFrom(PropertySetImpl.NULL_RESOURCE_ID);
                newResource = this.dao.storeACL(newResource);
            }
            
            this.context.publishEvent(new ResourceCreationEvent(this, principal, (Resource) newResource.clone()));

            return (Resource) newResource.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new IOException("Failed to clone object", e);
        }
    }
    
    /**
     * Write data to a resource.
     */
    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public Resource storeContent(@OpLogParam(name = "token") String token, String lockToken, @OpLogParam Path uri, ContentInputSource contentInput) throws AuthorizationException,
            AuthenticationException, ResourceNotFoundException, ResourceLockedException, IllegalOperationException,
            ReadOnlyException, IOException {

        Principal principal = getPrincipal(token);

        ResourceImpl r = this.dao.load(uri);

        if (r == null) {
            throw new ResourceNotFoundException(uri);
        } else if (r.isCollection()) {
            throw new IllegalOperationException("resource is collection");
        }

        checkLock(r, principal, lockToken);
        if (r.hasPublishDate()) {
            this.authorizationManager.authorizeReadWrite(uri, principal);
        } else {
            this.authorizationManager.authorizeReadWriteUnpublished(uri, principal);
        }
        
        try {
            final ResourceImpl original = (ResourceImpl) r.clone();

            TypeHandlerHooks hooks = typeHandlerHooksRegistry.getTypeHandlerHooks(r);
            if (hooks != null) {
                try {
                    r = hooks.storeContent(r, contentInput, MimeHelper.map(uri.getName()),
                                           p -> getPrincipal(token), TOKEN_REFRESH_PROGRESS_INTERVAL);
                } catch (Exception e) {
                    throw new TypeHandlerHookException("failed in onStoreContent hook: " + e.getMessage(), e);
                }
            } else {
                this.contentStore.storeContent(uri, contentInput,
                                               p -> getPrincipal(token), TOKEN_REFRESH_PROGRESS_INTERVAL);
            }

            Content content = getContent(r);
            r = this.resourceHelper.contentModification(r, principal, content);

            Resource newResource = this.dao.store(r);

            this.context.publishEvent(new ContentModificationEvent(this, principal, (Resource) newResource.clone(), original));

            return (Resource) newResource.clone();
        } catch (CloneNotSupportedException e) {
            throw new IOException("Failed to clone object", e);
        }
    }
    
    /**
     * Write data to a resource.
     * Used to update contents of working copy revision.
     */
    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public Resource storeContent(@OpLogParam(name = "token") String token, String lockToken, @OpLogParam Path uri, ContentInputSource contentInput, @OpLogParam Revision revision)
            throws AuthorizationException, AuthenticationException, ResourceNotFoundException, ResourceLockedException,
            IllegalOperationException, ReadOnlyException, IOException {

        if (uri == null) {
            throw new IllegalArgumentException("URI is NULL");
        }
        if (contentInput == null) {
            throw new IllegalArgumentException("content input is NULL");
        }
        if (revision == null) {
            throw new IllegalArgumentException("revision is NULL");
        }
        if (revision.getType() != Revision.Type.WORKING_COPY) {
            throw new IllegalArgumentException("Only WORKING_COPY is supported");
        }
        Principal principal = getPrincipal(token);

        ResourceImpl r = this.dao.load(uri);

        if (r == null) {
            throw new ResourceNotFoundException(uri);
        } else if (r.isCollection()) {
            throw new IllegalOperationException("resource is collection");
        }

        Revision existing = null;
        for (Revision rev : this.revisionStore.list(r)) {
            if (rev.getID() == revision.getID()) {
                existing = rev;
                break;
            }
        }
        if (existing == null) {
            throw new IllegalOperationException("No revision WORKING_COPY exists for resource " + r);
        }
        if (existing.getType() != Type.WORKING_COPY) {
            throw new IllegalOperationException("Only revisions of type WORKING_COPY may be updated" + r);
        }

        checkLock(r, principal, lockToken);
        
        // READ_WRITE_UNPUBLISHED is sufficient for updating working copy, regardless of publication status:
        this.authorizationManager.authorizeReadWriteUnpublished(uri, principal);

        long revisionId = existing.getID();
        Acl acl = null; // r.getAcl();
        String name = existing.getName();
        String uid = principal.getQualifiedName();
        Type type = existing.getType();
        Date timestamp = new Date();
        String checksum;

        File tempFile = null;
        try {
            tempFile = File.createTempFile("revision", null, this.tempDir);
            Digest.StreamWrapper wrapper = Revisions.wrap(contentInput.stream());
            OutputStream out = new FileOutputStream(tempFile);
            
            IO.copy(wrapper, out).bufferSize(FILE_COPY_BUF_SIZE)
                    .progress(p -> getPrincipal(token))
                    .progressInterval(TOKEN_REFRESH_PROGRESS_INTERVAL)
                    .perform();
            checksum = wrapper.compute();

            contentInput = ContentInputSources.fromFile(tempFile, true);

            Revision.Builder builder = Revision.newBuilder();
            Revision rev = builder.id(revisionId).acl(acl).checksum(checksum).name(name)
                    .type(type).timestamp(timestamp).uid(uid).build();
            this.revisionStore.store(r, rev, contentInput.stream());

            Content content = getContent(r, existing);
            ResourceImpl result = this.resourceHelper.contentModification(r, principal, content);
            return result;

        } finally {
            if (tempFile != null) {
                if (!tempFile.delete()) {
                    throw new DataAccessException("Failed to delete temporary file " + tempFile);
                }
            }
        }
    }

    @Override
    public boolean authorize(Principal principal, Acl acl, Privilege privilege) {
        return this.authorizationManager.authorize(principal, acl, privilege);
    }

    private boolean isAuthorizedInternal(Resource r, RepositoryAction a, Principal p, boolean considerLocks, String lockToken) throws IOException {

        if (r == null) {
            throw new IllegalArgumentException("Resource is NULL");
        }
        if (a == null) {
            throw new IllegalArgumentException("Action is NULL");
        }
        if (a == RepositoryAction.COPY || a == RepositoryAction.MOVE) {
            throw new IllegalArgumentException("Cannot authorize action " + a + " on a single resource");
        }
        try {
            if (considerLocks) {
                if (a == RepositoryAction.DELETE
                     || a == RepositoryAction.DELETE_UNPUBLISHED) {
                    if (r.getURI().isRoot()) {
                        return false;
                    }
                    Resource parent = this.dao.load(r.getURI().getParent());
                    checkLock(parent, p, lockToken);
                    checkLock(r, p, lockToken);

                // Else check lock if action can modify repo:
                } else if (a == RepositoryAction.ALL
                        || a == RepositoryAction.ADD_COMMENT
                        || a == RepositoryAction.EDIT_COMMENT
                        || a == RepositoryAction.REPOSITORY_ADMIN_ROLE_ACTION
                        || a == RepositoryAction.REPOSITORY_ROOT_ROLE_ACTION
                        || a == RepositoryAction.UNEDITABLE_ACTION
                        || a == RepositoryAction.UNLOCK
                        || a == RepositoryAction.CREATE
                        || a == RepositoryAction.CREATE_UNPUBLISHED
                        || a == RepositoryAction.WRITE
                        || a == RepositoryAction.READ_WRITE
                        || a == RepositoryAction.PUBLISH_UNPUBLISH
                        || a == RepositoryAction.READ_WRITE_UNPUBLISHED
                        || a == RepositoryAction.WRITE_ACL) {
                    checkLock(r, p, lockToken);
                }
            }
            this.authorizationManager.authorizeAction(r.getURI(), a, p);
            return true;
        } catch (AuthenticationException e) {
            return false;
        } catch (RepositoryException e) {
            return false;
        }

    }

    @Transactional(readOnly=true)
    @Override
    public boolean isAuthorized(Resource r, RepositoryAction a, Principal p, boolean considerLocks) throws IOException {
        return isAuthorizedInternal(r, a, p, considerLocks, null);
    }

    @Transactional(readOnly=true)
    @Override
    public boolean isAuthorized(Resource r, RepositoryAction a, Principal p, String lockToken) throws IOException {
        return isAuthorizedInternal(r, a, p, true, lockToken);
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public Resource storeACL(@OpLogParam(name = "token") String token, String lockToken, @OpLogParam Path uri, Acl acl) throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, IllegalOperationException, ReadOnlyException, IOException {
        return this.storeACL(token, lockToken, uri, acl, true);
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public Resource storeACL(@OpLogParam(name = "token") String token, String lockToken, @OpLogParam Path uri, Acl acl, boolean validateACL) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IllegalOperationException, ReadOnlyException, IOException {

        if (uri == null) {
            throw new IllegalArgumentException("URI is null");
        }
        if (acl == null) {
            throw new IllegalArgumentException("ACL is null");
        }
        Principal principal = getPrincipal(token);

        ResourceImpl r = this.dao.load(uri);
        if (r == null) {
            throw new ResourceNotFoundException(uri);
        }
        checkLock(r, principal, lockToken);
        if (!validateACL) {
            // Writing ACL without validation is a root role action
            this.authorizationManager.authorizeRootRoleAction(principal);
        } else {
            this.authorizationManager.authorizeAll(r.getURI(), principal);            
        }

        try {
            if (validateACL) {
                validateNewAcl(acl, r.getAcl());
            }
            Resource original = (Resource) r.clone();
            r.setAcl(acl);
            r.setInheritedAcl(false);
            r.setAclInheritedFrom(PropertySetImpl.NULL_RESOURCE_ID);


            ResourceImpl newResource = this.dao.storeACL(r);

            newResource = (ResourceImpl) newResource.clone(); // Clone for event
                                                              // publishing

            ACLModificationEvent event = new ACLModificationEvent(this, principal, newResource, original);

            this.context.publishEvent(event);

            return (Resource) newResource.clone(); // Clone for return value

        } catch (CloneNotSupportedException e) {
            throw new IOException("Failed to clone object", e);
        }
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public Resource deleteACL(@OpLogParam(name = "token") String token, String lockToken, @OpLogParam Path uri) throws ResourceNotFoundException, AuthorizationException,
            AuthenticationException, IllegalOperationException, ReadOnlyException, IOException {
        if (uri == null) {
            throw new IllegalArgumentException("URI is null");
        }
        if (uri == Path.ROOT) {
            throw new IllegalOperationException("The root resource cannot have an inherited ACL");
        }
        Principal principal = getPrincipal(token);

        ResourceImpl r = this.dao.load(uri);
        if (r == null) {
            throw new ResourceNotFoundException(uri);
        }
        checkLock(r, principal, lockToken);
        this.authorizationManager.authorizeAll(r.getURI(), principal);

        if (r.isInheritedAcl()) {
            throw new IllegalOperationException("Resource " + r.getURI() + " already has an inherited ACL");
        }
        try {
            ResourceImpl parent = this.dao.load(uri.getParent());

            Resource original = (Resource) r.clone();
            r.setAcl(parent.getAcl());
            r.setAclInheritedFrom(parent.getNumericId());
            r.setInheritedAcl(true);

            ResourceImpl newResource = this.dao.storeACL(r);

            newResource = (ResourceImpl) newResource.clone(); // clone for event
                                                              // publish

            ACLModificationEvent event = new ACLModificationEvent(this, principal, newResource, original);

            this.context.publishEvent(event);

            return (Resource) newResource.clone(); // clone for return value

        } catch (CloneNotSupportedException e) {
            throw new IOException("Failed to clone object", e);
        }
    }

    @Override
    public boolean isValidAclEntry(Privilege privilege, Principal principal) {
        return this.authorizationManager.isValidAclEntry(privilege, principal);
    }

    @Override
    public boolean isBlacklisted(Privilege privilege, Principal principal) {
        return this.authorizationManager.isBlackListed(principal, privilege);
    }

    @Transactional(readOnly=true)
    @OpLog
    @Override
    public List<Revision> getRevisions(@OpLogParam(name = "token") String token, @OpLogParam Path uri) throws AuthorizationException,
            ResourceNotFoundException, AuthenticationException, IOException {
        if (uri == null) {
            throw new IllegalArgumentException("URI is NULL");
        }
        ResourceImpl resource = this.dao.load(uri);
        if (resource == null) {
            throw new ResourceNotFoundException(uri);
        }
        Principal principal = getPrincipal(token);
        this.authorizationManager.authorizeReadProcessed(resource.getURI(), principal);
        return Collections.unmodifiableList(this.revisionStore.list(resource));
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public Revision createRevision(@OpLogParam(name = "token") String token, String lockToken, @OpLogParam Path uri, @OpLogParam Revision.Type type) throws ReadOnlyException,
            AuthorizationException, ResourceNotFoundException, AuthenticationException, IOException {
        if (uri == null) {
            throw new IllegalArgumentException("URI is NULL");
        }
        if (type == null) {
            throw new IllegalArgumentException("Type is NULL");
        }
        ResourceImpl resource = this.dao.load(uri);
        if (resource == null) {
            throw new ResourceNotFoundException(uri);
        }
        Principal principal = getPrincipal(token);

        checkLock(resource, principal, lockToken);
        if (resource.hasPublishDate()) {
            if (type == Revision.Type.WORKING_COPY) {
                // Allow for principal with only READ_WRITE_UNPUBLISHED for working copy
                // regardless of publication status:
                this.authorizationManager.authorizeReadWriteUnpublished(uri, principal);
            } else {
                this.authorizationManager.authorizeReadWrite(uri, principal);
            }
        } else {
            this.authorizationManager.authorizeReadWriteUnpublished(uri, principal);
        }
        
        List<Revision> revs = this.revisionStore.list(resource);

        Revision newest = null;
        for (Revision rev : revs) {
            if (type == Type.WORKING_COPY) {
                if (rev.getName().equals(Type.WORKING_COPY.name())) {
                    throw new IllegalOperationException("Working copy revision already exists for " + uri);
                }
                continue;
            }
            if (rev.getType() == Type.WORKING_COPY) {
                continue;
            }
            if (newest == null) {
                newest = rev;
                continue;
            }
            if (rev.getTimestamp().getTime() > newest.getTimestamp().getTime()) {
                newest = rev;
            }
        }
        String name;
        if (type == Type.WORKING_COPY) {
            name = Type.WORKING_COPY.name();
        } else if (newest != null) {
            name = String.valueOf(Integer.parseInt(newest.getName()) + 1);
        } else {
            name = "1";
        }
        InputStream content = this.contentStore.getInputStream(resource.getURI());

        long revisionId = this.revisionStore.newRevisionID();
        String uid = resource.getModifiedBy().getQualifiedName();
        String checksum;
        Date timestamp = resource.getLastModified();
        Acl acl = type == Type.WORKING_COPY ? null : resource.getAcl();

        File tempFile = null;
        try {
            tempFile = File.createTempFile("revision", null, this.tempDir);
            Digest.StreamWrapper wrapper = Revisions.wrap(content);
            OutputStream out = new FileOutputStream(tempFile);
            IO.copy(wrapper, out).bufferSize(FILE_COPY_BUF_SIZE)
                                 .progress(p -> getPrincipal(token))
                                 .progressInterval(TOKEN_REFRESH_PROGRESS_INTERVAL)
                                 .perform();
            
            checksum = wrapper.compute();

            content = new FileInputStream(tempFile);
            Revision.Builder builder = Revision.newBuilder();

            Revision revision = builder.id(revisionId).acl(acl).checksum(checksum)
                    .name(name).type(type).timestamp(timestamp).uid(uid).build();
            this.revisionStore.create(resource, revision, content);
            return revision;

        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public void deleteRevision(@OpLogParam(name = "token") String token, String lockToken, @OpLogParam Path uri, @OpLogParam Revision revision) throws ResourceNotFoundException,
            AuthorizationException, AuthenticationException, IOException {

        if (uri == null) {
            throw new IllegalArgumentException("URI is NULL");
        }
        if (revision == null) {
            throw new IllegalArgumentException("Revision is NULL");
        }
        ResourceImpl resource = this.dao.load(uri);
        if (resource == null) {
            throw new ResourceNotFoundException(uri);
        }
        Principal principal = getPrincipal(token);

        checkLock(resource, principal, lockToken);
        if (revision.getType() == Revision.Type.WORKING_COPY) {
            this.authorizationManager.authorizeReadWriteUnpublished(resource.getURI(), principal);
        } else {
            this.authorizationManager.authorizeReadWrite(resource.getURI(), principal);
        }

        Revision found = null;
        for (Revision rev: revisionStore.list(resource)) {
            if (rev.getID() == revision.getID()) {
                found = rev;
                break;
            }
        }
        if (found == null) {
            throw new IllegalOperationException("Revision not found: " + revision.getID());
        }

        this.revisionStore.delete(resource, revision);
    }

    @Transactional(readOnly=true)
    @OpLog
    @Override
    public List<Comment> getComments(@OpLogParam(name="token") String token, @OpLogParam Resource resource) {
        return getComments(token, resource, false, 500);
    }

    @Transactional(readOnly=true)
    @OpLog
    @Override
    public List<Comment> getComments(@OpLogParam(name="token") String token, @OpLogParam Resource resource, boolean deep, int max) {
        Principal principal = getPrincipal(token);

        if (resource == null) {
            throw new IllegalOperationException("Resource argument cannot be NULL");
        }

        try {
            ResourceImpl original = this.dao.load(resource.getURI());
            if (original == null) {
                throw new ResourceNotFoundException(resource.getURI());
            }
            this.authorizationManager.authorizeReadProcessed(resource.getURI(), principal);
            List<Comment> comments = this.commentDAO.listCommentsByResource(resource, deep, max);
            List<Comment> result = new ArrayList<>();
            Set<Path> authCache = new HashSet<>();
            // Fetch N comments, authorize on the result set:
            for (Comment c : comments) {
                try {
                    if (!authCache.contains(c.getURI())) {
                        this.authorizationManager.authorizeReadProcessed(c.getURI(), principal);
                        authCache.add(c.getURI());
                    }
                    result.add(c);
                } catch (Throwable t) {
                }
            }
            return Collections.unmodifiableList(result);
        } catch (IOException e) {
            throw new RuntimeException("Unhandled IO exception", e);
        }
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public Comment addComment(@OpLogParam(name="token") String token, String lockToken, @OpLogParam Resource resource, String title, String text) {
        Principal principal = getPrincipal(token);
        if (resource == null) {
            throw new IllegalOperationException("Resource argument cannot be NULL");
        }

        if (!(resource instanceof ResourceImpl)) {
            throw new IllegalOperationException("Can't store unknown implementation of resource..");
        }

        try {
            ResourceImpl original = this.dao.load(resource.getURI());
            if (original == null) {
                throw new ResourceNotFoundException(resource.getURI());
            }

            checkLock(resource, principal, lockToken);
            this.authorizationManager.authorizeAddComment(resource.getURI(), principal);

            List<Comment> comments = this.commentDAO.listCommentsByResource(resource, false, this.maxComments);
            if (comments.size() > this.maxComments) {
                throw new IllegalOperationException("Too many comments on resource " + resource.getURI());
            }

            Comment comment = new Comment();
            comment.setURI(original.getURI());
            comment.setTime(new java.util.Date());
            comment.setAuthor(principal);
            comment.setTitle(title);
            comment.setContent(text);
            comment.setApproved(true);

            comment = this.commentDAO.createComment(comment);

            Content content = getContent(original);
            ResourceImpl newResource = this.resourceHelper.commentsChange(original, principal, (ResourceImpl) resource,
                    content);
            newResource = this.dao.store(newResource);

            // Publish resource modification event
            ResourceModificationEvent event = new ResourceModificationEvent(this, principal, (Resource) newResource.clone(),
                    original);
            this.context.publishEvent(event);

            return comment;
        } catch (IOException e) {
            throw new RuntimeException("Unhandled IO exception", e);
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Internal error", e);
        }
    }

    @Transactional(readOnly=false)
    @OpLog
    @Override
    public Comment addComment(@OpLogParam(name="token") String token, String lockToken, @OpLogParam Comment comment) {

        Principal principal = getPrincipal(token);

        // XXX Only allow for users with root privilege?
        this.authorizationManager.authorizeRootRoleAction(principal);

        return this.commentDAO.createComment(comment);
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public void deleteComment(@OpLogParam(name="token") String token, String lockToken, @OpLogParam Resource resource, Comment comment) {
        Principal principal = getPrincipal(token);

        if (resource == null) {
            throw new IllegalOperationException("Resource argument cannot be NULL");
        }

        if (!(resource instanceof ResourceImpl)) {
            throw new IllegalOperationException("Can't store unknown implementation of resource..");
        }

        try {
            ResourceImpl original = this.dao.load(resource.getURI());
            if (original == null) {
                throw new ResourceNotFoundException(resource.getURI());
            }

            checkLock(resource, principal, lockToken);
            this.authorizationManager.authorizeEditComment(resource.getURI(), principal);
            this.commentDAO.deleteComment(comment);

            Content content = getContent(original);
            ResourceImpl newResource = this.resourceHelper.commentsChange(original, principal, (ResourceImpl) resource,
                    content);
            newResource = this.dao.store(newResource);

            // Publish resource modification event (necessary to trigger
            // re-indexing, since a prop is now modified)
            ResourceModificationEvent event = new ResourceModificationEvent(this, principal, (Resource) newResource.clone(),
                    original);
            this.context.publishEvent(event);

        } catch (Exception e) {
            throw new RuntimeException("Unhandled exception", e);
        }
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public void deleteAllComments(@OpLogParam(name="token") String token, String lockToken, @OpLogParam Resource resource) {
        Principal principal = getPrincipal(token);

        if (resource == null) {
            throw new IllegalOperationException("Resource argument cannot be NULL");
        }

        if (!(resource instanceof ResourceImpl)) {
            throw new IllegalOperationException("Can't store unknown implementation of resource..");
        }

        try {
            ResourceImpl original = this.dao.load(resource.getURI());
            if (original == null) {
                throw new ResourceNotFoundException(resource.getURI());
            }

            checkLock(resource, principal, lockToken);
            this.authorizationManager.authorizeEditComment(resource.getURI(), principal);
            this.commentDAO.deleteAllComments(resource);

            Content content = getContent(original);
            ResourceImpl newResource = this.resourceHelper.commentsChange(original, principal, (ResourceImpl) resource,
                    content);
            newResource = this.dao.store(newResource);

            // Publish resource modification event (necessary to trigger
            // re-indexing, since a prop is now modified)
            ResourceModificationEvent event = new ResourceModificationEvent(this, principal, (Resource) newResource.clone(),
                    original);
            this.context.publishEvent(event);

        } catch (Exception e) {
            throw new RuntimeException("Unhandled exception", e);
        }
    }

    @Transactional(readOnly=false)
    @OpLog(write = true)
    @Override
    public Comment updateComment(@OpLogParam(name="token") String token, String lockToken, @OpLogParam Resource resource, Comment comment) {
        Principal principal = getPrincipal(token);

        if (resource == null) {
            throw new IllegalOperationException("Resource argument cannot be NULL");
        }

        if (!(resource instanceof ResourceImpl)) {
            throw new IllegalOperationException("Can't store unknown implementation of resource..");
        }

        try {
            ResourceImpl original = this.dao.load(resource.getURI());
            if (original == null) {
                throw new ResourceNotFoundException(resource.getURI());
            }

            Comment old = null;
            List<Comment> comments = this.commentDAO.listCommentsByResource(resource, false, this.maxComments);
            for (Comment c : comments) {
                if (c.getID().equals(comment.getID()) && c.getURI().equals(comment.getURI())) {
                    old = c;
                    break;
                }
            }
            if (old == null) {
                throw new IllegalArgumentException("Trying to update a non-existing comment");
            }

            checkLock(resource, principal, lockToken);
            this.authorizationManager.authorizeEditComment(resource.getURI(), principal);
            comment = this.commentDAO.updateComment(comment);
            return comment;

        } catch (IOException e) {
            throw new RuntimeException("Unhandled IO exception", e);
        }
    }

    private Principal getPrincipal(String token) {
        return tokenManager.getPrincipal(token);
    }

    private void checkMaxChildren(ResourceImpl resource) {
        if (resource.getChildURIs().size() >= this.maxResourceChildren) {
            throw new AuthorizationException("Collection " + resource.getURI() + " has too many children, maximum is "
                    + this.maxResourceChildren);
        }
    }

    private void validateCopyURIs(Path srcPath, Path destPath) throws IllegalOperationException {

        if (srcPath.isRoot()) {
            throw new IllegalOperationException("Cannot copy or move the root resource ('/')");
        }

        if (destPath.isRoot()) {
            throw new IllegalOperationException("Cannot copy or move to the root resource ('/')");
        }

        if (destPath.equals(srcPath)) {
            throw new IllegalOperationException("Cannot copy or move a resource to itself");
        }

        if (srcPath.isAncestorOf(destPath)) {
            throw new IllegalOperationException("Cannot copy or move a resource into itself");
        }
    }

    /**
     * Validates all entries in an ACL
     */
    private void validateNewAcl(Acl acl) {
        Set<Privilege> actions = acl.getActions();
        for (Privilege action : actions) {
            Set<Principal> principals = acl.getPrincipalSet(action);
            for (Principal principal : principals) {
                if (!this.authorizationManager.isValidAclEntry(action, principal)) {
                    throw new InvalidPrincipalException(principal);
                }
            }
        }
    }

    /**
     * Validates all entries in an ACL, but keeps invalid entries if they
     * are already present in the original Acl
     */
    private void validateNewAcl(Acl acl, Acl originalAcl) throws InvalidPrincipalException {
        Set<Privilege> actions = acl.getActions();
        for (Privilege action : actions) {
            Set<Principal> principals = acl.getPrincipalSet(action);
            for (Principal principal : principals) {
                boolean valid = this.authorizationManager.isValidAclEntry(action, principal);
                if (!valid) {
                    // Preserve invalid principals already in ACL
                    if (!originalAcl.hasPrivilege(action, principal)) {
                        throw new InvalidPrincipalException(principal);
                    }
                }
            }
        }
    }

    @Override
    public void setReadOnly(String token, boolean readOnly) throws AuthorizationException {
        Principal principal = getPrincipal(token);
        this.authorizationManager.authorizeRootRoleAction(principal);
        this.authorizationManager.setReadOnly(readOnly);
    }

    @Override
    public ResultSet search(String token, Search search) throws QueryException {
        if (this.searcher != null) {

            // Set use default excludes to true, exclude not published and
            // unpublishedCollection resources from search

            long before = System.currentTimeMillis();
            try {
                return this.searcher.execute(token, search);
            } finally {
                long duration = System.currentTimeMillis() - before;
                if (searchLogger.isTraceEnabled()) {
                    searchLogger.trace("search: " + search.toString() + ": " + duration + " ms");
                } else if (searchLogger.isDebugEnabled()) {
                    searchLogger.debug("search: " + duration + " ms");
                }
            }

        } else {
            throw new IllegalStateException("No repository searcher has been configured.");
        }
    }

    @Required
    public void setTokenManager(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Required
    public void setDao(DataAccessor dao) {
        this.dao = dao;
    }

    @Required
    public void setRevisionStore(RevisionStore revisionStore) {
        this.revisionStore = revisionStore;
    }

    @Required
    public void setCommentDAO(CommentDAO commentDAO) {
        this.commentDAO = commentDAO;
    }

    @Required
    public void setContentStore(ContentStore contentStore) {
        this.contentStore = contentStore;
    }

    @Required
    public void setId(String id) {
        this.id = id;
    }

    public void setLockMaxTimeoutSeconds(int maxTimeoutSeconds) {
        if (maxTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("Max lock timeout must be > 0");
        }
        this.lockMaxTimeoutSeconds = maxTimeoutSeconds;
    }

    public void setLockDefaultTimeoutSeconds(int defaultTimeoutSeconds) {
        if (defaultTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("Default lock timeout must be > 0");
        }
        this.lockDefaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    @Required
    public void setAuthorizationManager(AuthorizationManager authorizationManager) {
        this.authorizationManager = authorizationManager;
    }

    @Override
    public void setApplicationContext(ApplicationContext context) {
        this.context = context;
    }

    @Required
    public void setRepositoryResourceHelper(RepositoryResourceHelper resourceHelper) {
        this.resourceHelper = resourceHelper;
    }
    
    @Required
    public void setTypeHandlerHooksRegistry(TypeHandlerHooksRegistry registry) {
        this.typeHandlerHooksRegistry = registry;
    }

    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    @Required
    public void setContentRepresentationRegistry(ContentRepresentationRegistry contentRepresentationRegistry) {
        this.contentRepresentationRegistry = contentRepresentationRegistry;
    }
    
    @Required
    public void setTempDir(String tempDir) {
        File f = new File(tempDir);
        if (!f.exists()) {
            throw new IllegalStateException("Directory " + tempDir + " does not exist");

        }
        this.tempDir = f;
    }

    public void setMaxComments(int maxComments) {
        if (maxComments < 0) {
            throw new IllegalArgumentException("Argument must be an integer >= 0");
        }

        this.maxComments = maxComments;
    }

    public void setMaxResourceChildren(int maxResourceChildren) {
        if (maxResourceChildren < 1) {
            throw new IllegalArgumentException("Argument must be an integer >= 1");
        }
        this.maxResourceChildren = maxResourceChildren;
    }

    public void setSearcher(Searcher searcher) {
        this.searcher = searcher;
    }

    public void setPermanentDeleteOverdueLimitInDays(int permanentDeleteOverdueLimitInDays) {
        if (permanentDeleteOverdueLimitInDays < 1) {
            throw new IllegalArgumentException("Limit cannot be less than 1. Currently set to: "
                    + permanentDeleteOverdueLimitInDays);
        }
        this.permanentDeleteOverdueLimitInDays = permanentDeleteOverdueLimitInDays;
    }
    
    // Get default content with NO hook overrides
    private Content getDefaultContent(ResourceImpl resource) throws IOException {
        if (resource == null || resource.isCollection()) {
            return null;
        }
        return new ContentImpl(resource.getURI(), this.contentStore, this.contentRepresentationRegistry);
    }

    // Get content with possible hook override
    private Content getContent(ResourceImpl resource) throws IOException {
        if (resource == null || resource.isCollection()) {
            return null;
        }

        TypeHandlerHooks hooks = typeHandlerHooksRegistry.getTypeHandlerHooks(resource);
        if (hooks != null) {
            try {
                return hooks.getContentForEvaluation(resource, getDefaultContent(resource));
            } catch (Exception e) {
                throw new IOException("failed in getContentForEvaluation hook: " + e.getMessage(), e);
            }
        }

        return getDefaultContent(resource);
    }

    private Content getContent(final ResourceImpl resource, final Revision revision) {
        if (resource == null || resource.isCollection() || revision == null) {
            return null;
        }

        ContentStore cs = new ContentStore() {

            @Override
            public long getContentLength(Path uri) throws DataAccessException {
                return revisionStore.getContentLength(resource, revision);
            }

            @Override
            public InputStream getInputStream(Path uri) throws DataAccessException {
                return revisionStore.getContent(resource, revision);
            }

            @Override
            public void deleteResource(Path uri) throws DataAccessException {
                throw new IllegalStateException("Don't call me, I'll call you");
            }

            @Override
            public void createResource(Path uri, boolean isCollection) throws DataAccessException {
                throw new IllegalStateException("Don't call me, I'll call you");
            }

            @Override
            public void storeContent(Path uri, ContentInputSource content) throws DataAccessException {
                throw new IllegalStateException("Don't call me, I'll call you");
            }

            @Override
            public void copy(Path srcURI, Path destURI) throws DataAccessException {
                throw new IllegalStateException("Don't call me, I'll call you");
            }

            @Override
            public void move(Path srcURI, Path destURI) throws DataAccessException {
                throw new IllegalStateException("Don't call me, I'll call you");
            }

            @Override
            public void moveToTrash(Path srcURI, String trashIdDir) throws DataAccessException {
                throw new IllegalStateException("Don't call me, I'll call you");
            }

            @Override
            public void recover(Path destURI, RecoverableResource recoverableResource) throws DataAccessException {
                throw new IllegalStateException("Don't call me, I'll call you");
            }

            @Override
            public void deleteRecoverable(RecoverableResource recoverableResource) throws DataAccessException {
                throw new IllegalStateException("Don't call me, I'll call you");
            }

        };
        ContentImpl content = new ContentImpl(resource.getURI(), cs, this.contentRepresentationRegistry);
        return content;
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public void setRevisionGCHours(Set<Integer> revisionGCHours) {
        this.revisionGCHours = revisionGCHours;
    }

    public void setTrashCanPurgeHours(Set<Integer> trashCanPurgeHours) {
        this.trashCanPurgeHours = trashCanPurgeHours;
    }

    public void setMaintenanceIntervalSeconds(int interval) {
        this.maintenanceIntervalSeconds = interval;
    }

    private final Logger periodicLogger = LoggerFactory.getLogger(RepositoryImpl.class.getName() + ".Maintenance");
    private Set<Integer> revisionGCHours = new HashSet<>(Arrays.asList(new Integer[] { 3 }));
    private Set<Integer> trashCanPurgeHours = new HashSet<>(Arrays.asList(new Integer[] { 4 }));
    private int maintenanceIntervalSeconds = 600;
    private PlatformTransactionManager transactionManager;
    private final MaintenanceManager mm = new MaintenanceManager();

    private Optional<ClusterRole> clusterRole = Optional.empty();

    @Override
    public void roleChange(ClusterRole role) {
        clusterRole = Optional.of(role);
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        mm.init();
        context.publishEvent(new RepositoryInitEvent(this));
    }
    
    public void destroy() {
        mm.destroy();
    }

    private final class MaintenanceManager implements Runnable {

        private ScheduledExecutorService executor;
        private TransactionOperations transactionOperations;
        private final DeleteExpiredLocksJob deleteExpiredLocksJob = new DeleteExpiredLocksJob();
        private final PurgeTrashJob purgeTrashJob = new PurgeTrashJob();
        private final RevisionStoreGcJob revisionStoreGcJob = new RevisionStoreGcJob();

        // Start background maintenance jobs
        public void init() {
            if (this.executor != null) {
                throw new IllegalStateException("init() should only be called once at start of bean life cycle");
            }

            // Support optionally configured transaction manager
            if (transactionManager != null) {
                TransactionTemplate tt = new TransactionTemplate(transactionManager);
                tt.setReadOnly(false);
                this.transactionOperations = tt;
            } else {
                // Dummy
                this.transactionOperations = new TransactionOperations() {
                    @Override
                    public <T> T execute(TransactionCallback<T> tc) throws TransactionException {
                        return tc.doInTransaction(null);
                    }
                };
            }

            this.executor = Executors.newSingleThreadScheduledExecutor((Runnable r) ->
                    new Thread(r, getId() + ".repository.maintenance"));

            this.executor.scheduleAtFixedRate(this, 10, maintenanceIntervalSeconds, TimeUnit.SECONDS);

            periodicLogger.info("Initialized repository maintenance manager");
        }

        // Shutdown background jobs
        public void destroy() {
            if (this.executor == null) {
                throw new IllegalStateException("destroy() was called, but init() was never called before.");
            }

            periodicLogger.info("Shutdown repository maintenance manager.");
            this.executor.shutdownNow();
        }

        @Override
        public void run() {
            if (isReadOnly()) {
                return;
            }
            final Optional<ClusterRole> role = clusterRole;
            if (role.isPresent() && role.get() == ClusterRole.SLAVE) {
                return;
            }

            // Delete expired locks at every period
            try {
                periodicLogger.debug("Deleting expired locks");
                deleteExpiredLocksJob.execute(transactionOperations);
            } catch (Throwable t) {
                periodicLogger.error("Error while deleting expired locks", t);
            }

            // Trash purging at certain hours of the day
            try {
                if (shouldRun(purgeTrashJob.lastRun(), trashCanPurgeHours)) {
                    periodicLogger.info("Executing trash purge");
                    purgeTrashJob.execute(transactionOperations);
                }
            } catch (Throwable t) {
                periodicLogger.error("Error while purging trash", t);
            }

            // Content revision GC at certain hours of the day
            try {
                if (shouldRun(revisionStoreGcJob.lastRun(), revisionGCHours)) {
                    periodicLogger.info("Executing revision store garbage collection.");
                    revisionStoreGcJob.execute(transactionOperations);
                }
            } catch (Throwable t) {
                periodicLogger.error("Error while running content revision garbage collection", t);
            }
        }

        // Determine if periodic job should be executed, when it is supposed to
        // be run once within certain hours of the day.
        private boolean shouldRun(Date lastRun, Set<Integer> runAtHours) {
            Calendar nowCal = Calendar.getInstance();

            if (runAtHours.contains(nowCal.get(Calendar.HOUR_OF_DAY))) {
                if (lastRun == null) {
                    return true;
                }

                Calendar lastRunCal = Calendar.getInstance();
                lastRunCal.setTime(lastRun);
                if (lastRunCal.get(Calendar.HOUR_OF_DAY) != nowCal.get(Calendar.HOUR_OF_DAY)) {
                    return true;
                }
                // Same hour of day, but maybe different day ..
                if (lastRunCal.get(Calendar.DAY_OF_MONTH) != nowCal.get(Calendar.DAY_OF_MONTH)) {
                    return true;
                }
            }

            return false;
        }

        private final class DeleteExpiredLocksJob {
            Date lastRun;

            void execute(TransactionOperations txWrapper) {
                txWrapper.execute(ts -> { dao.deleteExpiredLocks(new Date()); return null;});
                lastRun = new Date();
            }

            Date lastRun() {
                return this.lastRun;
            }
        }

        private final class RevisionStoreGcJob {
            Date lastRun;

            void execute(TransactionOperations txWrapper) throws IOException {
                txWrapper.execute(ts -> {

                    try {
                        revisionStore.gc();
                    } catch (IOException ex) {
                        throw new DataAccessException(ex);
                    }
                    return null;
                });

                lastRun = new Date();
            }

            Date lastRun() {
                return this.lastRun;
            }
        }

        /**
         * Permanently removes overdue and orphan resources from trash can. A
         * resource is overdue if it has been deleted (put in trash can) for
         * given configurable period of time. An orphan resource is a resource
         * that no longer has a parent (parent has been permanently deleted).
         */
        private final class PurgeTrashJob {
            private Date lastRun;

            void deleteRecoverable(RecoverableResource rr) {
                trashLogger.info("Permanently deleting recoverable resource: " + rr);
                dao.deleteRecoverable(rr);
                contentStore.deleteRecoverable(rr);
            }

            void execute(TransactionOperations txWrapper) {
                List<RecoverableResource> overdue = txWrapper.execute(ts -> dao.getTrashCanOverdue(permanentDeleteOverdueLimitInDays));
                if (! overdue.isEmpty()) {
                    trashLogger.info("Found " + overdue.size()
                            + " recoverable resources that are overdue for permanent deletion.");
                    for (final RecoverableResource rr : overdue) {
                        try {
                            txWrapper.execute(ts -> { deleteRecoverable(rr); return null; });
                        } catch (Exception e) {
                            trashLogger.warn("Failed to permanently delete recoverable resource " + rr, e);
                        }
                    }
                }
                
                List<RecoverableResource> orphans = txWrapper.execute(ts -> dao.getTrashCanOrphans());
                if (! orphans.isEmpty()) {
                    trashLogger.info("Found " + orphans.size() + " recoverable resources that are orphans.");
                    for (final RecoverableResource orphan : orphans) {
                        try {
                            txWrapper.execute(ts -> { deleteRecoverable(orphan); return null; });
                        } catch (Exception e) {
                            trashLogger.warn("Failed to permanently delete recoverable orphan " + orphan, e);
                        }
                    }
                }

                lastRun = new Date();
            }

            Date lastRun() {
                return this.lastRun;
            }
        }
    }
}
