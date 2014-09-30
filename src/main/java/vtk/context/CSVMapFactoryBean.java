/* Copyright (c) 2011, University of Oslo, Norway
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
package vtk.context;

import java.util.HashMap;
import java.util.Map;

import vtk.util.text.TextUtils;

/**
 * Creates mappings from string values to string values. 
 * 
 * <p>Syntax is
 * <code>key:value</code>. Colons in keys and values have to be
 * escaped using a backslash (<code>\:</code>). Entries without a 
 * colon are interpreted as mappings with <code>null</code> 
 * as the value.</p>
 * 
 */
public class CSVMapFactoryBean extends AbstractCSVFactoryBean {

    @Override
    protected Object createInstance() throws Exception {
        Map<String, String> map = new HashMap<String, String>();

        for (String element: super.elements) {
            String[] mapping = TextUtils.parseKeyValue(element, ':');
            map.put(mapping[0], mapping[1]);
        }
        return map;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Class getObjectType() {
        return Map.class;
    }

}
