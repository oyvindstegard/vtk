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
package org.vortikal.resourcemanagement.edit;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.IllegalClassException;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.SimpleFormController;
import org.vortikal.repository.Path;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.Repository.Depth;
import org.vortikal.resourcemanagement.EditRule;
import org.vortikal.resourcemanagement.PropertyDescription;
import org.vortikal.resourcemanagement.StructuredResource;
import org.vortikal.resourcemanagement.StructuredResourceDescription;
import org.vortikal.resourcemanagement.StructuredResourceManager;
import org.vortikal.resourcemanagement.ValidationError;
import org.vortikal.resourcemanagement.EditRule.EditRuleType;
import org.vortikal.security.Principal;
import org.vortikal.security.SecurityContext;
import org.vortikal.util.io.StreamUtil;
import org.vortikal.web.RequestContext;
import org.vortikal.web.actions.UpdateCancelCommand;
import org.vortikal.web.service.URL;

public class StructuredResourceEditor extends SimpleFormController {

    private StructuredResourceManager resourceManager;
    private Repository repository;

    public StructuredResourceEditor() {
        super();
        setCommandName("form");
    }

    protected Object formBackingObject(HttpServletRequest request) throws Exception {
        lock();
        Path uri = RequestContext.getRequestContext().getResourceURI();
        String token = SecurityContext.getSecurityContext().getToken();
        Resource resource = this.repository.retrieve(token, uri, false);
        StructuredResourceDescription description = this.resourceManager.get(resource
                .getResourceType());

        InputStream stream = this.repository.getInputStream(token, uri, true);
        byte[] buff = StreamUtil.readInputStream(stream);
        String encoding = resource.getCharacterEncoding();
        if (encoding == null)
            encoding = "utf-8";
        String source = new String(buff, encoding);
        StructuredResource structuredResource = new StructuredResource(description);
        structuredResource.parse(source);

        URL url = RequestContext.getRequestContext().getService().constructURL(uri);

        return new Form(structuredResource, url);
    }

    protected ModelAndView onSubmit(Object command) throws Exception {
        Form form = (Form) command;
        if (form.getCancelAction() != null) {
            unlock();
            return new ModelAndView(getSuccessView());
        }
        Map<String, Object> model = new HashMap<String, Object>();
        model.put("form", command);
        form.sync();
        Path uri = RequestContext.getRequestContext().getResourceURI();
        String token = SecurityContext.getSecurityContext().getToken();

        InputStream stream = new ByteArrayInputStream(form.getResource().toJSON()
                .toString().getBytes("utf-8"));
        this.repository.storeContent(token, uri, stream);

        if (form.getUpdateQuitAction() != null) {
            unlock();
            return new ModelAndView(getSuccessView());
        }
        return new ModelAndView(getFormView(), model);
    }

