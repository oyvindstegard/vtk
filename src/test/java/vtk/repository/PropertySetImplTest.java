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

package vtk.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinitionImpl;
import vtk.repository.resourcetype.Value;


/**
 *
 */
public class PropertySetImplTest {

    protected Set<Property> collector;
    protected Property title;
    protected Property modifiedBy;
    protected Property custom;
    protected PropertySetImpl ps;
    
    @Before
    public void setup() {
        ps = new PropertySetImpl();
        ps.setUri(Path.ROOT);
        ps.setResourceType("collection");
        
        collector = new HashSet<Property>();
        
        title = newStringProperty(Namespace.DEFAULT_NAMESPACE, "title", "Root resource");
        modifiedBy = newStringProperty(Namespace.DEFAULT_NAMESPACE, "modifiedBy", "vortex@localhost");
        custom = newStringProperty(new Namespace("cust", "http://custom/"), "foo", "bar");
    }
    
    @Test
    public void testIterationEmpty() {
        Iterator<Property> it = ps.iterator();
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Expected no such element exception");
        } catch (NoSuchElementException e) {
            // OK
        }
    }
    
    @Test
    public void testPropIterationPerfomance() {
        ps.addProperty(title);
        ps.propertyMap.put(new Namespace("foo", "bar"), new HashMap<String,Property>());
        ps.propertyMap.put(new Namespace("baz", "boz"), new HashMap<String,Property>());
        ps.propertyMap.put(new Namespace("bing", "bing"), new HashMap<String,Property>());
        ps.propertyMap.put(new Namespace("1", "2"), new HashMap<String,Property>());
        ps.propertyMap.put(new Namespace("3", "4"), new HashMap<String,Property>());
        ps.addProperty(modifiedBy);
        ps.addProperty(custom);
        ps.addProperty(newStringProperty(Namespace.DEFAULT_NAMESPACE, "prop1", "value1"));
        ps.addProperty(newStringProperty(Namespace.DEFAULT_NAMESPACE, "prop2", "value1"));
        ps.addProperty(newStringProperty(Namespace.DEFAULT_NAMESPACE, "prop3", "value1"));
        ps.addProperty(newStringProperty(Namespace.DEFAULT_NAMESPACE, "prop4", "value1"));
        ps.addProperty(newStringProperty(Namespace.DEFAULT_NAMESPACE, "prop5", "value1"));
        ps.addProperty(newStringProperty(Namespace.DEFAULT_NAMESPACE, "prop6", "value1"));
        ps.addProperty(newStringProperty(Namespace.DEFAULT_NAMESPACE, "prop7", "value1"));
        ps.addProperty(newStringProperty(Namespace.DEFAULT_NAMESPACE, "prop8", "value1"));
        ps.addProperty(newStringProperty(Namespace.DEFAULT_NAMESPACE, "prop9", "value1"));
        ps.addProperty(newStringProperty(Namespace.DEFAULT_NAMESPACE, "prop10", "value1"));
        ps.addProperty(newStringProperty(Namespace.DEFAULT_NAMESPACE, "prop11", "value1"));
        ps.addProperty(newStringProperty(Namespace.DEFAULT_NAMESPACE, "prop12", "value1"));
        ps.addProperty(newStringProperty(Namespace.DEFAULT_NAMESPACE, "prop13", "value1"));
        ps.addProperty(newStringProperty(Namespace.DEFAULT_NAMESPACE, "prop14", "value1"));
        ps.addProperty(newStringProperty(Namespace.DEFAULT_NAMESPACE, "prop15", "value1"));
        
        ps.addProperty(newStringProperty(Namespace.CUSTOM_NAMESPACE, "prop1", "value1"));
        ps.addProperty(newStringProperty(Namespace.CUSTOM_NAMESPACE, "prop2", "value1"));
        ps.addProperty(newStringProperty(Namespace.CUSTOM_NAMESPACE, "prop3", "value1"));
        ps.addProperty(newStringProperty(Namespace.CUSTOM_NAMESPACE, "prop4", "value1"));
        ps.addProperty(newStringProperty(Namespace.CUSTOM_NAMESPACE, "prop5", "value1"));
        ps.addProperty(newStringProperty(Namespace.CUSTOM_NAMESPACE, "prop6", "value1"));
        ps.addProperty(newStringProperty(Namespace.CUSTOM_NAMESPACE, "prop7", "value1"));
        ps.addProperty(newStringProperty(Namespace.CUSTOM_NAMESPACE, "prop8", "value1"));
        ps.addProperty(newStringProperty(Namespace.CUSTOM_NAMESPACE, "prop9", "value1"));
        ps.addProperty(newStringProperty(Namespace.CUSTOM_NAMESPACE, "prop10", "value1"));
        ps.addProperty(newStringProperty(Namespace.CUSTOM_NAMESPACE, "prop11", "value1"));
        ps.addProperty(newStringProperty(Namespace.CUSTOM_NAMESPACE, "prop12", "value1"));
        ps.addProperty(newStringProperty(Namespace.CUSTOM_NAMESPACE, "prop13", "value1"));
        ps.addProperty(newStringProperty(Namespace.CUSTOM_NAMESPACE, "prop14", "value1"));
        ps.addProperty(newStringProperty(Namespace.CUSTOM_NAMESPACE, "prop15", "value1"));
        
        long count = 0;
        long start = System.currentTimeMillis();
        for (int i = 0; i < 20000; i++) {
            for (Property p : ps.getProperties()) {
                Value v = p.getValue();
                count++;
            }
        }
        System.out.println("Time usage with getProperties(): " 
                + (System.currentTimeMillis() - start) + "ms, count = " + count);
        
        count = 0;
        start = System.currentTimeMillis();
        for (int i = 0; i < 20000; i++) {
            for (Property p : ps) {
                Value v = p.getValue();
                count++;
            }
        }
        System.out.println("Time usage with Iterable<Property>: "
                + (System.currentTimeMillis() - start) + "ms, count = " + count);
    }
    
    @Test
    public void testIterationOneProp() {
        ps.addProperty(title);
        Iterator<Property> it = ps.iterator();
        assertTrue(it.hasNext());
        assertEquals(title, it.next());
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Expected no such element exception");
        } catch (NoSuchElementException e) {
            // OK
        }
    }

    @Test
    public void testIterationTwoProps() {
        ps.addProperty(title);
        ps.addProperty(modifiedBy);
        for (Property p: ps) {
            collector.add(p);
        }
        assertTrue(collector.contains(title));
        assertTrue(collector.contains(modifiedBy));
        assertEquals(2, collector.size());
    }
    
    @Test
    public void testIterationMultipleNamespaces() {
        ps.addProperty(title);
        ps.addProperty(modifiedBy);
        ps.addProperty(custom);
        for (Property p: ps) {
            collector.add(p);
        }
        assertTrue(collector.contains(title));
        assertTrue(collector.contains(modifiedBy));
        assertTrue(collector.contains(custom));
        assertEquals(3, collector.size());
    }
    
    @Test
    public void testIterationEmptyNamespaceMap() {
        ps.addProperty(title);
        ps.propertyMap.put(new Namespace("foo", "bar"), new HashMap<String,Property>());
        ps.propertyMap.put(new Namespace("baz", "boz"), new HashMap<String,Property>());
        ps.propertyMap.put(new Namespace("bing", "bing"), new HashMap<String,Property>());
        ps.propertyMap.put(new Namespace("1", "2"), new HashMap<String,Property>());
        ps.propertyMap.put(new Namespace("3", "4"), new HashMap<String,Property>());
        ps.addProperty(modifiedBy);
        ps.addProperty(custom);
        
        for (Property p: ps) {
            collector.add(p);
        }
        assertTrue(collector.contains(title));
        assertTrue(collector.contains(modifiedBy));
        assertTrue(collector.contains(custom));
        assertEquals(3, collector.size());
    }

    @Test
    public void testIterationOnlyEmptyNamespaceMap() {
        ps.propertyMap.put(new Namespace("foo", "bar"), new HashMap<String,Property>());
        ps.propertyMap.put(new Namespace("baz", "boz"), new HashMap<String,Property>());
        ps.propertyMap.put(new Namespace("bing", "bing"), new HashMap<String,Property>());
        ps.propertyMap.put(new Namespace("1", "2"), new HashMap<String,Property>());
        ps.propertyMap.put(new Namespace("3", "4"), new HashMap<String,Property>());
        Iterator<Property> it = ps.iterator();
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Expected no such element exception");
        } catch (NoSuchElementException e) {
            // OK
        }
    }
    
    @Test
    public void testIterationRemoveNotAllowed() {
        // Removing property is not part of PropertySet interface API, so
        // iterator should not allow it either.
        ps.addProperty(title);
        Iterator<Property> it = ps.iterator();
        assertEquals(title, it.next());
        try {
            it.remove();
            fail("Expected IllegalStateException");
        } catch (UnsupportedOperationException e) {
            // OK
        }
    }
    
    private Property newStringProperty(Namespace namespace, String name, String value) {
        return PropertyTypeDefinitionImpl.createDefault(namespace, name, false)
                                         .createProperty(value);
    }
}
