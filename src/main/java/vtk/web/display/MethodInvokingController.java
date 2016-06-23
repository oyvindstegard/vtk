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
package vtk.web.display;


import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;


/**
 */
public class MethodInvokingController implements Controller, InitializingBean {

    private Object targetObject;
    private String methodName;
    private Method method;
    private String modelName;
    private String viewName;

    public void setTargetObject(Object targetObject) {
        this.targetObject = targetObject;
    }
    
    public void setTargetMethod(String method) {
        this.methodName = method;
    }
    
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public void setViewName(String viewName) {
        this.viewName = viewName;
    }
    
    public void afterPropertiesSet() {
        if (targetObject == null) {
            throw new BeanInitializationException(
                "JavaBean property 'targetObject' not set");
        }

        if (methodName == null) {
            throw new BeanInitializationException(
                "JavaBean property 'targetMethod' not set");
        }

        if (viewName != null) {
            if (modelName == null) {
                throw new BeanInitializationException(
                        "JavaBean property 'modelName' not set");
            }
        }

        Class<?> clazz = targetObject.getClass();
        Method[] methods = clazz.getMethods();
        for (int i = 0; i < methods.length; i++) {
            if (methodName.equals(methods[i].getName())) {
                // XXX: should check for overloaded methods
                this.method = methods[i];
            }
        }
    }
    

    @Override
    public ModelAndView handleRequest(
        HttpServletRequest request, HttpServletResponse response) throws Exception {
        Object result = method.invoke(targetObject, new Object[0]);
        if (viewName != null) {
            Map<String, Object> model = new HashMap<>();
            model.put(modelName, result);
            ModelAndView mv = new ModelAndView(viewName, model);
            return mv;
        }
        response.setContentType("text/plain;charset=utf-8");
        response.setCharacterEncoding("utf-8");
        response.getWriter().write(result.toString());
        response.flushBuffer();
        return null;
    }
}

