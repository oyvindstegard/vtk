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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.vortikal.resourcemanagement.DerivedPropertyDescription.EvalDescription;

public final class StructuredResourceDescription {

    private StructuredResourceManager manager;
    private String name;
    private String inheritsFrom;
    private List<PropertyDescription> propertyDescriptions;
    private List<EditRule> editRules;
    private DisplayTemplate displayTemplate;
    private List<ScriptDefinition> scripts;
    private List<ServiceDefinition> services;

    private List<ComponentDefinition> componentDefinitions = new ArrayList<ComponentDefinition>();
    private HashMap<String, HashMap<Locale, String>> localization = new HashMap<String, HashMap<Locale, String>>();

    public StructuredResourceDescription(StructuredResourceManager manager) {
        this.manager = manager;
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

        Set<PropertyDescription> alreadyAdded = new HashSet<PropertyDescription>();
        for (int i = 0; i < result.size(); i++) {
            PropertyDescription ancestor = result.get(i);
            for (int j = 0; j < this.propertyDescriptions.size(); j++) {
                PropertyDescription d = this.propertyDescriptions.get(j);
                if (d.getOverrides() != null) {
                    if (d.getName().equals(ancestor.getName())) {
                        result.remove(i);
                        result.add(i, d);
                        alreadyAdded.add(d);
                    }
                }
            }
        }
        for (PropertyDescription propertyDescription : this.propertyDescriptions) {
            if (!alreadyAdded.contains(propertyDescription)) {
                result.add(propertyDescription);
            }
        }
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

    public List<ComponentDefinition> getAllComponentDefinitions() {
        List<ComponentDefinition> result = new ArrayList<ComponentDefinition>();
        if (this.inheritsFrom != null) {
            StructuredResourceDescription ancestor = this.manager.get(this.inheritsFrom);
            result.addAll(ancestor.getAllComponentDefinitions());
        }
        result.addAll(this.getComponentDefinitions());
        return result;
    }

    public DisplayTemplate getDisplayTemplate() {
        return this.displayTemplate;
    }

    public void setDisplayTemplate(DisplayTemplate displayTemplate) {
        this.displayTemplate = displayTemplate;
    }

    public void addLocalization(String name, Map<Locale, String> m) {
        localization.put(name, (HashMap<Locale, String>) m);
    }

    public Map<String, HashMap<Locale, String>> getAllLocalization() {
        Map<String, HashMap<Locale, String>> locales = new HashMap<String, HashMap<Locale, String>>();
        if (this.inheritsFrom != null) {
            StructuredResourceDescription parent = this.manager.get(this.inheritsFrom);
            locales.putAll(parent.getAllLocalization());
        }
        locales.putAll(this.localization);
        return locales;
    }

    // XXX: handle parameters
    public String getLocalizedMsg(String key, Locale locale, @SuppressWarnings("unused") Object[] param) {
        HashMap<Locale, String> localizationMap = this.getAllLocalization().get(key);
        if (localizationMap == null) {
            return key;
        }
        return localizationMap.get(locale);
    }

    public void addScriptDefinition(ScriptDefinition scriptDefinition) {
        if (this.scripts == null) {
            this.scripts = new ArrayList<ScriptDefinition>();
        }
        this.scripts.add(scriptDefinition);
    }

    public List<ScriptDefinition> getScripts() {
        return this.scripts;
    }

    public void addServiceDefinition(ServiceDefinition serviceDefinition) {
        if (this.services == null) {
            this.services = new ArrayList<ServiceDefinition>();
        }
        this.services.add(serviceDefinition);
    }

    public List<ServiceDefinition> getServices() {
        return this.services;
    }

    public String toString() {
        return this.getClass().getName() + ":" + this.name;
    }

    void validate() {
        for (int i = 0; i < propertyDescriptions.size(); i++) {
            PropertyDescription d = propertyDescriptions.get(i);

            if (d instanceof DerivedPropertyDescription) {
                DerivedPropertyDescription derived = (DerivedPropertyDescription) d;
                
                List<String> dependentProperties = new ArrayList<String>();
                dependentProperties.addAll(derived.getDependentProperties());
                if (derived.getDefaultProperty() != null) {
                	dependentProperties.add(derived.getDefaultProperty());
                }
                
                for (String propName : dependentProperties) {
                    boolean found = false;
                    // Verify that each derived property is defined:
                    for (int j = 0; j < i; j++) {
                        if (propertyDescriptions.get(j).getName().equals(propName)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        // If not found in this definition, check parent:
                        if (this.inheritsFrom != null) {
                            StructuredResourceDescription parent = this.manager
                                    .get(this.inheritsFrom);
                            if (parent.getPropertyDescription(propName) != null) {
                                found = true;
                            }
                        }
                        if (!found) {
                            throw new IllegalStateException("Property definition '"
                                    + d.getName()
                                    + "' is declared to be derived from property '"
                                    + propName + "', which is not defined");
                        }
                    }
                    // Verify that properties do not derive from themselves:
                    if (propName.equals(d.getName())) {
                        throw new IllegalStateException("Property definition '"
                                + d.getName() + "' is declared to be derived from itself");
                    }
                }
                // Verify that derived properties evaluate using only
                // declared properties:
                for (EvalDescription eval : derived.getEvalDescriptions()) {
                    if (!eval.isString()) {
                        boolean found = false;
                        for (String propName : derived.getDependentProperties()) {
                            if (propName.equals(eval.getValue())) {
                                found = true;
                            }
                        }
                        if (!found) {
                            throw new IllegalStateException("Property definition '"
                                    + d.getName()
                                    + "' is declared to evaluate using property '" + eval.getValue()
                                    + "', which is not listed in the derives clause");
                        }
                    }
                }
                
            }
        }

    }

}
