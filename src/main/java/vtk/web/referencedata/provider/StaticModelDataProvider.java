/* Copyright (c) 2004, University of Oslo, Norway
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
package vtk.web.referencedata.provider;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletRequest;

import vtk.web.referencedata.ReferenceDataProvider;

/**
 * A reference data provider that puts static data in the model.  It
 * will not overwrite model data if a key is allready present in the
 * model.
 *
 * <p>Static model data can be set with these JavaBean properties:
 * <ul>
 *  <li><code>modelDataCSV</code> - model data as CSV string
 *  <li><code>modelData</code> - model data as {@link Properties}
 *  <li><code>modelDataMap</code> - model data as {@link Map}
 *  <li><code>mergeModelData</code> - try to merge Map entries if
 *  already present in model. Default is <code>false</code>.
 * </ul>
 * 
 * <p>Model data provided:
 * <ul>
 *   <li>the configured set of data, if not allready present in
 *   model. If <code>mergeModelData</code> is set and both "source"
 *   and "destination" entries are maps, those maps are merged.
 * </ul>
 * 
 */
public class StaticModelDataProvider implements ReferenceDataProvider {

    private final Map<String, Object> staticModelData = new HashMap<>();
    
    private boolean mergeModelData = false;
    

    /**
     * Set static model data as a CSV string.
     * Format is: modelname0={value1},modelname1={value1}
     */
    public void setModelDataCSV(String propString) {
        if (propString == null) {
            // leave static attributes unchanged
            return;
        }

        StringTokenizer st = new StringTokenizer(propString, ",");
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            int eqIndex = token.indexOf("=");
            if (eqIndex == -1) {
                throw new IllegalArgumentException(
                    "CSV string must be in the format 'name={value}[,name={value}]+', was '"
                    + propString + "'");
            }
            if (eqIndex >= token.length() - 2) {
                throw new IllegalArgumentException(
                        "At least 2 characters ([]) required in attributes CSV string '"
                        + propString + "'");
            }
            String name = token.substring(0, eqIndex);
            String value = token.substring(eqIndex + 1);

            // strip away: '{' and '}'
            value = value.substring(1);
            value = value.substring(0, value.length() - 1);

            addStaticModelData(name, value);
        }
    }

    /**
     * Set static model data for this provider from a
     * <code>java.util.Properties</code> object.
     * <p>This is the most convenient way to set static model data. Note that
     * static model data can be overridden by already existing model data, if a value
     * with the same name is in the model.
     * <p>Can be populated with a String "value" (parsed via PropertiesEditor)
     * or a "props" element in XML bean definitions.
     * @see org.springframework.beans.propertyeditors.PropertiesEditor
     */
//    public void setModelData(Properties props) {
//        setModelDataMap(props);
//    }

    /**
     * Set static model data for this provider from a Map. This allows to set
     * any kind of model values, for example bean references.
     * <p>Can be populated with a "map" or "props" element in XML bean definitions.
     * @param modelData Map with name Strings as keys and model objects as values
     */
    public void setModelDataMap(Map<String, Object> modelData) {
        if (modelData != null) {
            for (Map.Entry<String, Object> entry: modelData.entrySet()) {
                addStaticModelData(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Add static data provider, exposed in the model.
     * <p>Must be invoked before any calls to <code>referenceData</code>.
     * @param name name of model data to expose
     * @param value object to expose
     * @see #referenceData(Map, HttpServletRequest)
     */
    public void addStaticModelData(String name, Object value) {
        this.staticModelData.put(name, value);
    }
    

    public void setMergeModelData(boolean mergeModelData) {
        this.mergeModelData = mergeModelData;
    }
    

    @SuppressWarnings("unchecked")
    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) {

        for (String key: this.staticModelData.keySet()) {
            if (!model.containsKey(key)) {
                model.put(key, this.staticModelData.get(key));

            } else if (this.mergeModelData && (model.get(key) instanceof Map)
                       && (this.staticModelData.get(key) instanceof Map)) {
                // Merge model data:
                Map<String, Object> destination = (Map<String, Object>) model.get(key);
                destination.putAll((Map<String, Object>) this.staticModelData.get(key));
            }
        }
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer(this.getClass().getName());
        sb.append(" [ ");
        sb.append("staticModelData = ").append(this.staticModelData);
        sb.append(" ]");
        return sb.toString();
    }
    
}
