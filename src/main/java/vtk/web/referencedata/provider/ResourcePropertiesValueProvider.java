/* Copyright (c) 2004, University of Oslo, Norway
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
package vtk.web.referencedata.provider;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import vtk.repository.Namespace;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.RequestContext;
import vtk.web.referencedata.ReferenceDataProvider;

/**
 * Resource property value reference data provider. Takes a list of
 * resource property namespaces and names, and puts their values in
 * the model under a set of configurable names.
 * 
 * 
 * <p>Configurable properties:
 * <ul>
 *  <li> <code>modelNames</code> - a {@link Map} from {@link
 *      PropertyTypeDefinition#getNamespace() namespaces} to submodel names that are
 *      generated. For every namespace in the <code>properties</code>
 *      configuration property there must exist a mapping in this map,
 *      including the namespace for the standard properties
 *      (<code>null</code> or <code>""</code>). For example, the mapping
 *      <code>(http://foo.bar/baaz, title)</code> will cause all
 *      specified properties of that namespace to be published into
 *      the submodel <code>title</code>.
 *  <li> <code>properties</code> - array of
 *       <code>namespace:name</code> {@link String strings} of the
 *       resource properties whose values will be put in the model. If
 *       a namespace has a value other than <code>null</code> (i.e. at
 *       least one <code>:</code> is present in the property string),
 *       that namespace is used for {@link Resource#getProperty
 *       retrieving} a resource {@link Property}. Otherwise, the name
 *       maps to standard properties in this manner:
 *       <ul>
 *         <li><code>characterEncoding</code> - {@link Resource#getCharacterEncoding}
 *         <li><code>contentLocale</code> - {@link Resource#getContentLocale}
 *         <li><code>contentLastModified</code> - {@link Resource#getContentLastModified}
 *         <li><code>contentLength</code> - {@link Resource#getContentLength}
 *         <li><code>contentModifiedBy</code> - {@link Resource#getContentModifiedBy}
 *         <li><code>contentType</code> - {@link Resource#getContentType}
 *         <li><code>creationTime</code> - {@link Resource#getCreationTime}
 *         <li><code>lastModified</code> - {@link Resource#getLastModified}
 *         <li><code>name</code> - {@link Resource#getName}
 *         <li><code>propertiesLastModified</code> - {@link Resource#getPropertiesLastModified}
 *         <li><code>propertiesModifiedBy</code> - {@link Resource#getPropertiesModifiedBy}
 *         <li><code>uri</code> - {@link Resource#getURI}
 *         <li><code>isCollection</code> - {@link Resource#isCollection}
 *       </ul>
 *       A namespace followed by <code>:*</code> is interpreted as a
 *       shorthand notation for "all properties of the
 *       namespace". When this notation is used, all properties of
 *       that namespace are supplied in the model.
 *  <li><code>localizationKeys</code> - a map from property
 *      <code>namespace:name</code> (or only <code>name</code> in case
 *      of standard properties) strings to localization keys. If such
 *      a mapping exists for a property, instead of providing the
 *      regular value in the model, a localized message with the
 *      specified key and the value as parameter (with the default
 *      being the value itself) is looked up and provided in the
 *      model.
 * </ul>
 * 
 * <p>Model data provided:
 * <ul>
 *   <li>for every property in the <code>properties</code>
 *   configuration variable, the value of that property, under a name
 *   specified by <code>modelNames</code>, or <code>null</code> if the
 *   property does not exist.
 * </ul>
 *
 */
