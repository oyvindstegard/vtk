/* Copyright (c) 2007, University of Oslo, Norway
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
package org.vortikal.edit.editor;

import java.io.ByteArrayInputStream;

import javax.servlet.ServletRequest;

import org.springframework.web.bind.ServletRequestDataBinder;
import org.vortikal.repository.Property;
import org.vortikal.repository.Resource;
import org.vortikal.repository.resourcetype.PropertyType;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.text.html.HtmlElement;
import org.vortikal.text.html.HtmlPage;
import org.vortikal.text.html.HtmlPageParser;
import org.vortikal.text.html.HtmlSelectUtil;

public class ResourceEditDataBinder extends ServletRequestDataBinder {

    private HtmlPageParser htmlParser;
    
    public ResourceEditDataBinder(Object target, String objectName, HtmlPageParser htmlParser) {
        super(target, objectName);
        this.htmlParser = htmlParser;
    }

    @Override
    public void bind(ServletRequest request) {
        if (getTarget() instanceof ResourceEditWrapper) {
            ResourceEditWrapper command = (ResourceEditWrapper) getTarget();

            if (request.getParameter("save") != null) {
                command.setSave(true);
            } else if (request.getParameter("savequit") != null) {
                command.setSave(true);
                command.setQuit(true);
            } else {
                return;
            }
            
            
            Resource resource = command.getResource();
            
            for (PropertyTypeDefinition propDef: command.getContentProperties()) {
                
                String value = null;
                if (propDef.getType().equals(PropertyType.Type.TIMESTAMP) ||
                        propDef.getType().equals(PropertyType.Type.DATE)) {
                    value = request.getParameter("resource." + propDef.getName() + ".date");
                    String time = request.getParameter("resource." + propDef.getName() + ".time");
                    if (value != null && time != null) {
                        value += " " + time;
                    }
                } else
                    value = request.getParameter("resource." + propDef.getName());

                Property prop = resource.getProperty(propDef);
                if (prop == null) {
                    if (value != null && !value.trim().equals("")) {
                        try {
                            prop = resource.createProperty(propDef);
                            setPropValue(value, prop);
                            command.setPropChange(true);
                        } catch (Throwable t) {
                            command.reject(propDef, t.getMessage());
                            resource.removeProperty(propDef);
                        }
                        continue;
                    } else if (propDef.isMandatory()) {
                        command.reject(propDef, propDef.getName() + " is required ");
                        continue;
                    }
                } else if (value == null || value.trim().equals("")) {
                    if (propDef.isMandatory()) {
                        command.reject(propDef, propDef.getName() + " is required");
                        continue;
                    } 
                    command.setPropChange(true);
                    resource.removeProperty(propDef);
                    continue;
                } else {
                    try {
                        setPropValue(value, prop);
                        command.setPropChange(true);
                    } catch (Throwable t) {
                        command.reject(propDef, t.getMessage());
                    }
                }
            }
            
            String content = command.getContent().getStringRepresentation();
            String suppliedContent = request.getParameter("resource.content");
            if (!content.equals(suppliedContent)) {
                parseContent(command, suppliedContent);
            }
        } else {
            super.bind(request);
        }
    }

    

    
    private void setPropValue(String valueString, Property prop) throws IllegalArgumentException {
    	PropertyTypeDefinition propDef = prop.getDefinition();

        if (propDef.isMultiple()) {
            String[] strings = valueString.split(",");
            Value[] values = new Value[strings.length];

            int i = 0;
            for (String string : strings) {
                if (string.trim().equals("")) {
                    throw new IllegalArgumentException("Value cannot be empty");
                } 
                values[i++] = propDef.getValueFormatter().stringToValue(string.trim(), null, null);
            }
            prop.setValues(values);
        } else {
            Value value = propDef.getValueFormatter().stringToValue(valueString, null, null);
            prop.setValue(value);
        }
    }

    private void parseContent(ResourceEditWrapper command, String suppliedContent) {

        if (suppliedContent == null) suppliedContent = "";
        try {
            suppliedContent = "<html><head></head><body>" + suppliedContent + "</body></html>";
            ByteArrayInputStream in = new ByteArrayInputStream(suppliedContent.getBytes(command.getResource().getCharacterEncoding()));
            HtmlPage parsed = this.htmlParser.parse(in, command.getResource().getCharacterEncoding());

            HtmlElement body = HtmlSelectUtil.selectSingleElement(command.getContent(), "html.body");
            HtmlElement suppliedBody = HtmlSelectUtil.selectSingleElement(parsed, "html.body");
            
            if (body == null || suppliedBody == null) {
                throw new RuntimeException("No HTML body to save");
            }
            body.setChildNodes(suppliedBody.getChildNodes());
            command.setContentChange(true);
        } catch (Throwable t) {
            throw new RuntimeException("Unable to save content", t);
        }
    }
    
}
