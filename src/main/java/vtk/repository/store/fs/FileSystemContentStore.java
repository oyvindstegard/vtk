/* Copyright (c) 2006, 2007, 2011 University of Oslo, Norway
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
package vtk.repository.store.fs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import vtk.repository.IllegalOperationException;
import vtk.repository.Path;
import vtk.repository.RecoverableResource;
import vtk.repository.store.ContentStore;
import vtk.repository.store.DataAccessException;
import vtk.util.io.IO;
import vtk.util.io.IO.Copy;
import vtk.util.io.StreamUtil;
import vtk.web.service.URL;

/**
 * File system content store implementation operating directly on the repository
 * data.
 */
public class FileSystemContentStore implements InitializingBean, ContentStore {

    private static Logger logger = LoggerFactory.getLogger(FileSystemContentStore.class);

    private static final int COPY_BUF_SIZE = 122880;
    
    private String repositoryDataDirectory;
    private String repositoryTrashCanDirectory;

    private boolean urlEncodeFileNames = false;

    @Override
    public void afterPropertiesSet() throws Exception {
        createRootDirectory(this.repositoryDataDirectory);
        createRootDirectory(this.repositoryTrashCanDirectory);
    }

    @Override
    public void createResource(Path uri, boolean collection) throws DataAccessException {

        String fileName = getLocalFilename(uri);

        try {
            if (collection) {
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
        } catch (IOException e) {
            throw new DataAccessException("Create resource [" + uri + "] failed", e);
        }
    }

    @Override
    public long getContentLength(Path uri) throws DataAccessException {
        String fileName = getLocalFilename(uri);

        try {
            File f = new File(fileName);
            if (!f.exists()) {
                throw new DataAccessException("No file exists for URI " + uri + " at " + f.getCanonicalPath());
            }
            if (f.isFile()) {
                return f.length();
            }
            throw new IllegalOperationException("Length is undefined for collections");
        } catch (IOException e) {
            throw new DataAccessException("Get content length [" + uri + "] failed", e);
        }
    }

    @Override
    public void deleteResource(Path uri) {
        // Don't delete root
        if (!uri.isRoot()) {
            String fileName = getLocalFilename(uri);
            deleteFiles(new File(fileName));
        }
    }

    private void deleteFiles(File f) {
        if (!f.isDirectory()) {
            f.delete();
            return;
        }

        for (File child : f.listFiles()) {
            deleteFiles(child);
        }

        f.delete();
    }

    @Override
    public InputStream getInputStream(Path uri) throws DataAccessException {
        String fileName = getLocalFilename(uri);

        try {
            return new FileInputStream(new File(fileName));
        } catch (IOException e) {
            throw new DataAccessException("Get input stream [" + uri + "] failed", e);
        }
    }

    @Override
    public void storeContent(Path uri, InputStream content) throws DataAccessException {
        storeContent(uri, content, null, -1);
    }

    @Override
    public void storeContent(Path uri, InputStream content, Consumer<Long> progressCallback, int progressInterval) throws DataAccessException {
        String fileName = getLocalFilename(uri);
        File dest = new File(fileName);
        try {
            FileOutputStream outFileStream = new FileOutputStream(dest);
            Copy<InputStream,OutputStream> copyOp = IO.copy(content, outFileStream);
            if (progressCallback != null) {
                copyOp.progress(progressCallback).progressInterval(progressInterval);
            }
            copyOp.perform();
        } catch (IOException e) {
            throw new DataAccessException("Store content [" + uri + "] failed", e);
        }
    }

    @Override
    public void copy(Path srcURI, Path destURI) throws DataAccessException {
        String fileNameFrom = getLocalFilename(srcURI);
        String fileNameTo = getLocalFilename(destURI);
        try {
            File fromDir = new File(fileNameFrom);
            if (fromDir.isDirectory()) {
                copyDir(fromDir, new File(fileNameTo));
            } else {
                copyFile(fromDir, new File(fileNameTo));
            }
        } catch (IOException e) {
            throw new DataAccessException("Store content [" + fileNameFrom + ", " + fileNameTo + "] failed", e);
        }
    }

    private void copyDir(File fromDir, File toDir) throws IOException {

        toDir.mkdir();

        File[] children = fromDir.listFiles();
        for (File child : children) {
            File newFile = new File(toDir.getCanonicalPath() + File.separator + child.getName());
            if (child.isFile()) {
                copyFile(child, newFile);
            } else {
                copyDir(child, newFile);
            }
        }
    }

    private void copyFile(File from, File to) throws IOException {
        FileInputStream src = null;
        FileOutputStream dst = null;
        try {
            src = new FileInputStream(from);
            dst = new FileOutputStream(to);
            StreamUtil.fileStreamCopy(src, dst, false);
        } finally {
            try {
                if (src != null) {
                    src.close();
                }
            } finally {
                if (dst != null) {
                    dst.close();
                }
            }
        }
    }

    @Override
    public void move(Path srcURI, Path destURI) throws DataAccessException {
        String fileNameFrom = getLocalFilename(srcURI);
        String fileNameTo = getLocalFilename(destURI);
        if (!new File(fileNameFrom).renameTo(new File(fileNameTo))) {
            throw new DataAccessException("Unable to rename file " + fileNameFrom + " to " + fileNameTo);
        }
    }

    @Override
    public void moveToTrash(Path srcURI, final String trashIdDir) throws DataAccessException {

        String from = getLocalFilename(srcURI);
        File src = new File(from);

        String trashCanDir = this.repositoryTrashCanDirectory + "/" + trashIdDir;
        File trashDir = new File(trashCanDir);
        trashDir.mkdir();

        Path path = this.getEncodedPathIfConfigured(srcURI);
        File dest = new File(trashCanDir + "/" + path.getName());

        if (!src.renameTo(dest)) {
            throw new DataAccessException("Unable to move " + from + " to trash can");
        }
    }

    @Override
    public void recover(Path destURI, RecoverableResource recoverableResource) throws DataAccessException {
        String dest = this.getLocalFilename(destURI);
        String recover = dest + "/" + recoverableResource.getName();
        String trashPath = this.repositoryTrashCanDirectory + "/" + recoverableResource.getTrashUri();
        if (!new File(trashPath).renameTo(new File(recover))) {
            throw new DataAccessException("Unable to recover file " + recoverableResource.getTrashUri());
        }
        File trashDir = new File(this.repositoryTrashCanDirectory + "/" + recoverableResource.getTrashID());
        trashDir.delete();
    }

    @Override
    public void deleteRecoverable(RecoverableResource recoverableResource) throws DataAccessException {
        String filePath = this.repositoryTrashCanDirectory + "/" + recoverableResource.getTrashID();
        this.deleteFiles(new File(filePath));
    }
    

    private String getLocalFilename(Path uri) {
        Path path = this.getEncodedPathIfConfigured(uri);
        return this.repositoryDataDirectory + path.toString();
    }

    private Path getEncodedPathIfConfigured(Path original) {
        if (this.urlEncodeFileNames) {
            return URL.encode(original);
        }
        return original;
    }
    
    @Required
    public void setRepositoryDataDirectory(String repositoryDataDirectory) {
        this.repositoryDataDirectory = repositoryDataDirectory;
    }

    @Required
    public void setRepositoryTrashCanDirectory(String repositoryTrashCanDirectory) {
        this.repositoryTrashCanDirectory = repositoryTrashCanDirectory;
    }

    public void setUrlEncodeFileNames(boolean urlEncodeFileNames) {
        this.urlEncodeFileNames = urlEncodeFileNames;
    }

    private void createRootDirectory(String directoryPath) {
        File root = new File(directoryPath);

        if (!root.isAbsolute()) {
            directoryPath = System.getProperty("vortex.home") + File.separator + directoryPath;
            root = new File(directoryPath);
        }

        if (!root.exists()) {
            root.mkdir();
        }
    }

}
