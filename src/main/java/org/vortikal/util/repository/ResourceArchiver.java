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
package org.vortikal.util.repository;

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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.Acl;
import org.vortikal.repository.Comment;
import org.vortikal.repository.Namespace;
import org.vortikal.repository.Path;
import org.vortikal.repository.Privilege;
import org.vortikal.repository.Property;
import org.vortikal.repository.Repository;
import org.vortikal.repository.RepositoryAction;
import org.vortikal.repository.Resource;
import org.vortikal.repository.ResourceTypeTree;
import org.vortikal.repository.resourcetype.PropertyType;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.repository.resourcetype.ValueFormatter;
import org.vortikal.security.InvalidPrincipalException;
import org.vortikal.security.Principal;
import org.vortikal.security.PrincipalFactory;
import org.vortikal.security.Principal.Type;
import org.vortikal.web.RequestContext;

public class ResourceArchiver {

    private static Log logger = LogFactory.getLog(ResourceArchiver.class);

    private PrincipalFactory principalFactory;
    private Repository repository;
    private ResourceTypeTree resourceTypeTree;
    private File tempDir = new File(System.getProperty("java.io.tmpdir"));

    private final String dateFormat = "yyyy-MM-dd'T'HH:mm:ssZ";
    private final String commentPath = "META-INF/COMMENTS/";

    public interface EventListener {
        public void expanded(Path uri);

        public void archived(Path uri);

        public void warn(Path uri, String msg);
    }

    public void createArchive(String token, Resource r, OutputStream out, String ignorableResources) throws Exception {
        createArchive(token, r, out, ignorableResources, null);
    }

