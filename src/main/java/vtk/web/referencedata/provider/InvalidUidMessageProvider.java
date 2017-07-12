/* Copyright (c) 2011, University of Oslo, Norway
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.security.Principal;
import vtk.security.Principal.Type;
import vtk.security.PrincipalFactory;
import vtk.security.SecurityContext;
import vtk.web.RequestContext;
import vtk.web.referencedata.ReferenceDataProvider;

public class InvalidUidMessageProvider implements ReferenceDataProvider {

    private String localizationKey;
    private String modelName;
    private ResourceTypeTree resourceTypeTree;
    private PrincipalFactory principalFactory;

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) {

        RequestContext context = RequestContext.getRequestContext(request);
        Repository repository = context.getRepository();
        Path resourcePath = context.getResourceURI();
        String token = SecurityContext.getSecurityContext().getToken();
        try {
            Resource resource = repository.retrieve(token, resourcePath, false);
            PropertyTypeDefinition ptd = this.resourceTypeTree.getPropertyDefinitionByName("resource:username");
            if (ptd == null) {
                return;
            }
            Property prop = resource.getProperty(ptd);
            if (prop == null) {
                return;
            }
            String username = prop.getStringValue();
            Principal principal = this.principalFactory.getPrincipal(username, Type.USER);
            if (principal != null && principal.getMetadata() != null) {
                return;
            }

            org.springframework.web.servlet.support.RequestContext springContext = new org.springframework.web.servlet.support.RequestContext(
                    request);
            String message = springContext
                    .getMessage(this.localizationKey, new Object[] { username }, this.localizationKey);

            model.put(this.modelName, message);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Required
    public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    @Required
    public void setPrincipalFactory(PrincipalFactory principalFactory) {
        this.principalFactory = principalFactory;
    }

    @Required
    public void setLocalizationKey(String localizationKey) {
        this.localizationKey = localizationKey;
    }

    @Required
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

}
