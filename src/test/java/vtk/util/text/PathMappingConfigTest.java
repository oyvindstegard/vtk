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
package vtk.util.text;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;
import org.junit.Test;

import vtk.repository.Path;
import vtk.util.text.PathMappingConfig.Entry;


public class PathMappingConfigTest {

    private static final String TEST_CONFIG = 
        "/ = root\n"
        + "/a = value-a\n"
        + "/a[d:e,f:g] = value-a2\n"
        + "# comment\n"
        + "\t\t# comment\n"
        + "/b = value-b # comment\n"
        + "/c = value-c\n"
        + "/c/d# = value d\n"
        + "/c/d/e/f/g/ = exact\n"
        + "/c/d/e/f/g/h = bar\n"
        + "/x/y/z = xxx\n"
        + "/x/y/z[a:b,c:d]/ = exact\n"
        + "/esc/rhs = \\a\\b\\=";
    
    @Test
    public void get() throws Exception {
        InputStream is = new ByteArrayInputStream(TEST_CONFIG.getBytes("utf-8"));
        PathMappingConfig<String> config = PathMappingConfig.strConfig(is);

        assertNotNull(config.get(Path.fromString("/")));
        assertNull(config.get(Path.fromString("/unknown")));
        
        List<Entry<String>> entries = config.get(Path.fromString("/a"));
        assertEquals(2, entries.size());
        assertEquals("value-a", entries.get(0).value);
        assertEquals(0, entries.get(0).qualifiers.size());
        assertEquals("value-a2", entries.get(1).value);
        assertEquals(2, entries.get(1).qualifiers.size());
        assertEquals("d", entries.get(1).qualifiers.get(0).name);
        assertEquals("e", entries.get(1).qualifiers.get(0).value);
        assertEquals("f", entries.get(1).qualifiers.get(1).name);
        assertEquals("g", entries.get(1).qualifiers.get(1).value);

        assertNotNull(config.get(Path.fromString("/c")));
        assertNull(config.get(Path.fromString("/c/d")));
        assertNull(config.get(Path.fromString("/c/d/e")));
        assertNull(config.get(Path.fromString("/c/d/e/f")));

        assertNotNull(config.get(Path.fromString("/c/d/e/f/g")));
        assertTrue(config.get(Path.fromString("/c/d/e/f/g")).get(0).exact);
        assertNotNull(config.get(Path.fromString("/c/d/e/f/g/h")));
        assertFalse(config.get(Path.fromString("/c/d/e/f/g/h")).get(0).exact);

        assertNull(config.get(Path.fromString("/c/d/e/f/g/h/i")));
        
        assertNotNull(config.get(Path.fromString("/x/y/z")));
        assertTrue(config.get(Path.fromString("/x/y/z")).get(1).exact);
        
        entries = config.get(Path.fromString("/esc/rhs"));
        assertEquals(1, entries.size());
        assertEquals("\\a\\b=", entries.get(0).value);
    }
    
