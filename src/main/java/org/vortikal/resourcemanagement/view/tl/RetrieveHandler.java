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

import org.vortikal.repository.Path;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.resourcemanagement.view.StructuredResourceDisplayController;
import org.vortikal.security.SecurityContext;
import org.vortikal.text.tl.Context;
import org.vortikal.text.tl.Symbol;
import org.vortikal.text.tl.expr.Function;
import org.vortikal.web.RequestContext;

public class RetrieveHandler extends Function {

    private Repository repository;

    public RetrieveHandler(Symbol symbol, Repository repository) {
        super(symbol, 1);
        this.repository = repository;
    }

    @Override
    public Object eval(Context ctx, Object... args) throws Exception {

        Object arg = args[0];
        Resource resource;
        String ref = arg.toString();
        RequestContext requestContext = RequestContext.getRequestContext();
        if (ref.equals(".")) {
            HttpServletRequest request = requestContext.getServletRequest();
            Object o = request.getAttribute(StructuredResourceDisplayController.MVC_MODEL_REQ_ATTR);
            if (o == null) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> model = (Map<String, Object>) o;
            resource = (Resource) model.get("resource");
        } else {
            try {
                Path uri;
                if (!ref.startsWith("/")) {
                    uri = requestContext.getResourceURI().getParent().expand(ref);
                } else {
                    uri = Path.fromString(ref);
                }
                String token = SecurityContext.getSecurityContext().getToken();
                resource = repository.retrieve(token, uri, true);
            } catch (Throwable t) {
                return null;
            }
        }
        return resource;
    }

}
