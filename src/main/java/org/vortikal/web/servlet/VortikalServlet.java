/* Copyright (c) 2004, University of Oslo, Norway
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
package org.vortikal.web.servlet;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.ServletRequestHandledEvent;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.util.WebUtils;
import org.vortikal.context.BaseContext;
import org.vortikal.security.AuthenticationException;
import org.vortikal.security.AuthenticationProcessingException;
import org.vortikal.security.Principal;
import org.vortikal.security.SecurityContext;
import org.vortikal.security.web.AuthenticationChallenge;
import org.vortikal.security.web.InvalidAuthenticationRequestException;
import org.vortikal.security.web.SecurityInitializer;
import org.vortikal.util.Version;
import org.vortikal.util.web.URLUtil;
import org.vortikal.web.ErrorHandler;
import org.vortikal.web.RepositoryContextInitializer;
import org.vortikal.web.RequestContext;
import org.vortikal.web.RequestContextInitializer;
import org.vortikal.web.filter.RequestFilter;
import org.vortikal.web.service.Service;


/**
 * Subclass of {@link DispatcherServlet} in the Spring framework.
 * Overrides the <code>doService</code> method to support other
 * request types than <code>GET</code> and <code>POST</code> (support
 * for WebDAV methods, such as <code>PUT</code>,
 * <code>PROPFIND</code>, etc.).
 *
 * <p>If a {@link RequestFilter} is configured in the bean context
 * under the name <code>requestFilter</code>, it is invoked first on
 * every request. If no such bean is configured, the standard {@link
 * StandardRequestFilter} is invoked.
 *
 * <p>The servlet is also responsible for creating and disposing of
 * the request contexts through the {@link SecurityInitializer},
 * {@link RequestContextInitializer} and the optional 
 * {@link RepositoryContextInitializer} classes.
 *
 * <p>The security innitializer takes care of checking requests for
 * authentication credentials, and if present, authenticating users,
 * ultimately creating a {@link
 * org.vortikal.security.SecurityContext} that lasts throughout the
 * request.
 *
 * <p>The request initializer is responsible for creating a {@link
 * RequestContext} that contains the matched {@link Service} for the
 * request, along with the requested resource URI (and the {@link
 * org.vortikal.repository.Resource} that this URI maps to, if it
 * exists and is readable for the current user).
 * 
 * <p>The repository initializer initializes a request scoped holder 
 * used for caching repository access.
 *
 * <p>After context initialization, the servlet calls
 * <code>super.doService()</code>, in the standard DispatcherServlet
 * fashion. It explicitly catches exceptions of type {@link
 * AuthenticationException}, triggering an authentication challenge
 * presentation to the client.
 *
 * <p>Finally, the security context and request context are destroyed.
 */
public class VortikalServlet extends DispatcherServlet {

    private static final long serialVersionUID = 3256718498477715769L;

    /**	Method name for GET request */
    private static final String METHOD_GET = "GET";

    /**	Method name for HEAD request */
    private static final String METHOD_HEAD = "HEAD";    
    
    /** Header parameter asking for service if modified since the given date */
    private static final String HEADER_IFMODSINCE = "If-Modified-Since";    
    
    /** Response header attribute telling when the data was last modified */
    private static final String HEADER_LASTMOD = "Last-Modified";
    
    private static final String SECURITY_INITIALIZER_BEAN_NAME = "securityInitializer";
    private static final String REQUEST_CONTEXT_INITIALIZER_BEAN_NAME = "requestContextInitializer";
    private static final String REPOSITORY_CONTEXT_INITIALIZER_BEAN_NAME = "repositoryContextInitializer";
    private static final String REQUEST_FILTERS_BEAN_NAME = "defaultRequestFilters";
    
    public static final String INDEX_FILE_REQUEST_ATTRIBUTE =
        VortikalServlet.class.getName() + ".index_file_request";
    
    public static final String SERVLET_NAME_REQUEST_ATTRIBUTE =
        VortikalServlet.class.getName() + ".servlet_name";
    

    
    private Log logger = LogFactory.getLog(this.getClass().getName());
    private Log requestLogger = LogFactory.getLog(this.getClass().getName() + ".Request");
    private Log errorLogger = LogFactory.getLog(this.getClass().getName() + ".Error");

    private RequestFilter[] requestFilters = new RequestFilter[0];
    private SecurityInitializer securityInitializer;
    private RepositoryContextInitializer repositoryContextInitializer;
    private RequestContextInitializer requestContextInitializer;
    private ErrorHandler[] errorHandlers = new ErrorHandler[0];
    private long requests = 0;
    