    public void setResourceManager(StructuredResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    @Override
    protected ServletRequestDataBinder createBinder(HttpServletRequest request,
            Object command) throws Exception {
        Form form = (Form) command;
        FormDataBinder binder = new FormDataBinder(command, getCommandName(), form
                .getResource().getType());
        prepareBinder(binder);
        initBinder(request, binder);
        return binder;
    }

    private class FormDataBinder extends ServletRequestDataBinder {
        private StructuredResourceDescription description;

        public FormDataBinder(Object target, String objectName,
                StructuredResourceDescription description) {
            super(target, objectName);
            this.description = description;
        }

        @Override
        public void bind(ServletRequest request) {
            List<PropertyDescription> props = this.description
                    .getAllPropertyDescriptions();
            Form form = (Form) getTarget();
            for (PropertyDescription desc : props) {
                String posted = request.getParameter(desc.getName());
                try {
                    form.bind(desc.getName(), posted);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            super.bind(request);
        }
    }

    @SuppressWarnings("unchecked")
    public class Form extends UpdateCancelCommand {
        private StructuredResource resource;
        private List<Box> elements = new ArrayList<Box>();

        public Form(StructuredResource resource, URL url) {
            super(url.toString());
            this.resource = resource;
            StructuredResourceDescription type = resource.getType();
            for (PropertyDescription def : type.getAllPropertyDescriptions()) {
                Box elementBox = new Box(def.getName());
                elementBox.addFormElement(new FormElement(def, null, resource
                        .getProperty(def.getName())));
                
                this.elements.add(elementBox);
            }

            List<EditRule> editRules = type.getEditRules();
            if (editRules != null && editRules.size() > 0) {
                for (EditRule editRule : editRules) {
                    EditRuleType ruleType = editRule.getType();
                    if (EditRuleType.GROUP.equals(ruleType)) {
                        groupElements(editRule);
                    } else if (EditRuleType.POSITION_BEFORE.equals(ruleType)) {
                        rearrangePosition(editRule, EditRuleType.POSITION_BEFORE);
                    } else if (EditRuleType.POSITION_AFTER.equals(ruleType)) {
                        rearrangePosition(editRule, EditRuleType.POSITION_AFTER);
                    } else if (EditRuleType.EDITHINT.equals(ruleType)) {
                        setEditHints(editRule);
                    }
                }
            }
        }

        private void groupElements(EditRule editRule) {
            Box elementBox = new Box(editRule.getName());
            List<String> groupedProps = (List<String>) editRule.getValue();
            for (String groupedProp : groupedProps) {
                FormElement formElement = this.findElement(groupedProp);
                if (formElement != null) {
                    elementBox.addFormElement(formElement);
                    this.removeElementBox(formElement);
                }
            }
            this.elements.add(elementBox);
        }

        private void rearrangePosition(EditRule editRule, EditRuleType ruleType) {
            int indexOfpropToMove = -1;
            int indexToMoveToo = -1;
            for (int i = 0; i < elements.size(); i++) {
                Box elementBox = elements.get(i);
                if (editRule.getName().equals(elementBox.getName())) {
                    indexOfpropToMove = i;
                }
                if (editRule.getValue().toString().equals(elementBox.getName())) {
                    indexToMoveToo = i;
                }
            }
            if (indexOfpropToMove != -1 && indexToMoveToo != -1
                    && indexOfpropToMove != indexToMoveToo) {
                int rotation = EditRuleType.POSITION_BEFORE.equals(ruleType) ? 0 : 1;
                if (indexToMoveToo < indexOfpropToMove) {
                    Collections.rotate(elements.subList(indexToMoveToo + rotation,
                            indexOfpropToMove + 1), 1);
                } else {
                    Collections.rotate(elements.subList(indexOfpropToMove, indexToMoveToo
                            + rotation), -1);
                }
            }
        }

        private void setEditHints(EditRule editRule) {
            for (Box elementBox : elements) {
                if (elementBox.getName().equals(editRule.getName())) {
                    elementBox.addMetaData(editRule.getEditHintKey(), editRule
                            .getEditHintValue());
                }
                for (FormElement formElement : elementBox.getFormElements()) {
                    PropertyDescription pd = formElement.getDescription();
                    if (pd.getName().equals(editRule.getName())) {
                        pd.addEdithint(editRule.getEditHintKey(), editRule
                                .getEditHintValue());
                    }
                }
            }
        }

        public List<Box> getElements() {
            return Collections.unmodifiableList(this.elements);
        }

        public StructuredResource getResource() {
            return this.resource;
        }

        public void bind(String name, String value) throws Exception {
            FormElement elem = findElement(name);
            if (elem == null) {
                throw new IllegalArgumentException("No such element: " + name);
            }
            elem.setValue(value);
        }

        private FormElement findElement(String name) {
            for (Box elementBox : this.elements) {
                for (FormElement formElement : elementBox.getFormElements()) {
                    if (formElement.getDescription().getName().equals(name)) {
                        return formElement;
                    }
                }
            }
            return null;
        }

        private void removeElementBox(FormElement formElement) {
            Box elementBoxToRemove = null;
            for (Box elementBox : this.elements) {
                List<FormElement> formElements = elementBox.getFormElements();
                if (formElements.size() == 1 && formElement.equals(formElements.get(0))) {
                    elementBoxToRemove = elementBox;
                    break;
                }
            }
            if (elementBoxToRemove != null) {
                this.elements.remove(elementBoxToRemove);
            }
        }

        public void sync() {
            List<PropertyDescription> descriptions = this.resource.getType()
                    .getAllPropertyDescriptions();
            for (PropertyDescription desc : descriptions) {
                String name = desc.getName();
                FormElement elem = findElement(name);
                Object value = elem.getValue();
                this.resource.removeProperty(name);
                if (value != null) {
                    this.resource.addProperty(name, value);
                }
            }
        }

    }

    public class Box {

        private String name;
        private List<FormElement> formElements = new ArrayList<FormElement>();
        private Map<String, Object> metaData = new HashMap<String, Object>();

        public Box(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        public void addFormElement(FormElement formElement) {
            this.formElements.add(formElement);
        }

        public List<FormElement> getFormElements() {
            return this.formElements;
        }

        public void addMetaData(String key, Object metaData) {
            this.metaData.put(key, metaData);
        }

        public Map<String, Object> getMetaData() {
            return this.metaData;
        }

    }

    public class FormElement {
        private PropertyDescription description;
        private ValidationError error;
        private Object value;

        public FormElement(PropertyDescription description, ValidationError error,
                Object value) {
            this.description = description;
            this.error = error;
            this.value = value;
            
        }

        public void setDescription(PropertyDescription description) {
            this.description = description;
        }

        public PropertyDescription getDescription() {
            return description;
        }

        public void setError(ValidationError error) {
            this.error = error;
        }

        public ValidationError getError() {
            return error;
        }

        public void setValue(Object value) throws Exception {
            if (this.description.isMultiple()) {
                if (value instanceof String) {
                    String[] a = value.toString().split(",");
                    ArrayList<String> b = new ArrayList <String>();
                    for (int i = 0; i < a.length; i++) {
                        b.add(a[i].toString());
                    }
                    this.value = b;
                } else if (value instanceof List<?>) {
                    this.value = value;
                } else {
                    throw new IllegalClassException(
                            "Unknown value type: " + value.getClass() 
                            + " for multiple-valued property " 
                            + this.description.getName());
                }
            } else {
                this.value = value;
            }
        }

        public Object getValue() {
            return value;
        }
        
        @SuppressWarnings("unchecked")
        public Object getFormatedValue() {
            if (value instanceof List) {
                String result = "";
                List<String> l = (List<String>) value;
                for (int i = 0; i < l.size(); i++) {
                    if (i > 0) {
                        result += ",";
                    }
                    result += l.get(i);
                }
                return result;
            }
            return value;
        }
    }

    public void unlock() throws Exception {
        String token = SecurityContext.getSecurityContext().getToken();
        Path uri = RequestContext.getRequestContext().getResourceURI();
        this.repository.unlock(token, uri, null);
    }

    public void lock() throws Exception {
        String token = SecurityContext.getSecurityContext().getToken();
        Path uri = RequestContext.getRequestContext().getResourceURI();
        Principal principal = SecurityContext.getSecurityContext().getPrincipal();
        this.repository.lock(token, uri, principal.getQualifiedName(), Depth.ZERO, 600,
                null);
    }

}
