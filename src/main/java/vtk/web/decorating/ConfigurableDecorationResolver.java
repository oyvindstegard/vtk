/* Copyright (c) 2007, 2008, University of Oslo, Norway
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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.servlet.LocaleResolver;

import vtk.repository.AuthorizationException;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceNotFoundException;
import vtk.repository.TypeInfo;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.util.text.PathMappingConfig;
import vtk.util.text.PathMappingConfig.ConfigEntry;
import vtk.util.text.PathMappingConfig.Qualifier;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class ConfigurableDecorationResolver implements DecorationResolver, InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(
        ConfigurableDecorationResolver.class);

    private TemplateManager templateManager;
    private volatile PathMappingConfig<String> config;
    private Path configPath;
    private Properties decorationConfiguration;
    private PropertyTypeDefinition parseableContentPropDef;
    private Repository repository; 
    private boolean supportMultipleTemplates = false;
    private Map<String, RegexpCacheItem> regexpCache = new HashMap<>();
    private LocaleResolver localeResolver = null;
    private long maxDocumentSize = -1;

    private static class RegexpCacheItem {
        String string;
        Pattern compiled;
    }

    @Required
    public void setTemplateManager(TemplateManager templateManager) {
        this.templateManager = templateManager;
    }

    @Required
    public void setDecorationConfiguration(Properties decorationConfiguration) {
        this.decorationConfiguration = decorationConfiguration;
    }

    @Required
    public void setPathMappingConfiguration(String configUri) {
        this.configPath = Path.fromString(configUri);
    }

    public void setSupportMultipleTemplates(boolean supportMultipleTemplates) {
        this.supportMultipleTemplates = supportMultipleTemplates;
    }

    public void setParseableContentPropDef(
            PropertyTypeDefinition parseableContentPropDef) {
        this.parseableContentPropDef = parseableContentPropDef;
    }

    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }
    
    public void setLocaleResolver(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }
    
    public void setMaxDocumentSize(long maxDocumentSize) {
        this.maxDocumentSize = maxDocumentSize;
    }

    @Override
    public void afterPropertiesSet() {
        loadPathMappingConfiguration();
    }

    /**
     * Reload path mapping configuration from repository.
     */
    public void loadPathMappingConfiguration() {
        logger.debug("Reload path mapping config from {}", configPath);
        try (InputStream is = repository.getInputStream(null, configPath, true)) {
            this.config = PathMappingConfig.strConfig(is);
        } catch(Throwable t) {
            logger.error("Unable to create PathMappingConfig from configuration file " + configPath + ": " + t.getMessage(), t);
        }
    }

    @Override
    public DecorationDescriptor resolve(HttpServletRequest request,
                                        HttpServletResponse response) {
        InternalDescriptor descriptor = new InternalDescriptor();
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Path uri = requestContext.getResourceURI();
        Resource resource = null;
        TypeInfo typeInfo = null;
        String token = requestContext.getSecurityToken();
        try {
            resource = this.repository.retrieve(token, uri, true);
            typeInfo = this.repository.getTypeInfo(resource);
            
        }
        catch (ResourceNotFoundException e) {
        }
        catch (AuthorizationException e) {
        }
        catch (Throwable t) {
            throw new RuntimeException(
                    "Unrecoverable error when decorating '" + uri + "'", t);
        }

        if (resource != null && this.maxDocumentSize > 0
                // FIXME: check response.length instead:
                && typeInfo.isOfType("text") 
                && resource.getContentLength() >= this.maxDocumentSize) {
            descriptor.parse = false;
            descriptor.tidy = false;
            if (logger.isInfoEnabled()) {
                logger.info("Not decorating " + request.getRequestURI() 
                        + ": document size too large: " + resource.getContentLength());
            }
            return descriptor;
        }
        
        String paramString = null;
        
        boolean errorPage = false;
        
        response = mapStatusCode(request, response);
        
        int status = response.getStatus();
        
        if (status >= 400) {
            paramString = checkErrorCodeMatch(request, status);
            errorPage = true;
        }
        
        if (paramString == null && !errorPage) {
            paramString = checkRegexpMatch(uri.toString());
        }
        
        if (paramString == null && !errorPage) {
            paramString = checkPathMatch(request, response, uri, resource);
        }
        
        logger.debug("Decorator descriptor spec: {}", paramString);
        if (paramString != null) {
            Locale locale = new org.springframework.web.servlet.support.RequestContext(
                    request, request.getServletContext()).getLocale();
            populateDescriptor(descriptor, locale, paramString);
        }
        
        // Checking if there is a reason to parse content
        if (this.parseableContentPropDef != null && descriptor.parse) {
            if (resource == null) {
                descriptor.parse = false;
            }
            if (resource != null && !resource.isCollection() && 
                    resource.getProperty(this.parseableContentPropDef) == null) {
                descriptor.parse = false;
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Resolved request " + request.getRequestURI() 
                    + " to decorating descriptor " + descriptor);
        }
        return descriptor;
    }
    
    private HttpServletResponse mapStatusCode(HttpServletRequest request, HttpServletResponse response) {
        // Check for directives: map_status[service:service.name] = <code>
        Service currentService = RequestContext.getRequestContext(request).getService();
        while (currentService != null) {
            Object attr = currentService.getAttribute("decorating.servicePredicateName");
            if (attr != null && attr instanceof String) {
                String value = decorationConfiguration.getProperty(
                        "map_status[service:" + attr + "]");
                if (value != null) {
                    try {
                        int status = Integer.parseInt(value);
                        logger.debug("Mapping status code from {} to {} for service {}", 
                                response.getStatus(), status, currentService.getName());
                        return new HttpServletResponseWrapper(response) {
                            @Override
                            public int getStatus() {
                                return status;
                            }
                        };
                    }
                    catch (NumberFormatException e) { }
                }
            }
            currentService = currentService.getParent();
        }
        return response;
    }
    
    private String checkErrorCodeMatch(HttpServletRequest request, int status) {
        // Check error[status],service[service.name] = spec
        Service currentService = RequestContext.getRequestContext(request).getService();
        while (currentService != null) {
            Object attr = currentService.getAttribute("decorating.servicePredicateName");
            if (attr != null && attr instanceof String) {
                String value = decorationConfiguration.getProperty(
                        "error[" + status + "],service[" + attr + "]");
                if (value != null) {
                    logger.debug("Error code/service match: {}, {}, {}", status, attr, value);
                    return value;
                }
            }
            currentService = currentService.getParent();
        }

        // Check error[status] = spec
        String value = decorationConfiguration.getProperty("error[" + status + "]");
        if (value != null) {
            logger.debug("Error code match[{}]: {}", status, value);
            return value;
        }
        
        // Check error = spec
        value = decorationConfiguration.getProperty("error");
        if (value != null) {
            logger.debug("Error match: {}", value);
            return value;
        }
        return null;
    }
    
    private String checkRegexpMatch(String uri) {
        Enumeration<?> keys = this.decorationConfiguration.propertyNames();
        String result = null;
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            if (!key.startsWith("regexp[")) {
                continue;
            }

            RegexpCacheItem cached = this.regexpCache.get(key); // XXX unsynchronized access to cache map
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
                result = decorationConfiguration.getProperty(key);
            } 
        }
        logger.debug("Regexp match: {}", result);
        return result;
    }

    private String checkPathMatch(HttpServletRequest request,
            HttpServletResponse response,
            Path uri, Resource resource) {
        if (this.config == null) {
            return null;
        }
        List<Path> list = new ArrayList<>(uri.getPaths());
        Collections.reverse(list);

        // TODO: Algorithm: sort entries into "score groups".
        // If several entries exist in the top group, 
        // first look for one that has "exact = true". 
        // Otherwise, just pick first one in top group.
        for (Path path: list) {
            List<ConfigEntry<String>> entries = this.config.get(path);
            Map<ConfigEntry<String>, Integer> score = new HashMap<>();
            
            if (entries != null) {
                for (ConfigEntry entry: entries) {
                    if (entry.exact) {
                        if (!uri.equals(path)) {
                            score.put(entry, -1);
                            break;
                        } 
                        if (score.get(entry) == null) {
                            score.put(entry, 0);
                        }
                        // XXX: boost score for exact entries by 10 (temporarily).
                        // See TODO above.
                        score.put(entry, score.get(entry) + 10);
                    }
                    List<Qualifier> predicates = entry.qualifiers;
                    if (score.get(entry) == null) {
                        score.put(entry, 0);
                    }
                    for (Qualifier predicate: predicates) {
                        if (!matchPredicate(request, response, predicate, resource)) {
                            score.put(entry, -1);
                            break;
                        }
                        else {
                            if (!score.containsKey(entry)) {
                                score.put(entry, 0);
                            }
                            score.put(entry, score.get(entry) + 1);
                        }
                    }
                }
                int highest = 0;
                ConfigEntry<String> topEntry = null;
                for (ConfigEntry<String> entry: score.keySet()) {
                    if (score.get(entry) >= highest) {
                        highest = score.get(entry);
                        topEntry = entry;
                    }
                }
                if (topEntry != null) {
                    logger.debug("Path match: {}, matched: entry: {} = {} (q: {})", 
                            uri, path, topEntry.value, topEntry.qualifiers);
                    return topEntry.value;
                }
            }
        }
        logger.debug("Path match: no entry matched");
        return null;
    }
    

    private boolean matchPredicate(HttpServletRequest request,
            HttpServletResponse response, Qualifier predicate, Resource resource) {
        if ("status".equals(predicate.name)) {
            int status = response.getStatus();
            return String.valueOf(status).equals(predicate.value);
        }
        else if ("type".equals(predicate.name)) {
            if (resource == null) {
                return false;
            }
            return repository.getTypeInfo(resource).isOfType(predicate.value);
        }
        else if ("service".equals(predicate.name)) {

            Service currentService = RequestContext.getRequestContext(request).getService();
            while (currentService != null) {
                Object attr = currentService.getAttribute("decorating.servicePredicateName");
                if (attr != null && attr instanceof String) {
                    String servicePredicate = (String) attr;
                    if (servicePredicate.equals(predicate.value)) {
                        return true;
                    }
                    return false;
                }
                currentService = currentService.getParent();
            }
        }
        else if ("lang".equals(predicate.value)) {
            if (this.localeResolver != null) {
                Locale locale = this.localeResolver.resolveLocale(request);
                String value = predicate.value;
                if (value.equals(locale.getLanguage() + "_" + locale.getCountry() + "_" + locale.getVariant())) {
                    return true;
                }
                if (value.equals(locale.getLanguage() + "_" + locale.getCountry())) {
                    return true;
                }
                if (value.equals(locale.getLanguage())) {
                    return true;
                }
            }
        }
        return false;
    }

    
    // Example: regexp[/foo/bar/.*\.html]
    private Pattern parseRegexpParam(String s) {
        try {
            String regexp = s.substring("regexp[".length(), s.length() - 1);
            return Pattern.compile(regexp, Pattern.DOTALL);
        }
        catch (Throwable t) {
            return null;
        }
    }
    
    private void populateDescriptor(InternalDescriptor descriptor, Locale locale, 
                                    String entry) {
        String[] directives = entry.split(",");
        for (String directive : directives) {
            directive = directive.trim();
            if ("NONE".equals(directive)) {
                descriptor.tidy = false;
                descriptor.parse = false;
                descriptor.templates.clear();
                break;
            }
            else if ("TIDY".equals(directive)) {
                descriptor.tidy = true;
            }
            else if ("NOPARSING".equals(directive)) {
                descriptor.parse = false;
            }
            else {
                String name = directive;
                Map<String, Object> parameters = new HashMap<>();
                int queryIndex = name.indexOf('?');
                if (queryIndex > 0) {
                    String query = name.substring(queryIndex);
                    name = name.substring(0, queryIndex);
                    parameters = splitParameters(query);
                }
                
                Optional<Template> t = resolveTemplateReferences(locale, name);

                if (t.isPresent()) {
                    if (!this.supportMultipleTemplates) {
                        descriptor.templates.clear();
                    }
                    descriptor.templates.add(t.get());
                    descriptor.templateParameters.put(t.get(), parameters);
                }
            }
        }
    }
    

    private Map<String, Object> splitParameters(String paramString) {
        Map<String, String[]> params = URL.splitQueryString(paramString);
        Map<String, Object> result = new HashMap<>();

        for (String name: params.keySet()) {
            String[] values = params.get(name);

            if (values == null || values.length == 0) {
                result.put(name, null);

            }
            else if (values.length > 1) {
                List<Object> list = new ArrayList<>();
                for (int i = 0; i < values.length; i++) {
                    list.add(values[i]);
                }
                result.put(name, list);

            }
            else {
              result.put(name, values[0]);
            }
        }
        return result;
    }
    
    private static class InternalDescriptor implements DecorationDescriptor {
        private boolean tidy = false;
        private boolean parse = true;
        private List<Template> templates = new ArrayList<>();
        private Map<Template, Map<String, Object>> templateParameters = new HashMap<>();
        
        @Override
        public boolean decorate() {
            return !this.templates.isEmpty() || this.tidy || this.parse;
        }
        
        @Override
        public boolean tidy() {
            return this.tidy;
        }
        
        @Override
        public boolean parse() {
            return this.parse;
        }
        
        @Override
        public List<Template> getTemplates() {
            return Collections.unmodifiableList(this.templates);
        }
        
        @Override
        public Map<String, Object> getParameters(Template t) {
            return Collections.unmodifiableMap(this.templateParameters.get(t));
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(this.getClass().getName());
            sb.append(" [templates=").append(this.templates.toString());
            sb.append(", parse=").append(this.parse);
            sb.append(", tidy=").append(this.tidy).append("]");
            return sb.toString();
        }
    }


    private Optional<Template> resolveTemplateReferences(Locale locale, String mapping) {
        
        String[] localizedRefs = buildLocalizedReferences(mapping, locale);
        for (int j = 0; j < localizedRefs.length; j++) {
            String localizedRef = localizedRefs[j];
            Optional<Template> t = this.templateManager.getTemplate(localizedRef);
            if (t.isPresent()) {
                return t;
            }
        }
        return Optional.empty();
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
        
        List<String> references = new ArrayList<>();
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
}
