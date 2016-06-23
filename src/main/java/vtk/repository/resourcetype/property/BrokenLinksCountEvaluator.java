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

package vtk.repository.resourcetype.property;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.resourcetype.PropertyEvaluator;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.util.text.Json.ListContainer;
import vtk.util.text.Json.MapContainer;
import vtk.util.text.JsonStreamer;

public class BrokenLinksCountEvaluator implements PropertyEvaluator {

    private PropertyTypeDefinition linkCheckPropDef;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Counts number of entries in broken links list of link-check property
     * and stores the number as an integer value.
     * @param property
     * @param ctx
     * @return
     * @throws PropertyEvaluationException 
     */
    @Override
    public boolean evaluate(Property property, PropertyEvaluationContext ctx) throws PropertyEvaluationException {
        Property linkCheckProp = ctx.getNewResource().getProperty(this.linkCheckPropDef);
        if (linkCheckProp == null) return false;
        // Link check prop only changes for system evaluation, so only evaluate if it does
        // not already exist or when system change:
        if (property.isValueInitialized() &&
                ctx.getEvaluationType() != PropertyEvaluationContext.Type.SystemPropertiesChange) {
            return true;
        }

        try {
            MapContainer jsonValue = property.getJSONValue();
            ListContainer brokenLinks = jsonValue.arrayValue("brokenLinks");
            if (brokenLinks == null || brokenLinks.isEmpty()) {
                return false;
            }
            
            ErrorCount errorCount = new ErrorCount();
            for (Object o: brokenLinks) {
                Map<?, ?> brokenLink = (Map<?, ?>) o;
                Object type = brokenLink.get("type");
                if (type != null) {
                    if ("PROPERTY".equals(type.toString())) {
                        // Map PROPERTY type refs to IMG for now
                        type = "IMG";
                    }
                    errorCount.incrementForError("BROKEN_LINKS_" + type.toString());
                }
                errorCount.incrementForError("BROKEN_LINKS");
            }
            errorCount.write(property);
            return true;
        }
        catch (Exception e) {
            logger.warn("Exception during evaluation", e);
            return false;
        }
    }
    
    private static final class ErrorCount {
        private final Map<String,MutableInt> errorCount = new HashMap<String,MutableInt>();
        
        void incrementForError(String error) {
            MutableInt count = errorCount.get(error);
            if (count == null) {
                count = new MutableInt(0);
                errorCount.put(error, count);
            }
            count.increment();
        }
        
        void write(Property prop) {
            String jsonString = JsonStreamer.toJson(errorCount);
            prop.setValue(new Value(jsonString, PropertyType.Type.JSON));
        }
    }
    
    @Required
    public void setLinkCheckPropDef(PropertyTypeDefinition linkCheckPropDef) {
        this.linkCheckPropDef = linkCheckPropDef;
    }

}
