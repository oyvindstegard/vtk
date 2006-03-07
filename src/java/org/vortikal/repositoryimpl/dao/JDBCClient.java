/* Copyright (c) 2004, University of Oslo, Norway
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
package org.vortikal.repositoryimpl.dao;



import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import org.apache.commons.dbcp.BasicDataSource;
import org.springframework.beans.factory.DisposableBean;
import org.vortikal.repositoryimpl.ACL;
import org.vortikal.repositoryimpl.ACLPrincipal;
import org.vortikal.repositoryimpl.LockImpl;
import org.vortikal.repositoryimpl.Resource;
import org.vortikal.util.repository.ContentTypeHelper;
import org.vortikal.util.repository.LocaleHelper;
import org.vortikal.util.repository.URIUtil;
import org.vortikal.util.web.URLUtil;

/**
 * This class is going to be a "generic" JDBC database accessor. Currently, only
 * PostgreSQL is supported.
 *
 */
public class JDBCClient extends AbstractDataAccessor implements DisposableBean {

    private String databaseDriver;

    private int maxDatabaseConnections = 6;

    private String databaseURL;

    private String databaseUser;

    private String databasePassword;

    private String repositoryDataDirectory;

    private boolean urlEncodeFileNames = false;

    private BasicDataSource pool;
    
    public void setRepositoryDataDirectory(String repositoryDataDirectory) {
        this.repositoryDataDirectory = repositoryDataDirectory;
    }

    public void setDatabaseDriver(String databaseDriver) {
        this.databaseDriver = databaseDriver;
    }

    public void setDatabasePassword(String databasePassword) {
        this.databasePassword = databasePassword;
    }

    public void setDatabaseURL(String databaseURL) {
        this.databaseURL = databaseURL;
    }

    public void setDatabaseUser(String databaseUser) {
        this.databaseUser = databaseUser;
    }

    public void setMaxDatabaseConnections(int maxDatabaseConnections) {
        this.maxDatabaseConnections = maxDatabaseConnections;
    }

    public void setUrlEncodeFileNames(boolean urlEncodeFileNames) {
        this.urlEncodeFileNames = urlEncodeFileNames;
    }

    public void afterPropertiesSet()
            throws Exception {
        super.afterPropertiesSet();

        logger.info("Setting up database");

        if (repositoryDataDirectory == null) {
            throw new IOException("Missing property \"repositoryDataDirectory\"");
        }

        File root = new File(this.repositoryDataDirectory);

        if (!root.isAbsolute()) {
            this.repositoryDataDirectory = System.getProperty("vortex.home") + File.separator
                    + repositoryDataDirectory;
            root = new File(this.repositoryDataDirectory);
        }

        if (!root.exists()) {
            root.mkdir();
        }

        if (databaseURL == null) {
            throw new IOException("Missing property \"databaseURL\"");
        }

        if (databaseUser == null) {
            throw new IOException("Missing property \"databaseUser\"");
        }

        if (databasePassword == null) {
            throw new IOException("Missing property \"databasePassword\"");
        }

        if (maxDatabaseConnections == 0) {
            throw new IOException("Missing property " + "\"maxDatabaseConnections\"");
        }

        if (databaseDriver == null) {
            throw new IOException("Missing property " + "\"databaseDriver\"");
        }

        pool = new BasicDataSource();

        logger.info("Using driver " + databaseDriver);
        pool.setDriverClassName(databaseDriver);
        pool.setMaxActive(maxDatabaseConnections);
        pool.setUrl(databaseURL);
        pool.setUsername(databaseUser);
        pool.setPassword(databasePassword);
        pool.setDefaultAutoCommit(false);
    }

    /**
     * Gets a connection from the pool with auto commit set to false.
     * 
     * @return a <code>Connection</code>
     * @exception SQLException
     *                if an error occurs
     */
    public Connection getConnection()
            throws SQLException {
        Connection conn = pool.getConnection();
        conn.setAutoCommit(false);     
        return conn;
    }

    private boolean tableExists(Connection conn, String tableName)
            throws IOException {
        try {
            DatabaseMetaData md = conn.getMetaData();

            ResultSet rs = md.getTables(null, null, tableName, null);
            boolean exists = rs.next();

            rs.close();

            return exists;
        } catch (SQLException e) {
            logger.warn("Error occurred while checking for table existance: ", e);
            throw new IOException(e.getMessage());
        }
    }

    public boolean validate()
            throws IOException {
        Connection conn = null;
        boolean exists = false;

        try {
            conn = getConnection();
            exists = ((tableExists(conn, "vortex_resource") || tableExists(conn,
                    "VORTEX_RESOURCE"))
                    && (tableExists(conn, "lock_type") || tableExists(conn, "LOCK_TYPE"))
                    && (tableExists(conn, "vortex_lock") || tableExists(conn,
                            "VORTEX_LOCK"))
                    && (tableExists(conn, "action_type") || tableExists(conn,
                            "ACTION_TYPE"))
                    && (tableExists(conn, "acl_entry") || tableExists(conn, "ACL_ENTRY")) && (tableExists(
                    conn, "extra_prop_entry") || tableExists(conn, "EXTRA_PROP_ENTRY")));

            conn.commit();

            if (logger.isDebugEnabled()) {
                logger.debug(exists ? "All required tables exist"
                        : "Table(s) are missing");
            }
        } catch (SQLException e) {
            logger.warn("Error occurred while checking database validity: ", e);
            throw new IOException(e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                    conn = null;
                }
            } catch (SQLException e) {
                throw new IOException(e.getMessage());
            }
        }

