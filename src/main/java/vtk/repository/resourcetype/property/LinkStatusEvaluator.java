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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.resourcetype.LatePropertyEvaluator;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;

public class LinkStatusEvaluator implements LatePropertyEvaluator {

    private PropertyTypeDefinition hrefsPropDef;
    private PropertyTypeDefinition linkCheckPropDef;
    
    public void setHrefsPropDef(PropertyTypeDefinition hrefsPropDef) {
        this.hrefsPropDef = hrefsPropDef;
    }

    public void setLinkCheckPropDef(PropertyTypeDefinition linkCheckPropDef) {
        this.linkCheckPropDef = linkCheckPropDef;
    }

    /**
     * Values evaluated:
     *
     * <pre>
     * NO_LINKS
     * AWAITING_LINKCHECK
     * OK
     * LINKCHECK_ERROR
     * BROKEN_LINKS plus one or more qualifiers: 
     *      (BROKEN_LINKS_ANCHOR, BROKEN_LINKS_IMG, ...)
     * </pre>
     */
    @Override
    public boolean evaluate(Property property, PropertyEvaluationContext ctx)
            throws PropertyEvaluationException {
        Property hrefsProp = ctx.getNewResource().getProperty(hrefsPropDef);

        if (hrefsProp == null) {
            property.setValues(new Value[] {new Value("NO_LINKS", Type.STRING)});
            return true;
        }
        
        if (ctx.getEvaluationType() != PropertyEvaluationContext.Type.SystemPropertiesChange) {
            property.setValues(new Value[] {new Value("AWAITING_LINKCHECK", Type.STRING)});
            return true;
        }
        
        Property linkCheckProp = ctx.getNewResource().getProperty(this.linkCheckPropDef);
        
        try {
            JSONObject linkCheck = propValue(linkCheckProp);
            Object brokenLinks = linkCheck.get("brokenLinks");
            
            if (brokenLinks == null || ((JSONArray)brokenLinks).isEmpty()) {
                property.setValues(new Value[] {new Value("OK", Type.STRING)});
                return true;
            }

            JSONArray arr = (JSONArray) brokenLinks;
            Set<String> errors = new HashSet<>();
            errors.add("BROKEN_LINKS");
            for (Object o: arr) {
                Map<?, ?> map = (Map<?, ?>) o;
                Object type = map.get("type");
                if (type != null) {
                    if ("PROPERTY".equals(type.toString())) {
                        // Map PROPERTY type refs to IMG for now
                        type = "IMG";
                    }
                    errors.add("BROKEN_LINKS_" + type.toString());
                }
                if (map.containsKey("vrtxid")) {
                    errors.add("TARGET_RELOCATED");
                }
            }
            List<Value> values = new ArrayList<>();
            for (String s: errors) {
                values.add(new Value(s, Type.STRING));
            }
            property.setValues(values.toArray(new Value[values.size()]));
            return true;
        }
        catch (Throwable t) {
            property.setValues(new Value[]{new Value("LINKCHECK_ERROR", Type.STRING)});
            return true;
        }
    }

    
    private JSONObject propValue(Property linkCheck) throws Exception {
        InputStream stream = linkCheck.getBinaryStream().getStream();
        Object o = JSONValue.parse(new InputStreamReader(stream));
        stream.close();
        return (JSONObject) o;
    }
}
