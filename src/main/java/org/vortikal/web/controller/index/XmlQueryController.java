/* Copyright (c) 2006, University of Oslo, Norway
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
package org.vortikal.web.controller.index;

import java.io.OutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repositoryimpl.query.parser.XmlSearcher;
import org.vortikal.security.SecurityContext;
import org.vortikal.web.RequestContext;

import org.w3c.dom.Document;


/**
 * Perform a query via HTTP, get results as XML.
 * 
 * Defaults to only return resources readable by all. Set java bean property
 * authorizeCurrentPrincipal to <code>true</true> to authorize on current 
 * principal instead. 
 */
public class XmlQueryController implements Controller, InitializingBean {
    
    private String expressionParameterName = "query";
    private String limitParameterName = "limit";
    private String sortParameterName = "sort";
    private String fieldsParameterName = "fields";
    private String authorizeParameterName = "authorize";
    private int defaultMaxLimit = 500;
    private XmlSearcher xmlSearcher;
    private Repository repository;
    private boolean authorizeCurrentPrincipal = false;
    
    public void setXmlSearcher(XmlSearcher xmlSearcher) {
        this.xmlSearcher = xmlSearcher;
    }

    public void setExpressionParameterName(String expressionParameterName) {
        this.expressionParameterName = expressionParameterName;
    }

    public void setLimitParameterName(String limitParameterName) {
        this.limitParameterName = limitParameterName;
    }

    public void setSortParameterName(String sortParameterName) {
        this.sortParameterName = sortParameterName;
    }

    public void setFieldsParameterName(String fieldsParameterName) {
        this.fieldsParameterName = fieldsParameterName;
    }
    
    public void setDefaultMaxLimit(int defaultMaxLimit) {
        this.defaultMaxLimit = defaultMaxLimit;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }
    

    public void afterPropertiesSet()
        throws BeanInitializationException {
        if (this.xmlSearcher == null) {
            throw new BeanInitializationException("Property" +
                    " 'xmlSearcher' not set");
        }
        if (this.repository == null) {
            throw new BeanInitializationException(
                "JavaBean property 'repository' not set");
        }
    }
    
    public ModelAndView handleRequest(HttpServletRequest request, 
            HttpServletResponse response) throws Exception {

        // Attempt to retrieve resource
        RequestContext requestContext = RequestContext.getRequestContext();
        SecurityContext securityContext = SecurityContext.getSecurityContext();
        String token = securityContext.getToken();
        String uri = requestContext.getResourceURI();
        Resource resource = this.repository.retrieve(token, uri, true);

        String query = request.getParameter(this.expressionParameterName);
        if (query == null) 
            return null;

        int maxResults = this.defaultMaxLimit;
        String limitStr = request.getParameter(this.limitParameterName);
        if (limitStr != null) {
            try {
                maxResults = Integer.parseInt(limitStr);
            } catch (NumberFormatException e) { }
        }
        String sortStr = request.getParameter(this.sortParameterName);
        String fields = request.getParameter(this.fieldsParameterName);

        boolean authorize = this.authorizeCurrentPrincipal;
        String authorizeParameter = request.getParameter(this.authorizeParameterName);
        if (authorizeParameter != null) {
            authorize = "true".equals(authorizeParameter);
        }

        Document result = this.xmlSearcher.executeDocumentQuery(query, sortStr,
                maxResults, fields, authorize);

        OutputStream outputStream = null;
        response.setContentType("text/xml");
        outputStream = response.getOutputStream();

        // Write response using empty transformation:
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        Source input = new DOMSource(result);
        Result output = new StreamResult(outputStream);
        transformer.transform(input, output);

        return null;
    }

    public void setAuthorizeCurrentPrincipal(boolean authorizeCurrentPrincipal) {
        this.authorizeCurrentPrincipal = authorizeCurrentPrincipal;
    }
    
    
}
