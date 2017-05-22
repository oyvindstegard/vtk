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

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.service.Service;
import vtk.web.service.URL;


/**
 * URL (link) reference data provider. Puts a URL to the supplied path
 * in the model.
 * 
 * <p>Configurable JavaBean properties:
 * <ul>
 *  <li><code>modelName</code> - the name to use for the submodel generated
 *  <li><code>service</code> - the {@link Service} used to construct the URL
 *  <li><code>parameters</code> - optional map of URL parameters
 * </ul>
 * 
 * <p>Model data provided (in the submodel):
 * <ul>
 *   <li><code>url</code> - the URL of the resource
 * </ul>
 * 
 */
public class StaticURIServiceURLProvider implements ReferenceDataProvider {

    private Path path;
    private Service service;
    private String modelName = null;
    private Map<String, String> parameters;
    
    
    @Required public void setPath(String path) {
        this.path = Path.fromString(path);
    }

    @Required public void setModelName(String modelName) {
        this.modelName = modelName;
    }
    
    @Required public void setService(Service service) {
        this.service = service;
    }
    
    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }
    
    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) {

        URL url = this.parameters == null ?
            this.service.constructURL(this.path) :
            this.service.constructURL(this.path, this.parameters);
        

        @SuppressWarnings("unchecked")
        Map<String, Object> urlMap = (Map<String, Object>) model.get(this.modelName);
        if (urlMap == null) {
            urlMap = new HashMap<>();
        }
        urlMap.put("url", url);
        
        model.put(this.modelName, urlMap);
    }
    
}

