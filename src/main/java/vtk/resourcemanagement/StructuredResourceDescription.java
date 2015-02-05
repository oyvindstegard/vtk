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
package vtk.resourcemanagement;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import vtk.resourcemanagement.property.DerivedPropertyDescription;
import vtk.resourcemanagement.property.DerivedPropertyEvaluationDescription.EvaluationElement;
import vtk.resourcemanagement.property.PropertyDescription;

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
    private HashMap<String, Map<Locale, Map<Locale, String>>> localization = new HashMap<String, Map<Locale, Map<Locale, String>>>();
    private HashMap<String, Map<Locale, String>> tooltips = new HashMap<String, Map<Locale, String>>();

    private static final String DEFAULT_LANG = "en";

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
        if (propertyDescriptions == null) {
            return result;
        }
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

    public void addTooltips(String name, Map<Locale, String> m) {
        tooltips.put(name, m);
    }

    public Map<String, Map<Locale, String>> getAllLocalizedTooltips() {
        Map<String, Map<Locale, String>> locales = new HashMap<String, Map<Locale, String>>();
        if (this.inheritsFrom != null) {
            StructuredResourceDescription parent = this.manager.get(this.inheritsFrom);
            locales.putAll(parent.getAllLocalizedTooltips());
        }
        locales.putAll(this.tooltips);
        return locales;
    }

    public String getLocalizedTooltip(String key, Locale locale) {
        Map<Locale, String> tooltipMap = this.getAllLocalizedTooltips().get(key);
        if (tooltipMap == null) {
            return "";
        }
        String localizedMessage = tooltipMap.get(new Locale(locale.getLanguage()));
        return localizedMessage != null ? localizedMessage : "";
    }

    public void addLocalization(String name, Map<Locale, Map<Locale, String>> m) {
        localization.put(name, m);
    }

    public Map<String, Map<Locale, Map<Locale, String>>> getAllLocalization() {
        Map<String, Map<Locale, Map<Locale, String>>> locales = new HashMap<String, Map<Locale, Map<Locale, String>>>();
        if (this.inheritsFrom != null) {
            StructuredResourceDescription parent = this.manager.get(this.inheritsFrom);
            locales.putAll(parent.getAllLocalization());
        }
        locales.putAll(this.localization);
        return locales;
    }

    public StructuredResource buildResource(InputStream source) throws Exception {
        return StructuredResource.create(this, source);
    }
    
    public StructuredResource buildFromMap(Map<String, Object> json) throws Exception {
        return StructuredResource.createFromMap(this, json);
    }
    
    public String getLocalizedMsg(String key, Locale locale, Object[] param) {
       return getLocalizedMsg(key, locale, locale, param); 
    }

    // XXX: handle parameters
    public String getLocalizedMsg(String key, Locale locale1, Locale locale2, Object[] param) {
        Map<Locale, Map <Locale, String>> localizationMap = this.getAllLocalization().get(key);
        if (localizationMap == null) {
            return key;
        }
        Locale lang1 = new Locale(locale1 == null ? DEFAULT_LANG : locale1.getLanguage());

        Map <Locale, String> localizedMessages = localizationMap.get(lang1);
        String localizedMessage = null;
        if(localizedMessages != null) {
            // More than one language in localizationMap: use second language as second key
            if(localizedMessages.size() > 1) {
                Locale lang2 = new Locale(locale2 == null ? DEFAULT_LANG : locale2.getLanguage());
                localizedMessage = localizedMessages.get(lang2);
            } else { // Otherwise: use first language as second key
                localizedMessage = localizedMessages.get(lang1);
            }
        }
        return localizedMessage != null ? localizedMessage : key;
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

    public void validate() {
        if (propertyDescriptions == null) {
            return;
        }
        for (int i = 0; i < propertyDescriptions.size(); i++) {
            PropertyDescription d = propertyDescriptions.get(i);

            if (d instanceof DerivedPropertyDescription) {
                DerivedPropertyDescription derived = (DerivedPropertyDescription) d;

                if (derived.getAffectingService() != null) {
                    return;
                }

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
                            StructuredResourceDescription parent = this.manager.get(this.inheritsFrom);
                            if (parent.getPropertyDescription(propName) != null) {
                                found = true;
                            }
                        }
                        if (!found) {
                            throw new IllegalStateException("Property definition '" + d.getName()
                                    + "' is declared to be derived from property '" + propName
                                    + "', which is not defined");
                        }
                    }
                    // Verify that properties do not derive from themselves:
                    if (propName.equals(d.getName())) {
                        throw new IllegalStateException("Property definition '" + d.getName()
                                + "' is declared to be derived from itself");
                    }
                }
                // Verify that derived properties evaluate using only
                // declared properties:
                for (EvaluationElement evaluationElement : derived.getEvaluationDescription().getEvaluationElements()) {
                    if (!evaluationElement.isString()) {
                        boolean found = false;
                        for (String propName : derived.getDependentProperties()) {
                            if (propName.equals(evaluationElement.getValue())) {
                                found = true;
                            }
                        }
                        if (!found) {
                            throw new IllegalStateException("Property definition '" + d.getName()
                                    + "' is declared to evaluate using property '" + evaluationElement.getValue()
                                    + "', which is not listed in the derives clause");
                        }
                    }
                }

            }
        }

    }

}
