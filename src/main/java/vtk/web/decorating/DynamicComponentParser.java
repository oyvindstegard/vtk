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
package vtk.web.decorating;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import vtk.text.tl.Context;
import vtk.text.tl.DirectiveHandler;
import vtk.text.tl.DirectiveState;
import vtk.text.tl.DirectiveValidator;
import vtk.text.tl.Literal;
import vtk.text.tl.Node;
import vtk.text.tl.NodeList;
import vtk.text.tl.Parser.Directive;
import vtk.text.tl.TemplateContext;
import vtk.text.tl.TemplateHandler;
import vtk.text.tl.TemplateParser;
import vtk.text.tl.Token;
import vtk.util.io.InputSource;
import vtk.web.decorating.components.DynamicDecoratorComponent;

public class DynamicComponentParser {

    private List<DirectiveHandler> directiveHandlers;
    
    public DynamicComponentParser(List<DirectiveHandler> handlers) {
        this.directiveHandlers = new ArrayList<>(handlers);
    }
    
    public DecoratorComponent compile(final String namespace, final String name, 
            final InputSource source) throws IOException {
        InputStream inputStream = source.getInputStream();
        Reader reader = new InputStreamReader(inputStream);

        List<DirectiveHandler> handlers = 
                new ArrayList<DirectiveHandler>(this.directiveHandlers);
        
        final DynamicDecoratorComponent.Builder builder = 
                DynamicDecoratorComponent.newBuilder()
                    .namespace(namespace).name(name);
        
        final AtomicInteger componentTags = new AtomicInteger(0);

        DirectiveHandler componentHandler = new DirectiveHandler() {
            @Override
            public String[] tokens() {
                return new String[] { 
                        "messages", "/messages", 
                        "component", "/component", 
                        "description", "/description", 
                        "parameter",
                        "error"
                        };
            }
            
            @Override
            public void directive(Directive directive, 
                    TemplateContext context) {

                String name = directive.name();
                builder.context(directive);
                
                if ("component".equals(name)) {
                    componentStart(directive, context, builder);
                } else if ("/component".equals(name)) {
                    componentEnd(context, builder);
                } else if ("description".equals(name)) {
                    descriptionStart(directive, context);
                } else if ("/description".equals(name)) {
                    descriptionEnd(context, builder);
                } else if ("parameter".equals(name)) {
                    parameter(directive, context, builder);
                } else if ("error".equals(name)) {
                    error(directive, context);
                } else if ("messages".equals(name)) {
                    messagesStart(directive, context);
                } else if ("/messages".equals(name)) {
                    messagesEnd(context);
                } else {
                    unknown(directive, context);
                }
            }
            
            private void componentStart(Directive directive, 
                    TemplateContext context, 
                    DynamicDecoratorComponent.Builder builder) {

                if (context.level() != 0) {
                    context.error("[component] cannot be a child of "
                            + "[" + context.top().directive().name() + "]");
                    return;
                }
                if (componentTags.incrementAndGet() != 1) {
                    context.error("Only one [component] allowed");
                    return;
                }
                context.push(new DirectiveState(directive));
            }
            
            private void componentEnd(TemplateContext context, 
                    DynamicDecoratorComponent.Builder builder) {
                DirectiveState state = context.pop();
                if (state == null || !"component".equals(state.directive().name())) {
                    context.error("Misplaced [/component]");
                    return;
                }
                builder.body(state.nodes());
            }
            
            private void descriptionStart(Directive directive, TemplateContext context) {
                DirectiveState state = context.top();
                if (state == null || !"component".equals(state.directive().name())) {
                    context.error("Misplaced [description]");
                    return;
                }
                context.push(new DirectiveState(directive));
            }
            
            private void descriptionEnd(TemplateContext context, 
                    DynamicDecoratorComponent.Builder builder) {
                DirectiveState state = context.pop();
                if (state == null || !"description".equals(state.directive().name())) {
                    context.error("Misplaced [/description]");
                    return;
                }
                NodeList nodes = state.nodes();
                Context ctx = new Context(Locale.getDefault());
                StringWriter out = new StringWriter();
                for (Node node: nodes) {
                    try {
                        node.render(ctx, out);
                    } catch (Exception e) { 
                        context.error(e.getMessage()); 
                        return; 
                    }
                }
                builder.description(out.toString());
            }
            
            private void parameter(Directive directive, TemplateContext context, 
                    DynamicDecoratorComponent.Builder builder) {
                DirectiveState state = context.top();
                if (state == null || !"component".equals(state.directive().name())) {
                    context.error("Misplaced [parameter]");
                    return;
                }
                List<Token> args = directive.args();
                if (args.size() != 2) {
                    context.error("Malformed parameter directive. "
                            + "Should be: [parameter name description]");
                    return;
                }
                String arg1 = args.get(0).getRawValue();
                Token arg2 = args.get(1);
                if (!(arg2 instanceof Literal) || ((Literal) arg2).getType() != Literal.Type.STRING) {
                    context.error("[parameter] description must be a string");
                    return;
                }
                builder.parameter(arg1, ((Literal) arg2).getStringValue());
            }
            
            private void error(Directive directive, TemplateContext context) {
                List<Token> args = directive.args();
                if (args.size() != 1) {
                    context.error("[error]: missing argument");
                    return;
                }
                final Token arg = args.get(0);
                context.add(new Node() {
                    @Override
                    public boolean render(Context ctx, Writer out)
                            throws Exception {
                        Object msg = arg.getValue(ctx); 
                        out.write(msg != null ? msg.toString() : "null");
                        return false;
                    }});
            }
            
            private void messagesStart(Directive directive, TemplateContext context) {
                if (context.level() != 0) {
                    context.error("[messages] must be a top-level directive");
                    return;
                }
                context.push(new DirectiveState(directive));
            }
            
            private void messagesEnd(TemplateContext context) {
                DirectiveState state = context.pop();
                if (state == null || !"messages".equals(state.directive().name())) {
                    context.error("Misplaced [/messages]");
                    return;
                }
                NodeList nodes = state.nodes();
                Context ctx = new Context(Locale.getDefault());
                StringWriter out = new StringWriter();
                for (Node node: nodes) {
                    try {
                        node.render(ctx, out);
                    } catch (Exception e) { 
                        context.error(e.getMessage()); 
                        return; 
                    }
                }
                Map<String, Object> messages = new HashMap<>();
                addMessages(out.toString(), messages);
                builder.messages(messages);
            }
            
            private void unknown(Directive directive, TemplateContext context) {
                final String msg = source + ":" 
                        + directive.line() + ": Unknown directive: [" 
                        + directive.name() + "]";
                context.add(new Node() {
                    @Override
                    public boolean render(Context ctx, Writer out)
                            throws Exception {
                        out.write(msg);
                        return true;
                    }
                });
            }
        };
        
        handlers.add(componentHandler);
        
        DirectiveValidator validator = new DirectiveValidator() {
            @Override
            public void validate(Directive directive, TemplateContext context) {
                // Ensure no directives inside [description]
                // Rest of validation is performed in handleXXX() methods
                DirectiveState state = context.top();
                if (state != null && "description".equals(state.directive().name())) {
                    if (!"/description".equals(directive.name()))
                        context.error("Directive [" + directive.name() 
                                + "] not allowed inside [description]");
                }
            }
        };
        
        final List<String> errors = new ArrayList<String>();
        TemplateParser parser = new TemplateParser(reader, handlers, 
                componentHandler, validator, new TemplateHandler() {
            @Override
            public void success(NodeList nodeList) { }
            @Override
            public void error(String message, int line) {
                errors.add(source + ":" + line + ": " + message);
            }
        });

        parser.parse();
        
        if (!errors.isEmpty()) {
            return errorComponent(namespace, name, errors);
        }
        
        try {
            DynamicDecoratorComponent component = builder.build();
            return component;
        } catch (Exception e) {
            String message = e.getMessage();
            Directive context = builder.context();
            if (context != null) {
                message = source + ":" + context.line() + ": " + message;
            }
            message = "Error compiling component " + name + ": " + message;
            return errorComponent(namespace, name, message);
        }
    }
    
