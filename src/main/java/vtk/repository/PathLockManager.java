/* Copyright (c) 2011 University of Oslo, Norway
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Manager for locks on cache items (URIs) with support for shared or exclusive
 * access to paths (read and write locking).
 * 
 * There are two main "synchronization domains" in this code.
 * 1. Synchronization on individual lock objects, over which multiple threads
 *    contend for shared or exclusive access to paths.

 * 2. Synchronization on the map holding any locks currently in use and management
 *    of lock disposal. This synchronization is exclusively handled by the code
 *    managing the lock map (getLock(Path), returnLock(Lock) and unlockInternal(Path)).
 *
 * TODO return list of lock objects or a lock token instead of list of paths (tighten up API).
 *      Finally unlock(List<Path>) should only accept internal lock tokens/objects
 *      that client code cannot create, as parameter.
 */
public class PathLockManager {

    // How many seconds max to wait for each path lock requested if there is contention.
    private int lockTimeoutSeconds = 100;

    // Fair locking costs some through-put so false by default (should not be needed under normal circumstances)
    private boolean fairLocking = false;

    // All access to this map must be synchronized on the map object itself
    // There is room for improvement on how we handle lock disposal and synchronized access to this map, because
    // ReentrantReadWriteLock can be queried about queue size and hold count natively.
    private final Map<Path, PathLock> locks = new HashMap<Path, PathLock>();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Aquires lock on a single URI. Blocks until lock is
     * obtained, or throws an exception (leaving no lock) if it could not
     * be obtained.
     *
     * @param uri the URI to lock
     * @param exclusive if <code>true</code> then the lock will be exclusive (write lock), else it may be shared with other threads
     *        that do not required exclusive access to the same path.
     * @throws RuntimeException if URI could not be locked.
     */
    public List<Path> lock(Path uri, boolean exclusive) {
        return lockInternal(new Path[]{uri}, exclusive);
    }

    /**
     * Aquires locks for a list of URIs. Blocks until all locks are
     * obtained, or throws an exception (leaving no locks) if not all
     * the locks could be obtained. Duplicate paths in list are ignored, and
     * if  locking is successful, the returned list of locked paths will not
     * contain any duplicates.
     *
     * @param uris the list of URIs to lock
     * @param exclusive if <code>true</code> then the lock will be exclusive
     *        (write lock), else it may be shared with other threads
     *        that do not required exclusive access to the same path.
     * @throws RuntimeException if not all of the requested locks
     * could be obtained
     */
    public List<Path> lock(List<Path> uris, boolean exclusive) {

        // Do a shallow copy of URI list because we need to sort it
        // (not nice to directly modify input list).
        return lockInternal(uris.toArray(new Path[uris.size()]), exclusive);
    }

    /**
     * Releases locks on a list of URIs. Wakes up any threads waiting on
     * the locks.
     *
     * @param uris the URIs to unlock.
     * @param exclusive if the lock in question was claimed with exclusive access, then
     *                  it should also be unlocked with the same access.
     * @see #lock(vtk.repository.Path, boolean) 
     * 
     */
    public void unlock(List<Path> uris, boolean exclusive) {
        for (Path uri: uris) {
            unlockInternal(uri, exclusive);
        }
    }
    
    private List<Path> lockInternal(final Path[] uris, final boolean exclusive) {
        // Always try to lock a set of URIs in the same order to reduce chance of deadlocking.
        Arrays.sort(uris);
        
        final List<Path> claimedLocks = new ArrayList<Path>(uris.length);

        for (Path uri : uris) {
            final PathLock lock = getLock(uri);   // Request lock object for path
            if (lock.tryLock(this.lockTimeoutSeconds, TimeUnit.SECONDS, exclusive)) {
                claimedLocks.add(uri);
                if (this.logger.isDebugEnabled()) {
                    this.logger.debug("suceeded: locking " + uri);
                }
            } else {
                try {
                    if (this.logger.isDebugEnabled()) {
                        this.logger.debug("failed: locking " + uri + (exclusive ? " in exclusive mode" : " in shared mode")
                                + " due to interrupt or timeout (" + this.lockTimeoutSeconds + " seconds)");
                    }

                    throw new RuntimeException(
                            "Thread " + Thread.currentThread().getName()
                            + " giving up locking " + uri + (exclusive ? " in exclusive mode " : " in shared mode")
                            + " due to interrupt or timeout (" + this.lockTimeoutSeconds + " seconds)");
                } finally {
                    // Clean up, we failed.
                    // Return current lock, so it may be disposed of.
                    returnLock(lock);

                    // Release any locks we managed to claim as well.
                    unlock(claimedLocks, exclusive);
                }
            }
        }

        return Collections.unmodifiableList(claimedLocks);
    }
    
    private void unlockInternal(Path uri, boolean exclusive) {
        PathLock lock = null;
        synchronized (this.locks) {
            lock = this.locks.get(uri);
            if (lock == null) {
                throw new IllegalStateException("Thread "
                        + Thread.currentThread().getName()
                        + " tried to release lock on path '"
                        + uri + "', but there is currently no registered lock for that path.");
            }
            if (logger.isDebugEnabled()) {
                logger.debug("releasing " + uri);
            }

            returnLock(lock);
        }

        lock.unlock(exclusive); // Allow other threads waiting for this lock to proceed
    }
    
    /**
     * Gets a lock instance. The instance maps one to one to resource
     * URIs. This method itself never blocks for a long time.
     *
     * @param uri the URI for which to get the lock
     * @return the lock object corresponding to the URI
     */
    private PathLock getLock(Path uri) {
        PathLock lock = null;
        synchronized(this.locks) {
            lock = this.locks.get(uri);
            if (lock == null) {
                lock = new PathLock(uri);
                this.locks.put(uri, lock);
            }
            ++lock.useCount;
        }

        return lock;
    }

    /**
     * Returns a lock object (not the same as releasing the lock) so it may be
     * disposed of if no longer in use.
     * @param lock The lock object to return.
     */
    private void returnLock(PathLock lock) {
        synchronized (this.locks) {
            if (--lock.useCount <= 0) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Use count for lock on path '"
                            + lock.uri
                            + "' reached "
                            + lock.useCount + ", disposing it.");
                }
                this.locks.remove(lock.uri);
            }
        }
    }

    private final class PathLock {
        private final ReentrantReadWriteLock lockImpl;
        private final Path uri;
        int useCount = 0;
        
        PathLock(Path uri) {
            this.uri = uri;
            this.lockImpl = new ReentrantReadWriteLock(fairLocking);
        }
        
        boolean tryLock(long timeout, TimeUnit timeUnit, boolean exclusive) {
            try {
                if (exclusive) {
                    return this.lockImpl.writeLock().tryLock(timeout, timeUnit);
                } else {
                    return this.lockImpl.readLock().tryLock(timeout, timeUnit);
                }
            } catch (InterruptedException ie) {
                logger.warn("InterruptedException while waiting for lock on path " + this.uri, ie);
                return false;
            }
        }
        
        void unlock(boolean exclusive) {
            if (exclusive) {
                this.lockImpl.writeLock().unlock();
            } else {
                this.lockImpl.readLock().unlock();
            }
        }
        
        @Override
        public String toString() {
            return "Lock[URI = " + this.uri + (this.lockImpl.isWriteLocked() ? ", locked exclusively" : "") + "]";
        }
    }

    public void setLockTimeoutSeconds(int lockTimeoutSeconds) {
        this.lockTimeoutSeconds = lockTimeoutSeconds;
    }

    public void setFairLocking(boolean fairLocking) {
        this.fairLocking = fairLocking;
    }
}
