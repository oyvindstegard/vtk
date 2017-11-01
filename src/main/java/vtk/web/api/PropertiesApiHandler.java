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
package vtk.web.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.HttpRequestHandler;

import vtk.repository.AuthorizationException;
import vtk.repository.ContentInputSources;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.RepositoryAction;
import vtk.repository.Resource;
import vtk.repository.ResourceNotFoundException;
import vtk.repository.TypeInfo;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.resourcemanagement.StructuredResource;
import vtk.resourcemanagement.StructuredResourceDescription;
import vtk.resourcemanagement.StructuredResourceManager;
import vtk.resourcemanagement.ValidationResult;
import vtk.util.Result;
import vtk.util.repository.ResourceMappers;
import vtk.util.repository.ResourceMappers.PropertySetMapper;
import vtk.util.text.Json;
import vtk.util.text.JsonStreamer;
import vtk.web.RequestContext;

public class PropertiesApiHandler implements HttpRequestHandler {
    private StructuredResourceManager structuredResourceManager;
    private static Locale LOCALE = Locale.getDefault();
    
    public PropertiesApiHandler(StructuredResourceManager structuredResourceManager) {
        this.structuredResourceManager = structuredResourceManager;
    }
    
    @Override
    public void handleRequest(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Result<Optional<Resource>> res = retrieve(requestContext, requestContext.getResourceURI());
        Result<ApiResponseBuilder> bldr = res.flatMap(opt -> {
           if (!opt.isPresent()) {
               return Result.success(ApiResponseBuilder.notFound(
                       "404 Not Found: " + requestContext.getResourceURI()));
           }
           else {
               Resource resource = opt.get();
               switch (request.getMethod()) {
               case "GET": 
                   return getProperties(request, resource);
               case "PATCH": 
                   return updateProperties(request, resource);
               default: 
                   return unknownMethod(request, resource);
               }
           }
        });
        
        bldr = bldr.recover(ex -> {
            if (ex instanceof InvalidRequestException) {
                return ApiResponseBuilder.badRequest(ex.getMessage());
            }
            if (ex instanceof AuthorizationException) {
                return ApiResponseBuilder.forbidden(ex.getMessage());
            }
            return ApiResponseBuilder.internalServerError(
                    "An unexpected error occurred: " + ex.getMessage());
        });
        
        bldr.forEach(resp -> {
            resp.header("Accept-Patch", "application/merge-patch+json")
                .writeTo(response);
        });
    }
    
    
    private Result<ApiResponseBuilder> unknownMethod(HttpServletRequest request, 
            Resource resource) {
        return Result.success(new ApiResponseBuilder(HttpServletResponse.SC_BAD_REQUEST)
            .header("Content-Type", "text/plain;charset=utf-8")
            .message("Request method " + request.getMethod() 
                    + " not supported: on " + resource.getURI()));
    }
    
    private static abstract class PropertyOperation {
        public final PropertyTypeDefinition propDef;
        public PropertyOperation(PropertyTypeDefinition propDef) {
            this.propDef = propDef;
        }
    }
    
