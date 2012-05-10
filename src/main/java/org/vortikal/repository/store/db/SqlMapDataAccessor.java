/* Copyright (c) 2006, 2007, University of Oslo, Norway
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
package org.vortikal.repository.store.db;

import java.io.ByteArrayInputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.orm.ibatis.SqlMapClientCallback;
import org.vortikal.repository.Acl;
import org.vortikal.repository.ContentStream;
import org.vortikal.repository.Lock;
import org.vortikal.repository.LockImpl;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.Path;
import org.vortikal.repository.Privilege;
import org.vortikal.repository.Property;
import org.vortikal.repository.PropertyImpl;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.PropertySetImpl;
import org.vortikal.repository.RecoverableResource;
import org.vortikal.repository.Repository.Depth;
import org.vortikal.repository.ResourceImpl;
import org.vortikal.repository.ResourceTypeTree;
import org.vortikal.repository.resourcetype.BinaryValue;
import org.vortikal.repository.resourcetype.PropertyType;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.repository.store.DataAccessException;
import org.vortikal.repository.store.DataAccessor;
import org.vortikal.repository.store.db.SqlDaoUtils.PropHolder;
import org.vortikal.security.Principal;
import org.vortikal.security.Principal.Type;
import org.vortikal.security.PrincipalFactory;

import com.ibatis.sqlmap.client.SqlMapExecutor;

/**
 * An iBATIS SQL maps implementation of the DataAccessor interface.
 * 
 * XXX XXX XXX Our DataAccessor interface declares our own DataAccessException type as thrown by all methods,
 *             but THIS CLASS IN PRACTICE MOSTLY THROWS Spring's DataAccessException. What a mess.
 * 
 */
public class SqlMapDataAccessor extends AbstractSqlMapDataAccessor implements DataAccessor {

    private PrincipalFactory principalFactory;
    private ResourceTypeTree resourceTypeTree;

    private Log logger = LogFactory.getLog(this.getClass());

    private boolean optimizedAclCopySupported = false;


    @Override
    public boolean validate() {
        throw new DataAccessException("Not implemented");
    }

    @Override
    public ResourceImpl load(Path uri) {
        ResourceImpl resource = loadResourceInternal(uri);
        if (resource == null) {
            return null;
        }
        
        loadInheritedProperties(new ResourceImpl[] { resource });
        loadACLs(new ResourceImpl[] { resource });

        if (resource.isCollection()) {
            loadChildUris(resource);
        }

        return resource;
    }

    /**
     * Loads everthing except:
     * - ACL
     * - Inherited properties.
     * 
     * @param uri
     * @return 
     */
    @SuppressWarnings("unchecked")
    private ResourceImpl loadResourceInternal(Path uri) {
        String sqlMap = getSqlMap("loadResourceByUri");
        Map<String, ?> resourceMap = (Map<String, ?>) getSqlMapClientTemplate().queryForObject(sqlMap, uri.toString());
        if (resourceMap == null) {
            return null;
        }
        ResourceImpl resource = new ResourceImpl(uri);

        Map<Path, Lock> locks = loadLocks(new Path[] { resource.getURI() });
        if (locks.containsKey(resource.getURI())) {
            resource.setLock(locks.get(resource.getURI()));
        }

        populateStandardProperties(resource, resourceMap);
        int resourceId = resource.getID();
        sqlMap = getSqlMap("loadPropertiesForResource");
        List<Map<String, Object>> propertyList = getSqlMapClientTemplate().queryForList(sqlMap, resourceId);
        populateCustomProperties(new ResourceImpl[] { resource }, propertyList);

        Integer aclInheritedFrom = (Integer) resourceMap.get("aclInheritedFrom");
        boolean aclInherited = aclInheritedFrom != null;
        resource.setInheritedAcl(aclInherited);
        resource.setAclInheritedFrom(aclInherited ? aclInheritedFrom.intValue() : PropertySetImpl.NULL_RESOURCE_ID);
        return resource;
    }

    @Override
    public void deleteExpiredLocks(Date d) {
        String sqlMap = getSqlMap("deleteExpiredLocks");
        getSqlMapClientTemplate().update(sqlMap, d);
    }

    @Override
    public Path[] discoverLocks(Path uri) {

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(uri, SQL_ESCAPE_CHAR));
        parameters.put("timestamp", new Date());

        String sqlMap = getSqlMap("discoverLocks");
        @SuppressWarnings("unchecked")
        List<String> list = getSqlMapClientTemplate().queryForList(sqlMap, parameters);

