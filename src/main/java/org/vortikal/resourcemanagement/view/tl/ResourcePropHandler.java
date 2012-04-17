/* Copyright (c) 2009, University of Oslo, Norway
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
package org.vortikal.resourcemanagement.view.tl;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.vortikal.repository.Namespace;
import org.vortikal.repository.Path;
import org.vortikal.repository.Property;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.resourcemanagement.view.StructuredResourceDisplayController;
import org.vortikal.text.tl.Context;
import org.vortikal.text.tl.Symbol;
import org.vortikal.text.tl.expr.Function;
import org.vortikal.web.RequestContext;

public class ResourcePropHandler extends Function {

    public ResourcePropHandler(Symbol symbol) {
        super(symbol, 2);
    }

    @Override
    public Object eval(Context ctx, Object... args) {
        final Object arg1 = args[0];
        final Object arg2 = args[1];
        PropertySet resource = null;
        String ref = null;

        if (arg1 instanceof PropertySet) {
            resource = (PropertySet) arg1;
        } else {
            ref = arg1.toString();
        }

        if (resource == null) {
            RequestContext requestContext = RequestContext.getRequestContext();
            if (ref.equals(".")) {
                HttpServletRequest request = requestContext.getServletRequest();
                Object o = request.getAttribute(StructuredResourceDisplayController.MVC_MODEL_REQ_ATTR);
                if (o == null) {
                    throw new RuntimeException("Unable to access MVC model: "
                            + StructuredResourceDisplayController.MVC_MODEL_REQ_ATTR);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> model = (Map<String, Object>) o;
                resource = (Resource) model.get("resource");
            } else {
                Path uri;
                if (!ref.startsWith("/")) {
                    uri = requestContext.getResourceURI().getParent().expand(ref);
                } else {
                    uri = Path.fromString(ref);
                }
                String token = requestContext.getSecurityToken();
                Repository repository = requestContext.getRepository();
                try {
                    resource = repository.retrieve(token, uri, true);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        }
        String propName = arg2.toString();
        if ("uri".equals(propName)) {
            return resource.getURI();
        }

        Property property = resource.getProperty(Namespace.STRUCTURED_RESOURCE_NAMESPACE, propName);
        if (property == null) {
            property = resource.getProperty(Namespace.DEFAULT_NAMESPACE, propName);
        }
        if (property == null) {
            for (Property prop : resource) {
                if (propName.equals(prop.getDefinition().getName())) {
                    property = prop;
                }
            }
        }
        if (property == null) {
            return null;
        }
        if (property.getDefinition().isMultiple()) {
            return property.getValues();
        } else {
            return property.getValue();
        }
    }

}