        return exists;
    }

    public void destroy()
            throws IOException {
        try {
            pool.close();
        } catch (SQLException e) {
            logger.error("Error closing pooled connections:", e);
        }
    }


    public Resource load(String uri)
            throws IOException {
        Connection conn = null;
        Resource retVal = null;

        try {
            conn = getConnection();
            retVal = load(conn, uri);
            conn.commit();
        } catch (SQLException e) {
            logger.warn("Error occurred while loading resource(s)", e);
            throw new IOException(e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                    conn = null;
                }
            } catch (SQLException e) {
                throw new IOException(e.getMessage());
            }
        }

        return retVal;
    }

    private Resource load(Connection conn, String uri)
            throws SQLException {

        Map locks = loadLocks(conn, new String[] {uri});

        String query = "select r.* from VORTEX_RESOURCE r where r.uri = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, uri);
        
        ResultSet rs = stmt.executeQuery();

        Resource resource = null;

        if (rs.next()) {
            String owner = rs.getString("resource_owner");
            String contentModifiedBy = rs.getString("content_modified_by");
            String propertiesModifiedBy = rs.getString("properties_modified_by");
            boolean inherited = rs.getString("acl_inherited").equals("Y");
            ACL acl = new ACL();

            LockImpl lock = null;

            if (locks.containsKey(uri)) {
                lock = (LockImpl) locks.get(uri);
            }

            if (rs.getString("is_collection").equals("Y")) {
                String[] children = null;

                resource = new Resource(uri, owner, contentModifiedBy,
                                        propertiesModifiedBy, acl, inherited, lock,
                                        true, children);
            } else {
                resource = new Resource(uri, owner, contentModifiedBy,
                                        propertiesModifiedBy, acl, inherited, lock, false, null);
            }

            resource.setCreationTime(rs.getTimestamp("creation_time"));
            resource.setOwner(rs.getString("resource_owner"));

            Date contentLastModified = // rs.getTimestamp("content_last_modified");
            new Date(rs.getTimestamp("content_last_modified").getTime());
            Date propertiesLastModified = // rs.getTimestamp("properties_last_modified");
            new Date(rs.getTimestamp("properties_last_modified").getTime());

            resource.setContentLastModified(contentLastModified);
            resource.setPropertiesLastModified(propertiesLastModified);
            resource.setDisplayName(rs.getString("display_name"));
            resource.setContentType(rs.getString("content_type"));
            resource.setCharacterEncoding(rs.getString("character_encoding"));
            resource.setID(rs.getInt("resource_id"));

            if (!resource.isCollection()) {
                resource.setContentLocale(
                    LocaleHelper.getLocale(rs.getString("content_language")));
            }

        }

        rs.close();
        stmt.close();

        if (resource != null) {
            loadChildURIs(conn, resource);
            loadACLs(conn, new Resource[] {resource});
            loadProperties(conn, resource);
        }


        return resource;
    }

    private String getURIWildcard(String uri) {
        if ("/".equals(uri)) {
            return uri + "%";
        }
        return uri + "/%";
    }
    

    private int getURIDepth(String uri) {
        if ("/".equals(uri)) {
            return 0;
        }
        int count = 0;
        for (int index = 0; (index = uri.indexOf('/', index)) != -1; count++, index ++);
        return count;
    }


    private void loadProperties(Connection conn, Resource resource)
            throws SQLException {

        String query = "select * from EXTRA_PROP_ENTRY where resource_id in (select resource_id from vortex_resource where uri = '" + resource.getURI() + "')";

        Statement propStmt = conn.createStatement();
        ResultSet rs = propStmt.executeQuery(query);

        final int namespace = 0;
        final int name = 1;
        final int value = 2;

        List propertyList = new ArrayList();

        while (rs.next()) {

            String[] prop = new String[3];

            prop[namespace] = rs.getString("name_space");
            prop[name] = rs.getString("name");
            prop[value] = rs.getString("value");

            propertyList.add(prop);
        }

        rs.close();
        propStmt.close();

        for (Iterator i = propertyList.iterator(); i.hasNext();) {
            String[] prop = (String[]) i.next();
            resource.addProperty(prop[namespace], prop[name], prop[value]);
        }
    }

    public void deleteExpiredLocks() throws IOException {
        Connection conn = null;

        try {
            conn = getConnection();
            deleteExpiredLocks(conn);
            conn.commit();
        } catch (SQLException e) {
            logger.warn("Error occurred while deleting expired locks", e);
            throw new IOException(e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                    conn = null;
                }
            } catch (SQLException e) {
                throw new IOException(e.getMessage());
            }
        }
    }

    private void deleteExpiredLocks(Connection conn)
            throws SQLException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String format = formatter.format(new Date());

        String query = "delete from VORTEX_LOCK "
                + "where timeout < '" + format + "'";

        Statement stmt = conn.createStatement();
        int n = stmt.executeUpdate(query);
        if (logger.isInfoEnabled()) {
            logger.info("Deleted " + n + " expired locks");
        }
        stmt.close();
    }


    public void addChangeLogEntry(String loggerID, String loggerType, String uri,
                                  String operation, int resourceId, boolean collection,
                                  boolean recurse) throws IOException {
        Connection conn = null;

        try {
            conn = getConnection();
            addChangeLogEntry(conn, loggerID, loggerType, uri, operation, resourceId,
                              collection, recurse);
            conn.commit();
        } catch (SQLException e) {
            logger.warn("Error occurred while adding changelog entry for " + uri, e);
            throw new IOException(e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                    conn = null;
                }
            } catch (SQLException e) {
                throw new IOException(e.getMessage());
            }
        }
    }

    private void addChangeLogEntry(Connection conn, String loggerID, String loggerType,
                                   String uri, String operation, int resourceId,
                                   boolean collection, boolean recurse) throws SQLException {
        if (collection && recurse) {

            int id = Integer.parseInt(loggerID);
            int type = Integer.parseInt(loggerType);

            String statement = "INSERT INTO changelog_entry "
                + "(changelog_entry_id, logger_id, logger_type, "
                + "operation, timestamp, uri, resource_id, is_collection) "
                + "select nextval('changelog_entry_seq_pk'), " + id + ", " + type + ", "
                + "'" + operation + "', now(), uri, ";
            if (resourceId == -1) {
                statement += "NULL, ";
            } else {
                statement += "resource_id, ";
            }
            statement += "is_collection from vortex_resource "
                + "where uri = '" + uri + "' or uri like '" + getURIWildcard(uri) + "'";
            
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(statement);
            stmt.close();

        } else {


            try {
                int id = Integer.parseInt(loggerID);
                int type = Integer.parseInt(loggerType);

                String statement = "INSERT INTO changelog_entry "
                    + "(changelog_entry_id, logger_id, logger_type, "
                    + "operation, timestamp, uri, resource_id, is_collection) "
                    + "VALUES (nextval('changelog_entry_seq_pk'), ?, ?, ?, ?, ?, ?, ?)";

                PreparedStatement stmt = conn.prepareStatement(statement);

                stmt.setInt(1, id);
                stmt.setInt(2, type);
                stmt.setString(3, operation);
                stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                stmt.setString(5, uri);

                // resourceId of -1 indicates that resourceId should be set to SQL NULL
                if (resourceId == -1) {
                    stmt.setNull(6, java.sql.Types.NUMERIC);
                } else {
                    stmt.setInt(6, resourceId);
                }
            
                stmt.setString(7, collection ? "Y" : "N");
            
                stmt.executeUpdate();
                stmt.close();
            } catch (NumberFormatException e) {
                logger.warn("No changelog entry added! Only numerical types and "
                            + "IDs are supported by this database backend.");
            }

        }

    }

    public String[] discoverLocks(Resource directory)
            throws IOException {
        Connection conn = null;

        try {
            conn = getConnection();

            if (!directory.isCollection()) {
                LockImpl lock = loadLock(conn, directory.getURI());

                return (lock != null) ? new String[] {directory.getURI()} : new String[0];
            }

            String[] lockedURIs = discoverLocks(conn, directory);

            conn.commit();

            return lockedURIs;
        } catch (SQLException e) {
            logger.warn("Error occurred while discovering locks", e);
            throw new IOException(e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                    conn = null;
                }
            } catch (SQLException e) {
                throw new IOException(e.getMessage());
            }
        }
    }

    private String[] discoverLocks(Connection conn, Resource directory)
            throws SQLException {
        String uri = directory.getURI();

        String query = "select r.uri, l.* from VORTEX_LOCK l inner join VORTEX_RESOURCE r "
                + "on l.resource_id = r.resource_id "
                + "where r.resource_id in ("
                + "select resource_id from VORTEX_RESOURCE where uri like ?)";
        

        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, getURIWildcard(uri));

        ResultSet rs = stmt.executeQuery();
        List result = new ArrayList();

        while (rs.next()) {
            String lockURI = rs.getString("uri");

            result.add(lockURI);
        }

        rs.close();
        stmt.close();

        return (String[]) result.toArray(new String[] {});
    }

    private LockImpl loadLock(Connection conn, String uri)
            throws SQLException {
        String query = "select * from VORTEX_LOCK where resource_id in ("
                + "select resource_id from VORTEX_RESOURCE where uri = ?)";

        PreparedStatement stmt = conn.prepareStatement(query);

        stmt.setString(1, uri);

        ResultSet rs = stmt.executeQuery();

        LockImpl lock = null;

        if (rs.next()) {
            lock = new LockImpl(rs.getString("token"), rs.getString("lock_owner"), rs
                    .getString("lock_owner_info"), rs.getString("depth"), 
                    new Date(rs.getTimestamp("timeout").getTime()));
        }

        rs.close();
        stmt.close();

        return lock;
    }

    private Map loadLocks(Connection conn, String[] uris)
            throws SQLException {
        if (uris.length == 0) {
            return new HashMap();
        }

        String query = "select r.uri as uri, l.* from VORTEX_RESOURCE r "
                + "inner join VORTEX_LOCK l on r.resource_id = l.resource_id "
                + "where r.uri in (";

        for (int i = 0; i < uris.length; i++) {
            query += ((i < (uris.length - 1)) ? "?, " : "?)");
        }

        PreparedStatement stmt = conn.prepareStatement(query);

        for (int i = 0; i < uris.length; i++) {
            stmt.setString(i + 1, uris[i]);
        }

        ResultSet rs = stmt.executeQuery();
        Map result = new HashMap();

        while (rs.next()) {
            LockImpl lock = new LockImpl(
                rs.getString("token"), rs.getString("lock_owner"),
                rs.getString("lock_owner_info"), rs.getString("depth"),
                new Date(rs.getTimestamp("timeout").getTime()));

            if (lock.getTimeout().getTime() > System.currentTimeMillis()) {
                result.put(rs.getString("uri"), lock);
            }
        }

        rs.close();
        stmt.close();

        return result;
    }

    public String[] listSubTree(Resource parent)
            throws IOException {
        Connection conn = null;
        String[] retVal = null;

        try {
            conn = getConnection();
            retVal = listSubTree(conn, parent);
            conn.commit();
        } catch (SQLException e) {
            logger.warn("Error occurred while listing resource tree", e);
            throw new IOException(e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                    conn = null;
                }
            } catch (SQLException e) {
                throw new IOException(e.getMessage());
            }
        }

        return retVal;
    }

    private String[] listSubTree(Connection conn, Resource parent)
            throws SQLException {
        if (parent.getURI() == null) {
            return new String[] {};
        }

        String query = "select uri from VORTEX_RESOURCE "
                + "where uri like ? order by uri asc";

        PreparedStatement stmt = conn.prepareStatement(query);

        stmt.setString(1, getURIWildcard(parent.getURI()));

        ResultSet rs = stmt.executeQuery();

        ArrayList l = new ArrayList();

        while (rs.next()) {
            String childURI = rs.getString("uri");

            l.add(childURI);
        }

        rs.close();
        stmt.close();

        return (String[]) l.toArray(new String[] {});
    }

    public void store(Resource r)
            throws IOException {
        Connection conn = null;

        try {
            conn = getConnection();
            store(conn, r);
            conn.commit();
        } catch (SQLException e) {
            logger.warn("Error occurred while storing resource " + r.getURI(), e);
            throw new IOException(e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                    conn = null;
                }
            } catch (SQLException e) {
                throw new IOException(e.getMessage());
            }
        }
    }

    private void store(Connection conn, Resource r)
            throws SQLException, IOException {
        String uri = r.getURI();
        
        String parent = URIUtil.getParentURI(uri);

        Date contentLastModified = r.getContentLastModified();
        Date propertiesLastModified = r.getPropertiesLastModified();
        Date creationTime = r.getCreationTime();

        String displayName = r.getDisplayName();

        String contentType = r.getContentType();
        String characterEncoding = r.getCharacterEncoding();
        String owner = r.getOwner();
        String contentModifiedBy = r.getContentModifiedBy();
        String propertiesModifiedBy = r.getPropertiesModifiedBy();
        String contentLanguage = null;

        if (r.getContentLocale() != null) {
            contentLanguage = r.getContentLocale().toString();
        }

        // FIXME: allow null values:
        if ((contentLanguage == null) || contentLanguage.trim().equals("")) {
            contentLanguage = "unknown";
        }

        String query = "select * from VORTEX_RESOURCE where uri = ?";
        PreparedStatement existStmt = conn.prepareStatement(query);

        existStmt.setString(1, uri);

        ResultSet rs = existStmt.executeQuery();
        boolean existed = rs.next();

        rs.close();
        existStmt.close();

        if (existed) {
            // Save the VORTEX_RESOURCE table entry:
            PreparedStatement stmt = conn
                    .prepareStatement("update VORTEX_RESOURCE set "
                            + "content_last_modified = ?, "
                            + "properties_last_modified = ?, "
                            + "content_modified_by = ?, "
                            + "properties_modified_by = ?, " + "resource_owner = ?, "
                            + "display_name = ?, " + "content_language = ?, "
                            + "content_type = ?, " + "character_encoding = ?, "
                            + "creation_time = ? " + "where uri = ?");

            stmt.setTimestamp(1, new Timestamp(contentLastModified.getTime()));
            stmt.setTimestamp(2, new Timestamp(propertiesLastModified.getTime()));
            stmt.setString(3, contentModifiedBy);
            stmt.setString(4, propertiesModifiedBy);
            stmt.setString(5, owner);
            stmt.setString(6, displayName);
            stmt.setString(7, contentLanguage);
            stmt.setString(8, contentType);
            stmt.setString(9, characterEncoding);
            stmt.setTimestamp(10, new Timestamp(creationTime.getTime()));
            stmt.setString(11, uri);

            stmt.executeUpdate();
            stmt.close();
        } else {
            insertResourceEntry(conn, uri, creationTime, contentLastModified,
                    propertiesLastModified, displayName, owner, contentModifiedBy,
                    propertiesModifiedBy, contentLanguage, contentType,
                    characterEncoding, "application/x-vortex-collection"
                            .equals(contentType), parent);
        }

        // Save the ACL:
        storeACL(conn, r);

        // Save the Lock:
        storeLock(conn, r);

        // Save properties:
        storeProperties(conn, r);
    }

    private void insertResourceEntry(Connection conn, String uri, Date creationTime,
            Date contentLastModified, Date propertiesLastModified, String displayname,
            String owner, String contentModifiedBy, String propertiesModifiedBy,
            String contentlanguage, String contenttype, String characterEncoding,
            boolean isCollection, String parent)
            throws SQLException, IOException {
        int depth = getURIDepth(uri);

        String statement = "insert into VORTEX_RESOURCE "
                + "(resource_id, uri, depth, creation_time, content_last_modified, properties_last_modified, "
                + "content_modified_by, properties_modified_by, "
                + "resource_owner, display_name, "
                + "content_language, content_type, character_encoding, is_collection, acl_inherited) "
                + "values (nextval('vortex_resource_seq_pk'), "
                + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        PreparedStatement stmt = conn.prepareStatement(statement);

        stmt.setString(1, uri);
        stmt.setInt(2, depth);
        stmt.setTimestamp(3, new java.sql.Timestamp(creationTime.getTime()));
        stmt.setTimestamp(4, new java.sql.Timestamp(contentLastModified.getTime()));
        stmt.setTimestamp(5, new java.sql.Timestamp(propertiesLastModified.getTime()));
        stmt.setString(6, contentModifiedBy);
        stmt.setString(7, propertiesModifiedBy);
        stmt.setString(8, owner);
        stmt.setString(9, displayname);
        stmt.setString(10, contentlanguage);
        stmt.setString(11, contenttype);
        stmt.setString(12, characterEncoding);
        stmt.setString(13, isCollection ? "Y" : "N");
        stmt.setString(14, "Y");

        stmt.executeUpdate();
        stmt.close();


        String fileName = this.repositoryDataDirectory
                + ((this.urlEncodeFileNames) ? URLUtil.urlEncode(uri) : uri);

        if (isCollection) {
            if (logger.isDebugEnabled()) {
                logger.debug("Creating directory " + fileName);
            }

            new File(fileName).mkdir();
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Creating file " + fileName);
            }

            new File(fileName).createNewFile();
        }
    }

    private void storeLock(Connection conn, Resource r)
            throws SQLException {
        LockImpl lock = r.getLock();

        if (lock == null) {
            Statement stmt = conn.createStatement();

            stmt.execute("delete from VORTEX_LOCK where resource_id = " + r.getID());
            stmt.close();
        }

        if (lock != null) {
            String lockToken = lock.getLockToken();
            Date timeout = lock.getTimeout();
            String type = lock.getLockType();
            String user = lock.getUser();
            String ownerInfo = lock.getOwnerInfo();
            String depth = lock.getDepth();

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("select * from VORTEX_LOCK where token = '"
                    + lockToken + "'");
            boolean exists = rs.next();

            rs.close();
            stmt.close();

            stmt = conn.createStatement();
            rs = stmt.executeQuery("select lock_type_id from LOCK_TYPE where "
                    + "name = '" + type + "'");

            if (!rs.next()) {
                rs.close();
                stmt.close();
                throw new SQLException("Unknown lock type: " + type);
            }

            int lockType = rs.getInt("lock_type_id");

            rs.close();
            stmt.close();

            if (exists) {
                PreparedStatement updateStmt = conn
                        .prepareStatement("update VORTEX_LOCK set "
                                + "lock_type_id = ?, lock_owner = ?, lock_owner_info = ?, "
                                + "depth = ?, timeout = ? " + "where token = ?");

                updateStmt.setInt(1, lockType);
                updateStmt.setString(2, user);
                updateStmt.setString(3, ownerInfo);
                updateStmt.setString(4, depth);
                updateStmt.setTimestamp(5, new java.sql.Timestamp(timeout.getTime()));
                updateStmt.setString(6, lockToken);

                updateStmt.executeUpdate();
                updateStmt.close();
            } else {
                PreparedStatement insertStmt = conn
                        .prepareStatement("insert into VORTEX_LOCK "
                                + "(lock_id, token, resource_id, lock_type_id, lock_owner, "
                                + "lock_owner_info, depth, timeout) "
                                + "values (nextval('vortex_lock_seq_pk'), "
                                + "?, ?, ?, ?, ?, ?, ?)");

                insertStmt.setString(1, lockToken);
                insertStmt.setInt(2, r.getID());
                insertStmt.setInt(3, lockType);
                insertStmt.setString(4, user);
                insertStmt.setString(5, ownerInfo);
                insertStmt.setString(6, depth);
                insertStmt.setTimestamp(7, new java.sql.Timestamp(timeout.getTime()));

                insertStmt.executeUpdate();
                insertStmt.close();
            }
        }
    }

    private void storeACL(Connection conn, Resource r)
            throws SQLException {
        // first, check if existing ACL is inherited:
        Resource existingResource = load(conn, r.getURI());

        if (existingResource.isInheritedACL() && r.isInheritedACL()) {
            ACL newACL = r.getACL();

            if (existingResource.getACL().equals(newACL)) {
                /* No need to insert ACL, is inherited and not modified */
                return;
            }
        }

        // ACL is either not inherited OR inherited and THEN modified,
        // so we have to store the ACL entries
        insertACL(conn, r);
    }

    private void insertACL(Connection conn, Resource r)
            throws SQLException {
        Map aclMap = r.getACL().getActionMap();

        Set actions = aclMap.keySet();

        /*
         * First, delete any previously defined ACEs for this resource:
         */
        Statement deleteStatement = conn.createStatement();

        deleteStatement.executeUpdate("delete from ACL_ENTRY where " + "resource_id = "
                + r.getID());

        deleteStatement.close();

        if (r.isInheritedACL()) {
            /*
             * The ACL is inherited. Update resource entry to reflect this
             * situation (and return)
             */
            Statement updateStmt = conn.createStatement();

            updateStmt.executeUpdate("update VORTEX_RESOURCE set acl_inherited = 'Y' "
                    + "where resource_id = " + r.getID());
            updateStmt.close();

            return;
        }

        /* Insert an entry for each privilege */
        for (Iterator i = actions.iterator(); i.hasNext();) {
            String action = (String) i.next();

            for (Iterator j = ((List) aclMap.get(action)).iterator(); j.hasNext();) {
                ACLPrincipal p = (ACLPrincipal) j.next();

                insertACLEntry(conn, action, r, p);
            }
        }

        /*
         * At this point, we know that the ACL is not inherited. Update the
         * inheritance flag in the resource entry:
         */
        Statement updateStmt = conn.createStatement();

        updateStmt.executeUpdate("update VORTEX_RESOURCE set acl_inherited = 'N' "
                + "where resource_id = " + r.getID());
        updateStmt.close();
    }

    private void insertACLEntry(Connection conn, String action, Resource resource,
            ACLPrincipal p)
            throws SQLException {
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("select action_type_id from "
                + "ACTION_TYPE where name = '" + action + "'");

        if (!rs.next()) {
            rs.close();
            stmt.close();
            throw new SQLException("Database.insertACLEntry(): Unable to "
                    + "find id for action '" + action + "'");
        }

        int actionID = rs.getInt("action_type_id");

        rs.close();
        stmt.close();

        PreparedStatement insertStmt = conn
                .prepareStatement("insert into ACL_ENTRY (acl_entry_id, action_type_id, "
                        + "resource_id, user_or_group_name, "
                        + "is_user, granted_by_user_name, granted_date) "
                        + "values (nextval('acl_entry_seq_pk'), ?, ?, ?, ?, ?, ?)");

        insertStmt.setInt(1, actionID);
        insertStmt.setInt(2, resource.getID());
        insertStmt.setString(3, p.getUrl());
        insertStmt.setString(4, p.isGroup() ? "N" : "Y");
        insertStmt.setString(5, resource.getOwner());
        insertStmt.setTimestamp(6, new java.sql.Timestamp(System.currentTimeMillis()));

        insertStmt.executeUpdate();
        insertStmt.close();
    }

    private void storeProperties(Connection conn, Resource r)
            throws SQLException {
        Vector properties = r.getProperties();

        Statement deleteStatement = conn.createStatement();

        deleteStatement.executeUpdate("delete from EXTRA_PROP_ENTRY where "
                + "resource_id = '" + r.getID() + "'");

        deleteStatement.close();

        for (Iterator i = properties.iterator(); i.hasNext();) {
            Vector property = (Vector) i.next();

            String namespace = (String) property.get(0);
            String name = (String) property.get(1);
            String value = (String) property.get(2);

            insertPropertyEntry(conn, r, namespace, name, value);
        }
    }

    private void insertPropertyEntry(Connection conn, Resource r, String namespace,
            String name, String value)
            throws SQLException {
        PreparedStatement stmt = conn
                .prepareStatement("insert into EXTRA_PROP_ENTRY (extra_prop_entry_id, resource_id, "
                        + "name_space, name, value) "
                        + "values (nextval('extra_prop_entry_seq_pk'), ?, ?, ?, ?)");

        stmt.setInt(1, r.getID());
        stmt.setString(2, namespace);
        stmt.setString(3, name);
        stmt.setString(4, value);

        stmt.executeUpdate();
        stmt.close();
    }

    public void delete(Resource resource)
            throws IOException {
        Connection conn = null;

        try {
            conn = getConnection();
            delete(conn, resource);
            conn.commit();
        } catch (SQLException e) {
            throw new IOException(e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                    conn = null;
                }
            } catch (SQLException e) {
                throw new IOException(e.getMessage());
            }
        }
    }

    public void delete(Connection conn, Resource resource)
            throws SQLException {
        String query = "delete from ACL_ENTRY where resource_id in ("
                + "select resource_id from VORTEX_RESOURCE "
                + "where uri like ? or resource_id = ?)";
        PreparedStatement stmt = conn.prepareStatement(query);

        stmt.setString(1, getURIWildcard(resource.getURI()));
        stmt.setInt(2, resource.getID());
        stmt.executeUpdate();
        stmt.close();

        query = "delete from VORTEX_LOCK where resource_id in ("
                + "select resource_id from VORTEX_RESOURCE "
                + "where uri like ? or resource_id = ?)";
        stmt = conn.prepareStatement(query);
        stmt.setString(1, getURIWildcard(resource.getURI()));
        stmt.setInt(2, resource.getID());
        stmt.executeUpdate();
        stmt.close();

        query = "delete from EXTRA_PROP_ENTRY where resource_id in ("
                + "select resource_id from VORTEX_RESOURCE "
                + "where uri like ? or resource_id = ?)";
        stmt = conn.prepareStatement(query);
        stmt.setString(1, getURIWildcard(resource.getURI()));
        stmt.setInt(2, resource.getID());
        stmt.executeUpdate();
        stmt.close();

        query = "delete from VORTEX_RESOURCE where resource_id in ("
                + "select resource_id from VORTEX_RESOURCE "
                + "where uri like ? or resource_id = ?)";
        stmt = conn.prepareStatement(query);
        stmt.setString(1, getURIWildcard(resource.getURI()));
        stmt.setInt(2, resource.getID());
        stmt.executeUpdate();
        stmt.close();

        String fileName = this.repositoryDataDirectory
                + ((this.urlEncodeFileNames) ? URLUtil.urlEncode(resource.getURI())
                        : resource.getURI());

        deleteFiles(new File(fileName));
    }

    private void deleteFiles(File f) {
        if (!f.isDirectory()) {
            f.delete();

            return;
        }

        File[] children = f.listFiles();

        for (int i = 0; i < children.length; i++) {
            deleteFiles(children[i]);
        }

        f.delete();
    }

    public InputStream getInputStream(Resource resource)
            throws IOException {
        String fileName = this.repositoryDataDirectory
                + ((this.urlEncodeFileNames) ? URLUtil.urlEncode(resource.getURI())
                        : resource.getURI());

        return new java.io.FileInputStream(new File(fileName));
    }

    public OutputStream getOutputStream(Resource resource)
            throws IOException {
        String fileName = this.repositoryDataDirectory
                + ((this.urlEncodeFileNames) ? URLUtil.urlEncode(resource.getURI())
                        : resource.getURI());

        return new java.io.FileOutputStream(new File(fileName));
    }

    public long getContentLength(Resource resource)
            throws IOException {
        String fileName = this.repositoryDataDirectory
                + ((this.urlEncodeFileNames) ? URLUtil.urlEncode(resource.getURI())
                        : resource.getURI());

        return new File(fileName).length();
    }

    protected void executeACLQuery(Connection conn, Map acls)
            throws SQLException {

        String query = "select r.uri, a.*, t.namespace as action_namespace, "
                + "t.name as action_name from ACL_ENTRY a "
                + "inner join ACTION_TYPE t on a.action_type_id = t.action_type_id "
                + "inner join VORTEX_RESOURCE r on r.resource_id = a.resource_id "
                + "where r.uri in (";

        int n = acls.size();
        for (int i = 0; i < n; i++) {
            query += (i < n - 1) ? "?, " : "?)";
        }
        PreparedStatement stmt = conn.prepareStatement(query);
        for (Iterator i = acls.keySet().iterator(); i.hasNext();) {
            String uri = (String) i.next();
            stmt.setString(n--, uri);
        }
        ResultSet rs = stmt.executeQuery();

        while (rs.next()) {

            String uri = rs.getString("uri");
            String action = rs.getString("action_name");

            ACL acl = (ACL) acls.get(uri);

            if (acl == null) {

                acl = new ACL();
                acls.put(uri, acl);
            }

            Map actionMap = acl.getActionMap();

            List actionEntry = (List) actionMap.get(action);
            if (actionEntry == null) {
                actionEntry = new ArrayList();
                actionMap.put(action, actionEntry);
            }

            actionEntry.add(
                new ACLPrincipal(rs.getString("user_or_group_name"),
                                 rs.getString("is_user").equals("N")));
        }

        rs.close();
        stmt.close();
    }



    public Resource[] loadChildren(Resource parent)
            throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("load children:" + parent.getURI());
        }
        Connection conn = null;
        Resource[] retVal = null;

        try {
            conn = getConnection();
            retVal = loadChildren(conn, parent);
            conn.commit();
        } catch (SQLException e) {
            logger.warn("Error occurred while loading children: " + parent.getURI(), e);
            throw new IOException(e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                    conn = null;
                }
            } catch (SQLException e) {
                throw new IOException(e.getMessage());
            }
        }

        return retVal;
    }


    /**
     * Loads children for a given resource.
     */
    private void loadChildURIs(Connection conn, Resource parent)
            throws SQLException {

        StringBuffer query = new StringBuffer();
        query.append("select uri from vortex_resource where ");
        query.append("uri like '").append(getURIWildcard(parent.getURI())).append("'");
        query.append("and depth = ").append(getURIDepth(parent.getURI()) + 1);

        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query.toString());
        Map tempMap = new HashMap();

        List childURIs = new ArrayList();
        while (rs.next()) {
            String uri = rs.getString("uri");
            childURIs.add(uri);
        }
        rs.close();
        stmt.close();
        parent.setChildURIs((String[]) childURIs.toArray(new String[childURIs.size()]));
    }

    private Resource[] loadChildren(Connection conn, Resource parent)
        throws SQLException {

        Map locks = loadLocksForChildren(conn, parent);

        StringBuffer query = new StringBuffer();
        query.append("select * from vortex_resource where ");
        query.append("uri like ? and depth = ?");

        PreparedStatement stmt = conn.prepareStatement(query.toString());
        stmt.setString(1, getURIWildcard(parent.getURI()));
        stmt.setInt(2, getURIDepth(parent.getURI()) + 1);
        ResultSet rs = stmt.executeQuery();
        List resources = new ArrayList();

        while (rs.next()) {
            String uri = rs.getString("uri");
            String owner = rs.getString("resource_owner");
            String contentModifiedBy = rs.getString("content_modified_by");
            String propertiesModifiedBy = rs.getString("properties_modified_by");
            boolean inherited = rs.getString("acl_inherited").equals("Y");
            ACL acl = new ACL();

            LockImpl lock = null;

            if (locks.containsKey(uri)) {
                lock = (LockImpl) locks.get(uri);
            }

            Resource resource = null;

            if (rs.getString("is_collection").equals("Y")) {
                String[] children = null;

                resource = new Resource(uri, owner, contentModifiedBy,
                                        propertiesModifiedBy, acl, inherited, lock,
                                        true, children);
            } else {
                resource = new Resource(uri, owner, contentModifiedBy,
                        propertiesModifiedBy, acl, inherited, lock, false,
                        null);
            }

            resource.setCreationTime(rs.getTimestamp("creation_time"));
            resource.setOwner(rs.getString("resource_owner"));

            Date contentLastModified = // rs.getTimestamp("content_last_modified");
            new Date(rs.getTimestamp("content_last_modified").getTime());
            Date propertiesLastModified = // rs.getTimestamp("properties_last_modified");
            new Date(rs.getTimestamp("properties_last_modified").getTime());

            resource.setContentLastModified(contentLastModified);
            resource.setPropertiesLastModified(propertiesLastModified);
            resource.setDisplayName(rs.getString("display_name"));
            resource.setContentType(rs.getString("content_type"));
            resource.setCharacterEncoding(rs.getString("character_encoding"));
            resource.setID(rs.getInt("resource_id"));

            if (!resource.isCollection()) {
                resource.setContentLocale(
                    LocaleHelper.getLocale(rs.getString("content_language")));
            }

            resources.add(resource);
        }

        rs.close();
        stmt.close();

        Resource[] result = (Resource[]) resources.toArray(new Resource[0]);

        loadChildrenForChildren(conn, parent, result);
        loadACLs(conn, result);
        loadPropertiesForChildren(conn, parent, result);

        if (logger.isDebugEnabled()) {
            logger.debug("Loaded " + result.length + " resources");
        }

        return result;
    }


    private void loadChildrenForChildren(Connection conn, Resource parent,
            Resource[] children)
            throws SQLException {
        
        // Initialize a map from child.URI to the set of grandchildren's URIs:
        Map childMap = new HashMap();
        for (int i = 0; i < children.length; i++) {
            childMap.put(children[i].getURI(), new HashSet());
        }


        String query = "select uri from vortex_resource where uri like '"
            + getURIWildcard(parent.getURI()) + "' and depth = "
            + (getURIDepth(parent.getURI()) + 2);


        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        Map tempMap = new HashMap();

        while (rs.next()) {
            
//             Long parentID = new Long(rs.getLong("parent_id"));
            String uri = rs.getString("uri");
            String parentURI = URIUtil.getParentURI(uri);
            ((Set) childMap.get(parentURI)).add(uri);
        }

        rs.close();
        stmt.close();

        for (int i = 0; i < children.length; i++) {
            if (!children[i].isCollection()) continue;
            Set childURIs = (Set) childMap.get(children[i].getURI());
            children[i].setChildURIs((String[]) childURIs.toArray(new String[childURIs.size()]));
        }

    }

