/* Copyright (c) 2012,2013 University of Oslo, Norway
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
package vtk.web.display.linkcheck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import vtk.web.display.linkcheck.LinkChecker.LinkCheckRequest;
import vtk.web.display.linkcheck.LinkChecker.LinkCheckResult;
import vtk.web.display.linkcheck.LinkChecker.Status;
import vtk.web.service.URL;

public class LinkCheckerTest {

    @Rule
    public final JUnitRuleMockery context = new JUnitRuleMockery();

    private LinkChecker linkChecker = new LinkChecker();
    private Ehcache mockCache;

    @Before
    public void setUp() {
        mockCache = context.mock(Ehcache.class);
        linkChecker.setCache(mockCache);
    }

    @Test
    public void testValidateStatusOk() {

        URL base = URL.parse("http://www.uio.no/index.html");
        Status expectedStatus = Status.OK;
        String expectedMessage = null;

        List<TestLinkCheckObject> testOkLinks = new ArrayList<TestLinkCheckObject>();

        // http://httpstat.us is down as of Jan. 21. Remove while considering
        // alternatives
        // testOkLinks.add(new TestLinkCheckObject("http://httpstat.us/200",
        // base, expectedStatus, expectedMessage));
        // testOkLinks.add(new TestLinkCheckObject("http://httpstat.us/301",
        // base, expectedStatus, expectedMessage));
        // testOkLinks.add(new TestLinkCheckObject("http://httpstat.us/302",
        // base, expectedStatus, expectedMessage));
        // testOkLinks.add(new TestLinkCheckObject("http://httpstat.us/303",
        // base, expectedStatus, expectedMessage));

        testOkLinks.add(new TestLinkCheckObject("https://www.uio.no/om/index.html", base, expectedStatus,
                expectedMessage));
        testValidation(testOkLinks);

    }

    @Test
    public void testValidateStatusOkWhenRedirected() {
        List<TestLinkCheckObject> testRedirectLinks = new ArrayList<TestLinkCheckObject>();
        testRedirectLinks.add(new TestLinkCheckObject("/vrtx", URL.parse("http://www.uio.no/index.html"), Status.OK,
                null));
        testValidation(testRedirectLinks);
    }

    @Test
    public void testLinksWithSpaces() {

        final String unencodedAbsolute = "http://www.uio.no/a thing with spaces.pdf";
        final String alreadyEncodedAbsolute = "http://www.uio.no/a%20thing%20with%20spaces.pdf";
        final String unencodedHostRelative = "/a thing with spaces.pdf";
        final String encodedHostRelative = "/a%20thing%20with%20spaces.pdf";

        final String expectedEncodedUrl = "http://www.uio.no/a%20thing%20with%20spaces.pdf";

        context.checking(new Expectations() {
            {
                exactly(4).of(mockCache).get(expectedEncodedUrl);
                // Pretend the link was in cache to avoid hitting network
                will(onConsecutiveCalls(returnValue(new Element(expectedEncodedUrl, new LinkCheckResult(
                        unencodedAbsolute, Status.NOT_FOUND))), returnValue(new Element(expectedEncodedUrl,
                        new LinkCheckResult(alreadyEncodedAbsolute, Status.NOT_FOUND))), returnValue(new Element(
                        expectedEncodedUrl, new LinkCheckResult(unencodedHostRelative, Status.NOT_FOUND))),
                        returnValue(new Element(expectedEncodedUrl, new LinkCheckResult(encodedHostRelative,
                                Status.NOT_FOUND)))));
            }
        });

        URL base = URL.parse("http://www.uio.no/index.html");

        // Test unencoded absolute link
        LinkCheckRequest request = LinkCheckRequest.builder(unencodedAbsolute, base).build();
        linkChecker.validate(request);

        // Test already encoded absolute link
        request = LinkCheckRequest.builder(alreadyEncodedAbsolute, base).build();
        linkChecker.validate(request);

        // Test unencoded host relative link
        request = LinkCheckRequest.builder(unencodedHostRelative, base).build();
        linkChecker.validate(request);

        // Test already encoded host relative link
        request = LinkCheckRequest.builder(encodedHostRelative, base).build();
        linkChecker.validate(request);
    }

    @Test
    public void testValidateStatusNotFound() {

        URL base = URL.parse("http://www.usit.uio.no/index.html");
        Status expectedStatus = Status.NOT_FOUND;
        String expectedMessage = null;

        List<TestLinkCheckObject> testNotFoundLinks = new ArrayList<TestLinkCheckObject>();

        testNotFoundLinks.add(new TestLinkCheckObject("http://httpstat.us/404", base, expectedStatus, expectedMessage));
        testNotFoundLinks.add(new TestLinkCheckObject("http://httpstat.us/410", base, expectedStatus, expectedMessage));

        testNotFoundLinks.add(new TestLinkCheckObject("https://www.usit.uio.no/not-found.html", base, expectedStatus,
                expectedMessage));
        testValidation(testNotFoundLinks);
    }

    @Test
    public void testValidateStatusMalformed() {
        testValidation(new TestLinkCheckObject("http://foo.com:NaN", URL.parse("http://foo.com/bar"),
                Status.MALFORMED_URL, null));
    }

    @Test
    public void testIDN() throws java.net.MalformedURLException {
        context.checking(new Expectations() {
            {
                oneOf(mockCache).get("http://www.xn--l-4ga.com/#/BedsteBryggeprocess");
                will(returnValue(null));

                oneOf(mockCache).get("http://plain-ascii.com/foo/bar");
                will(returnValue(null));

                exactly(2).of(mockCache).put(with(any(Element.class)));
            }
        });
        LinkCheckRequest request = LinkCheckRequest.builder(
                "http://www.øl.com/#/BedsteBryggeprocess", 
                URL.parse("http://www.uio.no/index.html")).build();
        linkChecker.validate(request);
        
        request = LinkCheckRequest.builder("http://plain-ascii.com/foo/bar", 
                URL.parse("http://www.uio.no/index.html")).build();
        linkChecker.validate(request);
    }

    @Test
    public void testNonAscii() throws java.net.MalformedURLException {
        context.checking(new Expectations() {
            {
                oneOf(mockCache).get("http://www.example.com/a%E2%80%93b");
                will(returnValue(null));
                oneOf(mockCache).put(with(any(Element.class)));
            }
        });

        LinkCheckRequest request = LinkCheckRequest.builder(
                "http://www.example.com/a–b", // "a" <ndash> "b"
                URL.parse("http://www.uio.no/")).build();
        linkChecker.validate(request);
    }

    @Test
    public void testValidateStatusHeadNotFoundButGetOK() {
        // Test case taken from real issue VTK-3434
        URL base = URL.parse("http://www.usit.uio.no/index.html");
        String url = "http://www.washingtonpost.com/world/national-security/nsa-collects-millions-of-e-mail-address-books-globally/2013/10/14/8e58b5be-34f9-11e3-80c6-7e6dd8d22d8f_story.html";
        testValidation(new TestLinkCheckObject(url, base, Status.OK, null));
    }

    private void testValidation(List<TestLinkCheckObject> testLinks) {

        for (TestLinkCheckObject testLink : testLinks) {
            testValidation(testLink);
        }

    }

    private void testValidation(TestLinkCheckObject testLink) {

        final String href = testLink.testHref;
        final LinkCheckResult expected = new LinkCheckResult(testLink.testHref, testLink.expectedStatus,
                testLink.expectedReason);

        if (testLink.expectedStatus != Status.MALFORMED_URL) {
            context.checking(new Expectations() {
                {
                    oneOf(mockCache).get(with(any(String.class)));
                    will(returnValue(null));

                    oneOf(mockCache).put(with(any(Element.class)));
                }
            });
        }
        LinkCheckRequest request = LinkCheckRequest.builder(href, testLink.testBase).build();
        LinkCheckResult actual = linkChecker.validate(request);
        assertNotNull("Did not return expected link check result for '" + testLink.testHref + "'", actual);
        assertEquals("Input href not equal to link in result", href, actual.getLink());
        assertEquals("Did not return expected status for '" + testLink.testHref + "'", expected.getStatus(),
                actual.getStatus());

    }

    private class TestLinkCheckObject {

        String testHref;
        URL testBase;
        Status expectedStatus;
        String expectedReason;

        public TestLinkCheckObject(String testHref, URL testBase, Status expectedStatus, String expectedReason) {
            this.testHref = testHref;
            this.testBase = testBase;
            this.expectedStatus = expectedStatus;
            this.expectedReason = expectedReason;
        }

    }

}
