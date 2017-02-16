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
package vtk.util.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import vtk.util.text.Markdown.Flavor;

public class MarkdownTest {

    @Before
    public void setUp() throws Exception {
    }
    
    @Test
    public void testBasics() {
        String input = "This is *emphasized*";
        String output = Markdown.html(input, Flavor.DEFAULT);
        assertEquals("<p>This is <em>emphasized</em></p>\n", output);
    }
    
    @Test
    public void testTable() {
        String input = 
                "| A           | B     |       C |\n"
              + "| ----------- | ----- | ------- |\n"
              + "| xxx. x. xxx | yy:yy |  zz zzz |\n"
              + "| iii. i. iii | jj:jj |  kk kkk |\n"
              + "| lllll:             || **mmm** |\n";

        String output = Markdown.html(input, Flavor.DEFAULT);
        assertEquals(0, output.indexOf("<table>"));
        assertTrue(output.indexOf("</table>") > 0);
        assertTrue(output.indexOf("<thead>") > 0);
        assertTrue(output.indexOf("</thead>") > 0);
        assertTrue(output.indexOf("<tbody>") > 0);
        assertTrue(output.indexOf("</tbody>") > 0);
        assertTrue(output.indexOf("<tr><td> xxx. x. xxx </td>") > 0);
        assertTrue(output.indexOf("colspan=\"2\"") > 0);
    }
    
    @Test
    public void testAutoLinks() {
        String input = "Visit http://example.com";
        
        String output = Markdown.html(input, Flavor.DEFAULT);
        assertEquals(
                "<p>Visit <a href=\"http://example.com\">http://example.com</a></p>\n",
                output);
    }
    
    @Test
    public void testLineWraps() {
        String input = "This is some\ntext";
        
        String output = Markdown.html(input, Flavor.DEFAULT);
        assertEquals("<p>This is some\ntext</p>\n", output);

        output = Markdown.html(input, Flavor.GFM);
        assertEquals("<p>This is some<br />\ntext</p>\n", output);
    }
    
    @Test
    public void testStrikeThrough() {
        String input = "~~strikethrough~~";
        String output = Markdown.html(input, Flavor.DEFAULT);
        assertEquals("<p><del>strikethrough</del></p>\n", output);
    }
    
    @Test
    public void testTaskLists() {
        String input = 
                  "- [x] complete item\n"
                + "- [ ] incomplete item\n";
        String output = Markdown.html(input, Flavor.DEFAULT);
        assertTrue(output.indexOf("<li>[x] complete item</li>") > 0);
        assertTrue(output.indexOf("<li>[ ] incomplete item</li>") > 0);
        
        output = Markdown.html(input, Flavor.GFM);
        assertTrue(output.indexOf("<li class=\"task-list-item\"") > 0);
        assertTrue(output.indexOf("<input type=\"checkbox\" class=\"task-list-item\"") > 0);
        System.out.println(output);
        
    }
    
    
    @Test
    public void testFencedCodeBlocks() {
        String input = 
                "```\n"
              + "function test() {\n"
              + "    console.log('hello');\n"
              + "}\n"
              + "```\n";
        
        String output = Markdown.html(input, Flavor.DEFAULT);
        assertTrue(output.indexOf("<pre><code>") == 0);
        assertTrue(output.indexOf("function test() {") > 0);
        assertTrue(output.indexOf("</code></pre>") > 0);
    }
    

}
