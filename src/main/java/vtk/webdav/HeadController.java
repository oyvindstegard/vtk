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
package vtk.webdav;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceLockedException;
import vtk.repository.ResourceNotFoundException;
import vtk.web.RequestContext;

/**
 * Handler for HEAD requests
 *
 */
public class HeadController extends AbstractWebdavController {

    /**
     * Performs the HTTP/WebDAV 'HEAD' method.
     */
    public void handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws IOException {
         
        RequestContext requestContext = RequestContext.getRequestContext(request);
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();
        Repository repository = requestContext.getRepository();

        try {
            Resource resource = repository.retrieve(token, uri, false);
            if (resource.isCollection()) {
                responseBuilder(HttpServletResponse.SC_NOT_FOUND)
                    .header("Content-Type", "text/plain;charset=utf-8")
                    .message("HEAD on collection is not supported")
                    .writeTo(response);
            }
            String contentType = resource.getContentType();
            if (resource.getCharacterEncoding() != null) {
                contentType = contentType + ";charset=" + resource.getCharacterEncoding();
            }
            responseBuilder(HttpServletResponse.SC_OK)
                .header("Content-Type", contentType)
                .header("Content-Length", String.valueOf(resource.getContentLength()))
                .writeTo(response);
        }
        catch (ResourceNotFoundException e) {
            responseBuilder(HttpServletResponse.SC_NOT_FOUND)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message(e.getMessage())
                .writeTo(response);
        }
        catch (ResourceLockedException e) {
            responseBuilder(HttpStatus.LOCKED.value())
                .header("Content-Type", "text/plain;charset=utf-8")
                .message(e.getMessage())
                .writeTo(response);
        }
    }
}
