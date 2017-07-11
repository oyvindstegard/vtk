/* Copyright (c) 2011, University of Oslo, Norway
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.resourcetype.Value;
import vtk.web.RequestContext;
import vtk.web.decorating.DecoratorRequest;
import vtk.web.decorating.DecoratorResponse;

public class ListResourceTitleComponent extends ViewRenderingDecoratorComponent {

    private String multipleResourceRefField;

    @Override
    public void processModel(Map<String, Object> model, DecoratorRequest request, DecoratorResponse response)
            throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext(request.getServletRequest());
        Path uri = requestContext.getResourceURI();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Resource resource = repository.retrieve(token, uri, true);

        Property resourceRefProp = resource.getProperty(Namespace.STRUCTURED_RESOURCE_NAMESPACE,
                getMultipleResourceRefField());

        if (requestContext.isViewUnauthenticated()) { // VTK-2460
            token = null;
        }
        
        List<RelatedDocument> relatedDocuments = new ArrayList<>();
        if (resourceRefProp != null && resourceRefProp.getValues() != null) {
            for (Value x : resourceRefProp.getValues()) {
                try {
                    Path p = Path.fromString(x.getStringValue());
                    Resource currentResource = repository.retrieve(token, p, true);
                    relatedDocuments.add(new RelatedDocument(currentResource.getTitle(), p.toString()));
                } catch (Exception e) {
                    // ignore exceptions
                }
            }
        }
        model.put("relatedDocuments", relatedDocuments);
        model.put("viewName", this.getName());
    }

    public void setMultipleResourceRefField(String multipleResourceRefField) {
        this.multipleResourceRefField = multipleResourceRefField;
    }

    public String getMultipleResourceRefField() {
        return multipleResourceRefField;
    }

    public static class RelatedDocument implements Comparable<RelatedDocument> {
        private String title;
        private String url;

        RelatedDocument(String title, String url) {
            if (title == null)
                throw new IllegalArgumentException("title cannot be null");
            this.title = title;
            this.url = url;
        }

        public String getTitle() {
            return this.title;
        }

        public String getUrl() {
            return this.url;
        }

        public int compareTo(RelatedDocument o) {
            return this.title.compareTo(o.title);
        }
    }
}
