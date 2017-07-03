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

import java.util.Optional;

import vtk.repository.Path;
import vtk.text.tl.Context;
import vtk.text.tl.Symbol;
import vtk.text.tl.expr.Function;
import vtk.web.RequestContext;
import vtk.web.decorating.tl.DomainTypes.Failure;
import vtk.web.decorating.tl.DomainTypes.RequestContextType;
import vtk.web.decorating.tl.DomainTypes.Success;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class ServiceURLFunction extends Function {
    
    public ServiceURLFunction(Symbol symbol) {
        super(symbol, 3);
    }

    @Override
    public Object eval(Context ctx, Object... args) {
        // args: (RequestContext, Path, Service)
        Object arg0 = args[0];
        Object arg1 = args[1];
        Object arg2 = args[2];
        
        if (!(arg0 instanceof RequestContextType))
            return new Failure<>("Not a request context: " + arg0);

        if (arg1 == null || arg2 == null)
            return new Failure<>("NULL argument(s)");
        String path = arg1.toString();
        String serviceName = arg2.toString();

        try {
            RequestContext requestContext = ((RequestContextType) arg0).requestContext();
            Optional<Service> service = requestContext.service(serviceName);
            
            Optional<URL> url = service.map(s -> s.urlConstructor(requestContext.getRequestURL())
                    .withURI(Path.fromString(path))
                    .constructURL());
            
            return new Success<>(url.orElseThrow(() -> 
                new IllegalArgumentException("Service '" + serviceName + "' not found"))); 
        }
        catch (Throwable t) {
            return new Failure<>(t);
        }
    }

}
