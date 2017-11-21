/* Copyright (c) 2006, University of Oslo, Norway
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

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;



public class DataImportUtil implements InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(DataImportUtil.class);
    
    private Repository repository;
    private boolean skipExistingResources = false;

    public void afterPropertiesSet() {
        if (this.repository == null) {
            throw new BeanInitializationException(
                "JavaBean property 'repository' not specified");
        }
    }
    
    public void importFiles(File file, String token, String targetPath) throws Exception {
        if (!file.exists()) {
            throw new IOException("File " + file + " does not exist");
        }
        create(file, token, Path.fromString(targetPath));
    }
    
    private void create(File file, String token, Path uri) throws Exception {

        if (file.isDirectory()) {
            if (this.skipExistingResources && this.repository.exists(token, uri)) {
                logger.info("Skipping directory '" + file + "', resource already exists at URI '" 
                        + uri + "'");
            } else {
                logger.info("Importing directory '" + file + "' to URI '" + uri + "'");
                this.repository.createCollection(token, null, uri, AclMode.inherit());
            }

            // Recursively process children of directory
            File[] children = file.listFiles();
            for (int i = 0; i < children.length; i++) {
                Path childURI = uri.extend(children[i].getName());
                create(children[i], token, childURI);
            }
        }
        else {
            if (this.skipExistingResources && this.repository.exists(token, uri)) {
                logger.info("Skipping import of file '" + file + "', resource already exists at URI '"
                        + uri + "'");
                return;
            }
            
            logger.info("Importing file '" + file + "' to URI '" + uri + "'");
            this.repository.createDocument(token, null, uri, 
                    ContentInputSources.fromFile(file), AclMode.inherit());
        }
        
    }
    
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public void setSkipExistingResources(boolean skipExistingResources) {
        this.skipExistingResources = skipExistingResources;
    }
    
}
