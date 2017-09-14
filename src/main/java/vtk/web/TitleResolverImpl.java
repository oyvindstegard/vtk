/* Copyright (c) 2013, University of Oslo, Norway
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
package vtk.web;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.resourcetype.ResourceTypeDefinition;
import vtk.repository.resourcetype.Value;
import vtk.util.repository.LocaleHelper;
import vtk.util.text.PathMappingConfig;
import vtk.util.text.PathMappingConfig.Entry;
import vtk.util.text.PathMappingConfig.Qualifier;
import vtk.util.text.SimpleTemplate;

/**
 * Resolve resource web titles based on configuration.
 * 
 * <p>{@literal General form for a config <rule>:}<br>
 * 
 * {@literal <path> <predicates> = <template value> "\n" }
 * <br>
 * where:<br>
 * {@literal <predicates> is on the form "[P1,P2,P3..]" }
 * <br>and {@literal <path>} is some resource path, optionally ending with slash for exact match.
 *
 * <p>One line per &lt;rule&gt;. One config file to rule them all. For more info on
 * syntax, see {@link PathMappingConfig}.
 * 
 * <p>Matching:
 * <ol>
 * <li>For resource with path P, the config entries with path C that is either
 * equal or closest ancestor to P are selected for predicate matching.
 * 
 * <li>If one or more of the selected config entries for path C match on all
 * predicates, then the last one, with order in config file, is
 * selected.
 * 
 * <li>If none of selected entries for path C match, then matching restarts with
 * the parent path of P as input, thus looking for rules higher up in the
 * hierarchy, until finally a rule that matches is found.
 * 
 * <li>If no rules match, or there is no configuration file, then the configured
 * fallback title is used as template.
 * </ol>
 * 
 * <p>Example configuration:
 * <pre>
 * # Some config
 * / = ${title} - ${hostname}
 * 
 * /foo/bar = ${title} - Foobar area - ${hostname}
 * /foo/bar[lang:en] = ${title} - Foobar area in English - ${hostname}
 * /foo/bar[type:structured-event] = ${title} - Happenings under Foobar area - ${hostname}
 * /foo/bar[type:structured-event,lang:en] = ${title} - English happenings under Foobar area - ${hostname}
 * 
 * # Require that property "prop" in namespace "some" exists (has some value) for resource:
 * /a/b[some\:prop:*] = ${title} - a resource that has prop some:prop - ${hostname}
 * 
 * # A rule that matches ONLY for "/exactly", but <em>not</em> any descendants:
 * /exactly/ = The Exactly resource at /exactly, created at ${creationTime}
 * 
 * # A rule that will match <em>only</em> root resource:
 * // = Welcome to ${hostname}
 * 
 * # This will match any resource that inherits some inheritable property from type "some-context-type":
 * /[context:some-context-type] = ${title} - Some Context ${some-ctx: - ${hostname}
 * </pre>
 * 
 * <p>Available matching predicates:
 * <ul>
 *  <li><b>lang</b> - match on locale of resource.<br>
 *   Example: "/[lang:en] = ..."
 *  <li><b>type</b> - match on <em>exact</em> resource type<br>
 *   Example: "/[type:collection] = ...".
 *  <li><b>context</b> - match on resources that inherit properties from the given type<br>
 *   Example: "/[context:foo-context] = ..."
 *  <li><b>NAMESPACE\:NAME</b> - match on value of property NAME in namespace NAMESPACE<br>
 *   Example: "/[visual-profile\:disabled:true] = ...".
 *   Note the required escaping of ":" between namespace and name, since the colon is also used to separate property from
 *   required value.
 * </ul>
 * 
 * <p>Available template variables:
 * <ul>
 *   <li><b>${NAMESPACE:NAME}</b> - Value of resource property <b>NAME</b>, in namespace <b>NAMESPACE</b>.
 *   <li><b>${NAME}</b> - value of resource property <b>NAME</b> in the default namespace.
 *   <li><b>${hostname}</b> - the name of the host
 * </ul>
 * 
 */
public class TitleResolverImpl implements ApplicationListener<ContextRefreshedEvent>, TitleResolver {

    private final Logger logger = LoggerFactory.getLogger(TitleResolverImpl.class);
    private Repository repository;
    private ResourceTypeTree resourceTypeTree;
    private Path configPath;
    private volatile PathMappingConfig<SimpleTemplate> config;
    private SimpleTemplate fallbackTemplate = SimpleTemplate.compile("${title} - ${hostname}");
    
    @Override
    public String resolve(final Resource resource) {
        if (config == null) {
            // Be graceful if config is not loaded
            logger.warn("Configuration not loaded or failed to load, using fallback title for " + resource.getURI());
            return renderTemplate(fallbackTemplate, resource);
        }
        
        Entry<SimpleTemplate> entry = matchConfigForResource(resource);
        if (entry == null) return renderTemplate(fallbackTemplate, resource);
        
        return renderTemplate(entry.value, resource);
        
    }
     
    private String renderTemplate(SimpleTemplate template, final Resource resource) {
        final StringBuilder result = new StringBuilder();
        template.apply(v -> {
            if ("hostname".equals(v)) {
                return repository.getId();
            }
            Property prop = getPropertyByReference(resource, v);
            if (prop == null) {
                return "";
            }
            return prop.getFormattedValue();
        }, s -> result.append(s));

        return result.toString();
    }

