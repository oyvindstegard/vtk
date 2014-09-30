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
package vtk.web.actions.convert;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.security.SecurityContext;
import vtk.util.repository.ResourceArchiver;

public class CreateArchiveAction implements CopyAction {

    private static Log logger = LogFactory.getLog(CreateArchiveAction.class);

    private Repository repository;
    private File tempDir = new File(System.getProperty("java.io.tmpdir"));
    private ResourceArchiver archiver;

    public void process(Path uri, Path copyUri, Map<String, Object> properties) throws Exception {

        SecurityContext securityContext = SecurityContext.getSecurityContext();
        String token = securityContext.getToken();

        Resource resource = this.repository.retrieve(token, uri, false);
        if (!resource.isCollection()) {
            throw new RuntimeException("Cannot archive a single Resource, must be a collection");
        }
        File outFile = File.createTempFile("vrtx-archive", "jar", this.tempDir);
        FileOutputStream out = new FileOutputStream(outFile);
        BufferedOutputStream bo = new BufferedOutputStream(out);
        try {
            this.archiver.createArchive(token, resource, bo, properties);
            logger.info("Storing archive contents to '" + copyUri + "'");
            InputStream in = new FileInputStream(outFile);
            Resource dest = this.repository.createDocument(token, copyUri, in);
            if (!dest.isReadRestricted()) {
                logger.warn("The destination '" + copyUri + "' is open for access to all!");
            }
            // this.repository.storeContent(token, dest.getURI(), in);
            logger.info("Done storing archive to '" + copyUri + "'");

        } finally {
            outFile.delete();
        }
    }

    @Required
    public void setArchiver(ResourceArchiver archiver) {
        this.archiver = archiver;
    }

    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public void setTempDir(File tempDir) {
        this.tempDir = tempDir;
    }

}
