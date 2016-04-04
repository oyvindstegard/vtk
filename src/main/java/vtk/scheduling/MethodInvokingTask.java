/* Copyright (c) 2012, University of Oslo, Norway
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

package vtk.scheduling;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;


/**
 * General task which invokes a configurable object method.
 * Can be used instead of {@link SimpleMethodInvokingTriggerBean}.
 */
public class MethodInvokingTask extends AbstractTask implements InitializingBean {

    private String targetMethodName;
    private Method targetMethod;
    private Object targetObject;
    private Object[] arguments;
    private Class<?>[] argumentTypes;
    
    private final Logger logger = LoggerFactory.getLogger(getClass());
    
    @Override
    public void run() {
        try {

            // Invoke with rigorous logging of errors
            this.targetMethod.invoke(this.targetObject, this.arguments);

        } catch (IllegalAccessException iae) {
            logger.error("Target method not accessible", iae);
        } catch (IllegalArgumentException iae) {
            if (this.arguments != null) {
                StringBuilder argTypes = new StringBuilder("(");
                for (int i = 0; i < this.arguments.length; i++) {
                    argTypes.append(
                            this.arguments[i].getClass().getName());
                    if (i < this.arguments.length - 1) {
                        argTypes.append(", ");
                    }
                }
                argTypes.append(")");

                logger.error(
                        "Supplied arguments illegal for given target method: "
                        + argTypes.toString(), iae);
            } else {
                logger.error("No arguments supplied for given target method", iae);
            }

        } catch (InvocationTargetException ite) {
            logger.warn("Invoked method threw an exception: " + ite.getTargetException().getMessage(), ite.getTargetException());
        } catch (Exception e) {
            logger.warn("Got an unexpected exception during method invocation:", e);
        }
    }

    @Override
    public void afterPropertiesSet() {
        if (this.targetObject == null) {
            throw new BeanInitializationException("Bean property 'targetObject' null");
        } else if (this.targetMethodName == null) {
            throw new BeanInitializationException("Bean property 'targetMethodName' null");
        }
        
        if (!(this.arguments == null && this.argumentTypes == null)) {
            if (this.argumentTypes == null) {
                throw new BeanInitializationException("Bean property 'arguments' "
                        + "set, but missing needed property 'argumentTypes'");
            } else if (this.arguments == null) {
                throw new BeanInitializationException("Bean property 'argumentTypes' "
                        + "set, but missing needed property 'arguments'");
            } else if (this.arguments.length != this.argumentTypes.length) {
                throw new BeanInitializationException(
                "Number of 'arguments' and 'argumentTypes' must match.");
            }
        }
        
        this.targetMethod = BeanUtils.findMethod(
                this.targetObject.getClass(), this.targetMethodName,  
                                              this.argumentTypes);
        
        if (this.targetMethod == null) {
            throw new BeanInitializationException(
                    "Target method with name '" + this.targetMethodName
                    + "' not found in target object type: " 
                    + this.targetObject.getClass());
        }
        
    }

    @Required
    public void setTargetMethodName(String targetMethodName) {
        this.targetMethodName = targetMethodName;
    }
    
    @Required
    public void setTargetObject(Object targetObject) {
        this.targetObject = targetObject;
    }
    
    public void setArguments(Object[] arguments) {
        this.arguments = arguments;
    }
    
    public void setArgumentTypes(Class<?>[] argumentTypes) {
        this.argumentTypes = argumentTypes;
    }
}
