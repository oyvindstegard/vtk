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

import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import vtk.resourcemanagement.ComponentDefinition;
import vtk.text.html.HtmlContent;
import vtk.text.html.HtmlFragment;
import vtk.text.html.HtmlPageParser;
import vtk.text.tl.Context;
import vtk.text.tl.DirectiveHandler;
import vtk.text.tl.Node;
import vtk.text.tl.NodeList;
import vtk.text.tl.TemplateHandler;
import vtk.text.tl.TemplateParser;
import vtk.web.ModelProvider;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;
import vtk.web.decorating.DynamicDecoratorTemplate;
import vtk.web.decorating.HtmlDecoratorComponent;
import vtk.web.decorating.components.AbstractDecoratorComponent;
import vtk.web.decorating.components.DecoratorComponentException;


public class TemplateLanguageDecoratorComponent extends AbstractDecoratorComponent
implements HtmlDecoratorComponent {

    private String namespace;
    private ComponentDefinition definition;
    private String modelKey;
    private NodeList nodeList;
    private HtmlPageParser htmlParser;
    private List<DirectiveHandler> directiveHandlers;
    private static Log logger = LogFactory.getLog(TemplateLanguageDecoratorComponent.class); 

    private Date compileTime;

    public TemplateLanguageDecoratorComponent(String namespace, 
            ComponentDefinition definition, 
            String modelKey, List<DirectiveHandler> directiveHandlers, 
            HtmlPageParser htmlParser) throws Exception {
        this.namespace = namespace;
        this.definition = definition;
        this.modelKey = modelKey;
        this.htmlParser = htmlParser;
        this.directiveHandlers = directiveHandlers;
        compile();
    }

    private void compile() throws Exception {
        if (this.compileTime == null || 
                this.compileTime.getTime() < this.definition.getLastModified().getTime()) {
            new TemplateParser(
                    new StringReader(definition.getDefinition()), 
                    directiveHandlers, 
                    new TemplateHandler() {
                        @Override
                        public void success(NodeList result) {
                            nodeList = result;
                            compileTime = new Date();
                        }
                        @Override
                        public void error(final String message, final int line) {
                            NodeList err = new NodeList();
                            err.add(new Node() {
                                @Override
                                public boolean render(Context ctx, Writer out)
                                        throws Exception {
                                    out.write("Error compiling component " + getName() 
                                            + ": line " + line + ": " + message);
                                    return true;
                                }
                            });
                            nodeList = err;
                            compileTime = new Date();
                        }
                    }).parse();;
        }
    }

    public List<HtmlContent> render(DecoratorRequest request) throws Exception {
        try {
            compile();
            Context ctx = createContext(request);
            StringWriter writer = new StringWriter();
            this.nodeList.render(ctx, writer);
            HtmlFragment fragment = this.htmlParser.parseFragment(writer.getBuffer().toString());
            return fragment.getContent();
        } catch (Throwable t) {
            logger.info("Error rendering component '" + getName() + "'", t);
            throw new DecoratorComponentException("Error rendering component '" 
                    + getName() + "': " + t.getMessage(), t);
        }
    }

    public void render(DecoratorRequest request, DecoratorResponse response)
    throws Exception {
        try {
            compile();
            Context ctx = createContext(request);
            Writer writer = response.getWriter();
            this.nodeList.render(ctx, writer);
            writer.flush();
            writer.close();
        } catch (Throwable t) {
            logger.info("Error rendering component '" + getName() + "'", t);
            throw new DecoratorComponentException("Error rendering component '" 
                    + getName() + "': " + t.getMessage(), t);
        }
    }
    
    private Context createContext(DecoratorRequest request) {
        Context ctx = new Context(request.getLocale());
        Map<String, Object> mvcModel = ModelProvider.getModel(request.getServletRequest());
        
        if (this.modelKey != null) { 
            ctx.define(this.modelKey, mvcModel, true);
        }
        for (String param : this.definition.getParameters()) {
            Object value = request.getRawParameter(param);
            ctx.define(param, value, true);
        }
        ctx.setAttribute(DynamicDecoratorTemplate.SERVLET_REQUEST_CONTEXT_ATTR, request.getServletRequest());
        return ctx;
    }

    @Override
    protected String getDescriptionInternal() {
        return null;
    }

    @Override
    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> result = new HashMap<String, String>();
        for (String param : this.definition.getParameters()) {
            result.put(param, "#parameter");
        }
        return result;
    }

    @Override
    public String getName() {
        return this.definition.getName();
    }

    @Override
    public String getNamespace() {
        return this.namespace;
    }
}
