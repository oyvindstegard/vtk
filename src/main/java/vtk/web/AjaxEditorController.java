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

import org.json.simple.JSONObject;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.util.*;

import org.springframework.web.servlet.support.RequestContextUtils;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.resourcemanagement.StaticResourceResolver;
import vtk.security.Principal;
import vtk.text.tl.*;
import vtk.text.tl.expr.Expression;
import vtk.util.io.IO;

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
        Principal p = rc.getPrincipal();

        Resource resource = repository.lock(
                token,
                rc.getResourceURI(),
                p.getQualifiedName(),
                Repository.Depth.ZERO,
                600,
                null
        );
        String type = resource.getResourceType();

        Map<String, Object> model = new HashMap<>();
        model.put("url", rc.getRequestURL());
        model.put("editorJsURI", getStaticResourcePath(type, "editor.js"));
        model.put("editorCssURI", getStaticResourcePath(type, "editor.css"));
        model.put("contentType", JSONObject.escape(resource.getContentType()));
        model.put("contentLanguage", JSONObject.escape(resource.getContentLanguage()));
        model.put("resourceName", resource.getName());
        model.put("resourceContent", IO.readString(
                repository.getInputStream(token, rc.getResourceURI(), false)
        ).perform());
        
        model.put("username", p.getName());
        model.put("name", p.getDescription());
        model.put("url", p.getURL());
        
        Optional<ReaderWithPath> template = getTemplateReader(type, "editor.tl");
        if (template.isPresent()) {
            ReaderWithPath reader = template.get();
            model.put("staticURL", reader.path.getParent());
            this.renderTemplate(template.get(), model, httpServletRequest, httpServletResponse);
            return null;
        }
        model.put("staticURL", this.staticResourcesURL);
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

    private Optional<ReaderWithPath> getTemplateReader(
            String type, String filename
    ) throws Exception {
        RequestContext rc = RequestContext.getRequestContext();
        Path repositoryPath = Path.fromString(this.appPath + "/" + type + "/" + filename);
        org.springframework.core.io.Resource systemPath = this.staticResourceResolver.resolve(
                Path.fromString(this.staticResourcesURL + "/" + type + "/" + filename)
        );

        if (rc.getRepository().exists(rc.getSecurityToken(), repositoryPath)) {
            return Optional.of(new ReaderWithPath(
                    new InputStreamReader(rc.getRepository().getInputStream(
                        rc.getSecurityToken(), repositoryPath, false
                    )),
                    Path.fromString(this.appResourceURL + "/" + type + "/" + filename)
            ));
        } else if (systemPath != null && systemPath.exists()) {
            return Optional.of(new ReaderWithPath(
                    new InputStreamReader(systemPath.getInputStream()),
                    Path.fromString(this.staticResourcesURL + "/" + type + "/" + filename)
            ));
        }
        return Optional.empty();
    }

    private static class ReaderWithPath {
        public final Reader reader;
        public final Path path;

        private ReaderWithPath(Reader reader, Path path) {
            this.reader = reader;
            this.path = path;
        }
    }

    private void renderTemplate(
            ReaderWithPath readerWithPath,
            Map<String, Object> model,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws Exception {
        Expression.FunctionResolver functionResolver = new Expression.FunctionResolver();
        List<DirectiveHandler> handlers = Arrays.asList(
                new IfHandler(functionResolver),
                new ValHandler(null, functionResolver)
        );
        new TemplateParser(readerWithPath.reader, handlers, new TemplateHandler() {
            @Override
            public void success(NodeList nodeList) {
                Locale locale = RequestContextUtils.getLocale(request);
                Context ctx = new Context(locale);

                for (String key: model.keySet()) {
                    ctx.define(key, model.get(key), true);
                }

                response.setContentType("text/html;charset=utf-8");
                try {
                    nodeList.render(ctx, response.getWriter());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void error(String message, int line) {
                Locale locale = RequestContextUtils.getLocale(request);
                Context ctx = new Context(locale);
                NodeList nodeList = new NodeList();
                nodeList.add(new Node() {
                    public boolean render(Context ctx, Writer out) throws Exception {
                        out.write(String.format(
                                "Template parse error:\n---\n%s\n---\nLine %d in file \"%s\"",
                                message, line, readerWithPath.path
                        ));
                        return true;
                    }
                });
                response.setContentType("text/plain;charset=utf-8");
                try {
                    nodeList.render(ctx, response.getWriter());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }).parse();
    }

}
