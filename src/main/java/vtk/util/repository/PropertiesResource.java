/* Copyright (c) 2005, 2006, 2007, University of Oslo, Norway
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
package vtk.util.repository;

import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.event.ContextRefreshedEvent;
import vtk.repository.AuthorizationException;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.ResourceNotFoundException;


/**
 * A Properties that reads its elements from a repository resource in
 * {@link Properties} format. Supports (re)loading its elements via
 * the {@link #load} method.
 *
 * <p>Configurable JavaBean properties:
 * <ul>
 *   <li><code>repository</code> - the {@link Repository content repository} 
 *   <li><code>uri</code> - the full path of the properties
 *   resource. If this file does not exist, the map will be initially
 *   empty. (See also <code>demandResourceAvailability</code>)
 *   <li><code>token</code> - the token to use for reading the
 *   properties resource.
 *   <li><code>demandResourceAvailability</code> - whether to require
 *   that the properties resource is available. Default is
 *   <code>false</code>.
 *   <li><code>lazyInit</code> - whether to attempt loading the
 *   resource when the {@link #afterPropertiesSet} method is called,
 *   or to wait for a {@link ContextRefreshedEvent}. Default is
 *   <code>false</code> (attempt to load the resource immediately).
 * </ul>
 */
public final class PropertiesResource extends Properties implements InitializingBean {
    
    private static final long serialVersionUID = 8393113714334599560L;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private Repository repository;
    private Path uri;
    private String token;
    private boolean demandResourceAvailability = false;
    private boolean lazyInit = false;
    
    public PropertiesResource() {
    }
    
    public PropertiesResource(Properties defaults) {
        super(defaults);
    }
    
    public PropertiesResource(Repository repository, Path uri, String token,
                              boolean demandResourceAvailability, boolean lazyInit) 
        throws Exception {
        
        this.repository = repository;
        this.uri = uri;
        this.token = token;
        this.demandResourceAvailability = demandResourceAvailability;
        this.lazyInit = lazyInit;
        afterPropertiesSet();
    }

    @Required
    public void setRepository(Repository repository) {
        this.repository = repository;
    }


    @Required
    public void setUri(String uri) {
        this.uri = Path.fromString(uri);
    }


    public void setToken(String token) {
        this.token = token;
    }
    

    public void setDemandResourceAvailability(boolean demandResourceAvailability) {
        this.demandResourceAvailability = demandResourceAvailability;
    }


    public void setLazyInit(boolean lazyInit) {
        this.lazyInit = lazyInit;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!this.lazyInit) {
            this.load();
        }
    }

    public void load() throws Exception {
        if (this.repository == null || this.uri == null) {
            throw new IllegalStateException(
                "JavaBean properties 'repository' and 'uri' must be specified");
        }
        super.clear();
        this.logger.info("Loading properties resource at URI " + this.uri);
        if (demandResourceAvailability) {
            doLoad(token, uri);
        } else {
            try {
                doLoad(token, uri);
            } catch (ResourceNotFoundException rnf) {
                this.logger.warn("Unable to load properties from uri '"
                        + uri + "', repository '" + repository
                        + "', token '" + token + "' (resource not found)");
            
            } catch (AuthorizationException ae) {
                this.logger.warn("Unable to load properties from uri '"
                        + uri + "', repository '" + repository
                        + "', token '" + token + "' (authorization exception: "
                        + ae.getMessage()+ ")");
            } catch (Exception e) {
                this.logger.warn("Unable to load properties from uri '"
                            + uri + "', repository '" + repository
                            + "', token '" + token + "' (" + e.getMessage() + ")");
            }
        }
    }
    
    private void doLoad(String token, Path uri) throws Exception {
        InputStream inputStream = this.repository.getInputStream(token, uri, false);
        super.load(inputStream); 
        inputStream.close();
    }

}
