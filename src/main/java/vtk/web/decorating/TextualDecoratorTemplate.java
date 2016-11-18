/* Copyright (c) 2007, 2008, University of Oslo, Norway
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
package vtk.web.decorating;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vtk.text.html.HtmlPage;


public class TextualDecoratorTemplate implements Template {

    private static final String DEFAULT_DOCTYPE =
        "html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"";

    private static Logger logger = LoggerFactory.getLogger(TextualDecoratorTemplate.class);

    private TextualComponentParser parser;
    private ComponentInvocation[] fragments;
    private ComponentResolver componentResolver;
    private InputSource templateSource;
    private long lastModified = -1;
    

    public TextualDecoratorTemplate(TextualComponentParser parser,
                                     InputSource templateSource,
                                     ComponentResolver componentResolver) throws InvalidTemplateException {
        if (parser == null) {
            throw new IllegalArgumentException("Argument 'parser' is NULL");
        }
        if (templateSource == null) {
            throw new IllegalArgumentException("Argument 'templateSource' is NULL");
        }
        if (componentResolver == null) {
            throw new IllegalArgumentException("Argument 'componentResolver' is NULL");
        }
        this.parser = parser;
        this.templateSource = templateSource;
        this.componentResolver = componentResolver;
        try {
            compile();
        } catch (Exception e) {
            throw new InvalidTemplateException("Unable to compile template " 
                    + templateSource, e);
        }
    }

    public class Execution implements TemplateExecution {
        private HtmlPageContent content;
        private ComponentInvocation[] fragments;
        private ComponentResolver componentResolver;
        private HttpServletRequest request;
        private Map<String, Object> model;

        Execution(HtmlPageContent content, ComponentInvocation[] fragments, 
                ComponentResolver componentResolver, HttpServletRequest request,
                Map<String, Object> model) {
            this.content = content;
            this.componentResolver = componentResolver;
            this.fragments = fragments;
            this.request = request;
            this.model = model;
        }
        
        public void setComponentResolver(ComponentResolver componentResolver) {
            this.componentResolver = componentResolver;
        }
        
        @Override
        public ComponentResolver getComponentResolver() {
            return this.componentResolver;
        }

        @Override
        public PageContent render() throws Exception {
            HtmlPage html = this.content.getHtmlContent();

            StringBuilder sb = new StringBuilder();
            for (ComponentInvocation fragment: this.fragments) {
                try {
                    String doctype = html.getDoctype();
                    if (doctype == null) {
                        doctype = DEFAULT_DOCTYPE;
                    }
                    
                    if (fragment instanceof StaticTextFragment) {
                        StaticTextFragment f = (StaticTextFragment) fragment;
                        sb.append(f.buffer.toString());
                        continue;
                    }

                    Locale locale = 
                        new org.springframework.web.servlet.support.RequestContext(this.request).getLocale();
                    DecoratorRequest decoratorRequest = new DecoratorRequestImpl(
                            html, this.request, this.model, fragment.getParameters(), doctype, locale);
                    
                    DecoratorComponent component = this.componentResolver.resolveComponent(
                            fragment.getNamespace(), fragment.getName());
                    
                    String chunk = renderComponent(component, decoratorRequest);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Included component: " + fragment
                                     + " with result [" + chunk + "]");
                    }
                    sb.append(chunk);

                } catch (Throwable t) {
                    logger.warn("Error including component: " + fragment, t);
                    String msg = t.getMessage();
                    if (msg == null) {
                        msg = t.getClass().getName();
                    }
                    sb.append(fragment.getNamespace());
                    sb.append(":").append(fragment.getName());
                    sb.append(": ").append(msg);
                }
            }
            return new ContentImpl(sb.toString(), 
                    content.getOriginalCharacterEncoding());
        }
        
    }
    
    @Override
    public TemplateExecution newTemplateExecution(HtmlPageContent html,
            HttpServletRequest request, Map<String, Object> model,
            Map<String, Object> templateParameters) throws Exception {
        if (html == null) {
            throw new IllegalArgumentException("Argument 'html' cannot be NULL");
        }
        if (request == null) {
            throw new IllegalArgumentException("Argument 'request' cannot be NULL");
        }
        if (model == null) {
            throw new IllegalArgumentException("Argument 'model' cannot be NULL");
        }
        if (this.templateSource.getLastModified() > this.lastModified) {
            compile();
        }
        return new Execution(html, this.fragments, this.componentResolver, request, model);
    }

    
    private String renderComponent(DecoratorComponent c, DecoratorRequest request)
        throws Exception {
        
        // Default values for decorator responses:
        String defaultResponseDoctype = request.getDoctype();
        String defaultResponseEncoding = "utf-8";
        Locale defaultResponseLocale = Locale.getDefault();

        DecoratorResponseImpl response = new DecoratorResponseImpl(
            defaultResponseDoctype, defaultResponseLocale, defaultResponseEncoding);
        c.render(request, response);
        String result = response.getContentAsString();
        return result;
    }
    

    private synchronized void compile() throws Exception {
       if (this.fragments != null 
                && (this.lastModified == this.templateSource.getLastModified())) {
            return;
        }

        Reader reader = new InputStreamReader(
                this.templateSource.getInputStream(), 
                this.templateSource.getCharacterEncoding());

        try {
            this.fragments = this.parser.parse(reader);
        } finally {
            reader.close();
        }

        this.lastModified = templateSource.getLastModified();
    }
    
    @Override
    public String toString() {
        return this.getClass().getName() + ": " + this.templateSource;
    }

}
