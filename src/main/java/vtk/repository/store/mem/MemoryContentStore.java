/* Copyright (c) 2006, 2007, University of Oslo, Norway
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
package vtk.repository.store.mem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import vtk.repository.ContentInputSource;

import vtk.repository.IllegalOperationException;
import vtk.repository.Path;
import vtk.repository.RecoverableResource;
import vtk.repository.store.ContentStore;
import vtk.repository.store.DataAccessException;

/**
 * Very simple memory content store. Might be helpful in testing scenarios
 * combined with hsqldb for a memory-only Vortex instance. Should be thread
 * safe. (see test case
 * <code>vtk.repositoryimpl.dao.MemoryContentStoreTestCase</code>)
 * 
 * It maintains an internal tree structure of content- and directory-nodes.
 * 
 * @author oyviste
 * 
 */
public class MemoryContentStore implements ContentStore {

    public static final String URI_COMPONENT_SEPARATOR = "/";

    private final DirectoryNode root = new DirectoryNode(URI_COMPONENT_SEPARATOR);

    @Override
    public synchronized void createResource(Path uri, boolean isCollection) throws DataAccessException {
        String name = uri.getName();
        DirectoryNode parent = getParent(uri);

        if (parent == null) {
            throw new DataAccessException("Parent node does not exist or is not a directory");
        }

        if (parent.entries.containsKey(name)) {
            Node node = (Node) parent.entries.get(name);
            if ((node instanceof DirectoryNode) ^ isCollection) {
                throw new DataAccessException("Cannot create resource, "
                        + (isCollection ? "a non-directory already exists" : "a directory already exists"));
            }
            return;
        }

        if (isCollection) {
            parent.entries.put(name, new DirectoryNode(name));
        } else {
            parent.entries.put(name, new ContentNode(name));
        }
    }

    @Override
    public long getContentLength(Path uri) throws DataAccessException, IllegalOperationException {
        Node node = getNode(uri);

        if (node == null) {
            throw new DataAccessException("No such resource: " + uri);
            // return 0; // Same behaviour as java.io.File#length()
        } else if (node instanceof DirectoryNode) {
            throw new IllegalOperationException("Length is undefined for directory nodes");
        } else {
            return ((ContentNode) node).content.length;
        }

    }

    @Override
    public void deleteResource(Path uri) {
        String name = uri.getName();
        DirectoryNode parent = getParent(uri);

        if (parent != null) {
            synchronized (this) {
                parent.entries.remove(name);
            }
        }
    }

    @Override
    public InputStream getInputStream(Path uri) throws DataAccessException {
        Node node = getNode(uri);

        if (node == null) {
            throw new DataAccessException("Node does not exist.");
        }

        if (node instanceof DirectoryNode) {
            throw new DataAccessException("Node is a directory.");
        }

        return new ByteArrayInputStream(((ContentNode) node).content);
    }

    @Override
    public void storeContent(Path uri, ContentInputSource content) throws DataAccessException {

        Node node = getNode(uri);

        if (node == null) {
            throw new DataAccessException("Node does not exist.");
        }

        if (node instanceof DirectoryNode) {
            throw new DataAccessException("Node is a directory.");
        }

        try {
            final InputStream inputStream = content.stream();
            byte[] buffer = new byte[5000];
            int n;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            while ((n = inputStream.read(buffer, 0, buffer.length)) != -1) {
                output.write(buffer, 0, n);
            }
            output.close();
            inputStream.close();

            ((ContentNode) node).content = output.toByteArray();
        } catch (IOException e) {
            throw new DataAccessException("Unable to store content", e);
        }
    }

    @Override
    public synchronized void copy(Path srcURI, Path destURI) throws DataAccessException {
        Node srcNode = getNode(srcURI);

        if (srcNode == null) {
            throw new DataAccessException("Cannot copy: source node does not exist.");
        }

        if (getNode(destURI) != null) {
            throw new DataAccessException("Cannot copy: destination node already exists.");
        }

        DirectoryNode parent = getParent(destURI);
        String destNodeName = destURI.getName();
        if (parent == null) {
            throw new DataAccessException("Cannot copy: destination parent node does not exist.");
        }

        Node copy;
        try {
            copy = (Node) srcNode.clone();
        } catch (CloneNotSupportedException ex) {
            throw new DataAccessException("internal error");
        }
        copy.name = destNodeName;
        parent.entries.put(destNodeName, copy);
    }

