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
package org.vortikal.repository.store.db;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.ibatis.SqlMapClientCallback;
import org.springframework.orm.ibatis.SqlMapClientTemplate;
import org.vortikal.repository.Path;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.PropertySetImpl;
import org.vortikal.repository.ResourceTypeTree;
import org.vortikal.repository.store.IndexDao;
import org.vortikal.repository.store.PropertySetHandler;
import org.vortikal.security.Principal;
import org.vortikal.security.PrincipalFactory;

import com.ibatis.sqlmap.client.SqlMapExecutor;

/**
 * Index data accessor based on iBatis.
 */
public class SqlMapIndexDao extends AbstractSqlMapDataAccessor implements IndexDao {

    private static final Log LOG = LogFactory.getLog(SqlMapIndexDao.class);
    
    private ResourceTypeTree resourceTypeTree;

    private PrincipalFactory principalFactory;
    
    @Override
    public void orderedPropertySetIteration(PropertySetHandler handler) 
        throws DataAccessException { 

        SqlMapClientTemplate client = getSqlMapClientTemplate();
        String statementId = getSqlMap("orderedPropertySetIteration");

        PropertySetRowHandler rowHandler = 
            new PropertySetRowHandler(handler, this.resourceTypeTree, this.principalFactory, this);

        client.queryWithRowHandler(statementId, rowHandler);

        rowHandler.handleLastBufferedRows();
    }
    
    @Override
    public void orderedPropertySetIteration(Path startUri, PropertySetHandler handler) 
        throws DataAccessException {
        
        SqlMapClientTemplate client = getSqlMapClientTemplate();

        String statementId = getSqlMap("orderedPropertySetIterationWithStartUri");

        PropertySetRowHandler rowHandler = new PropertySetRowHandler(handler,
                this.resourceTypeTree, this.principalFactory, this);

        Map<String, Object> parameters = new HashMap<String, Object>();

        parameters.put("uri", startUri);
        parameters.put("uriWildcard", SqlDaoUtils.getUriSqlWildcard(startUri,
                                      AbstractSqlMapDataAccessor.SQL_ESCAPE_CHAR));

        client.queryWithRowHandler(statementId, parameters, rowHandler);

        rowHandler.handleLastBufferedRows();
    }
    
    @Override
    public void orderedPropertySetIterationForUris(final List<Path> uris, 
                                              PropertySetHandler handler)
        throws DataAccessException {
        
        if (uris.isEmpty()) {
            return;
        }
        
        SqlMapClientTemplate client = getSqlMapClientTemplate();

        String getSessionIdStatement = getSqlMap("nextTempTableSessionId");

        final Integer sessionId = (Integer) client
                .queryForObject(getSessionIdStatement);

        final String insertUriTempTableStatement = getSqlMap("insertUriIntoTempTable");

        Integer batchCount = (Integer) client.execute(new SqlMapClientCallback() {
            @Override
            public Object doInSqlMapClient(SqlMapExecutor sqlMapExec)
                    throws SQLException {

                int rowsUpdated = 0;
                int statementCount = 0;
                Map<String, Object> params = new HashMap<String, Object>();
                params.put("sessionId", sessionId);
                sqlMapExec.startBatch();
                for (Path uri : uris) {
                    params.put("uri", uri.toString());
                    sqlMapExec.insert(insertUriTempTableStatement, params);

                    if (++statementCount % UPDATE_BATCH_SIZE_LIMIT == 0) {
                        // Reached limit of how many inserts we batch, execute current batch immediately
                        rowsUpdated += sqlMapExec.executeBatch();
                        sqlMapExec.startBatch();
                    }
                }
                rowsUpdated += sqlMapExec.executeBatch();
                return new Integer(rowsUpdated);
            }
        });

        if (LOG.isDebugEnabled()) {
            LOG.debug("Number of inserts batched (uri list): " + batchCount);
        }

        String statement = getSqlMap("orderedPropertySetIterationForUris");

        PropertySetRowHandler rowHandler = new PropertySetRowHandler(handler,
                this.resourceTypeTree, this.principalFactory, this);

        client.queryWithRowHandler(statement, sessionId, rowHandler);

        rowHandler.handleLastBufferedRows();

        // Clean-up temp table
        statement = getSqlMap("deleteFromTempTableBySessionId");
        client.delete(statement, sessionId);

    }
    
    /**
     * Fetch a set of principals (normal principals, pseudo-principals and groups) 
     * which are allowed to read or read-processed the resource that the property set
     * represents.
     * 
     * @return <code>null</code> if the resource or the resource from which ACL is
     *                           inherited could not be found. Otherwise 
     *                           a <code>Set</code> of <code>Principal</code> instances. 
     */
    protected Set<Principal> getAclReadPrincipals(PropertySet propertySet)
            throws org.vortikal.repository.store.DataAccessException {
        
        // Cast to impl
        PropertySetImpl propSetImpl = (PropertySetImpl)propertySet;
        
        Set<Principal> aclReadPrincipals = new HashSet<Principal>();
        
        // Now determine where to fetch the rest of the ACL information (either from self or an ancestor)
        int aclResourceId = propSetImpl.isInheritedAcl() ? 
                    propSetImpl.getAclInheritedFrom() : propSetImpl.getID();
        
        // Fetch ACL from database for the given resource/node
        String statement = getSqlMap("getAclReadPrincipalNames");
        SqlMapClientTemplate client = getSqlMapClientTemplate();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> principalAttributeList = 
                                    client.queryForList(statement, aclResourceId);

        if (principalAttributeList.isEmpty()) {
            // Resource is gone, return null
            return null;
        } else {
            for (Map<String, Object> principalAttributes: principalAttributeList) {
                String name = (String) principalAttributes.get("name");
                Boolean isUser = (Boolean) principalAttributes.get("isUser");
                
                Principal.Type type;
                if (name.startsWith("pseudo:")) {
                    type = Principal.Type.PSEUDO;
                } else if (isUser.booleanValue()) {
                    type = Principal.Type.USER;
                } else {
                    type = Principal.Type.GROUP;
                }
                
                Principal principal = this.principalFactory.getPrincipal(name, type, false);
                aclReadPrincipals.add(principal);
            }
        }
        
        return aclReadPrincipals;
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
