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
package org.vortikal.resourcemanagement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StructuredResourceDescription {

    private StructuredResourceManager manager;
    private String name;
    private String inheritsFrom;
    private List<PropertyDescription> propertyDescriptions;
    private List<EditRule> editRules;
    private List<ComponentDefinition> componentDefinitions = new ArrayList<ComponentDefinition>();
    private DisplayTemplate displayTemplate;
    private HashMap<String, HashMap<String, String>> localization; //TODO: Gjøre om til .. <String, HashMap <Locale, String>.... 

    StructuredResourceDescription(StructuredResourceManager manager) {
        this.manager = manager;
        this.localization = new HashMap<String, HashMap<String, String>>();
    }

    public void setInheritsFrom(String inheritsFrom) {
        this.inheritsFrom = inheritsFrom;
    }

    public String getInheritsFrom() {
        return inheritsFrom;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public List<PropertyDescription> getPropertyDescriptions() {
        return this.propertyDescriptions;
    }

    public void setPropertyDescriptions(List<PropertyDescription> propertyDescriptions) {
        this.propertyDescriptions = propertyDescriptions;
    }

    public List<PropertyDescription> getAllPropertyDescriptions() {
        List<PropertyDescription> result = new ArrayList<PropertyDescription>();
        if (this.inheritsFrom != null) {
            StructuredResourceDescription ancestor = this.manager.get(this.inheritsFrom);
            result.addAll(ancestor.getAllPropertyDescriptions());
        }
        result.addAll(this.getPropertyDescriptions());
        return result;
    }
    
    public PropertyDescription getPropertyDescription(String name) {
        List<PropertyDescription> allPropertyDescriptions = getAllPropertyDescriptions();
        if (allPropertyDescriptions.size() == 0) {
            return null;
        }
        for (PropertyDescription propertyDescription : allPropertyDescriptions) {
            if (propertyDescription.getName().equals(name)) {
                return propertyDescription;
            }
        }
        return null;
    }

    public void addEditRule(EditRule editRule) {
        if (this.editRules == null) {
            this.editRules = new ArrayList<EditRule>();
        }
        this.editRules.add(editRule);
    }

    public List<EditRule> getEditRules() {
        return this.editRules;
    }

    public List<ComponentDefinition> getComponentDefinitions() {
        return this.componentDefinitions;
    }

    public void addComponentDefinition(ComponentDefinition def) {
        this.componentDefinitions.add(def);
    }

    public DisplayTemplate getDisplayTemplate() {
        return this.displayTemplate;
    }

    public void setDisplayTemplate(DisplayTemplate displayTemplate) {
        this.displayTemplate = displayTemplate;
    }
    
    public void addLocalization(String name, Map<String,String> m){
        localization.put(name,(HashMap<String, String>) m);
    }
    
    public String getLocalizedMsg(String key, Locale locale, Object[] param){
        HashMap<String,String> m = localization.get(key);
        return m.get(locale.getLanguage().toLowerCase());
    }
}
