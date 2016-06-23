/* Copyright (c) 2008, University of Oslo, Norway
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
package vtk.util.repository;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Acl;
import vtk.repository.Comment;
import vtk.repository.InheritablePropertiesStoreContext;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Privilege;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.RepositoryAction;
import vtk.repository.Resource;
import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.repository.resourcetype.ValueFormatter;
import vtk.security.InvalidPrincipalException;
import vtk.security.Principal;
import vtk.security.Principal.Type;
import vtk.security.PrincipalFactory;

public class ResourceArchiver {

    private static Logger logger = LoggerFactory.getLogger(ResourceArchiver.class);

    private PrincipalFactory principalFactory;
    private Repository repository;
    private ResourceTypeTree resourceTypeTree;
    private File tempDir = new File(System.getProperty("java.io.tmpdir"));

    private final String dateFormat = "yyyy-MM-dd'T'HH:mm:ssZ";
    private final String commentPath = "META-INF/COMMENTS/";
    private final String versionAttribute = "X-vrtx-archive-version";
    private final String encodedAttribute = "X-vrtx-archive-encoded";

    private Map<String, String> legacyPrincipalMappings = new HashMap<String, String>();
    private Map<String, String> legacyActionMappings = new HashMap<String, String>();

    public interface EventListener {
        public void expanded(Path uri);

        public void archived(Path uri);

        public void warn(Path uri, String msg);
    }

    private static final EventListener NULL_LISTENER = new EventListener() {
        @Override
        public void expanded(Path uri) {
        }

        @Override
        public void archived(Path uri) {
        }

        @Override
        public void warn(Path uri, String msg) {
            logger.warn(uri + ": " + msg);
        }
    };

    public void createArchive(String token, Resource r, OutputStream out, Map<String, Object> properties)
            throws Exception {
        createArchive(token, r, out, properties, NULL_LISTENER);
    }

    public void createArchive(String token, Resource r, OutputStream out, Map<String, Object> properties,
            EventListener listener) throws Exception {

        if (listener == null) {
            listener = NULL_LISTENER;
        }

        List<String> ignoreList = this.getIgnoreList(properties);

        logger.info("Creating archive '" + r.getURI() + "'");

        try {
            int rootLevel = r.getURI().getDepth() + 1;
            File tmp = null;
            try {
                tmp = File.createTempFile("tmp-manifest", "vrtx", this.tempDir);
                PrintWriter manifestOut = new PrintWriter(new FileOutputStream(tmp));
                writeManifest(token, rootLevel, r, manifestOut, ignoreList);
                logger.info("Writing manifest...");
                Manifest manifest = new Manifest(new FileInputStream(tmp));
                logger.info("Manifest written, creating jar...");
                JarOutputStream jo = new JarOutputStream(out, manifest);
                addEntry(token, rootLevel, r, jo, listener, ignoreList, r.getURI());
                jo.close();
                out.close();
            }
            finally {
                if (tmp != null)
                    tmp.delete();
            }
        }
        catch (Exception e) {
            // Logger the exception and throw it up the chain to break properly
            logger.error("An error occured while creating archive '" + r.getURI() + "'", e);
            throw e;
        }

        logger.info("Done creating archive '" + r.getURI() + "'");
    }

    public void expandArchive(String token, InputStream source, Path base, Map<String, Object> properties)
            throws Exception {
        expandArchive(token, source, base, properties, NULL_LISTENER);
    }

    public void expandArchive(String token, InputStream source, Path base, Map<String, Object> properties,
            EventListener listener) throws Exception {

        if (listener == null) {
            listener = NULL_LISTENER;
        }

        List<String> ignoreList = this.getIgnoreList(properties);

        JarInputStream jarIn = new JarInputStream(new BufferedInputStream(source));
        Manifest manifest = jarIn.getManifest();
        boolean legacyAcl = false;
        if (manifest != null) {
            String archiveVersion = manifest.getMainAttributes().getValue(this.versionAttribute);
            if (!isValidArchiveVersion(archiveVersion)) {
                throw new RuntimeException("Incompatible archive version: " + archiveVersion);
            }
            legacyAcl = "1.0".equals(archiveVersion);
        }

        boolean decodeValues = manifest != null
                && "true".equals(manifest.getMainAttributes().getValue(this.encodedAttribute));

        JarEntry entry;
        Set<Path> dirCache = new HashSet<Path>();
        List<Path> paths = base.getParent().getPaths();
        for (Path p : paths) {
            dirCache.add(p);
        }

        // XXX: dir modification times
        List<Comment> comments = new ArrayList<Comment>();
        logger.info("Writing jar entries");
        while ((entry = jarIn.getNextJarEntry()) != null) {
            String entryPath = entry.getName();

            if (isIgnorableResource(entryPath, ignoreList)) {
                continue;
            }

            // Keep comments for later processing, add them after resources
            // have been expanded
            if (entryPath.startsWith(commentPath)) {
                try {
                    comments.add(getArchivedComment(jarIn, base));
                }
                catch (Throwable t) {
                    logger.error("Could not handle comment in " + entryPath, t);
                }
                continue;
            }

            String resourceURI = getExpandedEntryUri(base, entryPath);

            Path uri = Path.fromString(resourceURI);
            Path dir = entry.isDirectory() ? uri : uri.getParent();
            createDirectoryStructure(token, dir, dirCache);

            boolean canStorePropsAndPermissions = true;
            if (!entry.isDirectory()) {
                canStorePropsAndPermissions = writeFile(token, uri, jarIn);
            }
            if (canStorePropsAndPermissions) {
                storePropsAndPermissions(token, entry, uri, decodeValues, legacyAcl, listener);
            }
            listener.expanded(uri);
        }
        jarIn.close();

        // We restore comments after everything else, since comments aren't
        // crucial. And we don't break the archiving if something should go
        // wrong here
        for (Comment comment : comments) {
            try {
                this.repository.addComment(token, comment);
            }
            catch (Throwable t) {
                logger.error("Could not add comment to resource '" + comment.getURI() + "': " + t.getMessage());
            }
        }
    }

    private boolean isValidArchiveVersion(String archiveVersion) {
        return archiveVersion != null && ("1.0".equals(archiveVersion) || "2.0".equals(archiveVersion));
    }

    private Comment getArchivedComment(JarInputStream jarIn, Path base) throws Exception {

        BufferedReader reader = new BufferedReader(new InputStreamReader(jarIn));

        String entryLinePrefix = "X-vrtx-comment-";
        String entryLine = null;
        String entryKey = null;
        Map<String, StringBuilder> commentContent = new HashMap<String, StringBuilder>();
        while ((entryLine = reader.readLine()) != null) {
            entryKey = entryLine.startsWith(entryLinePrefix) ? entryLine.substring(0, entryLine.indexOf(":"))
                    : entryKey;
            StringBuilder content = commentContent.get(entryKey);
            if (content == null) {
                commentContent.put(entryKey, new StringBuilder(entryLine.substring(entryLine.indexOf(":") + 1).trim()));
            }
            else {
                content.append("\n" + entryLine);
            }
        }

        String path = this.getRequiredCommentAttribute(entryLinePrefix + "parent", commentContent);
        String author = this.getRequiredCommentAttribute(entryLinePrefix + "author", commentContent);
        String time = this.getRequiredCommentAttribute(entryLinePrefix + "time", commentContent);
        Date timestamp = null;
        try {
            timestamp = new SimpleDateFormat(dateFormat).parse(time);
        }
        catch (Throwable t) {
            // Try java.util.Date's default format before giving up:
            timestamp = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy").parse(time);
        }
        String title = null;
        if (commentContent.containsKey(entryLinePrefix + "title")) {
            title = commentContent.get(entryLinePrefix + "title").toString();
        }
        String content = this.getRequiredCommentAttribute(entryLinePrefix + "content", commentContent);

        Comment comment = new Comment();
        comment.setURI(Path.fromString(getExpandedEntryUri(base, path)));
        comment.setAuthor(this.principalFactory.getPrincipal(author, Type.USER));
        comment.setTitle(title);
        comment.setTime(timestamp);
        comment.setContent(content);

        return comment;
    }

    private String getRequiredCommentAttribute(String key, Map<String, StringBuilder> commentContent) {
        StringBuilder sb = commentContent.get(key);
        if (sb == null) {
            logger.warn("Could not add comment, missing required key " + key + ". Check archive file contents.");
            return null;
        }
        return sb.toString();
    }

    private String getExpandedEntryUri(Path base, String entryPath) {
        String resourceURI = entryPath.startsWith("/") ? base + entryPath : base + "/" + entryPath;
        if (resourceURI.endsWith("/")) {
            resourceURI = resourceURI.substring(0, resourceURI.length() - 1);
        }
        return resourceURI;
    }

    private void writeManifest(String token, int rootLevel, Resource r, PrintWriter out, List<String> ignoreList)
            throws Exception {

        out.println("Manifest-Version: 1.0");
        out.println("Created-By: vrtx");
        out.println(this.versionAttribute + ": 2.0");
        out.println(this.encodedAttribute + ": true");

        addManifestEntry(token, rootLevel, r, out, ignoreList);
        out.flush();
        out.close();

    }

    private String getJarPath(Resource resource, int fromLevel) {
        Path path = resource.getURI();
        List<String> elements = path.getElements();
        StringBuilder result = new StringBuilder("/");
        for (int i = fromLevel; i < elements.size(); i++) {
            if (i != 0)
                result.append(elements.get(i));
            if (i < elements.size() - 1 && !"/".equals(elements.get(i)))
                result.append("/");
        }
        if (resource.isCollection() && !"/".equals(result.toString()))
            result.append("/");
        return result.toString();
    }

    private void addManifestEntry(String token, int fromLevel, Resource r, PrintWriter out, List<String> ignoreList)
            throws Exception {

        StringBuilder path = new StringBuilder(getJarPath(r, fromLevel));

        if (isIgnorableResource(path.toString(), ignoreList)) {
            return;
        }

        try {

            out.println("");
            StringBuilder name = new StringBuilder("Name: ");
            name.append(path.toString().replaceAll("\\r|\\n", ""));
            ensure72Bytes(name);
            out.println(name);

            addProperties(r, out);
            addAcl(r, out);

            if (r.isCollection()) {
                Resource[] children = this.repository.listChildren(token, r.getURI(), false);
                for (Resource child : children) {
                    addManifestEntry(token, fromLevel, child, out, ignoreList);
                }
            }
        }
        catch (Exception e) {
            // We'll ignore resources that fail and continue. Logger broken
            // resources and handle them manually some other way later.
            logger.error("Error writing manifest entry for '" + path.toString() + "'\n", e);
        }
    }

    private void addProperties(Resource r, PrintWriter out) throws Exception {
        int propCounter = 0;
        for (Property property : r) {
            if (property.isInherited()) {
                // Skip inherited properties
                continue;
            }

            PropertyTypeDefinition propDef = property.getDefinition();
            Namespace namespace = propDef.getNamespace();

            StringBuilder entry = new StringBuilder("X-vrtx-prop-");
            entry.append(propCounter).append(": ");
            entry.append("prefix:");
            if (namespace.getPrefix() != null) {
                entry.append(namespace.getPrefix());
            }
            entry.append(" ");
            entry.append("name:").append(propDef.getName()).append(" ");
            if (propDef.getType() == PropertyType.Type.DATE || propDef.getType() == PropertyType.Type.TIMESTAMP) {
                entry.append(property.getFormattedValue(dateFormat, null));
            }
            else if (propDef.getType() != PropertyType.Type.BINARY) {
                entry.append(property.getFormattedValue());
            }

            encode(entry);
            ensure72Bytes(entry);
            out.println(entry);
            propCounter++;
        }
    }

    private void addAcl(Resource r, PrintWriter out) throws Exception {
        if (r.isInheritedAcl()) {
            return;
        }
        Acl acl = r.getAcl();
        for (Privilege action : acl.getActions()) {
            StringBuilder entry = new StringBuilder("X-vrtx-acl-");
            entry.append(action.getName()).append(": ");

            boolean empty = true;

            Principal[] users = acl.listPrivilegedUsers(action);
            for (int i = 0; i < users.length; i++) {
                Principal user = users[i];
                if (i > 0)
                    entry.append(",");
                entry.append("u:").append(user.getQualifiedName());
                empty = false;
            }

            Principal[] groups = acl.listPrivilegedGroups(action);
            for (int i = 0; i < groups.length; i++) {
                Principal group = groups[i];
                if (!empty || i > 0)
                    entry.append(",");
                entry.append("g:").append(group.getQualifiedName());
                empty = false;
            }

            Principal[] pseudos = acl.listPrivilegedPseudoPrincipals(action);
            for (int i = 0; i < pseudos.length; i++) {
                Principal pseudo = pseudos[i];
                if (!empty || i > 0)
                    entry.append(",");
                entry.append("p:").append(pseudo.getQualifiedName());
                empty = false;
            }

            if (!empty) {
                encode(entry);
                ensure72Bytes(entry);
                out.println(entry.toString());
            }
        }
    }

    private void ensure72Bytes(StringBuilder s) throws Exception {
        int i = 0;
        int count = 0;
        while (i < s.length()) {
            int delta = s.substring(i, i + 1).getBytes("utf-8").length;
            if (count + delta >= 72) {
                s.insert(i, "\n ");
                count = 0;
            }
            else {
                count += delta;
            }
            i++;
        }
    }

    /**
     * Flatten "\r" and "\n" (not allowed in manifest entries)
     */
    private void encode(StringBuilder s) {
        // '_' --> '_esc_u_'
        // '\r' --> '_esc_r_'
        // '\n' --> '_esc_n_'
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            switch (c) {
            case '_':
                s.delete(i, i + 1);
                s.insert(i, "_esc_u_");
                i += 7;
                break;
            case '\r':
                s.delete(i, i + 1);
                s.insert(i, "_esc_r_");
                i += 7;
                break;
            case '\n':
                s.delete(i, i + 1);
                s.insert(i, "_esc_n_");
                i += 7;
                break;
            default:
                i++;
                break;
            }
        }
    }

    private void addEntry(String token, int fromLevel, Resource r, JarOutputStream jarOut, EventListener listener,
            List<String> ignoreList, Path baseResourceToArchivePath) throws Exception {

        String path = getJarPath(r, fromLevel);

        if (isIgnorableResource(path, ignoreList)) {
            return;
        }

        JarEntry je = new JarEntry(path);
        jarOut.putNextEntry(je);
        if (r.isCollection()) {
            Resource[] children = this.repository.listChildren(token, r.getURI(), false);
            for (Resource child : children) {
                addEntry(token, fromLevel, child, jarOut, listener, ignoreList, baseResourceToArchivePath);
            }
        }
        else {
            try {
                InputStream is = this.repository.getInputStream(token, r.getURI(), false);
                BufferedInputStream bi = new BufferedInputStream(is);

                byte[] buf = new byte[1024];
                int n;
                while ((n = bi.read(buf)) != -1) {
                    jarOut.write(buf, 0, n);
                }
                bi.close();

                // We don't break the archiving if something should go wrong
                // with comments
                try {
                    archiveComments(token, r, jarOut, baseResourceToArchivePath);
                }
                catch (Throwable t) {
                    logger.error("Could not archive comment for resource '" + r.getURI() + "': " + t.getMessage());
                }

            }
            catch (Throwable t) {
                logger.error("Colud not archive content for resource '" + r.getURI() + "': " + t.getMessage());
            }

        }
        listener.archived(r.getURI());
    }

    private void archiveComments(String token, Resource r, JarOutputStream jo, Path baseResourceToArchivePath)
            throws IOException {
        List<Comment> comments = this.repository.getComments(token, r);
        for (Comment comment : comments) {
            JarEntry je = new JarEntry(commentPath + comment.getID() + ".txt");
            jo.putNextEntry(je);
            StringBuilder sb = new StringBuilder();
            Path archivedResourcePath = r.getURI();
            int subStringIndex = baseResourceToArchivePath.isRoot() ? baseResourceToArchivePath.toString().length()
                    : baseResourceToArchivePath.toString().length() + 1;

            String timestamp = new SimpleDateFormat(dateFormat).format(comment.getTime());

            String archivedCommentParentPath = archivedResourcePath.toString().substring(subStringIndex);
            sb.append("X-vrtx-comment-parent: ").append(archivedCommentParentPath).append("\n");
            sb.append("X-vrtx-comment-author: ").append(comment.getAuthor()).append("\n");
            String title = comment.getTitle();
            if (title != null && !"".equals(title.trim())) {
                sb.append("X-vrtx-comment-title: ").append(comment.getTitle()).append("\n");
            }
            sb.append("X-vrtx-comment-time: ").append(timestamp).append("\n");
            sb.append("X-vrtx-comment-content: ").append(comment.getContent());
            jo.write(sb.toString().getBytes());
        }
    }

    private void storePropsAndPermissions(String token, JarEntry entry, Path resourceURI, boolean decode,
            boolean legacyAcl, EventListener listener) throws Exception {
        Attributes attributes = entry.getAttributes();
        if (attributes == null) {
            return;
        }

        Resource resource = this.repository.retrieve(token, resourceURI, false);
        boolean propsModified = false;

        // ACL will only be explicitly stored if resource does not inherit ACL
        Acl acl = Acl.EMPTY_ACL;
        boolean aclModified = false;

        InheritablePropertiesStoreContext sc = new InheritablePropertiesStoreContext();

        for (Object key : attributes.keySet()) {

            String name = key.toString();
            if (name.startsWith("X-vrtx-prop-")) {
                if (setProperty(resource, name, attributes, decode, sc, listener)) {
                    propsModified = true;
                }
            }
            else if (name.startsWith("X-vrtx-acl-")) {
                acl = setAclEntry(resource.getURI(), acl, name, attributes, decode, legacyAcl, listener);
                aclModified = true;
            }
        }
        try {
            if (propsModified) {
                if (!sc.getAffectedProperties().isEmpty()) {
                    this.repository.store(token, resource, sc);
                }
                else {
                    this.repository.store(token, resource);
                }
            }
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to store resource " + resource, t);
        }
        if (!aclModified) {
            return;
        }

        resource = this.repository.storeACL(token, resource.getURI(), acl, false);
    }

    private boolean setProperty(Resource resource, String name, Attributes attributes, boolean decode,
            InheritablePropertiesStoreContext sc, EventListener listener) throws Exception {
        String valueString = attributes.getValue(name);
        if (decode) {
            valueString = decodeValue(valueString);
        }
        PropertyTypeDefinition propDef = parsePropDef(valueString);
        if (propDef == null)
            return false;
        if (propDef.getProtectionLevel() == RepositoryAction.UNEDITABLE_ACTION) {
            return false;
        }
        String rawValue = parseRawValue(valueString);
        if (rawValue == null || "".equals(rawValue.trim())) {
            listener.warn(resource.getURI(), "empty value for property '" + propDef.getName() + "', skipping");
            return false;
        }
        Property prop = resource.getProperty(propDef);
        if (prop == null) {
            prop = propDef.createProperty();
            resource.addProperty(prop);
        }
        ValueFormatter valueFormatter = propDef.getValueFormatter();
        String format = null;
        if (propDef.getType() == PropertyType.Type.DATE || propDef.getType() == PropertyType.Type.TIMESTAMP) {
            format = dateFormat;
        }

        try {
            if (propDef.isMultiple()) {
                List<Value> values = new ArrayList<Value>();
                String[] splitValues = rawValue.split(",");
                for (String val : splitValues) {
                    values.add(valueFormatter.stringToValue(val.trim(), format, null));
                }
                prop.setValues(values.toArray(new Value[values.size()]));
            }
            else {
                prop.setValue(valueFormatter.stringToValue(rawValue.trim(), format, null));
            }
        }
        catch (Throwable t) {
            throw new RuntimeException(
                    "Failed to set value(s) of property " + propDef + 
                        " on " + resource + " to: " + rawValue, t);
        }

        if (propDef.isInheritable()) {
            // Make sure inheritable props are re-created as well
            sc.addAffectedProperty(propDef);
        }

        return true;
    }

    private PropertyTypeDefinition parsePropDef(String valueString) {
        if (!valueString.startsWith("prefix:")) {
            return null;
        }

        String prefix = null;
        int idx = "prefix:".length();
        if (valueString.charAt(idx) == ' ') {
            idx++;
        }
        else {
            int i = idx;
            while (valueString.charAt(i) != ' ') {
                i++;
            }
            prefix = valueString.substring("prefix:".length(), i);
            idx = i + 1;
        }

        if (!valueString.substring(idx).startsWith("name:")) {
            return null;
        }
        idx += "name:".length();
        int nameEndIdx = idx;
        while (valueString.charAt(nameEndIdx) != ' ') {
            nameEndIdx++;
        }
        String name = valueString.substring(idx, nameEndIdx);
        return this.resourceTypeTree.getPropertyDefinitionByPrefix(prefix, name);
    }

    private String parseRawValue(String valueString) {
        // Assume correctly formatted string
        int idx = "prefix:".length();
        while (valueString.charAt(idx) != ' ')
            idx++;
        idx++;
        idx += "name:".length();
        while (valueString.charAt(idx) != ' ')
            idx++;
        idx++;
        return valueString.substring(idx);
    }

    private Acl setAclEntry(Path uri, Acl acl, String name, Attributes attributes, boolean decode, boolean legacyAcl,
            EventListener listener) throws Exception {

        String actionName = name.substring("X-vrtx-acl-".length());
        if (legacyAcl) {
            if (this.legacyActionMappings.containsKey(actionName)) {
                String mapping = this.legacyActionMappings.get(actionName);
                if (mapping == null || mapping.trim().equals("")) {
                    listener.warn(uri, "legacy: ignoring acl entry action " + actionName);
                    return acl;
                }
                listener.warn(uri, "legacy: mapping acl entry action: " + actionName + ": " + mapping);
                actionName = mapping;
            }
        }
        Privilege action = Privilege.forName(actionName);

        String values = attributes.getValue(name);
        if (decode) {
            values = decodeValue(values);
        }
        String[] list = values.split(",");

        for (String value : list) {
            if (legacyAcl) {
                if (this.legacyPrincipalMappings.containsKey(value)) {
                    String mapping = this.legacyPrincipalMappings.get(value);
                    if (mapping == null || "".equals(mapping.trim())) {
                        listener.warn(uri, "legacy: dropping principal from ACL: " + value.substring(2));
                        continue;
                    }
                    listener.warn(uri, "legacy: mapping principal in ACL: " + value + ": " + mapping);
                    value = mapping;
                }
            }
            String principalName = value.substring(2);
            Principal p = null;
            char type = value.charAt(0);
            switch (type) {
            case 'p':
                try {
                    p = principalFactory.getPrincipal(principalName, Type.PSEUDO);
                }
                catch (InvalidPrincipalException e) {
                    // The pseudo principal doesn't exist, drop it
                }
                break;
            case 'u':
                p = principalFactory.getPrincipal(principalName, Type.USER);
                break;
            case 'g':
                p = principalFactory.getPrincipal(principalName, Type.GROUP);
                break;
            }
            if (p != null) {
                if (!this.repository.isBlacklisted(action, p)) {
                    acl = acl.addEntry(action, p);
                }
                else {
                    listener.warn(uri, "Invalid acl entry: " + p + ":" + action + ", skipping");
                }
            }
            else {
                listener.warn(uri, "Invalid principal: " + principalName + ", skipping");
            }
        }
        return acl;
    }

    private boolean writeFile(String token, Path uri, ZipInputStream is) {
        try {
            this.repository.createDocument(token, uri, new PartialZipStream(is));
        }
        catch (Exception e) {
            logger.error("Error writing resource '" + uri + "': " + e.getMessage());
            return false;
        }
        return true;
    }

    private String decodeValue(String s) {
        s = s.replaceAll("_esc_n_", "\n");
        s = s.replaceAll("_esc_r_", "\r");
        s = s.replaceAll("_esc_u_", "_");
        return s;
    }

    private void createDirectoryStructure(String token, Path dir, Set<Path> dirCache) throws Exception {
        List<Path> path = dir.getPaths();
        for (Path p : path) {
            if (!dirCache.contains(p)) {
                this.repository.createCollection(token, p);
                dirCache.add(p);
            }
        }
    }

    private static class PartialZipStream extends InputStream {
        ZipInputStream in;

        PartialZipStream(ZipInputStream in) {
            this.in = in;
        }

        @Override
        public int available() throws IOException {
            return this.in.available();
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public void mark(int readLimit) {
            this.in.mark(readLimit);
        }

        @Override
        public void reset() throws IOException {
            this.in.reset();
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public int read() throws IOException {
            return this.in.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return this.in.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return this.in.read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            return this.in.skip(n);
        }
    }

    public void setTempDir(String tempDirPath) {
        File tmp = new File(tempDirPath);
        if (!tmp.exists()) {
            throw new IllegalArgumentException("Unable to set tempDir: file " + tmp + " does not exist");
        }
        if (!tmp.isDirectory()) {
            throw new IllegalArgumentException("Unable to set tempDir: file " + tmp + " is not a directory");
        }
        this.tempDir = tmp;
    }

    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    @Required
    public void setPrincipalFactory(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }

    public void setLegacyPrincipalMappings(Map<String, String> legacyPrincipalMappings) {
        for (Map.Entry<String, String> entry : legacyPrincipalMappings.entrySet()) {
            this.legacyPrincipalMappings.put(entry.getKey(), entry.getValue());
        }
    }

    public void setLegacyActionMappings(Map<String, String> legacyActionMappings) {
        this.legacyActionMappings = legacyActionMappings;
    }

    @SuppressWarnings("unchecked")
    private List<String> getIgnoreList(Map<String, Object> properties) {
        List<String> ignoreList = null;
        if (properties != null) {
            ignoreList = (List<String>) properties.get("ignore");
            this.logIgnoredResources(ignoreList);
        }
        return ignoreList;
    }

    private boolean isIgnorableResource(String resourcePath, List<String> ignoreList) {
        if (ignoreList != null && ignoreList.size() > 0) {
            for (String ignorableResource : ignoreList) {
                if (ignorableResource.equals(resourcePath)
                        || (ignorableResource.endsWith("/") && resourcePath.startsWith(ignorableResource))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void logIgnoredResources(List<String> ignoreList) {
        if (ignoreList != null && ignoreList.size() > 0) {
            StringBuilder ignored = new StringBuilder();
            for (String ignorableResource : ignoreList) {
                if (!"".equals(ignorableResource.trim())) {
                    if (ignored.toString().equals("")) {
                        ignored.append("Ignoring the following resources:");
                    }
                    ignored.append("\n  ").append(ignorableResource);
                }
            }
            logger.info(ignored.toString());
        }
    }
}
