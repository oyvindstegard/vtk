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
package vtk.repository.store;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.PropertySet;
import vtk.repository.RecoverableResource;
import vtk.repository.ResourceImpl;
import vtk.security.Principal;

/**
 * No synchronization on URI namespace is done by this Cache (it has no LockManager).
 * It simply maintains the cache and flushes dirty entries on write operations.
 * Synchronization of operations in URI namespace should be done outside of
 * transaction context.
 * 
 * Very simple resource cache, wrapping around a {@link DataAccessor}. Resources
 * are cached when loaded (and stored). Resources are evicted in the following
 * cases:
 * 
 * <ul>
 * <ul>
 * the cache grows beyond its maximum size
 * <ul>
 * the resource itself is deleted
 * <ul>
 * the resource was locked and the lock has timed out
 * <ul>
 * a modification is made to one of its ancestors
 * <ul>
 * the resource's content is modified
 * </ul>
 * 
 * <p>
 * When the cache reaches its maximum size, a FIFO scheme is applied; a
 * configurable percentage of the items are removed, starting with the oldest
 * ones.
 * 
 * <p>
 * Configurable JavaBean properties:
 * <ul>
 * <li><code>wrappedAccessor</code> - the {@link DataAccessor} to act as a cache
 * for.
 * 
 * <li><code>maxItems</code> - an positive integer denoting the cache size. The
 * default value is <code>1000</code>.
 * 
 * <li><code>evictionRatio</code> - a number between 0 and 1 specifying the
 * number of items (as a percentage of <code>maxItems</code>) to remove when the
 * cache is filled up. The default value is <code>0.1</code> (10%).
 * 
 * <li><code>gatherStatistics</code> - a boolean specifying whether or not to
 * gather hit/miss statistics during operation. The default value is
 * <code>false</code>.
 * 
 * <li><code>loadChildrenSelectivelyThreshold</code> - Specifies threshold for
 * when children are loaded selectively from database, in
 * #loadChildren(ResourceImpl). If less than this percentage of children is
 * *missing* from cache, then *only* the missing children are loaded
 * invidiually, instead of loading absolutely all children from database
 * (regardless of their presence in the cache).
 * 
 * Example: If a resource has 1000 children, and 951 of those are currently
 * present in the cache, then 49 are missing. 49 is below the default threshold
 * of 5 percent, which will cause only the missing children to be loaded with
 * individual calls to the wrapped {@link DataAccessor#load(Path)}-method. If 51
 * were missing, then this would be above the threshold and all 1000 children
 * would be loaded by calling wrapped
 * {@link DataAccessor#loadChildren(ResourceImpl)}.
 * 
 * The number should be a float in the typical range of [0.01..0.1]. Don't set
 * it too high, as normally it's efficient to to load all children of a single
 * parent URI in one go. Only when the number of children gets high (in the
 * thousands) should selective loading of missing ones kick in. Default is 5
 * percent or 0.05.
 * </ul>
 */
public class Cache implements DataAccessor, InitializingBean {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private DataAccessor wrappedAccessor;
    private int maxItems = 1000;
    private double evictionRatio = 0.1;
    private int removeItems;
    private Items items = new Items(this.maxItems);

    private boolean gatherStatistics = false;
    private long hits = 0;
    private long misses = 0;

    private float loadChildrenSelectivelyThreshold = 0.05f;

    @Override
    public boolean validate() throws DataAccessException {
        return this.wrappedAccessor.validate();
    }

    public void setMaxItems(int maxItems) {
        if (maxItems <= 0) {
            throw new IllegalArgumentException("Cache size must be a positive number");
        }
        this.maxItems = maxItems;
        this.items = new Items(this.maxItems);
    }

    public void setEvictionRatio(double evictionRatio) {
        if (evictionRatio <= 0 || evictionRatio >= 1) {
            throw new IllegalArgumentException("JavaBean property 'evictionRatio' must be a "
                    + "number between 0 and 1");
        }
        this.evictionRatio = evictionRatio;
    }

