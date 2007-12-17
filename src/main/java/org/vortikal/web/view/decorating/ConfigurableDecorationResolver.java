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
package org.vortikal.web.view.decorating;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.security.SecurityContext;
import org.vortikal.util.web.URLUtil;
import org.vortikal.web.RequestContext;


public class ConfigurableDecorationResolver implements DecorationResolver, InitializingBean {

    private static Log logger = LogFactory.getLog(
        ConfigurableDecorationResolver.class);

    private TemplateManager templateManager;
    private Properties decorationConfiguration;
    private PropertyTypeDefinition parseableContentPropDef;
    private Repository repository; 
    private Map<String, RegexpCacheItem> regexpCache = new HashMap<String, RegexpCacheItem>();
    
    private class RegexpCacheItem {
        String string;
        Pattern compiled;
    }
    
    public void setTemplateManager(TemplateManager templateManager) {
        this.templateManager = templateManager;
    }
    
    
    public void setDecorationConfiguration(Properties decorationConfiguration) {
        this.decorationConfiguration = decorationConfiguration;
    }


    public void afterPropertiesSet() {
        if (this.templateManager == null) {
            throw new BeanInitializationException(
                "JavaBean property 'templateManager' not set");
        }

        if (this.decorationConfiguration == null) {
            throw new BeanInitializationException(
                "JavaBean property 'decorationConfiguration' not set");
        }
        
        if (this.parseableContentPropDef != null && this.repository == null) {
            throw new BeanInitializationException(
            "JavaBean property 'repository' must be set when property " +
            "'parseableContentPropDef' is set");
        }
    }

    public DecorationDescriptor resolve(HttpServletRequest request,
                                        Locale locale) throws Exception {

        InternalDescriptor descriptor = new InternalDescriptor();
        
        RequestContext requestContext = RequestContext.getRequestContext();
        String uri = requestContext.getResourceURI();

        String paramString = checkRegexpMatch(uri);
        if (paramString == null) {
            paramString = checkPathMatch(uri);
        }
        if (paramString != null) {
            populateDescriptor(descriptor, locale, paramString);
        }
        
        // Checking if there is a reason to parse content
        if (this.parseableContentPropDef != null && descriptor.parse) {
            Resource resource = null;
            String token = SecurityContext.getSecurityContext().getToken();
            try {
                resource = this.repository.retrieve(token, uri, true);
            } catch (Exception e) {
                throw new RuntimeException("Unrecoverable error when decorating '" + uri + "'", e);
            }
            
            if (resource.getProperty(this.parseableContentPropDef) == null) {
                descriptor.parse = false;
            }
        }
        return descriptor;
    }

    private String checkRegexpMatch(String uri) {
        Enumeration<?> keys = this.decorationConfiguration.propertyNames();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            if (!key.startsWith("regexp[")) {
                continue;
            }

            RegexpCacheItem cached = this.regexpCache.get(key);
            if (cached == null || !cached.string.equals(key)) {
                cached = new RegexpCacheItem();
                cached.string = key;
                cached.compiled = parseRegexpParam(key);
                synchronized(this.regexpCache) {
                    this.regexpCache.put(key, cached);
                }
            }
            if (cached.compiled == null) {
                continue;
            }
            
            Matcher m = cached.compiled.matcher(uri);
            if (m.find()) {
                return this.decorationConfiguration.getProperty(key);
            } 
        }
        return null;
    }

    private String checkPathMatch(String uri) {
        String[] path = URLUtil.splitUriIncrementally(uri);
        for (int i = path.length - 1; i >= 0; i--) {
            String prefix = path[i];
            String value = this.decorationConfiguration.getProperty(prefix);

            if (value != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Found match for URI prefix '" + prefix
                                 + "': descriptor: '" + value + "'");
                }
                return value;
            }
        }
        return null;
    }
    
    // Example: regexp[/foo/bar/.*\.html]
    private Pattern parseRegexpParam(String s) {
        try {
            String regexp = s.substring("regexp[".length(), s.length() - 1);
            return Pattern.compile(regexp, Pattern.DOTALL);
        } catch (Throwable t) {
            return null;
        }
    }
    
    private void populateDescriptor(InternalDescriptor descriptor, Locale locale, 
                                    String paramString) throws Exception {
        String[] params = paramString.split(",");
        for (String param : params) {
            if ("NONE".equals(param)) {
                descriptor.tidy = false;
                descriptor.parse = false;
                descriptor.template = null;
                break;
            } else if ("TIDY".equals(param)) {
                descriptor.tidy = true;
            } else if ("NOPARSING".equals(param)) {
                descriptor.parse = false;
            } else {
                descriptor.template = resolveTemplateReference(locale, param);
            }
        }
        
    }
    

    private class InternalDescriptor implements DecorationDescriptor {
        private boolean tidy = false;
        private boolean parse = true;
        private Template template = null;
        
        public boolean decorate() {
            return this.template != null || this.tidy || this.parse;
        }
        
        public boolean tidy() {
            return this.tidy;
        }
        public boolean parse() {
            return this.parse;
        }
        public Template getTemplate() {
            return this.template;
        }
    }


    private Template resolveTemplateReference(Locale locale, String mapping)
        throws Exception {

        String[] localizedRefs = buildLocalizedReferences(mapping, locale);
        for (int j = 0; j < localizedRefs.length; j++) {
            String localizedRef = localizedRefs[j];
            Template t = this.templateManager.getTemplate(localizedRef);
            if (t != null) {
                return t;
            }
        }
        return null;
    }
    

    private String[] buildLocalizedReferences(String ref, Locale locale) {
        String base = ref;
        String extension = "";
        int baseIdx = ref.lastIndexOf(".");
        int slashIdx = ref.lastIndexOf("/");
        if (baseIdx != -1 && slashIdx < baseIdx) {
            base = ref.substring(0, baseIdx);
            extension = ref.substring(baseIdx);
        }

        String language = locale.getLanguage();
        String country = locale.getCountry();
        String variant = locale.getVariant();
        
        List<String> references = new ArrayList<String>();
        if (!"".equals(country) && !"".equals(variant)) {
            references.add(base + "_" + language + "_" + country + "_" + variant + extension);
        }
        if (!"".equals(country)) {
            references.add(base + "_" + language + "_" + country + "_" + extension);
        }
        references.add(base + "_" + language + extension);
        references.add(base + extension);
        if (logger.isDebugEnabled()) {
            logger.debug("Attempting to resolve template ref '" + ref + "' using "
                         + "sequence " + references);
        }
        return references.toArray(new String[references.size()]);
    }


    public void setParseableContentPropDef(
            PropertyTypeDefinition parseableContentPropDef) {
        this.parseableContentPropDef = parseableContentPropDef;
    }


    public void setRepository(Repository repository) {
        this.repository = repository;
    }
    
    
}
