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
package org.vortikal.webdav;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.servlet.View;
import org.vortikal.repository.Lock;
import org.vortikal.repository.LockType;
import org.vortikal.repository.Property;
import org.vortikal.repository.Resource;
import org.vortikal.repository.resourcetype.PropertyType;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.security.Principal;
import org.vortikal.security.SecurityContext;
import org.vortikal.util.repository.LocaleHelper;
import org.vortikal.util.web.HttpUtil;
import org.vortikal.web.InvalidModelException;
import org.vortikal.web.service.Service;


/**
 * View for PROPFIND requests.
 */
public class PropfindView implements View, InitializingBean {

    private static Log logger = LogFactory.getLog(PropfindView.class);

    private Service webdavService = null;


    /**
     * Sets the WebDAV service. This service is needed for URL
     * construction (WebDAV PROPFIND browsing, downloading, etc.).
     *
     * @param webdavService a <code>Service</code> value
     */
    public void setWebdavService(Service webdavService) {
        this.webdavService = webdavService;
    }
    

    public void afterPropertiesSet() {
        if (this.webdavService == null) {
            throw new BeanInitializationException(
                "Property 'webdavService' not set. Must be set to a service object " +
                "in order to construct URLs to WebDAV resources.");
        }
    }



    /**
     *  Builds a DAV 'multistatus' XML element.
     *
     * @param model a <code>Map</code> value
     * @param request a <code>HttpServletRequest</code> value
     * @param response a <code>HttpServletResponse</code> value
     * @exception Exception if an error occurs
     */
    public void render(Map model, HttpServletRequest request,
                       HttpServletResponse response) throws Exception {

        List resources = (List) model.get(WebdavConstants.WEBDAVMODEL_REQUESTED_RESOURCES);
        if (resources == null) {
            throw new InvalidModelException(
                "Missing resource list in model " +
                "(expected a List of Resource objects having key" +
                " `" + WebdavConstants.WEBDAVMODEL_REQUESTED_RESOURCES + "')");
        }

        List properties = (List) model.get(WebdavConstants.WEBDAVMODEL_REQUESTED_PROPERTIES);
        if (logger.isDebugEnabled()) {
            logger.debug("Requested properties: " + properties);
        }

        Boolean appendValues = (Boolean) model.get(
            WebdavConstants.WEBDAVMODEL_REQUESTED_PROPERTIES_APPEND_VALUES);
        

        Element e = buildMultistatusElement(resources, properties,
                                            appendValues.booleanValue());

        Document doc = new Document(e);

        Format format = Format.getPrettyFormat();
        format.setEncoding("utf-8");
        //format.setLineSeparator("\r\n");
        //format.setIndent("");

        XMLOutputter xmlOutputter = new XMLOutputter(format);
        String xml = xmlOutputter.outputString(doc);
        byte[] buffer = null;
        try {
            buffer = xml.getBytes("utf-8");
        } catch (UnsupportedEncodingException ex) {
            logger.warn("Warning: UTF-8 encoding not supported", ex);
            throw new RuntimeException("UTF-8 encoding not supported");
        }
        response.setHeader("Content-Type", "text/xml;charset=utf-8");
        response.setIntHeader("Content-Length", buffer.length);
        response.setStatus(HttpUtil.SC_MULTI_STATUS,
                           WebdavUtil.getStatusMessage(
                               HttpUtil.SC_MULTI_STATUS));
        OutputStream out = null;
        try {
            out = response.getOutputStream();
            out.write(buffer, 0, buffer.length);

            out.flush();
            out.close();

        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }
    }
    


