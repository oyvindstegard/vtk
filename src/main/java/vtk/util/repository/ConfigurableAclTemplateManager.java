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
package vtk.util.repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.jetty.util.IO;

import vtk.repository.Acl;
import vtk.repository.Path;
import vtk.repository.Privilege;
import vtk.security.Principal;
import vtk.security.PrincipalImpl;
import vtk.util.Result;
import vtk.util.text.Json;
import vtk.util.text.Json.MapContainer;
import vtk.util.text.JsonStreamer;
import vtk.util.text.SimpleTemplate;

public class ConfigurableAclTemplateManager 
    implements AclTemplateManager, Consumer<Result<InputStream>> {
    
    private Function<Path,Result<Acl>> retrieve;
    private Result<Map<Path, Result<Template>>> config = 
            Result.success(Collections.emptyMap());

    
    public ConfigurableAclTemplateManager(Function<Path,Result<Acl>> retrieve) {
        this.retrieve = retrieve;
    }

    @Override
    public Optional<AclTemplate> template(Path uri) {
  
        Map<Path, Result<Template>> config = this.config.result()
                .orElseThrow(() -> new IllegalStateException(this.config.failure().get()));
        
        Result<Template> entry = config.get(uri);
        if (entry == null) {
            return Optional.empty();
        }
        Template template = entry.result()
                .orElseThrow(() -> new IllegalStateException(entry.failure().get()));

        Acl base = retrieve.apply(uri)
            .recover(err -> Acl.EMPTY_ACL)
            .result()
            .get();
            
        AclTemplate result = principal -> template.apply(principal, base);
        return Optional.of(result);
    }
    
    private static class Template implements BiFunction<Principal, Acl ,Acl> {
        private SimpleTemplate template;
        private String type;
        
        public Template(SimpleTemplate template, String type) {
            this.template = template;
            this.type = type;
        }
        
        @Override
        public Acl apply(Principal principal, Acl base) {
            Function<String,String> resolver = name -> {
                if ("user".equals(name)) {
                    return "\"" + principal.getQualifiedName() + "\"";
                }
                throw new IllegalArgumentException("Unknown identifier: " + name);
            };
            StringBuilder buffer = new StringBuilder();
            template.apply(resolver, str -> buffer.append(str));
            
            MapContainer json = Json.parseToContainer(buffer.toString()).asObject();
            Function<MapContainer, Acl> aclFactory =
                    ResourceMappers.defaultAclJsonMapper(
                            (str, type) -> new PrincipalImpl(str, type));
            
            Acl acl = aclFactory.apply(json);
            if ("additive".equals(type)) {
                Acl result = base;
                for (Privilege privilege: acl.getActions()) {
                    for (Principal p: acl.getPrincipalSet(privilege)) {
                        result = result.addEntry(privilege, p);
                    }
                }
                acl = result;
            }
            return acl;
        }
        
        @Override
        public String toString() {
            return getClass().getSimpleName() 
                    + "(" + template + ")";
        }
    }

    @Override
    public void accept(Result<InputStream> result) {
        Result<String> stringResult = result.flatMap(stream -> Result.attempt(() -> {
            try {
                return IO.toString(stream, StandardCharsets.UTF_8);
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }));
        
        Result<MapContainer> mapResult = stringResult.flatMap(str -> 
            Result.attempt(() -> Json.parseToContainer(str).asObject()));
        
        Result<Map<Path, Result<Template>>> templatesMap = mapResult.flatMap(map -> {
            return Result.attempt(() -> {
            Map<Path,Result<Template>> entries = new LinkedHashMap<>();
            
            for (String key: map.keySet()) {
                Path uri = Path.fromString(key);
                entries.put(uri, Result.attempt(() -> {
                    MapContainer objectValue = Objects
                            .requireNonNull(map.objectValue(key), 
                                    "Entry for key '" + key + "' is NULL");
                    MapContainer aclObject = Objects
                            .requireNonNull(objectValue.objectValue("acl"), 
                                    "Missing 'acl' entry in object: " + objectValue);
                    
                    aclObject.keySet().forEach(priv -> {
                        if (!Privilege.exists(priv)) {
                            throw new IllegalArgumentException("Invalid privilege: '" + priv + "'");
                        }   
                    });

                    SimpleTemplate template = SimpleTemplate.compile(JsonStreamer.toJson(aclObject), 
                            "\"${", "}\"", SimpleTemplate.ESC_NO_HANDLING);
                    Optional<String> type = objectValue.optStringValue("type");
                    if (type.isPresent() && !"additive".equals(type.get())) {
                        throw new IllegalArgumentException(
                                "Invalid field value for 'type': '" + type.get() + "'");
                    }
                    return new Template(template, type.orElse(null));
                }));
            }
            return entries;
            });
        });
        this.config = templatesMap;
    }
}