        Path[] locks = new Path[list.size()];
        for (int i = 0; i < list.size(); i++) {
            locks[i] = Path.fromString(list.get(i));
        }
        return locks;
    }

    @Override
    public ResourceImpl storeACL(ResourceImpl r) {
        updateACL(r);
        
        // Re-load and return newly written ResourceImpl
        return load(r.getURI());
    }

    @Override
    public ResourceImpl storeLock(ResourceImpl r) {

        // Delete any old persistent locks
        String sqlMap = getSqlMap("deleteLockByResourceId");
        getSqlMapClientTemplate().delete(sqlMap, r.getID());

        Lock lock = r.getLock();

        if (lock != null) {

            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("lockToken", lock.getLockToken());
            parameters.put("timeout", lock.getTimeout());
            parameters.put("owner", lock.getPrincipal().getQualifiedName());
            parameters.put("ownerInfo", lock.getOwnerInfo());
            parameters.put("depth", lock.getDepth().toString());
            parameters.put("resourceId", r.getID());

            sqlMap = getSqlMap("insertLock");
            getSqlMapClientTemplate().update(sqlMap, parameters);
        }
        return load(r.getURI());
    }

    private void updateACL(ResourceImpl r) {

        // XXX: ACL inheritance checking does not belong here!?
        boolean wasInherited = isInheritedAcl(r);
        if (wasInherited && r.isInheritedAcl()) {
            return;
        }

        if (wasInherited) {

            // ACL was inherited, new ACL is not inherited:
            int oldInheritedFrom = findNearestACL(r.getURI());
            insertAcl(r);

            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("resourceId", r.getID());
            parameters.put("inheritedFrom", null);

            String sqlMap = getSqlMap("updateAclInheritedFromByResourceId");
            getSqlMapClientTemplate().update(sqlMap, parameters);

            parameters = new HashMap<String, Object>();
            parameters.put("previouslyInheritedFrom", oldInheritedFrom);
            parameters.put("inheritedFrom", r.getID());
            parameters.put("uri", r.getURI().toString());
            parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(r.getURI(), SQL_ESCAPE_CHAR));

            sqlMap = getSqlMap("updateAclInheritedFromByPreviousInheritedFromAndUri");
            getSqlMapClientTemplate().update(sqlMap, parameters);
            return;
        }

        // ACL was not inherited
        // Delete previous ACL entries for resource:
        String sqlMap = getSqlMap("deleteAclEntriesByResourceId");
        getSqlMapClientTemplate().delete(sqlMap, r.getID());

        if (!r.isInheritedAcl()) {
            insertAcl(r);

        } else {

            // The new ACL is inherited, update pointers to the
            // previously "nearest" ACL node:
            int nearest = findNearestACL(r.getURI());

            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("inheritedFrom", nearest);
            parameters.put("resourceId", r.getID());
            parameters.put("previouslyInheritedFrom", r.getID());

            sqlMap = getSqlMap("updateAclInheritedFromByResourceIdOrPreviousInheritedFrom");
            getSqlMapClientTemplate().update(sqlMap, parameters);
        }
    }

    @Override
    public ResourceImpl store(ResourceImpl r) {
        String sqlMap = getSqlMap("loadResourceByUri");
        boolean existed = getSqlMapClientTemplate().queryForObject(sqlMap, r.getURI().toString()) != null;

        Map<String, Object> parameters = getResourceAsMap(r);
        if (!existed) {
            parameters.put("aclInheritedFrom", findNearestACL(r.getURI()));
        }
        parameters.put("depth", r.getURI().getDepth());

        sqlMap = existed ? getSqlMap("updateResource") : getSqlMap("insertResource");
        if (this.logger.isDebugEnabled()) {
            this.logger.debug((existed ? "Updating" : "Storing") + " resource " + r + ", parameter map: " + parameters);
        }

        getSqlMapClientTemplate().update(sqlMap, parameters);

        if (!existed) {
            sqlMap = getSqlMap("loadResourceIdByUri");
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) getSqlMapClientTemplate().queryForObject(sqlMap,
                    r.getURI().toString());
            Integer id = (Integer) map.get("resourceId");
            r.setID(id.intValue());
        }

        //storeLock(r);
        storeProperties(r);

        // Re-load and return newly written ResourceImpl
        return load(r.getURI());
    }

    @Override
    public void delete(ResourceImpl resource) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uri", resource.getURI().toString());
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(resource.getURI(), SQL_ESCAPE_CHAR));
        String sqlMap = getSqlMap("deleteResourceByUri");
        getSqlMapClientTemplate().update(sqlMap, parameters);
    }

    @Override
    public void markDeleted(ResourceImpl resource, ResourceImpl parent, Principal principal, final String trashID)
            throws DataAccessException {

        Path resourceURI = resource.getURI();
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uri", resourceURI.toString());
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(resourceURI, SQL_ESCAPE_CHAR));

        Path parentURI = resourceURI.getParent();

        int depthDiff = -1 * parentURI.getDepth();

        int uriTrimLength = parentURI.toString().length();
        if (!parentURI.isRoot()) {
            uriTrimLength++;
        }
        parameters.put("uriTrimLength", uriTrimLength);
        parameters.put("trashCanID", trashID);
        parameters.put("depthDiff", depthDiff);
        String sqlMap = getSqlMap("markDeleted");
        this.getSqlMapClientTemplate().update(sqlMap, parameters);

        parameters.put("trashCanURI", trashID + "/" + resourceURI.getName());
        parameters.put("parentID", parent.getID());
        parameters.put("principal", principal.getName());
        parameters.put("deletedTime", Calendar.getInstance().getTime());
        parameters.put("wasInheritedAcl", resource.isInheritedAcl() ? "Y" : "N");
        sqlMap = getSqlMap("insertTrashCanEntry");
        this.getSqlMapClientTemplate().update(sqlMap, parameters);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RecoverableResource> getRecoverableResources(final int parentResourceId) throws DataAccessException {
        String sqlMap = getSqlMap("getRecoverableResources");
        List<RecoverableResource> recoverableResources = this.getSqlMapClientTemplate().queryForList(sqlMap,
                parentResourceId);
        return recoverableResources;
    }

    @Override
    public ResourceImpl recover(Path parent, RecoverableResource recoverableResource) throws DataAccessException {

        int id = recoverableResource.getId();
        String sqlMap = getSqlMap("getRecoverableResourceById");
        Object o = getSqlMapClientTemplate().queryForObject(sqlMap, id);
        if (o == null) {
            throw new DataAccessException("Requested deleted object with id " + id + " was not found");
        }
        RecoverableResource deletedResource = (RecoverableResource) o;

        sqlMap = getSqlMap("deleteFromTrashCan");
        this.getSqlMapClientTemplate().delete(sqlMap, deletedResource.getId());

        Map<String, Object> parameters = new HashMap<String, Object>();
        String trashID = deletedResource.getTrashID();
        parameters.put("trashIDWildcard", SqlDaoUtils.getStringSqlWildcard(trashID, SQL_ESCAPE_CHAR));
        int uriTrimLength = trashID.length() + 1;
        if (parent.isRoot()) {
            uriTrimLength++;
        }

        int depthDiff = parent.getDepth();
        parameters.put("parentUri", parent.toString());
        parameters.put("depthDiff", depthDiff);
        parameters.put("uriTrimLength", uriTrimLength);

        sqlMap = getSqlMap("recoverResource");
        this.getSqlMapClientTemplate().update(sqlMap, parameters);

        Path recoverdResourcePath = parent.extend(deletedResource.getName());
        
        if (deletedResource.wasInheritedAcl()) {
            ResourceImpl recoveredResource = load(recoverdResourcePath);
            ResourceImpl parentResource = load(parent);
            Acl recoveredResourceAcl = recoveredResource.getAcl();
            Acl parentAcl = parentResource.getAcl();
            if (recoveredResourceAcl.equals(parentAcl)) {
                recoveredResource.setAclInheritedFrom(parentResource.getID());
                recoveredResource.setInheritedAcl(true);
                storeACL(recoveredResource);
            }
        }
        
        // Re-load and return newly written recovered resource
        return load(recoverdResourcePath);
    }

    @Override
    public void deleteRecoverable(RecoverableResource recoverableResource) throws DataAccessException {
        // XXX Lazy delete, #missing parent#
        String sqlMap = getSqlMap("deletePermanentlyMarkDeleted");
        Map<String, Object> parameters = new HashMap<String, Object>();
        String trashUri = recoverableResource.getTrashUri();
        parameters.put("trashCanURI", trashUri);
        parameters.put("trashCanURIWildCard", SqlDaoUtils.getStringSqlWildcard(trashUri, SQL_ESCAPE_CHAR));
        this.getSqlMapClientTemplate().delete(sqlMap, parameters);
        sqlMap = getSqlMap("deleteFromTrashCan");
        this.getSqlMapClientTemplate().delete(sqlMap, recoverableResource.getId());

    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RecoverableResource> getTrashCanOverdue(int overDueLimit) throws DataAccessException {
        Calendar cal = Calendar.getInstance();
        // Add negative limit -> substract
        cal.add(Calendar.DATE, -overDueLimit);
        Date overDueDate = cal.getTime();
        String sqlMap = getSqlMap("getOverdue");
        List<RecoverableResource> recoverableResources = this.getSqlMapClientTemplate().queryForList(sqlMap,
                overDueDate);
        return recoverableResources;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RecoverableResource> getTrashCanOrphans() throws DataAccessException {
        String sqlMap = getSqlMap("getOrphans");
        List<RecoverableResource> recoverableResources = this.getSqlMapClientTemplate().queryForList(sqlMap);
        return recoverableResources;
    }

    @Override
    public ResourceImpl[] loadChildren(ResourceImpl parent) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(parent.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("depth", parent.getURI().getDepth() + 1);

        List<ResourceImpl> children = new ArrayList<ResourceImpl>();
        String sqlMap = getSqlMap("loadChildren");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = getSqlMapClientTemplate().queryForList(sqlMap, parameters);
        Map<Path, Lock> locks = loadLocksForChildren(parent);
        for (Map<String, Object> resourceMap : resources) {
            Path uri = Path.fromString((String) resourceMap.get("uri"));

            ResourceImpl resource = new ResourceImpl(uri);

            populateStandardProperties(resource, resourceMap);

            if (locks.containsKey(uri)) {
                resource.setLock((LockImpl) locks.get(uri));
            }

            children.add(resource);
        }

        ResourceImpl[] result = children.toArray(new ResourceImpl[children.size()]);
        loadChildUrisForChildren(parent, result);
        loadInheritedProperties(result);
        loadACLs(result);
        loadPropertiesForChildren(parent, result);

        return result;
    }

    @Override
    public Path[] discoverACLs(Path uri) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uri", uri.toString());        
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(uri, SQL_ESCAPE_CHAR));

        String sqlMap = getSqlMap("discoverAcls");
        @SuppressWarnings("unchecked")
        List<Path> uris = getSqlMapClientTemplate().queryForList(sqlMap, parameters);

        return uris.toArray(new Path[uris.size()]);
    }

    private void supplyFixedProperties(Map<String, Object> parameters, PropertySet fixedProperties) {
        List<Property> propertyList = fixedProperties.getProperties(Namespace.DEFAULT_NAMESPACE);
        for (Property property : propertyList) {
            if (PropertyType.SPECIAL_PROPERTIES_SET.contains(property.getDefinition().getName())) {
                Object value = property.getValue().getObjectValue();
                if (property.getValue().getType() == PropertyType.Type.PRINCIPAL) {
                    value = ((Principal) value).getQualifiedName();
                }
                if (this.logger.isDebugEnabled()) {
                    this.logger.debug("Copy: fixed property: " + property.getDefinition().getName() + ": " + value);
                }
                parameters.put(property.getDefinition().getName(), value);
            }
        }
    }

    @Override
    public ResourceImpl copy(ResourceImpl resource, ResourceImpl destParent, PropertySet newResource, boolean copyACLs,
            PropertySet fixedProperties) {

        Path destURI = newResource.getURI();
        int depthDiff = destURI.getDepth() - resource.getURI().getDepth();

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("srcUri", resource.getURI().toString());
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(resource.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("destUri", destURI.toString());
        parameters.put("destUriWildcard", SqlDaoUtils.getUriSqlWildcard(destURI, SQL_ESCAPE_CHAR));
        parameters.put("depthDiff", depthDiff);

        if (fixedProperties != null) {
            supplyFixedProperties(parameters, fixedProperties);
        }

        String sqlMap = getSqlMap("copyResource");
        getSqlMapClientTemplate().update(sqlMap, parameters);

        sqlMap = getSqlMap("copyProperties");
        getSqlMapClientTemplate().update(sqlMap, parameters);

        if (copyACLs) {

            sqlMap = getSqlMap("copyAclEntries");
            getSqlMapClientTemplate().update(sqlMap, parameters);

            // Update inheritance to nearest node:
            int srcNearestACL = findNearestACL(resource.getURI());
            int destNearestACL = findNearestACL(destURI);

            parameters = new HashMap<String, Object>();
            parameters.put("uri", destURI.toString());
            parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(destURI, SQL_ESCAPE_CHAR));
            parameters.put("inheritedFrom", destNearestACL);
            parameters.put("previouslyInheritedFrom", srcNearestACL);

            sqlMap = getSqlMap("updateAclInheritedFromByPreviousInheritedFromAndUri");
            getSqlMapClientTemplate().update(sqlMap, parameters);

            if (this.optimizedAclCopySupported) {
                sqlMap = getSqlMap("updateAclInheritedFromByPreviousResourceId");
                getSqlMapClientTemplate().update(sqlMap, parameters);
            } else {
                sqlMap = getSqlMap("loadPreviousInheritedFromMap");

                @SuppressWarnings("unchecked")
                final List<Map<String, Object>> list = getSqlMapClientTemplate().queryForList(sqlMap, parameters);
                final String batchSqlMap = getSqlMap("updateAclInheritedFromByResourceId");

                getSqlMapClientTemplate().execute(new SqlMapClientCallback() {
                    @Override
                    public Object doInSqlMapClient(SqlMapExecutor executor) throws SQLException {
                        executor.startBatch();
                        for (Map<String, Object> map : list) {
                            executor.update(batchSqlMap, map);
                        }
                        executor.executeBatch();
                        return null;
                    }
                });
            }

        } else {
            int nearestAclNode = findNearestACL(destURI);
            parameters = new HashMap<String, Object>();
            parameters.put("uri", destURI.toString());
            parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(destURI, SQL_ESCAPE_CHAR));
            parameters.put("inheritedFrom", nearestAclNode);

            sqlMap = getSqlMap("updateAclInheritedFromByUri");
            getSqlMapClientTemplate().update(sqlMap, parameters);
        }

        parameters = new HashMap<String, Object>();
        parameters.put("uri", destURI.toString());
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(destURI, SQL_ESCAPE_CHAR));
        sqlMap = getSqlMap("clearPrevResourceIdByUri");
        getSqlMapClientTemplate().update(sqlMap, parameters);

        parameters = getResourceAsMap(destParent);
        sqlMap = getSqlMap("updateResource");
        getSqlMapClientTemplate().update(sqlMap, parameters);
        storeProperties(destParent);

        ResourceImpl created = loadResourceInternal(newResource.getURI());
        for (Property prop : newResource) {
            created.addProperty(prop);
            Property fixedProp = fixedProperties != null ? fixedProperties.getProperty(prop.getDefinition()
                    .getNamespace(), prop.getDefinition().getName()) : null;
            if (fixedProp != null) {
                created.addProperty(fixedProp);
            }
        }

        storeProperties(created);

        // Remove uncopyable properties
        // @see PropertyType.UNCOPYABLE_PROPERTIES
        removeUncopyableProperties(created);

        // Re-load and return newly written destination ResourceImpl
        return load(newResource.getURI());
    }

    private void removeUncopyableProperties(ResourceImpl r) {
        final String destUri = r.getURI().toString();
        final String uriWildcard = SqlDaoUtils.getUriSqlWildcard(r.getURI(), SQL_ESCAPE_CHAR);
        final String batchSqlMap = getSqlMap("deleteUncopyableProperties");
        getSqlMapClientTemplate().execute(new SqlMapClientCallback() {
            @Override
            public Object doInSqlMapClient(SqlMapExecutor executor) throws SQLException {
                executor.startBatch();
                for (String propertyName : PropertyType.UNCOPYABLE_PROPERTIES) {
                    Map<String, Object> params = new HashMap<String, Object>();
                    params.put("destUri", destUri);
                    params.put("uriWildcard", uriWildcard);
                    params.put("name", propertyName);
                    executor.delete(batchSqlMap, params);
                }
                executor.executeBatch();
                return null;
            }
        });
    }

    @Override
    public ResourceImpl move(ResourceImpl resource, ResourceImpl newResource) {
        Path destURI = newResource.getURI();
        int depthDiff = destURI.getDepth() - resource.getURI().getDepth();

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("srcUri", resource.getURI().toString());
        parameters.put("destUri", newResource.getURI().toString());
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(resource.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("depthDiff", depthDiff);

        String sqlMap = getSqlMap("moveResource");
        getSqlMapClientTemplate().update(sqlMap, parameters);

        sqlMap = getSqlMap("moveDescendants");
        getSqlMapClientTemplate().update(sqlMap, parameters);

        ResourceImpl created = loadResourceInternal(newResource.getURI());
        for (Property prop : newResource) {
            created.addProperty(prop);
        }
        sqlMap = getSqlMap("updateResource");
        parameters = getResourceAsMap(newResource);
        getSqlMapClientTemplate().update(sqlMap, parameters);

        storeProperties(created);

        if (newResource.isInheritedAcl()) {
            int srcNearestAcl = findNearestACL(resource.getURI());
            int nearestAclNode = findNearestACL(newResource.getURI());
            parameters = new HashMap<String, Object>();
            parameters.put("uri", newResource.getURI().toString());
            parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(newResource.getURI(), SQL_ESCAPE_CHAR));
            parameters.put("inheritedFrom", nearestAclNode);
            parameters.put("previouslyInheritedFrom", srcNearestAcl);

            sqlMap = getSqlMap("updateAclInheritedFromByPreviousInheritedFromAndUri");
            getSqlMapClientTemplate().update(sqlMap, parameters);
        }

        // Re-load and return newly written destination ResourceImpl
        return load(newResource.getURI());
    }

    private void loadChildUris(ResourceImpl parent) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(parent.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("depth", parent.getURI().getDepth() + 1);

        String sqlMap = getSqlMap("loadChildUrisForChildren");

        @SuppressWarnings("unchecked")
        List<Path> resourceUriList = getSqlMapClientTemplate().queryForList(sqlMap, parameters);

        parent.setChildURIs(resourceUriList);
    }

    private void loadChildUrisForChildren(ResourceImpl parent, ResourceImpl[] children) {

        // Initialize a map from child collection URI to the list of
        // grandchildren's URIs:
        Map<Path, List<Path>> childMap = new HashMap<Path, List<Path>>();
        for (ResourceImpl child : children) {
            if (child.isCollection()) {
                childMap.put(child.getURI(), new ArrayList<Path>());
            }
        }

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(parent.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("depth", parent.getURI().getDepth() + 2);

        String sqlMap = getSqlMap("loadChildUrisForChildren");

        @SuppressWarnings("unchecked")
        List<Path> resourceUris = getSqlMapClientTemplate().queryForList(sqlMap, parameters);

        for (Path uri : resourceUris) {
            Path parentUri = uri.getParent();
            if (parentUri != null) {
                List<Path> childUriList = childMap.get(parentUri);
                // Again, watch for children added in database while this
                // transaction is ongoing
                // (child map is populated before doing database query).
                if (childUriList != null) {
                    childUriList.add(uri);
                }
            }
        }

        for (ResourceImpl child : children) {
            if (!child.isCollection())
                continue;

            List<Path> childURIs = childMap.get(child.getURI());
            child.setChildURIs(childURIs);
        }
    }

    private void loadPropertiesForChildren(ResourceImpl parent, ResourceImpl[] resources) {
        if ((resources == null) || (resources.length == 0)) {
            return;
        }

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(parent.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("depth", parent.getURI().getDepth() + 1);

        String sqlMap = getSqlMap("loadPropertiesForChildren");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> propertyList = getSqlMapClientTemplate().queryForList(sqlMap, parameters);

        populateCustomProperties(resources, propertyList);
    }

    private Map<Path, Lock> loadLocks(Path[] uris) {
        if (uris.length == 0)
            return new HashMap<Path, Lock>();
        Map<String, Object> parameters = new HashMap<String, Object>();
        List<String> uriList = new ArrayList<String>();
        for (Path p : uris)
            uriList.add(p.toString());
        parameters.put("uris", uriList);
        parameters.put("timestamp", new Date());
        String sqlMap = getSqlMap("loadLocksByUris");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> locks = getSqlMapClientTemplate().queryForList(sqlMap, parameters);

        Map<Path, Lock> result = new HashMap<Path, Lock>();

        for (Map<String, Object> map : locks) {
            LockImpl lock = new LockImpl((String) map.get("token"), principalFactory.getPrincipal((String) map
                    .get("owner"), Principal.Type.USER), (String) map.get("ownerInfo"), Depth.fromString((String) map
                    .get("depth")), (Date) map.get("timeout"));

            result.put(Path.fromString((String) map.get("uri")), lock);
        }
        return result;
    }

    private Map<Path, Lock> loadLocksForChildren(ResourceImpl parent) {

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("timestamp", new Date());
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(parent.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("depth", parent.getURI().getDepth() + 1);

        String sqlMap = getSqlMap("loadLocksForChildren");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> locks = getSqlMapClientTemplate().queryForList(sqlMap, parameters);

        Map<Path, Lock> result = new HashMap<Path, Lock>();

        for (Iterator<Map<String, Object>> i = locks.iterator(); i.hasNext();) {
            Map<String, Object> map = i.next();
            LockImpl lock = new LockImpl((String) map.get("token"), principalFactory.getPrincipal((String) map
                    .get("owner"), Principal.Type.USER), (String) map.get("ownerInfo"), Depth.fromString((String) map
                    .get("depth")), (Date) map.get("timeout"));

            result.put(Path.fromString((String) map.get("uri")), lock);
        }
        return result;
    }

    private void insertAcl(final ResourceImpl r) {
        final Map<String, Integer> actionTypes = loadActionTypes();
        final Acl newAcl = r.getAcl();
        if (newAcl == null) {
            throw new DataAccessException("Resource " + r + " has no ACL");
        }
        final Set<Privilege> actions = newAcl.getActions();
        final String sqlMap = getSqlMap("insertAclEntry");

        getSqlMapClientTemplate().execute(new SqlMapClientCallback() {
            @Override
            public Object doInSqlMapClient(SqlMapExecutor executor) throws SQLException {
                executor.startBatch();
                Map<String, Object> parameters = new HashMap<String, Object>();
                for (Privilege action : actions) {
                    String actionName = action.getName();
                    for (Principal p : newAcl.getPrincipalSet(action)) {

                        Integer actionID = actionTypes.get(actionName);
                        if (actionID == null) {
                            throw new SQLException("insertAcl(): Unable to " + "find id for action '" + action + "'");
                        }

                        parameters.put("actionId", actionID);
                        parameters.put("resourceId", r.getID());
                        parameters.put("principal", p.getQualifiedName());
                        parameters.put("isUser", p.getType() == Principal.Type.GROUP ? "N" : "Y");
                        parameters.put("grantedBy", r.getOwner().getQualifiedName());
                        parameters.put("grantedDate", new Date());

                        executor.update(sqlMap, parameters);
                    }
                }
                executor.executeBatch();
                return null;
            }
        });
    }

    private Map<String, Integer> loadActionTypes() {
        Map<String, Integer> actionTypes = new HashMap<String, Integer>();

        String sqlMap = getSqlMap("loadActionTypes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = getSqlMapClientTemplate().queryForList(sqlMap, null);
        for (Map<String, Object> map : list) {
            actionTypes.put((String) map.get("name"), (Integer) map.get("id"));
        }
        return actionTypes;
    }

    private boolean isInheritedAcl(ResourceImpl r) {

        String sqlMap = getSqlMap("isInheritedAcl");
        @SuppressWarnings("unchecked")
        Map<String, Integer> map = (Map<String, Integer>) getSqlMapClientTemplate().queryForObject(sqlMap, r.getID());

        Integer inheritedFrom = map.get("inheritedFrom");
        return inheritedFrom != null;
    }

    private int findNearestACL(Path uri) {

        List<Path> path = uri.getPaths();

        // Reverse list to get deepest URI first
        Collections.reverse(path);

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("path", path);
        String sqlMap = getSqlMap("findNearestAclResourceId");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = getSqlMapClientTemplate().queryForList(sqlMap, parameters);

        Map<String, Integer> uris = new HashMap<String, Integer>();
        for (Map<String, Object> map : list) {
            uris.put((String) map.get("uri"), (Integer) map.get("resourceId"));
        }

        int nearestResourceId = -1;
        for (Path p : path) {
            String candidateUri = p.toString();
            if (uris.containsKey(candidateUri)) {
                nearestResourceId = uris.get(candidateUri).intValue();
                break;
            }
        }
        if (nearestResourceId == -1) {
            throw new DataAccessException("Database inconsistency: no acl to inherit " + "from for resource " + uri);
        }
        return nearestResourceId;
    }

    
    
    private int inheritedPropertiesBatch = 200;
    
    /**
     * Loads and populates only properties inherited from ancestors
     * for all resources. Does not overwrite existing properties, allowing
     * inheritable properties set directly on resources to override inherited ones.
     * 
     * @param resources 
     */
    private void loadInheritedProperties(ResourceImpl[] resources) {
        if (resources.length == 0) {
            return;
        }
        List<Map<String, Object>> propertyRows = new ArrayList<Map<String, Object>>();
        String sqlMap = getSqlMap("loadInheritableProperties");
        Map<String, Object> parameterMap = new HashMap<String, Object>();
        
        Set<Path> handled = new HashSet<Path>();
        Set<Path> paths = new HashSet<Path>();
        // Load inheritable properties from all ancestors
        for (int i = 0; i < resources.length; i++) {
            Path parent = resources[i].getURI().getParent();
            if (parent != null) {
                for (Path p : parent.getPaths()) {
                    if (handled.add(p)) {
                        paths.add(p);
                    }
                }
            }
            if ((i == resources.length - 1 || i % inheritedPropertiesBatch == 0) && !paths.isEmpty()) {
                parameterMap.put("uris", new ArrayList<Path>(paths));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rows = getSqlMapClientTemplate().queryForList(sqlMap, parameterMap);
                propertyRows.addAll(rows);
                paths.clear();
            }
        }

        // Map of all ancestor paths that have inheritable props
        final Map<Path, List<PropHolder>> inheritableMap = new HashMap<Path, List<PropHolder>>();
        
        final Map<PropHolder, List<Object>> propValuesMap = new HashMap<PropHolder, List<Object>>();
        for (Map<String, Object> propEntry : propertyRows) {
            PropHolder propHolder = new PropHolder();
            propHolder.propID = propEntry.get("id");
            propHolder.namespaceUri = (String) propEntry.get("namespaceUri");
            propHolder.name = (String) propEntry.get("name");
            propHolder.resourceId = (Integer) propEntry.get("resourceId");
            propHolder.binary = (Boolean) propEntry.get("binary");
            propHolder.inheritable = true;
            List<Object> values = propValuesMap.get(propHolder);
            if (values == null) {
                // New Property
                values = new ArrayList<Object>(2);
                propHolder.values = values;
                propValuesMap.put(propHolder, values);

                // Populate inheritables map with canonical PropHolder instance
                Path uri = Path.fromString((String) propEntry.get("uri"));
                List<PropHolder> holderList = inheritableMap.get(uri);
                if (holderList == null) {
                    holderList = new ArrayList<PropHolder>();
                    inheritableMap.put(uri, holderList);
                }
                holderList.add(propHolder);
            }
            
            // Aggregate value
            if (propHolder.binary) {
                values.add(propHolder.propID);
            } else {
                values.add(propEntry.get("value"));
            }
        }
        
        final Set<String> encountered = new HashSet<String>();
        for (ResourceImpl r : resources) {
            Path parent = r.getURI().getParent();
            if (parent == null) {
                continue; // root resource cannot inherit anything
            }
            
            List<Path> pathList = parent.getPaths();
            for (int i = pathList.size() - 1; i >= 0; i--) {
                Path p = pathList.get(i);
                List<PropHolder> holderList = inheritableMap.get(p);
                if (holderList != null) {
                    for (PropHolder h : holderList) {
                        if (encountered.add(h.namespaceUri + ":" + h.name)) {
                            // Add as inherited if property is not directly set on the resource:
                            Property prop = createInheritedProperty(h);
                            if (r.getProperty(prop.getDefinition()) == null) {
                                r.addProperty(prop);
                            }
                        }
                    }
                }
            }
            encountered.clear();
        }

//        for (ResourceImpl r: resources) {
//            Map<String, Map<String, PropHolder>> effectiveProps = new HashMap<String, Map<String, PropHolder>>();
//            for (Path uri: r.getURI().getPaths()) {
//                Set<PropHolder> set = inheritanceMap.get(uri);
//                if (set != null) {
//                    for (PropHolder prop: set) {
//                        Map<String, PropHolder> nameMap = effectiveProps.get(prop.namespaceUri);
//                        if (nameMap == null) {
//                            nameMap = new HashMap<String, PropHolder>();
//                            effectiveProps.put(prop.namespaceUri, nameMap);
//                        }
//                        nameMap.put(prop.name, prop);
//                    }
//                }
//            }
//            for (String namespaceUri: effectiveProps.keySet()) {
//                Map<String, PropHolder> nameMap = effectiveProps.get(namespaceUri);
//                for (String name: nameMap.keySet()) {
//                    PropHolder prop = nameMap.get(name);
//                    if (r.getID() == prop.resourceId) {
//                        r.addProperty(createProperty(prop));
//                    } else {
//                        r.addProperty(createInheritedProperty(prop));
//                    }
//                }
//            }
//        }
    }
    
    
    
    private void loadACLs(ResourceImpl[] resources) {

        if (resources.length == 0) {
            return;
        }
        Set<Integer> resourceIds = new HashSet<Integer>();
        for (int i = 0; i < resources.length; i++) {

            int id = resources[i].isInheritedAcl() ? resources[i].getAclInheritedFrom() : resources[i].getID();

            resourceIds.add(id);
        }
        Map<Integer, AclHolder> map = loadAclMap(new ArrayList<Integer>(resourceIds));

        for (ResourceImpl resource : resources) {
            AclHolder aclHolder = null;

            if (resource.getAclInheritedFrom() != -1) {
                aclHolder = map.get(resource.getAclInheritedFrom());
            } else {
                aclHolder = map.get(resource.getID());
            }

            if (aclHolder == null) {
                resource.setAcl(Acl.EMPTY_ACL);
            } else {
                resource.setAcl(new Acl(aclHolder));
            }
        }
    }

    private Map<Integer, AclHolder> loadAclMap(List<Integer> resourceIds) {

        Map<Integer, AclHolder> resultMap = new HashMap<Integer, AclHolder>();
        if (resourceIds.isEmpty()) {
            return resultMap;
        }
        int batchSize = 500;
        int total = resourceIds.size();

        List<Integer> subList;

        int start = 0;
        int end = Math.min(batchSize, total);

        while (end <= total && start < end) {
            subList = resourceIds.subList(start, end);

            loadAclBatch(subList, resultMap);

            start += batchSize;
            end = Math.min(end + batchSize, total);
        }
        return resultMap;
    }

    private void loadAclBatch(List<Integer> resourceIds, Map<Integer, AclHolder> resultMap) {
        Map<String, Object> parameterMap = new HashMap<String, Object>();
        parameterMap.put("resourceIds", resourceIds);

        String sqlMap = getSqlMap("loadAclEntriesByResourceIds");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> aclEntries = getSqlMapClientTemplate().queryForList(sqlMap, parameterMap);

        for (Map<String, Object> map : aclEntries) {

            Integer resourceId = (Integer) map.get("resourceId");
            String privilege = (String) map.get("action");

            AclHolder acl = resultMap.get(resourceId);

            if (acl == null) {
                acl = new AclHolder();
                resultMap.put(resourceId, acl);
            }

            boolean isGroup = "N".equals(map.get("isUser"));
            String name = (String) map.get("principal");
            Principal p = null;

            if (isGroup) {
                p = principalFactory.getPrincipal(name, Type.GROUP);
            } else if (name.startsWith("pseudo:")) {
                p = principalFactory.getPrincipal(name, Type.PSEUDO);
            } else {
                p = principalFactory.getPrincipal(name, Type.USER);
            }
            Privilege action = Privilege.forName(privilege);
            
            acl.addEntry(action, p);
        }
    }

    private void storeProperties(final ResourceImpl r) {
        
        for (Property p : r) {
            if (p.getType() == PropertyType.Type.BINARY) {
                // XXX: mem copying has to be done because of the way properties
                // are stored: first deleted then inserted (never updated)
                // If any binary value is of type BinaryValueReference (created only by this class)
                // then DataAccessException will be thrown if the reference is STALE.
                ensureBinaryValueBuffered(p);
            }
        }
        
        String sqlMap = getSqlMap("deletePropertiesByResourceId");
        getSqlMapClientTemplate().update(sqlMap, r.getID());

        final String batchSqlMap = getSqlMap("insertPropertyEntry");

        getSqlMapClientTemplate().execute(new SqlMapClientCallback() {

            @Override
            public Object doInSqlMapClient(SqlMapExecutor executor) throws SQLException {
                executor.startBatch();
                Map<String, Object> parameters = new HashMap<String, Object>();
                for (Property property : r) {
                    if (!PropertyType.SPECIAL_PROPERTIES_SET.contains(property.getDefinition().getName())) {
                        parameters.put("namespaceUri", property.getDefinition().getNamespace().getUri());
                        parameters.put("name", property.getDefinition().getName());
                        parameters.put("resourceId", r.getID());
                        parameters.put("inheritable", property.getDefinition().isInheritable());

                        Value[] values;
                        if (property.getDefinition() != null && property.getDefinition().isMultiple()) {
                            values = property.getValues();
                        } else {
                            values = new Value[]{property.getValue()};
                        }

                        for (Value v : values) {
                            parameters.put("value", v.getNativeStringRepresentation());
                            if (property.getType() == PropertyType.Type.BINARY) {
                                parameters.put("binaryContent", v.getBinaryValue().getBytes());
                                parameters.put("binaryMimeType", v.getBinaryValue().getContentType());
                            }
                            executor.update(batchSqlMap, parameters);
                        }
                    }
                    parameters.clear();
                }
                executor.executeBatch();
                return null;
            }
        });
    }
    
    private void populateCustomProperties(ResourceImpl[] resources,
            List<Map<String, Object>> propertyRows) {

        Map<Integer, ResourceImpl> resourceMap =
                new HashMap<Integer, ResourceImpl>(resources.length+1, 1f);
        for (ResourceImpl resource : resources) {
            resourceMap.put(resource.getID(), resource);
        }

        Map<PropHolder, List<Object>> propValuesMap = new HashMap<PropHolder, List<Object>>();

        for (Map<String, Object> propEntry : propertyRows) {
            PropHolder prop = new PropHolder();
            prop.propID = propEntry.get("id");
            prop.namespaceUri = (String) propEntry.get("namespaceUri");
            prop.name = (String) propEntry.get("name");
            prop.resourceId = (Integer) propEntry.get("resourceId");
            prop.binary = (Boolean)propEntry.get("binary");
            
            List<Object> values = propValuesMap.get(prop);
            if (values == null) {
                values = new ArrayList<Object>(2); // Most props have only one value
                prop.values = values;
                propValuesMap.put(prop, values);
            }
            if (prop.binary) {
                values.add(prop.propID);
            } else {
                values.add(propEntry.get("value"));
            }
        }

        for (PropHolder propHolder : propValuesMap.keySet()) {

            ResourceImpl r = resourceMap.get(propHolder.resourceId);

            if (r == null) {
                // A property was loaded for a resource that was committed to
                // database after we loaded
                // the initial set of children in loadChildren. This is normal
                // because of default
                // READ COMITTED tx isolation level. We simply skip the property
                // here ..
                continue;
            }
            
            r.addProperty(createProperty(propHolder));
        }
    }

    public void populateStandardProperties(ResourceImpl resourceImpl, Map<String, ?> resourceMap) {

        resourceImpl.setID(((Number) resourceMap.get("id")).intValue());

        boolean collection = "Y".equals(resourceMap.get("isCollection"));
        resourceImpl.addProperty(createProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.COLLECTION_PROP_NAME, Boolean
                .valueOf(collection)));

        Principal createdBy = principalFactory.getPrincipal((String) resourceMap.get("createdBy"), Principal.Type.USER);
        resourceImpl.addProperty(createProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.CREATEDBY_PROP_NAME,
                createdBy));

        resourceImpl.addProperty(createProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.CREATIONTIME_PROP_NAME,
                resourceMap.get("creationTime")));

        Principal principal = principalFactory.getPrincipal((String) resourceMap.get("owner"), Principal.Type.USER);
        resourceImpl.addProperty(createProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.OWNER_PROP_NAME, principal));

        String string = (String) resourceMap.get("contentType");
        if (string != null) {
            resourceImpl.addProperty(createProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.CONTENTTYPE_PROP_NAME,
                    string));
        }

        string = (String) resourceMap.get("characterEncoding");
        if (string != null) {
            resourceImpl.addProperty(createProperty(Namespace.DEFAULT_NAMESPACE,
                    PropertyType.CHARACTERENCODING_PROP_NAME, string));
        }

        string = (String) resourceMap.get("guessedCharacterEncoding");
        if (string != null) {
            resourceImpl.addProperty(createProperty(Namespace.DEFAULT_NAMESPACE,
                    PropertyType.CHARACTERENCODING_GUESSED_PROP_NAME, string));
        }

        string = (String) resourceMap.get("userSpecifiedCharacterEncoding");
        if (string != null) {
            resourceImpl.addProperty(createProperty(Namespace.DEFAULT_NAMESPACE,
                    PropertyType.CHARACTERENCODING_USER_SPECIFIED_PROP_NAME, string));
        }

        string = (String) resourceMap.get("contentLanguage");
        if (string != null) {
            resourceImpl.addProperty(createProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.CONTENTLOCALE_PROP_NAME,
                    string));
        }

        resourceImpl.addProperty(createProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.LASTMODIFIED_PROP_NAME,
                resourceMap.get("lastModified")));

        principal = principalFactory.getPrincipal((String) resourceMap.get("modifiedBy"), Principal.Type.USER);
        resourceImpl.addProperty(createProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.MODIFIEDBY_PROP_NAME,
                principal));

        resourceImpl.addProperty(createProperty(Namespace.DEFAULT_NAMESPACE,
                PropertyType.CONTENTLASTMODIFIED_PROP_NAME, resourceMap.get("contentLastModified")));

        principal = principalFactory.getPrincipal((String) resourceMap.get("contentModifiedBy"), Principal.Type.USER);
        resourceImpl.addProperty(createProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.CONTENTMODIFIEDBY_PROP_NAME,
                principal));

        resourceImpl.addProperty(createProperty(Namespace.DEFAULT_NAMESPACE,
                PropertyType.PROPERTIESLASTMODIFIED_PROP_NAME, resourceMap.get("propertiesLastModified")));

        principal = principalFactory
                .getPrincipal((String) resourceMap.get("propertiesModifiedBy"), Principal.Type.USER);
        resourceImpl.addProperty(createProperty(Namespace.DEFAULT_NAMESPACE,
                PropertyType.PROPERTIESMODIFIEDBY_PROP_NAME, principal));

        if (!collection) {
            long contentLength = ((Number) resourceMap.get("contentLength")).longValue();
            resourceImpl.addProperty(createProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.CONTENTLENGTH_PROP_NAME,
                    new Long(contentLength)));
        }

        resourceImpl.setResourceType((String) resourceMap.get("resourceType"));

        Integer aclInheritedFrom = (Integer) resourceMap.get("aclInheritedFrom");
        if (aclInheritedFrom == null) {
            aclInheritedFrom = new Integer(-1);
        }

        resourceImpl.setAclInheritedFrom(aclInheritedFrom.intValue());
    }

    private Property createProperty(Namespace ns, String name, Object value) {
        PropertyTypeDefinition propDef = this.resourceTypeTree.getPropertyTypeDefinition(ns, name);
        Property prop = propDef.createProperty(value);
        return prop;
    }

    private Property createProperty(PropHolder holder) {
        Namespace namespace = this.resourceTypeTree.getNamespace(holder.namespaceUri);
        PropertyTypeDefinition propDef = this.resourceTypeTree.getPropertyTypeDefinition(namespace, holder.name);
        if (holder.binary) {
            BinaryValueReference[] refs = new BinaryValueReference[holder.values.size()];
            for (int i=0; i<refs.length; i++) {
                refs[i] = new BinaryValueReference(this, (Integer)holder.values.get(i));
            }
            return propDef.createProperty(refs);
        } else {
            String[] stringValues = new String[holder.values.size()];
            for (int i=0; i<stringValues.length; i++) {
                stringValues[i] = (String)holder.values.get(i);
            }
            return propDef.createProperty(stringValues);
        }
    }
    
    private Property createInheritedProperty(PropHolder holder) {
        PropertyImpl impl = (PropertyImpl) createProperty(holder);
        impl.setInherited(true);
        return impl;
    }

    private Map<String, Object> getResourceAsMap(ResourceImpl r) {
        Map<String, Object> map = new HashMap<String, Object>();
        String parentURI = null;
        if (r.getURI().getParent() != null) {
            parentURI = r.getURI().getParent().toString();
        }
        map.put("parent", parentURI);
        // XXX: use Integer (not int) as aclInheritedFrom field:
        map.put("aclInheritedFrom", r.getAclInheritedFrom() == PropertySetImpl.NULL_RESOURCE_ID ? null : new Integer(r
                .getAclInheritedFrom()));
        map.put("uri", r.getURI().toString());
        map.put("resourceType", r.getResourceType());

        // XXX: get list of names from PropertyType.SPECIAL_PROPERTIES:
        map.put("collection", r.isCollection() ? "Y" : "N");
        map.put("owner", r.getOwner().getQualifiedName());
        map.put("creationTime", r.getCreationTime());
        map.put("createdBy", r.getCreatedBy().getQualifiedName());
        map.put("contentType", r.getContentType());
        map.put("characterEncoding", r.getCharacterEncoding());
        map.put("userSpecifiedCharacterEncoding", r.getUserSpecifiedCharacterEncoding());
        map.put("guessedCharacterEncoding", r.getGuessedCharacterEncoding());
        // XXX: contentLanguage should be contentLocale:
        map.put("contentLanguage", r.getContentLanguage());
        map.put("lastModified", r.getLastModified());
        map.put("modifiedBy", r.getModifiedBy().getQualifiedName());
        map.put("contentLastModified", r.getContentLastModified());
        map.put("contentModifiedBy", r.getContentModifiedBy().getQualifiedName());
        map.put("propertiesLastModified", r.getPropertiesLastModified());
        map.put("propertiesModifiedBy", r.getPropertiesModifiedBy().getQualifiedName());
        map.put("contentLength", new Long(r.getContentLength()));

        return map;
    }

    @Override
    public Set<Principal> discoverGroups() {
        String sqlMap = getSqlMap("discoverGroups");
        @SuppressWarnings("unchecked")
        List<String> groupNames = getSqlMapClientTemplate().queryForList(sqlMap, null);

        Set<Principal> groups = new HashSet<Principal>();
        for (String groupName : groupNames) {
            Principal group = principalFactory.getPrincipal(groupName, Principal.Type.GROUP);
            groups.add(group);
        }

        return groups;
    }
    
    byte[] getBinaryPropertyBytes(Integer reference) throws DataAccessException {
        String sqlMap = getSqlMap("selectBinaryPropertyEntry");
        @SuppressWarnings("unchecked")
        Map result = (Map) getSqlMapClientTemplate().queryForObject(sqlMap, reference);
        return (byte[]) result.get("byteArray");
    }

    String getBinaryPropertyContentType(Integer reference) throws DataAccessException {
        String sqlMap = getSqlMap("selectBinaryMimeTypeEntry");
        return (String) getSqlMapClientTemplate().queryForObject(sqlMap, reference);
    }

    /**
     * Makes sure binary value is not backed only by a reference to database id.
     * If so, then copy to memory buffered value.
     * @param prop 
     */
    private void ensureBinaryValueBuffered(Property prop) {
        boolean multiple = prop.getDefinition() != null && prop.getDefinition().isMultiple();
        final Value[] values;
        if (multiple) {
            values = prop.getValues();
        } else {
            values = new Value[]{prop.getValue()};
        }

        for (int i = 0; i < values.length; i++) {
            BinaryValue binVal = values[i].getBinaryValue();
            if (binVal.getClass() == BinaryValueReference.class) {
                values[i] = new Value(binVal.getBytes(), binVal.getContentType());
            }
        }

        if (multiple) {
            prop.setValues(values);
        } else {
            prop.setValue(values[0]);
        }
    }

    @SuppressWarnings("serial")
    private class AclHolder extends HashMap<Privilege, Set<Principal>> {

        public void addEntry(Privilege action, Principal principal) {
            Set<Principal> set = this.get(action);
            if (set == null) {
                set = new HashSet<Principal>();
                this.put(action, set);
            }
            set.add(principal);
        }
    }

    @Required
    public void setPrincipalFactory(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }

    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }
    
    public void setOptimizedAclCopySupported(boolean optimizedAclCopySupported) {
        this.optimizedAclCopySupported = optimizedAclCopySupported;
    }
}

