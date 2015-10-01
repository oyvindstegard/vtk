/* Copyright (c) 2015, University of Oslo, Norway
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
package vtk.text.html;

import java.util.Set;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Required;

/**
 * Basic OWASP HTML sanitizer integration with our own HTML configuration.
 * <p>Configure a {@link PolicyFactory} instance from set of valid HTML element
 * descriptors.
 * 
 * <p>
 * The resulting policy will <em>only</em> allow the valid elements/attributes and
 * disallow everything else. Standard URL protocols are allowed for elements with
 * such attribute values.
 */
public class HtmlSanitizerPolicyFactoryBean implements FactoryBean<PolicyFactory> {

    private PolicyFactory instance;
    
    @Override
    public PolicyFactory getObject() throws Exception {
        return instance;
    }

    @Override
    public Class<?> getObjectType() {
        return PolicyFactory.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Required
    public void setValidElements(Set<HtmlElementDescriptor> elements) {
        if (elements == null) {
            throw new IllegalArgumentException("elements cannot be null");
        }
        
        HtmlPolicyBuilder builder = new HtmlPolicyBuilder().allowStandardUrlProtocols();
        for (HtmlElementDescriptor desc: elements) {
            builder.allowElements(desc.getName());
            for (String attr: desc.getAttributes()) {
                builder.allowAttributes(attr).onElements(desc.getName());
            }
        }
        
        instance = builder.toFactory();
    }
    
}
