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
package vtk.repository.store.db;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Acl;
import vtk.repository.Lock;
import vtk.repository.LockImpl;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Privilege;
import vtk.repository.Property;
import vtk.repository.PropertyImpl;
import vtk.repository.PropertySet;
import vtk.repository.PropertySetImpl;
import vtk.repository.RecoverableResource;
import vtk.repository.Repository.Depth;
import vtk.repository.ResourceImpl;
import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.BinaryValue;
import vtk.repository.resourcetype.BufferedBinaryValue;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.repository.resourcetype.ValueFactory;
import vtk.repository.store.DataAccessException;
import vtk.repository.store.DataAccessor;
import vtk.repository.store.db.SqlDaoUtils.PropHolder;
import vtk.security.Principal;
import vtk.security.Principal.Type;
import vtk.security.PrincipalFactory;
import vtk.util.io.InputStreamWithLength;

/**
 * An iBATIS SQL maps implementation of the DataAccessor interface.
 *
 * XXX XXX XXX Our DataAccessor interface declares our own DataAccessException
 * type as thrown by all methods, but THIS CLASS IN PRACTICE MOSTLY THROWS
 * Spring's DataAccessException. What a mess.
 *
 */
public class SqlMapDataAccessor extends AbstractSqlMapDataAccessor implements DataAccessor {

    private static final int STRING_VALUE_BINARY_THRESHOLD = 512;
    private ResourceTypeTree resourceTypeTree;
    private PrincipalFactory principalFactory;
    private ValueFactory valueFactory;
    private ResourceTypeMapper resourceTypeMapper;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private boolean optimizedAclCopySupported = false;
    private String repositoryId;
    
    @Override
    public boolean validate() {
        throw new DataAccessException("Not implemented");
    }

    @Override
    public ResourceImpl load(Path uri) {
        return load(uri, getSqlSession());
    }
    
    private ResourceImpl load(Path uri, SqlSession sqlSession) {
        ResourceImpl resource = loadResourceInternal(uri, sqlSession);
        if (resource == null) {
            return null;
        }
        
        loadInheritedProperties(new ResourceImpl[] { resource }, sqlSession);
        loadACLs(new ResourceImpl[] { resource }, sqlSession);

        if (resource.isCollection()) {
            loadChildUris(resource, sqlSession);
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
    private ResourceImpl loadResourceInternal(Path uri, SqlSession sqlSession) {
        String sqlMap = getSqlMap("loadResourceByUri");
        Map<String, ?> resourceMap = sqlSession.selectOne(sqlMap, uri.toString());
        if (resourceMap == null) {
            return null;
        }
        ResourceImpl resource = new ResourceImpl(uri);

        Map<Path, Lock> locks = loadLocks(new Path[] { resource.getURI() }, sqlSession);
        if (locks.containsKey(resource.getURI())) {
            resource.setLock(locks.get(resource.getURI()));
        }

        populateStandardProperties(resource, resourceMap);
        int resourceId = resource.getID();
        sqlMap = getSqlMap("loadPropertiesForResource");
        List<Map<String, Object>> propertyList = sqlSession.selectList(sqlMap, resourceId);
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
        getSqlSession().update(sqlMap, d);
    }

    @Override
    public Path[] discoverLocks(Path uri) {

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(uri, SQL_ESCAPE_CHAR));
        parameters.put("timestamp", new Date());

        String sqlMap = getSqlMap("discoverLocks");
        SqlSession sqlSession = getSqlSession();
        List<String> list = sqlSession.selectList(sqlMap, parameters);

        Path[] locks = new Path[list.size()];
        for (int i = 0; i < list.size(); i++) {
            locks[i] = Path.fromString(list.get(i));
        }
        return locks;
    }

    @Override
    public ResourceImpl storeACL(ResourceImpl r) {
        SqlSession sqlSession = getSqlSession();
        updateACL(r, sqlSession);
        
        // Re-load and return newly written ResourceImpl
        return load(r.getURI(), sqlSession);
    }

    @Override
    public ResourceImpl storeLock(ResourceImpl r) {

        // Delete any old persistent locks
        String sqlMap = getSqlMap("deleteLockByResourceId");
        SqlSession sqlSession = getSqlSession();
        sqlSession.delete(sqlMap, r.getID());

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
            sqlSession.update(sqlMap, parameters);
        }
        return load(r.getURI(), sqlSession);
    }

    private void updateACL(ResourceImpl r, SqlSession sqlSession) {

        // XXX: ACL inheritance checking does not belong here!?
        boolean wasInherited = isInheritedAcl(r, sqlSession);
        if (wasInherited && r.isInheritedAcl()) {
            return;
        }

        if (wasInherited) {

            // ACL was inherited, new ACL is not inherited:
            int oldInheritedFrom = findNearestACL(r.getURI(), sqlSession);
            insertAcl(r, sqlSession);

            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("resourceId", r.getID());
            parameters.put("inheritedFrom", null);

            String sqlMap = getSqlMap("updateAclInheritedFromByResourceId");
            sqlSession.update(sqlMap, parameters);

            parameters = new HashMap<String, Object>();
            parameters.put("previouslyInheritedFrom", oldInheritedFrom);
            parameters.put("inheritedFrom", r.getID());
            parameters.put("uri", r.getURI().toString());
            parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(r.getURI(), SQL_ESCAPE_CHAR));

            sqlMap = getSqlMap("updateAclInheritedFromByPreviousInheritedFromAndUri");
            sqlSession.update(sqlMap, parameters);
            return;
        }

