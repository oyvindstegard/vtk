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
package org.vortikal.repository.resourcetype;

import java.util.Locale;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.vortikal.repository.Path;
import org.vortikal.web.RequestContext;
import org.vortikal.web.service.Service;
import org.vortikal.web.service.URL;

/**
 * 
 * XXX: fix lazy bean-lookup
 */
public class ImageRefValueFormatter implements ValueFormatter, ApplicationContextAware {

    private String thumbnailServiceBeanName;
    private boolean requireThumbnailService = false; 
    private ApplicationContext applicationContext;
    
    public Value stringToValue(String string, String format, Locale locale) {
        return new Value(string, PropertyType.Type.STRING);
    }

    public String valueToString(Value value, String format, Locale locale)
            throws IllegalValueTypeException {
        String val = value.getStringValue();
        if (val.startsWith("http://") || val.startsWith("https://")) {
            return val;
        }
        if (!"thumbnail".equals(format)) {
            return val;
        }
        Service thumbnailService = null;
        if (this.thumbnailServiceBeanName != null) {
            Object obj = this.applicationContext.getBean(
                    this.thumbnailServiceBeanName);
            if (obj != null && obj instanceof Service) {
                thumbnailService = (Service) obj;
            }
        }
        if (this.requireThumbnailService && thumbnailService == null) {
            throw new IllegalStateException(
                    "No bean named '" + this.thumbnailServiceBeanName 
                    + "' defined in context");
        }
        if (thumbnailService == null) {
            return val;
        }
        try {
            Path ref = null;
            if (val.startsWith("/")) {
                ref = Path.fromString(val);
            } else {
                RequestContext requestContext = RequestContext.getRequestContext();
                ref = requestContext.getCurrentCollection().extend(val);
            }
            URL url = thumbnailService.constructURL(ref);
            return url.getPathRepresentation();
        } catch (Throwable t) {
            return val;
        }
    }
    
    public void setThumbnailServiceBeanName(String thumbnailServiceBeanName) {
        this.thumbnailServiceBeanName = thumbnailServiceBeanName;
    }
    
    public void setRequireThumbnailService(boolean requireThumbnailService) {
        this.requireThumbnailService = requireThumbnailService;
    }

    public void setApplicationContext(ApplicationContext applicationContext)
            throws BeansException {
                this.applicationContext = applicationContext;
    }

}