    @Override
    public synchronized void move(Path srcURI, Path destURI) throws DataAccessException {
        copy(srcURI, destURI);
        deleteResource(srcURI);
    }

    private DirectoryNode getParent(Path uri) {
        if (uri.isRoot()) {
            return null;
        }

        Node node = getNode(uri.getParent());
        if (!(node instanceof DirectoryNode))
            return null;

        return (DirectoryNode) node;
    }

    // Non-recursive search for node in tree
    private Node getNode(Path uri) {
        if (uri.isRoot())
            return this.root;

        String uriString = uri.toString();
        String[] components = uriString.substring(URI_COMPONENT_SEPARATOR.length(), uriString.length()).split(
                URI_COMPONENT_SEPARATOR);

        Node node = null;
        DirectoryNode dir = this.root;
        synchronized (this) {
            for (int i = 0; i < components.length; i++) {
                if ((node = (Node) dir.entries.get(components[i])) != null) {
                    if (node instanceof DirectoryNode) {
                        dir = (DirectoryNode) node;
                    } else if (i < components.length - 1) {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }
        return node;
    }

    /**
     * Print current content tree of the memory store instance. For debugging,
     * etc.
     * 
     * @param output
     *            The <code>PrintStream</code> to write the output to.
     */
    public synchronized void printContents(PrintStream output) {
        printContentsInternal(output, this.root, "");
    }

    /**
     * Internal recursive print-contents helper method.
     */
    private void printContentsInternal(PrintStream output, DirectoryNode node, String prefix) {
        output.println(prefix + node.name);
        prefix = node.name.equals(URI_COMPONENT_SEPARATOR) ? URI_COMPONENT_SEPARATOR : prefix + node.name
                + URI_COMPONENT_SEPARATOR;
        for (String key : node.entries.keySet()) {
            Node entry = (Node) node.entries.get(key);
            if (entry instanceof DirectoryNode) {
                printContentsInternal(output, (DirectoryNode) entry, prefix);
            } else {
                ContentNode n = (ContentNode) entry;
                output.println(prefix + entry.name + " (" + n.content.length + " bytes)");
            }
        }
    }

    // Central parts of the internal data structure
    private abstract class Node implements Cloneable {
        String name;

        public Node(String name) {
            this.name = name;
        }

        @Override
        public abstract Object clone() throws CloneNotSupportedException;
    }

    private class ContentNode extends Node {
        byte[] content;

        public ContentNode(String name) {
            super(name);
            this.content = new byte[0];
        }

        @Override
        public String toString() {
            return "ContentNode[" + super.name + "]";
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            ContentNode n = new ContentNode(this.name);
            n.content = new byte[this.content.length];
            System.arraycopy(this.content, 0, n.content, 0, this.content.length);
            return n;
        }
    }

    private class DirectoryNode extends Node {
        Map<String, Node> entries;

        public DirectoryNode(String name) {
            super(name);
            this.entries = new HashMap<String, Node>();
        }

        @Override
        public String toString() {
            return "DirectoryNode[" + super.name + "]";
        }

        // recursive clone of entire (sub)tree (for copying)
        @Override
        public Object clone() throws CloneNotSupportedException {
            DirectoryNode n = new DirectoryNode(this.name);
            n.entries = new HashMap<String, Node>(this.entries.size());
            for (String key : this.entries.keySet()) {
                Node child = this.entries.get(key);
                n.entries.put(key, (Node) child.clone());
            }
            return n;
        }
    }

    @Override
    public void moveToTrash(Path srcURI, final String trashIdDir) throws DataAccessException {
        throw new UnsupportedOperationException("Not implemented...");
    }

    @Override
    public void recover(Path destURI, RecoverableResource recoverableResource) throws DataAccessException {
        throw new UnsupportedOperationException("Not implemented...");
    }

    @Override
    public void deleteRecoverable(RecoverableResource recoverableResource) throws DataAccessException {
        throw new UnsupportedOperationException("Not implemented...");
    }
}