        // ACL was not inherited
        // Delete previous ACL entries for resource:
        String sqlMap = getSqlMap("deleteAclEntriesByResourceId");
        sqlSession.delete(sqlMap, r.getID());

        if (!r.isInheritedAcl()) {
            insertAcl(r, sqlSession);

        } else {

            // The new ACL is inherited, update pointers to the
            // previously "nearest" ACL node:
            int nearest = findNearestACL(r.getURI(), sqlSession);

            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("inheritedFrom", nearest);
            parameters.put("resourceId", r.getID());
            parameters.put("previouslyInheritedFrom", r.getID());

            sqlMap = getSqlMap("updateAclInheritedFromByResourceIdOrPreviousInheritedFrom");
            sqlSession.update(sqlMap, parameters);
        }
    }

    @Override
    public ResourceImpl store(ResourceImpl r) {
        String sqlMap = getSqlMap("loadResourceByUri");
        SqlSession sqlSession = getSqlSession();
        boolean existed = sqlSession.selectOne(sqlMap, r.getURI().toString()) != null;

        Map<String, Object> parameters = getResourceAsMap(r);
        if (!existed) {
            parameters.put("aclInheritedFrom", findNearestACL(r.getURI(), sqlSession));
        }
        parameters.put("depth", r.getURI().getDepth());

        sqlMap = existed ? getSqlMap("updateResource") : getSqlMap("insertResource");
        if (logger.isDebugEnabled()) {
            logger.debug((existed ? "Updating" : "Storing") + " resource " + r + ", parameter map: " + parameters);
        }
        
        sqlSession.update(sqlMap, parameters);

        if (!existed) {
            sqlMap = getSqlMap("loadResourceIdByUri");
            Map<String, Object> map = getSqlSession().selectOne(sqlMap,
                    r.getURI().toString());
            Integer id = (Integer) map.get("resourceId");
            r.setID(id.intValue());
        }

        //storeLock(r);
        storeProperties(r, sqlSession);

        // Re-load and return newly written ResourceImpl
        return load(r.getURI());
    }

    @Override
    public void delete(ResourceImpl resource) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uri", resource.getURI().toString());
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(resource.getURI(), SQL_ESCAPE_CHAR));
        String sqlMap = getSqlMap("deleteResourceByUri");
        SqlSession sqlSession = getSqlSession();
        sqlSession.update(sqlMap, parameters);
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
        SqlSession sqlSession = getSqlSession();
        sqlSession.update(sqlMap, parameters);

        parameters.put("trashCanURI", trashID + "/" + resourceURI.getName());
        parameters.put("parentID", parent.getID());
        parameters.put("principal", principal.getName());
        parameters.put("deletedTime", Calendar.getInstance().getTime());
        parameters.put("wasInheritedAcl", resource.isInheritedAcl() ? "Y" : "N");
        sqlMap = getSqlMap("insertTrashCanEntry");
        sqlSession.update(sqlMap, parameters);
    }

    @Override
    public List<RecoverableResource> getRecoverableResources(final int parentResourceId) throws DataAccessException {
        String sqlMap = getSqlMap("getRecoverableResources");
        SqlSession sqlSession = getSqlSession();
        
        List<RecoverableResource> recoverableResources = sqlSession.selectList(sqlMap,
                parentResourceId);
        return recoverableResources;
    }

    @Override
    public ResourceImpl recover(Path parent, RecoverableResource recoverableResource) throws DataAccessException {

        int id = recoverableResource.getId();
        String sqlMap = getSqlMap("getRecoverableResourceById");
        SqlSession sqlSession = getSqlSession();
        Object o = sqlSession.selectOne(sqlMap, id);
        if (o == null) {
            throw new DataAccessException("Requested deleted object with id " + id + " was not found");
        }
        RecoverableResource deletedResource = (RecoverableResource) o;

        sqlMap = getSqlMap("deleteFromTrashCan");
        sqlSession.delete(sqlMap, deletedResource.getId());

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
        sqlSession.update(sqlMap, parameters);

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
        
        // Reload and return newly written recovered resource
        return load(recoverdResourcePath);
    }

    @Override
    public void deleteRecoverable(List<RecoverableResource> recoverableResources) throws DataAccessException {
        // XXX Lazy delete, #missing parent#
        String sqlMap = getSqlMap("deletePermanentlyMarkDeleted");
        
        int n = 0;
        Map<String, Object> parameters = new HashMap<String, Object>();
        SqlSession session = getSqlSession();
        for (RecoverableResource recoverable: recoverableResources) {
            logger.info("Permanently deleting recoverable: " + recoverable);
            
            String trashUri = recoverable.getTrashUri();
            parameters.put("trashCanURI", trashUri);
            parameters.put("trashCanURIWildCard", SqlDaoUtils.getStringSqlWildcard(trashUri, SQL_ESCAPE_CHAR));
            session.delete(sqlMap, parameters);
            sqlMap = getSqlMap("deleteFromTrashCan");
            session.delete(sqlMap, recoverable.getId());
            if (++n % UPDATE_BATCH_SIZE_LIMIT == 0) {
                session.flushStatements();
            }
        }
        session.flushStatements();
    }

    @Override
    public List<RecoverableResource> getTrashCanOverdue(int overDueLimit) throws DataAccessException {
        Calendar cal = Calendar.getInstance();
        // Add negative limit -> substract
        cal.add(Calendar.DATE, -overDueLimit);
        Date overDueDate = cal.getTime();
        String sqlMap = getSqlMap("getOverdue");
        List<RecoverableResource> recoverableResources = this.getSqlSession().selectList(sqlMap,
                overDueDate);
        return recoverableResources;
    }

    @Override
    public List<RecoverableResource> getTrashCanOrphans() throws DataAccessException {
        String sqlMap = getSqlMap("getOrphans");
        List<RecoverableResource> recoverableResources = this.getSqlSession().selectList(sqlMap);
        return recoverableResources;
    }

    @Override
    public ResourceImpl[] loadChildren(ResourceImpl parent) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(parent.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("depth", parent.getURI().getDepth() + 1);

        List<ResourceImpl> children = new ArrayList<ResourceImpl>();
        String sqlMap = getSqlMap("loadChildren");
        SqlSession sqlSession = getSqlSession();

        List<Map<String, Object>> resources = sqlSession.selectList(sqlMap, parameters);
        Map<Path, Lock> locks = loadLocksForChildren(parent, sqlSession);
        for (Map<String, Object> resourceMap : resources) {
            Path uri = (Path) resourceMap.get("uri");

            ResourceImpl resource = new ResourceImpl(uri);

            populateStandardProperties(resource, resourceMap);

            if (locks.containsKey(uri)) {
                resource.setLock(locks.get(uri));
            }

            children.add(resource);
        }

        ResourceImpl[] result = children.toArray(new ResourceImpl[children.size()]);
        loadChildUrisForChildren(parent, result, sqlSession);
        loadInheritedProperties(result, sqlSession);
        loadACLs(result, sqlSession);
        loadPropertiesForChildren(parent, result, sqlSession);

        return result;
    }

    @Override
    public Path[] discoverACLs(Path uri) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uri", uri.toString());        
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(uri, SQL_ESCAPE_CHAR));

        String sqlMap = getSqlMap("discoverAcls");
        List<Path> uris = getSqlSession().selectList(sqlMap, parameters);

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
                if (logger.isDebugEnabled()) {
                    logger.debug("Copy: fixed property: " + property.getDefinition().getName() + ": " + value);
                }
                parameters.put(property.getDefinition().getName(), value);
            }
        }
    }

    @Override
    public ResourceImpl copy(ResourceImpl resource, ResourceImpl destParent, PropertySet newResource, boolean copyACLs,
            PropertySet fixedProperties, Set<String> uncopyableProperties) {

        Path destURI = newResource.getURI();
        int depthDiff = destURI.getDepth() - resource.getURI().getDepth();

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("srcUri", resource.getURI().toString());
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(resource.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("destUri", destURI.toString());
        parameters.put("destUriWildcard", SqlDaoUtils.getUriSqlWildcard(destURI, SQL_ESCAPE_CHAR));
        parameters.put("depthDiff", depthDiff);
        parameters.put("uncopyableProperties", new ArrayList<>(uncopyableProperties));

        if (fixedProperties != null) {
            supplyFixedProperties(parameters, fixedProperties);
        }
        
        SqlSession sqlSession = getSqlSession();

        String sqlMap = getSqlMap("copyResource");
        sqlSession.update(sqlMap, parameters);

        sqlMap = getSqlMap("copyProperties");
        sqlSession.update(sqlMap, parameters);

        if (copyACLs) {

            sqlMap = getSqlMap("copyAclEntries");
            sqlSession.update(sqlMap, parameters);

            // Update inheritance to nearest node:
            int srcNearestACL = findNearestACL(resource.getURI(), sqlSession);
            int destNearestACL = findNearestACL(destURI, sqlSession);

            parameters = new HashMap<String, Object>();
            parameters.put("uri", destURI.toString());
            parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(destURI, SQL_ESCAPE_CHAR));
            parameters.put("inheritedFrom", destNearestACL);
            parameters.put("previouslyInheritedFrom", srcNearestACL);

            sqlMap = getSqlMap("updateAclInheritedFromByPreviousInheritedFromAndUri");
            sqlSession.update(sqlMap, parameters);

            if (this.optimizedAclCopySupported) {
                sqlMap = getSqlMap("updateAclInheritedFromByPreviousResourceId");
                sqlSession.update(sqlMap, parameters);
            } else {
                sqlMap = getSqlMap("loadPreviousInheritedFromMap");

                final List<Map<String, Object>> list = getSqlSession().selectList(sqlMap, parameters);
                
                final String batchSqlMap = getSqlMap("updateAclInheritedFromByResourceId");
                
                for (Map<String, Object> map : list) {
                    sqlSession.update(batchSqlMap, map);
                }
            }

        } else {
            int nearestAclNode = findNearestACL(destURI, sqlSession);
            parameters = new HashMap<String, Object>();
            parameters.put("uri", destURI.toString());
            parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(destURI, SQL_ESCAPE_CHAR));
            parameters.put("inheritedFrom", nearestAclNode);

            sqlMap = getSqlMap("updateAclInheritedFromByUri");
            sqlSession.update(sqlMap, parameters);
        }

        parameters = new HashMap<String, Object>();
        parameters.put("uri", destURI.toString());
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(destURI, SQL_ESCAPE_CHAR));
        sqlMap = getSqlMap("clearPrevResourceIdByUri");
        sqlSession.update(sqlMap, parameters);

        parameters = getResourceAsMap(destParent);
        sqlMap = getSqlMap("updateResource");
        sqlSession.update(sqlMap, parameters);
        storeProperties(destParent, sqlSession);

        ResourceImpl created = loadResourceInternal(newResource.getURI(), sqlSession);
        for (Property prop : newResource) {
            
            if (prop.getDefinition().getNamespace() == Namespace.DEFAULT_NAMESPACE 
                    && uncopyableProperties.contains(prop.getDefinition().getName())) {
                continue;
            }
            
            created.addProperty(prop);
            Property fixedProp = fixedProperties != null ? fixedProperties.getProperty(prop.getDefinition()
                    .getNamespace(), prop.getDefinition().getName()) : null;
            if (fixedProp != null) {
                created.addProperty(fixedProp);
            }
        }

        storeProperties(created, sqlSession);

        // Re-load and return newly written destination ResourceImpl
        return load(created.getURI());
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
        SqlSession sqlSession = getSqlSession();
        sqlSession.update(sqlMap, parameters);

        sqlMap = getSqlMap("moveDescendants");
        sqlSession.update(sqlMap, parameters);

        ResourceImpl created = loadResourceInternal(newResource.getURI(), sqlSession);
        for (Property prop : newResource) {
            created.addProperty(prop);
        }
        sqlMap = getSqlMap("updateResource");
        parameters = getResourceAsMap(newResource);
        sqlSession.update(sqlMap, parameters);

        storeProperties(created, sqlSession);

        if (newResource.isInheritedAcl()) {
            int srcNearestAcl = findNearestACL(resource.getURI(), sqlSession);
            int nearestAclNode = findNearestACL(newResource.getURI(), sqlSession);
            parameters = new HashMap<String, Object>();
            parameters.put("uri", newResource.getURI().toString());
            parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(newResource.getURI(), SQL_ESCAPE_CHAR));
            parameters.put("inheritedFrom", nearestAclNode);
            parameters.put("previouslyInheritedFrom", srcNearestAcl);

            sqlMap = getSqlMap("updateAclInheritedFromByPreviousInheritedFromAndUri");
            sqlSession.update(sqlMap, parameters);
        }

        // Reload and return newly written destination ResourceImpl
        return load(newResource.getURI());
    }

    private void loadChildUris(ResourceImpl parent, SqlSession sqlSession) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(parent.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("depth", parent.getURI().getDepth() + 1);

        String sqlMap = getSqlMap("loadChildUrisForChildren");

        List<Path> resourceUriList = sqlSession.selectList(sqlMap, parameters);
        
        parent.setChildURIs(resourceUriList);
    }

    private void loadChildUrisForChildren(ResourceImpl parent, ResourceImpl[] children, SqlSession sqlSession) {

        // Initialize a map from child collection URI to the list of
        // grandchildren's URIs:
        Map<Path, List<Path>> childMap = new HashMap<>();
        for (ResourceImpl child : children) {
            if (child.isCollection()) {
                childMap.put(child.getURI(), new ArrayList<Path>());
            }
        }

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(parent.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("depth", parent.getURI().getDepth() + 2);

        String sqlMap = getSqlMap("loadChildUrisForChildren");

        List<Path> resourceUris = sqlSession.selectList(sqlMap, parameters);

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

    private void loadPropertiesForChildren(ResourceImpl parent, ResourceImpl[] resources, SqlSession sqlSession) {
        if ((resources == null) || (resources.length == 0)) {
            return;
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(parent.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("depth", parent.getURI().getDepth() + 1);

        String sqlMap = getSqlMap("loadPropertiesForChildren");

        List<Map<String, Object>> propertyList = sqlSession.selectList(sqlMap, parameters);

        populateCustomProperties(resources, propertyList);
    }

    private Map<Path, Lock> loadLocks(Path[] uris, SqlSession sqlSession) {
        if (uris.length == 0)
            return new HashMap<Path, Lock>();
        Map<String, Object> parameters = new HashMap<String, Object>();
        List<String> uriList = new ArrayList<String>();
        for (Path p : uris)
            uriList.add(p.toString());
        parameters.put("uris", uriList);
        parameters.put("timestamp", new Date());
        String sqlMap = getSqlMap("loadLocksByUris");

        List<Map<String, Object>> locks = sqlSession.selectList(sqlMap, parameters);

        Map<Path, Lock> result = new HashMap<Path, Lock>();

        for (Map<String, Object> map : locks) {
            LockImpl lock = new LockImpl((String) map.get("token"), principalFactory.getPrincipal((String) map
                    .get("owner"), Principal.Type.USER), (String) map.get("ownerInfo"), Depth.fromString((String) map
                    .get("depth")), (Date) map.get("timeout"));

            result.put(Path.fromString((String) map.get("uri")), lock);
        }
        return result;
    }

    private Map<Path, Lock> loadLocksForChildren(ResourceImpl parent, SqlSession sqlSession) {

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("timestamp", new Date());
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(parent.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("depth", parent.getURI().getDepth() + 1);

        String sqlMap = getSqlMap("loadLocksForChildren");

        List<Map<String, Object>> locks = sqlSession.selectList(sqlMap, parameters);

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

    private void insertAcl(final ResourceImpl r, SqlSession sqlSession) {
        final Map<String, Integer> actionTypes = loadActionTypes(sqlSession);
        final Acl newAcl = r.getAcl();
        if (newAcl == null) {
            throw new DataAccessException("Resource " + r + " has no ACL");
        }
        final Set<Privilege> actions = newAcl.getActions();
        final String sqlMap = getSqlMap("insertAclEntry");
        
        Map<String, Object> parameters = new HashMap<String, Object>();
        for (Privilege action : actions) {
            String actionName = action.getName();
            for (Principal p : newAcl.getPrincipalSet(action)) {

                Integer actionID = actionTypes.get(actionName);
                if (actionID == null) {
                    throw new DataAccessException("insertAcl(): Unable to " + "find id for action '" + action + "'");
                }

                parameters.put("actionId", actionID);
                parameters.put("resourceId", r.getID());
                parameters.put("principal", p.getQualifiedName());
                parameters.put("isUser", p.getType() == Principal.Type.GROUP ? "N" : "Y");
                parameters.put("grantedBy", r.getOwner().getQualifiedName());
                parameters.put("grantedDate", new Date());

                sqlSession.update(sqlMap, parameters);
            }
        }
    }

    private Map<String, Integer> loadActionTypes(SqlSession sqlSession) {
        Map<String, Integer> actionTypes = new HashMap<String, Integer>();

        String sqlMap = getSqlMap("loadActionTypes");
        List<Map<String, Object>> list = sqlSession.selectList(sqlMap, null);
        for (Map<String, Object> map : list) {
            actionTypes.put((String) map.get("name"), (Integer) map.get("id"));
        }
        return actionTypes;
    }

    private boolean isInheritedAcl(ResourceImpl r, SqlSession sqlSession) {

        String sqlMap = getSqlMap("isInheritedAcl");
        Map<String, Integer> map = sqlSession.selectOne(sqlMap, r.getID());

        Integer inheritedFrom = map.get("inheritedFrom");
        return inheritedFrom != null;
    }

    private int findNearestACL(Path uri, SqlSession sqlSession) {

        List<Path> path = uri.getPaths();

        // Reverse list to get deepest URI first
        Collections.reverse(path);

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("path", path);
        String sqlMap = getSqlMap("findNearestAclResourceId");

        List<Map<String, Object>> list = sqlSession.selectList(sqlMap, parameters);

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
    private void loadInheritedProperties(ResourceImpl[] resources, SqlSession sqlSession) {
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
                List<Map<String, Object>> rows = sqlSession.selectList(sqlMap, parameterMap);
                propertyRows.addAll(rows);
                paths.clear();
            }
        }

        // Aggregate all properties in resultset rows, and also link paths to PropHolder instances
        // Map linking path to list of PropHolder instances
        final Map<Path, List<PropHolder>> inheritableMap = new HashMap<Path, List<PropHolder>>();
        // Map for PropHolder value aggregation
        final Map<PropHolder, List<Object>> holderValues = new HashMap<PropHolder, List<Object>>();
        for (Map<String, Object> propEntry : propertyRows) {
            PropHolder propHolder = new PropHolder();
            propHolder.propID = propEntry.get("id");
            propHolder.namespaceUri = (String) propEntry.get("namespaceUri");
            propHolder.name = (String) propEntry.get("name");
            propHolder.resourceId = (Integer) propEntry.get("resourceId");
            propHolder.binary = (Boolean) propEntry.get("binary");
            propHolder.inheritable = true;
            List<Object> values = holderValues.get(propHolder);
            if (values == null) {
                // New Property
                values = new ArrayList<Object>(2);
                propHolder.values = values;
                holderValues.put(propHolder, values);

                // Populate inheritables map with canonical PropHolder instance
                Path uri = (Path) propEntry.get("uri");
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
        
        // Create Property instances from PropHolders in inheritableMap
        final Map<Path, List<Property>> inheritableProperties
                = new HashMap<Path, List<Property>>(inheritableMap.size(), 1.0f);
        for (Map.Entry<Path, List<PropHolder>> entry: inheritableMap.entrySet()) {
            List<PropHolder> holderList = entry.getValue();
            List<Property> propList = new ArrayList<Property>(holderList.size());
            for (PropHolder ph: holderList) {
                propList.add(createInheritedProperty(ph));
            }
            inheritableProperties.put(entry.getKey(), propList);
        }

        // Populate loaded resources with inheritable props, handling override from bottom up in paths
        final Set<PropertyTypeDefinition> encountered = new HashSet<PropertyTypeDefinition>();
        for (ResourceImpl r : resources) {
            Path parent = r.getURI().getParent();
            if (parent == null) {
                continue; // root resource cannot inherit anything
            }
            
            List<Path> pathList = parent.getPaths();
            for (int i = pathList.size() - 1; i >= 0; i--) {
                Path p = pathList.get(i);
                List<Property> propList = inheritableProperties.get(p);
                if (propList != null) {
                    for (Property prop : propList) {
                        if (encountered.add(prop.getDefinition())) {
                            if (r.getProperty(prop.getDefinition()) == null) {
                                r.addProperty(prop);
                            }
                        }
                    }
                }
            }
            encountered.clear();
        }
    }
    
    
    private void loadACLs(ResourceImpl[] resources, SqlSession sqlSession) {

        if (resources.length == 0) {
            return;
        }
        Set<Integer> resourceIds = new HashSet<Integer>();
        for (int i = 0; i < resources.length; i++) {

            int id = resources[i].isInheritedAcl() ? resources[i].getAclInheritedFrom() : resources[i].getID();

            resourceIds.add(id);
        }
        Map<Integer, AclHolder> map = loadAclMap(new ArrayList<Integer>(resourceIds), sqlSession);

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

    private Map<Integer, AclHolder> loadAclMap(List<Integer> resourceIds, SqlSession sqlSession) {

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

            loadAclBatch(subList, resultMap, sqlSession);

            start += batchSize;
            end = Math.min(end + batchSize, total);
        }
        return resultMap;
    }

    /**
     * Load batch of ACLs from list of resource ids. The caller must provide
     * a map to populate with an AclHolder instance per resource id.
     * @param resourceIds resource ids that have an ACL. Should not contain
     * ids of resources that only inherit their ACL.
     * 
     * @param resultMap the map to populate with 
     */
    void loadAclBatch(List<Integer> resourceIds, Map<Integer, AclHolder> resultMap, SqlSession sqlSession) {
        Map<String, Object> parameterMap = new HashMap<String, Object>();
        parameterMap.put("resourceIds", resourceIds);

        String sqlMap = getSqlMap("loadAclEntriesByResourceIds");

        List<Map<String, Object>> aclEntries = sqlSession.selectList(sqlMap, parameterMap);

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
                /* We don't fetch metadata for groups here due to performance
                   concerns. Metadata for GROUP principals must be retreived on demand
                   by higher level code.
                */
                p = principalFactory.getPrincipal(name, Type.GROUP, false);
            } else if (name.startsWith("pseudo:")) {
                p = principalFactory.getPrincipal(name, Type.PSEUDO);
            } else {
                /* We really shouldn't fetch metadata for principals here, but do so
                   due to compatibility concerns with higher level code expecting it
                   to be present.
                */
                p = principalFactory.getPrincipal(name, Type.USER);
            }
            Privilege action = Privilege.forName(privilege);

            acl.addEntry(action, p);
        }
    }

    private void storeProperties(final ResourceImpl r, SqlSession sqlSession) {

        for (Property property : r) {
            // XXX: mem copying has to be done because of the way properties
            // are stored: first deleted, then inserted (never updated).
            // If any binary value is of type BinaryValueReference (created only by this class)
            // then DataAccessException will be thrown if the reference is STALE.
            ensureBinaryValuesAreBuffered(property);
        }
        
        String sqlMap = getSqlMap("deletePropertiesByResourceId");
        sqlSession.update(sqlMap, r.getID());

        final String batchSqlMap = getSqlMap("insertPropertyEntry");
        
        Map<String, Object> parameters = new HashMap<>();
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
                    String nativeStringValue = v.getNativeStringRepresentation();
                    parameters.put("value", nativeStringValue);
                    
                    if (property.getType() == PropertyType.Type.BINARY) {
                        parameters.put("binaryContent", v.getBinaryValue().getBytes());
                        parameters.put("binaryMimeType", v.getBinaryValue().getContentType());
                        
                    } else if (nativeStringValue.length() > STRING_VALUE_BINARY_THRESHOLD) {
                        // Store native string value longer than N chars as UTF-8 encoded binary data.
                        final String valueContentType;
                        switch (property.getType()) {
                            case IMAGE_REF:
                            case HTML:
                                valueContentType = "text/html"; break;
                            case STRING:
                                valueContentType = "text/plain"; break;
                            case JSON:
                                valueContentType = "application/json"; break;
                            default:
                                // For now we deny the possiblity of storing any other value types as binary values.
                                // This is unlikely to hit.
                                throw new DataAccessException("Cannot store value for property " 
                                        + property + ": native string size limit exceeded and type cannot be stored as binary.");
                        }
                        
                        BinaryValue bval = new BufferedBinaryValue(nativeStringValue, valueContentType);
                        parameters.put("value", "#binary");
                        parameters.put("binaryContent", bval.getBytes());
                        parameters.put("binaryMimeType", bval.getContentType());

                    } else {
                        // Value stored in regular "value" column, make sure binary ref columns are null
                        parameters.remove("binaryContent");
                        parameters.remove("binaryMimeType");
                    }
                    sqlSession.update(batchSqlMap, parameters);
                }
            }
            parameters.clear();
        }
    }
    
    private void populateCustomProperties(ResourceImpl[] resources,
            List<Map<String, Object>> propertyRows) {

        Map<Integer, ResourceImpl> resourceMap = new HashMap<>(resources.length+1, 1f);
        for (ResourceImpl resource : resources) {
            resourceMap.put(resource.getID(), resource);
        }

        Map<PropHolder, List<Object>> propValuesMap = new HashMap<>();

        for (Map<String, Object> propEntry : propertyRows) {
            PropHolder prop = new PropHolder();
            prop.propID = propEntry.get("id");
            prop.namespaceUri = (String) propEntry.get("namespaceUri");
            prop.name = (String) propEntry.get("name");
            prop.resourceId = (Integer) propEntry.get("resourceId");
            prop.binary = (Boolean) propEntry.get("binary");

            List<Object> values = propValuesMap.get(prop);
            if (values == null) {
                values = new ArrayList<>(2); // Most props have only one value
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
            Property property = createProperty(propHolder);
            r.addProperty(property);
        }
    }
    
    private String makeExternalId(int internalId) {
        return repositoryId + "_" + internalId;
    }

    void populateStandardProperties(PropertySetImpl resourceImpl, Map<String, ?> resourceMap) {

        // Internal resource id
        resourceImpl.setID(((Number) resourceMap.get("id")).intValue());
        
        // External resource id property
        resourceImpl.addProperty(createProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.EXTERNAL_ID_PROP_NAME, 
                makeExternalId(resourceImpl.getID())));

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
            resourceImpl.addProperty(createProperty(Namespace.DEFAULT_NAMESPACE, PropertyType.CONTENTLENGTH_PROP_NAME, contentLength));
        }

        String type = (String) resourceMap.get("resourceType");
        resourceImpl.setResourceType(resourceTypeMapper.resolveResourceType(type));

        Integer aclInheritedFrom = (Integer) resourceMap.get("aclInheritedFrom");
        if (aclInheritedFrom == null) {
            aclInheritedFrom = PropertySetImpl.NULL_RESOURCE_ID;
        }

        resourceImpl.setAclInheritedFrom(aclInheritedFrom);
    }
    

    /**
     * Create property from namespace, name and value.
     * 
     * Data type specific format validation is not performed here.
     */
    Property createProperty(Namespace ns, String name, Object objectValue) {
        
        PropertyTypeDefinition propDef = this.resourceTypeTree.getPropertyTypeDefinition(ns, name);
        PropertyImpl prop = new PropertyImpl();
        prop.setDefinition(propDef);

        // See also PropertyTypeDefinitionImppl#createProperty(Object value) 
        // which essentially does the same thing, but with strict validation of value.

        final Value value;
        if (objectValue instanceof Date) {
            value = new Value((Date)objectValue, propDef.getType() == PropertyType.Type.DATE);
        } else if (objectValue instanceof Boolean) {
            value = new Value((Boolean)objectValue);
        } else if (objectValue instanceof Long) {
            value = new Value((Long)objectValue);
        } else if (objectValue instanceof Integer) {
            value = new Value((Integer)objectValue);
        } else if (objectValue instanceof Principal) {
            value = new Value((Principal)objectValue);
        } else if (!(objectValue instanceof String)) {
            throw new DataAccessException("Supplied object value of property [namespaces: " + ns + ", name: " + name
                    + "] not of any supported type " + "(type was: " + objectValue.getClass() + ")");
        } else {
            value = new Value((String)objectValue, PropertyType.Type.STRING);
        }
        
        // Set value without strict validation
        prop.setValue(value, false);
        
        return prop;
    }

    /**
     * Create property from raw PropHolder instance loaded from database.
     * <p>
     * Data type specific format validation is not performed at this stage.
     */
    Property createProperty(PropHolder holder) {
        Namespace namespace = this.resourceTypeTree.getNamespace(holder.namespaceUri);
        PropertyTypeDefinition propDef = this.resourceTypeTree.getPropertyTypeDefinition(namespace, holder.name);
        PropertyImpl prop = new PropertyImpl();
        prop.setDefinition(propDef);
        
        // In case of multi-value props, some values may be stored as binary
        // and others directly as strings for the same property, depending on size.
        // So check each value in PropHolder.
        Value[] values = new Value[holder.values.size()];
        for (int i=0; i<holder.values.size(); i++) {
            Object objectValue = holder.values.get(i);
            if (objectValue.getClass() == String.class) {
                values[i] = this.valueFactory.createValue((String)objectValue, propDef.getType());
            } else if (objectValue.getClass() == Integer.class) {
                // Binary value reference
                BinaryValueReference binVal = new BinaryValueReference(this, (Integer)objectValue);
                try {
                    values[i] = this.valueFactory.createValue(binVal, propDef.getType());
                } catch (IllegalArgumentException ia) {
                    logger.warn("Failed to create property value from integer ref = " 
                            + objectValue + ", resource id = " + holder.resourceId 
                            + ", prop name = " + holder.name + ", prop name_space = " 
                            + holder.namespaceUri);
                    throw ia;
                }
            } else if (objectValue instanceof BinaryValue) {
                // Already (buffered/loaded) binary value
                values[i] = this.valueFactory.createValue((BinaryValue)objectValue, propDef.getType());
            } else {
                throw new DataAccessException("Expected PropHolder value to be either string or integer reference for property " + prop);
            }
        }
        
        // Set value(s) without strict validation
        if (propDef.isMultiple()) {
            prop.setValues(values, false);
        } else {
            if (values.length > 1) {
                    throw new DataAccessException("Property " + propDef
                            + " is not multi-value, but multiple values found in database.");
            }
            prop.setValue(values[0], false);
        }
        
        return prop;
    }

    /**
     * Create property instance from PropHolder loaded from database, with
     * inherited flag set to <code>true</code>.
     */
    Property createInheritedProperty(PropHolder holder) {
        PropertyImpl impl = (PropertyImpl) createProperty(holder);
        impl.setInherited(true);
        return impl;
    }

    private Map<String, Object> getResourceAsMap(ResourceImpl r) {
        Map<String, Object> resourceMap = new HashMap<String, Object>(32, 1.0f);
        Path parentPath = r.getURI().getParent();
        Integer aclInheritedFrom = r.getAclInheritedFrom() != PropertySetImpl.NULL_RESOURCE_ID ? r.getAclInheritedFrom() : null;
        
        resourceMap.put("parent", parentPath != null ? parentPath.toString() : null);
        resourceMap.put("aclInheritedFrom", aclInheritedFrom);
        resourceMap.put("uri", r.getURI().toString());
        resourceMap.put("resourceType", resourceTypeMapper.resourceTypePath(r.getResourceType()));

        resourceMap.put(PropertyType.COLLECTION_PROP_NAME, r.isCollection() ? "Y" : "N");
        resourceMap.put(PropertyType.OWNER_PROP_NAME, r.getOwner().getQualifiedName());
        resourceMap.put(PropertyType.CREATIONTIME_PROP_NAME, r.getCreationTime());
        resourceMap.put(PropertyType.CREATEDBY_PROP_NAME, r.getCreatedBy().getQualifiedName());
        resourceMap.put(PropertyType.CONTENTTYPE_PROP_NAME, r.getContentType());
        resourceMap.put(PropertyType.CHARACTERENCODING_PROP_NAME, r.getCharacterEncoding());
        resourceMap.put(PropertyType.CHARACTERENCODING_USER_SPECIFIED_PROP_NAME, r.getUserSpecifiedCharacterEncoding());
        resourceMap.put(PropertyType.CHARACTERENCODING_GUESSED_PROP_NAME, r.getGuessedCharacterEncoding());
        resourceMap.put(PropertyType.LASTMODIFIED_PROP_NAME, r.getLastModified());
        resourceMap.put(PropertyType.MODIFIEDBY_PROP_NAME, r.getModifiedBy().getQualifiedName());
        resourceMap.put(PropertyType.CONTENTLASTMODIFIED_PROP_NAME, r.getContentLastModified());
        resourceMap.put(PropertyType.CONTENTMODIFIEDBY_PROP_NAME, r.getContentModifiedBy().getQualifiedName());
        resourceMap.put(PropertyType.PROPERTIESLASTMODIFIED_PROP_NAME, r.getPropertiesLastModified());
        resourceMap.put(PropertyType.PROPERTIESMODIFIEDBY_PROP_NAME, r.getPropertiesModifiedBy().getQualifiedName());
        resourceMap.put(PropertyType.CONTENTLENGTH_PROP_NAME, new Long(r.getContentLength()));

        return resourceMap;
    }

    @Override
    public Set<Principal> discoverGroups() {
        String sqlMap = getSqlMap("discoverGroups");
        @SuppressWarnings("unchecked")
        List<String> groupNames = getSqlSession().selectList(sqlMap, null);

        Set<Principal> groups = new HashSet<Principal>();
        for (String groupName : groupNames) {
            Principal group = principalFactory.getPrincipal(groupName, Principal.Type.GROUP, false);
            groups.add(group);
        }

        return groups;
    }
    
    byte[] getBinaryPropertyBytes(Integer reference) throws DataAccessException {
        String sqlMap = getSqlMap("selectBinaryPropertyEntry");
        Map<String, Object> map = getSqlSession().selectOne(sqlMap, reference);
        final byte[] result = (byte[])map.get("byteArray");
        if (result == null) {
            throw new DataAccessException("Binary value with reference " + reference + " is stale.");
        }
        
        return result;
    }

    String getBinaryPropertyContentType(Integer reference) throws DataAccessException {
        String sqlMap = getSqlMap("selectBinaryMimeTypeEntry");
        return (String) getSqlSession().selectOne(sqlMap, reference);
    }

    /**
     * Makes sure any value is not backed only by a reference to database id.
     * If so, then copy to a memory buffered value.
     */
    private void ensureBinaryValuesAreBuffered(Property prop) {
        boolean multiple = prop.getDefinition() != null && prop.getDefinition().isMultiple();
        final Value[] values;
        if (multiple) {
            values = prop.getValues();
        } else {
            values = new Value[]{prop.getValue()};
        }

        for (int i = 0; i < values.length; i++) {
            BinaryValue binVal = values[i].getBinaryValue();
            if (binVal != null && binVal.getClass() == BinaryValueReference.class) {
                values[i] = new Value(binVal.getBytes(), binVal.getContentType(), prop.getDefinition().getType());
            }
        }

        if (multiple) {
            prop.setValues(values);
        } else {
            prop.setValue(values[0]);
        }
    }

    @Required
    public void setRepositoryId(String repositoryId) {
        this.repositoryId = repositoryId;
    }

    @SuppressWarnings("serial")
    static class AclHolder extends HashMap<Privilege, Set<Principal>> {

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
    public void setValueFactory(ValueFactory vf) {
        this.valueFactory = vf;
    }

    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
        this.resourceTypeMapper = new ResourceTypeMapper(resourceTypeTree);
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
    public InputStreamWithLength stream() throws DataAccessException {
        // Consider avoiding copy to mem here, but then we depend
        // on client code closing the underlying InputStream properly to free
        // database resource.
        byte[] data = getBytes();
        return new InputStreamWithLength(new ByteArrayInputStream(data), data.length);
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
