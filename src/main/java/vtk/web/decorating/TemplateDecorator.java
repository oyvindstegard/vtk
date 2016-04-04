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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vtk.text.html.HtmlNodeFilter;
import vtk.text.html.HtmlPage;
import vtk.text.html.HtmlPageFilter;
import vtk.text.html.HtmlPageParser;
import org.w3c.dom.Document;
import org.w3c.tidy.Tidy;

public class TemplateDecorator implements Decorator {

    private final static String EMPTY_DOCUMENT_START = 
        "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"\n"
        + "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n"
        + "<html><head></head><body>";

    private final static String EMPTY_DOCUMENT_END = "</body></html>";
    private final static String EMPTY_DOCUMENT = EMPTY_DOCUMENT_START + EMPTY_DOCUMENT_END;
    private final static String DEFAULT_ENCODING = "utf-8";

    private static Logger logger = LoggerFactory.getLogger(TemplateDecorator.class);
    
    private HtmlPageParser htmlParser;
    private DecorationResolver decorationResolver;
    boolean tidyXhtml = true;
    
    private List<HtmlNodeFilter> initialFilters;
    private List<HtmlNodeFilter> userFilters;
    private List<HtmlPageFilter> postFilters;

    private String preventDecoratingParameter;

    private static final String DECORATION_DESCRIPTOR_REQ_ATTR = 
        TemplateDecorator.class.getName() + ".DecorationDescriptor";
    
