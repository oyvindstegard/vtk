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

import vtk.repository.IllegalOperationException;
import vtk.repository.Path;
import vtk.repository.ReadOnlyException;
import vtk.repository.Repository;
import vtk.repository.ResourceLockedException;
import vtk.util.web.HttpUtil;
import vtk.web.RequestContext;

/**
 * Handler for MKCOL requests
 *
 */
public class MkColController extends AbstractWebdavController {


    /**
     * Performs the WebDAV 'MKCOL' method.
     * @throws IOException 
     */
    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
         
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();

        try {
            String contentLength = request.getHeader("Content-Length");
            if (contentLength != null && !contentLength.equals("0")) {
                responseBuilder(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE)
                    .header("Content-Type", "text/plain;charset=utf-8")
                    .message("Header 'Content-Length' other than 0 not supported")
                    .writeTo(response);
                return;
            }

            if (repository.exists(token, uri)) {
                responseBuilder(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                    .header("Content-Type", "text/plain;charset=utf-8")
                    .message("Resource " + uri + " already exists")
                    .writeTo(response);
            }
         
            if (!allowedResourceName(uri)) {
                responseBuilder(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                    .header("Content-Type", "text/plain;charset=utf-8")
                    .message("Rejected: " + uri)
                    .writeTo(response);
            }
            repository.createCollection(token, uri);
            
            responseBuilder(HttpServletResponse.SC_CREATED)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message("Created: " + uri)
                .writeTo(response);

        }
        catch (IllegalOperationException e) {
            responseBuilder(HttpServletResponse.SC_BAD_REQUEST)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message("Illegal operation: " + uri)
                .writeTo(response);
        }
        catch (ReadOnlyException e) {
            responseBuilder(HttpServletResponse.SC_FORBIDDEN)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message("Read-only: " + uri)
                .writeTo(response);

        }
        catch (ResourceLockedException e) {
            responseBuilder(HttpUtil.SC_LOCKED)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message("Locked: " + uri)
                .writeTo(response);
        }
    }
   
   
}
