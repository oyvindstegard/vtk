/* Copyright (c) 2004, 2007 University of Oslo, Norway
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
package vtk.security;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import vtk.util.cache.SimpleCache;
import vtk.util.cache.SimpleCacheImpl;


/**
 * A simple principal manager implementation, which is combines {@link PrincipalStore} and
 * {@link GroupStore} APIs and aggregates instances of those classes into a single entity.
 * 
 * Delegates API calls to a set of underlying {@link GroupStore} and {@link PrincipalStore} instances.
 * Caches aggregated group membership lookups.
 *
 */
public class PrincipalManagerImpl implements PrincipalManager, InitializingBean {
    
    private static Logger logger = LoggerFactory.getLogger(PrincipalManagerImpl.class);

    private PrincipalStore principalStore;
    private GroupStore groupStore;
    private int order = 0;
    
    public void setPrincipalStore(PrincipalStore principalStore) {
        if (principalStore == this) {
            throw new IllegalArgumentException("Cannot set self as aggregated principal store");
        }
        this.principalStore = principalStore;
    }

    public void setPrincipalStores(List<PrincipalStore> principalStores) {
        if (principalStores != null) {
            for (PrincipalStore s: principalStores) {
                if (this == s) {
                    throw new IllegalArgumentException("Found self in list of aggregated principal stores, which is not allowed");
                }
            }
            this.principalStore = new ChainedPrincipalStore(principalStores); 
        }
        logger.info("Initialized with principal stores: " + principalStores);
    }
    
    public void setGroupStores(List<GroupStore> groupStores) {
        if (groupStores != null) {
            for (GroupStore g: groupStores) {
                if (this == g) {
                    throw new IllegalArgumentException("Found self in list of aggregated group stores, which is not allowed");
                }
            }
            this.groupStore = new ChainedGroupStore(groupStores); 
        }

        logger.info("Initialized with group stores: " + groupStores);
    }    

    
    @Override
    public void afterPropertiesSet() throws Exception {
        if (this.principalStore == null) {
            throw new BeanInitializationException(
                "JavaBean Property 'principalStore' must be specified");
        }

        if (this.groupStore == null) {
            throw new BeanInitializationException(
                "JavaBean Property 'groupStore' must be specified");
        }
    }
    
    @Override
    public boolean validatePrincipal(Principal principal)
        throws AuthenticationProcessingException {
        return this.principalStore.validatePrincipal(principal);
    }


    @Override
    public boolean validateGroup(Principal group) throws AuthenticationProcessingException {
        return this.groupStore.validateGroup(group);
    }


    @Override
    public boolean isMember(Principal principal, Principal group) {
        return this.groupStore.isMember(principal, group);
    }


    @Override
    public Set<Principal> getMemberGroups(Principal principal) {
        return this.groupStore.getMemberGroups(principal);
    }

    @Override
    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    private static final class ChainedPrincipalStore implements PrincipalStore {

        private List<PrincipalStore> stores = null;

        ChainedPrincipalStore(List<PrincipalStore> stores) {
            this.stores = stores;
        }

        @Override
        public boolean validatePrincipal(Principal principal)
            throws AuthenticationProcessingException {
            
            for (PrincipalStore manager: this.stores) {
                if (manager.validatePrincipal(principal)) {
                    return true;
                }
            }
            
            return false;
        }

        @Override
        public int getOrder() {
            // XXX: DUMMY - not used, but should be refactored
            return 0;
        }

    }
    
    private static final class ChainedGroupStore implements GroupStore {
        private final List<GroupStore> stores;
        private final SimpleCache<Principal, Set<Principal>> groupMembershipCache;
        
        ChainedGroupStore(List<GroupStore> stores) {
            this.stores = stores;
            SimpleCacheImpl<Principal, Set<Principal>> cache = new SimpleCacheImpl<>(60);

            // Refresh cached groups periodically, regardless of user activity:
            cache.setRefreshTimestampOnGet(false);
            cache.afterPropertiesSet();
            
            this.groupMembershipCache = cache;
        }

        @Override
        public boolean validateGroup(Principal group) throws AuthenticationProcessingException {
            for (GroupStore store: this.stores) {
                if (store.validateGroup(group)) {
                    return true;
                }
            }
            
            return false;
        }

        @Override
        public boolean isMember(Principal principal, Principal group) {
            return getMemberGroups(principal).contains(group);
        }

        @Override
        public Set<Principal> getMemberGroups(Principal principal) {
            Set<Principal> memberGroups = this.groupMembershipCache.get(principal);
            if (memberGroups == null) {
                logger.debug("Groups for principal {} not in cache.", principal);
                
                memberGroups = new HashSet<>();
                for (GroupStore store : this.stores) {
                    Set<Principal> groups = store.getMemberGroups(principal);
                    if (groups != null) { // Extra precaution ..
                        memberGroups.addAll(groups);
                    }
                }

                // Make immutable
                memberGroups = Collections.unmodifiableSet(memberGroups);

                // Cache
                this.groupMembershipCache.put(principal, memberGroups);
            }

            return memberGroups;
        }

        @Override
        public int getOrder() {
            return 0;
        }
        
    }

}
