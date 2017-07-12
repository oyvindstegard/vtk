/* Copyright (c) 2010, University of Oslo, Norway
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
package vtk.web.service;

import java.lang.reflect.Method;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;

import vtk.repository.Resource;
import vtk.security.Principal;

public class MethodInvocationResultAssertion implements WebAssertion, InitializingBean {

    public static enum Operator {
        EQ, NEQ;
    }
    
    private Object target;
    private String method;
    private Object result;
    private Operator operator = Operator.EQ;
    
    private Method targetMethod;
    
    
    @Override
    public boolean conflicts(WebAssertion assertion) {
        return false;
    }

    @Override
    public boolean matches(HttpServletRequest request, Resource resource,
            Principal principal) {
        return invoke();
    }

    @Override
    public Optional<URL> processURL(URL url, Resource resource, Principal principal) {
        if (invoke()) return Optional.of(url);
        return Optional.empty();
    }
    
    private boolean invoke() {
        try {
            Object result = this.targetMethod.invoke(this.target, new Object[0]);
            switch (this.operator) {
            case EQ:
                return this.result.equals(result);
            case NEQ:
                return !this.result.equals(result);
            default:
                throw new IllegalStateException("Operator not configured: " + this.operator);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Error invoking method " + this.method 
                    + " on object " + this.target, t);
        }
    }

    @Override
    public URL processURL(URL url) {
        return url;
    }

    @Override
    public void afterPropertiesSet() {
        if (this.target == null) {
            throw new IllegalStateException("Javabean property 'target' not specified");
        }
        if (this.method == null) {
            throw new IllegalStateException("Javabean property 'method' not specified");
        }
        if (this.result == null) {
            throw new IllegalStateException("Javabean property 'result' not specified");
        }
        // Resolving only methods that take no arguments.
        this.targetMethod = 
            BeanUtils.findMethod(this.target.getClass(), 
                    this.method, new Class[0]);
        
        if (this.targetMethod == null) {
            throw new BeanInitializationException("Unable to resolve method with name '"
                    + this.method + "' for class " + this.target.getClass() 
                    + ". Only methods that take no arguments are supported.");
        }
    }
    
    @Override
    public String toString() {
        if (this.operator == Operator.EQ) {
            return "invoke([obj]." + method + "()) = " + result;
        }
        return "invoke([obj]." + method + "()) != " + result;
    }
    
    public void setTarget(Object target) {
        this.target = target;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setResult(Object result) {
        this.result = result;
    }
    
    public void setOperator(String operator) {
        this.operator = Operator.valueOf(operator);
    }

}
