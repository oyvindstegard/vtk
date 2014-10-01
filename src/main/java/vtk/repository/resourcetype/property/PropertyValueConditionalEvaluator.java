/* Copyright (c) 2007, University of Oslo, Norway
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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.resourcetype.PropertyEvaluator;
import vtk.repository.resourcetype.PropertyTypeDefinition;

public class PropertyValueConditionalEvaluator implements PropertyEvaluator {

    private Map<Pattern, PropertyEvaluator> conditionalValueEvaluatorMap;
    private PropertyTypeDefinition propertyDefinition;


    public boolean evaluate(Property property, PropertyEvaluationContext ctx) throws PropertyEvaluationException {
        
        Property existing = ctx.getNewResource().getProperty(this.propertyDefinition);
        if (existing == null) {
            return false;
        }

        String value = existing.getStringValue();
        
        for (Pattern pattern: conditionalValueEvaluatorMap.keySet()) {
            Matcher m = pattern.matcher(value);
            if (m.find()) {
                PropertyEvaluator evaluator =
                    this.conditionalValueEvaluatorMap.get(pattern);
                return evaluator.evaluate(property, ctx);
            }
        }
        return false;
    }

    public void setPropertyDefinition(PropertyTypeDefinition propertyDefinition) {
        this.propertyDefinition = propertyDefinition;
    }

    public void setConditionalValueEvaluatorMap(Map<String, PropertyEvaluator> conditionalValueEvaluatorMap) {
        this.conditionalValueEvaluatorMap = new HashMap<Pattern, PropertyEvaluator>();
        for (Entry<String, PropertyEvaluator> entry : conditionalValueEvaluatorMap.entrySet()) {
            Pattern pattern = Pattern.compile(entry.getKey(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            this.conditionalValueEvaluatorMap.put(pattern, entry.getValue());
        }
    }
}
