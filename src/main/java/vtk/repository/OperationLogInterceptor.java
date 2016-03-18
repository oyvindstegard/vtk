/* Copyright (c) 2006,2007,2016 University of Oslo, Norway
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
package vtk.repository;

import java.io.IOException;
import java.lang.reflect.Parameter;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.annotation.Required;
import vtk.security.AuthenticationException;
import vtk.security.Principal;
import vtk.security.token.TokenManager;

/**
 * AOP method interceptor class for excplicit logging repository operations.
 * 
 * <p>Only repository methods annotated with {@link OpLog} will be logged, and
 * of those, only parameters annotated with {@link OpLogParam} will be included
 * in the log entry.
 */
public class OperationLogInterceptor implements MethodInterceptor {

    private TokenManager tokenManager;
    
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        OpLog opLog;
        if ((opLog = invocation.getMethod().getAnnotation(OpLog.class)) == null) {
            return invocation.proceed();
        }
        
        Parameter[] formalParams = invocation.getMethod().getParameters();
        Object[] paramValues = invocation.getArguments();
        StringBuilder info = new StringBuilder("(");
        StringBuilder userInfo = new StringBuilder();
        if (paramValues.length == formalParams.length) {
            for (int i=0; i<formalParams.length; i++) {
                OpLogParam p = formalParams[i].getAnnotation(OpLogParam.class);
                if (p != null) {
                    String formattedValue = formatValue(paramValues[i]);
                    if (info.length() > 1) {
                        info.append(", ");
                    }
                    if (!p.name().isEmpty()) {
                        // Add user info if token param is recognized
                        if (p.name().equals("token")) {
                            userInfo.append("token: ").append(formattedValue).append(", ");
                            Principal principal = tokenManager.getPrincipal(paramValues[i] != null ? paramValues[i].toString() : null);
                            userInfo.append("principal: ").append(principal);
                        } else {
                            info.append(p.name()).append(": ").append(formattedValue);
                        }
                    } else {
                        info.append(formattedValue);
                    }
                }
            }
        }
        if (paramValues.length > 0 && info.length() == 1) {
            info.append("...");
        }
        info.append(")");
        if (userInfo.length() > 0) {
            info.append(", ").append(userInfo);
        }
        
        return dispatchAndLog(invocation, info.toString(), opLog);
    }
    
    private String formatValue(Object value) {
        if (value == null) return "null";
        
        if (value instanceof Resource) {
            return ((Resource) value).getURI().toString();
        }
        
        if (value instanceof Revision) {
            return "r" + ((Revision) value).getID();
        }
        
        if (value instanceof Comment) {
            return ((Comment) value).getURI().toString();
        }
        
        if (value instanceof RecoverableResource) {
            return ((RecoverableResource) value).getTrashUri();
        }
        
        return value.toString();
    }

    private Object dispatchAndLog(MethodInvocation mi, String params, OpLog op) throws Throwable {
        String operation = mi.getMethod().getName();
        Object retVal;
        try {
            retVal = mi.proceed();
            OperationLog.success(operation, params, op.write());
            return retVal;
            
        } catch (ReadOnlyException roe) {
            OperationLog.failure(operation, params, "read-only", op.write());
            throw roe;
        } catch (ResourceNotFoundException rnf) {
            OperationLog.failure(operation, params, "resource not found: '" + rnf.getURI() + "'", op.write());
            throw rnf;
        } catch (IllegalOperationException | IOException ioe) {
            OperationLog.failure(operation, params, ioe.getMessage(), op.write());
                    
            throw ioe;
        } catch (AuthenticationException authenticationException) {
            OperationLog.failure(operation, params, "not authenticated", op.write());
            throw authenticationException;
        } catch (AuthorizationException authorizationException) {
            OperationLog.failure(operation, params, "not authorized", op.write());
            throw authorizationException;
        } catch (ResourceLockedException le) {
            OperationLog.failure(operation, params, "resource locked", op.write());
            throw le;
        } catch (ResourceOverwriteException roe) {
            OperationLog.failure(operation, params, "cannot overwrite destination resource", op.write());
            throw roe;
        } catch (FailedDependencyException fde) {
            // XXX: Log this exception ?
            OperationLog.failure(operation, params, "failed dependency", op.write());
            throw fde;
        }
    }
    
    @Required
    public void setTokenManager(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

}
