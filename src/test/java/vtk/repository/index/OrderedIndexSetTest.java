/* Copyright (c) 2017, University of Oslo, Norway
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
package vtk.repository.index;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for {@link OrderedIndexSet}
 */
public class OrderedIndexSetTest {

    private OrderedIndexSet index;

    public OrderedIndexSetTest() {
    }

    @Before
    public void setUp() throws IOException {
        index = new OrderedIndexSet(new File(System.getProperty("java.io.tmpdir")));
    }

    @After
    public void tearDown() throws IOException {
        index.close();
    }

    /**
     * Test of add method, of class OrderedIndexSet.
     */
    @Test
    public void testAdd() {
        assertTrue(index.add("/a"));
        assertTrue(index.add("/b"));
        assertTrue(index.add("/c"));
        assertEquals(3, index.size());
    }

    /**
     * Test of add method, of class OrderedIndexSet.
     */
    @Test
    public void testAddValue() {
        index.addValue("/a");
        index.addValue("/b");
        index.addValue("/c");
        index.addValue("/c");
        assertEquals(3, index.size());
        assertTrue(index.contains("/a"));
        assertTrue(index.contains("/b"));
        assertTrue(index.contains("/c"));
        assertFalse(index.contains("/d"));
    }

    @Test
    public void testAddAll() {
        final Collection<String> set = Arrays.asList(new String[]{"/a", "/b", "/c"});
        final Collection<String> subSet = Arrays.asList(new String[]{"/a", "/b"});
        final Collection<String> superSet = Arrays.asList(new String[]{"/a", "/b", "/c", "/d"});

        assertTrue(index.addAll(set));
        assertEquals(3, index.size());

        assertFalse(index.addAll(set));
        assertFalse(index.addAll(subSet));

        assertTrue(index.addAll(superSet));
        assertEquals(4, index.size());
    }

    @Test
    public void testRetainAll() {
        final Collection<String> set = Arrays.asList(new String[]{"/a", "/b", "/c", "/d"});
        final Collection<String> subSet = Arrays.asList(new String[]{"/a", "/b"});

        assertTrue(index.addAll(set));
        assertTrue(index.retainAll(subSet));
        assertEquals(2, index.size());
        assertTrue(index.containsAll(subSet));
    }

    @Test
    public void testRetainAll_disjoint() {
        final Collection<String> set = Arrays.asList(new String[]{"/a", "/b", "/c", "/d"});
        final Collection<String> disjointSet = Arrays.asList(new String[]{"/x", "/y"});

        assertTrue(index.addAll(set));
        assertTrue(index.retainAll(disjointSet));
        assertEquals(0, index.size()); // empty intersection and thus nothing to retain
    }

    @Test
    public void testRetainAll_emptySet() {
        final Collection<String> set = Arrays.asList(new String[]{"/a", "/b", "/c", "/d"});

        assertTrue(index.addAll(set));
        assertTrue(index.retainAll(Collections.emptySet()));
        assertEquals(0, index.size()); // empty intersection and thus nothing to retain
    }

    /**
     * Test of add method, of class OrderedIndexSet.
     */
    @Test
    public void testAddDuplicates() {
        assertTrue(index.add("/a"));
        assertFalse(index.add("/a"));
        assertFalse(index.add("/a"));
        assertTrue(index.add("/b"));
        assertTrue(index.add("/c"));
        assertFalse(index.add("/c"));

        assertTrue(index.contains("/a"));
        assertTrue(index.contains("/b"));
        assertTrue(index.contains("/c"));
        assertEquals(3, index.size());
    }

    @Test
    public void testMultipleCommits() {
        // Tests that all index segments are considered for read ops
        index.add("/a");
        index.add("/b");
        index.add("/c");
        // force read:
        index.contains("/somehting");

        // add more
        index.add("/d");
        index.add("/e");
        index.add("/f");

        assertTrue(index.contains("/f"));
    }

    /**
     * Test of remove method, of class OrderedIndexSet.
     */
    @Test
    public void testRemove() {
        assertTrue(index.add("/a"));
        assertTrue(index.add("/b"));
        assertTrue(index.add("/c"));
        assertTrue(index.remove("/a"));

        assertEquals(2, index.size());
        index.remove("/b");

        assertEquals(1, index.size());
        index.remove("Something not existing");
    }

    /**
     * Test of removeAll method, of class OrderedIndexSet.
     */
    @Test
    public void testRemoveAll() {
        final Collection<String> set = Arrays.asList(new String[]{"/a", "/b", "/c", "/d"});
        final Collection<String> disjointSet = Arrays.asList(new String[]{"/x", "/y"});
        final Collection<String> subSet = Arrays.asList(new String[]{"/a", "/b"});

        assertTrue(index.addAll(set));
        assertFalse(index.removeAll(Collections.emptySet()));
        assertFalse(index.removeAll(disjointSet));
        assertEquals(4, index.size());
        assertTrue(index.removeAll(subSet));
        assertEquals(2, index.size());
    }

    @Test
    public void testClear() {
        index.add("/a");
        index.add("/b");
        index.add("/c");

        assertEquals(3, index.size());
        index.clear();

        assertTrue(index.isEmpty());
    }

    /**
     * Test of size method, of class OrderedIndexSet.
     */
    @Test
    public void testSizeAndEmpty() {
        assertTrue(index.isEmpty());
        index.add("/a");
        index.add("/b");
        index.add("/c");

        assertEquals(3, index.size());

        index.remove("/a");
        assertEquals(2, index.size());

        assertFalse(index.isEmpty());
    }

    /**
     * Test of contains method, of class OrderedIndexSet.
     */
    @Test
    public void testContains() {
        index.add("/a");
        index.add("/b");
        index.add("/c");

        assertTrue(index.contains("/a"));
        assertTrue(index.contains("/b"));
        assertTrue(index.contains("/c"));

        assertFalse(index.contains("/d"));
        assertFalse(index.contains(""));
    }

    /**
     * Test of close method, of class OrderedIndexSet.
     */
    @Test
    public void testClose() {
        index.close();
        assertFalse(index.indexDir().exists());
    }

    /**
     * Test of iterator method, of class OrderedIndexSet.
     *
     * <p>Tests lexicographic ordering and value set.
     */
    @Test
    public void testIterator() {
        final List<String> values = new ArrayList<>();
        for (char c = 'Z'; c >= 'A'; c--) {
            values.add("Value " + c);
        }
        assertTrue(index.addAll(values));
        assertFalse(index.addAll(values)); // Test duplicates consolidation

        int i=values.size()-1;
        for (String indexValue: index) {
            if (i < 0) {
                fail("Unexpected number of values encountered in index");
            }
            assertEquals(indexValue, values.get(i--));
        }

        for (char c = '9'; c >= '0'; c--) {
            values.add("Value " + c);
        }
        assertTrue(index.addAll(values));

        // Attempt provication of multiple segments w/deleted docs
        index.commit();
        assertTrue(index.add("delete-me"));
        index.commit();
        assertTrue(index.remove("delete-me"));
        index.commit();

        i=values.size()-1;
        for (String indexValue: index) {
            if (i < 0) {
                fail("Unexpected number of values encountered in index");
            }
            assertEquals(indexValue, values.get(i--));
        }
    }

}
