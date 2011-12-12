/* Copyright (c) 2011, University of Oslo, Norway
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
package org.vortikal.repository.store;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.orm.ibatis.SqlMapClientCallback;
import org.vortikal.repository.Acl;
import org.vortikal.repository.Path;
import org.vortikal.repository.Privilege;
import org.vortikal.repository.Resource;
import org.vortikal.repository.ResourceImpl;
import org.vortikal.repository.Revision;
import org.vortikal.repository.content.InputStreamWrapper;
import org.vortikal.repository.store.db.AbstractSqlMapDataAccessor;
import org.vortikal.security.Principal;
import org.vortikal.security.Principal.Type;
import org.vortikal.security.PrincipalFactory;
import org.vortikal.util.io.StreamUtil;

import com.ibatis.sqlmap.client.SqlMapExecutor;

public class DefaultRevisionStore extends AbstractSqlMapDataAccessor implements RevisionStore {

    private static final int COPY_BUF_SIZE = 122880;
    
    private String revisionDirectory;
    private PrincipalFactory principalFactory;
    
    @Required
    public void setPrincipalFactory(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }
    
    @Override
    public List<Revision> list(Resource resource) {

        String sqlMap = getSqlMap("loadResourceIdByUri");
        @SuppressWarnings("unchecked")
        Map<String, Object> idMap = (Map<String, Object>) getSqlMapClientTemplate().queryForObject(sqlMap,
                resource.getURI().toString());
        Integer id = (Integer) idMap.get("resourceId");

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("resourceId", id);
        
        Map<Long, Acl> aclMap = loadAclMap(id);
            
        sqlMap = getSqlMap("listRevisionsByResource");
        List<Revision> result = new ArrayList<Revision>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> revisions =
            getSqlMapClientTemplate().queryForList(sqlMap, parameters);

        for (Map<String, Object> map: revisions) {
            Number n = (Number) map.get("id");
            long revId = n.longValue();
            Date timestamp = new Date(((Timestamp) map.get("timestamp")).getTime());
            String name = (String) map.get("name");
            String uid = map.get("uid").toString();
            String checksum = map.get("checksum").toString();
            n = (Number) map.get("changeAmount");
            Integer changeAmount = n != null ? n.intValue() : null;
            Revision.Type type = Revision.Type.WORKING_COPY.name().equals(name) ? 
                    Revision.Type.WORKING_COPY : Revision.Type.REGULAR;
            Acl acl = aclMap.get(revId);
            
            Revision.Builder builder = Revision.newBuilder();
            Revision rev = builder.id(revId)
                    .type(type)
                    .name(name)
                    .uid(uid)
                    .timestamp(timestamp)
                    .acl(acl)
                    .checksum(checksum)
                    .changeAmount(changeAmount)
                    .build();
            result.add(rev);
        }         
        return Collections.unmodifiableList(result);
    }

    @Override
    public long newRevisionID() {
        return (Long) getSqlMapClientTemplate()
                .queryForObject(getSqlMap("nextRevisionID"));
    }

    @Override
    public void create(ResourceImpl resource, Revision revision, InputStream content) {
        insertRevision(resource, revision);
        File revisionFile = revisionFile(resource, revision, true);
        if (!revisionFile.exists()) {
            throw new DataAccessException("Cannot create revision " + revision.getID() 
                    + ", unable to create file: " + revisionFile.getAbsolutePath());
        }
        try {
            FileOutputStream out = new FileOutputStream(revisionFile);
            StreamUtil.pipe(content, out, COPY_BUF_SIZE, true);
        } catch (IOException e) {
            throw new DataAccessException(e);
        }
    }
    
    private void insertRevision(ResourceImpl resource, Revision revision) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("resourceId", resource.getID());
        parameters.put("revisionId", revision.getID());
        parameters.put("uid", revision.getUid());
        parameters.put("name", revision.getName());
        parameters.put("timestamp", revision.getTimestamp());
        parameters.put("checksum", revision.getChecksum());
        parameters.put("changeAmount", revision.getChangeAmount());
        
        String sqlMap = getSqlMap("insertRevision");
        getSqlMapClientTemplate().insert(sqlMap, parameters);

        insertAcl(resource, revision);
        
        List<Revision> list = list(resource);
        Revision found = null;
        
        for (Revision r: list) {
            if (revision.getID() == r.getID()) {
                found = r;
            }
        }
        if (found == null) {
            throw new IllegalStateException("Newly inserted revision not found");
        }
    }

    @Override
    public void delete(ResourceImpl resource, Revision revision) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("resourceId", resource.getID());
        parameters.put("revisionId", revision.getID());
        String sqlMap = getSqlMap("deleteRevision");
        getSqlMapClientTemplate().delete(sqlMap, parameters);
        
        File revisionFile = revisionFile(resource, revision, false);
        if (!revisionFile.exists()) {
            throw new DataAccessException("Cannot delete revision " + revision.getID() 
                    + ", file does not exist: " + revisionFile.getAbsolutePath());
        }
        if (!revisionFile.delete()) {
            throw new DataAccessException("Cannot delete revision " + revision.getID() 
                    + ", unable to delete file: " + revisionFile.getAbsolutePath());
        }
    }

    
    @Override
    public InputStreamWrapper getContent(ResourceImpl resource, Revision revision)
            throws DataAccessException {
        File revisionFile = revisionFile(resource, revision, false);
        if (!revisionFile.exists()) {
            throw new DataAccessException("Unable to find revision " + revision 
                    + ": no file: " + revisionFile.getAbsolutePath());
        }
        try {
            InputStream in = new FileInputStream(revisionFile);
            return new InputStreamWrapper(in, Path.fromString(revisionFile.getAbsolutePath()));
        } catch (IOException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public long getContentLength(ResourceImpl resource, Revision revision) throws DataAccessException {
        File revisionFile = revisionFile(resource, revision, false);
        if (!revisionFile.exists()) {
            throw new DataAccessException("Unable to find revision " + revision 
                    + ": no file: " + revisionFile.getAbsolutePath());
        }
        return revisionFile.length();
    }
    
    

    @Override
    public void store(ResourceImpl resource, Revision revision, InputStream content)
            throws DataAccessException {
        try {
            
            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("resourceId", resource.getID());
            parameters.put("revisionId", revision.getID());
            parameters.put("uid", revision.getUid());
            parameters.put("name", revision.getName());
            parameters.put("timestamp", revision.getTimestamp());
            parameters.put("checksum", revision.getChecksum());
            parameters.put("changeAmount", revision.getChangeAmount());
            
            String sqlMap = getSqlMap("updateRevision");
            getSqlMapClientTemplate().update(sqlMap, parameters);
            
            File dest = revisionFile(resource, revision, true);
            
            // Go via a temporary file in case the source input stream is 
            // passed as the content parameter:
            File tmp = File.createTempFile("foo", "bar");
            FileOutputStream outputStream = new FileOutputStream(tmp);
            StreamUtil.pipe(content, outputStream, COPY_BUF_SIZE, true);

            // File.renameTo() is unstable sometimes:
            FileInputStream srcStream = new FileInputStream(tmp);
            FileOutputStream destStream = new FileOutputStream(dest);
            StreamUtil.pipe(srcStream, destStream, COPY_BUF_SIZE, true);

            /*
            if (!tmp.renameTo(dest)) {
                throw new DataAccessException("Store revision content [" + revision + "] " 
                        + "failed: unable to rename file " + tmp + " to " + dest);
            }
            */
        } catch (IOException e) {
            throw new DataAccessException("Store revision content [" + revision + "] failed", e);
        }
    }
    
    
    private void insertAcl(final ResourceImpl resource, final Revision revision) {
        final Map<String, Integer> actionTypes = loadActionTypes();
        final Acl acl = revision.getAcl();
        if (acl == null) {
            throw new DataAccessException("Revision has no ACL: " + revision);
        }
        final Set<Privilege> actions = acl.getActions();
        final String sqlMap = getSqlMap("insertRevisionAclEntry");
        getSqlMapClientTemplate().execute(new SqlMapClientCallback() {
            @Override
            public Object doInSqlMapClient(SqlMapExecutor executor) throws SQLException {
                executor.startBatch();
                Map<String, Object> parameters = new HashMap<String, Object>();
                for (Privilege action : actions) {
                    String actionName = action.getName();
                    for (Principal p : acl.getPrincipalSet(action)) {

                        Integer actionID = actionTypes.get(actionName);
                        if (actionID == null) {
                            throw new SQLException(
                                    "insertAcl(): No action id exists for action '" + action + "'");
                        }

                        parameters.put("actionId", actionID);
                        parameters.put("revisionId", revision.getID());
                        parameters.put("principal", p.getQualifiedName());
                        parameters.put("isUser", p.getType() == Principal.Type.GROUP ? "N" : "Y");
                        parameters.put("grantedBy", resource.getOwner().getQualifiedName());
                        parameters.put("grantedDate", new Date());
                        executor.insert(sqlMap, parameters);
                    }
                }
                executor.executeBatch();
                return null;
            }
        });
    }
    

    private Map<Long, Acl> loadAclMap(Integer resourceID) {
        String sqlMap = getSqlMap("listRevisionAclEntriesByResource");
        Map<String, Integer> parameters = Collections.singletonMap("resourceId", resourceID);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> acls =
            getSqlMapClientTemplate().queryForList(sqlMap, parameters);

        Map<Long, AclHolder> aclMap = new HashMap<Long, AclHolder>();
        

        for (Map<String, Object> map: acls) {
            Long revisionID = (Long) map.get("revisionId");
            AclHolder holder = aclMap.get(revisionID);
            if (holder == null) {
                holder = new AclHolder();
                aclMap.put(revisionID, holder);
            }
            String privilege = (String) map.get("action");
            String name = (String) map.get("principal");
            boolean isGroup = "N".equals(map.get("isUser"));
            
            Principal p = isGroup ? 
                    principalFactory.getPrincipal(name, Type.GROUP)
                    : name.startsWith("pseudo:") ? 
                            principalFactory.getPrincipal(name, Type.PSEUDO)
                                : principalFactory.getPrincipal(name, Type.USER);
            Privilege action = Privilege.forName(privilege);
            holder.addEntry(action, p);
        }
        Map<Long, Acl> result = new HashMap<Long, Acl>();
        for (Map.Entry<Long, AclHolder> entry: aclMap.entrySet()) {
            result.put(entry.getKey(), new Acl(entry.getValue()));
        }
        return result;
    }
        
    
    private File revisionFile(ResourceImpl resource, Revision revision, boolean create) {
        long resourceID = resource.getID();
        long revisionID = revision.getID();
        String basePath = revisionPath(resourceID);
        File dir = new File(basePath);
        if (!dir.exists() && !create) {
            throw new DataAccessException("Directory does not exist: " + dir);
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new DataAccessException("Unable to create directory: " + dir);
        }
        File file = new File(basePath + File.separator + String.valueOf(revisionID));
        if (!file.exists() && !create) {
            throw new DataAccessException("File does not exist: " + dir);
        }
        if (!file.exists()) {
            try {
                if (!file.createNewFile()) {
                    throw new IOException("File.createNewFile() returned false");
                }
            } catch (IOException e) {                
                throw new DataAccessException("Unable to create file: " + file, e);
            }
        }
        return file;
    }
    
    private String revisionPath(long resourceID) {
        StringBuilder result = new StringBuilder();
        for (int i = 7; i >= 0; i--) {
            long n = resourceID >> (i * 8) & 0xff;
            String s = (n < 0xf) ? "0" + Long.toHexString(n) : Long.toHexString(n);
            result.append(s);
            if (i > 0) result.append(File.separator);
        }
        return this.revisionDirectory + File.separator + result.toString();
    }

    @Required
    public void setRevisionDirectory(String revisionDirectory) {
        this.revisionDirectory = revisionDirectory;
        if (!createRootDirectory(revisionDirectory)) {
            throw new IllegalStateException(
                    "Unable to create directory " + revisionDirectory);
        }
    }
    
    private boolean createRootDirectory(String directoryPath) {
        File root = new File(directoryPath);

        if (!root.isAbsolute()) {
            directoryPath = System.getProperty("vortex.home") + File.separator + directoryPath;
            root = new File(directoryPath);
        }

        if (!root.exists()) {
            if (!root.mkdir()) {
                return false;
            }
        }
        return true;
    }
    
    public void gc() throws IOException {
        Set<Long> batch = new HashSet<Long>();
        traverse(new File(this.revisionDirectory), 0, batch);
        if (batch.size() > 0) {
            clean(batch);
        }
    }

    private void traverse(File dir, int level, Set<Long> batch) throws IOException {

        File[] children = dir.listFiles();
        for (File child : children) {
            if (level > 7 || child.isFile()) {
                continue;
            }
            if (level == 7) {
                Long id = getID(child);
                batch.add(id);
            }
            if (batch.size() >= 100) {
                clean(batch);
            }
            traverse(child, level + 1, batch);
        }
    }
    
    private Long getID(File dir) {
        StringBuilder name = new StringBuilder();
        File cur = dir;
        for (int i = 0; i < 8; i++) {
            String n = cur.getName();
            name.insert(0, n);
            cur = new File(cur.getParent());
        }
        return Long.parseLong(name.toString(), 16);
    }

    private void clean(Set<Long> batch) {
        filterDeleted(batch);
        if (batch.size() > 0) {
            purgeDeleted(batch);
        }
    }
    
    private void filterDeleted(Set<Long> batch) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("resourceIds", new ArrayList<Long>(batch));
        
        String sqlMap = getSqlMap("listRevisionsByResourceIds");

        @SuppressWarnings("unchecked")
        List<Long> revisions =
            getSqlMapClientTemplate().queryForList(sqlMap, parameters);
        for (Long id: revisions) {
            if (batch.contains(id)) {
                batch.remove(id);
            }
        }
    }
    
    private void purgeDeleted(Set<Long> batch) {
        for (Long resourceID: batch) {
            File revisionDir = new File(revisionPath(resourceID));
            if (!revisionDir.exists()) {
                continue;
            }
            File[] children = revisionDir.listFiles();
            for (File child: children) {
                if (!child.delete()) {
                    throw new IllegalStateException("Unable to delete: " + child);
                }
            }
            if (!revisionDir.delete()) {
                throw new IllegalStateException("Unable to delete: " + revisionDir);
            }
        }
        batch.clear();
    }


    /**
     * Duplicate of {@link org.vortikal.repository.store.db.SqlMapDataAccessor#loadActionTypes}
     */
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

    /**
     * Duplicate of {@link org.vortikal.repository.store.db.SqlMapDataAccessor.AclHolder}
     */
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
}
