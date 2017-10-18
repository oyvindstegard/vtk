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
package vtk.web.js;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.HttpRequestHandler;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import vtk.js.JavascriptEngine;
import vtk.repository.Path;
import vtk.util.Result;
import vtk.web.RequestContext;
import vtk.web.servlet.BufferedResponse;

public class JavascriptHandler implements HttpRequestHandler {
    private static Logger logger = LoggerFactory.getLogger(JavascriptHandler.class);
    
    private JavascriptEngine scriptEngine;
    private ScriptURIResolver resolver;

    private Map<String, Module> modules = new HashMap<>();
    
    public static ScriptURIResolver resourceTypeResolver(String prefix) {
        return request -> {
            return Result.attempt(() -> {
                try {
                    RequestContext requestContext = RequestContext.getRequestContext(request);
                    return requestContext.getRepository()
                            .retrieve(requestContext.getSecurityToken(), 
                                    requestContext.getResourceURI(), true);
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            })
            .map(resource -> resource.getResourceType())
            .flatMap(resourceType -> 
                Result.attempt(() -> Path.fromString(prefix).expand(resourceType)));
        };
    }
    
    public static ScriptURIResolver requestParameterResolver(String prefix, String parameter) {
        return request -> {
            return Result.attempt(() -> Objects.requireNonNull(
                    request.getParameter(parameter), "Request parameter '" 
                            + parameter + "' is required"))
                    .flatMap(str -> Result.attempt(()  -> {
                        if (str.indexOf('/') != -1) 
                            throw new IllegalArgumentException(
                                    "Invalid value for request parameter '" + parameter 
                                    + "': " + str);
                        else return str;
                    }))
                    .flatMap(str -> Result.attempt(() -> Path.fromString(prefix).expand(str)));
        };
    }
    
    public JavascriptHandler(JavascriptEngine scriptProvider, ScriptURIResolver uriResolver, 
            Map<String, Module> modules) {
        this.scriptEngine = Objects.requireNonNull(scriptProvider);
        this.resolver = Objects.requireNonNull(uriResolver);
        this.modules = new HashMap<>(Objects.requireNonNull(modules));
    }
    
    private static class CallContext {
        JSContext context;
        @SuppressWarnings("restriction")
        ScriptObjectMirror function;
        CompletableFuture<Void> completionHandle;
        public CallContext(JSContext context, ScriptObjectMirror function, 
                CompletableFuture<Void> completionHandle) {
            this.context = context;
            this.function = function;
            this.completionHandle = completionHandle;
        }
    }

    @SuppressWarnings("restriction")
    @Override
    public void handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        
        Result<Path> scriptLocation = resolver.resolve(request);
        if (scriptLocation.failure.isPresent()) {
            throw new RuntimeException(scriptLocation.failure.get());
        }
        
        BufferedResponse bufferedResponse = new BufferedResponse(200);
        
        CompletableFuture<ScriptObjectMirror> handlerFunction = 
                scriptEngine.compiledFunction(scriptLocation.result.get().toString(), "handler");
        
        CompletableFuture<CallContext> callContextFuture = handlerFunction.thenComposeAsync(result -> {
                CompletableFuture<Void> completionFuture = new CompletableFuture<>();
                JSContext jsContext = new JSContext(request, bufferedResponse, completionFuture, 
                        scriptEngine, modules);
                
                return CompletableFuture.completedFuture(
                        new CallContext(jsContext, result, completionFuture));
        });
        
        CompletableFuture<CallContext> invocationFuture = callContextFuture
                .thenCompose(callCtx -> scriptEngine.execute(() -> {
                    callCtx.function.call(null, callCtx.context);
                    return callCtx;
                }));
        
        CompletableFuture<Void> completion = 
                invocationFuture.thenCompose(callContext -> callContext.completionHandle);
        
        logger.debug("Awaiting completion of Javascript handler for request {}", request);
        try {
            completion.get(10, TimeUnit.SECONDS);
            bufferedResponse.writeTo(response, true);
        }
        catch (Throwable t) {
            response.setStatus(500);
            PrintWriter writer = response.getWriter();
            writer.write(scriptEngine.jsError(t));
            writer.flush();
            writer.close();
        }
    }
}
