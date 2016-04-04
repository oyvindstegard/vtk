/* Copyright (c) 2015, University of Oslo, Norway
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
package vtk.web.decorating;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.util.io.InputSource;
import vtk.util.repository.RepositoryInputSource;

public class CollectionComponentManager implements ComponentResolver {
    
    private static Logger logger = LoggerFactory.getLogger(CollectionComponentManager.class);

    private Repository repository;
    private String token;
    private String[] collections;
    private DynamicComponentParser parser;
    private Map<Path, CompiledComponent> components = new HashMap<>();

    private static class CompiledComponent {
        private InputSource source;
        private DecoratorComponent component;
        private long timestamp;
        public CompiledComponent(InputSource source, DecoratorComponent component, long timestamp) {
            this.source = source;
            this.component = component;
            this.timestamp = timestamp;
        }
        public DecoratorComponent component() { return component; }
        public InputSource source() { return source; }
        public boolean outdated() throws IOException 
            { return timestamp < source.getLastModified(); }
    }
    
    public CollectionComponentManager(Repository repository, String token, 
            String[] collections, DynamicComponentParser parser) {
        this.repository = repository;
        this.token = token;
        this.collections = collections;
        this.parser = parser;
        loadSources();
    }
    
    
    public synchronized void refresh() {
        loadSources();
    }

    private synchronized void loadSources() {
        long now = System.currentTimeMillis();
        Map<Path, CompiledComponent> newComponents = new HashMap<>();
        for (String coll: collections) {
            try {
                Path uri = Path.fromString(coll);
                Resource[] libs = repository.listChildren(token, uri, false);
                for (Resource lib: libs) {
                    if (!lib.isCollection()) continue;
                    else loadLibrary(lib, now, newComponents);
                }
            } catch (Exception e) {
                if (logger.isDebugEnabled())
                    logger.warn("Unable to load components from collection '" 
                            + coll + "'" + e.getMessage() != null ? ": " 
                                    + e.getMessage() : "", e);
            }
        }
        components = newComponents;
    }
    
    private void loadLibrary(Resource lib, long timestamp, 
            Map<Path, CompiledComponent> newComponents) throws Exception {
        
        Resource[] children = repository.listChildren(token, lib.getURI(), false);
        String namespace = lib.getName();
        for (Resource r: children) {
            if (r.isCollection()) continue;
            if (components.containsKey(r.getURI())) {
                CompiledComponent component = components.get(r.getURI());
                if (component.outdated()) {
                    DecoratorComponent c = parser.compile(namespace, r.getName(), component.source());
                    component = new CompiledComponent(component.source(), c, timestamp);
                }
                newComponents.put(r.getURI(), component);
            }
            else {
                InputSource source = new RepositoryInputSource(r.getURI(), repository, token);
                DecoratorComponent c = parser.compile(namespace, r.getName(), source);
                newComponents.put(r.getURI(), new CompiledComponent(source, c, timestamp));
            }
        }
    }
    

    @Override
    public DecoratorComponent resolveComponent(String namespace, String name) {
        for (CompiledComponent comp: components.values()) {
            DecoratorComponent c = comp.component();
            if (namespace.equals(c.getNamespace()) && name.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }


    @Override
    public List<DecoratorComponent> listComponents() {
        List<DecoratorComponent> list = new ArrayList<>();
        for (CompiledComponent comp: components.values()) {
            DecoratorComponent c = comp.component();
            list.add(c);
        }
        return list;
    }
    
}
