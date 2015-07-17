/* Copyright (c) 2004, 2008, University of Oslo, Norway
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
package vtk.web.view.freemarker;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.view.freemarker.FreeMarkerView;

import vtk.web.referencedata.ReferenceDataProvider;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.SimpleHash;
import freemarker.template.Template;
import freemarker.template.TemplateException;


/**
 * An extension of the FreeMarkerView provided in the Spring Framework.
 * 
 * <p>
 * Configurable JavaBean properties:
 * <ul>
 * <li><code>debug</code> - a boolean indicating debug mode. When set to
 * <code>true</code>, an entry in the model will be added with key
 * <code>dumpedModel</code>, containing a string dump of the original model.
 * </ul>
 */
public class FreeMarkerViewRenderer extends FreeMarkerView {

    private boolean debug = false;
    private ReferenceDataProvider[] referenceDataProviders;
    private LocaleResolver resourceLocaleResolver;
    private String repositoryID;
    private Integer status = null;


    public void setResourceLocaleResolver(LocaleResolver resourceLocaleResolver) {
        this.resourceLocaleResolver = resourceLocaleResolver;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }
    
    public void setStatus(int status) {
        this.status = status;
    }
    
    @Override
    public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
        Map<String, Object> m = (Map<String, Object>) model;
        if (referenceDataProviders != null) {
            for (ReferenceDataProvider p: referenceDataProviders) {
                p.referenceData(m, request);
            }
        }
        super.render(model, request, response);
    }

    
    @Override
    protected void processTemplate(Template template, SimpleHash model, HttpServletResponse response)
            throws IOException, TemplateException {
        if (status != null) response.setStatus(status);
        
        if (this.debug) {
            String debugModel = model.toString();
            model.put("dumpedModel", debugModel);
        }
        model.put("debug", Boolean.valueOf(this.debug));
        if (this.resourceLocaleResolver != null) {
            model.put("resourceLocaleResolver", this.resourceLocaleResolver);
        }

        model.put("repositoryID", this.repositoryID);
        model.put("statics", BeansWrapper.getDefaultInstance().getStaticModels());
        super.processTemplate(template, model, response);
    }
    
    @Override
    protected void exposeModelAsRequestAttributes(Map<String, Object> model,
            HttpServletRequest request) throws Exception {
        
        // Prevent entire model from being exposed as request attributes. 
        // This causes leaking of state between view invocations.
    }


    public void setReferenceDataProviders(ReferenceDataProvider[] referenceDataProviders) {
        this.referenceDataProviders = referenceDataProviders;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(" + this.getUrl() + ")";
    }

    public void setRepositoryID(String repositoryID) {
        this.repositoryID = repositoryID;
    }
}
