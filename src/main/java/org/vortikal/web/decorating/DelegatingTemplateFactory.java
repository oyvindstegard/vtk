/* Copyright (c) 2008, University of Oslo, Norway
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
package org.vortikal.web.decorating;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.util.io.InputSource;


public class DelegatingTemplateFactory implements TemplateFactory {
    private static Log logger = LogFactory.getLog(DelegatingTemplateFactory.class);
    private Map<Pattern, TemplateFactory> templateFactoryMap;
    
    public Template newTemplate(InputSource templateSource)
            throws InvalidTemplateException {
        for (Pattern p: this.templateFactoryMap.keySet()) {
            Matcher m = p.matcher(templateSource.getID());
            if (m.find()) {
                TemplateFactory tf = this.templateFactoryMap.get(p);
                if (logger.isDebugEnabled()) {
                    logger.debug("Creating template for source " 
                            + templateSource + ", using template factory " + tf);
                }
                return tf.newTemplate(templateSource);
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("No matching template factory found for ID " 
                    + templateSource.getID());
        }
        return null;
    }

    @Required public void setTemplateFactoryMap(Map<String, TemplateFactory> templateFactoryMap) {
        this.templateFactoryMap = new HashMap<Pattern, TemplateFactory>();
        for (String key: templateFactoryMap.keySet()) {
            Pattern p = Pattern.compile(key);
            this.templateFactoryMap.put(p, templateFactoryMap.get(key));
        }
    }
}

