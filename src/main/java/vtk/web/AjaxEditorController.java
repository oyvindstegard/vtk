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

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.resourcemanagement.StaticResourceResolver;
import vtk.util.io.IO;
import vtk.web.service.URL;

public class AjaxEditorController implements Controller {

    private final View editView;
    private final StaticResourceResolver staticResourceResolver;
    private final String appResourceURL;
    private final String appPath;
    private final String staticResourcesURL;

    public AjaxEditorController(
            View editView,
            StaticResourceResolver staticResourceResolver,
            String appResourceURL,
            String appPath,
            String staticResourcesURL
    ) {
        this.editView = editView;
        this.staticResourceResolver = staticResourceResolver;
        this.appResourceURL = appResourceURL;
        this.appPath = appPath;
        this.staticResourcesURL = staticResourcesURL;
    }

    @Override
    public ModelAndView handleRequest(
            HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse
    ) throws Exception {
        RequestContext rc = RequestContext.getRequestContext();
        Repository repository = rc.getRepository();
        String token = rc.getSecurityToken();

        Resource resource = repository.retrieve(token, rc.getResourceURI(), false);
        String type = resource.getResourceType();

        Map<String, Object> model = new HashMap<>();
        model.put("url", rc.getRequestURL());
        model.put("editorJsURI", getStaticResourcePath(type, "editor.js"));
        model.put("editorCssURI", getStaticResourcePath(type, "editor.css"));
        model.put("resource", resource);
        model.put("resourceContent", IO.readString(
                repository.getInputStream(token, rc.getResourceURI(), false)
        ).perform());
        return new ModelAndView(editView, model);
    }

    private Path getStaticResourcePath(String type, String filename) throws Exception {
        RequestContext rc = RequestContext.getRequestContext();
        Path repositoryPath = Path.fromString(this.appPath + "/" + type + "/" + filename);
        org.springframework.core.io.Resource systemPath = this.staticResourceResolver.resolve(
                Path.fromString(this.staticResourcesURL + "/" + type + "/" + filename)
        );

        Path path;
        if (rc.getRepository().exists(rc.getSecurityToken(), repositoryPath)) {
            path = Path.fromString(this.appResourceURL + "/" + type + "/" + filename);
        } else if (systemPath != null && systemPath.exists()) {
            path = Path.fromString(this.staticResourcesURL + "/" + type + "/" + filename);
        } else {
            path = Path.fromString(this.staticResourcesURL + "/" + filename);
        }
        return path;
    }

}
