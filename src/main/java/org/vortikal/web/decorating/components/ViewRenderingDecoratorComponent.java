/* Copyright (c) 2007, 2008, University of Oslo, Norway
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
package org.vortikal.web.decorating.components;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.View;
import org.vortikal.web.referencedata.ReferenceDataProvider;
import org.vortikal.web.referencedata.ReferenceDataProviding;
import org.vortikal.web.servlet.BufferedResponse;
import org.vortikal.web.decorating.DecoratorRequest;
import org.vortikal.web.decorating.DecoratorResponse;


public class ViewRenderingDecoratorComponent extends AbstractDecoratorComponent {
    
    private View view;
    private Set<String> exposedParameters = new HashSet<String>();
    private boolean exposeMvcModel = false;

    @Required public void setView(View view) {
        this.view = view;
    }

    public void setExposedParameters(Set<String> exposedParameters) {
        this.exposedParameters = exposedParameters;
    }

    public void setExposeMvcModel(boolean exposeMvcModel) {
        this.exposeMvcModel = exposeMvcModel;
    }

    public final void render(DecoratorRequest request, DecoratorResponse response)
        throws Exception {
        Map<Object, Object> model = new HashMap<Object, Object>();
        if (this.exposeMvcModel) {
            model.putAll(request.getMvcModel());
        }
        processModel(model, request, response);
        renderView(model, request, response);
    }
    
    
    /**
     * Process the model prior to view rendering. The default
     * implementation performs the following steps:
     * <ol>
     *   <li>Gather all reference data providers (using
     *     <code>getReferenceDataProviders()</code>) and invoke them
     *     in order
     *   </li>
     *   <li>If <code>exposeComponentParameters</code> is set, add an
     *     entry in the model under the name determined by
     *     <code>exposedParametersModelName</code>, containing either 
     *     the full set of component parameters or a specified set, 
     *     depending on whether the config parameter <code>exposedParameters</code>
     *     is specified.
     *   <li>
     * </ol>
     *
     * @param model the MVC model
     * @param request the decorator request
     * @param response the decorator response
     * @exception Exception if an error occurs
     */
    protected void processModel(Map<Object, Object> model, DecoratorRequest request,
                                DecoratorResponse response) throws Exception {

        if (this.exposedParameters != null) {
            for (Iterator<String> i = request.getRequestParameterNames(); i.hasNext();) {
                String name = i.next();
                if (!this.exposedParameters.isEmpty() 
                        && !this.exposedParameters.contains(name)) {
                    continue;
                }
                Object value = request.getParameter(name);
                model.put(name, value);
            }
        }

        if (this.view instanceof ReferenceDataProviding) {
            ReferenceDataProvider[] providers =
                ((ReferenceDataProviding) this.view).getReferenceDataProviders();
            if (providers != null) {
                for (ReferenceDataProvider provider: providers) {
                    provider.referenceData(model, request.getServletRequest());
                }
            }
        }
    }
    

    @SuppressWarnings("unchecked")
    private void renderView(Map model, DecoratorRequest request, DecoratorResponse response)
        throws Exception {
        HttpServletRequest servletRequest = request.getServletRequest();
        BufferedResponse bufferedResponse = new BufferedResponse();
        this.view.render(model, servletRequest, bufferedResponse);
        response.setCharacterEncoding(bufferedResponse.getCharacterEncoding());
        OutputStream out = response.getOutputStream();
        out.write(bufferedResponse.getContentBuffer());
        out.close();
    }
    

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(this.getClass().getName()).append(": [");
        sb.append("view = ").append(this.view).append("]");
        return sb.toString();
    }


    protected String getDescriptionInternal() {
        return null;
    }


    protected Map<String, String> getParameterDescriptionsInternal() {
        return null;
    }
}
