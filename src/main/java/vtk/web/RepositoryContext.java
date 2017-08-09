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
package vtk.web;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.Path;
import vtk.repository.Resource;
import vtk.repository.TypeInfo;

/**
 * For corner cases, some form of cache limiting should probably be done..
 */
public class RepositoryContext {
    private static final String REQUEST_ATTRIBUTE = 
            RepositoryContext.class.getName() + ".requestAttribute";
    
    private final Map<String,ObjectStore> tokenMap = new HashMap<>();
    
    private static enum Category {
        FOR_PROCESSING_HIT,
        FOR_PROCESSING_MISS,
        NOT_FOR_PROCESSING_HIT,
        NOT_FOR_PROCESSING_MISS,
        TYPE_INFO_HIT,
        TYPE_INFO_MISS
    }
    
    // Categorized path object store/cache
    private static final class ObjectStore {

        private Map<Category, Map<Path, Object>> store;

        public void put(Category category, Path path, Object value) {
            if (this.store == null) {
                this.store = new EnumMap<>(Category.class);
            }
            
            Map<Path, Object> categoryMap = this.store.get(category);
            if (categoryMap == null) {
                categoryMap = new HashMap<>();
                this.store.put(category, categoryMap);
            }
            categoryMap.put(path, value);
        }

        public Object get(Category category, Path path) {
            if (this.store == null) return null;
            
            Map<Path, Object> categoryMap = this.store.get(category);
            if (categoryMap != null) {
                return categoryMap.get(path);
            }
            
            return null;            
        }

        public void remove(Category category, Path path) {
            if (this.store == null) return;
            
            Map<Path, Object> categoryMap = this.store.get(category);
            if (categoryMap != null) {
                categoryMap.remove(path);
            }
        }
    }

    private void putObject(String token, Category category, Path path, Object value) {
        ObjectStore store = this.tokenMap.get(token);
        if (store == null) {
            store = new ObjectStore();
            this.tokenMap.put(token, store);
        }
        store.put(category, path, value);
    }
    
    private Object getObject(String token, Category category, Path path) {
        ObjectStore store = this.tokenMap.get(token);
        if (store != null) {
            return store.get(category, path);
        }
        return null;
    }
    
    private void removeObject(String token, Category category, Path path) {
        ObjectStore store = this.tokenMap.get(token);
        if (store != null) {
            store.remove(category, path);
        }
    }

    public void clear() {
        this.tokenMap.clear();
    }
    
    public static void setRepositoryContext(RepositoryContext repositoryContext, HttpServletRequest request) {
        request.setAttribute(REQUEST_ATTRIBUTE, repositoryContext);
    }

    public static RepositoryContext getRepositoryContext(HttpServletRequest request) {
        RepositoryContext repositoryContext = (RepositoryContext)
            request.getAttribute(REQUEST_ATTRIBUTE);
        return repositoryContext;
    }

    public void addResourceHit(String token, Resource resource,
                               boolean forProcessing) {
        
        if (forProcessing) {
            putObject(token, Category.FOR_PROCESSING_HIT, resource.getURI(), resource);
            removeObject(token, Category.FOR_PROCESSING_MISS, resource.getURI());
        } else {
            putObject(token, Category.NOT_FOR_PROCESSING_HIT, resource.getURI(), resource);
            removeObject(token, Category.NOT_FOR_PROCESSING_MISS, resource.getURI());
        }
    }
    
    public Resource getResourceHit(String token, Path uri,
                                   boolean forProcessing) {
        if (forProcessing) {
            return (Resource)getObject(token, Category.FOR_PROCESSING_HIT, uri);
        } else {
            return (Resource)getObject(token, Category.NOT_FOR_PROCESSING_HIT, uri);
        }
    }
    

    public void addResourceMiss(String token, Path uri, Throwable throwable,
                                boolean forProcessing) {

        if (forProcessing) {
            putObject(token, Category.FOR_PROCESSING_MISS, uri, throwable);
            removeObject(token, Category.FOR_PROCESSING_HIT, uri);
        } else {
            putObject(token, Category.NOT_FOR_PROCESSING_MISS, uri, throwable);
            removeObject(token, Category.NOT_FOR_PROCESSING_HIT, uri);
        }
    }
    
    public Throwable getResourceMiss(String token, Path uri,
                                     boolean forProcessing) {
        if (forProcessing) {
            return (Throwable)getObject(token, Category.FOR_PROCESSING_MISS, uri);
        } else {
            return (Throwable)getObject(token, Category.NOT_FOR_PROCESSING_MISS, uri);
        }

    }
    
    public void addTypeInfoHit(String token, Path uri, TypeInfo typeInfo) {
        putObject(token, Category.TYPE_INFO_HIT, uri, typeInfo);
        removeObject(token, Category.TYPE_INFO_MISS, uri);
    }

    public TypeInfo getTypeInfoHit(String token, Path uri) {
        return (TypeInfo)getObject(token, Category.TYPE_INFO_HIT, uri);
    }


    public void addTypeInfoMiss(String token, Path uri, Throwable throwable) {
        putObject(token, Category.TYPE_INFO_MISS, uri, throwable);
        removeObject(token, Category.TYPE_INFO_HIT, uri);
    }

    public Throwable getTypeInfoMiss(String token, Path uri) {
        return (Throwable)getObject(token, Category.TYPE_INFO_MISS, uri);
    }
}


