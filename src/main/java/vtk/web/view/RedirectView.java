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
package vtk.web.view;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.view.AbstractView;

import vtk.web.InvalidModelException;
import vtk.web.referencedata.ReferenceDataProvider;


/**
 * Simple HTTP Redirect view.
 * 
 * <p>Expects a model property "redirectURL", containing the URL
 * string to redirect to.  Optional property 'http10' can be switched
 * to <code>false</code> to send 303 instead of 302 as HTTP status
 * code.
 * 
 * @see vtk.web.referencedata.provider.RedirectProvider
 */
public class RedirectView extends AbstractView {

    private boolean http10 = true;
    private ReferenceDataProvider[] referenceDataProviders;
    
    public void setReferenceDataProviders(ReferenceDataProvider[] referenceDataProviders) {
        this.referenceDataProviders = referenceDataProviders;
    }
    
    /**
     * @throws InvalidModelException ({@link InvalidModelException})
     * if expected model data 'redirectURL' is missing
     */
    protected void renderMergedOutputModel(
        Map<String, Object> model, HttpServletRequest request,
        HttpServletResponse response) throws Exception {
        
        for (ReferenceDataProvider p: referenceDataProviders) {
            p.referenceData(model, request);
        }
        
        String url = (String) model.get(
            "redirectURL");

        if (url == null)
            throw new InvalidModelException("Missing expected model data 'redirectURL'");
            
        if (this.http10) {
            // send status code 302
            response.sendRedirect(url);
        }
        else {
            // correct HTTP status code is 303, in particular for POST requests
            response.setStatus(303);
            response.setHeader("Location", url);
        }
    }


}
