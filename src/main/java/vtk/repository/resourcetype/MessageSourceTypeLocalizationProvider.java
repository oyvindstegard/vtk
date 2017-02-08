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
package vtk.repository.resourcetype;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.MessageSource;

/**
 * Provides i18n for properties and resource types.
 */
public class MessageSourceTypeLocalizationProvider implements TypeLocalizationProvider  {

    public static final String PROPERTY_TYPE_NAME_KEY_PREFIX = "proptype.name.";
    public static final String PROPERTY_TYPE_DESC_KEY_PREFIX = "proptype.description.";
    public static final String RESOURCE_TYPE_NAME_KEY_PREFIX = "resourcetype.name.";
    
    private MessageSource messageSource;

    @Override
    public String getLocalizedPropertyName(PropertyTypeDefinition def,
            Locale locale) {

        String key = null;
        String prefix = def.getNamespace().getPrefix();
        String name = def.getName();
        if (prefix != null){
            key = PROPERTY_TYPE_NAME_KEY_PREFIX + prefix + ":" + name;
        } else {
            key = PROPERTY_TYPE_NAME_KEY_PREFIX + name;
        }

        return this.messageSource.getMessage(key, null, name, locale);
    }

    @Override
    public String getPropertyDescription(PropertyTypeDefinition def,
            Locale locale) {
        String key = null;
        String prefix = def.getNamespace().getPrefix();
        String name = def.getName();
        if (prefix != null){
            key = PROPERTY_TYPE_DESC_KEY_PREFIX + prefix + ":" + name;
        } else {
            key = PROPERTY_TYPE_DESC_KEY_PREFIX + name;
        }

        return this.messageSource.getMessage(key, null, null, locale);
    }

    @Override
    public String getLocalizedResourceTypeName(ResourceTypeDefinition def,
            Locale locale) {
        String name = def.getName();
        String key = RESOURCE_TYPE_NAME_KEY_PREFIX + name;
        return this.messageSource.getMessage(key, null, name, locale);
    }
    
    @Required
    public void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource;
    }
}
