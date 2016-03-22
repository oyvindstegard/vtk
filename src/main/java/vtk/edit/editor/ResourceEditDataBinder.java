/* Copyright (c) 2007,2015 University of Oslo, Norway
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
package vtk.edit.editor;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletRequest;

import org.apache.commons.lang.StringUtils;
import org.springframework.web.bind.ServletRequestDataBinder;

import vtk.repository.Property;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.text.html.HtmlElement;
import vtk.text.html.HtmlFragment;
import vtk.text.html.HtmlPage;
import vtk.text.html.HtmlPageFilter;
import vtk.text.html.HtmlPageParser;

public class ResourceEditDataBinder extends ServletRequestDataBinder {

    private HtmlPageParser htmlParser;
    private HtmlPageFilter htmlPropsFilter;
    private final static String defaultCharacterEncoding = "utf-8";
    private Map<PropertyTypeDefinition, PropertyEditPreprocessor> propertyPreprocessors;


    public ResourceEditDataBinder(Object target, String objectName, HtmlPageParser htmlParser,
            HtmlPageFilter htmlPropsFilter, Map<PropertyTypeDefinition, PropertyEditPreprocessor> propertyPreprocessors) {
        super(target, objectName);
        this.htmlParser = htmlParser;
        this.htmlPropsFilter = htmlPropsFilter;
        this.propertyPreprocessors = propertyPreprocessors;
    }


    @Override
    public void bind(ServletRequest request) {
        if (getTarget() instanceof ResourceEditWrapper) {
            ResourceEditWrapper command = (ResourceEditWrapper) getTarget();

            if (request.getParameter("save") != null) {
                command.setSave(true);
            }
            else if (request.getParameter("saveview") != null) {
                command.setSave(true);
                command.setView(true);
            }
            else if (request.getParameter("savecopy") != null) {
                command.setSaveCopy(true);
            }
            else {
                return;
            }
            Resource resource = command.getResource();

            setProperties(request, command, resource, command.getEditProperties());

            if (command.getContent() != null) {
                String content = command.getContent().getStringRepresentation();
                String postedHtml = request.getParameter("resource.content");
                if (!content.equals(postedHtml)) {
                    parseContent(command, postedHtml);
                }
            }
        }
        else {
            super.bind(request);
        }
    }


    protected void setProperties(ServletRequest request, ResourceEditWrapper command, Resource resource,
            List<PropertyTypeDefinition> propDefs) {

        for (PropertyTypeDefinition propDef : propDefs) {
            String value = null;

            if (propDef.getType().equals(PropertyType.Type.TIMESTAMP)
                    || propDef.getType().equals(PropertyType.Type.DATE)) {
                value = request.getParameter("resource." + propDef.getName() + ".date");
                String time = request.getParameter("resource." + propDef.getName() + ".hours");
                if (value != null && time != null && !time.trim().equals("")) {
                    String minutes = request.getParameter("resource." + propDef.getName() + ".minutes");
                    if (minutes != null && !minutes.trim().equals("")) {
                        time += ":" + minutes;
                    }
                    value += " " + time;
                }
            } else {
                value = request.getParameter("resource." + propDef.getName());
            }

            Property prop = resource.getProperty(propDef);
            if (prop == null) {
                if ((value == null || value.trim().equals("")) && propDef.isMandatory()) {
                    command.reject(propDef, propDef.getName() + " is required ");
                    continue;
                }
                if (value == null || value.trim().equals("")) {
                    continue;
                }
                try {
                    prop = propDef.createProperty();
                    Object o = getValue(value, prop);
                    if (o == null && propDef.isMandatory()) {
                        command.reject(propDef, propDef.getName() + " is required ");
                        continue;
                    }
                    if (o == null) {
                        continue;
                    }
                    if (o instanceof Value[]) {
                        prop.setValues((Value[]) o);
                    } else {
                        prop.setValue((Value) o);
                    }
                    resource.addProperty(prop);
                    command.setPropChange(true);
                } catch (Throwable t) {
                    command.reject(propDef, t.getMessage());
                    resource.removeProperty(propDef);
                }
                continue;
            } 
            
            if (value == null || value.trim().equals("")) {
                if (propDef.isMandatory()) {
                    command.reject(propDef, propDef.getName() + " is required");
                    continue;
                }
                command.setPropChange(true);
                resource.removeProperty(propDef);
                continue;
            } 
            
            try {
                Object o = getValue(value, prop);
                if (o == null && propDef.isMandatory()) {
                    command.reject(propDef, propDef.getName() + " is required ");
                    continue;
                }
                if (o == null) {
                    resource.removeProperty(propDef);
                    command.setPropChange(true);
                    continue;
                }
                if (o instanceof Value[]) {
                    Value[] values = (Value[]) o;
                    prop.setValues(values);
                    command.setPropChange(true);
                    continue;
                } 
                prop.setValue((Value) o);
                command.setPropChange(true);
            } catch (Throwable t) {
                command.reject(propDef, t.getMessage());
            }
        }
    }
    
    protected Object getValue(String valueString, Property prop) {
        PropertyTypeDefinition propDef = prop.getDefinition();

        if (this.propertyPreprocessors != null && this.propertyPreprocessors.containsKey(propDef)) {
            valueString = this.propertyPreprocessors.get(propDef).preprocess(valueString, prop);
        }

        if (propDef.isMultiple()) {
            String[] strings = valueString.split(",");
            if (strings.length == 0) {
                throw new IllegalArgumentException("Value cannot be empty");
            }

            List<Value> values = new ArrayList<Value>();
            for (String string : strings) {
                if (StringUtils.isNotBlank(string)) {
                    values.add(propDef.getValueFormatter().stringToValue(string.trim(), null, null));
                }
            }
            return values.toArray(new Value[values.size()]);
        } else if (prop.getDefinition().getType() == PropertyType.Type.HTML) {

            try {
                HtmlFragment fragment = this.htmlParser.parseFragment(valueString);
                fragment.filter(this.htmlPropsFilter);
                String s = fragment.getStringRepresentation();
                if ("".equals(s.trim())) {
                    return null;
                }
                Value value = new Value(s, PropertyType.Type.HTML);
                return value;
            } catch (Throwable t) {
                throw new IllegalArgumentException(t);
            }

        } else {
            Value value = propDef.getValueFormatter().stringToValue(valueString, null, null);
            return value;
        }
    }


    protected void parseContent(ResourceEditWrapper command, String postedHtml) {

        if (postedHtml == null) postedHtml = "";
        try {
            postedHtml = "<html><head></head><body>" + postedHtml + "</body></html>";
            ByteArrayInputStream in = null;
            HtmlPage parsed = null;

            if (Charset.isSupported(command.getResource().getCharacterEncoding())) {
                in = new ByteArrayInputStream(postedHtml.getBytes(command.getResource().getCharacterEncoding()));
                parsed = this.htmlParser.parse(in, command.getResource().getCharacterEncoding());
            } else {
                in = new ByteArrayInputStream(postedHtml.getBytes(defaultCharacterEncoding));
                parsed = this.htmlParser.parse(in, defaultCharacterEncoding);
            }

            HtmlElement body = command.getContent().selectSingleElement("html.body");
            HtmlElement suppliedBody = parsed.selectSingleElement("html.body");

            // If body tag is non-existing add standard web-page with supplied body-content
            if (body == null) {
                body = command.getContent().createElement("html.body");
                command.setContent(parsed);
                // Else: Normal behavior
            } else {
                body.setChildNodes(suppliedBody.getChildNodes());
            }
            command.setContentChange(true);
        } catch (Throwable t) {
            throw new RuntimeException("Unable to save content", t);
        }
    }


    public void setHtmlParser(HtmlPageParser htmlParser) {
        this.htmlParser = htmlParser;
    }


    protected HtmlPageParser getHtmlParser() {
        return htmlParser;
    }

}