    public void setLoadChildrenSelectivelyThreshold(float loadChildrenSelectivelyThreshold) {
        this.loadChildrenSelectivelyThreshold = loadChildrenSelectivelyThreshold;
    }

    @Required
    public void setWrappedAccessor(DataAccessor wrappedAccessor) {
        this.wrappedAccessor = wrappedAccessor;
    }

    public void setGatherStatistics(boolean gatherStatistics) {
        this.gatherStatistics = gatherStatistics;
    }

    public long getMisses() {
        return this.misses;
    }

    public long getHits() {
        return this.hits;
    }

    @Override
    public void afterPropertiesSet() {
        this.removeItems = (int) (this.maxItems * this.evictionRatio);

        if (this.removeItems == 0) {
            this.removeItems = 1;
        }
    }

    /**
     * Loads resource into cache (if it wasn't already there), returns resource.
     * 
     * @param uri
     *            a <code>String</code> value
     * @return a <code>Resource</code> value
     * @exception DataAccessException
     *                if an error occurs
     */
    @Override
    public ResourceImpl load(Path uri) throws DataAccessException {
        long start = System.currentTimeMillis();

        ResourceImpl r = this.items.get(uri);

        boolean davLockTimedOut = (r != null && r.getLock() != null && r.getLock().getTimeout().getTime() < System
                .currentTimeMillis());

        if (this.logger.isInfoEnabled() && davLockTimedOut) {
            this.logger.info("Dropping cached copy of " + uri + " (DAV lock timed out)");
        }

        if (r != null && !davLockTimedOut) {
            if (this.gatherStatistics) {
                updateStatistics(1, 0);
            }
            return r;
        }

        if (this.gatherStatistics) {
            updateStatistics(0, 1);
        }


        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Load from wrappedAccessor: " + uri);
        }

        r = this.wrappedAccessor.load(uri);

