/* Copyright (c) 2017, University of Oslo, Norway
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

import java.util.Objects;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import vtk.repository.Path;
import vtk.repository.Resource;
import vtk.repository.resourcetype.RepositoryAssertion;
import vtk.security.Principal;
import vtk.text.tl.Context;
import vtk.text.tl.Symbol;
import vtk.text.tl.expr.Function;

import vtk.web.RequestContext;
import vtk.web.decorating.DynamicDecoratorTemplate;


/**
 * Adapt any
 * {@link vtk.repository.resourcetype.RepositoryAssertion repository assertion}
 * to a TL predicate function which takes zero or one argument.
 *
 * <p>If an argument is provided, it should be a resource reference. Otherwise
 * the current resource from the request context is used in matching.
 *
 * <p>The wrapped assertion is given the current principal (from request context)
 * as the second argument to {@link RepositoryAssertion#matches(java.util.Optional, java.util.Optional) }.
 *
 * <p>The TL function returns a result object with attributes 'success' and 'value' or 'error', where
 * 'value' is the actual boolean predicate return value on success, and 'error' is the error
 * in case of failure. See {@link vtk.web.decorating.tl.DomainTypes.Result DomainTypes.Result}.
 */
public class RepositoryAssertionFunction extends Function {

    private final RepositoryAssertion assertion;

    /**
     * @param symbol the TL symbol to associate with the function instance
     * @param assertion the repository assertion used for matching resources
     */
    public RepositoryAssertionFunction(Symbol symbol, RepositoryAssertion assertion) {
        super(symbol);
        this.assertion = Objects.requireNonNull(assertion);
    }

    @Override
    public Object eval(Context ctx, Object... args) {
        HttpServletRequest request = (HttpServletRequest)
                ctx.getAttribute(DynamicDecoratorTemplate.SERVLET_REQUEST_CONTEXT_ATTR);
        if (request == null) {
            throw new RuntimeException("Servlet request not found in context by attribute: "
                    + DynamicDecoratorTemplate.SERVLET_REQUEST_CONTEXT_ATTR);
        }
        RequestContext requestContext = RequestContext.getRequestContext(request);

        if (args.length > 1) {
            return new DomainTypes.Failure<>("Error: this function accepts zero or one argument, but " + args.length + " given");
        }
        Path path;
        if (args.length == 1) {
            if (args[0] == null) {
                return new DomainTypes.Failure<>("Resource reference is null");
            }
            DomainTypes.Result<Path> result = DomainTypes.toPath(args[0], requestContext);
            if (!result.isSuccess()) {
                return result;
            }
            path = result.asSuccess().value();

        } else {
            path = requestContext.getResourceURI();
        }

        try {
            Resource resource = requestContext.getRepository().retrieve(requestContext.getSecurityToken(), path, true);
            Principal principal = requestContext.getPrincipal();
            boolean match = assertion.matches(Optional.of(resource), Optional.ofNullable(principal));
            return new DomainTypes.Success<>(match);
        } catch (Throwable t) {
            return new DomainTypes.Failure<>(t);
        }
    }

}
