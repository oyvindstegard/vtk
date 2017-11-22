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

import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Iteration;
import com.github.mustachejava.reflect.SimpleObjectHandler;

import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import vtk.js.JavascriptEngine;
import vtk.util.Result;

public class Mustache implements Module {
    DefaultMustacheFactory mustacheFactory = new DefaultMustacheFactory();
    
    public Mustache() {
        mustacheFactory.setObjectHandler(new SimpleObjectHandler() {
            @SuppressWarnings("restriction")
            @Override
            public Writer iterate(Iteration iteration, Writer writer,
                    Object object, List<Object> scopes) {
                if (object instanceof ScriptObjectMirror) {
                    ScriptObjectMirror jsobject = (ScriptObjectMirror) object;
                    if (jsobject.isArray()) {
                        for (int i = 0; i < jsobject.size(); i++) {
                            writer = iteration.next(writer, coerce(jsobject.getSlot(i)), scopes);
                        }
                    }
                }
                else {
                    writer = super.iterate(iteration, writer, object, scopes);
                }
                return writer;
            }
        });
    }


    @Override
    public Object instance(JSContext context, JavascriptEngine engine) {
        Map<String, Object> module = new HashMap<>();
        module.put("render", renderFunction(context, engine));
        return module;
    }


    @FunctionalInterface
    public static interface RenderFunction<T,U> {
        void render(T template, U model, U callback);
    }

    @SuppressWarnings("restriction")
    private RenderFunction<String, JSObject> renderFunction(JSContext context, JavascriptEngine engine) {
        return (template, model, callback) -> {
            CompletableFuture.supplyAsync(() -> {
                return Result.attempt(() -> {
                    com.github.mustachejava.Mustache mustache = 
                            mustacheFactory.compile(new StringReader(template), 
                                    "templatename." + template);
                    Writer writer = new StringWriter();
                    mustache.execute(writer, model);
                    return writer.toString();
                });
            })
            .thenAccept(result -> {
                if (callback != null && callback.isFunction()) {
                    engine.execute(() -> {
                        // Invoke callback from engine thread
                        try {
                            if (result.failure().isPresent()) {
                                callback.call(null, result.failure().get(), null);
                            }
                            else {
                                callback.call(null, null, result.result().get());
                            }
                        }
                        catch (Throwable t) {
                            context.end(engine.jsError(t));
                        }
                        return null;
                    });
                }
            });
        };
    }
    
}