    public String getServletInfo() {
        return Version.getFrameworkTitle()
            + " - version " + Version.getVersion()
            + ", built " + Version.getBuildDate()
            + " on " + Version.getBuildHost()
            + " - " + Version.getVendorURL();
    }

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
    protected void initFrameworkServlet()
        throws ServletException, BeansException {
        super.initFrameworkServlet();
        initRequestFilters();
        initSecurityInitializer();
        initRequestContextInitializer();
        initRepositoryContextInitializer();
        initErrorHandlers();
    }
    
    

    private void initSecurityInitializer() {
        this.securityInitializer = (SecurityInitializer)
            getWebApplicationContext().getBean(SECURITY_INITIALIZER_BEAN_NAME, SecurityInitializer.class);

        this.logger.info("Security initializer set up successfully: " + this.securityInitializer);
    }


    private void initRequestContextInitializer() {
        this.requestContextInitializer = (RequestContextInitializer) 
            getWebApplicationContext().getBean(REQUEST_CONTEXT_INITIALIZER_BEAN_NAME, RequestContextInitializer.class);
        
        this.logger.info("Request context initializer " + 
                this.requestContextInitializer + " set up successfully");
    }
    
    
    private void initRepositoryContextInitializer() {
        try {
            this.repositoryContextInitializer = (RepositoryContextInitializer) 
                getWebApplicationContext().getBean(REPOSITORY_CONTEXT_INITIALIZER_BEAN_NAME, 
                        RepositoryContextInitializer.class);
            this.logger.info("Repository context initializer " + 
                    this.repositoryContextInitializer + " set up successfully");
        } catch (NoSuchBeanDefinitionException e) {
            // Ok
        }
    }
    
    


    private void initRequestFilters() {
        RequestFilter[] filterArray = 
            (RequestFilter[]) getWebApplicationContext().getBean(REQUEST_FILTERS_BEAN_NAME, RequestFilter[].class);
        if (filterArray == null || filterArray.length == 0) {
            this.logger.info("No request filters found under name " + REQUEST_FILTERS_BEAN_NAME);
        }
        
        this.requestFilters = filterArray;
        this.logger.info("Request filters: " + filterArray + " set up successfully");
    }
    

    private void initErrorHandlers() {
        Map handlers = getWebApplicationContext().getBeansOfType(
            ErrorHandler.class, false, false);

        if (handlers.size() > 0) {
            this.errorHandlers = new ErrorHandler[handlers.size()];
            int j = 0;
            for (Iterator i = handlers.entrySet().iterator(); i.hasNext();) {
                Map.Entry entry = (Map.Entry) i.next();
                this.errorHandlers[j] = (ErrorHandler) entry.getValue();
                this.logger.info("Registered error handler " + this.errorHandlers[j]);
                j++;
            }
        }
    }
    
    
    /**
     * This method duplicates the HTTP last modified checking done in
     * the servlet specs implementation of service() in
     * HttpServlet. We are overriding service(), so we need to supply
     * this functionality ourselves.
     * 
     * FIXME: Don't check lastModified if HEADER_IFMODSINCE isn't set
     *
     * @return <code>true</code> if the request should continue, or
     * <code>false</code> if a "304 Not Modified" status has been sent
     * to the client and the request processing should stop.
     */
    private boolean checkLastModified(HttpServletRequest request,
                                      HttpServletResponse response) {
        
        // Last modified checking:
        String method = request.getMethod();

        if (method.equals(METHOD_GET)) {
            long lastModified = getLastModified(request);
            if (lastModified != -1) {
                long ifModifiedSince = -1;
                try {
                    ifModifiedSince = request.getDateHeader(HEADER_IFMODSINCE);
                } catch (IllegalArgumentException e) {
                    // The client is sending an illegal header format, so we ignore it.
                    ifModifiedSince = -1;
                }
                
                if (ifModifiedSince < (lastModified / 1000 * 1000)) {
                    // If the servlet mod time is later, call doGet()
                    // Round down to the nearest second for a proper compare
                    // A ifModifiedSince of -1 will always be less
                    maybeSetLastModified(response, lastModified);
                } else {
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    if (this.logger.isDebugEnabled()) {
                        this.logger.debug("Skipping service of "
                        + request.getRequestURI()
                        + " because content didn't change since last request");
                    }
                    return false;
                }
            }
        } else if (method.equals(METHOD_HEAD)) {
            long lastModified = getLastModified(request);
            maybeSetLastModified(response, lastModified);
        }

        return true;
    }
    
