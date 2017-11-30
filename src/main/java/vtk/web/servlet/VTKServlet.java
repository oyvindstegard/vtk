/* Copyright (c) 2004, 2005, 2006, 2007, 2008, University of Oslo, Norway
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
package vtk.web.servlet;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.Filter;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.web.context.support.ServletRequestHandledEvent;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.util.WebUtils;

import vtk.context.ApplicationInitializedEvent;
import vtk.security.AuthenticationException;
import vtk.security.Principal;
import vtk.security.web.SecurityInitializer;
import vtk.util.Version;
import vtk.web.InvalidRequestException;
import vtk.web.RepositoryContextInitializer;
import vtk.web.RequestContext;
import vtk.web.RequestContextInitializer;
import vtk.web.service.Service;


/**
 * Subclass of {@link DispatcherServlet} in the Spring framework.
 * Overrides the <code>doService</code> method to support other
 * request types than <code>GET</code> and <code>POST</code> (support
 * for WebDAV methods, such as <code>PUT</code>,
 * <code>PROPFIND</code>, etc.).
 * 
 * <p>The servlet is also responsible for creating and disposing of
 * the request contexts through the {@link SecurityInitializer},
 * {@link RequestContextInitializer} and the optional 
 * {@link RepositoryContextInitializer} classes.
 *
 * <p>The security initializer takes care of checking requests for
 * authentication credentials, and if present, authenticating users,
 * ultimately creating a {@link
 * vtk.security.SecurityContext} that lasts throughout the
 * request.
 *
 * <p>The request initializer is responsible for creating a {@link
 * RequestContext} that contains the matched {@link Service} for the
 * request, along with the requested resource URI (and the {@link
 * vtk.repository.Resource} that this URI maps to, if it
 * exists and is readable for the current user).
 * 
 * <p>The repository initializer initializes a request scoped holder 
 * used for caching repository access.
 *
 * Two lists of {@link Filter servlet filters} are looked up under the names
 * {@value #INITIALIZING_SERVLET_FILTERS_BEAN_NAME} and 
 * {@value #CONTEXTUAL_SERVLET_FILTERS_BEAN_NAME}, respectively. The first list
 * is invoked before the security context and request context are set up, the 
 * second list is invoked before control is handed to 
 * {@link DispatcherServlet#doService}.
 *
 * <p>The servlet explicitly catches exceptions of type {@link
 * AuthenticationException}, triggering an authentication challenge
 * presentation to the client.
 *
 * <p>Finally, the security context and request context are destroyed.
 */
public class VTKServlet extends DispatcherServlet {

    private static final long serialVersionUID = 3256718498477715769L;

    private static final String SECURITY_INITIALIZER_BEAN_NAME = "securityInitializer";
    private static final String REQUEST_CONTEXT_INITIALIZER_BEAN_NAME = "requestContextInitializer";
    private static final String REPOSITORY_CONTEXT_INITIALIZER_BEAN_NAME = "repositoryContextInitializer";
    private static final String INITIALIZING_SERVLET_FILTERS_BEAN_NAME = "vtk.initializingServletFilters";
    private static final String CONTEXTUAL_SERVLET_FILTERS_BEAN_NAME = "vtk.contextualServletFilters";
    private static final String GLOBAL_HEADERS_BEAN_NAME = "globalHeaders";
    
    public static final String INDEX_FILE_REQUEST_ATTRIBUTE =
        VTKServlet.class.getName() + ".index_file_request";
    
    public static final String SERVLET_NAME_REQUEST_ATTRIBUTE =
        VTKServlet.class.getName() + ".servlet_name";

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    private final Logger requestLogger = LoggerFactory.getLogger(this.getClass().getName() + ".Request");

    // Servlet filters invoked before any contexts are set up:
    private List<Filter> initializingServletFilters = Collections.emptyList();
    // Servlet filters invoked just before super.doService(), with initialized contexts
    private List<Filter> contextualServletFilters = Collections.emptyList();
    private SecurityInitializer securityInitializer;
    private RepositoryContextInitializer repositoryContextInitializer;
    private RequestContextInitializer requestContextInitializer;
    private Map<String,String> globalHeaders = null;
    private final AtomicLong requests = new AtomicLong(0);

