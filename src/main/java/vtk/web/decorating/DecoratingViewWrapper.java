/* Copyright (c) 2005, 2007, 2008, University of Oslo, Norway
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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.servlet.View;
import vtk.repository.Resource;
import vtk.util.io.SizeLimitException;
import vtk.util.repository.ContentTypeHelper;
import vtk.util.text.HtmlExtractUtil;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.referencedata.ReferenceDataProviding;
import vtk.web.servlet.BufferedResponse;
import vtk.web.servlet.ConfigurableRequestWrapper;
import vtk.web.servlet.StatusAwareResponseWrapper;

/**
 * This view wrapper takes a view and decorates the output from that
 * view. A list of (textual) {@link Decorator decorators} is matched. 
 * If any matches, a buffered servlet response is created before the view is rendered, 
 * and the matching decorators applied. 
 * 
 * <p>Main application areas include merging common components
 * like menus, breadcrumb trails, etc. into the HTML generated by
 * views and substituting content (like SSI directives) in the response.
 * 
 * <p>The Content-Type header written to the response is the same as
 * that of the original response, with a possible modification of the
 * <code>charset</code> parameter (see below).
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>maxBufferSize</code> - the maximum length of a wrapped response
 *   <li><code>decorators</code> - an array of {@link
 *   Decorator decorators} to apply to the textual content
 *   that was the result of the wrapped view invocation.
 *   <li><code>guessCharacterEncodingFromContent</code> (boolean) -
 *   whether to check (HTML) body contents for character encoding, if
 *   it was not specified by the <code>Content-Type</code> header of
 *   the wrapped view. Default is <code>false</code>.
 *   <li><code>appendCharacterEncodingToContentType</code> (boolean)
 *   - if set to <code>false</code>, no <code>charset</code> parameter
 *   will be added to the <code>Content-Type</code> header set by this
 *   view. Default is <code>true</code>.
 *   <li><code>forcedOutputEncoding</code> - if this option is set,
 *   the output will be written using that character encoding.
 * </ul>
 * 
 * @see Decorator
 * @see Decorator#match(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse) 
 */
public class DecoratingViewWrapper implements ViewWrapper, ReferenceDataProviding {

    private static Log logger = LogFactory.getLog(DecoratingViewWrapper.class);

    private long maxDocumentSize = -1;
    private Decorator[] decorators;
    private ReferenceDataProvider[] referenceDataProviders;
    private String forcedOutputEncoding;
    private boolean guessCharacterEncodingFromContent = false;
    private boolean appendCharacterEncodingToContentType = true;
    private Map<String, Object> staticHeaders = null;
    private View documentTooLargeView;
    private String preventDecoratingParameter;

    public void setMaxDocumentSize(long maxDocumentSize) {
        this.maxDocumentSize = maxDocumentSize;
    }


