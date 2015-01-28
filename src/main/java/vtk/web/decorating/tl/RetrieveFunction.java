/* Copyright (c) 2015, University of Oslo, Norway
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
package vtk.web.decorating.tl;

import java.util.HashMap;
import java.util.Map;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.text.tl.Context;
import vtk.text.tl.Symbol;
import vtk.text.tl.expr.Function;
import vtk.util.repository.ResourceToMapConverter;
import vtk.web.RequestContext;

public class RetrieveFunction extends Function {

    public RetrieveFunction(Symbol symbol) {
        super(symbol, 1);
    }
    
    @Override
    public Object eval(Context ctx, Object... args) {

        String ref = args[0].toString();
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        
        try {
            Path uri;
            if (!ref.startsWith("/")) {
                uri = requestContext.getResourceURI().getParent().expand(ref);
            } else {
                uri = Path.fromString(ref);
            }
            String token = requestContext.getSecurityToken();
            Resource resource = repository.retrieve(token, uri, true);
            return success(resource);
        } catch (Throwable t) {
            return error(t);
        }
    }
    
    private static Map<String, Object> success(Resource resource) {
        Map<String, Object> result = new HashMap<>();
        result.put("resource", ResourceToMapConverter.toMap(resource));
        return result;
    }
    
    private static Map<String, Object> error(Throwable t) {
        Map<String, Object> result = new HashMap<>();
        result.put("error", t.getMessage());
        return result;
    }
}