        if (r == null) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Not found in wrappedAccessor: " + uri);
            }

            return null;
        }

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Miss: " + uri);
        }

        enterResource(r);

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Load took " + (System.currentTimeMillis() - start) + " ms");
            this.logger.debug("Cache size: " + this.items.size());
        }

        return r;
    }
    
    @Override
    public ResourceImpl[] loadChildren(ResourceImpl parent) throws DataAccessException {

        final List<Path> childUris = parent.getChildURIs();
        final List<ResourceImpl> found = new ArrayList<ResourceImpl>();
        final List<Path> notFound = new ArrayList<Path>();

        for (Path uri : childUris) {

            ResourceImpl r = this.items.get(uri);
            boolean davlockTimedOut = (r != null && r.getLock() != null && r.getLock().getTimeout().getTime() < System
                    .currentTimeMillis());

            if (this.logger.isInfoEnabled() && davlockTimedOut) {
                this.logger.info("Dropping cached copy of " + uri + " (lock timed out)");
            }

            if (r == null || davlockTimedOut) {
                notFound.add(uri);
            } else {
                found.add(r);
            }
        }

        if (this.gatherStatistics) {
            updateStatistics(found.size(), notFound.size());
        }

        if (notFound.isEmpty()) {
            // Every child was found in cache, and none of them had expired
            // locks.
            return found.toArray(new ResourceImpl[found.size()]);
        }

        ResourceImpl[] resources = null;

        float notFoundRatio = (float) notFound.size() / childUris.size();

        if (notFoundRatio <= this.loadChildrenSelectivelyThreshold) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("loadChildren(): Less than " + (this.loadChildrenSelectivelyThreshold * 100)
                        + " percent of children missing from cache for URI '" + parent.getURI() + "', loading "
                        + notFound.size() + " missing or expired children individually.");
            }

            // Below threshold for number of missing children in cache, we
            // load the missing ones selectively from database for better
            // efficiency.
            List<ResourceImpl> loadedResources = new ArrayList<ResourceImpl>(notFound.size());
            for (Path missingChild : notFound) {
                ResourceImpl resourceImpl = this.wrappedAccessor.load(missingChild);
                if (resourceImpl != null) {
                    found.add(resourceImpl);
                    loadedResources.add(resourceImpl);
                }
            }
            
            synchronized (this.items) {
                for (ResourceImpl loaded: loadedResources) {
                    enterResource(loaded);
                }
            }

            return found.toArray(new ResourceImpl[found.size()]);

        } else {
            // Above threshold of children missing from cache or child list
            // too large for current cache size. Load everything in one go.
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("loadChildren(): Loading all children of URI '" + parent.getURI()
                        + "' from database, " + parent.getChildURIs().size() + " resources.");
            }


            // Treat entire child loading operation as atomic modification of cache,
            // synchronize on items immediately.
            resources = this.wrappedAccessor.loadChildren(parent);
            synchronized (this.items) {
                for (ResourceImpl resourceImpl : resources) {
                    enterResource(resourceImpl); // Put in cache, replace any existing (full refresh).
                }
            }
        }

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Cache size : " + this.items.size());
        }

        return resources;
    }


    @Override
    public ResourceImpl storeACL(ResourceImpl r) throws DataAccessException {
        ResourceImpl writtenResource = this.wrappedAccessor.storeACL(r); // Persist
        if (r.isCollection()) {
            this.items.remove(r.getURI(), true); // Purge resource and all descendants from cache (due to ACL inheritance)
        }

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("cache size : " + this.items.size());
        }
        
        enterResource(writtenResource);
        
        return writtenResource;
    }
    
    @Override
    public ResourceImpl storeLock(ResourceImpl r) throws DataAccessException {
        ResourceImpl writtenResource = this.wrappedAccessor.storeLock(r); // Persist
        // We don't do hierarchical locking:
//        if (r.isCollection()) {
//            this.items.remove(r.getURI(), true); 
//        }

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("cache size : " + this.items.size());
        }
        
        enterResource(writtenResource);
        
        return writtenResource;
    }

    @Override
    public ResourceImpl store(final ResourceImpl resource) throws DataAccessException {

        ResourceImpl writtenResource = this.wrappedAccessor.store(resource); // Persist
        
        enterResource(writtenResource);

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("cache size : " + this.items.size());
        }

        return writtenResource;
    }

    /**
     * Explicity purge a resource from cache by URI. Used by external cache
     * control code.
     * 
     * Optionally purges all descendants as well.
     * 
     * @param uri
     */
    public void flushFromCache(Path uri, boolean flushDescendants) {
        this.items.remove(uri, flushDescendants);
    }

    /**
     * Returns all currently cached descendant URIs.
     * 
     * @param uri
     * @return 
     */
    public List<Path> getCachedDescendantPaths(Path uri) {
        List<Path> paths = new ArrayList<Path>();
        
        for (Path cachedUri: this.items.uriSet()) {
            if (uri.isAncestorOf(cachedUri)) {
                paths.add(cachedUri);
            }
        }
        
        return paths;
    }

    @Override
    public ResourceImpl copy(ResourceImpl r, ResourceImpl destParent, PropertySet newResource, boolean copyACLs,
            PropertySet fixedProperties, Set<String> uncopyableProperties) throws DataAccessException {

        // Persist copy operation
        ResourceImpl writtenDestResource = this.wrappedAccessor.copy(
                r, destParent, newResource, copyACLs, fixedProperties, uncopyableProperties);

        // Purge affected destination parent from cache
        this.items.remove(destParent.getURI(), false);
        this.items.remove(newResource.getURI(), true);

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("cache size : " + this.items.size());
        }
        
        enterResource(writtenDestResource);

        return writtenDestResource;
    }

    @Override
    public ResourceImpl move(ResourceImpl r, ResourceImpl newResource) throws DataAccessException {

        ResourceImpl writtenDestResource = this.wrappedAccessor.move(r, newResource); // Persist move operation

        // Purge all affected items from cache
        if (newResource.getURI().getParent() != null) {
            this.items.remove(newResource.getURI().getParent(), false);
        }
        if (r.getURI().getParent() != null) {
            this.items.remove(r.getURI().getParent(), false);
        }
        // remove source+dest (all descendants, and we remove dest in case it was overwrite).
        this.items.remove(r.getURI(), true);
        this.items.remove(newResource.getURI(), true);


        if (this.logger.isDebugEnabled()) {
            this.logger.debug("cache size : " + this.items.size());
        }

        enterResource(writtenDestResource);
        
        return writtenDestResource;
    }

    @Override
    public void delete(ResourceImpl r) throws DataAccessException {
        this.performDelete(false, r, null, null, null);
    }

    @Override
    public void markDeleted(ResourceImpl resource, ResourceImpl parent, Principal principal, final String trashID)
            throws DataAccessException {
        this.performDelete(true, resource, parent, principal, trashID);
    }

    private void performDelete(boolean restorable, ResourceImpl resource, ResourceImpl parent, Principal principal,
            final String trashID) {

        // Dispatch to wrapped DAO for persistence
        if (restorable) {
            this.wrappedAccessor.markDeleted(resource, parent, principal, trashID);
        } else {
            this.wrappedAccessor.delete(resource);
        }

        this.items.remove(resource.getURI(), resource.isCollection());
        if (resource.getURI().getParent() != null) {
            this.items.remove(resource.getURI().getParent(), false);
        }

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("cache size : " + this.items.size());
        }

    }

    @Override
    public List<RecoverableResource> getRecoverableResources(final int parentResourceId) throws DataAccessException {
        return this.wrappedAccessor.getRecoverableResources(parentResourceId);
    }

    @Override
    public ResourceImpl recover(Path parent, RecoverableResource recoverableResource) {
        ResourceImpl writtenResource = this.wrappedAccessor.recover(parent, recoverableResource);
        
        enterResource(writtenResource);
        
        return writtenResource;
    }

    @Override
    public void deleteRecoverable(List<RecoverableResource> recoverableResources) throws DataAccessException {
        this.wrappedAccessor.deleteRecoverable(recoverableResources);
    }

    @Override
    public List<RecoverableResource> getTrashCanOverdue(int overDueLimit) throws DataAccessException {
        return this.wrappedAccessor.getTrashCanOverdue(overDueLimit);
    }

    @Override
    public List<RecoverableResource> getTrashCanOrphans() throws DataAccessException {
        return this.wrappedAccessor.getTrashCanOrphans();
    }

    @Override
    public void deleteExpiredLocks(Date expireDate) throws DataAccessException {
        this.wrappedAccessor.deleteExpiredLocks(expireDate);
    }

    @Override
    public Path[] discoverLocks(Path uri) throws DataAccessException {
        return this.wrappedAccessor.discoverLocks(uri);
    }

    /**
     * Clears all cache entries.
     * 
     */
    public void clear() {
        this.items.clear();
    }

    /**
     * Gets the number of resources currently in the cache.
     * 
     * @return the number of resources in the cache.
     */
    public int size() {
        return this.items.size();
    }

    /**
     * Note: this method is not thread safe, lock uri first
     * 
     * @param item a <code>Resource</code> value
     * 
     */
    private void enterResource(ResourceImpl resource) {
        synchronized (this.items) {
            if (this.items.size() > (this.maxItems - 1)) {
                long startTime = System.currentTimeMillis();
                this.items.removeOldItems(this.removeItems);
                long processingTime = System.currentTimeMillis() - startTime;
                if (this.logger.isInfoEnabled()) {
                    this.logger.info("Maximum cache size (" + this.maxItems + ") reached, removed "
                            + this.removeItems + " oldest items in " + processingTime + " ms");
                }
            }

            this.items.put(resource.getURI(), resource);
        }
    }

    private synchronized void updateStatistics(long hits, long misses) {
        if ((this.hits > (Long.MAX_VALUE - this.hits)) || (this.misses > (Long.MAX_VALUE) - misses)) {

            if (this.logger.isInfoEnabled()) {
                this.logger.info("Number of hits/misses too big, resetting counters");
            }

            this.hits = 0;
            this.misses = 0;
        }

        this.hits += hits;
        this.misses += misses;
    }

    public void dump(java.io.PrintStream out) {
        this.items.dump(out);
    }

    private static class Item {
        Item older = null;
        Item newer = null;
        final ResourceImpl resource;

        Item(ResourceImpl resource) {
            this.resource = resource;
        }
    }

    /**
     * Simple resource cache with non-blocking reads.
     * - All read operations are concurrent/non-blocking
     * - All write operations are completely synchronized/blocking.
     */
    private class Items {
        private final Map<Path, Item> map;
        private volatile int size = 0;
        private Item in = null; // Item eviction list head (newest item)
        private Item out = null;// Item eviction list tail (oldest item)
        
        public Items(int initialCapacity) {
            this.map = new ConcurrentHashMap<Path, Item>(initialCapacity);
        }

        public ResourceImpl get(Path uri) {
            final Item item = this.map.get(uri);

            if (item != null) {
                return item.resource;
            }

            return null;
        }

        public int size() {
            // The size() method of ConcurrentHashMap can be expensive. We can
            // however easily avoid it and track it externally, since all
            // modifying operations are synchronized.
            return this.size;
        }

        public Set<Path> uriSet() {
            return this.map.keySet();
        }

        public synchronized void put(Path uri, ResourceImpl resource) {
            final Item item = new Item(resource);

            final Item replaced = this.map.put(uri, item);
            if (replaced != null) {
                // New Path item will be reinserted at front
                removeFromEvictionQueue(replaced);
            } else {
                // Previously uncached URI, we grow.
                ++this.size;
            }

            addToEvictionQueue(item);
        }

        public synchronized void remove(final Path uri, boolean removeDescendants) {
            Item item = this.map.remove(uri);
            if (item != null) {
                // Something was actually removed
                --this.size;
                removeFromEvictionQueue(item);
            }
            
            if (removeDescendants) {
                for (Iterator<Map.Entry<Path,Item>> iter = this.map.entrySet().iterator(); iter.hasNext();) {
                    Map.Entry<Path,Item> entry = iter.next();
                    
                    if (uri.isAncestorOf(entry.getKey())) {
                        removeFromEvictionQueue(entry.getValue());
                        iter.remove();
                        --this.size;
                    }
                }
            }
        }

        public synchronized void removeOldItems(int n) {
            for (int i = 0; i < n; i++) {
                Item oldest = pollEvictionQueue();
                if (oldest == null) {
                    break;
                }
                if (Cache.this.logger.isDebugEnabled()) {
                    Cache.this.logger.debug("Removing old item " + oldest.resource.getURI());
                }
                if (this.map.remove(oldest.resource.getURI()) != null) {
                    --this.size;
                }
            }
        }
        
        public synchronized void clear() {
            this.map.clear();
            this.size = 0;
            this.in = this.out = null;
        }

        public void dump(java.io.PrintStream out) {
            for (Path uri: this.map.keySet()) {
                out.println(uri);
            }
        }

        // Put new item in eviction list head
        private void addToEvictionQueue(final Item item) {
            if (this.in != null) {
                this.in.newer = item;
            }

            item.older = this.in;
            this.in = item;

            if (this.out == null) {
                this.out = this.in;
            }
        }

        // Remove an item from eviction queue
        private void removeFromEvictionQueue(final Item item) {
            if (item.older != null) {
                item.older.newer = item.newer;
            } else {
                this.out = item.newer;
            }

            if (item.newer != null) {
                item.newer.older = item.older;
            } else {
                this.in = item.older;
            }
        }

        // Poll (return and remove) oldest item from eviction queue
        private Item pollEvictionQueue() {
            if (this.out != null) {
                Item polled = this.out;
                
                if (this.out != this.in) {
                    this.out.newer.older = null;
                    this.out = this.out.newer;
                } else {
                    this.out = this.in = null;
                }
                
                return polled;
            }
            
            return null;
        }
    }

    @Override
    public Path[] discoverACLs(Path uri) throws DataAccessException {
        return this.wrappedAccessor.discoverACLs(uri);
    }

    @Override
    public Set<Principal> discoverGroups() throws DataAccessException {
        return this.wrappedAccessor.discoverGroups();
    }

}
