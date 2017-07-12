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
package vtk.web.view;

import java.util.Locale;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.RequestContext;

public class PropertyAccessor {

    private ResourceTypeTree resourceTypeTree;

    public String propertyValue(HttpServletRequest request, String uri, 
            String prefix, String name, String format) {

        RequestContext requestContext = RequestContext.getRequestContext(request);
        Path path = requestContext.getResourceURI();

        if (uri != null && !uri.equals("")) {
            if (uri.startsWith("/")) {
                path = Path.fromString(uri);
            } else {
                path = requestContext.getCurrentCollection();
                path = path.expand(uri);
            }
        }

        if (prefix != null && prefix.equals("")) {
            prefix = null;
        }

        PropertyTypeDefinition def =
            resourceTypeTree.getPropertyDefinitionByPrefix(prefix, name);

        if (def == null) {
            return "";
        }

        if (format != null && format.equals("")) {
            format = null;
        }
        String token = requestContext.getSecurityToken();
        Locale locale = 
            new org.springframework.web.servlet.support.RequestContext(request, request.getServletContext()).getLocale();
        Repository repository = requestContext.getRepository();
        try {
            Resource resource = repository.retrieve(token, path, true);
            Property prop = resource.getProperty(def);
            if (prop != null)
                return prop.getFormattedValue(format, locale);
        } catch (Exception e) { }
        return "";
    }

    @Required public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }
}
