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

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.ResourceNotFoundException;
import vtk.web.RequestContext;

/**
 * Handler for OPTIONS requests
 * 
 */
public class OptionsController extends AbstractWebdavController {

    /*
     * Write the following headers: DAV: 1, 2 Allow: GET, HEAD, POST, PUT,
     * DELETE, CONNECT, OPTIONS, PATCH, PROPFIND, PROPPATCH, MKCOL, COPY, MOVE,
     * LOCK, UNLOCK, TRACE
     */
    public void handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        RequestContext requestContext = RequestContext.getRequestContext(request);
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();

        // Note: '*' as request URI for OPTIONS requests should be translated to '/'
        // before we get here.
        try {
            repository.retrieve(token, uri, false);
            
            String optionsHeader = "1, 2";
            String allowHeader = new StringBuilder("GET, HEAD, POST, PUT, DELETE, CONNECT, OPTIONS, ")
                .append("PATCH, PROPFIND, PROPPATCH, MKCOL, COPY, MOVE, ")
                .append("LOCK, UNLOCK, TRACE").toString();
            
            responseBuilder(HttpServletResponse.SC_OK)
                .header("Allow", allowHeader)
                .header("MS-Author-Via", "DAV")
                .header("DAV", optionsHeader)
                .writeTo(response);
            
        }
        catch (ResourceNotFoundException e) {
            responseBuilder(HttpServletResponse.SC_NOT_FOUND)
                .header("Content-Type", "text/plain;charset=utf-8")
                .message("Not found: " + uri)
                .writeTo(response);
        }
    }

}
