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
package org.vortikal.web.actions.convert;

import java.io.InputStream;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.vortikal.repository.Path;
import org.vortikal.repository.Repository;
import org.vortikal.repository.Resource;
import org.vortikal.repository.Repository.Depth;
import org.vortikal.security.SecurityContext;

public class SimpleFilteringCopyAction implements CopyAction, InitializingBean {

    private Repository repository;
    private Filter filter;

    public void process(Path originalUri, Path copyUri) throws Exception{
        String token = SecurityContext.getSecurityContext().getToken();

        this.repository.copy(token, originalUri, copyUri, Depth.ZERO, false, true);

        if (this.filter != null) {
            try {
                Resource resource = this.repository.retrieve(token, copyUri, false);
                InputStream is = this.repository.getInputStream(token, originalUri, false);
                is = this.filter.transform(is, resource);

                this.repository.store(token, resource);
                this.repository.storeContent(token, copyUri, is);
            } catch (Exception e) {
                try {
                    this.repository.delete(token, copyUri);
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
            throw new BeanInitializationException(
                    "Required Java Bean Property 'repository' not set");
    }

}
