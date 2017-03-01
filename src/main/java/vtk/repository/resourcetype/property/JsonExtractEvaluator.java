/* Copyright (c) 2013, University of Oslo, Norway
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

package vtk.repository.resourcetype.property;


import java.util.Optional;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.PropertyEvaluationContext.Type;
import vtk.repository.content.JsonParseResult;
import vtk.repository.resourcetype.PropertyEvaluator;
import vtk.repository.resourcetype.ValueFactory;
import vtk.util.text.Json;

/**
 * Set property value based on value extracted from JSON content. The value
 * is selected by a simple dotted keys expression.
 */
public class JsonExtractEvaluator implements PropertyEvaluator {

    private ValueFactory valueFactory;
    private String expression;
    private boolean alwaysEvaluate = false;
    
    @Override
    public boolean evaluate(Property property, PropertyEvaluationContext ctx) throws PropertyEvaluationException {
        if (ctx.getContent() == null) {
            return false;
        }
        if (alwaysEvaluate
                || ctx.getEvaluationType() == Type.ContentChange
                || ctx.getEvaluationType() == Type.Create) {

            try {
                JsonParseResult json = ctx.getContent()
                        .getContentRepresentation(JsonParseResult.class);
                if (json.value.failure.isPresent()) {
                    return false;
                }
                Optional<Json.MapContainer> document = json.asObject();
                
                if (document.isPresent()) {
                    Object o = Json.select(document.get(), expression);
                    if (o != null) {
                        String stringValue = o.toString();
                        property.setValue(valueFactory
                                .createValue(stringValue, property.getType()));
                        return true;
                    }
                }
            } catch (Exception e) {}

            return false;
        } 

        return property.isValueInitialized();
    }
    
    @Required
    public void setExpression(String expression) {
        if (expression == null) throw new IllegalArgumentException("Expression cannot be null");
        this.expression = expression;
    }
    
    @Required
    public void setValueFactory(ValueFactory vf) {
        this.valueFactory = vf;
    }

    /**
     * Specify whether to always evaluate from content regardless of current
     * {@link Type evaluation type}. Normally, evaluation only occurs
     * when content changes, but this setting can override that.
     * 
     * @param alwaysEvaluate 
     */
    public void setAlwaysEvaluate(boolean alwaysEvaluate) {
        this.alwaysEvaluate = alwaysEvaluate;
    }
}
