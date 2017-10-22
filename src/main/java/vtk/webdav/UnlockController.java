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
import vtk.web.InvalidRequestException;
import vtk.web.RequestContext;

/**
 * Handler for UNLOCK requests
 *
 */
public class UnlockController extends AbstractWebdavController {

    public void handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws IOException {
         
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();
        try {
            Resource resource = repository.retrieve(token, uri, false);
            String lockToken = getLockToken(request);
            if (resource.getLock() != null && !resource.getLock().getLockToken().equals(lockToken)) {
                responseBuilder(HttpServletResponse.SC_PRECONDITION_FAILED).writeTo(response);
            }
            else {
                repository.unlock(token, uri, lockToken);
                responseBuilder(HttpServletResponse.SC_OK).writeTo(response);
            }
        }
        catch (InvalidRequestException e) {
            responseBuilder(HttpServletResponse.SC_BAD_REQUEST).writeTo(response);
        }
        catch (ResourceNotFoundException e) {
            responseBuilder(HttpServletResponse.SC_NOT_FOUND).writeTo(response);
        }
        catch (ResourceLockedException e) {
            responseBuilder(HttpStatus.LOCKED.value()).writeTo(response);
        }
    }
   
    protected String getLockToken(HttpServletRequest request)
        throws InvalidRequestException {

        String header = request.getHeader("Lock-Token");
        // Lock-Token Header as defined in RFC 2518:
        // Lock-Token = "Lock-Token" ":" Coded-URL
        // Coded-URL = "<" absoluteURI ">"
        
        String lockToken = header;

        if (lockToken == null || lockToken.trim().equals("")) {
            throw new InvalidRequestException("Invalid Lock-Token header: \""
                                              + header + "\"");
        }
        if (! (lockToken.startsWith("<") && lockToken.endsWith(">"))) {
            //throw new InvalidRequestException("Invalid Lock-Token header: \""
            //                                  + header + "\"");

            // Some clients omit "<" and ">" from their Lock-Token
            // headers, we allow that behaviour here:
            lockToken = "<" + lockToken + ">";
        }

        // Trim "<" and ">"
        lockToken = lockToken.substring(1);
        lockToken = lockToken.substring(0, lockToken.length() - 1);

        return lockToken;
    }
}
