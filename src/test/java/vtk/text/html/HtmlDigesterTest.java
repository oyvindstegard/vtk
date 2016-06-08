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
package vtk.text.html;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;
import vtk.util.io.IO;

public class HtmlDigesterTest {

    private HtmlDigester htmlDigester;
    private String testHtml;

    @Before
    public void init() throws IOException {
        this.htmlDigester = new HtmlDigester();
        InputStream in = this.getClass().getResourceAsStream("test-html401strict.html");
        this.testHtml = IO.readString(in).perform();
    }

    @Test
    public void compress() {
        int startLength = this.testHtml.length();
        String result = this.htmlDigester.compress(this.testHtml);
        assertTrue(result.length() < startLength);
    }

    @Test
    public void truncateHtmlWithinLimitAfterCompress() {
        this.truncate(this.testHtml, 3500);
    }

    @Test
    public void truncateSimpleHtml() {
        this.truncate("<p>A paragraph with some <b>bold</b> content<p/>", 50, 10);
    }

    @Test
    public void truncateHtml() {
        this.truncate(this.testHtml, 1500, 1000);
    }

    private void truncate(String html, int startLimit, int endLimit) {
        for (int limit = startLimit; limit >= endLimit; limit--) {
            this.truncate(html, limit);
        }
    }

    private void truncate(String html, int limit) {
        String truncated = this.htmlDigester.truncateHtml(html, limit);
        assertNotNull(truncated);
        assertTrue(truncated.length() <= limit);
        // System.out.println(truncated + " " + truncated.length() + " (" +
        // limit + ")");
    }

}