    public void setDecorators(Decorator[] decorators) {
        this.decorators = decorators;
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


    public ReferenceDataProvider[] getReferenceDataProviders() {
        return this.referenceDataProviders;
    }


    public void setReferenceDataProviders(ReferenceDataProvider[] referenceDataProviders) {
        this.referenceDataProviders = referenceDataProviders;
    }


    public void setStaticHeaders(Map<String, Object> staticHeaders) {
        this.staticHeaders = staticHeaders;
    }
    

    public void setDocumentTooLargeView(View documentTooLargeView) {
        this.documentTooLargeView = documentTooLargeView;
    }


    public void setPreventDecoratingParameter(String preventDecoratingParameter) {
        this.preventDecoratingParameter = preventDecoratingParameter;
    }

    @SuppressWarnings("rawtypes")
    public void renderView(View view, Map model, HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        Object o = model.get("resource");
        if (this.maxDocumentSize > 0 && o instanceof Resource) {
            Resource resource = (Resource) o;
            if (resource.getContentLength() > this.maxDocumentSize) {
                logger.info("Document too large to be decorated: " + request.getRequestURI());
                view.render(model, request, response);
                return;
            }
        }
        List<Decorator> decoratorList = new ArrayList<Decorator>();
        if (this.decorators != null) {
            for (int i = 0; i < this.decorators.length; i++) {
                if (this.decorators[i].match(request, response)) {
                    decoratorList.add(this.decorators[i]);
                }
            }        
        }

        if (decoratorList.size() == 0) {
            view.render(model, request, response);
            return;
        }

        ConfigurableRequestWrapper requestWrapper = new ConfigurableRequestWrapper(request);
        requestWrapper.setMethod("GET");
        
        BufferedResponse bufferedResponse = new BufferedResponse(this.maxDocumentSize);

        int status = HttpServletResponse.SC_OK;
        if (response instanceof StatusAwareResponseWrapper) {
            status = ((StatusAwareResponseWrapper) response).getStatus();
            bufferedResponse.setStatus(status);
        }

        if (view instanceof HtmlRenderer) {
            HtmlPageContent page = ((HtmlRenderer) view).render(model, requestWrapper);
            decorate(status, model, request, decoratorList, page, response);

        } else {
            try {
                view.render(model, requestWrapper, bufferedResponse);
            } catch (SizeLimitException e) {
                logger.info("Document too large to be decorated: " + request.getRequestURI());
                if (this.documentTooLargeView != null) {
                    this.documentTooLargeView.render(model, requestWrapper, response);
                } else {
                    throw e;
                }
            }
            if (logger.isDebugEnabled()) {
                logger.debug("About to post process buffered content, content type: "
                        + bufferedResponse.getContentType()
                        + ", character encoding: "
                        + bufferedResponse.getCharacterEncoding());
            }
            decorate(model, request, decoratorList, bufferedResponse, response);
        }
    }

    
    @SuppressWarnings("rawtypes")
    private void decorate(Map model, HttpServletRequest request,
                           List<Decorator> decoratorList, BufferedResponse bufferedResponse,
                           HttpServletResponse response)
        throws Exception {

        byte[] contentBuffer = bufferedResponse.getContentBuffer();

        String contentType = bufferedResponse.getContentType();
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        contentType = contentType.trim();
        
        String characterEncoding = null;
        
        
        if (contentType.indexOf("charset") != -1
                && contentType.indexOf(";") != -1) {
            contentType = contentType.substring(0, contentType.indexOf(";"));
            characterEncoding = bufferedResponse.getCharacterEncoding();
        } else if (this.guessCharacterEncodingFromContent) {
            characterEncoding = HtmlExtractUtil.getCharacterEncodingFromBody(contentBuffer);
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
            bufferedResponse.writeTo(response, true);
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Reading buffered content using character encoding "
                    + characterEncoding);
        }


        PageContent content = new ContentImpl(new String(contentBuffer, characterEncoding),
                                          characterEncoding);

        if (shouldDecorate(request)) {
            for (Decorator decorator: decoratorList) {
                content = decorator.decorate(model, request, content);
                if (logger.isDebugEnabled()) {
                    logger.debug("Invoked decorator: " + decorator);
                }
            }
        }

        if (this.forcedOutputEncoding != null) {
            characterEncoding = this.forcedOutputEncoding;
        }

        if (this.appendCharacterEncodingToContentType
                && ContentTypeHelper.isTextContentType(contentType)) {

            contentType = contentType + ";charset=" + characterEncoding;
        }
        int status = bufferedResponse.getStatus();
        writeResponse(status, content.getContent().getBytes(characterEncoding), contentType, response);
    }

    @SuppressWarnings("rawtypes")
    private void decorate(int status, Map model, HttpServletRequest request,
                          List<Decorator> decoratorList, HtmlPageContent page,
                          HttpServletResponse response)
        throws Exception {

        PageContent content = page;
        String characterEncoding = page.getHtmlContent().getCharacterEncoding();
        String contentType = "text/html";
        
        if (shouldDecorate(request)) {
            for (Decorator decorator: decoratorList) {
                content = decorator.decorate(model, request, content);
                if (logger.isDebugEnabled()) {
                    logger.debug("Invoked decorator: " + decorator);
                }
            }
        }
        
        if (this.forcedOutputEncoding != null) {
            characterEncoding = this.forcedOutputEncoding;
        }

        if (this.appendCharacterEncodingToContentType
                && ContentTypeHelper.isTextContentType(contentType)) {

            contentType = contentType + ";charset=" + characterEncoding;
        }
        writeResponse(status, content.getContent().getBytes(characterEncoding), contentType, response);
    }


    protected void writeResponse(int status, byte[] content,
            String contentType, HttpServletResponse response)
            throws Exception {
        writeStaticHeaders(response);

        if (logger.isDebugEnabled()) {
            logger.debug("Write response: Content-Length: " + content.length
                    + ", Content-Type: " + contentType);
        }
        response.setStatus(status);
        response.setContentType(contentType);
        response.setContentLength(content.length);
        ServletOutputStream outStream = response.getOutputStream();
        outStream.write(content);
        outStream.flush();
        outStream.close();
        response.flushBuffer();
    }


    protected void writeStaticHeaders(HttpServletResponse response) throws Exception {
        if (this.staticHeaders == null) {
            return;
        }

        for (Map.Entry<String, Object> header: this.staticHeaders.entrySet()) {
            
            if (header.getValue() instanceof Date) {
                response.setDateHeader(header.getKey(), ((Date) header.getValue()).getTime());
            } else {
                response.setHeader(header.getKey(), (String) header.getValue());
            }
        }
    }
    
    private boolean shouldDecorate(HttpServletRequest request) {
        boolean decorate = this.decorators != null;
        if (decorate && this.preventDecoratingParameter != null) {
            decorate = request.getParameter(this.preventDecoratingParameter) == null;
        }
        return decorate;
    }
    
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(this.getClass().getName()).append(":");
        sb.append(" [decorators = ").append(
            (this.decorators != null) ? Arrays.asList(this.decorators) : null);
        sb.append("]");
        return sb.toString();
    }

}