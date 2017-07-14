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
package vtk.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.RepositoryException;
import vtk.repository.Resource;
import vtk.repository.ResourceNotFoundException;
import vtk.security.AuthenticationException;

/**
 * Resolves abstract stylesheet identifiers into URIs of the content
 * repository.
 *
 * <p>Configurable properties:
 * <ul>
 *  <li><code>repository</code> - the content {@link Repository}
 *  <li><code>token</code> - token to use for repository retrieval. If
 *      not set, the current user's token will be used instead.
 *  <li><code>pathRegexp</code> - regular expression denoting the
 *      legal values of stylesheet references. If this regexp does not
 *      match the value of the expanded repository URI, the resolver
 *      will act as if the stylesheet was not found. Default value is
 *      <code>.*</code>.
 *  <li><code>retrieveForProcessing</code> - value to set for the
 *      <code>forProcessing</code> flag when calling {@link
 *      Repository#retrieve} and {@link
 *      Repository#getInputStream}. The default value is
 *      <code>true</code>.
 */
public class RepositoryURIResolver extends AbstractPathBasedURIResolver
  implements InitializingBean {

    private Repository repository = null;
    private String token;
    private boolean retrieveForProcessing = true;


    public void setRepository(Repository repository)  {
        this.repository = repository;
    }


    public void setToken(String token) {
        this.token = token;
    }

    
    public void setRetrieveForProcessing(boolean retrieveForProcessing) {
        this.retrieveForProcessing = retrieveForProcessing;
    }
    

    public void afterPropertiesSet() throws Exception {
        if (this.repository == null) {
            throw new BeanInitializationException(
                "Property 'repository' must be set");
        }
    }

    

    @Override
    public Date getLastModifiedInternal(Path path)
        throws Exception {
        try {
            Resource r = this.repository.retrieve(token, path,
                                                  this.retrieveForProcessing);
            return r.getLastModified();
        }
        catch (ResourceNotFoundException e) {
            throw new IOException(
                "Resource '" + path + "' does not exist");
        }
    }

    
    @Override
    protected InputStream getInputStream(Path path) throws Exception {
        try {
            InputStream inputStream = this.repository.getInputStream(
                token, path, this.retrieveForProcessing);
            return inputStream;
        } catch (RepositoryException e) {
            return null;
        } catch (AuthenticationException e) {
            return null;
        }
    }
}
