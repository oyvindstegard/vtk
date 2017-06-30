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
package vtk.web;

import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.HttpRequestHandler;

import vtk.repository.Path;
import vtk.security.AuthenticationException;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class FileBrowserController implements HttpRequestHandler {
    private static final String FCK_URI_TEMPLATE = "%s/plugins/filemanager/browser/default/browser.html?basefolder=%s&connector=%s";
    private final String editorBase;
    private Path fckConnectorBase;
    private Service fckConnectorService;

    public FileBrowserController(Service fckConnector,
            String fckConnectorBase, String editorBase) {
        this.editorBase = editorBase;
        this.fckConnectorBase = Path.fromString(fckConnectorBase);
        this.fckConnectorService = fckConnector;
    }

    @Override
    public void handleRequest(
            HttpServletRequest request, HttpServletResponse response
    ) throws ServletException, IOException {
        RequestContext rc = RequestContext.getRequestContext();
        if (rc.getPrincipal() == null) {
            throw new AuthenticationException();
        }
        
        String connectorURL = URL.encode(fckConnectorService.urlConstructor(rc.getRequestURL())
                .withURI(fckConnectorBase)
                .constructURL()
                .getPathRepresentation());
        String redirectURL = String.format(
                FCK_URI_TEMPLATE, editorBase, rc.getResourceURI(), connectorURL
        );

        Enumeration<String> keys = request.getParameterNames();
        StringBuilder queryString = new StringBuilder();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            if ("vrtx".equals(key)) continue;
            queryString.append('&');
            queryString.append(key);
            queryString.append('=');
            queryString.append(request.getParameter(key));
        }
        if (queryString.length() > 1) {
            redirectURL += queryString.toString();
        }

        response.sendRedirect(redirectURL);
    }
}
