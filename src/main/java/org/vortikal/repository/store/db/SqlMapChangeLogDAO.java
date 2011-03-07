/* Copyright (c) 2009, University of Oslo, Norway
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
import java.util.List;
import java.util.Map;

import org.springframework.orm.ibatis.SqlMapClientCallback;
import org.springframework.orm.ibatis.SqlMapClientTemplate;
import org.vortikal.repository.ChangeLogEntry;
import org.vortikal.repository.store.ChangeLogDAO;
import org.vortikal.repository.store.DataAccessException;

import com.ibatis.sqlmap.client.SqlMapExecutor;

public class SqlMapChangeLogDAO extends AbstractSqlMapDataAccessor 
    implements ChangeLogDAO {
	
    @SuppressWarnings("unchecked")
    @Override
    public void removeChangeLogEntries(final List<ChangeLogEntry> entries)
        throws DataAccessException {
    	
        final SqlMapClientTemplate client = getSqlMapClientTemplate();
        
        String statement = getSqlMap("nextTempTableSessionId");
        
        final Integer sessionId = (Integer)client.queryForObject(statement);
        
        final SqlMapClientCallback callback = new SqlMapClientCallback() {

            @Override
            public Object doInSqlMapClient(SqlMapExecutor executor)
                    throws SQLException {

                Map params = new HashMap();
                params.put("sessionId", sessionId);
                String statement = getSqlMap("insertChangelogEntryIdIntoTempTable");
                
                // Batch inserts into temporary table and respect UPDATE_BATCH_SIZE_LIMIT
                int statementCount = 0;
                int rowsUpdated = 0;
                executor.startBatch();
                for (ChangeLogEntry entry: entries) {
                    params.put("changelogEntryId", entry.getChangeLogEntryId());
                    executor.insert(statement, params);
                    
                    if (++statementCount % UPDATE_BATCH_SIZE_LIMIT == 0) {
                    	// Reached limit of how many inserts we batch, execute current batch immediately
                    	rowsUpdated += executor.executeBatch();
                    	executor.startBatch(); // Start new batch immediately
                    }
                }
                // Execute anything remaining in last batch
                rowsUpdated += executor.executeBatch();
                
                return new Integer(rowsUpdated);
            }
        };
        
        // Insert into vortex_tmp:
        client.execute(callback);
        
        // Delete from changelog_entry:
        statement = getSqlMap("removeChangelogEntriesByTempTable");
        client.delete(statement, sessionId);
        
        // Flush session entries from vortex_tmp:
        statement = getSqlMap("deleteFromTempTableBySessionId");
        client.delete(statement, sessionId);
        
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<ChangeLogEntry> getChangeLogEntries(int loggerType, int loggerId) 
        throws DataAccessException {
        
        SqlMapClientTemplate client = getSqlMapClientTemplate();

        Map params = new HashMap();
        params.put("loggerType", loggerType);
        params.put("loggerId", loggerId);
        
        String statement = getSqlMap("getChangeLogEntries");
        
        return client.queryForList(statement, params);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<ChangeLogEntry> getChangeLogEntries(int loggerType, int loggerId, int limit) 
        throws DataAccessException {
        
        SqlMapClientTemplate client = getSqlMapClientTemplate();

        Map params = new HashMap();
        params.put("loggerType", loggerType);
        params.put("loggerId", loggerId);
        params.put("limit", limit);
        
        String statement = getSqlMap("getChangeLogEntries");
        
        List<ChangeLogEntry> retlist = client.queryForList(statement, params);
        
        return retlist;
    }

    @Override
    public void addChangeLogEntry(ChangeLogEntry entry, boolean recurse) 
        throws DataAccessException {
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

    @Override
    public void addChangeLogEntryInherited(ChangeLogEntry entry) 
        throws DataAccessException {
        String sqlMap = null;
        sqlMap = getSqlMap("insertChangeLogEntryInherited");
        getSqlMapClientTemplate().insert(sqlMap, entry);
    }

    @Override
    public void addChangeLogEntryInheritedToInheritance(ChangeLogEntry entry) 
        throws DataAccessException {
        String sqlMap = null;
        
        sqlMap = getSqlMap("insertChangeLogEntryInheritedToInheritance");
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("entry", entry);
        parameters.put("uriWildcard", 
                       SqlDaoUtils.getUriSqlWildcard(entry.getUri(), SQL_ESCAPE_CHAR));
        getSqlMapClientTemplate().insert(sqlMap, parameters);
    }

}