    /**
     * Overriding service to be able to handle dav requests,
     * duplicating Spring FrameworkServlet's serviceWrapper
     * funcionality, and duplicating httpservlet's handling of
     * lastModified in service.  Handle this request, publishing an
     * event regardless of the outcome.  The actual event handling is
     * performed by the doService() method in DispatcherServlet.
     * 
     */
    protected void service(HttpServletRequest request,
                                 HttpServletResponse response) 
        throws ServletException {

        StatusAwareResponseWrapper responseWrapper = new StatusAwareResponseWrapper(response);
        long startTime = System.currentTimeMillis();
        Throwable failureCause = null;

        String threadName = Thread.currentThread().getName();
        long number = 0;

        synchronized(this) {
            this.requests++;
            number = this.requests;
        }

        boolean proceedService = true;

        try {

            request.setAttribute(SERVLET_NAME_REQUEST_ATTRIBUTE, getServletName());
            BaseContext.pushContext();
            Thread.currentThread().setName(this.getServletName() + "." + String.valueOf(number));

            request = filterRequest(request);

            if (this.repositoryContextInitializer != null) {
                this.repositoryContextInitializer.createContext(request);
            }
            
            if (!this.securityInitializer.createContext(request, responseWrapper)) {
                if (this.logger.isDebugEnabled()) {
                    this.logger.debug("Request " + request + " handled by " + 
                            "security initializer (authentication challenge)");
                }
                return;
            }

            this.requestContextInitializer.createContext(request);

            proceedService = checkLastModified(request, responseWrapper);

            if (proceedService) {
                super.doService(request, responseWrapper);
            }

        } catch (AuthenticationException ex) {
            authenticationChallenge(request, responseWrapper, ex);
        } catch (AuthenticationProcessingException e) {
            handleAuthenticationProcessingError(request, responseWrapper, e);
        } catch (InvalidAuthenticationRequestException e) {
            responseWrapper.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            logError(request, e);
        } catch (Throwable t) {
            if (HttpServletResponse.SC_OK == responseWrapper.getStatus()) {
                responseWrapper.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            failureCause = t;
            handleError(request, responseWrapper, t);
        } finally {

            long processingTime = System.currentTimeMillis() - startTime;

            if (request.getAttribute(INDEX_FILE_REQUEST_ATTRIBUTE) == null) {
                logRequest(request, responseWrapper, processingTime, !proceedService);
                getWebApplicationContext().publishEvent(
                        new ServletRequestHandledEvent(this, request
                                .getRequestURI(), request.getRemoteAddr(),
                                request.getMethod(), getServletConfig()
                                        .getServletName(), WebUtils
                                        .getSessionId(request),
                                getUsernameForRequest(request), processingTime,
                                failureCause));
            }

            this.securityInitializer.destroyContext();
            this.requestContextInitializer.destroyContext();
            Thread.currentThread().setName(threadName);
            BaseContext.popContext();
        }
    }

    private void handleAuthenticationProcessingError(HttpServletRequest request, StatusAwareResponseWrapper responseWrapper, AuthenticationProcessingException e) throws ServletException {
        if (HttpServletResponse.SC_OK == responseWrapper.getStatus()) {
            responseWrapper.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        logError(request, e);
        throw new ServletException("Fatal processing error while " +
                "performing authentication", e);
    }

    private HttpServletRequest filterRequest(HttpServletRequest request) {
        for (int i = 0; i < this.requestFilters.length; i++) {
            RequestFilter filter = this.requestFilters[i];
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Running request filter: " + filter);
            }
            request = filter.filterRequest(request);
        }
        
        return request;
    }

    private void authenticationChallenge(HttpServletRequest request, HttpServletResponse response, 
            AuthenticationException ex) throws ServletException {
        Service service = RequestContext.getRequestContext()
                .getService();
        AuthenticationChallenge challenge = getAuthenticationChallenge(service);

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Authentication required for request "
                         + request + ", service " + service + ". "
                         + "Using challenge " + challenge, ex);
        }
        if (challenge == null) {
            throw new ServletException(
                "Authentication challenge for service " + service
                + " (or any of its ancestors) is not specified.");
        }

