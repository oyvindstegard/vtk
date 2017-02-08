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

import java.util.Enumeration;
import java.util.Locale;
import java.util.ResourceBundle;

import org.springframework.context.support.ResourceBundleMessageSource;

public class MessageSourceValueFormatter implements ValueFormatter {

    private final ReversableMessageSource messageSource = new ReversableMessageSource();
    private final PropertyType.Type type;

    private String keyPrefix = "value.";
    private String unsetKey = "unset";
    
    public MessageSourceValueFormatter(String... messageSourceBaseNames) {
        this(PropertyType.Type.STRING, messageSourceBaseNames);
    }

    public MessageSourceValueFormatter(PropertyType.Type type, String... messageSourceBaseNames) {
        messageSource.addBasenames(messageSourceBaseNames);
        this.type = type;
    }

    /**
     * Add a base name to set of basenames for this message source.
     * @param basenames like {@link ResourceBundleMessageSource#addBasenames(java.lang.String...) }
     */
    public void addMessageSourceBasenames(String... basenames) {
        messageSource.addBasenames(basenames);
    }

    /**
     * Set a single basename for i18n resource bundle.
     * @param basename
     */
    public void setMessageSourceBasename(String basename) {
        messageSource.setBasename(basename);
    }

    @Override
    public String valueToString(Value value, String format, Locale locale)
            throws IllegalValueTypeException {
        if ("localized".equals(format)) {
            if (value == null || "".equals(value.toString())) {
                return this.messageSource.getMessage(this.unsetKey, null, "unset", locale);
            }
            
            return this.messageSource.getMessage(keyPrefix + value.toString(), null, value.toString(), locale);
        }
        return value.toString();
    }

    @Override
    public Value stringToValue(String string, String format, Locale locale) {
        if (string == null) {
            throw new IllegalArgumentException("Cannot get value for 'null' formatted value");
        }
        
        if (!"localized".equals(format)) {
            return stringToValueInternal(string);
        }
        
        String value = this.messageSource.getKeyFromMessage(string, locale);
        if (value == null) {
            throw new IllegalArgumentException("Unknown formatted value: " + string);
        }
        
        if (this.unsetKey.equals(value)) {
            return null;
        }
        return stringToValueInternal(value);
    }

    private Value stringToValueInternal(String stringValue) {

        return new Value(stringValue, type);

    }

    private class ReversableMessageSource extends ResourceBundleMessageSource {
        
        public String getKeyFromMessage(String value, Locale locale) {

            for (String baseName : getBasenameSet()) {
                ResourceBundle resourceBundle = getResourceBundle(baseName, locale);
                Enumeration<String> keys = resourceBundle.getKeys();
                while (keys.hasMoreElements()) {
                    String key = keys.nextElement();
                    String keyValue = resourceBundle.getString(key);
                    if (keyValue.equals(value)) {
                        return key;
                    }
                }
            }

            return null;
        }
    }

    /**
     * Prefix used for value keys. Default is <code>"value."</code>.
     * @param keyPrefix 
     */
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /**
     * Name of the key for unset value. Default is <code>"unset"</code>.
     * @param unsetKey
     */
    public void setUnsetKey(String unsetKey) {
        this.unsetKey = unsetKey;
    }
}
