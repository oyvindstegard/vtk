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
package vtk.repository.resourcetype.property;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jaxen.SimpleFunctionContext;
import org.jaxen.SimpleNamespaceContext;
import org.jaxen.SimpleVariableContext;
import org.jaxen.XPath;
import org.jaxen.XPathFunctionContext;
import org.jaxen.jdom.JDOMXPath;
import org.jdom.Attribute;
import org.jdom.Document;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Required;
import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.resourcetype.PropertyEvaluator;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.Value;
import vtk.repository.resourcetype.ValueFactory;
import vtk.xml.xpath.XPathFunction;

/**
 * Evaluates XPath expressions on an XML document.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>expression</code> - the XPath expression
 *   <li><code>trimValues</code> - whether or not to trim (remove
 *   leading and trailing whitespace) from (textual) evaluated
 *   values. Default is <code>true</code>.
 *   <li><code>customFunctions</code> - a {@link Set<XPathFunction>
 *   set of custom functions} that are made available to the XPath
 *   evaluation
 * </ul>
 *
 */
public class XPathEvaluator implements PropertyEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(XPathEvaluator.class);

    private ValueFactory valueFactory;
    
    private String expression;
    private boolean trimValues = true;
    private Set<XPathFunction> customFunctions;
    
    public void setExpression(String expression) {
        this.expression = expression;
    }
    
    public void setTrimValues(boolean trimValues) {
        this.trimValues = trimValues;
    }

    public void setCustomFunctions(Set<XPathFunction> customFunctions) {
        this.customFunctions = customFunctions;
    }

    public void afterPropertiesSet() {
        if (this.expression == null) {
            throw new BeanInitializationException(
                "JavaBean property 'expression' not specified");
        }
    }

    public boolean evaluate(Property property, PropertyEvaluationContext ctx) throws PropertyEvaluationException {
        if (ctx.getContent() == null) {
            return false;
        }
        try {
            XPath xpath = createXPath(ctx);
            Document doc = null;
            doc = ctx.getContent().getContentRepresentation(Document.class);
            if (doc == null) {
                return false;
            }
            
            if (property.getDefinition().isMultiple()) {
                @SuppressWarnings("unchecked")
                List<Object> list = xpath.selectNodes(doc);
                if (list.size() == 0) {
                    return false;
                }
                
                Value[] values = new Value[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    Object o = list.get(i);
                    Value value = getValue(o, property.getDefinition().getType());
                    if (value == null) {
                        return false;
                    }
                    values[i] = value;
                }
                property.setValues(values);
            } else {
                Object o = xpath.selectSingleNode(doc);
                Value value = getValue(o, property.getDefinition().getType());
                if (value == null) {
                    return false;
                }
                property.setValue(value);
            }
            return true;
        } catch (Throwable e) {
            if (logger.isDebugEnabled()) {
                logger.warn("Unable to evaluate property " + property
                            + " using XPath expression '" + this.expression + "'", e);
            }
            return false;
        }
    }


    private XPath createXPath(PropertyEvaluationContext ctx) throws Exception {

        XPath xpath = new JDOMXPath(this.expression);

        SimpleFunctionContext fc = new XPathFunctionContext();
        SimpleNamespaceContext nc = new SimpleNamespaceContext();
        for (XPathFunction func: this.customFunctions) {
            fc.registerFunction(func.getNamespace(), func.getName(), func);
            nc.addNamespace(func.getPrefix(), func.getNamespace());
        }

        xpath.setFunctionContext(fc);
        xpath.setNamespaceContext(nc);

        SimpleVariableContext vc = new SimpleVariableContext();
        vc.setVariableValue("vrtx", "resource", ctx.getNewResource());
        xpath.setVariableContext(vc);

        return xpath;
    }
    

    private Value getValue(Object o, Type type) {
        String stringVal = null;

        if (o == null) {
            return null;
        }
        if (o instanceof org.jdom.Content) {
            stringVal = ((org.jdom.Content) o).getValue();
        } else if (o instanceof Attribute) {
            stringVal = ((Attribute) o).getValue();
        } else if (o instanceof String) {
            stringVal = (String) o;
        } else if (o instanceof Boolean) {
            stringVal = String.valueOf((Boolean) o);
        } else {
            throw new IllegalArgumentException(
                "Unsupported class: " + o.getClass());
        }

        if (this.trimValues && stringVal != null) {
            stringVal = stringVal.trim();
        }
        if ("".equals(stringVal)) {
            return null;
        }
        return this.valueFactory.createValue(stringVal, type);
     }

    @Required
    public void setValueFactory(ValueFactory valueFactory) {
        this.valueFactory = valueFactory;
    }

}
