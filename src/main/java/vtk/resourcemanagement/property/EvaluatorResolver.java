/* Copyright (c) 2012, University of Oslo, Norway
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
package vtk.resourcemanagement.property;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Namespace;
import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.Resource;
import vtk.repository.content.JsonParseResult;
import vtk.repository.resourcetype.PropertyEvaluator;
import vtk.repository.resourcetype.Value;
import vtk.repository.resourcetype.ValueFormatter;
import vtk.repository.resourcetype.property.PropertyEvaluationException;
import vtk.resourcemanagement.ServiceDefinition;
import vtk.resourcemanagement.StructuredResourceDescription;
import vtk.resourcemanagement.property.DerivedPropertyEvaluationDescription.EvaluationElement;
import vtk.resourcemanagement.property.DerivedPropertyEvaluationDescription.Operator;
import vtk.resourcemanagement.service.ExternalServiceInvoker;
import vtk.resourcemanagement.studies.SharedTextResolver;
import vtk.text.html.HtmlDigester;
import vtk.text.html.HtmlUtil;
import vtk.util.text.Json;
import vtk.util.text.JsonStreamer;

public class EvaluatorResolver {
    private static Logger logger = LoggerFactory.getLogger(EvaluatorResolver.class);

    // XXX Reconsider this whole setup. No good implementation.
    private ExternalServiceInvoker serviceInvoker;
    private HtmlDigester htmlDigester;
    private Locale defaultLocale;
    private SharedTextResolver sharedTextResolver;
    private HtmlUtil htmlUtil;

    public PropertyEvaluator createPropertyEvaluator(PropertyDescription desc,
            StructuredResourceDescription resourceDesc) {
        if (desc instanceof SimplePropertyDescription) {
            return createSimplePropertyEvaluator((SimplePropertyDescription) desc, resourceDesc);
        }
        else if (desc instanceof JSONPropertyDescription) {
            return createJSONPropertyEvaluator((JSONPropertyDescription) desc, resourceDesc);
        }
        else if (desc instanceof BinaryPropertyDescription) {
            return new BinaryPropertyEvaluator(desc);
        }
        return createDerivedPropertyEvaluator((DerivedPropertyDescription) desc, resourceDesc);
    }

    private PropertyEvaluator createSimplePropertyEvaluator(final SimplePropertyDescription desc,
            final StructuredResourceDescription resourceDesc) {
        return new FieldSelectPropertyEvaluator(resourceDesc, desc);
    }

    private PropertyEvaluator createJSONPropertyEvaluator(JSONPropertyDescription desc,
            StructuredResourceDescription resourceDesc) {
        return new FieldSelectPropertyEvaluator(resourceDesc, desc);
    }

    private PropertyEvaluator createDerivedPropertyEvaluator(final DerivedPropertyDescription desc,
            final StructuredResourceDescription resourceDesc) {
        return new DerivedPropertyEvaluator(desc, resourceDesc);
    }

    private class FieldSelectPropertyEvaluator implements PropertyEvaluator {

        private final StructuredResourceDescription resourceDesc;
        private final PropertyDescription propertyDesc;

        private FieldSelectPropertyEvaluator(StructuredResourceDescription resourceDesc, PropertyDescription desc) {
            this.resourceDesc = resourceDesc;
            this.propertyDesc = desc;
        }

        @Override
        public String toString() {
            return getClass().getName() + ": " + propertyDesc.getName();
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean evaluate(Property property, PropertyEvaluationContext ctx) throws PropertyEvaluationException {
            Object value = null;
            String affectingService = propertyDesc.getAffectingService();
            if (affectingService != null) {
                Object o = ctx.getEvaluationAttribute(affectingService);
                if (o != null) {
                    Map<String, Object> map = (Map<String, Object>) o;
                    value = map.get(property.getDefinition().getName());
                    // No value was found for this prop, don't show anything
                    if (value == null) {
                        return false;
                    }
                }
            }

            if (!emptyValue(value)) {
                setPropValue(property, value);
            }
            else {
                JsonParseResult json;
                
                try {
                    json = ctx.getContent()
                            .getContentRepresentation(JsonParseResult.class);
                }
                catch (Exception e) {
                    throw new PropertyEvaluationException(
                            "Unable to get JSON representation of content", e);
                }
                if (json.value.failure.isPresent()) {
                    throw new PropertyEvaluationException(
                            "Unable to get JSON representation of content", json.value.failure.get());
                }
                Optional<Json.MapContainer> document = json.asObject();
                if (!document.isPresent()) {
                    throw new PropertyEvaluationException(
                            "Unable to get JSON Object representation of content");
                }
                String expression = "properties." + property.getDefinition().getName();
                if (propertyDesc instanceof JSONPropertyDescription) {
                    value = Json.select(document.get(), expression);
                    
                    if (value != null) {
                        if (propertyDesc.isMultiple()) {
                            if (!(value instanceof List<?>)) {
                                throw new PropertyEvaluationException(
                                        "Value " + value + " is not a list");
                            }
                            List<Object> tmp = new ArrayList<>();
                            for (Object o: (List<?>) value) {
                                if (o != null) {
                                    tmp.add(JsonStreamer.toJson(o));
                                }
                                else {
                                    tmp.add(null);
                                }
                            }
                            value = tmp;
                        }
                        else {
                            value = JsonStreamer.toJson(value);
                        }
                    }
                }
                else {
                    value = Json.select(document.get(), expression);
                }
                if (emptyValue(value)) {
                    if (propertyDesc.isOverrides()) {
                        // XXX Consider the order of how this is done
                        if (!property.getDefinition().isMandatory()) {
                            // XXX What about structured namespace?
                            ctx.getNewResource().removeProperty(Namespace.DEFAULT_NAMESPACE,
                                    propertyDesc.getOverrides());
                        }
                        else {
                            Property overriddenProp = ctx.getNewResource().getProperty(Namespace.DEFAULT_NAMESPACE,
                                    propertyDesc.getOverrides());
                            if (overriddenProp != null) {
                                if (overriddenProp.getDefinition().isMultiple()) {
                                    setPropValue(property, overriddenProp.getValues());
                                }
                                else {
                                    setPropValue(property, overriddenProp.getValue());
                                }
                            }
                            return true;
                        }
                    }
                    return false;
                }
                setPropValue(property, value);
            }
            invokeService(property, ctx, propertyDesc, resourceDesc);
            return true;
        }
    }

    private boolean emptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value.toString().trim().equals("")) {
            return true;
        }
        if ((value instanceof Collection<?>) && ((Collection<?>) value).isEmpty()) {
            return true;
        }
        if (value instanceof Collection<?>) {
            @SuppressWarnings("unchecked")
            Collection<Object> coll = (Collection<Object>) value;
            for (Object object : coll) {
                if (object == null || "".equals(object.toString().trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private class DerivedPropertyEvaluator implements PropertyEvaluator {

        private final DerivedPropertyDescription propertyDesc;
        private final StructuredResourceDescription resourceDesc;

        private DerivedPropertyEvaluator(DerivedPropertyDescription propertyDesc,
                StructuredResourceDescription resourceDesc) {
            this.propertyDesc = propertyDesc;
            this.resourceDesc = resourceDesc;
        }

        @Override
        public boolean evaluate(Property property, PropertyEvaluationContext ctx) throws PropertyEvaluationException {

            if (ctx.getEvaluationType() != PropertyEvaluationContext.Type.ContentChange
                    && ctx.getEvaluationType() != PropertyEvaluationContext.Type.SystemPropertiesChange
                    && ctx.getEvaluationType() != PropertyEvaluationContext.Type.Create) {
                return ctx.getOriginalResource().getProperty(property.getDefinition()) != null;
            }

            try {
                Object value = getEvaluatedValue(propertyDesc, property, ctx);
                
                if (emptyValue(value)) {
                    // Evaluated value returned empty, check any default
                    // value that might exist, than finally check for any
                    // overridden value
                    Property prop = null;
                    if (propertyDesc.hasDefaultProperty()) {
                        prop = getProperty(ctx.getNewResource(), propertyDesc.getDefaultProperty());
                    }
                    if (prop == null) {
                        prop = getProperty(ctx.getNewResource(), property.getDefinition().getName());
                    }
                    if (prop != null) {
                        value = prop.getDefinition().isMultiple() ? prop.getValues() : prop.getValue();
                    }
                }
                if (!emptyValue(value)) {
                    setPropValue(property, value);
                    invokeService(property, ctx, propertyDesc, resourceDesc);
                    return true;
                }
                return false;
            }
            catch (Exception e) {
                return false;
            }
        }

        @SuppressWarnings("unchecked")
        private Object getEvaluatedValue(DerivedPropertyDescription desc, Property property,
                PropertyEvaluationContext ctx) {

            String affectingService = desc.getAffectingService();
            if (affectingService != null) {
                Object o = ctx.getEvaluationAttribute(affectingService);
                if (o != null) {
                    Map<String, Object> map = (Map<String, Object>) o;
                    return map.get(property.getDefinition().getName());
                }
            }

            DerivedPropertyEvaluationDescription evaluationDescription = desc.getEvaluationDescription();
            StringBuilder value = new StringBuilder();
            for (EvaluationElement evaluationElement : evaluationDescription.getEvaluationElements()) {

                String v;
                if (evaluationElement.isString()) {
                    v = evaluationElement.getValue();
                }
                else {
                    v = fieldValue(ctx, evaluationElement.getValue());
                }
                Operator operator = evaluationElement.getOperator();
                if (operator != null) {
                    Object result = evaluateOperator(evaluationElement.getValue(), v, ctx, operator);
                    v = result == null ? null : result.toString();
                }
                if (v == null) {
                    return null;
                }
                value.append(v);
            }
            return value.toString();
        }

        private String fieldValue(PropertyEvaluationContext ctx, String propName) {

            Property prop = getProperty(ctx.getNewResource(), propName);
            if (prop != null) {
                return prop.getStringValue();
            }
            JsonParseResult json;
            try {
                json = ctx.getContent()
                        .getContentRepresentation(JsonParseResult.class);
            }
            catch (Exception e) {
                throw new PropertyEvaluationException(
                        "Unable to get JSON representation of content", e);
            }
            if (json.value.failure.isPresent()) {
                throw new PropertyEvaluationException(
                        "Unable to get JSON representation of content", 
                        json.value.failure.get());
            }
            String expression = "properties." + propName;
            Optional<Json.MapContainer> document = json.asObject();
            if (!document.isPresent()) {
                throw new PropertyEvaluationException(
                        "Unable to get JSON object representation of content");
            }
            Object jsonObject = Json.select(document.get(), expression);
            if (jsonObject != null) {
                return jsonObject.toString();
            }
            return null;

        }

        private Object evaluateOperator(String propName, String propValue, PropertyEvaluationContext ctx,
                Operator operator) {
            switch (operator) {
            case EXISTS:
                return Boolean.valueOf(propValue != null);
            case TRUNCATE:
                return getTruncated(ctx, propName, propValue);
            case ISTRUNCATED:
                Object obj = ctx.getEvaluationAttribute(propName);
                return Boolean.valueOf(operator.equals(obj));
            case LOCALIZED:
                // Fetch locale from original resource, since prop is
                // inheritable and is probably not available in
                // ctx.getNewResource().
                Locale locale = ctx.getOriginalResource().getContentLocale();
                if (locale == null) {
                    locale = EvaluatorResolver.this.defaultLocale;
                }
                return resourceDesc.getLocalizedMsg(propValue, locale, locale, null);
            case SHARED_TEXT:
                // Original resource is used to fetch locale and property type
                // definition in resolveSharedText. Property is used to fetch
                // the new value for the shared text.
                Property prop = ctx.getNewResource().getProperty(Namespace.STRUCTURED_RESOURCE_NAMESPACE, propName);
                String sharedText = null;
                if (prop != null) {
                    sharedText = sharedTextResolver.resolveSharedText(ctx.getOriginalResource(), prop);
                    if (sharedText != null) {
                        sharedText = htmlUtil.flatten(sharedText);
                    }
                }
                return sharedText;
            case CONTEXTUAL:
                String contextual = null;
                for (Property property : ctx.getOriginalResource().getProperties()) {
                    if (property.getDefinition().getName().equals(propName)) {
                        contextual = property.getStringValue();
                    }
                }
                return contextual;
            default:
                return null;
            }
        }

        private String getTruncated(PropertyEvaluationContext ctx, String propName, String value) {
            String compressed = htmlDigester.compress(value);
            String truncated = htmlDigester.truncateHtml(compressed);
            if (truncated != null && (truncated.length() < compressed.length())) {
                // Mark the property as truncated on the evaluation context
                ctx.addEvaluationAttribute(propName, Operator.ISTRUNCATED);
            }
            return truncated;
        }
    }

    private class BinaryPropertyEvaluator implements PropertyEvaluator {

        private final PropertyDescription propertyDesc;

        private BinaryPropertyEvaluator(PropertyDescription desc) {
            this.propertyDesc = desc;
        }

        @Override
        public String toString() {
            return getClass().getName() + ": " + propertyDesc.getName();
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean evaluate(Property property, PropertyEvaluationContext ctx) throws PropertyEvaluationException {

            if (property.isValueInitialized()
                    && ctx.getEvaluationType() != PropertyEvaluationContext.Type.ContentChange
                    && ctx.getEvaluationType() != PropertyEvaluationContext.Type.Create) {
                return true;
            }

            Object value = null;
            String affectingService = propertyDesc.getAffectingService();
            if (affectingService != null) {
                Object o = ctx.getEvaluationAttribute(affectingService);
                if (o != null) {
                    Map<String, Object> map = (Map<String, Object>) o;
                    value = map.get(property.getDefinition().getName());
                    // No value was found for this prop, don't show anything
                    if (value == null) {
                        return false;
                    }
                }
            }
            if (value != null) {
                setPropValue(property, value);
                return true;
            }
            return false;
        }
    }

    private void setPropValue(Property property, Object value) {
        if (!property.getDefinition().isMultiple()) {
            // If value is collection, pick first element
            if (value instanceof Collection<?>) {
                Collection<?> c = (Collection<?>) value;
                if (c.isEmpty()) {
                    value = null;
                }
                else {
                    value = c.toArray()[0];
                }
            }
            // XXX:
            if (value == null) return;
            Value v = property.getDefinition().getValueFormatter().stringToValue(value.toString(), null, null);
            property.setValue(v);

        }
        else {
            List<Object> values = new ArrayList<Object>();
            if (value instanceof Collection<?> || value instanceof Value[]) {
                values.addAll((Collection<?>) value);
            }
            else {
                values.add(value);
            }
            ValueFormatter vf = property.getDefinition().getValueFormatter();
            List<Value> result = new ArrayList<Value>();
            for (Object object : values) {
                Value v = vf.stringToValue(object.toString(), null, null);
                result.add(v);
            }
            property.setValues(result.toArray(new Value[result.size()]));
        }
    }

    private Property getProperty(Resource resource, String propName) {
        Property prop = resource.getProperty(Namespace.STRUCTURED_RESOURCE_NAMESPACE, propName);
        if (prop == null) {
            prop = resource.getProperty(Namespace.DEFAULT_NAMESPACE, propName);
        }
        return prop;
    }

    private void invokeService(Property property, PropertyEvaluationContext ctx, PropertyDescription desc,
            StructuredResourceDescription resourceDesc) {
        List<ServiceDefinition> services = resourceDesc.getServices();
        if (services != null && services.size() > 0) {
            for (ServiceDefinition serviceDefinition : services) {
                if (serviceDefinition.getName().equals(desc.getName())) {
                    this.serviceInvoker.invokeService(property, serviceDefinition, ctx);
                }
            }
        }
    }

    @Required
    public void setServiceInvoker(ExternalServiceInvoker serviceInvoker) {
        this.serviceInvoker = serviceInvoker;
    }

    @Required
    public void setHtmlDigester(HtmlDigester htmlDigester) {
        this.htmlDigester = htmlDigester;
    }

    @Required
    public void setDefaultLocale(Locale defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    @Required
    public void setSharedTextResolver(SharedTextResolver sharedTextResolver) {
        this.sharedTextResolver = sharedTextResolver;
    }

    @Required
    public void setHtmlUtil(HtmlUtil htmlUtil) {
        this.htmlUtil = htmlUtil;
    }

}
