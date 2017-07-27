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
package vtk.web.actions.copymove;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.TypeInfo;
import vtk.web.RequestContext;
import vtk.web.actions.convert.CopyAction;

/**
 * CopyAction that performs an un-publish operation on the 
 * resource after copying.
 */
public class UnpublishAfterCopyAction implements CopyAction {

    @Override
    public void process(HttpServletRequest request, Path originalUri, Path copyUri,
            Map<String, Object> properties) throws Exception {
        RequestContext requestContext = RequestContext.getRequestContext(request);
        Repository repository = requestContext.getRepository();
        String token = requestContext.getSecurityToken();
        repository.copy(token, null, originalUri, copyUri, false, false);
        Resource result = repository.retrieve(token, copyUri, true);

        TypeInfo typeInfo = repository.getTypeInfo(result);
        if (typeInfo.isOfType("managed-json-resource")) {
            result.removeProperty(Namespace.DEFAULT_NAMESPACE, "publish-date");
            repository.store(token, null, result);
        }
    }

}
