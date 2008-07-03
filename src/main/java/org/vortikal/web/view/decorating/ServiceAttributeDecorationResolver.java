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
package org.vortikal.web.view.decorating;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;
import org.vortikal.web.RequestContext;
import org.vortikal.web.service.Service;

public class ServiceAttributeDecorationResolver implements DecorationResolver {

    private String attributeName;
    private TemplateManager templateManager;

    @Required public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    @Required public void setTemplateManager(TemplateManager templateManager) {
        this.templateManager = templateManager;
    }
    
    public DecorationDescriptor resolve(HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
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
        
        final Template t = this.templateManager.getTemplate(templateName);
        return new DecorationDescriptor() {
            public boolean decorate() {
                return t != null;
            }

            public Template getTemplate() {
                return t;
            }

            public boolean parse() {
                return false;
            }

            public boolean tidy() {
                return false;
            }
            
            public String toString() {
                return this.getClass().getName() + ": [template: " + t + "]";
            }
        };
    }
}
