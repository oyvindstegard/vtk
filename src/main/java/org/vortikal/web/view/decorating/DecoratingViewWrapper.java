/* Copyright (c) 2007, University of Oslo, Norway
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
package org.vortikal.web.view.decorating;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.web.servlet.View;

import org.vortikal.util.repository.ContentTypeHelper;
import org.vortikal.util.text.HtmlUtil;
import org.vortikal.web.service.Assertion;
import org.vortikal.web.servlet.BufferedResponse;
import org.vortikal.web.view.decorating.ssi.SsiHandler;
import org.vortikal.web.view.wrapper.RequestWrapper;
import org.vortikal.web.view.wrapper.ViewWrapper;
import org.vortikal.web.view.wrapper.ViewWrapperException;

/**
 * 
 */
public class DecoratingViewWrapper implements ViewWrapper {

    protected Log logger = LogFactory.getLog(this.getClass());

    private HtmlPageParser parser;
    private TemplateResolver templateResolver;
    
    private boolean propagateExceptions = true;
    private String forcedOutputEncoding;
    private boolean guessCharacterEncodingFromContent = false;
    private boolean appendCharacterEncodingToContentType = true;
    private Assertion[] assertions;
    private HtmlNodeFilter htmlNodeFilter;    


    public void setTemplateResolver(TemplateResolver templateResolver) {
        this.templateResolver = templateResolver;
    }
    

    public void setForcedOutputEncoding(String forcedOutputEncoding) {
        this.forcedOutputEncoding = forcedOutputEncoding;
    }


    public void setGuessCharacterEncodingFromContent(
            boolean guessCharacterEncodingFromContent) {
        this.guessCharacterEncodingFromContent = guessCharacterEncodingFromContent;
    }


    public void setAppendCharacterEncodingToContentType(
            boolean appendCharacterEncodingToContentType) {
        this.appendCharacterEncodingToContentType = appendCharacterEncodingToContentType;
    }


    public void setPropagateExceptions(boolean propagateExceptions) {
        this.propagateExceptions = propagateExceptions;
    }


    public void setAssertions(Assertion[] assertions) {
        this.assertions = assertions;
    }
    

    public void setHtmlParser(HtmlPageParser parser) {
        this.parser = parser;
    }
    
    public void setHtmlNodeFilter(HtmlNodeFilter htmlNodeFilter) {
        this.htmlNodeFilter = htmlNodeFilter;
    }
    

    public void renderView(View view, Map model, HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        RequestWrapper requestWrapper = new RequestWrapper(request, "GET");
        BufferedResponse responseWrapper = new BufferedResponse();

        preRender(model, request, responseWrapper);

        if (this.propagateExceptions) {
            view.render(model, requestWrapper, responseWrapper);
        } else {
            try {
                view.render(model, requestWrapper, responseWrapper);
            } catch (Throwable t) {
                throw new ViewWrapperException(
                        "An error occurred while rendering the wrapped view",
                        t, model, view);
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Rendered view " + view + ", proceeding to postRender step");
        }

        postRender(model, request, responseWrapper, response);
    }


    protected void preRender(Map model, HttpServletRequest request,
            BufferedResponse bufferedResponse) throws Exception {
    }


    protected void postRender(Map model, HttpServletRequest request,
                              BufferedResponse bufferedResponse,
                              HttpServletResponse servletResponse) throws Exception {

        byte[] contentBuffer = bufferedResponse.getContentBuffer();

        String characterEncoding = null;
        String contentType = bufferedResponse.getContentType();
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        contentType = contentType.trim();
        if (contentType.indexOf("charset") != -1
                && contentType.indexOf(";") != -1) {
            contentType = contentType.substring(0, contentType.indexOf(";"));
            characterEncoding = bufferedResponse.getCharacterEncoding();
        } else if (this.guessCharacterEncodingFromContent) {
            characterEncoding = HtmlUtil
                    .getCharacterEncodingFromBody(contentBuffer);
        }

        if (!ContentTypeHelper.isHTMLContentType(contentType)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Unable to decorate response " + bufferedResponse
                             + " for requested URL " + request.getRequestURL()
                             + ": unsupported content type: " + contentType);

            }
            writeResponse(bufferedResponse, servletResponse,
                          bufferedResponse.getContentType());
            return;
        }