//     protected void loadACLsForChildren(Connection conn, Resource parent,
//             Resource[] children)
//             throws SQLException {

//         if (children == null || children.length == 0) {
//             return;
//         }

//         Map acls = new HashMap();
//         String[] parentPath = URLUtil.splitUriIncrementally(parent.getURI());
//         for (int i = parentPath.length - 1; i >= 0; i--) {
//             if (!acls.containsKey(parentPath[i])) {
//                 acls.put(parentPath[i], null);
//             }
//         }

//         /* Initialize (empty) ACL for resource: */
//         for (int i = 0; i < children.length; i++) {
//             children[i].setACL(new ACL());
//         }

//         if (acls.size() == 0) {
//             throw new SQLException("No ancestor path");
//         }

//         /*
//          * Populate the parent ACL map (these are all the ACLs that will be
//          * needed)
//          */

//         String query = "select r.uri, a.*, t.namespace as action_namespace, "
//                 + "t.name as action_name from ACL_ENTRY a "
//                 + "inner join ACTION_TYPE t on a.action_type_id = t.action_type_id "
//                 + "inner join VORTEX_RESOURCE r on r.resource_id = a.resource_id "
//                 + "where r.resource_id in (select resource_id from vortex_resource "
//             + "where uri in (?, ";
        
