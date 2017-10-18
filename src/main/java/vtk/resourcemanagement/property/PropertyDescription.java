/* Copyright (c) 2009, University of Oslo, Norway
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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import vtk.resourcemanagement.parser.ParserConstants;

public abstract class PropertyDescription {

    private String name;
    private String type;
    private String overrides;
    private boolean noExtract;
    private boolean multiple;
    private boolean trim;
    private String affectingService;

    private Map<Locale, Map<String, Object>> vocabulary = new HashMap<>();

    public final void setName(String name) {
        this.name = name;
    }

    public final String getName() {
        return name;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
    
    public boolean isRefType() {
        return "resource_ref".equals(type) 
                || "image_ref".equals(type) 
                || "media_ref".equals(type);
    }

    public void setOverrides(String overrides) {
        this.overrides = overrides;
    }

    public String getOverrides() {
        return overrides;
    }

    public boolean isOverrides() {
        return this.overrides != null;
    }

    public void setNoExtract(boolean noExtract) {
        this.noExtract = noExtract;
    }

    public boolean isNoExtract() {
        return noExtract;
    }

    public void setMultiple(boolean multiple) {
        this.multiple = multiple;
    }

    public boolean isMultiple() {
        return this.multiple;
    }

    public String getAffectingService() {
        return affectingService;
    }

    public void setAffectingService(String affectingService) {
        this.affectingService = affectingService;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + ": " + this.name;
    }

    public void addVocabulary(Locale lang, String vocabularyKey, Object vocabularyValue) {
        Map<String, Object> m = vocabulary.get(lang);
        if (m == null) {
            m = new LinkedHashMap<>();
        }
        m.put(vocabularyKey, vocabularyValue);
        this.vocabulary.put(lang, m);
    }

    public Object getVocabularyValue(Locale lang, String vocabularyKey) {
        Map<String, Object> m = vocabulary.get(lang);
        if (m == null) {
            return vocabularyKey;
        }
        Object value = m.get(vocabularyKey);
        if (value != null) {
            return value;
        }
        return vocabularyKey;
    }

    public Map<String, Object> getValuemap(Locale local) {
        Map<String, Object> valuemap = vocabulary.get(local);
        return valuemap;
    }

    public boolean isTrim() {
        return ParserConstants.PROPTYPE_STRING.equals(this.type) && this.trim;
    }

    public void setTrim(boolean trim) {
        this.trim = trim;
    }

}
