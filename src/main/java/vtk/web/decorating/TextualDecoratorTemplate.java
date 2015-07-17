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
import java.io.OutputStream;
import java.io.Reader;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import vtk.text.html.HtmlPage;
import vtk.util.io.InputSource;


public class TextualDecoratorTemplate implements Template {

    private static final String DEFAULT_DOCTYPE =
        "html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"";

    private static Log logger = LogFactory.getLog(TextualDecoratorTemplate.class);

    private TextualComponentParser parser;
    private ComponentInvocation[] fragments;
    private ComponentResolver componentResolver;
    private InputSource templateSource;
    private long lastModified = -1;
    

    public TextualDecoratorTemplate(TextualComponentParser parser, 
            InputSource templateSource, ComponentResolver componentResolver) 
                    throws InvalidTemplateException {
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

//    public class Execution implements TemplateEnvironment {
//        private HtmlPage content;
//        private ComponentInvocation[] fragments;
//        private ComponentResolver componentResolver;
//        private HttpServletRequest request;
//        private HttpServletResponse response;
//        private Map<String, Object> model;
//
//        Execution(HtmlPage content, ComponentInvocation[] fragments, 
//                ComponentResolver componentResolver, HttpServletRequest request,
//                HttpServletResponse response, Map<String, Object> model) {
//            this.content = content;
//            this.componentResolver = componentResolver;
//            this.fragments = fragments;
//            this.request = request;
//            this.response = response;
//            this.model = model;
//        }
//        
//        @Override
//        public ComponentResolver componentResolver() {
//            return componentResolver;            
//        }
//
//        @Override
//        public HtmlPage htmlPage() {
//            return content;
//        }
//        
//        @Override
//        public HttpServletRequest request() {
//            return request;
//        }
//
//        @Override
//        public Map<String, Object> model() {
//            return model;
//        }
//
//        @Override
//        public Map<String, Object> templateParameters() {
//            return new HashMap<>();
//        }
//        
//    }
    
    
    private void renderComponent(DecoratorComponent c, DecoratorRequest req, OutputStream out)
        throws Exception {
        
        // Default values for decorator responses:
        String defaultResponseDoctype = req.getDoctype();
        String defaultResponseEncoding = "utf-8";
        Locale defaultResponseLocale = Locale.getDefault();

        DecoratorResponseImpl response = new DecoratorResponseImpl(
            defaultResponseDoctype, defaultResponseLocale, defaultResponseEncoding, out);
        c.render(req, response);
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


    @Override
    public void render(TemplateEnvironment env, OutputStream out) throws Exception {
        Optional<HtmlPage> html = env.htmlPage();
        if (!html.isPresent()) throw new Exception("No HTML page");
        
        for (ComponentInvocation fragment: fragments) {
            try {
                String doctype = html.get().getDoctype();
                if (doctype == null) {
                    doctype = DEFAULT_DOCTYPE;
                }

                if (fragment instanceof StaticTextFragment) {
                    StaticTextFragment f = (StaticTextFragment) fragment;
                    f.write(out);
                    continue;
                }

                Locale locale = 
                        new org.springframework.web.servlet.support.RequestContext(env.request()).getLocale();
                DecoratorRequest decoratorRequest = new DecoratorRequestImpl(
                        html.get(), env.request(), 
                        //env.model(), 
                        fragment.getParameters(), doctype, locale);

                DecoratorComponent component = this.componentResolver.resolveComponent(
                        fragment.getNamespace(), fragment.getName());

                renderComponent(component, decoratorRequest, out);
                out.flush();

            } catch (Throwable t) {
                logger.warn("Error including component: " + fragment, t);
                String msg = t.getMessage();
                if (msg == null) {
                    msg = t.getClass().getName();
                }
                StringBuilder sb = new StringBuilder();
                sb.append(fragment.getNamespace());
                sb.append(":").append(fragment.getName());
                sb.append(": ").append(msg);
                out.write(sb.toString().getBytes("utf-8"));
            }
        }
        out.flush();
    }

}
