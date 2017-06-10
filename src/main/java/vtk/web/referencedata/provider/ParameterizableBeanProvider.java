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
package vtk.web.referencedata.provider;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;

import vtk.web.referencedata.ReferenceDataProvider;


/**
 * Reference data provider for putting arbitrary beans in the model.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>object</code> - the {@link Object bean} to publish
 *   <li><code>modelName</code> - the object's key to in the model
 * </ul>
 */
public class ParameterizableBeanProvider implements ReferenceDataProvider, InitializingBean {
    
    private Object object;
    private String modelName;

    public void setObject(Object object) {
        this.object = object;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
    

    public void afterPropertiesSet() {
        if (this.object == null) {
            throw new BeanInitializationException(
                "JavaBean property 'object' not specified");
        }
        if (this.modelName == null) {
            throw new BeanInitializationException(
                "JavaBean property 'modelName' not specified");
        }
    }
    

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) {
        model.put(this.modelName, this.object);
    }

}
