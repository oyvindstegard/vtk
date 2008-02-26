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

import java.sql.SQLException;
import java.util.ArrayList;
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
import org.springframework.orm.ibatis.SqlMapClientCallback;
import org.vortikal.repository.Acl;
import org.vortikal.repository.AclImpl;
import org.vortikal.repository.ChangeLogEntry;
import org.vortikal.repository.Lock;
import org.vortikal.repository.LockImpl;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.Privilege;
import org.vortikal.repository.Property;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.PropertySetImpl;
import org.vortikal.repository.RepositoryAction;
import org.vortikal.repository.ResourceImpl;
import org.vortikal.repository.resourcetype.PropertyType;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.repository.store.DataAccessException;
import org.vortikal.repository.store.DataAccessor;
import org.vortikal.security.Principal;
import org.vortikal.util.repository.URIUtil;
import org.vortikal.util.web.URLUtil;

import com.ibatis.sqlmap.client.SqlMapExecutor;


/**
 * An iBATIS SQL maps implementation of the DataAccessor interface.
 */
public class SqlMapDataAccessor extends AbstractSqlMapDataAccessor
  implements DataAccessor {

    public static final char SQL_ESCAPE_CHAR = '@';

    private Log logger = LogFactory.getLog(this.getClass());

    private boolean optimizedAclCopySupported = false;

    public void setOptimizedAclCopySupported(boolean optimizedAclCopySupported) {
        this.optimizedAclCopySupported = optimizedAclCopySupported;
    }
    
    public boolean validate() {
        throw new DataAccessException("Not implemented");
    }


    

    public ResourceImpl load(String uri) {
        ResourceImpl resource = loadResourceInternal(uri);
        if (resource == null) {
            return null;
        }

        loadACLs(new ResourceImpl[] {resource});
        loadChildUris(resource);

        return resource;
    }

    /**
     * This method needs to be overridden by the framework.
     * 
     */
    protected ResourceImpl createResourceImpl() { return null; }
    
    private ResourceImpl loadResourceInternal(String uri) {
        String sqlMap = getSqlMap("loadResourceByUri");
        Map<String, ?> resourceMap = (Map<String, ?>)
            getSqlMapClientTemplate().queryForObject(sqlMap, uri);
        if (resourceMap == null) {
            return null;
        }
        ResourceImpl resource = createResourceImpl();
        resource.setUri(uri);
        
        Map<String, Lock> locks = loadLocks(new String[] {resource.getURI()});
        if (locks.containsKey(resource.getURI())) {
            resource.setLock(locks.get(resource.getURI()));
        }

        populateStandardProperties(resource, resourceMap);
        int resourceId = resource.getID();
        sqlMap = getSqlMap("loadPropertiesForResource");
        List<Map<String, Object>> propertyList = 
            getSqlMapClientTemplate().queryForList(sqlMap, resourceId);
        populateCustomProperties(new ResourceImpl[] {resource}, propertyList);

        Integer aclInheritedFrom = (Integer) resourceMap.get("aclInheritedFrom");
        boolean aclInherited = aclInheritedFrom != null;
        resource.setInheritedAcl(aclInherited);
        resource.setAclInheritedFrom(aclInherited ?
                                     aclInheritedFrom.intValue() :
                                     PropertySetImpl.NULL_RESOURCE_ID);
        return resource;
    }


    public void deleteExpiredLocks(Date d) {
        String sqlMap = getSqlMap("deleteExpiredLocks");
        getSqlMapClientTemplate().update(sqlMap, d);
    }

    


    public void addChangeLogEntry(ChangeLogEntry entry, boolean recurse) {
        String sqlMap = null;
        if (entry.isCollection() && recurse) {
            sqlMap = getSqlMap("insertChangeLogEntriesRecursively");
            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("entry", entry);
            parameters.put("uriWildcard", 
                           SqlDaoUtils.getUriSqlWildcard(entry.getUri(), SQL_ESCAPE_CHAR));

            getSqlMapClientTemplate().insert(sqlMap, parameters);
                
        } else {
            sqlMap = getSqlMap("insertChangeLogEntry");
            getSqlMapClientTemplate().insert(sqlMap, entry);
        }

    }


    public String[] discoverLocks(String uri) {

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(
                           uri, SQL_ESCAPE_CHAR));
        parameters.put("timestamp", new Date());

        String sqlMap = getSqlMap("discoverLocks");
        List<Map<String,String>> list = 
            getSqlMapClientTemplate().queryForList(sqlMap, parameters);

        String[] locks = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            locks[i] = list.get(i).get("uri");
        }
        return locks;

    }


    public void storeACL(ResourceImpl r) {
        updateACL(r);
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
            parameters.put("uri", r.getURI());
            parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(
                               r.getURI(), SQL_ESCAPE_CHAR));
            
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
    
    

    public void store(ResourceImpl r) {
        String sqlMap = getSqlMap("loadResourceByUri");
        boolean existed = getSqlMapClientTemplate().queryForObject(sqlMap, r.getURI()) != null;

        Map<String, Object> parameters = getResourceAsMap(r);
        if (!existed) {
            parameters.put("aclInheritedFrom", findNearestACL(r.getURI()));
        }
        parameters.put("depth", SqlDaoUtils.getUriDepth(r.getURI()));

        sqlMap = existed ? getSqlMap("updateResource") : getSqlMap("insertResource");
        if (this.logger.isDebugEnabled()) {
            this.logger.debug((existed? "Updating" : "Storing") + " resource " + r
                              + ", parameter map: " + parameters);
        }

        getSqlMapClientTemplate().update(sqlMap, parameters);

        if (!existed) {
            sqlMap = getSqlMap("loadResourceIdByUri");
            Map map = (Map) getSqlMapClientTemplate().queryForObject(sqlMap, r.getURI());
            Integer id = (Integer) map.get("resourceId");
            r.setID(id.intValue());
        } 

        storeLock(r);
        storeProperties(r);
    }



    public void delete(ResourceImpl resource) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uri", resource.getURI());
        parameters.put("uriWildcard",
                       SqlDaoUtils.getUriSqlWildcard(resource.getURI(),
                                                     SQL_ESCAPE_CHAR));
            
        String sqlMap = getSqlMap("deleteAclEntriesByUri");
        getSqlMapClientTemplate().update(sqlMap, parameters);

        sqlMap = getSqlMap("deleteLocksByUri");
        getSqlMapClientTemplate().update(sqlMap, parameters);

        sqlMap = getSqlMap("deletePropertiesByUri");
        getSqlMapClientTemplate().update(sqlMap, parameters);

        sqlMap = getSqlMap("deleteResourceByUri");
        getSqlMapClientTemplate().update(sqlMap, parameters);
    }


    public ResourceImpl[] loadChildren(ResourceImpl parent) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(
                           parent.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("depth", SqlDaoUtils.getUriDepth(parent.getURI()) + 1);

        List<ResourceImpl> children = new ArrayList<ResourceImpl>();
        String sqlMap = getSqlMap("loadChildren");
        List<Map> resources = getSqlMapClientTemplate().queryForList(sqlMap, parameters);
        Map locks = loadLocksForChildren(parent);

        for (Map resourceMap: resources) {
            String uri = (String) resourceMap.get("uri");

            ResourceImpl resource = createResourceImpl();
            resource.setUri(uri);

            populateStandardProperties(resource, resourceMap);
            
            if (locks.containsKey(uri)) {
                resource.setLock((LockImpl) locks.get(uri));
            }

            children.add(resource);
        }

        ResourceImpl[] result = children.toArray(new ResourceImpl[children.size()]);
        loadChildUrisForChildren(parent, result);
        loadACLs(result);
        loadPropertiesForChildren(parent, result);

        return result;
        
    }


    public String[] discoverACLs(String uri) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(uri, SQL_ESCAPE_CHAR));

        String sqlMap = getSqlMap("discoverAcls");
        List<Map> uris = getSqlMapClientTemplate().queryForList(sqlMap, parameters);
            
        String[] result = new String[uris.size()];
        int n = 0;
        for (Map map: uris) {
            result[n++] = (String) map.get("uri");
        }
        return result;

    }
    
    private void supplyFixedProperties(Map<String, Object> parameters, PropertySet fixedProperties) {
        List<Property> propertyList = fixedProperties.getProperties(Namespace.DEFAULT_NAMESPACE);
        for (Property property: propertyList) {
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
    
    
    public void copy(ResourceImpl resource, ResourceImpl dest,
                     PropertySet newResource, boolean copyACLs,
                     PropertySet fixedProperties) {

        String destURI = newResource.getURI();
        int depthDiff = SqlDaoUtils.getUriDepth(destURI)
            - SqlDaoUtils.getUriDepth(resource.getURI());
    
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uri", resource.getURI());
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(
                           resource.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("destUri", destURI);
        parameters.put("destUriWildcard", SqlDaoUtils.getUriSqlWildcard(
                           destURI, SQL_ESCAPE_CHAR));
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
            parameters.put("uri", destURI);
            parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(
                               destURI, SQL_ESCAPE_CHAR));
            parameters.put("inheritedFrom", destNearestACL);
            parameters.put("previouslyInheritedFrom", srcNearestACL);

            sqlMap = getSqlMap("updateAclInheritedFromByPreviousInheritedFromAndUri");
            getSqlMapClientTemplate().update(sqlMap, parameters);

            if (this.optimizedAclCopySupported) {
                sqlMap = getSqlMap("updateAclInheritedFromByPreviousResourceId");
                getSqlMapClientTemplate().update(sqlMap, parameters);
            } else {
                sqlMap = getSqlMap("loadPreviousInheritedFromMap");

                final List<Map> list = getSqlMapClientTemplate().queryForList(sqlMap, parameters);
                final String batchSqlMap = getSqlMap("updateAclInheritedFromByResourceId");

                getSqlMapClientTemplate().execute(new SqlMapClientCallback() {
                    public Object doInSqlMapClient(SqlMapExecutor executor) throws SQLException {
                        executor.startBatch();
                        for (Map map: list) {
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
            parameters.put("uri", destURI);
            parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(
                               destURI, SQL_ESCAPE_CHAR));
            parameters.put("inheritedFrom", nearestAclNode);

            sqlMap = getSqlMap("updateAclInheritedFromByUri");
            getSqlMapClientTemplate().update(sqlMap, parameters);
        }

        parameters = new HashMap<String, Object>();
        parameters.put("uri", destURI);
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(
                           destURI, SQL_ESCAPE_CHAR));
        sqlMap = getSqlMap("clearPrevResourceIdByUri");
        getSqlMapClientTemplate().update(sqlMap, parameters);

        parameters = getResourceAsMap(dest);
        sqlMap = getSqlMap("updateResource");
        getSqlMapClientTemplate().update(sqlMap, parameters);

        ResourceImpl created = loadResourceInternal(newResource.getURI());
        for (Property prop: newResource.getProperties()) {
            created.addProperty(prop);
            Property fixedProp = fixedProperties != null ?
                fixedProperties.getProperty(prop.getDefinition().getNamespace(), prop.getDefinition().getName()) : null;
            if (fixedProp != null) {
                created.addProperty(fixedProp);
            }
        }
        storeProperties(created);

    }


    public void move(ResourceImpl resource, ResourceImpl newResource) {
        String destURI = newResource.getURI();

        int depthDiff = SqlDaoUtils.getUriDepth(destURI)
            - SqlDaoUtils.getUriDepth(resource.getURI());

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("srcUri", resource.getURI());
        parameters.put("destUri", newResource.getURI());

        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(
                           resource.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("depthDiff", depthDiff);

        String sqlMap = getSqlMap("moveResource");
        getSqlMapClientTemplate().update(sqlMap, parameters);

        sqlMap = getSqlMap("moveDescendants");
        getSqlMapClientTemplate().update(sqlMap, parameters);

        ResourceImpl created = loadResourceInternal(newResource.getURI());
        for (Property prop: newResource.getProperties()) {
            created.addProperty(prop);
        }
        storeProperties(created);

        if (newResource.isInheritedAcl()) {
            int srcNearestAcl = findNearestACL(resource.getURI());
            int nearestAclNode = findNearestACL(newResource.getURI());
            parameters = new HashMap<String, Object>();
            parameters.put("uri", newResource.getURI());
            parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(
                               newResource.getURI(), SQL_ESCAPE_CHAR));
            parameters.put("inheritedFrom", nearestAclNode);
            parameters.put("previouslyInheritedFrom", srcNearestAcl);

            //sqlMap = getSqlMap("updateAclInheritedFromByUri");
            sqlMap = getSqlMap("updateAclInheritedFromByPreviousInheritedFromAndUri");
            getSqlMapClientTemplate().update(sqlMap, parameters);
        }
    }
    

    private void loadChildUris(ResourceImpl parent) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uriWildcard",
                       SqlDaoUtils.getUriSqlWildcard(parent.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("depth", SqlDaoUtils.getUriDepth(parent.getURI()) + 1);

        String sqlMap = getSqlMap("loadChildUrisForChildren");
        List<Map> resourceList = getSqlMapClientTemplate().queryForList(sqlMap, parameters);
        
        String[] childUris = new String[resourceList.size()];
        int n = 0;
        for (Map map: resourceList) {
            childUris[n++] = (String) map.get("uri");
        }

        parent.setChildURIs(childUris);
    }
    

    private void loadChildUrisForChildren(ResourceImpl parent, ResourceImpl[] children) {
        
        // Initialize a map from child.URI to the set of grandchildren's URIs:
        Map<String, Set<String>> childMap = new HashMap<String, Set<String>>();
        for (int i = 0; i < children.length; i++) {
            childMap.put(children[i].getURI(), new HashSet<String>());
        }

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uriWildcard",
                       SqlDaoUtils.getUriSqlWildcard(parent.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("depth", SqlDaoUtils.getUriDepth(parent.getURI()) + 2);

        String sqlMap = getSqlMap("loadChildUrisForChildren");
        List<Map> resourceUris = getSqlMapClientTemplate().queryForList(sqlMap, parameters);

        for (Map map: resourceUris) {
            String uri = (String) map.get("uri");
            String parentUri = URIUtil.getParentURI(uri);
            childMap.get(parentUri).add(uri);
        }

        for (int i = 0; i < children.length; i++) {
            if (!children[i].isCollection()) continue;
            Set<String> childURIs = childMap.get(children[i].getURI());
            children[i].setChildURIs(childURIs.toArray(new String[childURIs.size()]));
        }
    }
    

    private void loadPropertiesForChildren(ResourceImpl parent, ResourceImpl[] resources) {
        if ((resources == null) || (resources.length == 0)) {
            return;
        }

        Map<Integer, ResourceImpl> resourceMap = new HashMap<Integer, ResourceImpl>();

        for (int i = 0; i < resources.length; i++) {
            resourceMap.put(resources[i].getID(), resources[i]);
        }
        
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uriWildcard",
                       SqlDaoUtils.getUriSqlWildcard(parent.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("depth", SqlDaoUtils.getUriDepth(parent.getURI()) + 1);

        String sqlMap = getSqlMap("loadPropertiesForChildren");
        List propertyList = getSqlMapClientTemplate().queryForList(sqlMap, parameters);

        populateCustomProperties(resources, propertyList);
    }



    private Map<String, Lock> loadLocks(String[] uris) {
        if (uris.length == 0) return new HashMap<String, Lock>();
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("uris", java.util.Arrays.asList(uris));
        parameters.put("timestamp", new Date());
        String sqlMap = getSqlMap("loadLocksByUris");

        List<Map<String, ?>> locks = getSqlMapClientTemplate().queryForList(sqlMap, parameters);

        Map<String, Lock> result = new HashMap<String, Lock>();

        for (Map<String, ?> map: locks) {
            LockImpl lock = new LockImpl(
                (String) map.get("token"),
                new Principal((String) map.get("owner"), Principal.Type.USER),
                (String) map.get("ownerInfo"),
                (String) map.get("depth"),
                (Date) map.get("timeout"));
            
            result.put((String) map.get("uri"), lock);
        }
        return result;
    }
    

    private Map<String, Lock> loadLocksForChildren(ResourceImpl parent) {

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("timestamp", new Date());
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(
                           parent.getURI(), SQL_ESCAPE_CHAR));
        parameters.put("depth", SqlDaoUtils.getUriDepth(parent.getURI()) + 1);
        
        String sqlMap = getSqlMap("loadLocksForChildren");
        List locks = getSqlMapClientTemplate().queryForList(sqlMap, parameters);
        Map<String, Lock> result = new HashMap<String, Lock>();

        for (Iterator i = locks.iterator(); i.hasNext();) {
            Map map = (Map) i.next();
            LockImpl lock = new LockImpl(
                (String) map.get("token"),
                new Principal((String) map.get("owner"), Principal.Type.USER),
                (String) map.get("ownerInfo"),
                (String) map.get("depth"),
                (Date) map.get("timeout"));
            
            result.put((String) map.get("uri"), lock);
        }
        return result;
    }
    


    private void insertAcl(final ResourceImpl r) {
        final Map<String, Integer> actionTypes = loadActionTypes();
        final Acl newAcl = r.getAcl();
        if (newAcl == null) {
            throw new DataAccessException("Resource " + r + " has no ACL");
        }
        final Set<RepositoryAction> actions = newAcl.getActions();
        final String sqlMap = getSqlMap("insertAclEntry");

        getSqlMapClientTemplate().execute(new SqlMapClientCallback() {
            public Object doInSqlMapClient(SqlMapExecutor executor) throws SQLException {
                executor.startBatch();
                for (RepositoryAction action: actions) {
                    String actionName = Privilege.getActionName(action);
            
                    for (Principal p: newAcl.getPrincipalSet(action)) {

                        Integer actionID = actionTypes.get(actionName);
                        if (actionID == null) {
                            throw new SQLException("insertAcl(): Unable to "
                                                   + "find id for action '" + action + "'");
                        }

                        Map<String, Object> parameters = new HashMap<String, Object>();
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
        List<Map> list = getSqlMapClientTemplate().queryForList(sqlMap, null);
        for (Map map: list) {
            actionTypes.put((String) map.get("name"), (Integer) map.get("id"));
        }
        return actionTypes;
    }
    
    private boolean isInheritedAcl(ResourceImpl r) {

        String sqlMap = getSqlMap("isInheritedAcl");
        Map map = (Map) getSqlMapClientTemplate().queryForObject(sqlMap, r.getID());
        
        Integer inheritedFrom = (Integer) map.get("inheritedFrom");
        return inheritedFrom != null;
    }       
    


    private int findNearestACL(String uri) {
        
        List<String> path = java.util.Arrays.asList(URLUtil.splitUriIncrementally(uri));
        
        // Reverse list to get deepest URI first
        Collections.reverse(path);
        
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("path", path);
        String sqlMap = getSqlMap("findNearestAclResourceId");
        List<Map> list = getSqlMapClientTemplate().queryForList(sqlMap, parameters);
        Map<String, Integer> uris = new HashMap<String, Integer>();
        for (Map map: list) {
             uris.put((String) map.get("uri"), (Integer) map.get("resourceId"));
        }

        int nearestResourceId = -1;
        for (String candidateUri: path) {
            if (uris.containsKey(candidateUri)) {
                nearestResourceId = uris.get(candidateUri).intValue();
                break;
            }
        }
        if (nearestResourceId == -1) {
            throw new DataAccessException("Database inconsistency: no acl to inherit "
                                          + "from for resource " + uri);
        }
        return nearestResourceId;
    }
    

    private void loadACLs(ResourceImpl[] resources) {

        if (resources.length == 0) return; 

        Set<Integer> resourceIds = new HashSet<Integer>();
        for (int i = 0; i < resources.length; i++) {

            int id = resources[i].isInheritedAcl() ?
                resources[i].getAclInheritedFrom() :
                resources[i].getID();

            resourceIds.add(id);
        }
        Map<Integer, AclImpl> map = loadAclMap(new ArrayList<Integer>(resourceIds));

        if (map.isEmpty()) {
            throw new DataAccessException(
                "Database inconsistency: no ACL entries exist for "
                + "resources " + java.util.Arrays.asList(resources));
        }

        for (ResourceImpl resource: resources) {
            AclImpl acl = null;

            if (resource.getAclInheritedFrom() != -1) {
                acl = map.get(resource.getAclInheritedFrom());
            } else {
                acl = map.get(resource.getID());
            }

            if (acl == null) {
                throw new DataAccessException(
                    "Resource " + resource + " has no ACL entry (ac_inherited_from = "
                    + resource.getAclInheritedFrom() + ")");
            }

            acl = (AclImpl) acl.clone();
            resource.setAcl(acl);
        }
    }
    


    private Map<Integer, AclImpl> loadAclMap(List<Integer> resourceIds) {

        Map<Integer, AclImpl> resultMap = new HashMap<Integer, AclImpl>();
        if (resourceIds.isEmpty()) {
            return resultMap;
        }

        Map<String, Object> parameterMap = new HashMap<String, Object>();
        parameterMap.put("resourceIds", resourceIds);

        String sqlMap = getSqlMap("loadAclEntriesByResourceIds");
        List<Map> aclEntries = getSqlMapClientTemplate().queryForList(sqlMap, parameterMap);
            

        for (Map map: aclEntries) {

            Integer resourceId = (Integer) map.get("resourceId");
            String privilege = (String) map.get("action");

            AclImpl acl = resultMap.get(resourceId);
            
            if (acl == null) {
                acl = new AclImpl();
                resultMap.put(resourceId, acl);
            }
            
            boolean isGroup = "N".equals(map.get("isUser"));
            String name = (String) map.get("principal");
            Principal p = null;

            if (isGroup)
                p = new Principal(name, Principal.Type.GROUP);
            else if (name.startsWith("pseudo:"))
                p = Principal.getPseudoPrincipal(name);
            else
                p = new Principal(name, Principal.Type.USER);
            RepositoryAction action = Privilege.getActionByName(privilege);
            acl.addEntry(action, p);
        }
        return resultMap;
    }
    

    

    private void storeLock(ResourceImpl r) {

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
            parameters.put("depth", lock.getDepth());
            parameters.put("resourceId", r.getID());

            sqlMap = getSqlMap("insertLock");
            getSqlMapClientTemplate().update(sqlMap, parameters);
        }
    }
    


    private void storeProperties(final ResourceImpl r) {
        
        String sqlMap = getSqlMap("deletePropertiesByResourceId");
        getSqlMapClientTemplate().update(sqlMap, r.getID());

        final List<Property> properties = r.getProperties();
        
        if (properties != null) {

            final String batchSqlMap = getSqlMap("insertPropertyEntry");

            getSqlMapClientTemplate().execute(new SqlMapClientCallback() {
                public Object doInSqlMapClient(SqlMapExecutor executor) throws SQLException {
                    executor.startBatch();
                    for (Property property: properties) {

                        if (!PropertyType.SPECIAL_PROPERTIES_SET.contains(property.getDefinition().getName())) {
                            Map<String, Object> parameters = new HashMap<String, Object>();
                            parameters.put("namespaceUri", property.getDefinition().getNamespace().getUri());
                            parameters.put("name", property.getDefinition().getName());
                            parameters.put("resourceId", r.getID());
                    
                            if (property.getDefinition() != null
                                && property.getDefinition().isMultiple()) {

                                Value[] values = property.getValues();
                                for (int i = 0; i < values.length; i++) {
                                    parameters.put("value",
                                                   values[i].getNativeStringRepresentation());
                            
                                    executor.update(batchSqlMap, parameters);
                                }
                            } else {
                                Value value = property.getValue();
                                parameters.put("value", value.getNativeStringRepresentation());
                                executor.update(batchSqlMap, parameters);
                            }
                        }
                    }
                    executor.executeBatch();
                    return null;
                }
            });            
        }
    }
    


    private void populateCustomProperties(ResourceImpl[] resources, List<Map<String, Object>> propertyList) {

        Map<Integer, ResourceImpl> resourceMap = new HashMap<Integer, ResourceImpl>();

        for (ResourceImpl resource: resources) {
            resourceMap.put(resource.getID(), resource);
        }

        Map<SqlDaoUtils.PropHolder, List<String>> propMap =
            new HashMap<SqlDaoUtils.PropHolder, List<String>>();

        for (Map<String, Object> propEntry: propertyList) {

            SqlDaoUtils.PropHolder prop = new SqlDaoUtils.PropHolder();
            prop.namespaceUri = (String) propEntry.get("namespaceUri");
            prop.name = (String) propEntry.get("name");
            prop.resourceId = ((Integer) propEntry.get("resourceId")).intValue();
            
            List<String> values = propMap.get(prop);
            if (values == null) {
                values = new ArrayList<String>();
                prop.values = values;
                propMap.put(prop, values);
            }
            values.add((String) propEntry.get("value"));
        }

        for (SqlDaoUtils.PropHolder prop: propMap.keySet()) {
            
            ResourceImpl r = resourceMap.get(prop.resourceId);

            r.createProperty(prop.namespaceUri, prop.name, 
                    prop.values.toArray(new String[]{}));
        }
    }
    

    public void populateStandardProperties(ResourceImpl resourceImpl,  Map<String, ?> resourceMap) {

        resourceImpl.setID(((Number)resourceMap.get("id")).intValue());
        
        boolean collection = "Y".equals(resourceMap.get("isCollection"));
        resourceImpl.createProperty(
            Namespace.DEFAULT_NAMESPACE, PropertyType.COLLECTION_PROP_NAME,
            Boolean.valueOf(collection));
        
        Principal createdBy = new Principal(
            (String) resourceMap.get("createdBy"), Principal.Type.USER);
        resourceImpl.createProperty(
                Namespace.DEFAULT_NAMESPACE, PropertyType.CREATEDBY_PROP_NAME,
                createdBy);

        resourceImpl.createProperty(
            Namespace.DEFAULT_NAMESPACE, PropertyType.CREATIONTIME_PROP_NAME,
            resourceMap.get("creationTime"));

        Principal principal = new Principal(
            (String) resourceMap.get("owner"), Principal.Type.USER);
        resourceImpl.createProperty(
            Namespace.DEFAULT_NAMESPACE, PropertyType.OWNER_PROP_NAME,
            principal);

        String string = (String) resourceMap.get("contentType");
        if (string != null) {
            resourceImpl.createProperty(
                Namespace.DEFAULT_NAMESPACE, 
                PropertyType.CONTENTTYPE_PROP_NAME,
                string);
        }
        
        string = (String) resourceMap.get("characterEncoding");
        if (string != null) {
            resourceImpl.createProperty(
                Namespace.DEFAULT_NAMESPACE, 
                PropertyType.CHARACTERENCODING_PROP_NAME,
                string);
        }
        
        string = (String) resourceMap.get("guessedCharacterEncoding");
        if (string != null) {
            resourceImpl.createProperty(
                Namespace.DEFAULT_NAMESPACE, 
                PropertyType.CHARACTERENCODING_GUESSED_PROP_NAME,
                string);
        }
        
        string = (String) resourceMap.get("userSpecifiedCharacterEncoding");
        if (string != null) {
            resourceImpl.createProperty(
                Namespace.DEFAULT_NAMESPACE, 
                PropertyType.CHARACTERENCODING_USER_SPECIFIED_PROP_NAME,
                string);
        }
        
        string = (String) resourceMap.get("contentLanguage");
        if (string != null) {
            resourceImpl.createProperty(
                Namespace.DEFAULT_NAMESPACE, 
                PropertyType.CONTENTLOCALE_PROP_NAME,
                string);
        }

        resourceImpl.createProperty(
                Namespace.DEFAULT_NAMESPACE, PropertyType.LASTMODIFIED_PROP_NAME,
                resourceMap.get("lastModified"));

        principal = new Principal((String) resourceMap.get("modifiedBy"), Principal.Type.USER);
        resourceImpl.createProperty(
                Namespace.DEFAULT_NAMESPACE, PropertyType.MODIFIEDBY_PROP_NAME,
                principal);

        resourceImpl.createProperty(
            Namespace.DEFAULT_NAMESPACE, PropertyType.CONTENTLASTMODIFIED_PROP_NAME,
            resourceMap.get("contentLastModified"));

        principal = new Principal((String) resourceMap.get("contentModifiedBy"), Principal.Type.USER);
        resourceImpl.createProperty(
            Namespace.DEFAULT_NAMESPACE, PropertyType.CONTENTMODIFIEDBY_PROP_NAME,
            principal);

        resourceImpl.createProperty(
            Namespace.DEFAULT_NAMESPACE, PropertyType.PROPERTIESLASTMODIFIED_PROP_NAME,
            resourceMap.get("propertiesLastModified"));

        principal = new Principal((String) resourceMap.get("propertiesModifiedBy"), Principal.Type.USER);
        resourceImpl.createProperty(
            Namespace.DEFAULT_NAMESPACE, PropertyType.PROPERTIESMODIFIEDBY_PROP_NAME,
            principal);

        if (!collection) {
            long contentLength = ((Number) resourceMap.get("contentLength")).longValue();
            resourceImpl.createProperty(
                Namespace.DEFAULT_NAMESPACE, PropertyType.CONTENTLENGTH_PROP_NAME,
                new Long(contentLength));
        }
        
        resourceImpl.setResourceType((String) resourceMap.get("resourceType"));

        Integer aclInheritedFrom = (Integer) resourceMap.get("aclInheritedFrom");
        if (aclInheritedFrom == null) {
            aclInheritedFrom = new Integer(-1);
        }

        resourceImpl.setAclInheritedFrom(aclInheritedFrom.intValue());
    }


    
    private Map<String, Object> getResourceAsMap(ResourceImpl r) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("parent", r.getParent());
        // XXX: use Integer (not int) as aclInheritedFrom field:
        map.put("aclInheritedFrom", r.getAclInheritedFrom() == PropertySetImpl.NULL_RESOURCE_ID
                ? null : new Integer(r.getAclInheritedFrom()));
        map.put("uri", r.getURI());
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
    

    
    public Set<Principal> discoverGroups() {
        String sqlMap = getSqlMap("discoverGroups");
        List<String> groupNames = getSqlMapClientTemplate().queryForList(sqlMap, null);
        
        Set<Principal> groups = new HashSet<Principal>();
        for (String groupName: groupNames) {
            Principal group = new Principal(groupName, Principal.Type.GROUP);
            groups.add(group);
        }
            
        return groups;
    }
    
}