    private Element buildMultistatusElement(List resourceList, List requestedProps,
                                            boolean appendPropertyValues) throws Exception {

        Element multiStatus = new Element("multistatus", WebdavConstants.DAV_NAMESPACE);

        /* Iterate trough all resources, build a 'response' element
         * for each one, and add it to the multistatus element. */

        for (Iterator iter = resourceList.iterator(); iter.hasNext();) {
            Resource currentResource = (Resource) iter.next();
        
            if (!currentResource.isCollection()
                && currentResource.getLock() != null
                && currentResource.getContentLength() == 0) {

                /* resource is lock-null (avoid listing), unless
                 * client is requesting the lockdiscovery property: */

                if (requestedProps.contains(
                        new Element("lockdiscovery", WebdavConstants.DAV_NAMESPACE))) {
                    Element responseElement = buildResponseElement(
                        currentResource, requestedProps, 
                        appendPropertyValues);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Lock-Null resource " + currentResource.getURI());
                    }
                    multiStatus.addContent(responseElement);
                }

            } else {

                Element responseElement = buildResponseElement(
                    currentResource, requestedProps, 
                    appendPropertyValues);
            
                multiStatus.addContent(responseElement);
            }
        }
        
        return multiStatus;
    }
   



    /**
     * Builds a DAV 'response' XML element from data in a
     * <code>Resource</code>.
     *
     * @param resource the <code>Resource</code> that is being queried
     * @param requestedProps a list of DAV property names (must be
     * given as a list of <code>org.jdom.Element</code> objects) for
     * which to find values
     * @param appendPropertyValues determines whether property values should
     * be appended to the JDOM elements or not (this is not always
     * requested, i.e. when a client requests only "propnames").
     * @return an <code>org.jdom.Element</code> representing the DAV
     * response element
     * @deprecated Will be moved to a View class
     */
    private Element buildResponseElement(Resource resource,
                                         List requestedProps,
                                         boolean appendPropertyValues) throws Exception {

        Principal p = SecurityContext.getSecurityContext().getPrincipal();

        Element responseElement = new Element("response", WebdavConstants.DAV_NAMESPACE);
        String href = webdavService.constructLink(resource, p);

        responseElement.addContent(
                new Element("href", WebdavConstants.DAV_NAMESPACE).addContent(href));
        Element foundProperties = new Element("prop", WebdavConstants.DAV_NAMESPACE);
        Element unknownProperties = new Element("prop", WebdavConstants.DAV_NAMESPACE);

        for (Iterator propIter = requestedProps.iterator(); 
             propIter.hasNext();) {

            Element prop = (Element) propIter.next();
            Element foundProp = buildPropertyElement(resource, prop, appendPropertyValues);

            if (foundProp != null) {

                foundProperties.addContent(foundProp);

            } else {

                unknownProperties.addContent(
                    new Element(prop.getName(), prop.getNamespace().getPrefix(),
                                prop.getNamespace().getURI()));
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Found properties: " + foundProperties.getChildren()
                         + ", unknown properties: " + unknownProperties.getChildren());
        }


        if (foundProperties.getChildren().size() > 0) {

            Element propStatElement = new Element("propstat", WebdavConstants.DAV_NAMESPACE);
            propStatElement.addContent(foundProperties);
            Element status = new Element("status", WebdavConstants.DAV_NAMESPACE);
            status.addContent(WebdavUtil.getStatusMessage(
                                  HttpServletResponse.SC_OK));
            propStatElement.addContent(status);
            responseElement.addContent(propStatElement);
        }
        

        if (unknownProperties.getChildren().size() > 0) {

            Element status = new Element("status", WebdavConstants.DAV_NAMESPACE);
            Element propStatUnknown = new Element("propstat", WebdavConstants.DAV_NAMESPACE);
            propStatUnknown.addContent(unknownProperties);
            propStatUnknown.addContent(status);
            status.addContent(WebdavUtil.getStatusMessage(
                                  HttpServletResponse.SC_NOT_FOUND));
            responseElement.addContent(propStatUnknown);
        }
        
        return responseElement;
    }

   

    /**
     * Translates DAV property names to appropriate method calls on a
     * <code>Resource</code> object and returns JDOM elements
     * containing the name and possibly also the value.
     *
     * @param resource the <code>Resource</code> object to query
     * information about
     * @param propElement DAV property name
     * @param appendValue determines whether the value should be added
     * to the JDOM element
     * @return a JDOM element containing the property and possibly its
     * value, or <code>null</code> if not found.
     */
    protected Element buildPropertyElement(Resource resource,
                                           Element propElement, boolean appendValue)
        throws Exception {
        
        String property = propElement.getName();
        Namespace namespace = propElement.getNamespace();

        Element element = new Element(property, propElement.getNamespace());
        if (!appendValue) {
            return element;
        }
      
        if (namespace.equals(WebdavConstants.DAV_NAMESPACE)) {

            if (property.equals("creationdate")) {
                element.addContent(formatCreationTime(
                                       resource.getCreationTime()));
            
            } else if (property.equals("displayname")) {
                String name = resource.getDisplayName();
                if (name == null || name.equals("")) return null;
                element.addContent(name);

            } else if (property.equals("getcontentlanguage")) {
                Locale locale = LocaleHelper.getLocale(resource.getContentLanguage());
                if (locale == null) return null;
                element.addContent(locale.getLanguage());

            } else if (property.equals("getcontentlength")) {
                if (resource.isCollection()) {
                    element.addContent("0");
                } else {
                    element.addContent(String.valueOf(
                                           resource.getContentLength()));
                }

            } else if (property.equals("getcontenttype")) {
                String type = resource.getContentType();
                if (type == null || type.equals("")) return null;
                
                if (resource.isCollection()) {

                    return null;
                    //element.addContent("httpd/unix-directory");

                }   
               
                element.addContent(type);
               
            } else if (property.equals("getetag")) {
                if (resource.getSerial() == null) {
                    return null;
                }
                element.addContent(resource.getSerial());

            } else if (property.equals("getlastmodified")) {
                element.addContent(HttpUtil.getHttpDateString(resource.getLastModified()));

            } else if (property.equals("lockdiscovery")) {
                element = buildLockDiscoveryElement(resource);

            } else if (property.equals("resourcetype")) {
                if (resource.isCollection()) {
                    element.addContent(new Element("collection",
                                                   WebdavConstants.DAV_NAMESPACE));
                }
            } else if (property.equals("source")) {
                //element.addContent(buildSourceElement(resource));
                return null;            

            } else if (property.equals("supportedlock")) {
                element = buildSupportedLockElement(resource);

            } else if (property.equals("supported-privilege-set")) {
                element = buildSupportedPrivilegeSetElement(resource);

            } else if (property.equals("current-user-privilege-set")) {
                element = buildCurrentUserPrivilegeSetElement(resource);
            }


        } else {

            org.vortikal.repository.Namespace ns = org.vortikal.repository.Namespace.getNamespace(namespace.getURI());
            Property customProperty = resource.getProperty(ns, property);
            if (customProperty == null) {
                return null;
            }
            
            PropertyTypeDefinition def = customProperty.getDefinition();
            if (def != null && def.isMultiple()) {
                element = buildMultiValueCustomPropertyElement(customProperty);
            } else {
                element = buildCustomPropertyElement(customProperty);
            }

        }

        return element;
    }
   


    /**
     * Builds a WebDAV "lockdiscovery" JDOM element for a resource.
     *
     * @param resource the resource to find lock information about
     * @return a JDOM lockdiscovery element
     */
    private Element buildLockDiscoveryElement(Resource resource) {
        Element lockDiscovery = new Element("lockdiscovery",
                WebdavConstants.DAV_NAMESPACE);

        Lock lock = resource.getLock();
        
        if (lock == null) {
            // No lock on resource, return empty 'lockdiscovery' element.
            return lockDiscovery;
        }
        
        Element activeLock = new Element("activelock",
                WebdavConstants.DAV_NAMESPACE);

        String type = "";
        String scope = "";
        if (lock.getLockType().equals(LockType.LOCKTYPE_EXCLUSIVE_WRITE)) {
            scope = "exclusive";
            type = "write";
        }

        activeLock.addContent(new Element("locktype",
                WebdavConstants.DAV_NAMESPACE).addContent(new Element(type,
                WebdavConstants.DAV_NAMESPACE)));

        activeLock.addContent(new Element("lockscope",
                WebdavConstants.DAV_NAMESPACE).addContent(new Element(scope,
                WebdavConstants.DAV_NAMESPACE)));

        activeLock.addContent(new Element("depth",
                WebdavConstants.DAV_NAMESPACE).addContent(lock.getDepth()));

        activeLock.addContent(LockView.buildLockOwnerElement(lock.getOwnerInfo()));

        long timeout = lock.getTimeout().getTime() - System.currentTimeMillis();

        if (timeout < 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("Lock's timeout was: " + lock.getTimeout()
                        + ", (has already timed out) ");
            }
            return null;
        }

        String timeoutStr = "Second-" + (timeout / 1000);

        activeLock.addContent(new Element("timeout",
                WebdavConstants.DAV_NAMESPACE).addContent(timeoutStr));

        activeLock.addContent(new Element("locktoken",
                        WebdavConstants.DAV_NAMESPACE).addContent(new Element(
                        "href", WebdavConstants.DAV_NAMESPACE).addContent(lock.getLockToken())));

        lockDiscovery.addContent(activeLock);

        return lockDiscovery;
    }
   




    /**
     * Builds a WebDAV "supportedlock" JDOM element for a resource.
     * 
     * @param resource
     *            the resource in question
     * @return a JDOM supportedlock element
     */
    private Element buildSupportedLockElement(Resource resource) {
        Element supportedLock = new Element("supportedlock", WebdavConstants.DAV_NAMESPACE);
        Element lockEntry = new Element("lockentry", WebdavConstants.DAV_NAMESPACE);
        lockEntry.addContent(
                new Element("lockscope", WebdavConstants.DAV_NAMESPACE).addContent(
                        new Element("exclusive", WebdavConstants.DAV_NAMESPACE)));
        lockEntry.addContent(
                new Element("locktype", WebdavConstants.DAV_NAMESPACE).addContent(
                        new Element("write", WebdavConstants.DAV_NAMESPACE)));

        supportedLock.addContent(lockEntry);

        return supportedLock;
    }


    /**
     * Builds a JDOM element for a custom (non "DAV:" namespace)
     * resource property.
     */
    private Element buildCustomPropertyElement(Property property) {

        /* FIXME: This is not a particurlarly nice/efficient way of
         * building the element. Assume first that the property is an
         * XML fragment, and try to build a document from it. If that
         * fails, we assume it is a 'name = value' style property, and
         * build a simple JDOM element from it. */

        String value = property.getValue().getNativeStringRepresentation();

        /* If the value does not contain both "<" and ">" we know for
         * sure that it is not an XML fragment: */

        if (value.indexOf("<") >= 0 && value.indexOf(">") >= 0) {
            
            try {
        
                Namespace customNamespace =
                    Namespace.getNamespace(property.getNamespace().getUri());

                String xml = "<" + property.getName() + " xmlns=\"" +
                    customNamespace.getURI() + "\">" + value + "</" +
                    property.getName() + ">";

                Document doc = (new SAXBuilder()).build(
                    new StringReader(xml));

                if (!doc.getRootElement().getNamespace().equals(customNamespace)) {
                    doc.getRootElement().setNamespace(customNamespace);
                }
                Element rootElement = doc.getRootElement();
                rootElement.detach();
                return rootElement;
            
            } catch (IOException e) {

                /* Ignore this, build a simple JDOM element instead. */

            } catch (JDOMException e) {

                /* Ignore this, build a simple JDOM element instead. */
            }
        }
        
        Namespace customNamespace = 
            Namespace.getNamespace(property.getNamespace().getUri());
        Element propElement = new Element(property.getName(),
                                          customNamespace);
        
        // Format dates according to HTTP spec, 
        // use value's native string representation for other types.
        if (property.getValue().getType() == PropertyType.TYPE_DATE) {
            propElement.setText(WebdavUtil.formatPropertyDateValue(property.getDateValue()));
        } else {
            propElement.setText(property.getValue().getNativeStringRepresentation());    
        }
        
        return propElement;

    }
   
    /**
     * Make a simple XML-list structure out of a multi-valued property.
     * @param property
     * @return
     */
    private Element buildMultiValueCustomPropertyElement(Property property) {
        Value[] values = property.getValues();

        Namespace customNamespace = 
            Namespace.getNamespace(property.getNamespace().getUri());
        Element propElement = new Element(property.getName(),
                                          customNamespace);
        
        Element valuesElement = new Element("values", WebdavConstants.VORTIKAL_PROPERTYVALUES_XML_NAMESPACE);
        
        for (int i=0; i<values.length; i++) {
            Element valueElement = 
                new Element("value", WebdavConstants.VORTIKAL_PROPERTYVALUES_XML_NAMESPACE);

            // Format dates according to HTTP spec, 
            // use value's native string representation for other types.
            if (values[i].getType() == PropertyType.TYPE_DATE) {
                valueElement.setText(WebdavUtil.formatPropertyDateValue(values[i].getDateValue()));
            } else {
                valueElement.setText(values[i].getNativeStringRepresentation());
            }
            
            valuesElement.addContent(valueElement);
        }
        
        propElement.addContent(valuesElement);
        
        return propElement;
    }


    private String formatCreationTime(Date date) {
        /* example: 1970-01-01T12:00:00Z */

        SimpleDateFormat formatter =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return formatter.format(date);
    }

    /* EXPERIMENTAL WebDAV ACL STUFF: */

    protected Element buildSupportedPrivilegeSetElement(Resource resource) {

        Element supportedPrivilege =
            new Element("supported-privilege", WebdavConstants.DAV_NAMESPACE);

        supportedPrivilege.addContent(
            new Element("privilege", WebdavConstants.DAV_NAMESPACE).addContent(
                new Element("all", WebdavConstants.DAV_NAMESPACE)));
        supportedPrivilege.addContent(
            new Element("description", WebdavConstants.DAV_NAMESPACE).addContent(
                "Any operation"));


        Element sub = new Element("supported-privilege", WebdavConstants.DAV_NAMESPACE);
        sub.addContent(
            new Element("privilege", WebdavConstants.DAV_NAMESPACE).addContent(
                new Element("read", WebdavConstants.DAV_NAMESPACE)));
        sub.addContent(new Element("description", WebdavConstants.DAV_NAMESPACE).addContent(
                           "Read any object"));
        
        supportedPrivilege.addContent(sub);


        sub = new Element("supported-privilege", WebdavConstants.DAV_NAMESPACE);
        sub.addContent(
            new Element("privilege", WebdavConstants.DAV_NAMESPACE).addContent(
                new Element("write", WebdavConstants.DAV_NAMESPACE)));
        sub.addContent(
            new Element("description", WebdavConstants.DAV_NAMESPACE).addContent(
                           "Write any object"));
        
        supportedPrivilege.addContent(sub);
        
        Element supportedPrivilegeSet =
            new Element("supported-privilege-set", WebdavConstants.DAV_NAMESPACE);
        supportedPrivilegeSet.addContent(supportedPrivilege);

        return supportedPrivilegeSet;
    }
    


    protected Element buildCurrentUserPrivilegeSetElement(Resource resource) {
        
        Element e = new Element("current-user-privilege-set", WebdavConstants.DAV_NAMESPACE);
        e.addContent(new Element("privilege", WebdavConstants.DAV_NAMESPACE).addContent(
                         new Element("read", WebdavConstants.DAV_NAMESPACE)));
        e.addContent(new Element("privilege", WebdavConstants.DAV_NAMESPACE).addContent(
                         new Element("write", WebdavConstants.DAV_NAMESPACE)));
        e.addContent(new Element("privilege", WebdavConstants.DAV_NAMESPACE).addContent(
                         new Element("all", WebdavConstants.DAV_NAMESPACE)));
        return e;
    }

}
