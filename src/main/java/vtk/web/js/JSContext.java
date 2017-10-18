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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import vtk.js.JavascriptEngine;
import vtk.repository.Path;
import vtk.util.Result;
import vtk.web.RequestContext;

/**
 * Context class exposed to scripts.
 */
public class JSContext {
    private static Logger logger = LoggerFactory.getLogger(JSContext.class);
    private boolean ended = false;
    private HttpServletRequest request;
    private HttpServletResponse response;    
    private Optional<PrintWriter> writer = Optional.empty();
    private JavascriptEngine engine;
    private CompletableFuture<Void> completionHandle;
    private Map<String, Module> modules;
    
    public JSContext(HttpServletRequest request,
            HttpServletResponse response, 
            CompletableFuture<Void> completionHandle,
            JavascriptEngine engine,
            Map<String, Module> modules) {
        this.request = request;
        this.response = response;
        this.completionHandle = completionHandle;
        this.engine = engine;
        this.modules = modules;
    }
    
    public void status(int sc) {
        response.setStatus(sc);
    }
    
    public void contentType(String contentType) {
        logger.debug("Content-Type: {}", contentType);
        response.setContentType(contentType);
    }
    

    public void end() {
        end(null);
    }
    
    public void end(String str) {
        logger.debug("end({})", str);

        CompletableFuture.supplyAsync(() -> { 
            try {
                if (ended) {
                    return null;
                }
                ended = true;
                if (!writer.isPresent()) {
                    writer = Optional.of(response.getWriter());
                }
                if (str != null) {
                    writer.get().write(str);
                }
                writer.get().flush();
                writer.get().close();
                return Result.success(true);
            }
            catch (IOException e) {
                return Result.failure(e);
            }
        })
        .thenAcceptAsync(result -> {
            if (result.failure.isPresent()) {
                completionHandle.completeExceptionally(result.failure.get());
            }
            else {
                completionHandle.complete(null);
            }
        });
    }
       
   public void write(String str) {
       write(str, null);
   }
   
   public void write(String str, ScriptObjectMirror callback) {
       logger.debug("write({}, {})", str, callback);
       // Writing is done in a buffered response (not blocking), 
       // so no need to do it asynchronously 
       Result<?> writeAttempt = Result.attempt(() -> {
           try {
               if (ended) {
                   throw new IllegalStateException("ctx.end already called");
               }
               if (!writer.isPresent()) {
                   writer = Optional.of(response.getWriter());
               }
               writer.get().write(str);
               return true;
           }
           catch (IOException e) {
               throw new UncheckedIOException(e);
           }
       });
       engine.execute(() -> 
           Result.attempt(() -> {
               // Invoke callback from engine thread
               if (callback != null && callback.isFunction()) {
                   if (writeAttempt.failure.isPresent()) {
                       callback.call(null, writeAttempt.failure.get(), null);
                   }
                   else {
                       callback.call(null, null, writeAttempt.result.get());
                   }
               }
               return true;
           }))
       .thenAcceptAsync(res -> {
           if (res.failure.isPresent()) {
               end(engine.jsError(res.failure.get()));
           }
       });
   }
        
    public Function<String, Object> module = name -> {
        if (!modules.containsKey(name)) {
            throw new IllegalArgumentException("No such module: " + name);
        }
        Module module = modules.get(name);
        return module.instance(this, engine);
    };
    
    public Locale locale() {
        RequestContext requestContext = RequestContext
                .getRequestContext(request);
        return requestContext.getLocale();
    }
    
    public String securityToken() {
        RequestContext requestContext = RequestContext
                .getRequestContext(request);
        return requestContext.getSecurityToken();
    }

    public Path resourceURI() {
        RequestContext requestContext = RequestContext
                .getRequestContext(request);
        return requestContext.getResourceURI();
    }
    
    public Path collection() {
        RequestContext requestContext = RequestContext
                .getRequestContext(request);
        return requestContext.getCurrentCollection();
    }
    
    public String parameter(String name) {
        return request.getParameter(name);
    }
}
