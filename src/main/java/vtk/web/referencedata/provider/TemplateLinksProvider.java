/* Copyright (c) 2009, University of Oslo, Norway
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.security.Principal;
import vtk.util.text.SimpleTemplate;
import vtk.web.RequestContext;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.service.Service;
import vtk.web.service.URL;

/**
 * Provides links generated from patterns with placeholders for
 * dynamic values.
 * 
 * Supported fields:
 * <ul>
 *   <li>%{url}     (view service URL for current resource)
 *   <li>%{foo:bar} (property values on current resource).
 * </ul>
 * 
 * Links are provided as a list of beans of type {@link Link}.
 * 
 * Example template value: "http://www.foo.com/share?url=%{url}"
 * 
 */
public class TemplateLinksProvider implements ReferenceDataProvider {

    private static final String URL_ENCODING_CHARSET = "utf-8";

    private int truncateLimit = 250;
    private String truncation = "...";
    private List<TemplateLink> templateLinks;
    private boolean onlyReadAll = false;
    private Service viewService;
    private String modelKey = "links";

    public static final class Link {
        private String name;
        private String url;

        /**
         * @return URL-encoded link
         */
        public String getUrl() {
            return url;
        }

        /**
         * @return link name.
         */
        public String getName() {
            return name;
        }
    }

    private static final class TemplateLink {
        String name;
        SimpleTemplate template;
    }

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) {

        if (this.templateLinks == null) return;

        RequestContext requestContext = RequestContext.getRequestContext(request);
        String token = requestContext.getSecurityToken();
        Repository repository = requestContext.getRepository();

        try {
            final Resource resource = repository.retrieve(token, requestContext.getResourceURI(), true);

            if(resource.isReadRestricted() && onlyReadAll) return;

            final Principal principal = requestContext.getPrincipal();
            final Service service = requestContext.getService();

            List<Link> links = new ArrayList<>();
            for (TemplateLink tl : this.templateLinks) {
                SimpleTemplate template = tl.template;

                if (template != null) {
                    final StringBuilder url = new StringBuilder();
                    template.apply(variable -> {
                        if ("url".equals(variable)) {
                            String s = service.urlConstructor(URL.create(request))
                                    .withResource(resource)
                                    .withPrincipal(principal)
                                    .constructURL()
                                    .toString();
                            try {
                                return URLEncoder.encode(s, URL_ENCODING_CHARSET);
                            }
                            catch (UnsupportedEncodingException e) {
                                return "";
                            }
                        }
                        else {
                            // Assume it's a reference to a resource property
                            String prefix = null;
                            String name = null;
                            int idx = variable.indexOf(":");
                            if (idx != -1) {
                                prefix = variable.substring(0, idx);
                                name = variable.substring(idx + 1);
                            } else {
                                name = variable;
                            }
                            String retVal = propValue(request, resource, prefix, name);
                            if (retVal.length() > truncateLimit) {
                                retVal = retVal.substring(0, truncateLimit);
                                retVal = retVal + truncation;
                            }
                            try {
                                return URLEncoder.encode(retVal, URL_ENCODING_CHARSET);
                            } catch (UnsupportedEncodingException e) {
                                return "";
                            }
                        }
                    }, s -> url.append(s));

                    Link link = new Link();
                    link.name = tl.name;
                    link.url = url.toString();
                    links.add(link);
                }
            }
            model.put(this.modelKey, links);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String propValue(HttpServletRequest request, Resource resource, String prefix, String name) {
        String retVal = "";
        Property prop = resource.getPropertyByPrefix(prefix, name);
        if (prop == null) {
            prop = resource.getPropertyByPrefix("resource", name); // Try structured namespace
        }
        if (prop != null) {
            PropertyTypeDefinition def = prop.getDefinition();
            if (def != null) {
                if (def.getType() == PropertyType.Type.HTML) {
                    retVal = prop.getFormattedValue("flattened", null);
                }
                else {
                    retVal = prop.getFormattedValue();
                    RequestContext requestContext = RequestContext.getRequestContext(request);
                    if (!retVal.isEmpty() && def.getType() == PropertyType.Type.IMAGE_REF) { // Construct absolute URLs
                        try {
                            if (!retVal.startsWith("/")) {
                                Path currentCollection = requestContext.getCurrentCollection();
                                retVal = currentCollection.expand(retVal).toString();
                            }
                            retVal = viewService.urlConstructor(requestContext.getRequestURL())
                                    .withURI(Path.fromString(retVal))
                                    .constructURL()
                                    .toString();
                        }
                        catch (Exception iae) {
                            retVal = "";
                        }
                    }
                }
            }
        }

        return retVal;
    }

    /**
     * Set link templates to be provided in model (link names mapped to
     * link template values).
     */
    @Required
    public void setTemplates(Map<String, String> templates) {
        this.templateLinks = new ArrayList<>();
        for (Map.Entry<String,String> entry: templates.entrySet()) {
            String key = entry.getKey();
            String templateValue = entry.getValue();
            TemplateLink tl = new TemplateLink();
            tl.name = key;
            tl.template = SimpleTemplate.compile(templateValue, "%{", "}");
            this.templateLinks.add(tl);
        }
    }

    /**
     * Set if links should only be displayed if resource can be read by all
     */
    public void setOnlyReadAll(boolean onlyReadAll) {
        this.onlyReadAll = onlyReadAll;
    }

    public void setTruncateLimit(int truncateLimit) {
        this.truncateLimit = truncateLimit;
    }

    public void setTruncation(String truncation) {
        this.truncation = truncation;
    }

    public void setViewService(Service viewService) {
        this.viewService = viewService;
    }

    public void setModelKey(String key) {
        this.modelKey = key;
    }

}
