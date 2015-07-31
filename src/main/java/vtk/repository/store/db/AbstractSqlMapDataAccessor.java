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

import static org.springframework.util.Assert.notNull;

import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.dao.support.DaoSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public abstract class AbstractSqlMapDataAccessor extends DaoSupport {

    private Map<String, String> sqlMaps;
    private SqlSession sqlSession;
    private SqlSession batchSqlSession;

    /**
     * The escape character used in SQL.
     */
    public static final char SQL_ESCAPE_CHAR = '@';
    
    /**
     * General limit on size of SQL batch updates (number of statements in batch).
     * Respecting this limit helps to avoid hangs on batched inserts through JDBC 
     * (observed on Oracle in test, at least).
     */
    public static final int UPDATE_BATCH_SIZE_LIMIT = 512;

    @Required
    public void setSqlMaps(Map<String, String> sqlMaps) {
        this.sqlMaps = sqlMaps;
    }
    
    public void setSqlSession(SqlSession sqlSession) {
        this.sqlSession = sqlSession;
    }
    
    public void setBatchSqlSession(SqlSession batchSqlSession) {
        this.batchSqlSession = batchSqlSession;
    }
    
    protected final String getSqlMap(String statementId) {
        if (this.sqlMaps.containsKey(statementId)) {
            return this.sqlMaps.get(statementId);
        }
        return statementId;
    }

    protected final SqlSession getSqlSession() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            String name = TransactionSynchronizationManager.getCurrentTransactionName();
            if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Transaction " + name + ": read-only=true");
                }
                return sqlSession;
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Transaction " + name + ": read-only=false");
            }
            return batchSqlSession;
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Unknown transaction, assume read-only=false");
        }
        return batchSqlSession;
    }
    
    protected void checkDaoConfig() {
        notNull(sqlSession, "Property 'sqlSession' not configured");
        notNull(batchSqlSession, "Property 'batchSqlSession' not configured");
    }
    
}
