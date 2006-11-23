/* Copyright (c) 2006, University of Oslo, Norway
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
package org.vortikal.scheduling;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;

/**
 * Simple timer/trigger utility bean for invoking a method on another object at a 
 * certain time interval. It currently only supports invocation of methods 
 * on instantiated objects (typically other beans). Any return value from the 
 * target method is discarded.
 * 
 * There are no timing guarantees other than what is provided by 
 * the {@link java.lang.Thread#sleep(long)} method. 
 * 
 * <p>Configurable bean properties:</p>
 * <ul>
 *  <li><code>triggerThreadName</code> - The name of the trigger thread. It
 *  defaults to '[beanName]-trigger'
 *  </li>
 *  <li><code>targetMethodName</code> - The name of the method to invoke on the
 *  target object. Required. 
 *  </li>
 *  <li><code>targetObject</code> - The object on which to invoke the method. 
 *  Required.
 *  </li>
 *  <li><code>arguments</code> - The arguments that should be supplied to the 
 *  target method. Not required if the target method takes no arguments.
 *  </li>
 *  <li><code>argumentTypes</code> - The argument types for the target method in 
 *  correct order. Not required if the target method takes no arguments.
 *  </li>
 *  <li><code>startDelay</code> - Delay in <em>milliseconds</em> before starting 
 *  the interval triggering. Defaults to 0 ms.
 *  </li>
 *  <li><code>repeatInterval</code> - How long to sleep in <em>milliseconds</em> 
 *  between each invocation. The time used in the invoked method is <em>not</em>
 *  included in this interval. Default is 5000 ms.
 *  </li>
 *  <li><code>repeatCount</code> - How many times to trigger the target method 
 *  in total. Optional, defaults to <code>REPEAT_INDEFINITELY</code> (-1).
 *  </li>
 *  <li><code>abortTriggerOnTargetMethodException</code> - Abort the interval 
 *  triggering if the target method throws an exception upon invocation. 
 *  Defaults is false (don't abort)
 *  </li>
 *  <li><code>startTriggerAfterInitialization</code> - Start the triggering 
 *  immediately after bean initialization has successfully completed. 
 *  Default is <code>true</code>.
 *  </li>
 * </ul>
 * 
 * Note that once the triggering is running, the only methods that are safe to call
 * are {@link #start()}, {@link #stop(boolean)} and {@link #isEnabled}. To alter
 * parameters of this bean at runtime, the triggering must be stopped first. 
 * Otherwise, the results are undefined.
 * 
 * @author oyviste
 *
 */
