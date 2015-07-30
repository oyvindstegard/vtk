/* Copyright (c) 2007, University of Oslo, Norway
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.dao.DataAccessException;

import vtk.repository.Acl;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.ResourceTypeTree;
import vtk.repository.store.IndexDao;
import vtk.repository.store.PropertySetHandler;
import vtk.repository.store.db.SqlMapDataAccessor.AclHolder;
import vtk.security.PrincipalFactory;

/**
 * Index data accessor based on iBatis.
 * 
 * TODO possible workaround for trouble with binary prop refs going stale
 * (VTK-3887) is to immediately load all values into memory. But this will cause
 * higher memory usage for temporarily cached values, and some more thought
 * needs to be put into the idea.
 */
public class SqlMapIndexDao extends AbstractSqlMapDataAccessor implements IndexDao {

    private static final Log LOG = LogFactory.getLog(SqlMapIndexDao.class);
    
    private ResourceTypeTree resourceTypeTree;

    private PrincipalFactory principalFactory;
    
    private SqlMapDataAccessor sqlMapDataAccessor;
    
    @Override
    public void orderedPropertySetIteration(PropertySetHandler handler) 
        throws DataAccessException { 
        
        SqlSession client = getSqlSession();
        
        String statementId = getSqlMap("orderedPropertySetIteration");

        PropertySetRowHandler rowHandler = 
            new PropertySetRowHandler(handler, this.resourceTypeTree, 
                    this.principalFactory, this, client);

        client.select(statementId, rowHandler);

        rowHandler.handleLastBufferedRows();
    }
    
    @Override
    public void orderedPropertySetIteration(Path startUri, PropertySetHandler handler) 
        throws DataAccessException {
        
        SqlSession client = getSqlSession();

        String statementId = getSqlMap("orderedPropertySetIterationWithStartUri");

        PropertySetRowHandler rowHandler = new PropertySetRowHandler(handler,
                this.resourceTypeTree, this.principalFactory, this, client);

        Map<String, Object> parameters = new HashMap<String, Object>();

        parameters.put("uri", startUri);
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(startUri,
                                      AbstractSqlMapDataAccessor.SQL_ESCAPE_CHAR));

        client.select(statementId, parameters, rowHandler);

        rowHandler.handleLastBufferedRows();
    }
    
    @Override
    public void orderedPropertySetIterationForUris(final List<Path> uris, 
                                              PropertySetHandler handler)
        throws DataAccessException {
        
        if (uris.isEmpty()) {
            return;
        }
        
        SqlSession client = getSqlSession();

        String getSessionIdStatement = getSqlMap("nextTempTableSessionId");
        final Integer sessionID = client.selectOne(getSessionIdStatement);

        final String insertUriTempTableStatement = getSqlMap("insertUriIntoTempTable");
        
        int rowsUpdated = 0;
        int statementCount = 0;
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("sessionId", sessionID);
        
        for (Path uri : uris) {
            params.put("uri", uri.toString());
            client.insert(insertUriTempTableStatement, params);

            if (++statementCount % UPDATE_BATCH_SIZE_LIMIT == 0) {
                // Reached limit of how many inserts we batch, execute current batch immediately
                rowsUpdated += client.flushStatements().size();
            }
        }
        rowsUpdated += client.flushStatements().size();
        
        int batchCount = rowsUpdated;

        if (LOG.isDebugEnabled()) {
            LOG.debug("Number of inserts batched (uri list): " + batchCount);
        }

        String statement = getSqlMap("orderedPropertySetIterationForUris");

        PropertySetRowHandler rowHandler = new PropertySetRowHandler(handler,
                this.resourceTypeTree, this.principalFactory, this, client);
        
        client.select(statement, sessionID, rowHandler);
        
        rowHandler.handleLastBufferedRows();

        // Clean-up temp table
        statement = getSqlMap("deleteFromTempTableBySessionId");
        client.delete(statement, sessionID);
    }
    
    List<Map<String,Object>> loadInheritablePropertyRows(List<Path> paths) {
        String sqlMap = getSqlMap("loadInheritableProperties");
        Map<String, Object> parameterMap = new HashMap<String, Object>();
        parameterMap.put("uris", paths);
        
        return getSqlSession().selectList(sqlMap, parameterMap);
    }
    
    /**
     * Load full ACL for a property set from database. Returned ACL may be <code>null</code>
     * if it no longer exists.
     * 
     * @param resourceId the ID of the resource having the (non-inherited) ACL.
     * @return an <code>Acl</code> instance, possibly {@link Acl#EMPTY_ACL} if the
     * resource did not exist, but never <code>null</code>.
     */
    Acl loadAcl(Integer resourceId, SqlSession sqlSession) {
        List<Integer> resourceIds = new ArrayList<Integer>(1);
        resourceIds.add(resourceId);
        
        Map<Integer, AclHolder> aclMap = 
                new HashMap<Integer, AclHolder>(1);
        
        this.sqlMapDataAccessor.loadAclBatch(resourceIds, aclMap, sqlSession);
        
        AclHolder aclHolder = aclMap.get(resourceId);
        if (aclHolder == null) {
            return Acl.EMPTY_ACL;
        }
        
        return new Acl(aclHolder);
    }
    
    Property createProperty(SqlDaoUtils.PropHolder holder) {
        return this.sqlMapDataAccessor.createProperty(holder);
    }
    
    Property createInheritedProperty(SqlDaoUtils.PropHolder holder) {
        return this.sqlMapDataAccessor.createInheritedProperty(holder);
    }
    
    Property createProperty(Namespace ns, String name, Object value) {
        return this.sqlMapDataAccessor.createProperty(ns, name, value);
    }

    @Required
    public void setSqlMapDataAccessor(SqlMapDataAccessor sqlMapDataAccessor){
        this.sqlMapDataAccessor = sqlMapDataAccessor;
    }
    
    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    @Required
    public void setPrincipalFactory(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }

}
