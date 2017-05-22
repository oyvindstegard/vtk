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
package vtk.web.decorating;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.util.io.InputSource;


public class DelegatingTemplateFactory implements TemplateFactory {
    private static Logger logger = LoggerFactory.getLogger(DelegatingTemplateFactory.class);
    private Map<Pattern, TemplateFactory> templateFactoryMap;
    
    public DelegatingTemplateFactory(Map<String, TemplateFactory> templateFactoryMap) {
        this.templateFactoryMap = new HashMap<>();
        for (String key: templateFactoryMap.keySet()) {
            Pattern p = Pattern.compile(key);
            this.templateFactoryMap.put(p, templateFactoryMap.get(key));
        }
    }
    public Template newTemplate(InputSource templateSource)
            throws InvalidTemplateException {
        for (Pattern p: templateFactoryMap.keySet()) {
            Matcher m = p.matcher(templateSource.getID());
            if (m.find()) {
                TemplateFactory tf = templateFactoryMap.get(p);
                    logger.debug("Creating template for source " 
                            + templateSource + ", using template factory " + tf);
                return tf.newTemplate(templateSource);
            }
        }
            logger.debug("No matching template factory found for ID " 
                    + templateSource.getID());
        return null;
    }

}

