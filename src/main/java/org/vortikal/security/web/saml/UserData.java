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
package org.vortikal.security.web.saml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.opensaml.common.xml.SAMLConstants;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.saml2.core.AttributeValue;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.schema.XSString;


public class UserData {

    private Map<String, List<String>> attrs = new HashMap<String, List<String>>();

    public UserData(Assertion assertion) {
        for (AttributeStatement attrStatement : assertion.getAttributeStatements()) {
            for (Attribute attr : attrStatement.getAttributes()) {
                this.attrs.put(attr.getName(), extractValues(attr));
            }
        }
    }


    public String getUsername() {
        return getSimpleAttribute("uid");
    }


    public String getCommonName() {
        return getSimpleAttribute("cn");
    }

    
    public List<String> getAttribute(String name) {
        return Collections.unmodifiableList(this.attrs.get(name));
    }
    
    public String getSimpleAttribute(String name) {
        List<String> list = this.attrs.get(name);
        if (list == null) {
            return null;
        }
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    private List<String> extractValues(Attribute attribute) {
        
        List<XMLObject> values = attribute.getAttributeValues();
        List<String> result = new ArrayList<String>();

        for (XMLObject val : values) {
            if (!(val instanceof XSString)) {
                continue;
            }
            XSString s = (XSString) val;
            QName qname = s.getElementQName();
            if (!SAMLConstants.SAML20_NS.equals(qname.getNamespaceURI())) {
                continue;
            }
            if (!AttributeValue.DEFAULT_ELEMENT_LOCAL_NAME.equals(qname.getLocalPart())) {
                continue;
            }
            result.add(s.getValue());
        }
        return result;
    }
}