public class SimpleMethodInvokingTriggerBean implements BeanNameAware,
        InitializingBean {

    public static final int REPEAT_INDEFINITELY = -1;
    
    Log logger = LogFactory.getLog(SimpleMethodInvokingTriggerBean.class);
    
    private String beanName;
    private Thread triggerThread;
    private Trigger trigger;
    private String triggerThreadName;
 
    private String targetMethodName;
    private Method targetMethod;
    private Object targetObject;
    private Object[] arguments;
    private Class[] argumentTypes;
    
    private int startDelay = 0;
    private int repeatInterval = 5000;
    private int repeatCount = REPEAT_INDEFINITELY;
    private boolean abortTriggerOnTargetMethodException = false;
    private boolean startTriggerAfterInitialization = true;
    
    
    /* (non-Javadoc)
     * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    public void afterPropertiesSet() throws BeanInitializationException {
        
        if (this.targetObject == null) {
            throw new BeanInitializationException("Bean property 'targetObject' null");
        } else if (this.targetMethodName == null) {
            throw new BeanInitializationException("Bean property 'targetMethodName' null");
        } else if (this.startDelay < 0) {
            throw new BeanInitializationException(
                    "Bean property 'startDelay' must be positive integer");
        } else if (this.repeatInterval <= 0) {
            throw new BeanInitializationException(
                    "Bean property 'repeatInterval' must be greater than zero.");
        } else if (this.repeatCount != REPEAT_INDEFINITELY && this.repeatCount < 0) {
            throw new BeanInitializationException(
                    "Bean property 'repeatCount' must be either REPEAT_INDEFINITELY, 0 or "
                    + " a positive integer");
        }
        
        if (! (this.arguments == null && this.argumentTypes == null)) {
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
        
        if (this.startTriggerAfterInitialization) start(); // Start up after init
    }
    
    /**
     * Start trigger
     *
     */
    public synchronized void start() {
        if (isEnabled()) {
            this.logger.warn("start() called, but interval triggering is already enabled and running");
            return;
        }
        
        this.trigger = new Trigger();
        
        if (this.triggerThreadName != null) {
            this.triggerThread = new Thread(this.trigger, this.triggerThreadName);
        } else {
            this.triggerThread = new Thread(this.trigger, this.beanName 
                                                                    + "-trigger");
        }
        
        this.logger.info("Starting interval trigger thread '" 
                                        + this.triggerThread.getName() + "'");
        
        this.triggerThread.setDaemon(true);
        this.triggerThread.start();
        
    }
    
    /**
     * Stop trigger
     * @param interrupt Whether to interrupt the trigger thread or just signal
     *                  and wait.
     */
    public synchronized void stop(boolean interrupt) {
        if (! isEnabled()) {
            this.logger.warn("stop() called, but interval triggering is not running");
            return;
            
        }
        
        this.logger.info("Signalling interval trigger thread '"
                + this.triggerThread.getName() + "' to stop.");
        this.trigger.alive = false;
        if (interrupt) {
            this.logger.info("Interrupting trigger thread");
            this.triggerThread.interrupt(); // Interrupt
        }
        try {
            this.triggerThread.join();
        } catch (InterruptedException ie) {
            this.logger.warn("Interrupted while waiting for trigger thread '"
                    + this.triggerThread.getName() + "' to stop");
        }
        
        if (this.triggerThread.isAlive()) {
            this.logger.warn("Failed to stop interval triggering thread '" 
                    + this.triggerThread.getName() + "' !");
        } else {
            this.logger.info("Stopped interval trigger thread '" 
                    + this.triggerThread.getName() + "'");
            
        }
        
        
        this.trigger = null;
        this.triggerThread = null;
    }
    
    /**
     * Check whether triggering is enabled or not
     * @return
     */
    public synchronized boolean isEnabled() {
        return this.triggerThread != null && this.triggerThread.isAlive();
    }
    
    /**
     * <code>Runnable</code> trigger class
     */
    private class Trigger implements Runnable {
        boolean alive = true;
        int counter = 0;
        
        public void run() {
            
            if (SimpleMethodInvokingTriggerBean.this.startDelay > 0) {
                try {
                    if (SimpleMethodInvokingTriggerBean.this.logger.isDebugEnabled()) {
                        SimpleMethodInvokingTriggerBean.this.logger.debug("Delaying " 
                                + SimpleMethodInvokingTriggerBean.this.startDelay 
                                + " ms before" 
                                + " starting the interval triggering");
                    }
                    
                    Thread.sleep(SimpleMethodInvokingTriggerBean.this.startDelay);
                } catch (InterruptedException ie) {
                    SimpleMethodInvokingTriggerBean.this.logger.warn(
                                                        "Interrupted while waiting to start !");
                }
            }
            
            while (this.alive && 
                    (SimpleMethodInvokingTriggerBean.this.repeatCount == REPEAT_INDEFINITELY
                      || ++this.counter <= SimpleMethodInvokingTriggerBean.this.repeatCount)) {
                
                try {
                    
                    // Invoke
                    SimpleMethodInvokingTriggerBean.this.targetMethod.invoke(
                                           SimpleMethodInvokingTriggerBean.this.targetObject, 
                                           SimpleMethodInvokingTriggerBean.this.arguments);
                    
                } catch (IllegalAccessException iae) {
                    SimpleMethodInvokingTriggerBean.this.logger.error(
                                                            "Target method not accessible", iae);
                    SimpleMethodInvokingTriggerBean.this.logger.error(
                                                "Unrecoverable error, aborting interval trigger");
                    this.alive = false;
                    break;
                    
                } catch (IllegalArgumentException iae) {
                    if (SimpleMethodInvokingTriggerBean.this.arguments!= null) {
                        StringBuffer argTypes = new StringBuffer("(");
                        for (int i=0; i<SimpleMethodInvokingTriggerBean.this.arguments.length; i++) {
                            argTypes.append(
                                    SimpleMethodInvokingTriggerBean.this.arguments[i].getClass().getName());
                            if (i < SimpleMethodInvokingTriggerBean.this.arguments.length-1) {
                                argTypes.append(", ");
                            }
                        }
                        argTypes.append(")");
                        
                        SimpleMethodInvokingTriggerBean.this.logger.error(
                                "Supplied arguments illegal for given target method: "
                                + argTypes.toString(), iae);
                    } else {
                        SimpleMethodInvokingTriggerBean.this.logger.error(
                                "No arguments supplied for given target method", iae);
                    }
                    
                    SimpleMethodInvokingTriggerBean.this.logger.error(
                            "Unrecoverable error, aborting interval trigger");
                    this.alive = false;
                    break;

                } catch (InvocationTargetException ite) {
                    SimpleMethodInvokingTriggerBean.this.logger.warn(
                            "Invoked method threw an exception: "  
                            + ite.getTargetException().getMessage(), 
                            ite.getTargetException());
                    if (SimpleMethodInvokingTriggerBean.
                            this.abortTriggerOnTargetMethodException) {
                        SimpleMethodInvokingTriggerBean.this.logger.warn("Aborting interval trigger " 
                                + "because of unhandled target method exception");
                        this.alive = false;
                        break;
                    }
                } catch (Exception e) {
                    SimpleMethodInvokingTriggerBean.this.logger.warn(
                                        "Got an unexpected exception during method invocation:", e);
                }
                
                // Sleep
                try {
                    Thread.sleep(SimpleMethodInvokingTriggerBean.this.repeatInterval);
                } catch (InterruptedException ie) {
                    SimpleMethodInvokingTriggerBean.this.logger.warn("Interrupted while sleeping");
                }
            } // while(this.alive)
            
        } // run()
    }
    
    public void setRepeatInterval(int repeatInterval) {
        this.repeatInterval = repeatInterval;
    }

    public void setStartDelay(int startDelay) {
        this.startDelay = startDelay;
    }

    public void setAbortTriggerOnTargetMethodException(
                                boolean abortTriggerOnTargetMethodException) {
        this.abortTriggerOnTargetMethodException = abortTriggerOnTargetMethodException;
    }

    public void setTargetMethodName(String targetMethodName) {
        this.targetMethodName = targetMethodName;
    }
    
    public void setTargetObject(Object targetObject) {
        this.targetObject = targetObject;
    }
    
    public void setArguments(Object[] arguments) {
        this.arguments = arguments;
    }
    
    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public void setTriggerThreadName(String triggerThreadName) {
        this.triggerThreadName = triggerThreadName;
    }

    public void setArgumentTypes(Class[] argumentTypes) {
        this.argumentTypes = argumentTypes;
    }

    public void setStartTriggerAfterInitialization(boolean startTriggerAfterInitialization) {
        this.startTriggerAfterInitialization = startTriggerAfterInitialization;
    }

    public void setRepeatCount(int repeatCount) {
        this.repeatCount = repeatCount;
    }

}
