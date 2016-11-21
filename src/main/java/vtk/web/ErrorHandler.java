/* Copyright (c) 2004, University of Oslo, Norway
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

import vtk.web.service.Service;


/**
 * Interface for error handlers. Error handlers are selected based on
 * two criteria, matched in the following order:
 * 
 * <ol>
 *   <li>The error {@link #getErrorType type}. This specifies the
 *   class of exceptions handled by the error handler. More specific
 *   exception (sub) classes take precedence over less specific
 *   ones. For example, given that the error in question is a
 *   <code>java.io.IOException</code> an error handler specifying that
 *   class will get selected over a handler specifying
 *   <code>java.lang.Throwable</code>.
 *   <li>The {@link #getService service}. This specifies the set
 *   of {@link Service services} for which the error handler is
 *   applicable. Error handlers with more specific services
 *   (descendants) take precedence over less specific ones. If
 *   <code>null</code> is specified, it is interpreted as "match any
 *   service".
 * </ol>
 *
 */
public interface ErrorHandler {

    
    /**
     * Gets the class of exceptions handled by this error handler. The
     * class returned must be a {@link Throwable} (or a subclass).
     *
     * @return the exception class
     */
    public Class<? extends Throwable> getErrorType();


    /**
     * Gets the {@link Service} (if any) for which this error handler
     * is applicable.
     * 
     * @return the service, or <code>null</code> if this error handler
     * is not service context sensitive.
     */
    public Service getService();
    

    /**
     * Creates a model for the error handler view.
     *
     * @param error a <code>Throwable</code> value
     * @return a <code>View</code>
     * @exception Exception if an error occurs during error handling
     * (this will most likely cause a servlet exception)
     */
    public Map<String, Object> 
    getErrorModel(HttpServletRequest request, HttpServletResponse response,
            Throwable error) throws Exception;
    

    /**
     * Gets a view capable of rendering the error model.
     *
     * @param request a <code>HttpServletRequest</code> value
     * @param response a <code>HttpServletResponse</code> value
     * @param error a <code>Throwable</code> value
     * @return a <code>View</code> or a view name
     * @exception Exception if an error occurs
     */
    public Object getErrorView(HttpServletRequest request, HttpServletResponse response,
                               Throwable error) throws Exception;
    

    /**
     * Gets the HTTP status code that should be set on the response,
     * based on the type of error.
     *
     * @param request a <code>HttpServletRequest</code> value
     * @param response a <code>HttpServletResponse</code> value
     * @param error a <code>Throwable</code> value
     * @return the HTTP status code.
     * @exception Exception if an error occurs
     */
    public int getHttpStatusCode(HttpServletRequest request, HttpServletResponse response,
                                 Throwable error) throws Exception;

}
