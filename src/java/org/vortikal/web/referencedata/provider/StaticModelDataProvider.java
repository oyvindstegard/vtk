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
package org.vortikal.web.referencedata.provider;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletRequest;

import org.vortikal.web.referencedata.ReferenceDataProvider;

/**
 * A reference data provider that puts static data in the model.
 * It will not overwrite model data if a key is allready present in the model.
 *
 * <p>Static model data can be set with these JavaBean properties:
 * <ul>
 *  <li><code>modelDataCSV</code> - CSV string
 *  <li><code>modelData</code> - Properties
 *  <li><code>modelDataMap</code> - 
 * </ul>
 * 
 * <p>Model data provided:
 * <ul>
 *   <li>the configured set of data, if not allready present in model.
 * </ul>
 * 
 */
public class StaticModelDataProvider implements ReferenceDataProvider {

    private final Map staticModelData = new HashMap();
    
    /**
     * Set static model data as a CSV string.
     * Format is: modelname0={value1},modelname1={value1}
     */
    public void setModelDataCSV(String propString) throws IllegalArgumentException {
        if (propString == null) {
            // leave static attributes unchanged
            return;
        }

        StringTokenizer st = new StringTokenizer(propString, ",");
        while (st.hasMoreTokens()) {
            String tok = st.nextToken();
            int eqIdx = tok.indexOf("=");
            if (eqIdx == -1) {
                throw new IllegalArgumentException("Expected = in attributes CSV string '" + propString + "'");
            }
            if (eqIdx >= tok.length() - 2) {
                throw new IllegalArgumentException(
                        "At least 2 characters ([]) required in attributes CSV string '" + propString + "'");
            }
            String name = tok.substring(0, eqIdx);
            String value = tok.substring(eqIdx + 1);

            // celete first and last characters of value: { and }
            value = value.substring(1);
            value = value.substring(0, value.length() - 1);

            addStaticModelData(name, value);
        }
    }

    /**
     * Set static model data for this provider from a
     * <code>java.util.Properties</code> object.
     * <p>This is the most convenient way to set static model data. Note that
     * static model data can be overridden by allready existing model data, if a value
     * with the same name is in the model.
     * <p>Can be populated with a String "value" (parsed via PropertiesEditor)
     * or a "props" element in XML bean definitions.
     * @see org.springframework.beans.propertyeditors.PropertiesEditor
     */
    public void setModelData(Properties props) {
        setModelDataMap(props);
    }

    /**
     * Set static model data for this provider from a Map. This allows to set
     * any kind of model values, for example bean references.
     * <p>Can be populated with a "map" or "props" element in XML bean definitions.
     * @param modelData Map with name Strings as keys and model objects as values
     */
    public void setModelDataMap(Map modelData) {
        if (modelData != null) {
            Iterator it = modelData.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry) it.next();
                if (!(entry.getKey() instanceof String)) {
                    throw new IllegalArgumentException(
                            "Illegal model key [" + entry.getKey() + "]: only Strings allowed");
                }
                addStaticModelData((String) entry.getKey(), entry.getValue());
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
    
    public void referenceData(Map model, HttpServletRequest request)
            throws Exception {

        for (Iterator iter = staticModelData.keySet().iterator(); iter.hasNext();) {
            String key = (String)iter.next();
            if (!model.containsKey(key))
                model.put(key, staticModelData.get(key));
        }
    }
}
