/* Copyright (c) 2009, University of Oslo, Norway
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
package vtk.resourcemanagement.view;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.Controller;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.Revision;
import vtk.resourcemanagement.ComponentDefinition;
import vtk.resourcemanagement.StructuredResource;
import vtk.resourcemanagement.StructuredResourceDescription;
import vtk.resourcemanagement.StructuredResourceManager;
import vtk.resourcemanagement.view.tl.ComponentInvokerNodeFactory;
import vtk.text.html.HtmlPage;
import vtk.text.html.HtmlPageFilter;
import vtk.text.html.HtmlPageParser;
import vtk.text.tl.Context;
import vtk.text.tl.DirectiveHandler;
import vtk.web.RequestContext;
import vtk.web.decorating.ComponentResolver;
import vtk.web.decorating.Template;
import vtk.web.decorating.TemplateEnvironment;
import vtk.web.decorating.TemplateManager;
import vtk.web.referencedata.ReferenceDataProvider;

public class StructuredResourceDisplayController implements Controller, InitializingBean {

    public static final String MVC_MODEL_CTX_ATTR = "__mvc_model__";
    public static final String COMPONENT_RESOLVER = "__component_resolver__";

    private static final String COMPONENT_NS = "comp";

    private StructuredResourceManager resourceManager;
    private TemplateManager templateManager;
    private HtmlPageParser htmlParser;
    private String resourceModelKey;
    private List<ReferenceDataProvider> configProviders;

    private List<DirectiveHandler> directiveHandlers;

    private List<HtmlPageFilter> postFilters;
    
    private ComponentResolver componentResolver;

    // XXX: clean up this mess:
    private Map<StructuredResourceDescription,
    Map<String, TemplateLanguageDecoratorComponent>> components = 
        new ConcurrentHashMap<StructuredResourceDescription, Map<String, TemplateLanguageDecoratorComponent>>();

    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        RequestContext requestContext = RequestContext.getRequestContext();
        Path uri = requestContext.getResourceURI();
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();
        String revisionParam = request.getParameter("revision");
        Revision revision = null;
        if (revisionParam != null) {
            for (Revision rev: repository.getRevisions(token, uri)) {
                if (rev.getName().equals(revisionParam)) {
                    revision = rev;
                    break;
                }
            }
        }
        Resource r;
        if (revision != null) {
            r = repository.retrieve(token, uri, true, revision);
        } else {
            r = repository.retrieve(token, uri, true);
        }

        InputStream stream;
        if (revision != null) {
            stream = repository.getInputStream(token, uri, true, revision);
        } else {
            stream = repository.getInputStream(token, uri, true);
        }

        StructuredResourceDescription desc = this.resourceManager.get(r.getResourceType());
        if (desc == null) {
            throw new IllegalStateException("Unable to find resource type description '" 
                    + r.getResourceType() + "' for resource " + r.getURI());
        }
        if (!desc.getComponentDefinitions().isEmpty() && !this.components.containsKey(desc)) {
            initComponentDefs(desc);
        }

        Map<String, Object> model = new HashMap<String, Object>();
        StructuredResource res = desc.buildResource(stream);
        model.put("structured-resource", res);
        model.put("resource", r);
        model.put(this.resourceModelKey, res);

        if (this.configProviders != null) {
            Map<String, Object> config = new HashMap<String, Object>();
            for (ReferenceDataProvider p : this.configProviders) {
                p.referenceData(config, request);
            }
            model.put("config", config);
        }
        return new ModelAndView(new StructuredResourceView(res), model);
    }
    
    private class StructuredResourceView implements View {
        private StructuredResource res;
        
        public StructuredResourceView(StructuredResource res) {
            this.res = res;
        }

        @Override
        public String getContentType() {
            return "text/html;charset=utf-8";
        }

        @Override
        public void render(Map<String, ?> model, HttpServletRequest request,
                HttpServletResponse response) throws Exception {
            final HtmlPage initialPage = htmlParser.createEmptyPage("initial-page");
            String templateRef = res.getType().getName();
            Template t = templateManager.getTemplate(templateRef);
            
            Map<String, TemplateLanguageDecoratorComponent> componentMap = components.get(res.getType());
            final ComponentResolver resolver = new DynamicComponentResolver(COMPONENT_NS, componentResolver, componentMap);
            request.setAttribute(COMPONENT_RESOLVER, resolver);
            
            TemplateEnvironment execution = TemplateEnvironment.create(
                    resolver, initialPage, request, 
                    //model, 
                    Collections.emptyMap());

            response.setContentType("text/html;charset=utf-8");
            OutputStream out = response.getOutputStream();
            System.out.println(Thread.currentThread().getName() + "__render_to: " + out);
            t.render(execution, out);
            response.flushBuffer();
        }
        
    } 

    private void initComponentDefs(StructuredResourceDescription desc) throws Exception {
        // XXX: "concurrent initialization":

        Map<String, TemplateLanguageDecoratorComponent> comps = new HashMap<String, TemplateLanguageDecoratorComponent>();

        List<ComponentDefinition> defs = desc.getAllComponentDefinitions();
        for (ComponentDefinition def : defs) {
            String name = def.getName();
            TemplateLanguageDecoratorComponent comp = 
                new TemplateLanguageDecoratorComponent(COMPONENT_NS, def, MVC_MODEL_CTX_ATTR,
                        this.directiveHandlers, this.htmlParser);
            comps.put(name, comp);
        }
        this.components.put(desc, comps);
    }

    @Override
    public void afterPropertiesSet() {
        List<StructuredResourceDescription> allDescriptions = this.resourceManager.list();
        for (StructuredResourceDescription desc : allDescriptions) {
            try {
                initComponentDefs(desc);
            } catch (Exception e) {
                throw new BeanInitializationException("Unable to initialize component definitions "
                        + "for resource type " + desc, e);
            }
        }
    }
    
    public static class ComponentSupport implements ComponentInvokerNodeFactory.ComponentSupport {
        
        @Override
        public ComponentResolver getComponentResolver(Context context) {
            RequestContext requestContext = RequestContext.getRequestContext();
            HttpServletRequest request = requestContext.getServletRequest();
            return (ComponentResolver) request.getAttribute(COMPONENT_RESOLVER);
        }

        @Override
        public HtmlPage getHtmlPage(Context context) {
            // Don't allow access to HTML page in 'call-component' component..
            return null;
        }
    }

    public void setResourceManager(StructuredResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    @Required
    public void setTemplateManager(TemplateManager templateManager) {
        this.templateManager = templateManager;
    }

    @Required
    public void setHtmlParser(HtmlPageParser htmlParser) {
        this.htmlParser = htmlParser;
    }

    @Required
    public void setResourceModelKey(String resourceModelKey) {
        this.resourceModelKey = resourceModelKey;
    }

    public void setConfigProviders(List<ReferenceDataProvider> configProviders) {
        this.configProviders = configProviders;
    }

    public void setPostFilters(List<HtmlPageFilter> postFilters) {
        this.postFilters = new ArrayList<HtmlPageFilter>(postFilters);
    }
    
    @Required
    public void setDirectiveHandlers(List<DirectiveHandler> directiveHandlers) {
        this.directiveHandlers = directiveHandlers;
    }
    
    @Required
    public void setComponentResolver(ComponentResolver resolver) {
        this.componentResolver = resolver;
    }
}
