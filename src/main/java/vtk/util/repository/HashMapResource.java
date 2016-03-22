/* Copyright (c) 2005, 2008 University of Oslo, Norway
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import vtk.repository.Path;
import vtk.repository.Repository;

/**
 * A HashMap that reads its elements from a repository resource in
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
 *   <li><code>storeValuesAsLists</code> - if this boolean flag is
 *   <code>true</code>, the map values are treated as lists, assumed
 *   to be in a comma separated format. Default is <code>false</code>.
 * </ul>
 */
@SuppressWarnings("rawtypes")
public class HashMapResource extends HashMap implements InitializingBean,
                                                        ApplicationListener {

    private static final long serialVersionUID = 3588458569422752713L;

    private static Logger logger = LoggerFactory.getLogger(HashMapResource.class);

    private Repository repository;
    private Path uri;
    private String token;
    private boolean demandResourceAvailability = false;
    private boolean lazyInit = false;
    private boolean storeValuesAsLists = false;
    

    public HashMapResource() {
        super();
    }
    
    public HashMapResource(Repository repository, Path uri, String token,
                       boolean demandResourceAvailability, boolean lazyInit) {
        super();
        this.repository = repository;
        this.uri = uri;
        this.token = token;
        this.demandResourceAvailability = demandResourceAvailability;
        this.lazyInit = lazyInit;
    }
    

    public void setRepository(Repository repository) {
        this.repository = repository;
    }


    public void setUri(Path uri) {
        this.uri = uri;
    }


    public void setToken(String token) {
        this.token = token;
    }
    

    public void setDemandResourceAvailability(boolean demandResourceAvailability) {
        this.demandResourceAvailability = demandResourceAvailability;
    }


    public void setStoreValuesAsLists(boolean storeValuesAsLists) {
        this.storeValuesAsLists = storeValuesAsLists;
    }
    

    public void setLazyInit(boolean lazyInit) {
        this.lazyInit = lazyInit;
    }
    

    public void onApplicationEvent(ApplicationEvent event) {
        if (!this.lazyInit) {
            return;
        }

        if (event instanceof ContextRefreshedEvent) {
            try {
                load();
            }
            catch (Exception e) {
                logger.warn("Load failed", e);
            }
        }
    }
    

    public void afterPropertiesSet() throws Exception {
        if (this.repository == null) {
            throw new BeanInitializationException(
                "JavaBean property 'repository' not specified");
        }
        if (this.uri == null) {
            throw new BeanInitializationException(
                "JavaBean property 'uri' not specified");
        }
        if (!this.lazyInit) {
            load();
        }
    }

    public void load(Repository repository, Path uri, String token) throws Exception {
        this.load(repository, uri, token, false);
    }
    
    public void load(Repository repository, Path uri, String token,
                     boolean demandResourceAvailability) throws Exception {
        if (demandResourceAvailability) {
            this.loadInternal(repository, uri, token);
        } else {
            try {
                this.loadInternal(repository, uri, token);
            } catch (Exception e) {
                logger.warn("Unable to load properties from uri '"
                            + uri + "', repository '" + repository
                            + "', token '" + token + "'", e);
            }
        }
    }
    
    public void load() throws Exception {

        if (this.repository == null || this.uri == null) {
            throw new IllegalStateException(
                "JavaBean properties 'repository' and 'uri' must be specified");
        }
        this.clear();
        this.load(this.repository, this.uri, this.token, this.demandResourceAvailability);
    }
    
    @SuppressWarnings("unchecked")
    private void loadInternal(Repository repository, Path uri, String token)
        throws Exception {
        InputStream inputStream = repository.getInputStream(token, uri, false);
        Properties p = new Properties();
        p.load(inputStream);
        if (logger.isDebugEnabled()) {
            logger.debug("Loaded raw mappings from resource " + uri + ": " + p);
        }
        
        Enumeration e = p.propertyNames();
        while (e.hasMoreElements()) {
            String name = (String) e.nextElement();
            String value = p.getProperty(name);

            if (value != null) {
                if (!this.storeValuesAsLists) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Adding regular mapping " + name + ": " + value);
                    }

                    this.put(name, value);
                } else {
                    List<String> list = new ArrayList<String>();
                    String[] components = value.split(",");
                    list.addAll(Arrays.asList(components));
                    if (logger.isDebugEnabled()) {
                        logger.debug("Adding list mapping " + name + ": " + list);
                    }
                    this.put(name, list);
                }

            }
        }
    }

}