        if (characterEncoding == null) {
            characterEncoding = bufferedResponse.getCharacterEncoding();
        }

        if (!Charset.isSupported(characterEncoding)) {
            if (logger.isInfoEnabled()) {
                logger.info("Unable to perform content filtering on response  "
                        + bufferedResponse + " for requested URL "
                        + request.getRequestURL() + ": character encoding '"
                        + characterEncoding
                        + "' is not supported on this system");
            }
            writeResponse(bufferedResponse, servletResponse,
                          bufferedResponse.getContentType());
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Reading buffered content using character encoding "
                    + characterEncoding);
        }

        String content = new String(contentBuffer, characterEncoding);
        
        org.springframework.web.servlet.support.RequestContext ctx =
            new org.springframework.web.servlet.support.RequestContext(request);

        Template[] templates = resolveTemplates(model, request, ctx.getLocale());
            
        if (templates == null || templates.length == 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("Unable to resolve template for request " + request);
            }
            writeResponse(bufferedResponse, servletResponse,
                    bufferedResponse.getContentType());
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Rendering template sequence "
                         + java.util.Arrays.asList(templates));
        }
        BufferedResponse templateResponse = null;

        for (int i = 0; i < templates.length; i++) {
            templateResponse = new BufferedResponse();
            
            HtmlPage html = parseHtml(content);
            if (logger.isDebugEnabled()) {
                logger.debug("Parsed document [root element: " + html.getRootElement() + " "
                             + ", doctype: "+ html.getDoctype() + "]");
            }
            HtmlElement rootElement = html.getRootElement();
            if (rootElement != null && "frameset".equals(rootElement.getName())) {
                // Framesets are not decorated:
                content = html.getDoctype() + rootElement.getEnclosedContent();
                // XXX: write content to response
                templateResponse.getOutputStream().write(content.getBytes("utf-8"));

            } else {
                templates[i].render(model, html, request, ctx.getLocale(), templateResponse);
                content = new String(templateResponse.getContentBuffer(),
                                     templateResponse.getCharacterEncoding());
            }
        }
        writeResponse(templateResponse, servletResponse, "text/html");
    }
    


    protected Template[] resolveTemplates(Map model, HttpServletRequest request,
                                          Locale locale) throws Exception {
        return this.templateResolver.resolveTemplates(model, request, locale);

    }
    

    protected HtmlPage parseHtml(String content) throws Exception {
        long before = System.currentTimeMillis();

        // XXX: encoding
        String encoding = "utf-8";        
        InputStream stream = new java.io.ByteArrayInputStream(content.getBytes(encoding));
        HtmlPage html = null;
        if (this.htmlNodeFilter != null) {
            html = this.parser.parse(stream, encoding, this.htmlNodeFilter);
        } else {
            html = this.parser.parse(stream, encoding);
        }

        long duration = System.currentTimeMillis() - before;
        if (logger.isDebugEnabled()) {
            logger.debug("Parsing document took " + duration + " ms");
        }
        return html;
    }
    

    

    /**
     * Writes the buffer from the wrapped response to the actual
     * response. Sets the HTTP header <code>Content-Length</code> to
     * the size of the buffer in the wrapped response.
     * 
     * @param responseWrapper the wrapped response.
     * @param response the real servlet response.
     * @param contentType the content type of the response.
     * @exception Exception if an error occurs.
     */
    protected void writeResponse(BufferedResponse responseWrapper,
                                 ServletResponse response, String contentType)
            throws Exception {
        byte[] content = responseWrapper.getContentBuffer();
        
        ServletOutputStream outStream = response.getOutputStream();

        if (logger.isDebugEnabled()) {
            logger.debug("Write response: Content-Length: " + content.length
                    + ", Content-Type: " + contentType);
        }
        if (contentType.indexOf("charset") == -1) {
            response.setContentType(contentType  + ";charset=utf-8");
        } else {
            response.setContentType(contentType);
        }
        response.setContentLength(content.length);

        // Make sure content is not cached:
        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            httpServletResponse.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0");
            httpServletResponse.setHeader("Expires", "0");
            httpServletResponse.setHeader("Pragma", "no-cache");
        }
        outStream.write(content);
        outStream.flush();
        outStream.close();
    }


    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(this.getClass().getName()).append(":");
        sb.append("]");
        return sb.toString();
    }


}
