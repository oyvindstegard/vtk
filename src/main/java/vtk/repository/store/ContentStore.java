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
package vtk.repository.store;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

import vtk.repository.Path;
import vtk.repository.RecoverableResource;
import vtk.util.io.IO;


/**
 * Defines a pure content store. It is organized hierarchically and 
 * resource nodes are addressed by their URIs. The behaviour shall be equal
 * to that of a common file system. Every node must have an 
 * existing parent collection resource node, except the root node. 
 * The root collection node shall always exist, and should not need to be 
 * created upon initialization.
 * 
 */
public interface ContentStore {

    /**
     * Create a new empty node in content store. Node may be either
     * content/file or a collection.
     * @param uri path to new resource
     * @param isCollection if <code>true</code> a collection node is created,
     *        otherwise an empty content node.
     * @throws DataAccessException 
     */
    public void createResource(Path uri, boolean isCollection)
            throws DataAccessException;

    /**
     * Get length of file node at path, in number of bytes.
     * @param uri path to a file node.
     * @return length of file in number of bytes
     * @throws DataAccessException if I/O-error or path is directory node.
     */
    public long getContentLength(Path uri) throws DataAccessException;

    /**
     * Delete node at path. If the path points to a directory node, then
     * the entire tree shall be deleted.
     * @param uri path to node
     * @throws DataAccessException if I/O-error
     */
    public void deleteResource(Path uri) throws DataAccessException;

    /**
     * Get an input stream for file node at path.
     * @param uri
     * @return
     * @throws DataAccessException 
     */
    public InputStream getInputStream(Path uri) throws DataAccessException;

    /**
     * Store content in the resource given by the URI.
     * The supplied <code>InputStream</code> should be closed by this
     * method, after it has been read.
     * 
     * @param uri path to where content should be stored
     * @param content input stream of content to store
     * @throws DataAccessException in case of errors.
     */
    public void storeContent(Path uri, InputStream content) throws DataAccessException;
    
    public default void storeContent(Path uri, InputStream content, Consumer<Long> progress, int progressInterval) throws DataAccessException {
        storeContent(uri, content);
        if (progress != null) {
            // Minimum requirement is at least one call to progress callback.
            // Content store impls should generally override this method and provide proper progress feedback
            progress.accept(getContentLength(uri));
        }
    }
    
    public void copy(Path srcURI, Path destURI) throws DataAccessException;
    
    public void move(Path srcURI, Path destURI) throws DataAccessException;
    
    public void moveToTrash(Path srcURI, final String trashIdDir) throws DataAccessException;
    
    public void recover(Path destURI, RecoverableResource recoverableResource) throws DataAccessException;
    
    public void deleteRecoverable(RecoverableResource recoverableResource) throws DataAccessException;

}
