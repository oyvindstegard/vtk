/* Copyright (c) 2016, University of Oslo, Norway
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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.time.FastDateFormat;

import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.PropertyEvaluationContext.Type;
import vtk.repository.resourcetype.LatePropertyEvaluator;
import vtk.util.text.Json;

public class LocationHistoryEvaluator implements LatePropertyEvaluator {
    private static final int MAX_ENTRIES = 100;

    @Override
    public boolean evaluate(Property property, PropertyEvaluationContext ctx)
            throws PropertyEvaluationException {
        
        boolean exists = ctx.getOriginalResource().getProperty(property.getDefinition()) != null;
        if (ctx.getEvaluationType() != Type.NameChange) {
            return exists;
        }
        
        List<Object> log = null;
        if (property.isValueInitialized()) {
            Json.MapContainer object = property.getJSONValue();
            log = object.optArrayValue("locations", new Json.ListContainer());
        }
        if (log == null) log = new ArrayList<>();
        
        Json.MapContainer entry = new Json.MapContainer();
        entry.put("principal", ctx.getPrincipal().getQualifiedName());
        entry.put("time", FastDateFormat.getInstance("yyyyMMdd HH:mm:ss").format(ctx.getTime()));
        entry.put("from_uri", ctx.getOriginalResource().getURI());
        entry.put("to_uri", ctx.getNewResource().getURI());
        log.add(entry);
        
        if (log.size() > MAX_ENTRIES) {
            log.remove(0);
        }

        Json.MapContainer value = new Json.MapContainer();
        value.put("locations", log);
        property.setJSONValue(value);
        return true;
    }
    
}
