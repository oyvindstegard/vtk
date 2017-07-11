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
package vtk.web.referencedata.provider;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.web.RequestContext;
import vtk.web.referencedata.ReferenceDataProvider;

/**
 * A reference data provider that puts a message in the model, based
 * on the current {@link RequestContext}.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>localizationKey</code> - the localization key to use
 *   when looking up the message to display in the model. The
 *   resource's name is used as a parameter.
 *   <li><code>modelName</code> - the name to use for the sub-model in
 *   the main model. 
 * </ul>
 *
 * <p>Model data provided:
 * <ul>
 *   <li>the localized message (in a sub-model whose name is
 *   configurable trough the <code>modelName</code> JavaBean property)
 * </ul>
 * 
 */
public class IndexFileMessageProvider implements ReferenceDataProvider {

    private String localizationKey;
    private String modelName;


    @Required
    public void setLocalizationKey(String localizationKey) {
        this.localizationKey = localizationKey;
    }

    @Required
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) {

        RequestContext context = RequestContext.getRequestContext(request);
        
        Path index = context.getIndexFileURI();
        if (index == null) {
            return;
        }
        org.springframework.web.servlet.support.RequestContext springContext =
            new org.springframework.web.servlet.support.RequestContext(request);
        String message = springContext.getMessage(this.localizationKey,
                new Object[] { index.getName() },
                this.localizationKey);

        model.put(this.modelName, message);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("modelName : ").append(this.modelName);
        sb.append(", localizationKey : ").append(this.localizationKey);
        sb.append("}");
        return sb.toString();
    }

}