public class ResourcePropertiesValueProvider 
  implements ReferenceDataProvider, InitializingBean {

    private static Logger logger =
        LoggerFactory.getLogger(ResourcePropertiesValueProvider.class);

    private String[] properties = null;

    private String[] namespaces = null;
    private String[] names = null;

    private Map<String, String> localizationKeys = null;
    private Map<String, String> modelNames = null;

    public void setModelNames(Map<String, String> modelNames) {
        this.modelNames = modelNames;
    }
    
    public void setProperties(String[] properties) {
        this.properties = properties;
    }
    
    public void setLocalizationKeys(Map<String, String> localizationKeys) {
        this.localizationKeys = localizationKeys;
    }
    
    public void afterPropertiesSet() {
        if (this.modelNames == null) {
            throw new BeanInitializationException(
                "Bean property 'modelNames' must be set");
        }
        if (this.properties == null) {
            throw new BeanInitializationException(
                "Bean property 'properties' must be set");
        }

        splitProperties();

        for (int i = 0; i < this.namespaces.length; i++) {

            boolean exists;
            
            if (this.namespaces[i] == null) {
                exists = this.modelNames.containsKey(null) || this.modelNames.containsKey("");
            } else {
                exists = this.modelNames.containsKey(this.namespaces[i]);
            }

            if (!exists) {
                throw new BeanInitializationException(
                    "The 'modelNames' bean property does not contain "
                    + "an entry for resource property namespace '"
                    + this.namespaces[i] + "'");
            }
        }
    }
    


    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request)
            throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Resource resource = repository.retrieve(token,
                                                requestContext.getResourceURI(),
                                                true);

        for (int i = 0; i < this.properties.length; i++) {

            String subModelKey = this.modelNames.get(this.namespaces[i]);
            if (subModelKey == null && this.namespaces[i] == null) {
                subModelKey = this.modelNames.get("");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> subModel = (Map<String, Object>) model.get(subModelKey);
            if (subModel == null) {
                subModel = new HashMap<String, Object>();
                model.put(subModelKey, subModel);
            }

            if (this.namespaces[i] != null && "*".equals(this.names[i])) {

                Namespace ns = Namespace.getNamespace(this.namespaces[i]);
                for (Property prop: resource.getProperties(ns)) {
                    PropertyTypeDefinition propDef = prop.getDefinition();
                    subModel.put(propDef.getName(),
                                 maybeLocalizeValue(propDef.getNamespace().getUri(),
                                                    propDef.getName(),
                                                    prop.getStringValue(),
                                                    request));
                }

            } else {

                Object value = null;
                if (this.namespaces[i] == null) {
                    value = getStandardPropertyValue(resource, this.names[i]);
                } else {
                    Namespace ns = Namespace.getNamespace(this.namespaces[i]);
                    Property property = resource.getProperty(ns, this.names[i]);
                    if (property != null) {
                        value = property.getValue();
                    }
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Got resource property: '" + this.namespaces[i]
                                 + ":" + this.names[i] + "' = " + value);
                }
                value = maybeLocalizeValue(this.namespaces[i], this.names[i],
                                           value, request);
                subModel.put(this.names[i], value);
            }
        }
    }
    

    private Object maybeLocalizeValue(String namespace, String name,
                                      Object value, HttpServletRequest request) {
        if (this.localizationKeys == null) {
            return value;
        }

        if (value == null) {
            return value;
        }

        String key = name;
        if (namespace != null) {
            key = namespace + ":" + key;
        }

        // Look up real key from localizationKeys:
        key = this.localizationKeys.get(key);
        if (key == null) {
            return value;
        }

        org.springframework.web.servlet.support.RequestContext springContext =
            new org.springframework.web.servlet.support.RequestContext(request);
                
        value = springContext.getMessage(key, new Object[] {value},
                                         value.toString());
        
        return value;
    }
    

    private Object getStandardPropertyValue(Resource resource,
                                            String propertyName) {

        if ("characterEncoding".equals(propertyName)) {
            return resource.getCharacterEncoding();
            
        } else if ("contentLocale".equals(propertyName)) {
            return resource.getContentLanguage();
            
        } else if ("contentLastModified".equals(propertyName)) {
            return resource.getContentLastModified();

        } else if ("contentLength".equals(propertyName)) {
            return new Long(resource.getContentLength());

        } else if ("contentModifiedBy".equals(propertyName)) {
            return resource.getContentModifiedBy();
            
        } else if ("contentType".equals(propertyName)) {
            return resource.getContentType();
            
        } else if ("creationTime".equals(propertyName)) {
            return resource.getCreationTime();
            
        } else if ("lastModified".equals(propertyName)) {
            return resource.getLastModified();
            
        } else if ("name".equals(propertyName)) {
            return resource.getName();
            
        } else if ("propertiesLastModified".equals(propertyName)) {
            return resource.getPropertiesLastModified();
            
        } else if ("propertiesModifiedBy".equals(propertyName)) {
            return resource.getPropertiesModifiedBy();
            
        } else if ("uri".equals(propertyName)) {
            return resource.getURI();
            
        } else if ("isCollection".equals(propertyName)) {
            return resource.isCollection();

        } else {
            return null;
        }
    }
    

    private void splitProperties() {

        this.namespaces = new String[this.properties.length];
        this.names = new String[this.properties.length];

        
        for (int i = 0; i < this.properties.length; i++) {

            String namespace = null;
            String name = this.properties[i];

            if (this.properties[i].indexOf(":") != -1) {

                namespace = this.properties[i].substring(
                    0, this.properties[i].lastIndexOf(":"));

                name = this.properties[i].substring(
                    this.properties[i].lastIndexOf(":") + 1);
            }

            this.namespaces[i] = namespace;
            this.names[i] = name;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getName());
        sb.append(" [ ");
        sb.append("modelNames = ").append(this.modelNames);
        sb.append(" ]");
        return sb.toString();
    }
}
