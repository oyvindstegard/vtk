/* Copyright (c) 2005, University of Oslo, Norway
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

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;

import vtk.web.referencedata.ReferenceDataProvider;

/**
 * A reference data provider that puts a static URL in the model under
 * a configurable model name.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *  <li><code>modelName</code> - the name to use for the submodel
 *  <li><code>url</code> - a {@link String} representing the model data
 *  <li><code>descriptionKey</code> - the localization key used to
 *  obtain the URL description
 *  <li><code>target</code> - name of the URL target (for opening
 *  links in another browser window)
 * </ul>
 * 
 * <p>Model data provided (in a sub-model having key configurable by
 * the <code>modelName</code> property):
 * <ul>
 *   <li><code>url</code> - the URL
 *   <li><code>description</code> - the (possibly localized) description
 *   <li><code>target</code> - the target (its value may be null)
 * </ul>
 * 
 */
public class StaticURLProvider implements ReferenceDataProvider, InitializingBean {

    private String modelName = null;
    private String url = null;
    private String descriptionKey = null;
    private String target = null;

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setDescriptionKey(String descriptionKey) {
        this.descriptionKey = descriptionKey;
    }
    
    public void setTarget(String target) {
        this.target = target;
    }
    
    public void afterPropertiesSet() {
        if (this.modelName == null) {
            throw new BeanInitializationException(
                "JavaBean property 'modelName' must be set");
        }
        if (this.descriptionKey == null) {
            throw new BeanInitializationException(
                "JavaBean property 'descriptionKey' must be set");
        }
        if (this.url == null) {
            throw new BeanInitializationException(
                "JavaBean property 'url' must be set");
        }
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void referenceData(Map model, HttpServletRequest request) {


        org.springframework.web.servlet.support.RequestContext springContext =
            new org.springframework.web.servlet.support.RequestContext(request);

        String description = springContext.getMessage(
            this.descriptionKey, this.descriptionKey);

        Map urlModel = new HashMap();
        urlModel.put("url", this.url);
        urlModel.put("description", description);
        urlModel.put("target", this.target);
        model.put(this.modelName, urlModel);
    }
}
