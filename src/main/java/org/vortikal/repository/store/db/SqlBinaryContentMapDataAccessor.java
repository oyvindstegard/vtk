/* Copyright (c) 2008, 2009, University of Oslo, Norway
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.vortikal.repository.ContentStream;
import org.vortikal.repository.store.BinaryContentDataAccessor;

public class SqlBinaryContentMapDataAccessor extends AbstractSqlMapDataAccessor implements BinaryContentDataAccessor {

    private static final Logger log = Logger.getLogger(SqlBinaryContentMapDataAccessor.class);

    public ContentStream getBinaryStream(String binaryRef) {
        try {
            Integer binaryId = Integer.valueOf(binaryRef);
            String sqlMap = getSqlMap("selectBinaryPropertyEntry");
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("binaryRef", binaryId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resultList = getSqlMapClientTemplate().queryForList(sqlMap, params);
            return (ContentStream) resultList.get(0).get("binaryStream");
        } catch (Exception e) {
            log.error("An error occured while getting the binary stream for property with id: " + binaryRef, e);
        }
        return null;
    }

    public String getBinaryMimeType(String binaryRef) {
        try {
            Integer binaryId = Integer.valueOf(binaryRef);
            String sqlMap = getSqlMap("selectBinaryMimeTypeEntry");
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("binaryRef", binaryId);
            String binaryMimeType = (String) getSqlMapClientTemplate().queryForObject(sqlMap, params);
            return binaryMimeType;
        } catch (Exception e) {
            log.error("An error occured while getting the binary mimetype for property with id: " + binaryRef, e);
        }
        return null;
    }

}