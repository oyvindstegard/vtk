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
package vtk.web.display.collection.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import vtk.text.html.HtmlContent;
import vtk.text.html.HtmlFragment;
import vtk.util.text.TextUtils;

import org.apache.abdera.Abdera;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;

public class MessageListingAtomFeedGeneratorTest {

    private class MockHtmlContent implements HtmlContent {
        String content = null;

        public MockHtmlContent(String content) {
            this.content = content; 
        }
        
        @Override
        public String getContent() {
            return content;
        }
    }
    
    private MessageListingAtomFeedGenerator generator = new MessageListingAtomFeedGenerator();
    private Feed feed = null;
    private Entry entry = null;
    private StringWriter stringWriter = null;
    
    private HtmlFragment makeSummary(String summaryText) {
        HtmlContent htmlContent = new MockHtmlContent(summaryText);
        List<HtmlContent> contents = new ArrayList<HtmlContent>();
        contents.add(htmlContent);
        HtmlFragment summary = new HtmlFragment(contents);
        return summary;
    }
    
    private void doTest(String summaryText) throws Exception {
        feed.setTitle(TextUtils.removeUnprintables(summaryText));
        entry.setTitle(TextUtils.removeUnprintables(summaryText));
        HtmlFragment summary = makeSummary(summaryText);
        generator.setFeedEntrySummary(entry, summary);
        assertTrue("got here safely with no exception", true);
        feed.writeTo("prettyxml", stringWriter);
        assertTrue("xml should contain summary text", stringWriter.toString().contains(entry.getSummary()));
    }
    
    @Before
    public void beforeEachTest() {
        feed = Abdera.getInstance().newFeed();
        entry = feed.addEntry();
        stringWriter = new StringWriter();
    }
    
    @Test
    public void testWhenContentContainsControlCharactersThenUnknownShouldBeRemoved() throws Exception {
        doTest("back \b space");
        assertEquals("should not contain back space", -1, entry.getSummary().indexOf('\b')); 
    }

    @Test
    public void testWhenContentContainsControlCharactersThenUnknownShouldBeRemoved2() throws Exception {
        doTest("some \u000btext");
        assertEquals("should not contain control character", "some text", entry.getSummary()); 
    }

    @Test
    public void testWhenContentContainsLocalizedQuotesThenShouldBeAcceptedSinceTheyAreValidUnicode() throws Exception {
        String text = "\u201C some text \u201D";
        doTest(text);
        assertEquals("text should be unchanged", text, entry.getSummary()); 
    }

    @Test
    public void testWhenContentContainsLocalizedQuotesAsEntitiesShouldBeDecoded() throws Exception {
        String text = "&ldquo; some text &rdquo;";
        String text2 = "\u201C some text \u201D";
        doTest(text);
        assertEquals("entities should be decoded", text2, entry.getSummary());
    }
}