    // XXX We're doing exactly the same thing in many places for
    // handling "prefix:name" prop references.. perhaps utility candidate
    private Property getPropertyByReference(Resource resource, String ref) {
        int separatorIdx = ref.indexOf(':');
        if (separatorIdx > 0 && separatorIdx < ref.length()-1) {
            String prefix = ref.substring(0, separatorIdx);
            String suffix = ref.substring(separatorIdx+1);

            Property prop = resource.getPropertyByPrefix(prefix, suffix);
            return prop;
        }
        Property prop = resource.getProperty(Namespace.DEFAULT_NAMESPACE, ref);
        if (prop == null) {
            prop = resource.getProperty(Namespace.STRUCTURED_RESOURCE_NAMESPACE, ref);
        }
        return prop;
    }
    
    private Entry<SimpleTemplate> matchConfigForResource(Resource resource) {
        
        Path path = resource.getURI();
        while (path != null) {
            List<Entry<SimpleTemplate>> entries = this.config.getMatchAncestor(path);
            if (entries == null || entries.isEmpty()) {
                break;
            }
            // Jump up to config entry ancestor path, so we don't do needless matching
            // on the way up:
            path = entries.get(0).path;
            
            List<Entry<SimpleTemplate>> candidates = new ArrayList<>();
            for (Entry<SimpleTemplate> entry: entries) {
                if (entry.exact && !path.equals(resource.getURI())) {
                    // For exact entries, we don't apply URI namespace inheritance of rules.
                    continue;
                }

                boolean match = true;
                for (Qualifier q: entry.qualifiers) {
                    if (!matchQualifier(q, resource)) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    candidates.add(entry);
                }
            }
            
            if (!candidates.isEmpty()) {
                // Sort candidate list [stably], so that any exact entries come last
                Collections.sort(candidates, (Entry o1, Entry o2) -> {
                    if (o1.exact && !o2.exact) return 1;
                    if (o2.exact && !o1.exact) return -1;
                    return 0;
                });
                
                return candidates.get(candidates.size()-1);
            }
            
            path = path.getParent();
        }
        
        return null;
    }
    
    private boolean matchQualifier(Qualifier q, Resource r) {
        if ("type".equals(q.name)) {
            return r.getResourceType().equals(q.value);
        }
        if ("context".equals(q.name)) {
            return matchContext(q.value, r);
        }
        if ("lang".equals(q.name)) {
            return matchLanguage(q.value, r);
        }
        
        return matchPropertyValue(q.name, q.value, r);
    }
    
    private boolean matchContext(String contextType, Resource r) {
        ResourceTypeDefinition typeDef = null;

        try {
            typeDef = resourceTypeTree.getResourceTypeDefinitionByName(contextType);
        } catch (IllegalArgumentException ie) {}

        if (typeDef == null) return false;
        
        for (PropertyTypeDefinition def: typeDef.getPropertyTypeDefinitions()) {
            if (def.isInheritable()) {
                if (r.getProperty(def) != null) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private boolean matchLanguage(String lang, Resource r) {
        Locale contentLocale = r.getContentLocale();
        if (contentLocale == null) {
            return false;
        }
        
        String resourceLang = LocaleHelper.getPreferredLang(contentLocale);
        return resourceLang.equalsIgnoreCase(lang);
    }
    
    private boolean matchPropertyValue(String propRef, String value, Resource r) {
        Property prop = getPropertyByReference(r, propRef);
        // Asterisk as value means "exists" testing on property
        if ("*".equals(value)) {
            return prop != null;
        }
        if (prop == null) {
            return false;
        }
        
        PropertyTypeDefinition def = prop.getDefinition();
        Value[] values;
        if (def.isMultiple()) {
           values = prop.getValues();
        } else {
            values = new Value[] {prop.getValue()};
        }

        for (Value v: values) {
            if (value.equals(v.getNativeStringRepresentation())) {
                return true;
            }
        }

        return false;
    }
    
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        loadConfig();
    }
    
    /**
     * (Re)load configuration data from resource at config path. Must be called
     * at least once before using instance.
     * 
     * @see #setConfigPath(java.lang.String) 
     */
    public void loadConfig() {
        try {
            InputStream inputStream = this.repository.getInputStream(null, this.configPath, true);
            this.config = new PathMappingConfig<>(inputStream, t -> SimpleTemplate.compile(t, SimpleTemplate.ESC_UNESCAPE));
        } catch(Throwable t) {
            logger.warn("Unable to load title configuration file: " 
                    + this.configPath + ": " + t.getMessage(), t);
        }
    }

    /**
     * Set fallback template to use when no configuration rules match a resource.
     * Default template value is "${title} - ${hostname}".
     * 
     * @param template The template to use as fallback, as a string.
     */
    public void setFallbackTemplate(String template) {
        this.fallbackTemplate = SimpleTemplate.compile(template);
    }
    
    /**
     * Set repository path to configuration resource.
     * @param configPath the path to the configuration resource.
     */
    @Required
    public void setConfigPath(String configPath) {
        this.configPath = Path.fromString(configPath);
    }

    @Required
    public void setResourceTypeTree(ResourceTypeTree rt) {
        this.resourceTypeTree = rt;
    }
    
    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }
}
