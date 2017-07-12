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
package vtk.web.decorating.components;

import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;

public class ResourcePropertiesDecoratorComponent extends AbstractDecoratorComponent {

    private static final String PARAMETER_ID = "id";
    private static final String PARAMETER_ID_DESC = 
        "Identifies the property to report. One of 'uri', 'name', 'type' or '<prefix>:<name>' identifying a property.";

    private static final String PARAMETER_URI_LEVEL = "uri-level";
    private static final String PARAMETER_URI_LEVEL_DESC = "Report property for the resource on this level of the current resource's uri." +
    		"Root (\"/\") has level 0. If the current resource is on a higher level, nothing is reported.";

    private static final String PARAMETER_URI = "uri";
    private static final String PARAMETER_URI_DESC = "Report property for resource specified by this (absolute or relative) uri.";

    
    private static final String DESCRIPTION = "Report a property on the current resource, as a formatted and localized string";
    private static final String DESCRIPTION_RELATIVE = "Report a property on a resource, as specified by either uri or uri-level. " +
    		"The property is formatted and localized.";
    
    private static final String PARAMETER_FORMAT = "format";
    private static final String PARAMETER_FORMAT_DESC = "Optional format specification";

    private static final String URL_IDENTIFIER = "url";
    private static final String NAME_IDENTIFIER = "name";
    private static final String TYPE_IDENTIFIER = "type";
    private static final String URI_IDENTIFIER = "uri";

    private boolean forProcessing = true;

    private ResourceTypeTree resourceTypeTree;
    private boolean relative = false;

    
    
    public void render(DecoratorRequest request, DecoratorResponse response)
            throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext(request.getServletRequest());
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path uri = requestContext.getResourceURI();
        
        String format = request.getStringParameter(PARAMETER_FORMAT);
       
        if (this.relative) {
            String uriString = request.getStringParameter(PARAMETER_URI);
            String uriLevelString = request.getStringParameter(PARAMETER_URI_LEVEL);
            
            
            if (uriString == null && uriLevelString == null) {
                throw new IllegalArgumentException(PARAMETER_URI + " or " + PARAMETER_URI_LEVEL + " must be specified");
            }

            if (uriString != null && uriString.trim().equals("") && uriLevelString != null && uriLevelString.trim().equals("")) {
                throw new IllegalArgumentException("Both " + PARAMETER_URI + " and " + PARAMETER_URI_LEVEL + " cannot be specified");
            }
            
            if (uriLevelString != null) {
                try {
                    uri = getParentAtLevel(uri, uriLevelString);
                } catch (NumberFormatException e) {
                    throw new NumberFormatException("uri-level must be a positive integer");
                } catch (IllegalArgumentException e) {
                    return;
                }
            } else {
                if (uriString != null && uriString.startsWith("/")) {
                    uri = Path.fromString(uriString);                    
                } else {
                    uri = RequestContext.getRequestContext(request.getServletRequest()).getCurrentCollection();
                    uri = uri.expand(uriString);
                    if (uri == null) {
                        throw new IllegalArgumentException("Unable to expand URI: " + uriString);
                    }
                }
            }
        }
        
        Resource resource = repository.retrieve(token, uri, this.forProcessing);

        String id = request.getStringParameter(PARAMETER_ID);
        String result = null;

        if (id == null || id.trim().equals("")) {
            return;
        }
 
        if (URI_IDENTIFIER.equals(id)) {
            result = uri.toString();
        } else if (NAME_IDENTIFIER.equals(id)) {
            result = resource.getName();
        } else if (TYPE_IDENTIFIER.equals(id)) {
            result = resource.getResourceType();
        } else if (URL_IDENTIFIER.equals(id)) {
            return;
        } else {
            String prefix = null;
            String name = null;

            int i = id.indexOf(":");
            if (i < 0) {
                name = id;
            } else if (i == 0 || i == id.length() - 1) {
                // XXX: throw something
                return;
            } else {
                prefix = id.substring(0, i);
                name = id.substring(i + 1);
            }
            PropertyTypeDefinition def = this.resourceTypeTree.getPropertyDefinitionByPrefix(prefix, name);

            if (def == null) {
                return;
            }

            Property prop = resource.getProperty(def);

            if (prop == null) {
                return;
            }

            result = prop.getFormattedValue(format , request.getLocale());
        }

        Writer writer = response.getWriter();
        try {
            writer.write(result);
        } finally {
            writer.close();
        }
    }

    // XXX: should be renamed to "getAncestorAtLevel"
    Path getParentAtLevel(Path uri, String uriLevelString) 
        throws NumberFormatException, IllegalArgumentException {
        
        int uriLevel = Integer.parseInt(uriLevelString);
        
        if (uriLevel < 0) {
            throw new NumberFormatException("uri-level must be a positive integer");
        } 
        return uri.getPath(uriLevel);
    }

    public void setForProcessing(boolean forProcessing) {
        this.forProcessing = forProcessing;
    }

    @Required public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

    @Override
    protected String getDescriptionInternal() {
        if (this.relative)
            return DESCRIPTION_RELATIVE;
        
        return DESCRIPTION;
    }

    @Override
    protected Map<String, String> getParameterDescriptionsInternal() {
        Map<String, String> map = new HashMap<>();
        map.put(PARAMETER_ID, PARAMETER_ID_DESC);
        if (this.relative) {
            map.put(PARAMETER_URI, PARAMETER_URI_DESC);
            map.put(PARAMETER_URI_LEVEL, PARAMETER_URI_LEVEL_DESC);
        }
        map.put(PARAMETER_FORMAT, PARAMETER_FORMAT_DESC);
        return map;
    }

    public void setUriLevelEnabled(boolean uriLevelEnabled) {
        this.relative = uriLevelEnabled;
    }

    public void setRelative(boolean relative) {
        this.relative = relative;
    }

}