        try {
            challenge.challenge(request, response);
        } catch (AuthenticationProcessingException e) {
            logError(request, e);
            throw new ServletException(
                "Fatal processing error while performing " +
                "authentication challenge", e);
        }
    }

        
    /**
     * Sets the Last-Modified entity header field, if it has not
     * already been set and if the value is meaningful. Called before
     * doGet, to ensure that headers are set before response data is
     * written. A subclass might have set this header already, so we
     * check.
     * 
     * @param resp Response on which we can set the last modification date
     * @param lastModified The date at which the data has been last changed
     */
    private void maybeSetLastModified(HttpServletResponse resp,
				      long lastModified) {
        if (resp.containsHeader(HEADER_LASTMOD))
            return;
        if (lastModified >= 0)
            resp.setDateHeader(HEADER_LASTMOD, lastModified);
    }
   
	
    private AuthenticationChallenge getAuthenticationChallenge(Service service) {
        AuthenticationChallenge challenge = service.getAuthenticationChallenge();
        
        if (challenge == null && service.getParent() != null) 
            return getAuthenticationChallenge(service.getParent());
        return challenge;
    }



    private void logRequest(HttpServletRequest req, StatusAwareResponseWrapper resp,
                            long processingTime, boolean wasCacheRequest) {

        if (!this.requestLogger.isInfoEnabled()) {
            return;
        }

        SecurityContext securityContext = SecurityContext.getSecurityContext();
        RequestContext requestContext = RequestContext.getRequestContext();

        String remoteHost = req.getRemoteHost();

        String requestURI = req.getRequestURI();
        String queryString = req.getQueryString();
        if (queryString != null) {
            requestURI += ("?"  + queryString);
        }

        String request = req.getMethod() + " " + requestURI + " "
            + req.getProtocol() + " - status: " + resp.getStatus();

        Principal principal = null;
        String token = null;
        
        if (securityContext != null && securityContext.getPrincipal() != null) {
            token = securityContext.getToken();
            principal = securityContext.getPrincipal();
        }

        String service = null;
        if (requestContext != null && requestContext.getService() != null) {
            service = requestContext.getService().getName();
        }

        String userAgent = req.getHeader("User-Agent");
        String jSession = null;
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (int i = 0; i < cookies.length; i++) {
                if (cookies[i].getName().equals("JSESSIONID")) {
                    jSession = cookies[i].getValue();
                    break;
                }
            }
        }
        StringBuffer msg = new StringBuffer();
        msg.append(remoteHost).append(" - ").append(request);
        msg.append(" - principal: ").append(principal);
        msg.append(" - token: ").append(token);
        msg.append(" - jsession: ").append(jSession);
        msg.append(" - service: ").append(service);
        msg.append(" - user agent: ").append(userAgent);
        msg.append(" - cached: ").append(wasCacheRequest);
        msg.append(" - time: ").append(processingTime);
        this.requestLogger.info(msg);
    }
    


    /**
     * Logs an error to the error log with no message.
     *
     * @param req the servlet request
     * @param t the error to log
     */
    private void logError(HttpServletRequest req, Throwable t) {
        logError(null, req, t);
    }
    


    /**
     * Logs an error to the error log with a given error message.
     *
     * @param message the message to output
     * @param req the servlet request
     * @param t the error to log
     */
    private void logError(String message, HttpServletRequest req, Throwable t) {
            
        RequestContext requestContext = RequestContext.getRequestContext();
        SecurityContext securityContext = SecurityContext.getSecurityContext();
        String httpMethod = req.getMethod();

        Map requestParameters = req.getParameterMap();
        StringBuffer params = new StringBuffer("{");
        for (Iterator iter = requestParameters.keySet().iterator(); iter.hasNext();) {
            String name = (String) iter.next();
            params.append(name).append("=[");
            String[] values = req.getParameterValues(name);
            for (int i = 0; i < values.length; i++) {
                params.append(values[i]);
                if (i < values.length - 1) {
                    params.append(",");
                }
            }
            params.append("]");
            if (iter.hasNext()) {
                params.append(",");
            }
        }
        params.append("}");
            
        StringBuffer requestURL = req.getRequestURL();
        String queryString = req.getQueryString();
        if (queryString != null) {
            requestURL.append("?").append(queryString);
        }


        StringBuffer sb = new StringBuffer();
        if (message != null) sb.append(message).append(" ");
        sb.append("Message: ").append(t.getMessage()).append(" - ");
        sb.append("Full request URL: [").append(requestURL).append("], ");
        sb.append("Request context: [").append(requestContext).append("], ");
        sb.append("security context: [").append(securityContext).append("], ");
        sb.append("method: [").append(httpMethod).append("], ");
        sb.append("request parameters: [").append(params).append("], ");
        sb.append("user agent: [").append(req.getHeader("User-Agent")).append("], ");
        sb.append("host: [").append(URLUtil.getHostName(req)).append("], ");
        sb.append("remote host: [").append(req.getRemoteHost()).append("]");

        this.errorLogger.error(sb.toString(), t);
    }
    


    /**
     * Handles an error that occurred during request processing. An
     * error handler is looked up based on the throwable's class. If
     * the error handler sets the response status to <code>500</code>
     * (Internal Server Error), the incident is logged to the error
     * log.
     *
     * @param req the servlet request
     * @param resp the servlet response
     * @param t the error to handle
     * @exception ServletException if an error occurs during error
     * handling
     */
    private void handleError(HttpServletRequest req, HttpServletResponse resp,
                             Throwable t) throws ServletException {

        WebApplicationContext springContext = null;
        try {
            springContext = RequestContextUtils.getWebApplicationContext(req);
        } catch (Throwable x) { }

        if (springContext == null) {
            // When no Spring WebApplicationContext is found, we
            // cannot trust our regular error handling mechanism to
            // work. In most cases this is caused by the request
            // context initialization failing due to the content
            // repository being unavailable for some reason (i.e. JDBC
            // errors, etc.). The safest thing to do here is to log
            // the error and throw a ServletException and let the
            // container handle it.
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Caught unexpected throwable " + t.getClass()
                             + " with no Spring context available, "
                             + "logging as internal server error");
            }

            logError(req, t);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            throw new ServletException(t);
        }

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Caught unexpected throwable " + t.getClass()
                         + ", resolving error handler");
        }
        ErrorHandler handler = resolveErrorHandler(t);
        if (handler == null) {
            logError("No error handler configured for " + t.getClass().getName(), req, t);
            throw new ServletException(t);
        } 

        int statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        Map model = null;
        View view = null;
        try {
            model = handler.getErrorModel(req, resp, t);

            Object obj = handler.getErrorView(req, resp, t);
            if (obj instanceof View) {
                view = (View) obj;
            } else {
                Locale locale = RequestContextUtils.getLocale(req);
                view = this.resolveViewName((String) obj, model, locale, req);
            }

            statusCode = handler.getHttpStatusCode(req, resp, t);

        } catch (Throwable errorHandlerException) {
            errorHandlerException.initCause(t);
            logError("Caught exception while performing error handling",
                     req, errorHandlerException);
            throw new ServletException(errorHandlerException);
        }

        if (view == null) {
            throw new ServletException("Unable to resolve error view for handler "
                                       + handler, t);
        }


        // Log '500 internal server error' incidents to the error log:
        if (statusCode == HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            logError(req, t);
        }

        resp.setStatus(statusCode);
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0");        
        try {

            if (this.logger.isDebugEnabled()) {
                this.logger.debug(
                    "Performed error handling using handler " + handler + ". "
                    + "Status code is " + statusCode + "." + " Will render "
                    + "error model using view " + view);
            }
            view.render(model, req, resp);
            
        } catch (Exception e) {
            e.initCause(t);
            throw new ServletException("Error while rendering error view", e);
        }
    }
    
    
    /**
     * Resolves an error handler based on the throwable's class and
     * the current service.
     * 
     * @param t the error to resolve an error handler for
     * @return an error handler, or <code>null</code> if no error
     * handler could be resolved.
     */
    private ErrorHandler resolveErrorHandler(Throwable t) {
        Service currentService = null;
        RequestContext requestContext = RequestContext.getRequestContext();
        if (requestContext != null) {
            currentService = requestContext.getService();
        }

        ErrorHandler selected = null;

        for (int i = 0; i < this.errorHandlers.length; i++) {

            ErrorHandler candidate = this.errorHandlers[i];

            if (!candidate.getErrorType().isAssignableFrom(t.getClass())) {
                continue;
            }
            
            if ((candidate.getService() != null && currentService == null)
                || (candidate.getService() != null && currentService != null
                    && !(currentService == candidate.getService() ||
                         currentService.isDescendantOf(candidate.getService())))) {

                continue;
            }

            if (selected == null) {
                
                selected = candidate;

            } else if (!selected.getErrorType().equals(candidate.getErrorType())
                       && selected.getErrorType().isAssignableFrom(
                           candidate.getErrorType())) {
                
                selected = candidate;

            } else if (candidate.getService() != null && selected.getService() == null) {
                
                selected = candidate;

            } else if (candidate.getService() != null && selected.getService() != null
                       && candidate.getService().isDescendantOf(selected.getService())) {

                selected = candidate;
            }

            if (selected != null && selected == candidate) {
                if (this.logger.isDebugEnabled()) {
                    this.logger.debug("Setting new currently matched error handler: "
                                 + candidate);
                }
            }
        }

        return selected;
    }

    
}
