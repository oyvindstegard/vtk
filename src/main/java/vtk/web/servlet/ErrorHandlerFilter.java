/* Copyright (c) 2017, University of Oslo, Norway
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

import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.support.RequestContextUtils;

import vtk.security.AuthenticationException;
import vtk.security.SecurityContext;
import vtk.web.ErrorHandler;
import vtk.web.RequestContext;
import vtk.web.service.Service;

public class ErrorHandlerFilter extends AbstractServletFilter {
    private static Logger logger = LoggerFactory.getLogger(ErrorHandlerFilter.class);
    private static Logger errorLogger = LoggerFactory.getLogger(ErrorHandlerFilter.class.getName() + ".Error");
    private List<ErrorHandler> errorHandlers;
    private ViewResolver viewResolver;
    
    public ErrorHandlerFilter(List<ErrorHandler> errorHandlers, ViewResolver viewResolver) {
        this.errorHandlers = new ArrayList<>(errorHandlers);
        this.viewResolver = viewResolver;
    }

    @Override
    protected void doFilter(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        }
        catch (AuthenticationException e) {
            // Until SecurityInitializer is a filter:
            throw e;
        }
        catch (Throwable t) {
            handleError(request, response, t);
        }
    }

    
    private boolean isClientDisconnect(Throwable t) {
        if (t instanceof UncheckedIOException) {
            t = ((UncheckedIOException) t).getCause();
        }
        if (t instanceof EOFException || 
                (t.getCause() != null && (t.getCause() instanceof TimeoutException))) {
            return true;
        }
        return false;
    }

    private void handleError(HttpServletRequest req, HttpServletResponse resp,
            Throwable t) throws ServletException {
        if (t instanceof DispatchException) {
            t = ((DispatchException) t).getCause();
        }
        if (isClientDisconnect(t)) {
            logger.info("Client disconnect: " + req.getRequestURI() 
            + ": " + t.getMessage());
            return;
        }

        WebApplicationContext springContext = null;
        try {
            springContext = RequestContextUtils.findWebApplicationContext(req);
        }
        catch (Throwable x) { }

        if (springContext == null) {
            // When no Spring WebApplicationContext is found, we
            // cannot trust our regular error handling mechanism to
            // work. In most cases this is caused by the request
            // context initialization failing due to the content
            // repository being unavailable for some reason (i.e. JDBC
            // errors, etc.). The safest thing to do here is to log
            // the error and throw a ServletException and let the
            // container handle it.
            if (logger.isDebugEnabled()) {
                logger.debug("Caught unexpected throwable " + t.getClass()
                + " with no Spring context available, "
                + "logging as internal server error");
            }
            logError(req, t);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            throw new ServletException(t);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Caught unexpected throwable " + t.getClass()
            + ", resolving error handler");
        }
        ErrorHandler handler = resolveErrorHandler(t);
        if (handler == null) {
            logError("No error handler configured for " + t.getClass().getName(), req, t);
            throw new ServletException(t);
        } 

        int statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        Map<String, Object> model = null;
        View view = null;
        try {
            model = handler.getErrorModel(req, resp, t);

            Object obj = handler.getErrorView(req, resp, t);
            if (obj instanceof View) {
                view = (View) obj;
            }
            else {
                Locale locale = RequestContextUtils.getLocale(req);
                view = viewResolver.resolveViewName((String) obj, locale);
            }

            statusCode = handler.getHttpStatusCode(req, resp, t);
        }
        catch (Throwable errorHandlerException) {
            //errorHandlerException.initCause(t);
            logError("Caught exception while performing error handling: " 
                    + errorHandlerException, req, t);
            throw new ServletException(errorHandlerException);
        }

        if (view == null) {
            throw new ServletException("Unable to resolve error view for handler "
                    + handler, t);
        }

        // Logger '500 internal server error' incidents to the error log:
        if (statusCode == HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            logError(req, t);
        }
        if (resp.isCommitted()) {
            logger.debug(
                    "Response already committed for request " + req.getRequestURL() 
                    + ", not invoking error handler ");
            return;
        }

        resp.setStatus(statusCode);
        try {

            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Performed error handling using handler " + handler + ". "
                                + "Status code is " + statusCode + "." + " Will render "
                                + "error model using view " + view);
            }
            view.render(model, req, resp);

        }
        catch (Exception e) {
            try {
                e.initCause(t);
            }
            catch (Throwable unhandled) { }
            throw new ServletException("Error while rendering error view", e);
        }
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
    

    private ErrorHandler resolveErrorHandler(Throwable t) {
        Service currentService = null;

        if (RequestContext.exists()) {
            RequestContext requestContext = RequestContext.getRequestContext();
            if (requestContext != null) {
                currentService = requestContext.getService();
            }
        }
        ErrorHandler selected = null;

        for (ErrorHandler candidate: errorHandlers) {
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

            }
            else if (!selected.getErrorType().equals(candidate.getErrorType())
                       && selected.getErrorType().isAssignableFrom(
                           candidate.getErrorType())) {                
                selected = candidate;
            }
            else if (candidate.getService() != null && selected.getService() == null) {                
                selected = candidate;
            }
            else if (candidate.getService() != null && selected.getService() != null
                       && candidate.getService().isDescendantOf(selected.getService())) {
                selected = candidate;
            }

            if (selected != null && selected == candidate) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Setting new currently matched error handler: "
                                 + candidate);
                }
            }
        }
        return selected;
    }

    /**
     * Logs an error to the error log with a given error message.
     *
     * @param message the message to output
     * @param req the servlet request
     * @param t the error to log
     */
    @SuppressWarnings("rawtypes")
    private void logError(String message, HttpServletRequest req, Throwable t) {
        Optional<RequestContext> requestContext = Optional.empty();
        if (RequestContext.exists()) {
            requestContext = Optional.of(RequestContext.getRequestContext());
        }
        Optional<SecurityContext> securityContext = Optional.empty();
        if (SecurityContext.exists()) {
            securityContext = Optional.of(SecurityContext.getSecurityContext());
        }
        String httpMethod = req.getMethod();

        Map requestParameters = req.getParameterMap();
        StringBuilder params = new StringBuilder("{");
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

        StringBuilder sb = new StringBuilder();
        if (message != null) sb.append(message).append(" ");
        sb.append("Message: ").append(t.getMessage()).append(" - ");
        sb.append("Full request URL: [").append(requestURL).append("], ");
        requestContext.ifPresent(ctx -> sb.append("Request context: [").append(ctx).append("], "));
        securityContext.ifPresent(ctx -> sb.append("security context: [").append(ctx).append("], "));
        sb.append("method: [").append(httpMethod).append("], ");
        sb.append("request parameters: [").append(params).append("], ");
        sb.append("user agent: [").append(req.getHeader("User-Agent")).append("], ");
        sb.append("host: [").append(req.getServerName()).append("], ");
        sb.append("remote host: [").append(req.getRemoteHost()).append("]");
        this.errorLogger.error(sb.toString(), t);
    }
}
