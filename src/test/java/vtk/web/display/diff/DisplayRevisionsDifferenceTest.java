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
package vtk.web.display.diff;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.IOException;

import org.apache.axiom.attachments.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.Ignore;
import org.junit.Test;
import vtk.util.io.StreamUtil;
import org.xml.sax.InputSource;

/*
 * Test the difference computing library for visually displayed changes.
 */
public class DisplayRevisionsDifferenceTest {

    static {
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.Log4JLogger");
        System.setProperty("log4j.configuration", "log4j.test.xml");
    }

    private static Logger logger = LoggerFactory.getLogger(DisplayRevisionsDifferenceTest.class);
    
    @Test
    public void diffWhenTextChangeThenReportAsSuch() throws Exception {
        String html1 = "<html>Text1</html>";
        String html2 = "<html>Text2</html>";
        String result = diffHtml(html1, html2);
        assertFileEqualToResult("result1.txt", result);
    }
    
    @Test
    public void diffwhenSourceContainsOuterHtmlBodyTagsThenOnlyBodyInnerHtmlIsReturned() throws Exception {
        String html1 = "<html><body><div><p>Text1</p></div></body></html>";
        String html2 = "<html><body><div><p>Text2</p></div></body></html>";
        String result = diffHtml(html1, html2);
        assertFileEqualToResult("result2.txt", result);
    }

    @Test
    public void diffwhenSourceContainsOuterHtmlHeadBodyTagsThenOnlyBodyInnerHtmlIsReturned() throws Exception {
        String html1 = "<html><head></head><body><div><p>Text1</p></div></body></html>";
        String html2 = "<html><head></head><body><div><p>Text2</p></div></body></html>";
        String result = diffHtml(html1, html2);
        assertFileEqualToResult("result2.txt", result);
    }

    @Test
    public void diffwhenHeaderContainsTextTheTextShouldBeRemovedBeforeComparison() throws Exception {
        String html1 = "<html><head><title>TITLE A</title></head><body><div><p>Text1</p></div></body></html>";
        String html2 = "<html><head><title>TITLE B</title></head><body><div><p>Text2</p></div></body></html>";
        String result = diffHtml(html1, html2);
        assertFileEqualToResult("result4.txt", result);
    }

    @Test
    public void diffWhenChangingContainerTagTypeShouldBeDetectedAsSuch() throws Exception {
        String html1 = "<html><head></head><body><p>Text1</p></body></html>";
        String html2 = "<html><head></head><body><h1>Text1</h1></body></html>";
        String result = diffHtml(html1, html2);
        assertFileEqualToResult("result5.txt", result);
    }

    /**
     * Cause of stall is Sax parser trying to look up dtd-urls.
     * Will delay for ca 120 seconds before completing.
     */
    @Test
    @Ignore("LongRunning - pauses for 120 seconds due to a timeout")
    public void diffWhenNotUsingNekoThenLongTimeoutLookingForDtd() throws Exception {
        String result = diffHtml(
                getClass().getResourceAsStream("test-ver-v5.html"),
                getClass().getResourceAsStream("test-ver-v4.html"),
                false
            );
        assertNotEquals("tag", "", result);
    }

    /**
     * Cause of stall was Sax parser trying to look up dtd-urls.
     * Fixed by using Neko-parser instead.
     */
    @Test
    public void diffWhenUsingNekoThenDtdIsSkipped() throws Exception {
        String result = diffHtml(
                getClass().getResourceAsStream("test-ver-v5.html"),
                getClass().getResourceAsStream("test-ver-v4.html"),
                true
            );
        assertFileEqualToResult("result3.txt", result);
    }

    private void assertFileEqualToResult(String filename, String result) throws Exception {
        String expectedResult = fileContent(filename);
        assertEquals("diff markup", expectedResult, result);
    }

    private String fileContent(String filename) throws IOException {
        return StreamUtil.streamToString(getClass().getResourceAsStream(filename));
    }
  
    private String diffHtml(String contentA, String contentB) throws Exception {
        DifferenceEngine differ = new DifferenceEngine();
        String result = differ.diff(contentA, contentB);
        logger.debug(result);
        return result;
    }
    
    private String diffHtml(java.io.InputStream contentA, java.io.InputStream contentB, boolean useNeko) throws Exception {
        return diffHtml(new InputSource(contentA), new InputSource(contentB), useNeko);
    }

    private String diffHtml(InputSource contentA, InputSource contentB, boolean useNeko) throws Exception {
        DifferenceEngine differ = new DifferenceEngine();
        differ.setUseNeko(useNeko);
        String result = differ.diff(contentA, contentB);
        logger.debug(result);
        return result;
    }
    
}
