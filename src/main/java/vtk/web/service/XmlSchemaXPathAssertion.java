/* Copyright (c) 2006, University of Oslo, Norway
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
package vtk.web.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import vtk.repository.Property;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.security.Principal;
import vtk.xml.XmlSchemaRegistry;


/**
 * 
 */
public class XmlSchemaXPathAssertion extends AbstractRepositoryAssertion
  implements InitializingBean {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private PropertyTypeDefinition schemaPropertyDefinition;
    private XmlSchemaRegistry schemaRegistry;
    private String xpath;
    private Pattern matchValue;
    

    public void setSchemaPropertyDefinition(PropertyTypeDefinition schemaPropertyDefinition) {
        this.schemaPropertyDefinition = schemaPropertyDefinition;
    }
    
    public void setSchemaRegistry(XmlSchemaRegistry schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    public void setXpath(String xpath) {
        try {
            XPath.newInstance(xpath);
        } catch (JDOMException e) {
            throw new IllegalArgumentException("Illegal XPath expression '"
                    + xpath + "': " + e.getMessage());
        }
        this.xpath = xpath;
    }

    public void setMatchValue(String matchValue) {
        this.matchValue = Pattern.compile(matchValue);
    }
    

    public void afterPropertiesSet() throws Exception {
        if (this.schemaPropertyDefinition == null) {
            throw new BeanInitializationException(
                "JavaBean property 'schemaPropertyDefinition' not specified");
        }
        if (this.schemaRegistry == null) {
            throw new BeanInitializationException(
                "JavaBean property 'schemaRegistry' not specified");
        }
        if (this.xpath == null) {
            throw new BeanInitializationException(
                "JavaBean property 'xpath' not specified");
        }
    }


    public boolean conflicts(Assertion assertion) {
        return false;
    }



    public boolean matches(Resource resource, Principal principal) {

        if (resource.isCollection()) {
            return false;
        }

        Property schemaProp = resource.getProperty(this.schemaPropertyDefinition);
        if (schemaProp == null) {
            return false;
        }
        String docType = schemaProp.getStringValue();
        Document schema = null;
        try {
            schema = this.schemaRegistry.getXMLSchema(docType);
        } catch (Exception e) {
            // Unable to get schema
            logger.warn("Unable to obtain XML schema from registry: " + docType, e);
        }
            
        if (schema == null) {
            return false;
        }

        try {
            String stringVal = XPath.newInstance(this.xpath).valueOf(schema);
            if (logger.isDebugEnabled()) {
                logger.debug("Got string value '" + stringVal
                        + "' from XPath evaluation "
                        + "of expression on schema: '" + this.xpath + "'");
            }

            if (stringVal == null || "".equals(stringVal)) {
                return false;
            }

            if (this.matchValue != null) {
                Matcher m = this.matchValue.matcher(stringVal);
                boolean match = m.matches();
                if (logger.isDebugEnabled()) {
                    logger.debug("Got regular expression match: " + match
                            + " for string value '" + stringVal + "'");
                }
                return match;
            }

            return true;

        } catch (Exception e) {
            throw new RuntimeException(
                "Unable to match on resource " + resource, e);
        }
    }

    public String toString() {
        return "document.xmlschema exists and document.xmlschema.matches(" + this.xpath + ")";
    }

}