//         int n = acls.size();
//         for (int i = 0; i < n; i++) {
//             query += (i < n - 1) ? "?, " : "?)";
//         }
//         PreparedStatement stmt = conn.prepareStatement(query);

//         logger.info("--- " + query + "[" + acls.keySet() + "]");
        

//         stmt.setString(1, parent.getURI());
//         for (Iterator i = acls.keySet().iterator(); i.hasNext();) {
//             String uri = (String) i.next();
//             stmt.setString(n--, uri);
//         }
//         ResultSet rs = stmt.executeQuery();

//         while (rs.next()) {

//             String uri = rs.getString("uri");
//             String action = rs.getString("action_name");

//             ACL acl = (ACL) acls.get(uri);

//             if (acl == null) {

//                 acl = new ACL();
//                 acls.put(uri, acl);
//             }

//             Map actionMap = acl.getActionMap();

//             List actionEntry = (List) actionMap.get(action);
//             if (actionEntry == null) {
//                 actionEntry = new ArrayList();
//                 actionMap.put(action, actionEntry);
//             }

//             actionEntry.add(new ACLPrincipal(rs.getString("user_or_group_name"), rs
//                     .getString("is_user").equals("N")));
//         }

//         rs.close();
//         stmt.close();

//         /*
//          * The ACL map is now populated. Walk trough every resource and see if
//          * there is an ACL entry:
//          */

