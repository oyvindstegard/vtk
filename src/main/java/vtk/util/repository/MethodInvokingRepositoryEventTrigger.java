/* Copyright (c) 2005, University of Oslo, Norway
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
package vtk.util.repository;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationListener;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.event.RepositoryEvent;
import vtk.repository.event.ResourceCreationEvent;
import vtk.repository.event.ResourceDeletionEvent;
import vtk.repository.event.ResourceMovedEvent;

/**
 *  
 */
public class MethodInvokingRepositoryEventTrigger 
  implements ApplicationListener<RepositoryEvent>, InitializingBean {

    private static Log logger = LogFactory.getLog(MethodInvokingRepositoryEventTrigger.class);

    private Repository repository;
    private Path uri;
    private List<Pattern> uriPatterns;
    private Object targetObject;
    private String method;

    private LinkedHashMap<Object, String> multipleInvocations;
    private List<TargetAndMethod> methodInvocations;

    @Required
    public void setRepository(Repository repository)  {
        this.repository = repository;
    }
    
    public void setTargetObject(Object targetObject) {
        this.targetObject = targetObject;
    }
    
    /**
     * Only methods which take no arguments are supported for resolving ! 
     * @param method
     */
    public void setMethod(String method) {
        this.method = method;
    }

    public void setMultipleInvocations(LinkedHashMap<Object, String> multipleInvocations) {
        this.multipleInvocations = multipleInvocations;
    }

    public void setUri(String uri) {
        this.uri = Path.fromString(uri);
    }
        
    public void setUriPattern(String uriPattern) {
        this.uriPatterns = new ArrayList<>();
        uriPatterns.add(Pattern.compile(uriPattern));
    }
    
    public void setUriPatterns(List<String> uriPatterns) {
        this.uriPatterns = new ArrayList<>();
        for (String uriPattern: uriPatterns)
            this.uriPatterns.add(Pattern.compile(uriPattern));
    }

    @Override
    public void afterPropertiesSet() {
        if (this.uri == null && this.uriPatterns == null) {
            throw new BeanInitializationException(
                "One of JavaBean properties 'uri', 'uriPattern' or 'uriPatterns' must be specified.");
        }
        if (this.multipleInvocations != null && this.targetObject != null) {
            throw new BeanInitializationException(
                    "Specify only one of properties 'targetObject + method' or 'multipleInvocations'");
        }
        if (this.multipleInvocations == null && (this.targetObject == null || this.method == null)) {
            throw new BeanInitializationException(
                    "Specify one of properties 'targetObject + method' or 'multipleInvocations'");
        }

        this.methodInvocations = new ArrayList<>();
        if (this.multipleInvocations == null) {
            initMethodInvocation(this.targetObject, this.method);
        } else {
            for (Map.Entry<Object,String> entry: this.multipleInvocations.entrySet()) {
                Object target = entry.getKey();
                String methodName = entry.getValue();
                initMethodInvocation(target, methodName);
            }
        }
    }
    
    private void initMethodInvocation(Object target, String methodName) {
        // Resolving only methods that take no arguments.
        Method m =  BeanUtils.findMethod(target.getClass(), methodName, new Class[0]);
        
        if (m == null) {
            throw new BeanInitializationException("Unable to resolve method with name '"
                    + methodName + "' for class " + target.getClass() 
                    + ". Only methods that take no arguments are supported.");
        }
        this.methodInvocations.add(new TargetAndMethod(target, m));
    }

    @Override
    public void onApplicationEvent(RepositoryEvent event) {
        
        Repository rep = event.getRepository();
        if (! rep.getId().equals(this.repository.getId())) {
            return;
        }
        
        Path resourceURI = event.getURI();

        if (checkEvent(event, resourceURI)) {
            invoke();
        }
        if (event instanceof ResourceMovedEvent) {
            resourceURI = ((ResourceMovedEvent) event).getFrom().getURI();
            if (checkEvent(event, resourceURI)) {
                invoke();
            }
        }
    }
    
    private boolean checkEvent(RepositoryEvent event, Path resourceURI) {
        if (this.uri != null) {
            if (((event instanceof ResourceDeletionEvent)
                 || (event instanceof ResourceCreationEvent))
                && (resourceURI.isAncestorOf(this.uri)
                    || this.uri.isAncestorOf(resourceURI))) {
                return true;
            }
            else if (this.uri.equals(resourceURI)) {
                return true;
            }
        } 
        else if (this.uriPatterns != null) {
            for (Pattern uriPattern: this.uriPatterns) {
                Matcher matcher = uriPattern.matcher(resourceURI.toString());
                if (matcher.find()) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private void invoke() {
        for (TargetAndMethod tm: this.methodInvocations) {
            Method m = tm.getMethod();
            Object target = tm.getTarget();

            try {
                if (logger.isDebugEnabled()) {
                    logger.debug("Invoking method " + m + " on object "
                            + this.targetObject);
                }
                m.invoke(target, new Object[0]);
            } catch (Throwable t) {
                logger.warn("Error occurred while invoking method '" + m +
                        "' on object '" + target + "'", t);
            }
        }
    }

    private static final class TargetAndMethod {
        private Object target;
        private Method method;

        public TargetAndMethod(Object target, Method m) {
            this.target = target;
            this.method = m;
        }

        public Object getTarget() {
            return this.target;
        }

        public Method getMethod() {
            return this.method;
        }
    }

}