    public void createArchive(String token, Resource r, OutputStream out, String ignorableResources,
            EventListener listener) throws Exception {

        // HACK VTK-1712
        List<String> ignoreList = getIgnoreList(ignorableResources);
        // END HACK

        logger.info("Creating archive '" + r.getURI() + "'");
        this.logIgnoredResources(ignoreList);

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
                addEntry(token, rootLevel, r, jo, listener, ignoreList);
                jo.close();
                out.close();
            } finally {
                if (tmp != null)
                    tmp.delete();
            }
        } catch (Exception e) {
            // Log the exception and throw it up the chain to break properly
            logger.error("An error occured while creating archive '" + r.getURI() + "'", e);
            throw e;
        }

        logger.info("Done creating archive '" + r.getURI() + "'");
    }

    public void expandArchive(String token, InputStream source, Path base, String ignorableResources) throws Exception {
        expandArchive(token, source, base, ignorableResources, null);
    }

    public void expandArchive(String token, InputStream source, Path base, String ignorableResources,
            EventListener listener) throws Exception {

        // HACK VTK-1712
        List<String> ignoreList = getIgnoreList(ignorableResources);
        // END HACK

        this.logIgnoredResources(ignoreList);

        JarInputStream jarIn = new JarInputStream(new BufferedInputStream(source));
        Manifest manifest = jarIn.getManifest();
        if (manifest != null) {
            String archiveVersion = manifest.getMainAttributes().getValue("X-vrtx-archive-version");
            if (archiveVersion != null && !"1.0".equals(archiveVersion)) {
                throw new RuntimeException("Incompatible archive version: " + archiveVersion);
            }
        }

        boolean decodeValues = manifest != null
                && "true".equals(manifest.getMainAttributes().getValue("X-vrtx-archive-encoded"));

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
                } catch (Throwable t) {
                    logger.error("Could not handle comment", t);
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
                storePropsAndPermissions(token, entry, uri, decodeValues, listener);
            }
            if (listener != null) {
                listener.expanded(uri);
            }
        }
        jarIn.close();

        // We restore comments after everything else, since comments aren't
        // crucial. And we don't break the archiving if something should go
        // wrong here
        for (Comment comment : comments) {
            try {
                this.repository.addComment(token, comment);
            } catch (Throwable t) {
                logger.error("Could not add comment to resource '" + comment.getURI() + "': " + t.getMessage());
            }
        }
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
            } else {
                content.append("\n" + entryLine);
            }
        }

        String path = commentContent.get(entryLinePrefix + "parent").toString();
        String author = commentContent.get(entryLinePrefix + "author").toString();
        String time = commentContent.get(entryLinePrefix + "time").toString();
        String title = null;
        if (commentContent.containsKey(entryLinePrefix + "title")) {
            title = commentContent.get(entryLinePrefix + "title").toString();
        }
        String content = commentContent.get(entryLinePrefix + "content").toString();

        Comment comment = new Comment();
        comment.setURI(Path.fromString(getExpandedEntryUri(base, path)));
        comment.setAuthor(this.principalFactory.getPrincipal(author, Type.USER));
        comment.setTitle(title);
        comment.setTime(new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").parse(time.toString()));
        comment.setContent(content.toString());

        return comment;
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
        out.println("X-vrtx-archive-version: 1.0");
        out.println("X-vrtx-archive-encoded: true");

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
        } catch (Exception e) {
            // We'll ignore resources that fail and continue. Log broken
            // resources and handle them manually some other way later.
            logger.error("Error writing manifest entry for '" + path.toString() + "'\n", e);
        }
    }

    private void addProperties(Resource r, PrintWriter out) throws Exception {
        List<Property> properties = r.getProperties();
        int propCounter = 0;
        for (Property property : properties) {
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
            } else if (propDef.getType() != PropertyType.Type.BINARY) {
                entry.append(property.getFormattedValue());
            }

            encode(entry);
            ensure72Bytes(entry);
            out.println(entry);
            propCounter++;
        }
    }

    private void addAcl(Resource r, PrintWriter out) throws Exception {
        if (!r.isInheritedAcl()) {
            Acl acl = r.getAcl();
            for (RepositoryAction action : acl.getActions()) {
                StringBuilder entry = new StringBuilder("X-vrtx-acl-");
                entry.append(Privilege.getActionName(action)).append(": ");

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
    }

    private void ensure72Bytes(StringBuilder s) throws Exception {
        int i = 0;
        int count = 0;
        while (i < s.length()) {
            int delta = s.substring(i, i + 1).getBytes("utf-8").length;
            if (count + delta >= 72) {
                s.insert(i, "\n ");
                count = 0;
            } else {
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
            List<String> ignoreList) throws Exception {

        String path = getJarPath(r, fromLevel);

        if (isIgnorableResource(path, ignoreList)) {
            return;
        }

        JarEntry je = new JarEntry(path);
        jarOut.putNextEntry(je);
        if (r.isCollection()) {
            Resource[] children = this.repository.listChildren(token, r.getURI(), false);
            for (Resource child : children) {
                addEntry(token, fromLevel, child, jarOut, listener, ignoreList);
            }
        } else {

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
                    archiveComments(token, r, jarOut);
                } catch (Throwable t) {
                    logger.error("Could not archive comment for resource '" + r.getURI() + "': " + t.getMessage());
                }

            } catch (Throwable t) {
                logger.error("Colud not archive content for resource '" + r.getURI() + "': " + t.getMessage());
            }

        }
        if (listener != null)
            listener.archived(r.getURI());
    }

    private void archiveComments(String token, Resource r, JarOutputStream jo) throws IOException {
        List<Comment> comments = this.repository.getComments(token, r);
        for (Comment comment : comments) {
            JarEntry je = new JarEntry(commentPath + comment.getID() + ".txt");
            jo.putNextEntry(je);
            StringBuilder sb = new StringBuilder();
            RequestContext rc = RequestContext.getRequestContext();
            Path currentCollection = rc.getCurrentCollection();
            Path archivedResourcePath = r.getURI();
            int subStringIndex = currentCollection.isRoot() ? currentCollection.toString().length() : currentCollection
                    .toString().length() + 1;
            String archivedCommentParentPath = archivedResourcePath.toString().substring(subStringIndex);
            sb.append("X-vrtx-comment-parent: " + archivedCommentParentPath + "\n");
            sb.append("X-vrtx-comment-author: " + comment.getAuthor() + "\n");
            String title = comment.getTitle();
            if (title != null && !"".equals(title.trim())) {
                sb.append("X-vrtx-comment-title: " + comment.getTitle() + "\n");
            }
            sb.append("X-vrtx-comment-time: " + comment.getTime() + "\n");
            sb.append("X-vrtx-comment-content: " + comment.getContent());
            jo.write(sb.toString().getBytes());
        }
    }

    private void storePropsAndPermissions(String token, JarEntry entry, Path resourceURI, boolean decode,
            EventListener listener) throws Exception {
        Attributes attributes = entry.getAttributes();
        if (attributes == null) {
            return;
        }

        Resource resource = this.repository.retrieve(token, resourceURI, false);
        boolean propsModified = false;
        boolean aclModified = false;

        // Clear ACL before extracting resource metadata
        // (ACL will only be explicitly stored if resource does not inherit ACL)
        resource.getAcl().clear();

        for (Object key : attributes.keySet()) {

            String name = key.toString();
            if (name.startsWith("X-vrtx-prop-")) {
                if (setProperty(resource, name, attributes, decode, listener)) {
                    propsModified = true;
                }
            } else if (name.startsWith("X-vrtx-acl-")) {
                if (setAclEntry(resource, name, attributes, decode, listener)) {
                    aclModified = true;
                }
            }
        }
        if (propsModified) {
            this.repository.store(token, resource);
        }
        if (aclModified) {
            // XXX: Repository API not "friendly" to special clients like
            // resource archiver/expander wrt. ACL modifications. One shouldn't
            // have to call storeACL twice. And don't validate acl when writing acl
            // to db, add it the same way it was 
            this.repository.storeACL(token, resource, false); // Switch inheritance off
            this.repository.storeACL(token, resource, false); // Store new ACL
        }
    }

    private boolean setProperty(Resource resource, String name, Attributes attributes, boolean decode,
            EventListener listener) throws Exception {
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
            if (listener != null) {
                listener.warn(resource.getURI(), "empty value for property '" + propDef.getName() + "', skipping");
            }
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

        if (propDef.isMultiple()) {
            List<Value> values = new ArrayList<Value>();
            String[] splitValues = rawValue.split(",");
            for (String val : splitValues) {
                values.add(valueFormatter.stringToValue(val.trim(), format, null));
            }
            prop.setValues(values.toArray(new Value[values.size()]));
        } else {
            prop.setValue(valueFormatter.stringToValue(rawValue.trim(), format, null));
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
        } else {
            int i = idx;
            while (valueString.charAt(i) != ' ') {
                i++;
            }
            prefix = valueString.substring("prefix:".length(), i);
            idx = i + 1;
        }

        String name = null;

        if (!valueString.substring(idx).startsWith("name:")) {
            return null;
        }
        idx += "name:".length();
        int nameEndIdx = idx;
        while (valueString.charAt(nameEndIdx) != ' ') {
            nameEndIdx++;
        }
        name = valueString.substring(idx, nameEndIdx);
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

    private boolean setAclEntry(Resource resource, String name, Attributes attributes, boolean decode,
            EventListener listener) throws Exception {
        String actionName = name.substring("X-vrtx-acl-".length());
        RepositoryAction action = Privilege.getActionByName(actionName);

        String values = attributes.getValue(name);
        if (decode) {
            values = decodeValue(values);
        }
        String[] list = values.split(",");

        Acl acl = resource.getAcl();
        for (String value : list) {
            String principalName = value.substring(2);
            Principal p = null;
            char type = value.charAt(0);
            switch (type) {
            case 'p':
                try {
                    p = principalFactory.getPrincipal(principalName, Type.PSEUDO);
                } catch (InvalidPrincipalException e) {
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
                resource.setInheritedAcl(false);
                if (acl.isValidEntry(action, p)) {
                    acl.addEntry(action, p);
                } else {
                    if (listener != null) {
                        listener.warn(resource.getURI(), "Invalid acl entry: " + p + ":" + action + ", skipping");
                    }
                }
            } else {
                if (listener != null) {
                    listener.warn(resource.getURI(), "Invalid principal: " + p + ", skipping");
                }
            }
        }
        return !resource.isInheritedAcl();
    }

    private boolean writeFile(String token, Path uri, ZipInputStream is) {
        try {
            this.repository.createDocument(token, uri, new PartialZipStream(is));
        } catch (Exception e) {
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

    private class PartialZipStream extends InputStream {
        ZipInputStream in;

        PartialZipStream(ZipInputStream in) {
            this.in = in;
        }

        public int available() throws IOException {
            return this.in.available();
        }

        public void close() throws IOException {
        }

        public void mark(int readLimit) {
            this.in.mark(readLimit);
        }

        public void reset() throws IOException {
            this.in.reset();
        }

        public boolean markSupported() {
            return false;
        }

        public int read() throws IOException {
            return this.in.read();
        }

        public int read(byte[] b) throws IOException {
            return this.in.read(b);
        }

        public int read(byte[] b, int off, int len) throws IOException {
            return this.in.read(b, off, len);
        }

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

    // HACK VTK-1712
    private List<String> getIgnoreList(String ignorableResources) {
        List<String> ignoreList = null;
        if (ignorableResources != null) {
            ignoreList = new ArrayList<String>();
            String[] ss = ignorableResources.split(",");
            for (String s : ss) {
                s = s.trim();
                if (!"".equals(s)) {
                    ignoreList.add(s);
                }
            }
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
                    ignored.append("\n  " + ignorableResource);
                }
            }
            logger.info(ignored);
        }
    }
    // END HACK

}
