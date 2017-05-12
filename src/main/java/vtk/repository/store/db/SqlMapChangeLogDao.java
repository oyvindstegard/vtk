/* Copyright (c) 2009,2016 University of Oslo, Norway
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

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.springframework.transaction.annotation.Transactional;

import vtk.repository.ChangeLogEntry;
import vtk.repository.RepositoryImpl;
import vtk.repository.store.DataAccessException;
import vtk.repository.store.ChangeLogDao;

/**
 * This DAO has {@code @Transactional} methods since they can be called outside the
 * context of transactional methods in {@link RepositoryImpl}.
 */
public class SqlMapChangeLogDao extends AbstractSqlMapDataAccessor implements ChangeLogDao {
	
    @Override
    @Transactional(readOnly = true)
    public List<ChangeLogEntry> getChangeLogEntries(int loggerType, int loggerId, Date olderThan, int limit) {
        SqlSession client = getSqlSession();

        if (limit < 0) {
            limit = Integer.MAX_VALUE;
        }

        Map<String, Object> params = new HashMap<>(5, 1f);
        params.put("loggerType", loggerType);
        params.put("loggerId", loggerId);
        params.put("olderThan", olderThan);
        params.put("limit", limit);

        return client.selectList(getSqlMap("getChangeLogEntries"), params);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChangeLogEntry> getChangeLogEntries(int loggerType, int loggerId, int limit) 
        throws DataAccessException {

        if (limit < 0) {
            limit = Integer.MAX_VALUE;
        }
        
        SqlSession client = getSqlSession();

        Map<String, Object> params = new HashMap<>(4, 1f);
        params.put("loggerType", loggerType);
        params.put("loggerId", loggerId);
        params.put("limit", limit);
        
        String statement = getSqlMap("getChangeLogEntries");
        
        List<ChangeLogEntry> retlist = client.selectList(statement, params);
        
        return retlist;
    }

    @Override
    @Transactional(readOnly = true)
    public int countChangeLogEntries(int loggerType, int loggerId) {
        final SqlSession client = getSqlSession();

        Map<String, Object> params = new HashMap<>(3, 1f);
        params.put("loggerType", loggerType);
        params.put("loggerId", loggerId);

        return client.<Integer>selectOne(getSqlMap("countChangeLogEntries"), params);
    }

    @Override
    @Transactional(readOnly = true)
    public int countChangeLogEntries(int loggerType, int loggerId, Date olderThan) {
        final SqlSession client = getSqlSession();

        Map<String, Object> params = new HashMap<>(4, 1f);
        params.put("loggerType", loggerType);
        params.put("loggerId", loggerId);
        params.put("olderThan", olderThan);

        return client.<Integer>selectOne(getSqlMap("countChangeLogEntries"), params);
    }

    @Override
    @Transactional(readOnly = false)
    public int removeChangeLogEntries(final List<ChangeLogEntry> entries)
        throws DataAccessException {

        final SqlSession client = getSqlSession();

        final Integer sessionId = client.selectOne(getSqlMap("nextTempTableSessionId"));

        Map<String, Object> params = new HashMap<>(3, 1f);
        params.put("sessionId", sessionId);

        // Batch inserts into temporary table and respect UPDATE_BATCH_SIZE_LIMIT
        int statementCount = 0;
        int rowsUpdated = 0;
        final String insertStatement = getSqlMap("insertChangelogEntryIdIntoTempTable");
        for (ChangeLogEntry entry: entries) {
            params.put("changelogEntryId", entry.getChangeLogEntryId());
            // Insert into vortex_tmp:
            client.insert(insertStatement, params);

            if (++statementCount % UPDATE_BATCH_SIZE_LIMIT == 0) {
                // Reached limit of how many inserts we batch, execute current batch immediately
                rowsUpdated += client.flushStatements().size();
            }
        }
        // Execute anything remaining in last batch
        rowsUpdated += client.flushStatements().size();

        // Delete from changelog_entry:
        client.delete(getSqlMap("removeChangelogEntriesByTempTable"), sessionId);

        // Flush session entries from vortex_tmp:
        client.delete(getSqlMap("deleteFromTempTableBySessionId"), sessionId);

        return rowsUpdated;
    }

    @Override
    @Transactional(readOnly = false)
    public void addChangeLogEntries(List<ChangeLogEntry> entries, DescendantsSpec generate) throws DataAccessException {
        SqlSession client = getSqlSession();
        for (ChangeLogEntry entry: entries) {
            addChangeLogEntryInternal(entry, generate, client);
        }
    }

    @Override
    @Transactional(readOnly = false)
    public void addChangeLogEntry(ChangeLogEntry entry, DescendantsSpec generate) throws DataAccessException {
        addChangeLogEntryInternal(entry, generate, getSqlSession());
    }
    
    private void addChangeLogEntryInternal(ChangeLogEntry entry, DescendantsSpec generate, SqlSession client) throws DataAccessException {
        String sqlMap;
        switch (generate) {
            case NONE:
                sqlMap = getSqlMap("insertChangeLogEntry");
                client.insert(sqlMap, entry);
                break;
            
            case SUBTREE:
                if (entry.isCollection()) {
                    sqlMap = getSqlMap("insertChangeLogEntriesRecursively");
                    Map<String, Object> parameters = new HashMap<>(3, 1f);
                    parameters.put("entry", entry);
                    parameters.put("uriWildcard",
                            SqlDaoUtils.getUriSqlWildcard(entry.getUri(), SQL_ESCAPE_CHAR));

                    client.insert(sqlMap, parameters);
                } else {
                    sqlMap = getSqlMap("insertChangeLogEntry");
                    client.insert(sqlMap, entry);
                }
                break;

            case ACL_INHERITED:
                sqlMap = getSqlMap("insertChangeLogEntryInherited");
                client.insert(sqlMap, entry);
                break;
                
            case ACL_INHERITED_TO_INHERITANCE:
                sqlMap = getSqlMap("insertChangeLogEntryInheritedToInheritance");
                Map<String, Object> parameters = new HashMap<>(3, 1f);
                parameters.put("entry", entry);
                parameters.put("uriWildcard",
                        SqlDaoUtils.getUriSqlWildcard(entry.getUri(), SQL_ESCAPE_CHAR));
                client.insert(sqlMap, parameters);
                break;
                
            default:
                throw new IllegalArgumentException("Unsupported generate mode: " + generate);
        }
    }

}
