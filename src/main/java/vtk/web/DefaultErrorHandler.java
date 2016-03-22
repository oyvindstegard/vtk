/* Copyright (c) 2004, 2008, University of Oslo, Norway
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
package vtk.web;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.servlet.View;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.service.Service;


/**
 * Default error handler.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>errorViewName</code> - a view name, used to resolve an
 *   error {@link View} for rendering the error model
 *   <li><code>errorView</code> - a {@link View} for rendering the
 *   error model (overrides <code>errorViewName</code>)
 *   <li><code>errorType</code> - a {@link Throwable} deciding the
 *   type(s) of errors that are handled by this class
 *   <li><code>service</code> - the {@link Service} (if any) for which
 *   this error handler is applicable
 *   <li><code>referenceDataProviders</code> - a list of {@link
 *   ReferenceDataProvider} objects, invoked on the error model. Note:
 *   some (or all) of these may crash during invocation, so this list
 *   should be a conservative set of reference data providers that
 *   should at least not be operating on the repository.
 *   <li><code>statusCodeMappings</code> - a {@link Map} that maps
 *   between error class names and HTTP status codes,
 *   e.g. <code>java.lang.Throwable --&gt; 500</code> (internal server
 *   error).
 * </ul>
 *
 * <p>Model data provided:
 * <ul>
 *   <li><code>exception</code> - a {@link Throwable} - the error
 *   that occurred
 *   <li><code>errorDescription</code> - a localized error description
 * </ul>
 *
 */
public class DefaultErrorHandler
  implements ErrorHandler, BeanNameAware, InitializingBean {
    
    private static Logger logger = LoggerFactory.getLogger(DefaultErrorHandler.class);
    
    public static final String DEFAULT_ERROR_CODE = "error.default";
    public static final String DEFAULT_ERROR_DESCRIPTION = "Internal server error";

    public static final String ERROR_MODEL_KEY = "error";
    public static final String ERROR_MODEL_EXCEPTION_KEY = "exception";
    public static final String ERROR_MODEL_ERROR_DESCRIPTION_KEY = "errorDescription";

    private String beanName = null;
    private View errorView = null;
    private String errorViewName = null;
    private Class<Throwable> errorType = Throwable.class;
    private Service service = null;
    private ReferenceDataProvider[] providers = new ReferenceDataProvider[0];
    private Map<String, Integer> statusCodeMappings = new HashMap<String, Integer>();
    private boolean logExceptions = false;

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public void setErrorView(View errorView) {
        this.errorView = errorView;
    }

    public void setErrorViewName(String errorViewName) {
        this.errorViewName = errorViewName;
    }

    public void setErrorType(Class<Throwable> errorType) {
        this.errorType = errorType;
    }

    @Override
    public Class<Throwable> getErrorType() {
        return this.errorType;
    }
    
    public void setService(Service service) {
        this.service = service;
    }

    @Override
    public Service getService() {
        return this.service;
    }
    
    public void setReferenceDataProviders(ReferenceDataProvider[] providers) {
        this.providers = providers;
    }
    
    public void setStatusCodeMappings(Map<String, Integer> statusCodeMappings) {
        this.statusCodeMappings = statusCodeMappings;
    }
    
    public void setLogExceptions(boolean logExceptions) {
        this.logExceptions = logExceptions;
    }

    public void afterPropertiesSet() {
        if (this.errorView == null && this.errorViewName == null) {
            throw new BeanInitializationException(
                "One of JavaBean properties 'errorView' or 'errorViewName' must be specified.");
        }
        if (this.errorType == null) {
            throw new BeanInitializationException(
                "JavaBean property 'errorType' cannot be null.");
        }
        if (this.statusCodeMappings == null) {
            throw new BeanInitializationException(
                "JavaBean property 'statusCodeMappings' cannot be null.");
        }
    }
    
    
    @Override
    public Map<String, Object> getErrorModel(HttpServletRequest request,
                             HttpServletResponse response,
                             Throwable error) throws Exception {
        if (this.logExceptions) {
            logger.warn("Error in request " + request, error);
        }
        Map<String, Object> model = new HashMap<String, Object>();
        if (this.providers != null) {
            try {
                for (int i = 0; i < this.providers.length; i++) {
                    this.providers[i].referenceData(model, request);
                }
            } catch (Throwable t) {
                // Silently ignore
            }
        }


        org.springframework.web.servlet.support.RequestContext ctx = null;
        try {
            ctx = new org.springframework.web.servlet.support.RequestContext(request);
        } catch (Throwable t) {
            
        }
        
        String errorClassName = error.getClass().getName();
        String errorMessage = (ctx == null) ? errorClassName
            : ctx.getMessage(errorClassName, errorClassName);
        
        if (errorClassName.equals(errorMessage)) {
            errorMessage = (ctx == null) ? error.getMessage()
                : ctx.getMessage(DEFAULT_ERROR_CODE, DEFAULT_ERROR_DESCRIPTION);
        }

        Map<Object, Object> errorModel = new HashMap<Object, Object>();
        errorModel.put(ERROR_MODEL_EXCEPTION_KEY, error);
        errorModel.put(ERROR_MODEL_ERROR_DESCRIPTION_KEY, errorMessage);
        model.put(ERROR_MODEL_KEY, errorModel);
        return model;
    }
    


    @Override
    public Object getErrorView(HttpServletRequest request,
                             HttpServletResponse response,
                             Throwable error) throws Exception {
        if (this.errorView != null) {
            return this.errorView;
        }
        return this.errorViewName;
    }
    

    @Override
    public int getHttpStatusCode(HttpServletRequest request,
                                 HttpServletResponse response,
                                 Throwable error) throws Exception {

        String key = error.getClass().getName();

        if (!this.statusCodeMappings.containsKey(key)) {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }

        Integer value = this.statusCodeMappings.get(key);
        if (value == null) {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }

        try {
            return value;
        } catch (NumberFormatException e) {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getName()).append(":").append(this.beanName);
        sb.append(": [");
        if (this.errorView != null) {
            sb.append("errorView = ").append(this.errorView).append(", ");
        } else {
            sb.append("errorViewName = ").append(this.errorViewName).append(", ");
        }
        sb.append("errorType = ").append(this.errorType.getName()).append("]");
        return sb.toString();
    }
    
}