//         for (int i = 0; i < children.length; i++) {

//             Resource resource = children[i];
//             ACL acl = null;

//             if (!resource.isInheritedACL()) {

//                 if (!acls.containsKey(resource.getURI())) {
//                     throw new SQLException("Database inconsistency: resource "
//                             + resource.getURI() + " claims  ACL is inherited, "
//                             + "but no ACL exists");
//                 }

//                 acl = (ACL) acls.get(resource.getURI());

//             } else {

//                 String[] path = URLUtil.splitUriIncrementally(resource.getURI());

//                 for (int j = path.length - 2; j >= 0; j--) {

//                     ACL found = (ACL) acls.get(path[j]);

//                     if (found != null) {
//                         try {
//                             /*
//                              * We have to clone the ACL here, because ACLs and
//                              * children are "doubly linked".
//                              */
//                             acl = (ACL) found.clone();
//                         } catch (CloneNotSupportedException e) {
//                             throw new SQLException(e.getMessage());
//                         }

//                         break;
//                     }
//                 }

//                 if (acl == null) {
//                     throw new SQLException("Resource " + resource.getURI()
//                             + ": no ACL to inherit! At least root "
//                             + "resource should contain an ACL");
//                 }
//             }

//             resource.setACL(acl);
//         }
//     }

    private void loadPropertiesForChildren(Connection conn, Resource parent,
            Resource[] resources)
            throws SQLException {
        if ((resources == null) || (resources.length == 0)) {
            return;
        }

        String query = "select * from EXTRA_PROP_ENTRY where resource_id in ("
            + "select resource_id from vortex_resource "
            + "where uri like '" + getURIWildcard(parent.getURI()) + "' and depth = "
            + (getURIDepth(parent.getURI()) + 1) + ")";

        Statement propStmt = conn.createStatement();
        ResultSet rs = propStmt.executeQuery(query);
        Map resourceMap = new HashMap();

        final int namespace = 0;
        final int name = 1;
        final int value = 2;

        while (rs.next()) {
            // FIXME: type conversion
            Integer resourceID = new Integer((int) rs.getLong("resource_id"));

            Map propertyMap = (Map) resourceMap.get(resourceID);

            if (propertyMap == null) {
                propertyMap = new HashMap();
                resourceMap.put(resourceID, propertyMap);
            }

            String[] prop = new String[3];

            prop[namespace] = rs.getString("name_space");
            prop[name] = rs.getString("name");
            prop[value] = rs.getString("value");

            propertyMap.put(new Integer(rs.getInt("extra_prop_entry_id")), prop);
        }

        rs.close();
        propStmt.close();

        for (int i = 0; i < resources.length; i++) {
            Map propertyMap = (Map) resourceMap.get(new Integer(resources[i].getID()));

            if (propertyMap == null) {
                continue;
            }

            for (Iterator j = propertyMap.keySet().iterator(); j.hasNext();) {
                Integer key = (Integer) j.next();
                String[] prop = (String[]) propertyMap.get(key);

                if (prop != null) {
                    resources[i].addProperty(prop[namespace], prop[name], prop[value]);
                }
            }
        }
    }

    private Map loadLocksForChildren(Connection conn, Resource parent)
            throws SQLException {

        String query = "select r.uri as uri, l.* from VORTEX_RESOURCE r "
            + "inner join VORTEX_LOCK l on r.resource_id = l.resource_id "
            + "where r.resource_id in (select resource_id from vortex_resource "
            + "where uri like '" + getURIWildcard(parent.getURI()) + "' and depth = "
            + (getURIDepth(parent.getURI()) + 1) + ")";

        PreparedStatement stmt = conn.prepareStatement(query);

        ResultSet rs = stmt.executeQuery();
        Map result = new HashMap();

        while (rs.next()) {
            LockImpl lock = new LockImpl(
                rs.getString("token"), rs.getString("lock_owner"),
                rs.getString("lock_owner_info"), rs.getString("depth"),
                new Date(rs.getTimestamp("timeout").getTime()));

            if (lock.getTimeout().getTime() > System.currentTimeMillis()) {
                result.put(rs.getString("uri"), lock);
            }
        }

        rs.close();
        stmt.close();

        return result;
    }

    //------------- NEW STUFF

    public String[] discoverACLs(Resource resource) throws IOException {
        Connection conn = null;
        String[] retVal = null;

        try {
            conn = getConnection();
            retVal = discoverACLs(conn, resource);
            conn.commit();
        } catch (SQLException e) {
            logger.warn("Error occurred finding ACLs ", e);
            throw new IOException(e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                    conn = null;
                }
            } catch (SQLException e) {
                throw new IOException(e.getMessage());
            }
        }

        return retVal;
    }
    
    protected String[] discoverACLs(Connection conn, Resource resource)
            throws SQLException {

        String query = "select distinct r.uri as uri from ACL_ENTRY a inner join VORTEX_RESOURCE r "
            + "on a.resource_id = r.resource_id "
            + "where r.resource_id in ("
            + "select resource_id  from VORTEX_RESOURCE where uri like ?)";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt = conn.prepareStatement(query);
        stmt.setString(1, getURIWildcard(resource.getURI()));
        ResultSet rs = stmt.executeQuery();
        
        List uris = new ArrayList();

        while (rs.next()) {
            uris.add(rs.getString("uri"));
        }

        rs.close();
        stmt.close();

        return (String[]) uris.toArray(new String[uris.size()]);

    }


    public void copy(Resource resource, String destURI, boolean copyACLs,
                     boolean setOwner, String owner) throws IOException {
        Connection conn = null;

        try {
            conn = getConnection();
            copy(conn, resource, destURI, copyACLs, setOwner, owner);
            long timestamp = System.currentTimeMillis();
            conn.commit();
            long duration = System.currentTimeMillis() - timestamp;
            System.out.println("__COPY: " + resource.getURI() + " -> " + destURI
                               + ": commit took " + duration + " ms");
            
        } catch (SQLException e) {
            logger.warn("Error occurred while copying resource " + resource, e);
            throw new IOException(e.getMessage());
        } catch (IOException e) {
            logger.warn("Error occurred while copying resource " + resource, e);
            throw e;
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                    conn = null;
                }
            } catch (SQLException e) {
                throw new IOException(e.getMessage());
            }
        }
    }
    
    private void copy(Connection conn, Resource resource, String destURI, boolean copyACLs,
                      boolean setOwner, String owner) throws SQLException, IOException {

        int depthDiff = getURIDepth(destURI) - getURIDepth(resource.getURI());
        int idx = resource.getURI().length() + 1;
    
        String ownerVal = setOwner ? "'" + owner + "'" : "resource_owner";

        String query = "insert into vortex_resource (resource_id, prev_resource_id, "
            + "uri, depth, creation_time, content_last_modified, properties_last_modified, "
            + "content_modified_by, properties_modified_by, resource_owner, "
            + "display_name, description, content_language, content_type, character_encoding, "
            + "is_collection, acl_inherited) "
            + "select nextval('vortex_resource_seq_pk'), resource_id, "
            + "'" + destURI + "' || substring(uri, " + idx + ", length(uri)), "
            + "depth + " + depthDiff + ", creation_time, content_last_modified, "
            + "properties_last_modified, " 
            + "content_modified_by, properties_modified_by, " + ownerVal + ", display_name, "
            + "description, content_language, content_type, character_encoding, is_collection, "
            + "acl_inherited from vortex_resource "
            + "where uri = '" + resource.getURI() + "'"
            + "or uri like '" + getURIWildcard(resource.getURI()) + "'";
        
        System.out.println("__: " + query);
        

        Statement stmt = conn.createStatement();
        stmt.executeUpdate(query);
        stmt.close();

        query = "insert into extra_prop_entry (extra_prop_entry_id, "
            + "resource_id, name_space, name, value) "
            + "select nextval('extra_prop_entry_seq_pk'), r.resource_id, p.name_space, "
            + "p.name, p.value from vortex_resource r inner join extra_prop_entry p "
            + "on r.prev_resource_id = p.resource_id where r.uri = '" + destURI
            + "' or r.uri like '" + getURIWildcard(destURI) + "' "
            + "and r.prev_resource_id is not null";
        
        
        stmt = conn.createStatement();
        stmt.executeUpdate(query);
        stmt.close();


        if (copyACLs) {

            query = "insert into acl_entry (acl_entry_id, resource_id, "
                + "action_type_id, user_or_group_name, is_user, granted_by_user_name, "
                + "granted_date) "
                + "select nextval('acl_entry_seq_pk'), r.resource_id, a.action_type_id, "
                + "a.user_or_group_name, a.is_user, a.granted_by_user_name, a.granted_date "
                + "from vortex_resource r inner join acl_entry a "
                + "on r.prev_resource_id = a.resource_id "
                + "where r.uri = '" + destURI
                + "' or r.uri like '" + getURIWildcard(destURI) + "' "
                + "and r.prev_resource_id is not null";
        }


        query = "update vortex_resource set prev_resource_id = null "
            + "where uri = '" + destURI + "' or uri like '" + destURI + "/%'";

        System.out.println("__: " + query);

        stmt = conn.createStatement();
        stmt.executeUpdate(query);
        stmt.close();

        String fileNameFrom = this.repositoryDataDirectory
            + ((this.urlEncodeFileNames) ? URLUtil.urlEncode(resource.getURI())
               : resource.getURI());
        String fileNameTo = this.repositoryDataDirectory
            + ((this.urlEncodeFileNames) ? URLUtil.urlEncode(destURI)
               : destURI);

        long timestamp = System.currentTimeMillis();
        if (resource.isCollection()) {
            copyDir(new File(fileNameFrom), new File(fileNameTo));
        } else {
            copyFile(new File(fileNameFrom), new File(fileNameTo));
        }
        long duration = System.currentTimeMillis() - timestamp;
        if (logger.isDebugEnabled()) {
            logger.debug("Successfully copied '" + fileNameFrom + "' to '"
                         + fileNameTo + "' in " + duration + " ms");
        }
            System.out.println("Successfully copied '" + fileNameFrom + "' to '"
                               + fileNameTo + "' in " + duration + " ms");
    }
    

    private void copyDir(File fromDir, File toDir) throws IOException {

        if (!fromDir.isDirectory()) {
            throw new IllegalArgumentException("File " + fromDir + " is not a directory");
        }
        toDir.mkdir();

        File[] children = fromDir.listFiles();
        for (int i = 0; i < children.length; i++) {
            File newFile = new File(toDir.getCanonicalPath()
                                  + File.separator + children[i].getName());
            if (children[i].isFile()) {
                copyFile(children[i], newFile);
            } else {
                copyDir(children[i], newFile);
            }
        }
    }
    

    private void copyFile(File from, File to) throws IOException {

        FileChannel srcChannel = new FileInputStream(from).getChannel();
        FileChannel dstChannel = new FileOutputStream(to).getChannel();
        dstChannel.transferFrom(srcChannel, 0, srcChannel.size());
        srcChannel.close();
        dstChannel.close();
    }


}
