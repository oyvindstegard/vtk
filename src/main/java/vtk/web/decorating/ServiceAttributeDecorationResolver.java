/* Copyright (c) 2008, University of Oslo, Norway
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
package vtk.web.decorating;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import vtk.web.RequestContext;
import vtk.web.service.Service;

public class ServiceAttributeDecorationResolver implements DecorationResolver {
    private String attributeName;
    private TemplateManager templateManager;
    
    public ServiceAttributeDecorationResolver(String attributeName, TemplateManager templateManager) {
        this.attributeName = Objects.
                requireNonNull(attributeName, "Attribute name cannot be null");
        this.templateManager = Objects.
                requireNonNull(templateManager, "Template manager cannot be null");
    }

    public DecorationDescriptor resolve(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Service service = requestContext.getService();
        
        String templateName = null;
        while (service != null) {
            Object attribute = service.getAttribute(this.attributeName);
            if (attribute != null) {
                templateName = (String) attribute;
                break;
            }
            service = service.getParent();
        }
        if (templateName == null) {
            return null;
        }
        
        final Optional<Template> t = this.templateManager.getTemplate(templateName);
        return new DecorationDescriptor() {
            public boolean decorate() {
                return t.isPresent();
            }

            public List<Template> getTemplates() {
                return Collections.singletonList(t.get());
            }

            public boolean parse() {
                return false;
            }

            public boolean tidy() {
                return false;
            }
            
            public Map<String, Object> getParameters(Template template) {
                return Collections.emptyMap();
            }
            
            @Override
            public String toString() {
                return this.getClass().getName() + ": [template: " + t + "]";
            }
        };
    }
}
