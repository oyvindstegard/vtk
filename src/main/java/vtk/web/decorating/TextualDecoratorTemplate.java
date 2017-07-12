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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.text.html.HtmlPage;
import vtk.util.io.InputSource;
import vtk.web.RequestContext;


public class TextualDecoratorTemplate implements Template {

    private static final String DEFAULT_DOCTYPE =
        "html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"";

    private static Logger logger = LoggerFactory.getLogger(TextualDecoratorTemplate.class);

    private TextualComponentParser parser;
    private ComponentInvocation[] fragments;
    private ComponentResolver componentResolver;
    private InputSource templateSource;
    private Optional<Instant> lastModified = Optional.empty();
    

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
    
    

    @Override
    public void render(HtmlPage page, OutputStream out, Charset encoding, 
            HttpServletRequest request, Map<String, Object> model,
            Map<String, Object> templateParameters) {

        if (needCompile()) {
            compile();
        }
        try (Writer writer = new OutputStreamWriter(out)){

            for (ComponentInvocation fragment: fragments) {
                try {
                    String doctype = page.getDoctype();
                    if (doctype == null) {
                        doctype = DEFAULT_DOCTYPE;
                    }

                    if (fragment instanceof StaticTextFragment) {
                        StaticTextFragment f = (StaticTextFragment) fragment;
                        writer.write(f.buffer.toString());
                        continue;
                    }
                    Locale locale = RequestContext.getRequestContext(request).getLocale();
                    DecoratorRequest decoratorRequest = new DecoratorRequestImpl(
                            page, request, model, fragment.getParameters(), doctype, locale);

                    DecoratorComponent component = componentResolver.resolveComponent(
                            fragment.getNamespace(), fragment.getName());
                    
                    if (component == null && fragment.optional()) {
                        continue;
                    }

                    String chunk = renderComponent(component, decoratorRequest);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Included component: " + fragment
                                + " with result [" + chunk + "]");
                    }
                    writer.write(chunk);

                }
                catch (Throwable t) {
                    logger.warn("Error including component: " + fragment, t);
                    String msg = t.getMessage();
                    if (msg == null) {
                        msg = t.getClass().getName();
                    }
                    writer.write(fragment.getNamespace() + ":" + fragment.getName() + ": ");
                    writer.write(msg);
                }
            }
            writer.flush();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
    
    private boolean needCompile() {
        Optional<Instant> templateMod = templateSource.getLastModified();
        if (!templateMod.isPresent() || !lastModified.isPresent()) return true;
        return templateMod.get().isAfter(lastModified.get());
    }


    private synchronized void compile() {
        if (this.fragments != null && !needCompile()) {
            return;
        }
        try {
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
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (Exception e) {
           throw new RuntimeException(e);
       }
    }
    
    @Override
    public String toString() {
        return this.getClass().getName() + ": " + this.templateSource;
    }


}
