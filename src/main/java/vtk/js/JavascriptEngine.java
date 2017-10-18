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
package vtk.js;

import java.io.Reader;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.SimpleScriptContext;

import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import vtk.util.Result;
import vtk.util.io.InputSource;
import vtk.util.io.InputSourceProvider;

public class JavascriptEngine {
    private ScriptEngine engine;
    private InputSourceProvider sourceProvider;
    private Map<String, Script> scripts = new HashMap<>();
    
    private ExecutorService jsExecutor = 
            Executors.newSingleThreadExecutor(new JSThreadFactory());
    
    @SuppressWarnings("restriction")
    public JavascriptEngine(InputSourceProvider sourceProvider) {
        NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        engine = factory.getScriptEngine("--language=es6", "--no-java");
        this.sourceProvider = sourceProvider;
    }
    
    private static final class Script {
        public final Result<CompiledScript> compiled;
        public final Instant timestamp;
        public Script(Result<CompiledScript> script) {
            this.compiled = script;
            this.timestamp = Instant.now();
        }
    }

    @SuppressWarnings("restriction")
    public CompletableFuture<ScriptObjectMirror> compiledFunction(String scriptName, String functionName) {
        CompletableFuture<Script> scriptFuture = script(scriptName);
        
        return scriptFuture.thenComposeAsync(script -> {
            CompletableFuture<ScriptObjectMirror> future = new CompletableFuture<>();
            if (script.compiled.failure.isPresent()) {
                future.completeExceptionally(script.compiled.failure.get());
            }
            else {
                CompiledScript compiledScript = script.compiled.result.get();
                Bindings bindings = engine.createBindings();
                ScriptContext scriptContext = new SimpleScriptContext();
                scriptContext.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
                try {
                    compiledScript.eval(scriptContext);
                    ScriptObjectMirror object = (ScriptObjectMirror) 
                            scriptContext.getBindings(ScriptContext.ENGINE_SCOPE).get(functionName);
                    if (object == null) {
                        throw new RuntimeException("No such object: " + functionName);
                    }
                    if (!object.isFunction()) {
                        throw new RuntimeException("Not a function: " + object);
                    }
                    future.complete(object);
                }
                catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            }
            return future;
        }, jsExecutor);
    }
    
    private CompletableFuture<Script> script(String identifier) {
        
        CompletableFuture<Map<String,Script>> 
            fooFuture = CompletableFuture.supplyAsync(() -> scripts, jsExecutor);
        
        return fooFuture.thenCompose(map -> {
            Script cached = scripts.get(identifier);
            Result<InputSource> source = sourceProvider.apply(identifier);
            if (cached != null) {
                try {
                    if (source.result.isPresent() && source.get().getLastModified().isPresent()) {
                        if (cached.timestamp.isAfter(source.get().getLastModified().get())) {
                            return CompletableFuture.completedFuture(cached);
                        }
                    }
                }
                catch (Throwable t) {
                    CompletableFuture<Script> failure = new CompletableFuture<>();
                    failure.completeExceptionally(t);
                    return failure;
                }
            }
            Result<CompiledScript> compiled = source.flatMap(src -> compile(src.getReader()));
            Script script = new Script(compiled);
            scripts.put(identifier, script);
            return CompletableFuture.completedFuture(script);
        });
    }

    public <T> CompletableFuture<T> execute(Supplier<T> code) {
        return CompletableFuture.supplyAsync(() -> code.get(), jsExecutor);
    }
    
    @SuppressWarnings("restriction")
    public CompletableFuture<Object> toJsObject(Object o) {
        Object converted = ScriptObjectMirror.wrapAsJSONCompatible(o, null);
        return CompletableFuture.completedFuture(converted);
    }
    
    @SuppressWarnings("restriction")
    public String jsError(Throwable t) {
        if (t instanceof jdk.nashorn.internal.runtime.ECMAException) {
            jdk.nashorn.internal.runtime.ECMAException e 
                = (jdk.nashorn.internal.runtime.ECMAException) t;
            return e.getEcmaError().toString() + "\n" + 
                        jdk.nashorn.internal.runtime.ECMAException.getScriptStackString(e);
        }
        else if (t instanceof jdk.nashorn.api.scripting.NashornException) {
            return jdk.nashorn.api.scripting.NashornException.getScriptStackString(t);
        }
        else if (t.getCause() instanceof jdk.nashorn.api.scripting.NashornException) {
            return jdk.nashorn.api.scripting.NashornException.getScriptStackString(t.getCause());
        }
        return t.getMessage();
    }
    
    private Result<CompiledScript> compile(Reader reader) {
        try {
            CompiledScript compiled = ((Compilable) engine).compile(reader);
            return Result.success(compiled);
        }
        catch (Throwable t) {
            return Result.failure(t);
        }
    }
    
    private static class JSThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
          return new Thread(r, "js-engine-" + threadNumber.getAndIncrement());
        }
    }

    
}
