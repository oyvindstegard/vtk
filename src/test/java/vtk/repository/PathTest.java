/* Copyright (c) 2008, University of Oslo, Norway
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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

import java.util.List;
import java.util.function.Supplier;

import org.junit.Test;

public class PathTest {

    @Test
    public void commonPaths() {
        testCommonPath(Path.fromString("/a/b"), null, Path.ROOT);
        testCommonPath(Path.ROOT, null, Path.ROOT);
        testCommonPath(Path.fromString("/a/b/c"), Path.ROOT, Path.ROOT);
        testCommonPath(Path.ROOT, Path.fromString("/a/g"), Path.ROOT);
        testCommonPath(Path.fromString("/a/b"), Path.fromString("/a/b/c"), Path.fromString("/a/b"));
        testCommonPath(Path.fromString("/a/b/c"), Path.fromString("/a/b"), Path.fromString("/a/b"));
        testCommonPath(Path.fromString("/a/c/d"), Path.fromString("/a/b"), Path.fromString("/a"));
        testCommonPath(Path.fromString("/a/b"), Path.fromString("/b/a"), Path.ROOT);
        testCommonPath(Path.fromString("/a/b/g"), Path.fromString("/c/b/g"), Path.ROOT);
        Path path = Path.fromString("/foo/bar/baz");
        Path otherPath = Path.fromString("/foo/bar/baz");
        testCommonPath(path, otherPath, path);
    }

    private void testCommonPath(Path path, Path otherPath, Path expected) {
        Path common = path.common(otherPath);
        assertEquals(expected, common);
    }
    
    @Test
    public void paths() {

        assertInvalid((String) null);
        assertInvalid("");
        assertInvalid(" ");
        assertInvalid("//");
        assertInvalid("invalid.path");
        assertInvalid(" /invalid/path");
        assertInvalid("/invalid.path/..");
        assertInvalid("/invalid.path/.");
        assertInvalid("/invalid//path");
        assertInvalid("/invalid/../path");
        assertInvalid("/invalid.path/");
        assertInvalid(getString("i", Path.MAX_LENGTH));

        Path p = Path.fromString("/");
        assertEquals(p.toString(), "/");
        assertTrue(p.isRoot());
        assertEquals(p, Path.ROOT);
        assertEquals("/", p.getName());
        assertEquals(0, p.getDepth());
        assertNull(p.getParent());
        assertEquals(0, p.getAncestors().size());

        p = Path.fromString("/a/b/c/d");
        assertEquals(p.toString(), "/a/b/c/d");
        assertFalse(p.isRoot());
        assertEquals("d", p.getName());
        assertEquals(5, p.getElements().size());
        assertEquals(5, p.getPaths().size());
        assertEquals(4, p.getDepth());

        assertEquals(4, p.getAncestors().size());

        Path p2 = Path.fromString("/a/b/c/d");
        assertEquals(p, p2);
        assertEquals(p.hashCode(), p2.hashCode());

        p2 = Path.fromString("/b/c/d");
        assertTrue(p2.compareTo(p) > 0);

        assertEquals("/", p.getElements().get(0));
        assertEquals("a", p.getElements().get(1));
        assertEquals("b", p.getElements().get(2));
        assertEquals("c", p.getElements().get(3));
        assertEquals("d", p.getElements().get(4));

        assertEquals(1, Path.fromString("/").getElements().size());
        assertEquals("/", Path.fromString("/").getElements().get(0));

        List<Path> paths = Path.fromString("/aa/bb/cc").getPaths();
        assertEquals(4, paths.size());
        assertEquals(Path.fromString("/"), paths.get(0));
        assertEquals(0, paths.get(0).getDepth());
        assertEquals(Path.fromString("/aa"), paths.get(1));
        assertEquals(1, paths.get(1).getDepth());
        assertEquals(Path.fromString("/aa/bb"), paths.get(2));
        assertEquals(2, paths.get(2).getDepth());
        assertEquals(Path.fromString("/aa/bb/cc"), paths.get(3));
        assertEquals(3, paths.get(3).getDepth());

        assertNull(Path.fromString("/").getParent());
        assertEquals(Path.ROOT, Path.fromString("/a").getParent());
        assertEquals(Path.fromString("/a"), Path.fromString("/a/b").getParent());
        assertEquals(Path.fromString("/a/b"), Path.fromString("/a/b/c").getParent());

        paths = Path.fromString("/").getPaths();
        assertEquals(1, paths.size());
        assertEquals(Path.ROOT, paths.get(0));

        assertEquals("c", p.getParent().getElements().get(3));

        assertTrue(p.isAncestorOf(Path.fromString("/a/b/c/d/e")));
        assertFalse(p.isAncestorOf(Path.fromString("/a/b/c/e")));

        assertTrue(Path.fromString("/zz").isAncestorOf(Path.fromString("/zz/z")));
        assertTrue(Path.fromString("/").isAncestorOf(Path.fromString("/x")));
        assertTrue(Path.fromString("/foo").isAncestorOf(Path.fromString("/foo/bar")));
        assertTrue(Path.fromString("/foo").isAncestorOf(Path.fromString("/foo/bar/baz")));
        assertTrue(Path.fromString("/foo/bar/baz").isAncestorOf(Path.fromString("/foo/bar/baz/bong")));

        assertFalse(Path.fromString("/x").isAncestorOf(Path.fromString("/")));
        assertFalse(Path.fromString("/zz").isAncestorOf(Path.fromString("/zzz")));
        assertFalse(Path.fromString("/x").isAncestorOf(Path.fromString("/y")));
        assertFalse(Path.fromString("/xx").isAncestorOf(Path.fromString("/y")));
        assertFalse(Path.fromString("/xx").isAncestorOf(Path.fromString("/xy")));
        assertFalse(Path.fromString("/xx").isAncestorOf(Path.fromString("/xy/xx/xx/xx/xx")));
        assertFalse(Path.fromString("/foo").isAncestorOf(Path.fromString("/foobar/baz")));
        assertFalse(Path.fromString("/foo/bar/baz").isAncestorOf(Path.fromString("/foo/BAR/baz/bong")));
        
        // Self should not be ancestor of self
        assertFalse(Path.fromString("/a/b").isAncestorOf(Path.fromString("/a/b")));
        assertFalse(Path.fromString("/a/b/c").isAncestorOf(Path.fromString("/a/b/d")));
        assertFalse(Path.fromString("/").isAncestorOf(Path.fromString("/")));

        assertEquals(p.extend("e"), Path.fromString("/a/b/c/d/e"));
        assertEquals(p.extend("e/f/g"), Path.fromString("/a/b/c/d/e/f/g"));

        // Test getAncestors()
        List<Path> ancestors = Path.fromString("/1/2/3/4").getAncestors();
        assertEquals(Path.fromString("/"), ancestors.get(0));
        assertEquals(Path.fromString("/1"), ancestors.get(1));
        assertEquals(Path.fromString("/1/2"), ancestors.get(2));
        assertEquals(Path.fromString("/1/2/3"), ancestors.get(3));

        assertEquals(0, Path.fromString("/").getAncestors().size());

        assertEquals(Path.fromString("/a/b/c"), Path.fromString("/a/b/c/d").getPath(3));

        p = Path.fromString("/a/b/c");
        assertEquals(p, p.expand("."));
        assertEquals(p, p.expand("./"));

        assertEquals(Path.fromString("/a/b"), p.expand(".."));
        assertEquals(Path.fromString("/a/b"), p.expand("../"));

        assertEquals(p, p.expand("../../../a/b/c"));
        assertNull(p.expand("../../../../"));
        
        assertEquals(Path.fromString("/x"), p.expand("../../../x/."));
        
        p = Path.fromString("/a/b");
        assertEquals(p, p.append(Path.ROOT));
        assertEquals(p, Path.ROOT.append(p));
        assertEquals(Path.fromString("/a/b/c"), p.append(Path.fromString("/c")));
        assertEquals(Path.ROOT, Path.ROOT.append(Path.ROOT));

        p = Path.fromString("/a/b/c.html");
        p2 = Path.fromString("/");
        assertEquals(p, p.right(p2));

        p = Path.fromString("/a/b/c.html");
        p2 = Path.fromString("/a/b");
        assertEquals(Path.fromString("/c.html"), p.right(p2));
        p2 = Path.fromString("/a/b/c.html");
        assertEquals(Path.ROOT, p.right(p2));

        assertInvalid(() -> Path.fromString("/a/b").right(Path.fromString("/d/e")));

    }

    @Test
    public void fromStringWithTrailingSlash() {

        Path actual = Path.fromStringWithTrailingSlash("/test/path/");
        Path expected = Path.fromString("/test/path");
        assertEquals(expected, actual);

        actual = Path.fromStringWithTrailingSlash("/test/path//");
        assertEquals(expected, actual);

        actual = Path.fromStringWithTrailingSlash("/test/path/////");
        assertEquals(expected, actual);
        
        actual = Path.fromStringWithTrailingSlash("/");
        expected = Path.ROOT;
        assertEquals(expected, actual);

        actual = Path.fromStringWithTrailingSlash("//");
        expected = Path.ROOT;
        assertEquals(expected, actual);
        
        actual = Path.fromStringWithTrailingSlash("///");
        expected = Path.ROOT;
        assertEquals(expected, actual);
        
        try {
            actual = Path.fromStringWithTrailingSlash("/test///path///");
            fail("Invalid path was not treated as such");
        } catch (IllegalArgumentException e) {
            // Expected
            assertTrue(e.getMessage().contains("/test///path"));
        }
        
        try {
            actual = Path.fromStringWithTrailingSlash("");
            fail("Invalid empty path was not treated as such");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        
        try {
            actual = Path.fromStringWithTrailingSlash("fail");
            fail("Invalid path was not treated as such");
        } catch (IllegalArgumentException e) {
            // Expected
        }

    }

    private void assertInvalid(Supplier<Path> fn) {
        try {
            Path path = fn.get();
            fail("Invalid path constructed: '" + path + "'");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }
    
    private void assertInvalid(String path) {
        try {
            Path.fromString(path);
            fail("Invalid path constructed: '" + path + "'");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    private String getString(String str, int length) {
        StringBuilder sb = new StringBuilder("/");
        for (int i = 1; i < length; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

}
