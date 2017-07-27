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
package vtk.web.actions.convert;

import java.io.InputStream;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;

import vtk.repository.ContentInputSources;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.web.RequestContext;

public class SimpleFilteringCopyAction implements CopyAction, InitializingBean {

    private Repository repository;
    private Filter filter;

    @Override
    public void process(HttpServletRequest request,
            Path originalUri, Path copyUri, Map<String, Object> properties) throws Exception {
        RequestContext requestContext = RequestContext
                .getRequestContext(request);
        String token = requestContext.getSecurityToken();

        this.repository.copy(token, null, originalUri, copyUri, false, true);

        if (this.filter != null) {
            try {
                Resource resource = this.repository.retrieve(token, copyUri, false);
                InputStream is = this.repository.getInputStream(token, originalUri, false);
                is = this.filter.transform(is, resource);

                this.repository.store(token, null, resource);
                this.repository.storeContent(token, null, copyUri, ContentInputSources.fromStream(is));
            }
            catch (Exception e) {
                try {
                    this.repository.delete(token, null, copyUri, true);
                } catch (Exception ex) {
                    // XXX: should log
                    // Can't do much, hope it's okay
                }
                throw e;
            }
        }
    }

    public void setFilter(Filter filter) {
        this.filter = filter;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public void afterPropertiesSet() throws Exception {
        if (this.repository == null)
            throw new BeanInitializationException("Required Java Bean Property 'repository' not set");
    }

}