    private static class Delete extends PropertyOperation {
        public Delete(PropertyTypeDefinition propDef) { 
            super(propDef);
        }
        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + propDef.getName() + ")";
        }
    }
    
    private static class Update extends PropertyOperation {
        public final Object value;
        public Update(PropertyTypeDefinition propDef, Object value) {
            super(propDef);
            this.value = value;
        }
        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" 
                    + propDef.getName() + ", " + value + ")";
        }
    }
    
    
    private static class PropertyOperations {
        private List<PropertyOperation> regularProps = new ArrayList<>();
        private List<PropertyOperation> structuredProps = new ArrayList<>();
        
        public PropertyOperations add(PropertyOperation operation) {
            
            List<PropertyOperation> list = operation.propDef.getNamespace() == 
                    Namespace.STRUCTURED_RESOURCE_NAMESPACE ? structuredProps : regularProps;
            
            list.add(operation);
            return this;
        }
        
        public Stream<PropertyOperation> stream() {
            return Stream.concat(regularProps.stream(), structuredProps.stream());
        }
        
        public Result<Resource> apply(Resource resource, RequestContext requestContext, 
                StructuredResourceManager manager) {
            
            Result<Resource> result = Result.success(resource);
            
            if (!structuredProps.isEmpty()) {
                // Properties in the structured resource name space
                // are always derived from the JSON content. The procedure
                // to update such properties is as follows:
                // 
                // 1. fetch JSON content of resource from repository
                // 2. attach input fields to JSON (or remove)
                // 3. store content
                Result<StructuredResource> structured = structuredResource(
                        requestContext, manager, resource);
                structured.map(res -> {
                    for (PropertyOperation op: structuredProps) {
                        if (op instanceof Delete) {
                            res.removeProperty(op.propDef.getName());
                        }
                        else if (op instanceof Update) {
                            Update update = (Update) op;
                            res.addProperty(update.propDef.getName(), update.value);
                        }
                    }
                    return res;
                });
                structured = structured.flatMap(r -> {
                    ValidationResult validation = r.validate(r.toJSON());
                    if (validation.isValid()) return Result.success(r);
                    return Result.failure(new RuntimeException(validation.getErrors().get(0).getMessage()));
                });
                result = structured.flatMap(res -> storeContent(requestContext, res));
            }
            
            result = result.flatMap(res -> {
                return Result.attempt(() -> {
                    regularProps.forEach(op -> {
                        res.removeProperty(op.propDef);
                        if (op instanceof Update) {
                            res.addProperty(createProperty(((Update) op).value, op.propDef));
                        }
                    });
                    try {
                        return requestContext.getRepository()
                                .store(requestContext.getSecurityToken(), null, res);
                    }
                    catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
                
            });
            
            return result;
        }
    }
    
    private Result<ApiResponseBuilder> getProperties(HttpServletRequest request,
            Resource resource) {
        PropertySetMapper<Consumer<JsonStreamer>> mapper = 
                ResourceMappers.jsonStreamer(LOCALE)
                .uris(false).types(false).acls(false).compact(true).build();
        
        ApiResponseBuilder builder = new ApiResponseBuilder(HttpServletResponse.SC_OK)
                .header("Content-Type", "application/json")
                .handler(response -> {
                    try (Writer writer = response.getWriter()) {
                        JsonStreamer streamer = new JsonStreamer(writer, 2, false);
                        mapper.apply(resource).accept(streamer);
                    }
                    catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        return Result.success(builder);
    }
    
    private static Result<StructuredResource> structuredResource(RequestContext requestContext, 
            StructuredResourceManager manager, Resource resource) {
        Result<StructuredResourceDescription> desc = Result.attempt(() -> Objects.requireNonNull(
                manager.get(resource.getResourceType()), "Resource " + resource.getURI() 
                + " is not a structured resource"));
        return desc.flatMap(d -> {
            return getContent(requestContext, resource)
            .flatMap(is -> Result.attempt(() -> d.buildResource(is)));
        });
    }

    private static Result<Resource> storeContent(RequestContext requestContext, 
            StructuredResource structuredResource) {
        return Result.attempt(() -> {
            try {
                byte[] buffer = JsonStreamer
                        .toJson(structuredResource.toJSON(), 3, false)
                        .getBytes("utf-8");
                return requestContext.getRepository()
                        .storeContent(requestContext.getSecurityToken(), 
                                null, requestContext.getResourceURI(),
                                ContentInputSources.fromBytes(buffer));
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    private static Result<InputStream> getContent(RequestContext requestContext, Resource resource) {
        return Result.attempt(() -> {
            try {
                return requestContext.getRepository()
                        .getInputStream(requestContext.getSecurityToken(), resource.getURI(), false);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    private Result<Optional<Resource>> retrieve(RequestContext requestContext, Path uri) {
        return Result.attempt(() -> {
            try {
                return Optional.of(requestContext.getRepository()
                        .retrieve(requestContext.getSecurityToken(), uri, false));
            }
            catch (ResourceNotFoundException e) {
                return Optional.empty();
            }
            catch (AuthorizationException e) {
                throw e;
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    private Result<ApiResponseBuilder> updateProperties(HttpServletRequest request, 
            Resource resource) {
        RequestContext requestContext = RequestContext
                .getRequestContext(request);
        if (!"application/merge-patch+json".equals(request.getContentType())) {
            return Result.success(ApiResponseBuilder.badRequest(
                    "Content-Type 'application/merge-patch+json' is required for PATCH method"));
        }
        Result<InputStream> stream = Result.attempt(() -> {
            try {
                return request.getInputStream();
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        
        Result<Json.MapContainer> json = stream.flatMap(is -> parseJson(is));
        Result<PropertyOperations> operations = json
                .flatMap(body -> propertyOperations(body, resource, requestContext));
        
        Result<ApiResponseBuilder> result = operations.flatMap(ops -> {
            Result<Resource> updated = ops.apply(resource, requestContext, structuredResourceManager);
            return updated.map(res -> {
                return ops.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining("\n", "Properties patched:\n", ""));
        
            })
           .map(msg -> ApiResponseBuilder.ok(msg));
        })
        .recover(err -> {
            if (err instanceof AuthorizationException) {
                return ApiResponseBuilder.forbidden(err.getMessage());
            }
            return ApiResponseBuilder.badRequest(err.getMessage());
        });

        return result;
    }

    
    private Result<PropertyOperations> propertyOperations(Json.MapContainer body, 
            Resource resource, RequestContext requestContext) {
        Result<PropertyOperations> result = Result.attempt(() -> {
            Map<String, Object> propsMap = body.optObjectValue("properties").orElse(null);
            if (propsMap == null) throw new IllegalArgumentException(
                    "Json body must contain a 'properties' entry");
            TypeInfo typeInfo = requestContext.getRepository().getTypeInfo(resource);
            PropertyOperations operations = new PropertyOperations();
            for (String key: propsMap.keySet()) {
                Namespace ns = Namespace.DEFAULT_NAMESPACE;
                String prefix = null;
                String name = key;
                int colonIdx = key.indexOf(':');
                if (colonIdx != -1) {
                    prefix = key.substring(0, colonIdx);
                    name = key.substring(colonIdx + 1);
                }
                if (prefix != null) {
                    ns = typeInfo.getNamespaceByPrefix(prefix);
                }
                PropertyTypeDefinition propDef = typeInfo
                        .getPropertyTypeDefinition(ns, name);
                
                if (propDef.getNamespace() != 
                        Namespace.STRUCTURED_RESOURCE_NAMESPACE) {
                    if (propDef.getProtectionLevel() == RepositoryAction.UNEDITABLE_ACTION) {
                        throw new IllegalArgumentException("Property '" + key + "' is not editable");
                    }
                }
                Object value = propsMap.get(key);
                if (value == null) {
                    if (propDef.isMandatory()) {
                        throw new IllegalArgumentException("Property '" 
                                + key + "' cannot be deleted");
                    }
                    operations.add(new Delete(propDef));
                }
                else {
                    operations.add(new Update(propDef, value));
                }
            }
            return operations;
        });
        return result;
    }
    
    
    private static Property createProperty(Object input, PropertyTypeDefinition propDef) {
        if (propDef.isMultiple()) {
            throw new IllegalArgumentException("Multi-value properties are not suported");
        }
        
        switch (propDef.getType()) {
        case STRING:
        case HTML:
        case IMAGE_REF:
        case INT:
        case BOOLEAN:
        case LONG:
        case PRINCIPAL:
            return propDef.createProperty(input.toString());
        case TIMESTAMP:
            Property prop = propDef.createProperty();
            prop.setValue(propDef.getValueFormatter()
                    .stringToValue(input.toString(), "iso-8601", null));
            return prop;
        case DATE:
            prop = propDef.createProperty();
            prop.setValue(propDef.getValueFormatter()
                    .stringToValue(input.toString(), "iso-8601-short", null));
            return prop;
        case JSON:
            if (input instanceof Json.MapContainer) {
                prop = propDef.createProperty();
                prop.setJSONValue((Json.MapContainer) input);
                return prop;
            }
            throw new IllegalArgumentException(
                    "Only object types are supported for JSON properties");
        case BINARY:
            throw new IllegalArgumentException("Binary properties not supported");
        default:
            throw new IllegalArgumentException("Unknown property type " + propDef.getType());
        }
    }
    
    private static Result<Json.MapContainer> parseJson(InputStream input) {
        return Result.attempt(() -> {
            try {
                return Json.parseToContainer(input).asObject();
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
    
}