    @Override
    public String getServletInfo() {
        return Version.getFrameworkTitle()
            + " - version " + Version.getVersion()
            + ", built " + Version.getBuildDate()
            + " on " + Version.getBuildHost()
            + " - " + Version.getVendorURL();
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        String threadName = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName(config.getServletName());
            this.logger.info(getServletInfo());
            super.init(config);
        } finally {
            Thread.currentThread().setName(threadName);
        }
    }
    
    /**
     * Overridden method, invoked after any bean properties have been set and the
     * WebApplicationContext and BeanFactory for this namespace is available.
     * <p>Delegates to <code>super</code>, then calls 
     * <code>initContextInitializers()</code>.
     */
    @Override
    protected void initFrameworkServlet()
        throws ServletException, BeansException {
        super.initFrameworkServlet();
        initServletFilters();
        initSecurityInitializer();
        initRequestContextInitializer();
        initRepositoryContextInitializer();
        initGlobalHeaders();
        getWebApplicationContext()
            .publishEvent(new ApplicationInitializedEvent(this));
    }
    
    private void initSecurityInitializer() {
        this.securityInitializer = getWebApplicationContext()
            .getBean(SECURITY_INITIALIZER_BEAN_NAME, SecurityInitializer.class);

        this.logger.info("Security initializer set up successfully: " 
                + this.securityInitializer);
    }

    private void initRequestContextInitializer() {
        this.requestContextInitializer = getWebApplicationContext()
                .getBean(REQUEST_CONTEXT_INITIALIZER_BEAN_NAME, 
                        RequestContextInitializer.class);
        
        this.logger.info("Request context initializer " + 
                this.requestContextInitializer + " set up successfully");
    }
    
    private void initRepositoryContextInitializer() {
        try {
            this.repositoryContextInitializer = getWebApplicationContext()
                .getBean(REPOSITORY_CONTEXT_INITIALIZER_BEAN_NAME, 
                         RepositoryContextInitializer.class);
            this.logger.info("Repository context initializer " + 
                    this.repositoryContextInitializer + " set up successfully");
        }
        catch (NoSuchBeanDefinitionException e) {
            // Ok
        }
    }
    
    private void initServletFilters() {
        List<?> filterList = null;
        try {
            filterList = getWebApplicationContext()
                .getBean(INITIALIZING_SERVLET_FILTERS_BEAN_NAME, List.class);
        }
        catch (NoSuchBeanDefinitionException e) { }
        
        if (filterList == null || filterList.size() == 0) {
            this.logger.info("No servlet filters found under name " 
                    + INITIALIZING_SERVLET_FILTERS_BEAN_NAME);
            return;
        }
        List<Filter> result = new ArrayList<>();
        for (Object o: filterList) {
            if (o instanceof FilterFactory) {
                result.add(((FilterFactory) o).filter());
            }
        }
        this.initializingServletFilters = Collections.unmodifiableList(result);
        this.logger.info("Using init servlet filters: " + initializingServletFilters);
        
        try {
            filterList = getWebApplicationContext()
                .getBean(CONTEXTUAL_SERVLET_FILTERS_BEAN_NAME, List.class);
        }
        catch (NoSuchBeanDefinitionException e) { }
        
        if (filterList == null || filterList.size() == 0) {
            this.logger.info("No servlet filters found under name " 
                    + CONTEXTUAL_SERVLET_FILTERS_BEAN_NAME);
            return;
        }
        result = new ArrayList<>();
        for (Object o: filterList) {
            if (o instanceof FilterFactory) {
                result.add(((FilterFactory) o).filter());
            }
        }
        this.contextualServletFilters = Collections.unmodifiableList(result);
        this.logger.info("Using contextual servlet filters: " + contextualServletFilters);
    }

    private void initGlobalHeaders() {
        try { 
            Map<?,?> headers = 
                    getWebApplicationContext().getBean(GLOBAL_HEADERS_BEAN_NAME, Map.class);
            this.globalHeaders = new LinkedHashMap<>();
            for (Object key: headers.keySet()) {                
                if (key != null && !"".equals(key.toString().trim())) {
                    Object o = headers.get(key);
                    if (o != null && !"".equals(o.toString().trim())) {
                        String headerName = key.toString().trim();
                        String headerValue = o.toString().trim();
                        this.globalHeaders.put(headerName, headerValue);
                    }
                }
            }
        }
        catch (NoSuchBeanDefinitionException e) { }
    }
    
   @Override
   protected void service(HttpServletRequest request, HttpServletResponse response) 
           throws ServletException, IOException {
       FilterChain chain = new FilterChain("InitializingFilterChain", 
               initializingServletFilters, (req, resp) -> {
           try {
               serviceInternal(req, resp);   
           }
           catch (AuthenticationException e) {
               throw e;
           }
           catch (DispatchException e) {
               throw e;
           }
           catch (Exception e) {
               throw new DispatchException(e);
           }
       });
       try {
           chain.doFilter(request,  response);
       }
       catch (DispatchException e) {
           Throwable cause = (e.getCause() != null) ? e.getCause() : e; 
           throw new ServletException(cause);
       }
   }


   protected void serviceInternal(HttpServletRequest request, HttpServletResponse response) 
           throws IOException, ServletException {
       long startTime = System.currentTimeMillis();
       Throwable failureCause = null;
       
       String threadName = Thread.currentThread().getName();
       long number = this.requests.incrementAndGet();

       response = new HeaderAwareResponseWrapper(response);

       try {

           if (this.globalHeaders != null) {
               for (String header: this.globalHeaders.keySet()) {
                   response.setHeader(header, this.globalHeaders.get(header));
               }
           }
           request.setAttribute(SERVLET_NAME_REQUEST_ATTRIBUTE, getServletName());
           Thread.currentThread().setName(this.getServletName() + "." + String.valueOf(number));

           if (this.repositoryContextInitializer != null) {
               this.repositoryContextInitializer.createContext(request);
           }

           if (!this.securityInitializer.createContext(request, response)) {
               if (this.logger.isDebugEnabled()) {
                   this.logger.debug("Request " + request + " handled by " + 
                           "security initializer (authentication challenge)");
               }
               return;
           }

           try {
               this.requestContextInitializer.createContext(request);
           }
           catch (InvalidRequestException e) {
               response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
               return;
           }
           response = new HeaderAwareResponseWrapper(response);
           FilterChain chain = new FilterChain("ContextualFilterChain", 
                   contextualServletFilters, (req, resp) -> {
                       try {
                           super.doService(req, resp);
                       }
                       catch (AuthenticationException e) {
                           throw e;
                       }
                       catch (Exception e) {
                           throw new DispatchException(e);
                       }
                   });
           chain.doFilter(request,  response);
       }
       catch (AuthenticationException ex) {
           this.securityInitializer.challenge(request, response, ex);
       }
       finally {
           long processingTime = System.currentTimeMillis() - startTime;

           if (request.getAttribute(INDEX_FILE_REQUEST_ATTRIBUTE) == null) {
               logRequest(request, response, processingTime);
               getWebApplicationContext().publishEvent(
                       new ServletRequestHandledEvent(this, request
                               .getRequestURI(), request.getRemoteAddr(),
                               request.getMethod(), getServletConfig()
                               .getServletName(), WebUtils
                               .getSessionId(request),
                               getUsernameForRequest(request), processingTime,
                               failureCause));
           }

           this.securityInitializer.destroyContext(request);
           this.requestContextInitializer.destroyContext(request);
           this.repositoryContextInitializer.destroyContext(request);
           Thread.currentThread().setName(threadName);
       }
   }
   
    private void logRequest(HttpServletRequest req, HttpServletResponse resp,
                            long processingTime) {
        if (!this.requestLogger.isInfoEnabled()) {
            return;
        }
        String remoteHost = req.getRemoteHost();
        String requestURL = req.getRequestURL() + 
                (req.getQueryString() != null ? req.getQueryString() : "");


        String request = req.getMethod() + " " + requestURL + " "
            + req.getProtocol() + " - status: " + resp.getStatus();

        // Request context may not be available depending on when service handling returns, which
        // can be before any contexts have been created
        RequestContext requestContext = RequestContext.getRequestContext(req);
        Principal principal = null;
        String token = null;
        String service = null;
        if (requestContext != null) {
            principal = requestContext.getPrincipal();
            token = requestContext.getSecurityToken();
            if (requestContext.getService() != null) {
                service = requestContext.getService().getName();
            }
        }

        String userAgent = req.getHeader("User-Agent");
        String sessionID = null;
        HttpSession session = req.getSession(false);
        if (session != null) {
            sessionID = session.getId(); 
        }
        StringBuilder msg = new StringBuilder();
        msg.append(remoteHost).append(" - ").append(request);
        msg.append(" - principal: ").append(principal);
        msg.append(" - token: ").append(token);
        msg.append(" - session: ").append(sessionID);
        msg.append(" - service: ").append(service);
        msg.append(" - user-agent: ").append(userAgent);
        msg.append(" - referrer: ").append(req.getHeader("Referer"));
        msg.append(" - bytes: ").append(resp.getHeader("Content-Length"));
        msg.append(" - time: ").append(processingTime);
        this.requestLogger.info(msg.toString());
    }
    
}
