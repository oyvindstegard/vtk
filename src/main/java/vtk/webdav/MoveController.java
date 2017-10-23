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

import vtk.repository.FailedDependencyException;
import vtk.repository.IllegalOperationException;
import vtk.repository.Path;
import vtk.repository.ReadOnlyException;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceLockedException;
import vtk.repository.ResourceNotFoundException;
import vtk.repository.ResourceOverwriteException;
import vtk.web.InvalidRequestException;
import vtk.web.RequestContext;

/**
 * Handler for MOVE requests
 *
 */
public class MoveController extends AbstractWebdavController {
    
    private String trustedToken = null;


    /**
     * Performs the WebDAV 'MOVE' method.      
     */
    public void handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        RequestContext requestContext = RequestContext.getRequestContext(request);
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();
        String destHeader = request.getHeader("Destination");
    
        try {
            Resource resource = repository.retrieve(trustedToken, uri, false);
            
            if (destHeader == null || destHeader.trim().equals("")) {
                throw new InvalidRequestException(
                    "Missing `Destination' request header");
            }
            Path destURI = mapToResourceURI(destHeader);
            String depth = request.getHeader("Depth");
            if (depth == null) {
                depth = "infinity";
            }
         
            boolean overwrite = true;
            String overwriteHeader = request.getHeader("Overwrite");
            if (overwriteHeader != null && overwriteHeader.equals("F")) {
                overwrite = false;
            } 

            boolean existed = repository.exists(token, destURI);
            
            if (existed) {
                Resource destination = repository.retrieve(token, destURI, false);
            }
            repository.move(token, null, uri, destURI, overwrite);

            int status = existed ? HttpServletResponse.SC_NO_CONTENT 
                    : HttpServletResponse.SC_CREATED;
            
            responseBuilder(status)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message("Move: from " + uri + " to " + destURI + " succeeded")
                .writeTo(response);
        }
        catch (InvalidRequestException e) {
            responseBuilder(HttpServletResponse.SC_BAD_REQUEST)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message(e.getMessage())
                .writeTo(response);
        }
        catch (IllegalOperationException | ReadOnlyException e) {
            responseBuilder(HttpServletResponse.SC_FORBIDDEN)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message(e.getMessage())
                .writeTo(response);
        }
        catch (FailedDependencyException | ResourceOverwriteException e) {
            responseBuilder(HttpServletResponse.SC_PRECONDITION_FAILED)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message(e.getMessage())
                .writeTo(response);

        } catch (ResourceLockedException e) {
            responseBuilder(HttpStatus.LOCKED.value())
                .header("Content-Type", "text/plain;charset=utf-8")
                .message(e.getMessage())
                .writeTo(response);
        }
        catch (ResourceNotFoundException e) {
            responseBuilder(HttpServletResponse.SC_NOT_FOUND)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message(e.getMessage())
                .writeTo(response);
        }
    }
   
    public void setTrustedToken(String trustedToken) {
        this.trustedToken = trustedToken;
    }

}
