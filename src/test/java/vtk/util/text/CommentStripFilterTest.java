/* Copyright (c) 2014, University of Oslo, Norway
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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import vtk.util.io.IO;

/**
 *
 * @author oyvind
 */
@SuppressWarnings("unchecked")
public class CommentStripFilterTest {
    
    /**
     * A hairy JSON file which should be completely valid after
     * stripping away all the comments.
     */
    public static final String JSON_TEST_RESOURCE = "CommentStripFilterTest.txt";
    
    private String jsonTestText;
    
    @Before
    public void setUp() throws IOException {
        InputStream is = getClass().getResourceAsStream(JSON_TEST_RESOURCE);
        jsonTestText = IO.readString(is, "utf-8").perform();
    }
    
    @Test
    public void specialCases() {
        assertEquals("", CommentStripFilter.stripComments(""));
        assertEquals("/", CommentStripFilter.stripComments("/"));
        assertEquals("", CommentStripFilter.stripComments("//"));
        assertEquals("", CommentStripFilter.stripComments("/**/"));
        assertEquals("", CommentStripFilter.stripComments("/*/**/"));
        assertEquals("", CommentStripFilter.stripComments("/*"));
        assertEquals("*/", CommentStripFilter.stripComments("*/"));
    }
    
    @Test
    public void lineComments() {
        assertEquals("\na\n", CommentStripFilter.stripComments("//ignore me\na\n// and me too..."));
        assertEquals("\r\na\r\n", CommentStripFilter.stripComments("//ignore me\r\na\r\n// and me too..."));
    }
    
    @Test
    public void commentsInString() {
        assertEquals("\"a /*c*/\"", CommentStripFilter.stripComments("/*x*/\"a /*c*/\"/*y*/"));
        assertEquals("\"x//y\"", CommentStripFilter.stripComments("\"x//y\"//z"));
    }
    
    @Test
    public void escapedDoubleQuoteDoesNotEndString() {
        assertEquals("\"a \\\"// /*string//\\\"..\"", CommentStripFilter.stripComments("\"a \\\"// /*string//\\\"..\""));
    }
    
    @Test
    public void blockComments() {
        assertEquals("\na", CommentStripFilter.stripComments("/*xxxxx\nxxx\nxxxxxxx   xxx\n   xxx*/\na"));
    }
    
    @Test
    public void parseJsonSourceWithStrippedComments() throws IOException, ParseException {
        Reader csr = new CommentStripFilter(jsonTestText);
        
        JSONParser parser = new JSONParser();
        Object o = parser.parse(csr);
        assertTrue(o instanceof List);
        List<Object> array = (List<Object>)o;
        
        assertEquals(2, array.size());
        
        Object o1 = array.get(0);
        assertTrue(o1 instanceof Map);
        Map<String,Object> map1 = (Map<String,Object>)o1;
        
        assertEquals(5, map1.size());
        assertEquals("b", map1.get("a"));
        assertEquals("d", map1.get("c"));
        assertEquals(Long.valueOf(1000), map1.get("abba /* ali baba */ \"bi //ng\""));
        assertEquals("/bar", map1.get("/foo"));
        
        assertTrue(map1.containsKey("some-array"));
        assertEquals(Long.valueOf(3), ((List<Object>)map1.get("some-array")).get(2));
    
        Object o2 = array.get(1);
        assertTrue(o2 instanceof Map);
        Map<String,Object> map2 = (Map<String,Object>)o2;
        assertEquals("http://www.uio.no", map2.get("900000"));
    }

}
