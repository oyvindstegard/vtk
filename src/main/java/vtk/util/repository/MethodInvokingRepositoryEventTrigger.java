/* Copyright (c) 2005,2016 University of Oslo, Norway
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

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;

import vtk.cluster.ClusterAware;
import vtk.cluster.ClusterContext;
import vtk.cluster.ClusterRole;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.event.RepositoryEvent;
import vtk.repository.event.ResourceCreationEvent;
import vtk.repository.event.ResourceDeletionEvent;
import vtk.repository.event.ResourceMovedEvent;

/**
 *  
 */
public class MethodInvokingRepositoryEventTrigger extends AbstractRepositoryEventHandler 
    implements InitializingBean, BeanNameAware, ClusterAware {

    private final Logger logger = LoggerFactory.getLogger(MethodInvokingRepositoryEventTrigger.class);

    private Repository repository;
    private Path uri;
    private List<Pattern> uriPatterns;
    private Object targetObject;
    private String method;

    private LinkedHashMap<Object, String> multipleInvocations;
    private List<TargetAndMethod> methodInvocations;

    // Cluster related fields
    private String beanId;
    private Optional<ClusterRole> clusterRole = Optional.empty();
    private Optional<ClusterContext> clusterContext = Optional.empty();

    public MethodInvokingRepositoryEventTrigger() {
        super(true);
    }

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
        for (String uriPattern: uriPatterns) {
            this.uriPatterns.add(Pattern.compile(uriPattern));
        }
    }
    
    @Override
    public void clusterContext(ClusterContext context) {
        this.clusterContext = Optional.of(context);
        context.subscribe(InvokeMessage.class);
    }

    @Override
    public void roleChange(ClusterRole role) {
        this.clusterRole = Optional.of(role);
    }
    
    @Override
    public void afterPropertiesSet() {
        if (uri == null && uriPatterns == null) {
            throw new BeanInitializationException(
                "One of JavaBean properties 'uri', 'uriPattern' or 'uriPatterns' must be specified.");
        }
        if (multipleInvocations != null && targetObject != null) {
            throw new BeanInitializationException(
                    "Specify only one of properties 'targetObject + method' or 'multipleInvocations'");
        }
        if (multipleInvocations == null && (targetObject == null || method == null)) {
            throw new BeanInitializationException(
                    "Specify one of properties 'targetObject + method' or 'multipleInvocations'");
        }

        methodInvocations = new ArrayList<>();
        if (multipleInvocations == null) {
            initMethodInvocation(targetObject, method);
        }
        else {
            for (Map.Entry<Object,String> entry: multipleInvocations.entrySet()) {
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
        methodInvocations.add(new TargetAndMethod(target, m));
    }


    @Override
    public void clusterMessage(Object message) throws Exception {
        if (message instanceof InvokeMessage
                && beanId.equals(((InvokeMessage)message).triggerBeanId)) {
            // Only invoke if the message comes from an instance of this
            // trigger on another cluster node.
            invoke();
        }
    }

    /**
     * Receive events from the repository. These events are typically only generated
     * for write operations, and so normally only occur on the node that is currently MASTER.
     *
     * <p>But in certain cases, they also can occur on slaves, since root role has write
     * permissions even on such nodes, and perhaps in the critical moment of MASTER/SLAVE
     * role switching.
     *
     * <p>In any case we propagate such repository events to "partner triggers" on the
     * other nodes, so that we ensure the configured invocations take place. Normally, it is
     * better with one triggering too much, rather than losing trigger events.
     *
     * @param event
     */
    @Override
    public void handleEvent(RepositoryEvent event) {
        
        Repository rep = event.getRepository();
        if (!rep.getId().equals(repository.getId())) {
            return;
        }
        
        Path resourceURI = event.getURI();

        if (checkEvent(event, resourceURI)) {
            invoke();
            invokeTriggerOnOtherNodes();
        }
        if (event instanceof ResourceMovedEvent) {
            resourceURI = ((ResourceMovedEvent) event).getFrom().getURI();
            if (checkEvent(event, resourceURI)) {
                invoke();
                invokeTriggerOnOtherNodes();
            }
        }
    }

    // Check if the event applies to this trigger, based on configured URI
    // and/or URI patterns
    private boolean checkEvent(RepositoryEvent event, Path resourceURI) {
        if (uri != null) {
            if (((event instanceof ResourceDeletionEvent)
                 || (event instanceof ResourceCreationEvent))
                && (resourceURI.isAncestorOf(uri)
                    || uri.isAncestorOf(resourceURI))) {
                return true;
            }
            else if (uri.equals(resourceURI)) {
                return true;
            }
        }
        else if (uriPatterns != null) {
            for (Pattern uriPattern: uriPatterns) {
                Matcher matcher = uriPattern.matcher(resourceURI.toString());
                if (matcher.find()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void invokeTriggerOnOtherNodes() {
        clusterContext.ifPresent(ctx -> ctx.clusterMessage(new InvokeMessage(beanId)));
    }
    
    private void invoke() {
        for (TargetAndMethod tm: methodInvocations) {
            if (logger.isDebugEnabled()) {
                logger.debug("Invoking method " + tm.method + " on object " 
                        + tm.target);
            }
            try {
                tm.method.invoke(tm.target, new Object[0]);
            }
            catch (Throwable t) {
                logger.warn("Failed to invoke method " + tm.method
                        + " on object " + tm.target, t);
            }
        }
    }

    @Override
    public void setBeanName(String beanId) {
        this.beanId = beanId;
    }

    private static final class InvokeMessage implements Serializable {
        private static final long serialVersionUID = 3634433934600061048L;

        final String triggerBeanId;

        InvokeMessage(String triggerBeanId) {
            this.triggerBeanId = triggerBeanId;
        }
    }


    private static final class TargetAndMethod {
        public final Object target;
        public final Method method;

        public TargetAndMethod(Object target, Method m) {
            this.target = target;
            this.method = m;
        }
    }

    @Override
    public String toString() {
        return "MethodInvokingRepositoryEventTrigger{" + "beanId=" + beanId + '}';
    }

}

