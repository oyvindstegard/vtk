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

import java.io.InputStream;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.util.repository.ResourceArchiver;
import vtk.web.RequestContext;

public class ExpandArchiveAction implements CopyAction {

    private static Logger logger = LoggerFactory.getLogger(CreateArchiveAction.class);

    private Repository repository;
    private ResourceArchiver archiver;

    @Override
    public void process(HttpServletRequest request, Path uri,
            Path copyUri, Map<String, Object> properties) throws Exception {

        RequestContext requestContext = RequestContext.getRequestContext(request);
        String token = requestContext.getSecurityToken();

        logger.info("Expanding archive '{}' to '{}'", uri, copyUri);

        Resource resource = this.repository.retrieve(token, uri, false);
        if (resource.isCollection()) {
            throw new RuntimeException("Cannot unzip a collection");
        }
        InputStream source = this.repository.getInputStream(token, uri, false);
        this.archiver.expandArchive(token, source, copyUri, properties);

        logger.info("Done expanding archive '{}' to '{}'", uri, copyUri);

    }

    @Required
    public void setArchiver(ResourceArchiver archiver) {
        this.archiver = archiver;
    }

    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

}
