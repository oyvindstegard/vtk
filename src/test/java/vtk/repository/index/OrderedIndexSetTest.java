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
    public void testAdd() throws IOException {
        index.add("/a");
        index.add("/b");
        index.add("/c");
        index.commit();
        assertEquals(3, index.size());
    }

    /**
     * Test of add method, of class OrderedIndexSet.
     */
    @Test
    public void testAddDuplicates() throws IOException {
        index.add("/a");
        index.add("/a");
        index.add("/a");
        index.add("/b");
        index.add("/c");
        index.add("/c");
        index.commit();
        assertTrue(index.contains("/a"));
        assertTrue(index.contains("/b"));
        assertTrue(index.contains("/c"));
        assertEquals(3, index.size());
    }

    @Test
    public void testMultipleCommits() throws IOException {
        // Tests that all index segments are considered for read ops
        index.add("/a");
        index.add("/b");
        index.add("/c");
        index.commit();
        index.add("/d");
        index.add("/e");
        index.add("/f");
        index.commit();
        assertTrue(index.contains("/f"));
    }

    /**
     * Test of remove method, of class OrderedIndexSet.
     * @throws java.io.IOException
     */
    @Test
    public void testRemove() throws IOException {
        index.add("/a");
        index.add("/b");
        index.add("/c");
        index.remove("/a");
        index.commit();
        assertEquals(2, index.size());
        index.remove("/b");
        index.commit();
        assertEquals(1, index.size());
        index.remove("Something not existing");
    }

    @Test
    public void testClear() throws IOException {
        index.add("/a");
        index.add("/b");
        index.add("/c");
        index.commit();
        assertEquals(3, index.size());
        index.clear();
        index.commit();
        assertTrue(index.isEmpty());
    }

    /**
     * Test of size method, of class OrderedIndexSet.
     */
    @Test
    public void testSizeAndEmpty() throws IOException {
        assertTrue(index.isEmpty());
        index.add("/a");
        index.add("/b");
        index.add("/c");
        index.commit();
        assertEquals(3, index.size());
        assertFalse(index.isEmpty());
    }

    /**
     * Test of contains method, of class OrderedIndexSet.
     */
    @Test
    public void testContains() throws IOException {
        index.add("/a");
        index.add("/b");
        index.add("/c");
        index.commit();
        assertTrue(index.contains("/a"));
        assertTrue(index.contains("/b"));
        assertTrue(index.contains("/c"));

        assertFalse(index.contains("/d"));
        assertFalse(index.contains(""));
    }

    /**
     * Test of commit method, of class OrderedIndexSet.
     */
    @Test
    public void testCommit() throws IOException {
        index.add("/a");
        index.add("/b");
        index.add("/c");
        assertEquals(0, index.size()); // Check not available before commit
        index.commit();
        assertEquals(3, index.size());
    }

    /**
     * Test of close method, of class OrderedIndexSet.
     */
    @Test
    public void testClose() throws IOException {
        index.close();
        assertFalse(index.indexDir().exists());
    }

    /**
     * Test of iterator method, of class OrderedIndexSet.
     *
     * <p>Tests lexicographic ordering and value set.
     */
    @Test
    public void testIterator() throws IOException {
        final List<String> values = new ArrayList<>();
        for (char c = 'Z'; c >= 'A'; c--) {
            values.add("Value " + c);
        }
        index.addAll(values);
        index.addAll(values); // Test duplicates consolidation
        index.commit();

        int i=values.size()-1;
        for (String indexValue: index) {
            if (i < 0) {
                fail("Unexpected number of values encountered in index");
            }
            assertEquals(indexValue, values.get(i--));
        }

        // Multiple segments
        for (char c = '9'; c >= '0'; c--) {
            values.add("Value " + c);
        }
        index.addAll(values);
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
