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
package vtk.web;

import java.io.Reader;
import java.io.Writer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.support.RequestContextUtils;

import vtk.text.tl.Context;
import vtk.text.tl.DirectiveHandler;
import vtk.text.tl.IfHandler;
import vtk.text.tl.Node;
import vtk.text.tl.NodeList;
import vtk.text.tl.TemplateHandler;
import vtk.text.tl.TemplateParser;
import vtk.text.tl.ValHandler;
import vtk.text.tl.expr.Expression;
import vtk.util.Result;
import vtk.util.io.InputSource;
import vtk.util.io.InputSourceProvider;
import vtk.web.referencedata.ReferenceDataProvider;

public class ActionTemplateHandler implements HttpRequestHandler {
    private String parameter;
    private List<ReferenceDataProvider> referenceDataProviders;
    private TemplateProvider templateProvider;
    
    public ActionTemplateHandler(InputSourceProvider provider, String parameter, 
            List<ReferenceDataProvider> referenceDataProviders) {
        
        this.templateProvider = new TemplateProvider(Objects.requireNonNull(
                provider, "Constructor argument 'provider' is null"), this::parse);
        
        this.parameter = Objects.requireNonNull(
                parameter, "Constructor argument 'parameter' is null");
        
        this.referenceDataProviders = new ArrayList<>(Objects.requireNonNull(
                referenceDataProviders, 
                "Constructor argument 'referenceDataProviders' is null"));
    }
    
    
    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) {
        Result<String> templateName = templateName(request);
        Result<NodeList> template = templateName.flatMap(name -> templateProvider.apply(name));
        Result<Boolean> rendered = template.flatMap(nodes -> render(nodes, request, response));
        if (rendered.failure.isPresent()) {
            throw new RuntimeException(rendered.failure.get());
        }
    }
    
    private static class CompiledTemplate {
        public final Result<NodeList> template;
        public final Instant timestamp;
        public CompiledTemplate(Result<NodeList> template, Instant timestamp) {
            this.template = template;
            this.timestamp = timestamp;
        }
    }
    
    private static class TemplateProvider implements Function<String, Result<NodeList>> {
        private InputSourceProvider sourceProvider;
        private Map<String, CompiledTemplate> cache = new HashMap<>();
        BiFunction<Reader, String, Result<NodeList>> parser;
        
        public TemplateProvider(InputSourceProvider sourceProvider, 
                BiFunction<Reader, String, Result<NodeList>> parser) {
            this.sourceProvider = sourceProvider;
            this.parser = parser;
        }

        @Override
        public Result<NodeList> apply(String name) {
            Instant now = Instant.now();
            Result<InputSource> source = sourceProvider.apply(name);
            CompiledTemplate cached = cache.get(name);
            
            if (cached != null && source.result.isPresent()) {
                Optional<Instant> sourceModified = source.result.get().getLastModified();
                if (sourceModified.isPresent() && !sourceModified.get().isAfter(cached.timestamp)) {
                    return cached.template;
                }
            }
            Result<NodeList> template = source.flatMap(inputSource ->
                  Result.attempt(() -> inputSource.getReader()).flatMap(reader -> 
                    parser.apply(reader, inputSource.getID())));
            
            CompiledTemplate compiledTemplate = new CompiledTemplate(template, now);
            cache.put(name, compiledTemplate);
            
            return compiledTemplate.template;
        }
    }
    
    
    private Result<String> templateName(HttpServletRequest request) {
        return Result.attempt(() -> Objects.requireNonNull(
            request.getParameter(parameter), "Request parameter '" 
                        + parameter + "' is required"))
        .flatMap(str -> Result.attempt(()  -> {
            if (str.indexOf('/') != -1) 
                throw new IllegalArgumentException(
                    "Invalid value for request parameter '" + parameter 
                    + "': " + str);
            else return str + ".tl";}));
    }
    
    private static class TemplateHolder {
        public NodeList nodes;
    }
    
    private Result<NodeList> parse(Reader reader, String identifier) {
        Expression.FunctionResolver functionResolver = new Expression.FunctionResolver();
        List<DirectiveHandler> handlers = Arrays.asList(
                new IfHandler(functionResolver),
                new ValHandler(null, functionResolver)
        );
        final TemplateHolder nodes = new TemplateHolder();
        TemplateParser parser = new TemplateParser(reader, handlers, new TemplateHandler() {
            @Override
            public void success(NodeList nodeList) {
                nodes.nodes = nodeList;
            }
            @Override
            public void error(String message, int line) {
                nodes.nodes = new NodeList();
                nodes.nodes.add(new Node() {
                    @Override
                    public boolean render(Context ctx, Writer out) throws Exception {
                        out.write(String.format(
                                "Template parse error:\n---\n%s\n---\nLine %d in file \"%s\"",
                                message, line, identifier
                        ));
                        return true;
                    }
                });
            }
        });
        return Result.attempt(() -> {
            parser.parse();
             return nodes.nodes;
        });
    }
    
    Result<Boolean> render(NodeList nodeList, HttpServletRequest request, HttpServletResponse response) {
        return Result.attempt(() -> {
          Locale locale = RequestContextUtils.getLocale(request);
          Context ctx = new Context(locale);

          Map<String, Object> model = new HashMap<>();
          response.setContentType("text/html;charset=utf-8");
          try {
              for (ReferenceDataProvider provider: referenceDataProviders) {
                  provider.referenceData(model, request);
              }
              for (String key: model.keySet()) {
                  ctx.define(key, model.get(key), true);
              }
              boolean renderResult = nodeList.render(ctx, response.getWriter());
              response.flushBuffer();
              return renderResult;
          }
          catch (Exception e) {
              throw new RuntimeException(e);
          }
        });
    }

}
