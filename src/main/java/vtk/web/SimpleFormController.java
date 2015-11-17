/* Copyright (c) 2015, University of Oslo, Norway
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

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

/**
 * A form controller designed to mimic a subset of Spring's  
 * {@code SimpleFormController}, which was deprecated in version 4.0.
 * 
 * <p>Method names and behavior is kept the same (except that the class 
 * now takes a type parameter). This way controllers based on 
 * {@code SimpleFormController} can stay (mostly) source code compatible, 
 * and it also provides an alternative for controllers that cannot be based 
 * on annotation-driven MVC.
 * 
 * @param <T> type of the command object
 */
public abstract class SimpleFormController<T> implements Controller {
    
    private String formView;
    private String successView;
    private String commandName = "form";
    private boolean sessionForm;
    private Validator validator;
    
    private final Log logger = LogFactory.getLog(SimpleFormController.class.getName());

    public String getFormView() {
        return formView;
    }
    
    public void setFormView(String formView) {
        this.formView = formView;
    }
    
    public String getSuccessView() {
        return successView;      
    }
    
    public void setSuccessView(String successView) {
        this.successView = successView;
    }
    
    public String getCommandName() {
        return commandName;
    }
    
    public void setCommandName(String commandName) {
        this.commandName = commandName;
    }
    
    public Validator getValidator() {
        return validator;
    }
    
    public void setValidator(Validator validator) {
        this.validator = validator;
    }
    
    public void setSessionForm(boolean sessionForm) {
        this.sessionForm = sessionForm;
    }
    
    public ModelAndView handleRequest(HttpServletRequest request, 
            HttpServletResponse response) throws Exception {
        
        if (isFormSubmission(request)) {
            try {
                T command = getCommand(request);
                if (command == null) 
                    throw new NullPointerException("formBackingObject()");
                ServletRequestDataBinder binder = bindAndValidate(request, command);
                BindException errors = new BindException(binder.getBindingResult());
                return processFormSubmission(request, response, command, errors);
            }
            catch (SessionRequiredException e) {
                T command = formBackingObject(request);
                ServletRequestDataBinder binder = bindAndValidate(request, command);
                BindException errors = new BindException(binder.getBindingResult());
                return processFormSubmission(request, response, command, errors);
            }
        }
        
        else {
            T command = formBackingObject(request);
            ServletRequestDataBinder binder = createBinder(request, command);
            BindException errors = new BindException(binder.getBindingResult());
            return showForm(request, errors, getFormView(), errors.getModel());
        }
    }
    
    @SuppressWarnings("unchecked")
    private T getCommand(HttpServletRequest request) throws Exception {
        if (!sessionForm) {
            return formBackingObject(request);
        }
        HttpSession session = request.getSession(false);
        if (session == null) 
            throw new SessionRequiredException("Session required");
        Object attr = session.getAttribute(formAttributeName());
        if (attr == null) 
            throw new SessionRequiredException("Missing session form attribute");
        session.removeAttribute(formAttributeName());
        return (T) attr;
    }

    protected ServletRequestDataBinder bindAndValidate(
            HttpServletRequest request, T command) throws Exception {
        ServletRequestDataBinder binder = createBinder(request, command);
        prepareBinder(binder);
        initBinder(request, binder);
        BindException errors = new BindException(binder.getBindingResult());
        
        binder.bind(request);
        
        if (validator != null) {
            validator.validate(command, errors);
        }
        onBindAndValidate(request, command, errors);
        return binder;
    }

    @SuppressWarnings("unused") 
    protected ServletRequestDataBinder createBinder(HttpServletRequest request,
            T command) throws Exception {
        return new ServletRequestDataBinder(command, getCommandName());
    }
    
    protected ModelAndView showForm(HttpServletRequest request, 
            BindException errors, String viewName, 
            Map<String, Object> controllerModel) throws Exception {

        @SuppressWarnings("unchecked")
        T command = (T) errors.getTarget();

        Map<String, Object> model = errors.getModel();
        
        Map<String, Object> referenceData = 
                referenceData(request, command, errors);
        if (referenceData != null) {
            model.putAll(referenceData);
        }
        if (controllerModel != null) {
            model.putAll(controllerModel);
        }
        if (sessionForm) {
            request.getSession().setAttribute(formAttributeName(), command);
        }
        return new ModelAndView(viewName, model);
    }    
    
    @SuppressWarnings("unused") 
    protected Map<String, Object> referenceData(HttpServletRequest request, 
            T command, Errors errors) throws Exception {
        return null;
    }
    
    protected abstract T formBackingObject(HttpServletRequest request) 
            throws Exception;
    
    protected boolean isFormSubmission(HttpServletRequest request) 
            throws Exception {
        return "POST".equals(request.getMethod());
    }
    
    @SuppressWarnings("unused") 
    protected void onBindAndValidate(HttpServletRequest request, T command, 
            BindException errors) throws Exception {
    }
    
    protected ModelAndView processFormSubmission(HttpServletRequest request, 
            HttpServletResponse response, T command, BindException errors)
            throws Exception {
        if (errors.hasErrors()) {
            return showForm(request, errors, getFormView(), errors.getModel());
        }
        return onSubmit(request, response, command, errors);
    }
    

    @SuppressWarnings("unused")
    protected ModelAndView onSubmit(HttpServletRequest request, 
            HttpServletResponse response, T command, BindException errors) 
                    throws Exception {
        return new ModelAndView(getSuccessView(), errors.getModel());
    }

    @SuppressWarnings("unused")
    protected final void prepareBinder(ServletRequestDataBinder binder) {
    }

    @SuppressWarnings("unused")
    protected void initBinder(HttpServletRequest request, ServletRequestDataBinder binder) 
            throws Exception {
    }
    
    private String formAttributeName() { 
        return getClass().getName() + ".form." + System.identityHashCode(this); 
    }
    
    private static class SessionRequiredException extends RuntimeException { 
        private static final long serialVersionUID = -7967269569002028897L;
        public SessionRequiredException(String msg) { super(msg); }
    }
    
}
