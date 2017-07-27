/* Copyright (c) 2017, University of Oslo, Norway
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
package vtk.repository;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vtk.util.ValidationException;
import vtk.util.io.IO;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class FileUpload {
    private static final Logger logger = LoggerFactory.getLogger(FileUpload.class);
    private final Repository repository;
    private final File tempDir;
    private final Map<String, String> replaceNameChars;
    private final boolean downcaseNames;

    public FileUpload(Repository repository) {
        this(
                repository,
                System.getProperty("java.io.tmpdir"),
                null,
                false
        );
    }

    public FileUpload(
            Repository repository,
            String tempDirPath,
            Map<String, String> replaceNameChars,
            boolean downcaseNames
    ) {
        this.repository = repository;
        this.replaceNameChars = replaceNameChars;
        this.downcaseNames = downcaseNames;
        File tmp = new File(tempDirPath);
        if (!tmp.exists()) {
            throw new IllegalArgumentException(
                    "Unable to set tempDir: file " + tmp + " does not exist"
            );
        }
        if (!tmp.isDirectory()) {
            throw new IllegalArgumentException(
                    "Unable to set tempDir: file " + tmp + " is not a directory"
            );
        }
        this.tempDir = tmp;
    }

    public void upload(
            String token,
            FileItemIterator fileIterator,
            Path destination,
            boolean shouldOverwriteExisting
    ) {
        Map<Path, IO.TempFile> fileMap = new LinkedHashMap<>();
        try {
            while (fileIterator.hasNext()) {
                FileItemStream uploadItem = fileIterator.next();
                if (!uploadItem.isFormField()) {
                    String name = this.fixFileName(uploadItem.getName());
                    Path itemPath = destination.extend(name);
                    if (!shouldOverwriteExisting && this.fileExists(token, itemPath)) {
                        throw new ValidationException(
                                "manage.upload.resource.exists", "A resource of this name already exists"
                        );
                    }
                    IO.TempFile tmpFile = IO.tempFile(uploadItem.openStream(), tempDir).perform();
                    fileMap.put(itemPath, tmpFile);
                }
            }

            // Write files
            for (Map.Entry<Path, IO.TempFile> entry : fileMap.entrySet()) {
                Path path = entry.getKey();
                IO.TempFile tempFile = entry.getValue();
                if (logger.isDebugEnabled()) {
                    logger.debug("Uploaded resource will be: " + path);
                }

                if (this.repository.exists(token, path)) {
                    this.repository.delete(token, null, path, false);
                }
                this.repository.createDocument(
                        token, null, path, ContentInputSources.fromFile(tempFile.file(), true)
                );
            }
        }
        catch (ValidationException e) {
            throw e;
        }
        catch (Exception e) {
            logger.warn("Caught exception while performing file upload", e);
            throw new ValidationException("manage.upload.error",
                    "An unexpected error occurred while processing file upload");
        } finally {
            // Always clean up temp files
            for (IO.TempFile t : fileMap.values()) {
                t.delete();
            }
        }
    }

    public String fixFileName(String filename) {
        String fixedName = stripWindowsPath(filename);

        if (fixedName == null || fixedName.trim().equals("")) {
            throw new ValidationException(
                    "manage.upload.resource.name-problem", "A resource has an illegal name"
            );
        }

        if (this.downcaseNames) {
            fixedName = fixedName.toLowerCase();
        }
        if (this.replaceNameChars != null) {
            for (String regex : this.replaceNameChars.keySet()) {
                String replacement = this.replaceNameChars.get(regex);
                fixedName = fixedName.replaceAll(regex, replacement);
            }
        }

        if (fixedName.equals("")) {
            throw new ValidationException(
                    "manage.upload.resource.name-problem", "A resource has an illegal name"
            );
        }

        return fixedName;
    }

    public boolean fileExists(String token, Path file) throws IOException {
        return this.repository.exists(token, file);
    }

    /**
     * Attempts to extract only the file name from a Windows style pathname, by
     * stripping away everything up to and including the last backslash in the
     * path.
     */
    private static String stripWindowsPath(String fileName) {

        if (fileName == null || fileName.trim().equals("")) {
            return null;
        }

        int pos = fileName.lastIndexOf("\\");

        if (pos > fileName.length() - 2) {
            return fileName;
        }
        else if (pos >= 0) {
            return fileName.substring(pos + 1, fileName.length());
        }

        return fileName;
    }

}
