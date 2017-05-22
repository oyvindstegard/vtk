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
package vtk.web.referencedata.provider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.web.RequestContext;
import vtk.web.referencedata.ReferenceDataProvider;
import vtk.web.service.Assertion;
import vtk.web.service.Service;

public class ResourceServiceSwitchURLProvider implements ReferenceDataProvider {

    private Service service;

    private String linkToServiceName;
    private String linkToResourceName;

    @Override
    public void referenceData(Map<String, Object> model, HttpServletRequest request) {
        RequestContext requestContext = RequestContext.getRequestContext();
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        Path resourceURI = requestContext.getResourceURI();

        try {
            Resource resource = repository.retrieve(token, resourceURI, true);

            String link = getService().constructLink(resource.getURI());
            boolean displayResource = true;
            List<Assertion> serviceAssertions = getService().getAssertions();
            for (Assertion assertion : serviceAssertions) {

                if (!assertion.matches(request, resource, requestContext.getPrincipal())) {
                    displayResource = false;
                    break;
                }
            }
            if (displayResource) {
                model.put(linkToResourceName, resource.getURI().toString());
            }
            else {
                model.put(linkToServiceName, link);
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    public void setService(Service service) {
        this.service = service;
    }

    public Service getService() {
        return service;
    }

    public void setLinkToServiceName(String linkToServiceName) {
        this.linkToServiceName = linkToServiceName;
    }

    public void setLinkToResourceName(String linkToResourceName) {
        this.linkToResourceName = linkToResourceName;
    }
}