    public boolean match(HttpServletRequest request, HttpServletResponse response) throws Exception {
        DecorationDescriptor descriptor = resolveDecorationDescriptor(request, response);
        if (descriptor != null) {
            request.setAttribute(DECORATION_DESCRIPTOR_REQ_ATTR, descriptor);
            return descriptor.decorate();
        }
        return false;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public PageContent decorate(Map model, HttpServletRequest request, PageContent content)
        throws Exception, UnsupportedEncodingException, IOException {

        DecorationDescriptor descriptor = (DecorationDescriptor) request.getAttribute(DECORATION_DESCRIPTOR_REQ_ATTR);
        if (logger.isDebugEnabled()) {
            logger.debug("Resolved decorator descriptor for request " 
                    + request + ": " + descriptor);
        }
        if (descriptor != null) {
            request.removeAttribute(DECORATION_DESCRIPTOR_REQ_ATTR);
        }
        if (descriptor == null || !descriptor.decorate()) {
            return content;
        }

        boolean filter = descriptor.parse();
        List<HtmlNodeFilter> filters = new ArrayList<HtmlNodeFilter>();
        if (this.initialFilters != null) {
            filters.addAll(this.initialFilters);
        }
        
        if (filter && this.userFilters != null) {
            filters.addAll(this.userFilters);
        }
        HtmlPageContent htmlContent = parseHtml(content, filters);

        List<Template> templates = descriptor.getTemplates();
        if (templates.isEmpty()) {
            if (descriptor.tidy()) {
                return tidyContent(htmlContent);
            }
        }

        if (htmlContent.getHtmlContent().isFrameset()) {
            // Framesets are not decorated:
            return content;
        }

        content = htmlContent;
        
        for (Template template: templates) {
            if (logger.isDebugEnabled()) {
                logger.debug("Rendering request for " + request.getRequestURI()
                        + " using template '" + template + "'");
            }
            HtmlPageContent c = parseHtml(content, this.userFilters);
            TemplateExecution execution = template.newTemplateExecution(c, request, model, 
                    descriptor.getParameters(template));
            content = execution.render();
            
            if (descriptor.tidy()) {
                content = tidyContent(content);
            }
        }
        
        if (this.postFilters != null) {
            htmlContent = parseHtml(content, null);
            HtmlPage p = htmlContent.getHtmlContent();
            for (HtmlPageFilter f: this.postFilters) {
                if (f.match(p)) {
                    p.filter(f);
                }
            }
            content = new HtmlPageContentImpl(
                    content.getOriginalCharacterEncoding(), p);
        }
        
        return content;
    }


    protected HtmlPageContent parseHtml(PageContent content, List<HtmlNodeFilter> filters) throws Exception {
        if (content instanceof HtmlPageContent) {
            if (logger.isDebugEnabled()) {
                logger.debug("HTML content already parsed");
            }
            return (HtmlPageContent) content;
        }
        
        long before = System.currentTimeMillis();
        String encoding = content.getOriginalCharacterEncoding();
        String source = content.getContent();

        // Best-effort attempt to parse empty and garbled
        // documents:
        if (source == null || "".equals(source.trim())) {
            source = EMPTY_DOCUMENT;
        } else if (!source.trim().startsWith("<")) {
            StringBuilder sb = new StringBuilder();
            sb.append(EMPTY_DOCUMENT_START);
            sb.append(source);
            sb.append(EMPTY_DOCUMENT_END);
            source = sb.toString();
        }

        InputStream stream = new ByteArrayInputStream(source.getBytes(encoding));
        HtmlPage html = null;
        if (filters != null) {
            html = this.htmlParser.parse(stream, encoding, filters);
        } else {
            html = this.htmlParser.parse(stream, encoding);
        }

        long duration = System.currentTimeMillis() - before;
        if (logger.isDebugEnabled()) {
            logger.debug("Parsing document took " + duration + " ms");
        }
        return new HtmlPageContentImpl(content.getOriginalCharacterEncoding(), html);
    }
    
    protected PageContent tidyContent(PageContent content) throws Exception {
        ByteArrayInputStream inStream = new ByteArrayInputStream(content.getContent().getBytes(DEFAULT_ENCODING));

        Tidy tidy = new Tidy();
        tidy.setTidyMark(false);
        tidy.setMakeClean(false);
        tidy.setShowWarnings(false);
        tidy.setQuiet(true);
        tidy.setXHTML(tidyXhtml);
        tidy.setDocType("transitional");
        tidy.setInputEncoding(DEFAULT_ENCODING);
        tidy.setOutputEncoding(DEFAULT_ENCODING);

        Document document = tidy.parseDOM(inStream, null);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        tidy.pprint(document, outputStream);
            
        content = new ContentImpl(new String(outputStream.toByteArray(), DEFAULT_ENCODING),
                content.getOriginalCharacterEncoding());
        return content;
    }
    
    protected DecorationDescriptor resolveDecorationDescriptor(
            HttpServletRequest request, HttpServletResponse response) throws Exception {
        
        final DecorationDescriptor desc = this.decorationResolver.resolve(request, response);
        final boolean override = this.preventDecoratingParameter != null 
                && request.getParameter(this.preventDecoratingParameter) != null;
        
        if (!override) {
            return desc;
        }
        
        return new DecorationDescriptor() {
            @Override
            public boolean decorate() {
                return true;
            }
            @Override
            public boolean tidy() {
                return desc.tidy();
            }
            @Override
            public boolean parse() {
                return true;
            }
            @Override
            public List<Template> getTemplates() {
                return Collections.emptyList();
            }
            @Override
            public Map<String, Object> getParameters(Template template) {
                return Collections.emptyMap();
            }
        };
    }
        
    public void setUserFilters(List<HtmlNodeFilter> userFilters) {
        this.userFilters = userFilters;
    }
    
    public void setInitialFilters(List<HtmlNodeFilter> initialFilters) {
        this.initialFilters = initialFilters;
    }
    
    public void setPostFilters(List<HtmlPageFilter> postFilters) {
        this.postFilters = postFilters;
    }

    public void setDecorationResolver(DecorationResolver decorationResolver) {
        this.decorationResolver = decorationResolver;
    }

    public void setHtmlParser(HtmlPageParser htmlParser) {
        this.htmlParser = htmlParser;
    }

    public void setTidyXhtml(boolean tidyXhtml) {
        this.tidyXhtml = tidyXhtml;
    }

    public void setPreventDecoratingParameter(String preventDecoratingParameter) {
        this.preventDecoratingParameter = preventDecoratingParameter;
    }
}
