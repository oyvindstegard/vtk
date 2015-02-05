/* Copyright (c) 2012, University of Oslo, Norway
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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.util.text.Json;

public class JSONBackedListResource implements List<Object>, InitializingBean {
    private Repository repository;
    private Path uri;
    private String token;
    private List<Object> list;
    
    
    @Override
    public void afterPropertiesSet() throws Exception {
        try {
            load();
        } catch (Throwable t) {
            // Ignore
        }
    }   

    public List<?> getList() {
        return Collections.unmodifiableList(this.list);
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
    
    public void load() throws Exception {
        List<Object> list = null;
        try {
            InputStream inputStream = this.repository.getInputStream(this.token, this.uri, false);
            Object parsed = Json.parse(inputStream);
            if (!(parsed instanceof List<?>)) {
                return;
            }
            List<?> l = (List<?>) parsed;
            list = new ArrayList<Object>();
            for (Object o: l) {
                list.add(o);
            }
        } finally {
            this.list = list;
        }
    }

    @Override
    public int size() {
        if (this.list == null) {
            return 0;
        }
        return this.list.size();
    }

    @Override
    public boolean isEmpty() {
        if (this.list == null) {
            return true;
        }
        return this.list.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        if (this.list == null) {
            return false;
        }
        return this.list.contains(o);
    }

    @Override
    public Iterator<Object> iterator() {
        if (this.list == null) {
            return EMPTY_ITERATOR;
        }
        return list.iterator();
    }

    @Override
    public Object[] toArray() {
        if (this.list == null) {
            return new Object[0];
        }        
        return this.list.toArray();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T[] toArray(T[] a) {
        if (this.list == null) {
            return (T[]) new Object[0];
        }
        return this.list.toArray(a);
    }

    @Override
    public boolean add(Object e) {
        throw new RuntimeException("Illegal operation");
    }

    @Override
    public boolean remove(Object o) {
        throw new RuntimeException("Illegal operation");
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        if (this.list == null) {
            return false;
        }
        return this.list.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends Object> c) {
        throw new RuntimeException("Illegal operation");
    }

    @Override
    public boolean addAll(int index, Collection<? extends Object> c) {
        throw new RuntimeException("Illegal operation");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new RuntimeException("Illegal operation");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new RuntimeException("Illegal operation");
    }

    @Override
    public void clear() {
        throw new RuntimeException("Illegal operation");
    }

    @Override
    public Object get(int index) {
        if (this.list == null) {
            throw new IndexOutOfBoundsException("Index: 0, Size: 0");
        }
        return this.list.get(index);
    }

    @Override
    public Object set(int index, Object element) {
        throw new RuntimeException("Illegal operation");
    }

    @Override
    public void add(int index, Object element) {
        throw new RuntimeException("Illegal operation");
    }

    @Override
    public Object remove(int index) {
        throw new RuntimeException("Illegal operation");
    }

    @Override
    public int indexOf(Object o) {
        if (this.list == null) {
            return -1;
        }
        return this.list.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        if (this.list == null) {
            return -1;
        }
        return this.list.lastIndexOf(o);
    }

    @Override
    public ListIterator<Object> listIterator() {
        if (this.list == null) {
            return EMPTY_ITERATOR;
        }
        return this.list.listIterator();
    }

    @Override
    public ListIterator<Object> listIterator(int index) {
        if (this.list == null) {
            return EMPTY_ITERATOR;
        }
        return this.list.listIterator();
    }

    @Override
    public List<Object> subList(int fromIndex, int toIndex) {
        if (this.list == null) {
            return new ArrayList<Object>();
        }
        return this.list.subList(fromIndex, toIndex);
    }
    
    @Override
    public String toString() {
        if (this.list == null) {
            return "[]";
        }
        return this.list.toString();
    }

    private static final ListIterator<Object> EMPTY_ITERATOR = new ListIterator<Object>() {
        @Override
        public boolean hasNext() {
            return false;
        }
        @Override
        public Object next() {
            return null;
        }
        @Override
        public boolean hasPrevious() {
            return false;
        }
        @Override
        public Object previous() {
            throw new NoSuchElementException();
        }
        @Override
        public int nextIndex() {
            return 0;
        }
        @Override
        public int previousIndex() {
            return -1;
        }
        @Override
        public void remove() {
            throw new IllegalStateException();
        }
        @Override
        public void set(Object e) {
            throw new IllegalStateException();
        }
        @Override
        public void add(Object e) {
            throw new IllegalStateException();
        }
    };
}
