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
package vtk.repository.store;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Acl;
import vtk.repository.Privilege;
import vtk.repository.Resource;
import vtk.repository.ResourceImpl;
import vtk.repository.Revision;
import vtk.repository.store.db.AbstractSqlMapDataAccessor;
import vtk.security.Principal;
import vtk.security.Principal.Type;
import vtk.security.PrincipalFactory;
import vtk.util.io.IO;

public class DefaultRevisionStore extends AbstractSqlMapDataAccessor implements RevisionStore {

    private static final int COPY_BUF_SIZE = 122880;
    
    private String revisionDirectory;
    private PrincipalFactory principalFactory;
    private File tempDir;
    
    
    @Required
    public void setPrincipalFactory(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }
    
    @Required
    public void setTempDir(String tempDir) {
        File f = new File(tempDir);
        if (!f.exists()) {
            throw new IllegalStateException(
                    "Directory " + tempDir + " does not exist");
            
        }
        this.tempDir = f;
    }

    @Override
    public List<Revision> list(Resource resource) {

        String sqlMap = getSqlMap("loadResourceIdByUri");
        Map<String, Object> idMap = getSqlSession().selectOne(sqlMap,
                resource.getURI().toString());
        Integer id = (Integer) idMap.get("resourceId");

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("resourceId", id);
        
        Map<Long, Acl> aclMap = loadAclMap(id);
            
        sqlMap = getSqlMap("listRevisionsByResource");
        List<Revision> result = new ArrayList<Revision>();
        List<Map<String, Object>> revisions =
            getSqlSession().selectList(sqlMap, parameters);

        for (Map<String, Object> map: revisions) {
            Number n = (Number) map.get("id");
            long revId = n.longValue();
            Date timestamp = new Date(((Timestamp) map.get("timestamp")).getTime());
            String name = (String) map.get("name");
            String uid = map.get("uid").toString();
            String checksum = map.get("checksum").toString();
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
                    .build();
            result.add(rev);
        }         
        return Collections.unmodifiableList(result);
    }

    @Override
    public long newRevisionID() {
        return getSqlSession()
                .selectOne(getSqlMap("nextRevisionID"));
    }

    @Override
    public void create(ResourceImpl resource, Revision revision, InputStream content) {
        SqlSession sqlSession = getSqlSession();
        insertRevision(resource, revision, sqlSession);
        File revisionFile = revisionFile(resource, revision, true);
        if (!revisionFile.exists()) {
            throw new DataAccessException("Cannot create revision " + revision.getID() 
                    + ", unable to create file: " + revisionFile.getAbsolutePath());
        }
        try {
            FileOutputStream out = new FileOutputStream(revisionFile);
            IO.copy(content, out).bufferSize(COPY_BUF_SIZE).perform();
        } catch (IOException e) {
            throw new DataAccessException(e);
        }
    }
    
    private void insertRevision(ResourceImpl resource, Revision revision, SqlSession sqlSession) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("resourceId", resource.getID());
        parameters.put("revisionId", revision.getID());
        parameters.put("uid", revision.getUid());
        parameters.put("name", revision.getName());
        parameters.put("timestamp", revision.getTimestamp());
        parameters.put("checksum", revision.getChecksum());
        
        String sqlMap = getSqlMap("insertRevision");
        sqlSession.insert(sqlMap, parameters);