    @Test
    public void getMatchAncestors() throws Exception {
        String testConfig = 
                  "// = The Root Resource exactly\n"
                + "/[lunarPhase:full moon]/ = Full Moon\n"
                + "/ = Default\n"
                + "/a[] =   The A area  \n"
                + "/a # no effect\n"
                + "/a/b[x:y]/ = The AB value in case of x=y\n"
                + "/a/b/[z:1] = The AB value in case of z=1\n"
                + "/E/ = Exactly E\n"
                + "/a/b/c = The ABC area\n"
                + "/a/b/c[foo\\:bar:baz] = The ABC area in case of foo:bar=baz\n"
                + "/a/b/c/d/ = Exactly ABCD\n";

        InputStream is = new ByteArrayInputStream(testConfig.getBytes("utf-8"));
        PathMappingConfig config = PathMappingConfig.strConfig(is);
        
        // For "/"
        List<Entry<String>> entries = config.getMatchAncestor(Path.fromString("/"));
        assertEquals(3, entries.size());
        assertTrue(entries.get(0).exact);
        assertEquals("The Root Resource exactly", entries.get(0).value);
        assertEquals(Path.ROOT, entries.get(0).path);
        assertTrue(entries.get(0).qualifiers.isEmpty());
        
        assertTrue(entries.get(1).exact);
        assertEquals("Full Moon", entries.get(1).value);
        assertEquals(Path.ROOT, entries.get(1).path);
        assertEquals(1, entries.get(1).qualifiers.size());
        assertEquals("lunarPhase", entries.get(1).qualifiers.get(0).name);
        assertEquals("full moon", entries.get(1).qualifiers.get(0).value);

        assertFalse(entries.get(2).exact);
        assertEquals("Default", entries.get(2).value);
        assertEquals(Path.ROOT, entries.get(2).path);
        assertTrue(entries.get(2).qualifiers.isEmpty());
        
        // For "/unknown"
        entries = config.getMatchAncestor(Path.fromString("/unknown"));
        assertEquals(1, entries.size());
        assertFalse(entries.get(0).exact);
        assertEquals("Default", entries.get(0).value);
        assertEquals(Path.ROOT, entries.get(0).path);
        assertTrue(entries.get(0).qualifiers.isEmpty());
        
        // For "/a"
        entries = config.getMatchAncestor(Path.fromString("/a"));
        assertEquals(1, entries.size());
        assertFalse(entries.get(0).exact);
        assertEquals("The A area", entries.get(0).value);
        assertEquals(Path.fromString("/a"), entries.get(0).path);
        assertTrue(entries.get(0).qualifiers.isEmpty());

        // For "/a/1/2/3/unknown"
        entries = config.getMatchAncestor(Path.fromString("/a/1/2/3/unknown"));
        assertEquals(1, entries.size());
        assertFalse(entries.get(0).exact);
        assertEquals("The A area", entries.get(0).value);
        assertEquals(Path.fromString("/a"), entries.get(0).path);
        assertTrue(entries.get(0).qualifiers.isEmpty());

        // For "/a/b"
        entries = config.getMatchAncestor(Path.fromString("/a/b"));
        assertEquals(2, entries.size());
        assertTrue(entries.get(0).exact);
        assertEquals("The AB value in case of x=y", entries.get(0).value);
        assertEquals(Path.fromString("/a/b"), entries.get(0).path);
        assertEquals(1, entries.get(0).qualifiers.size());
        assertEquals("x", entries.get(0).qualifiers.get(0).name);
        assertEquals("y", entries.get(0).qualifiers.get(0).value);
        
        assertTrue(entries.get(1).exact);
        assertEquals("The AB value in case of z=1", entries.get(1).value);
        assertEquals(Path.fromString("/a/b"), entries.get(1).path);
        assertEquals(1, entries.get(1).qualifiers.size());
        assertEquals("z", entries.get(1).qualifiers.get(0).name);
        assertEquals("1", entries.get(1).qualifiers.get(0).value);
        
        // For "/E"
        entries = config.getMatchAncestor(Path.fromString("/E"));
        assertEquals(1, entries.size());
        assertTrue(entries.get(0).exact);
        assertEquals("Exactly E", entries.get(0).value);
        assertEquals(Path.fromString("/E"), entries.get(0).path);
        assertTrue(entries.get(0).qualifiers.isEmpty());

        // For "/E/x" (gets config from "/", skips exact config for "/E")
        entries = config.getMatchAncestor(Path.fromString("/E/x"));
        assertEquals(1, entries.size());
        assertFalse(entries.get(0).exact);
        assertEquals("Default", entries.get(0).value);
        assertEquals(Path.fromString("/"), entries.get(0).path);
        assertTrue(entries.get(0).qualifiers.isEmpty());
        
        // For /a/b/c/f (should inherit from /a/b/c)
        entries = config.getMatchAncestor(Path.fromString("/a/b/c/f"));
        assertEquals(2, entries.size());
        assertFalse(entries.get(0).exact);
        assertEquals("The ABC area", entries.get(0).value);
        assertEquals(Path.fromString("/a/b/c"), entries.get(0).path);
        assertTrue(entries.get(0).qualifiers.isEmpty());
        
        assertFalse(entries.get(1).exact);
        assertEquals("The ABC area in case of foo:bar=baz", entries.get(1).value);
        assertEquals(Path.fromString("/a/b/c"), entries.get(1).path);
        assertEquals(1, entries.get(1).qualifiers.size());
        assertEquals("foo:bar", entries.get(1).qualifiers.get(0).name);
        assertEquals("baz", entries.get(1).qualifiers.get(0).value);
        
        // For /a/b/c/d
        entries = config.getMatchAncestor(Path.fromString("/a/b/c/d"));
        assertEquals(1, entries.size());
        assertTrue(entries.get(0).exact);
        assertEquals("Exactly ABCD", entries.get(0).value);
        assertEquals(Path.fromString("/a/b/c/d"), entries.get(0).path);
        assertTrue(entries.get(0).qualifiers.isEmpty());
        
    }

    @Test
    public void valueFactory() throws Exception {
        InputStream is = new ByteArrayInputStream("/a = 1\n/a = 2".getBytes("utf-8"));
        PathMappingConfig<Integer> cfg = new PathMappingConfig<>(is, s -> Integer.parseInt(s));
        assertEquals(Integer.valueOf(1), cfg.get(Path.fromString("/a")).get(0).value);
        assertEquals(Integer.valueOf(2), cfg.get(Path.fromString("/a")).get(1).value);
    }

    @Test(expected = IllegalStateException.class)
    public void valueFactoryError1() throws Exception {

        InputStream is = new ByteArrayInputStream("/a = x".getBytes("utf-8"));
        try {
            PathMappingConfig<Integer> cfg = new PathMappingConfig<>(is, s -> Integer.parseInt(s));
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("line 1"));
            throw e;
        }
    }

    @Test(expected = IllegalStateException.class)
    public void valueFactoryError2() throws Exception {

        InputStream is = new ByteArrayInputStream("/a = 1\n#some comment\n/b = x".getBytes("utf-8"));
        try {
            PathMappingConfig<Integer> cfg = new PathMappingConfig<>(is, s -> Integer.parseInt(s));
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("line 3"));
            throw e;
        }
    }
}
