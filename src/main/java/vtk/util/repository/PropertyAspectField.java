/* Copyright (c) 2010, University of Oslo, Norway
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
package vtk.util.repository;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PropertyAspectField implements Serializable {
    private static final long serialVersionUID = 1675394681041209153L;
    String identifier;
    String type;
    private boolean inherited;
    private List<?> values;
    private Map<?, ?> i18n;
    
    public PropertyAspectField(String identifier, String type, boolean inherited, List<?> values, Map<?, ?> i18n) {
        if (!("string".equals(type) || "flag".equals(type) || "enum".equals(type))) {
            throw new IllegalArgumentException("Illegal type: " + type);
        }
        this.identifier = identifier;
        this.type = type;
        this.inherited = inherited;
        this.values = values;
        this.i18n = i18n;
    }
    
    public String getIdentifier() {
        return this.identifier;
    }
    
    public String getType() {
        return this.type;
    }
    
    public boolean isInherited() {
        return this.inherited;
    }
    
    public List<?> getValues() {
        return this.values;
    }
    
    public String getLocalizedName(Locale locale) {
        return getLocalizedString("name", locale);
    }
    
    public Object getLocalizedValue(Object object, Locale locale) {
        String str = object == null ? "null" : object.toString();
        return getLocalizedString(str, locale);
    }

    private String getLocalizedString(String str, Locale locale) {
        if (this.i18n == null) {
            return str;
        }
        String key = "{" + str + "}_" + locale.toString();
        Object o = this.i18n.get(key);
        if (o != null) {
            return o.toString();
        }
        key = "{" + str + "}_" + locale.getLanguage() + "_" + locale.getCountry();
        o = this.i18n.get(key);
        if (o != null) {
            return o.toString();
        }
        key = "{" + str + "}_" + locale.getLanguage();
        o = this.i18n.get(key);
        if (o != null) {
            return o.toString();
        }
        key = "{" + str + "}";
        o = this.i18n.get(key);
        if (o != null) {
            return o.toString();
        }
        return str;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("identifier : ").append(this.identifier);
        sb.append(", type : ").append(this.type);
        sb.append(", inherited: ").append(this.inherited);
        sb.append(", values : ").append(this.values);
        sb.append(", i18n : ").append(this.i18n);
        sb.append("}");
        return sb.toString();
    }
}