/**
 * An on-demand loading binary value with reference and
 * access to value in DataAccesor.
 * 
 */
final class BinaryValueReference implements BinaryValue {

    private final Integer ref;
    private final SqlMapDataAccessor dao;

    BinaryValueReference(SqlMapDataAccessor dao, Integer ref) {
        this.ref = ref;
        this.dao = dao;
    }
    
    @Override
    public String getContentType() throws DataAccessException {
        return this.dao.getBinaryPropertyContentType(this.ref);
    }

    @Override
    public ContentStream getContentStream() throws DataAccessException {
        // Consider avoiding copy to mem here, but then we depend
        // on client code closing the underlying InputStream properly to free
        // database resource.
        byte[] data = getBytes();
        return new ContentStream(new ByteArrayInputStream(data), data.length);
    }
    
    @Override
    public byte[] getBytes() throws DataAccessException {
        return this.dao.getBinaryPropertyBytes(this.ref);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final BinaryValueReference other = (BinaryValueReference) obj;
        if (this.ref != other.ref && (this.ref == null || !this.ref.equals(other.ref))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + (this.ref != null ? this.ref.hashCode() : 0);
        return hash;
    }
    
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("BinaryValueReference[");
        b.append("ref = ").append(this.ref).append("]");
        return b.toString();
    }

}