        if (revision.getAcl() != null) {
            insertAcl(resource, revision, sqlSession);
        }
        
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
        getSqlSession().delete(sqlMap, parameters);
        
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
    public InputStream getContent(ResourceImpl resource, Revision revision)
            throws DataAccessException {
        File revisionFile = revisionFile(resource, revision, false);
        if (!revisionFile.exists()) {
            throw new DataAccessException("Unable to find revision " + revision 
                    + ": no file: " + revisionFile.getAbsolutePath());
        }
        try {
            return new FileInputStream(revisionFile);
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
            
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("resourceId", resource.getID());
            parameters.put("revisionId", revision.getID());
            parameters.put("uid", revision.getUid());
            parameters.put("name", revision.getName());
            parameters.put("timestamp", revision.getTimestamp());
            parameters.put("checksum", revision.getChecksum());
            
            String sqlMap = getSqlMap("updateRevision");
            getSqlSession().update(sqlMap, parameters);
            
            File dest = revisionFile(resource, revision, true);
            
            // Go via a temporary file in case the source input stream is 
            // passed as the content parameter:
            File tmp = File.createTempFile("revision-" + revision.getID() + "-", null, this.tempDir);

            try {
                FileOutputStream outputStream = new FileOutputStream(tmp);
                IO.copy(content, outputStream).bufferSize(COPY_BUF_SIZE).perform();

                FileInputStream srcStream = new FileInputStream(tmp);
                FileOutputStream destStream = new FileOutputStream(dest);
                IO.copy(srcStream, destStream).bufferSize(COPY_BUF_SIZE).perform();

            } finally {
                tmp.delete();
            }
        } catch (IOException e) {
            throw new DataAccessException("Store revision content [" + revision + "] failed", e);
        }
    }
    
    
    private void insertAcl(final ResourceImpl resource, final Revision revision, SqlSession sqlSession) {
        final Map<String, Integer> actionTypes = loadActionTypes();
        final Acl acl = revision.getAcl();
        if (acl == null) {
            throw new DataAccessException("Revision has no ACL: " + revision);
        }
        final Set<Privilege> actions = acl.getActions();
        final String sqlMap = getSqlMap("insertRevisionAclEntry");
        
        Map<String, Object> parameters = new HashMap<String, Object>();
        for (Privilege action : actions) {
            String actionName = action.getName();
            for (Principal p : acl.getPrincipalSet(action)) {

                Integer actionID = actionTypes.get(actionName);
                if (actionID == null) {
                    throw new DataAccessException(
                            "insertAcl(): No action id exists for action '" + action + "'");
                }

                parameters.put("actionId", actionID);
                parameters.put("revisionId", revision.getID());
                parameters.put("principal", p.getQualifiedName());
                parameters.put("isUser", p.getType() == Principal.Type.GROUP ? "N" : "Y");
                parameters.put("grantedBy", resource.getOwner().getQualifiedName());
                parameters.put("grantedDate", new Date());
                sqlSession.insert(sqlMap, parameters);
            }
        }
    }
    

    private Map<Long, Acl> loadAclMap(Integer resourceID) {
        String sqlMap = getSqlMap("listRevisionAclEntriesByResource");
        Map<String, Integer> parameters = Collections.singletonMap("resourceId", resourceID);
        List<Map<String, Object>> acls =
            getSqlSession().selectList(sqlMap, parameters);

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
            String s = Long.toHexString(n);
            if (s.length() == 1) {
                result.append("0");
            }
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
    
    @Override
    public synchronized void gc() throws IOException {
        logger.info("Starting revisions GC");
        Set<Long> batch = new HashSet<Long>();
        traverse(new File(this.revisionDirectory), 0, batch);
        if (batch.size() > 0) {
            clean(batch);
        }
        logger.info("Finished revisions GC");
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

        List<Long> revisions =
            getSqlSession().selectList(sqlMap, parameters);
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
            if (children.length > 0) {
                logger.info("Revisions GC: purge " + children.length + " files");
            }
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
     * Duplicate of {@link vtk.repository.store.db.SqlMapDataAccessor#loadActionTypes}
     */
    private Map<String, Integer> loadActionTypes() {
        Map<String, Integer> actionTypes = new HashMap<String, Integer>();

        String sqlMap = getSqlMap("loadActionTypes");
        List<Map<String, Object>> list = getSqlSession().selectList(sqlMap, null);
        for (Map<String, Object> map : list) {
            actionTypes.put((String) map.get("name"), (Integer) map.get("id"));
        }
        return actionTypes;
    }

    /**
     * Duplicate of {@link vtk.repository.store.db.SqlMapDataAccessor.AclHolder}
     */
    @SuppressWarnings("serial")
    private static class AclHolder extends HashMap<Privilege, Set<Principal>> {

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
