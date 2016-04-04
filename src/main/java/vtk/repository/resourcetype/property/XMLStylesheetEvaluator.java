/* Copyright (c) 2006, University of Oslo, Norway
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
package vtk.repository.resourcetype.property;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jdom.Document;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;

import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.PropertySet;
import vtk.repository.resourcetype.PropertyEvaluator;
import vtk.xml.StylesheetReferenceResolver;

/**
 * Evaluate XML stylesheet reference. Stylesheet references are
 * obtained through the {@link StylesheetReferenceResolver} API.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>stylesheetReferenceResolvers</code> - an array of
 *   {@link StylesheetReferenceResolver} objects. These resolvers are
 *   queried in succession, and the first one to match decides the
 *   stylesheet reference.
 * </ul>
 */
public class XMLStylesheetEvaluator 
    implements PropertyEvaluator, InitializingBean {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private StylesheetReferenceResolver[] stylesheetReferenceResolvers;

    public void setStylesheetReferenceResolvers(
        StylesheetReferenceResolver[] stylesheetReferenceResolvers) {
        this.stylesheetReferenceResolvers = stylesheetReferenceResolvers;
    }

    public void afterPropertiesSet() {
        if (this.stylesheetReferenceResolvers == null) {
            throw new BeanInitializationException(
                "JavaBean property 'stylesheetReferenceResolvers' not set");
        }
    }
    
    public boolean evaluate(Property property, PropertyEvaluationContext ctx) throws PropertyEvaluationException {
        if (ctx.getContent() == null) {
            return false;
        }
        try {
            Document doc = ctx.getContent().getContentRepresentation(org.jdom.Document.class);
            if (doc == null) {
                if (this.logger.isDebugEnabled()) {
                    this.logger.debug("Content representation '"
                                 + org.jdom.Document.class.getName() + "' not available");
                }
                return false;
            }
            
            String stylesheetReference = resolveTemplateReference(ctx.getNewResource(), doc);
            
            if (stylesheetReference == null) {
                if (this.logger.isDebugEnabled()) {
                    this.logger.debug("Did not find stylesheet identifier for resource '" +
                                 ctx.getNewResource() + "'");
                }
                return false;
            }
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Found stylesheet identifier for resource '" +
                             ctx.getNewResource() + "': '" + stylesheetReference + "'");
            }
            property.setStringValue(stylesheetReference);
            return true;
            

        } catch (Exception e) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Unable to find stylesheet reference for resource "
                             + ctx.getNewResource(), e);
            }
            return false;
        }
    }

    private String resolveTemplateReference(PropertySet resource, Document document) {
        for (int i = 0; i < this.stylesheetReferenceResolvers.length; i++) {
            StylesheetReferenceResolver resolver = this.stylesheetReferenceResolvers[i];
            String reference = resolver.getStylesheetIdentifier(document);
            
            if (reference != null) {
                return reference;
            }
        }
        return null;
    }
}