    private DecoratorComponent errorComponent(String namespace, String name, List<String> messages) {
        String message = "";
        for (String s: messages) 
            if (message.length() > 0) { message = (message + ", " + s); } 
            else message = s;
        return errorComponent(namespace, name, message);
    }
    
    private DecoratorComponent errorComponent(String namespace, String name, final String message) {
        DynamicDecoratorComponent.Builder builder = DynamicDecoratorComponent.newBuilder();
        builder.name(name);
        builder.namespace(namespace);
        builder.description("");
        builder.body(new NodeList(new Node() {
            @Override
            public boolean render(Context ctx, Writer out) throws Exception {
                out.write(message);
                return true;
            }}));
        return builder.build();
    }

    private void addMessages(String string, Map<String, Object> map) {
        try {
            BufferedReader reader = new BufferedReader(new StringReader(string));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                int eqPos = line.indexOf('=');
                if (eqPos < 1) continue;
                String key = line.substring(0, eqPos).trim();
                String val = line.substring(eqPos + 1).replace("\\=", "=").trim();
                
                String[] elems = key.split("\\.");
                Map<String, Object> cur = map;
                for (int i = 0; i < elems.length - 1; i++) {
                    String elem = elems[i];
                    Map<String, Object> newMap = null;
                    if (!cur.containsKey(elem)) {
                        newMap = new HashMap<String, Object>();
                        cur.put(elem, newMap);
                    } else {
                        Object existing = cur.get(elem);
                        if (existing instanceof Map<?,?>) {
                            newMap = (Map<String, Object>) existing;
                        } else {
                            newMap = new HashMap<String, Object>();
                            cur.put(elem, newMap);
                        }
                    }
                    cur = newMap;
                }
                cur.put(elems[elems.length -1], val);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }}
