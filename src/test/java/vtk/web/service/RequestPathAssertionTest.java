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
package vtk.web.service;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.springframework.mock.web.MockHttpServletRequest;
import vtk.web.RequestContext;

/**
 *
 */
public class RequestPathAssertionTest {

    private final RequestContext rc;
    private final RequestPathAssertion assertion;

    public RequestPathAssertionTest() {
        rc = mock(RequestContext.class);
        assertion = new RequestPathAssertion();
    }

    @Test
    public void testMatches_exact() {
        assertion.setPath("/path");
        MockHttpServletRequest req = mockRequest(URL.parse("http://wwww/path"));

        // exact
        assertTrue(assertion.matches(req, null, null));
    }

    @Test
    public void testMatches_noMatch() {
        assertion.setPath("/path");
        MockHttpServletRequest req = mockRequest(URL.parse("http://wwww/foo"));

        // No match
        assertFalse(assertion.matches(req, null, null));
    }

    @Test
    public void testMatches_trailingSlash() {
        assertion.setPath("/path");
        MockHttpServletRequest req = mockRequest(URL.parse("http://wwww/path/"));

        // with trailing slash
        assertTrue(assertion.matches(req, null, null));
    }

    @Test
    public void testMatches_noTrailingSlash() {
        assertion.setPath("/path");
        assertion.setMatchTrailingSlash(false);

        MockHttpServletRequest req = mockRequest(URL.parse("http://www/path/"));

        // request with trailing slash should not match
        assertFalse(assertion.matches(req, null, null));
    }

    @Test
    public void testMatches_withDescendants_exact() {
        assertion.setPath("/path");
        assertion.setMatchDescendants(true);

        MockHttpServletRequest req = mockRequest(URL.parse("http://www/path"));

        // exact
        assertTrue(assertion.matches(req, null, null));
    }

    @Test
    public void testMatches_withDescendants_trailingSlash() {
        assertion.setPath("/path");
        assertion.setMatchDescendants(true);

        MockHttpServletRequest req = mockRequest(URL.parse("http://www/path/"));

        // should match request with exact path but trailing slash
        assertTrue(assertion.matches(req, null, null));
    }

    @Test
    public void testMatches_withDescendants_descendant() {
        assertion.setPath("/path");
        assertion.setMatchDescendants(true);

        MockHttpServletRequest req = mockRequest(URL.parse("http://www/path/a/b"));

        // should match request with exact path but trailing slash
        assertTrue(assertion.matches(req, null, null));
    }

    @Test
    public void testMatches_withDescendants_descendantWithTrailingSlash() {
        assertion.setPath("/path");
        assertion.setMatchDescendants(true);

        MockHttpServletRequest req = mockRequest(URL.parse("http://www/path/a/b/"));

        // should match request with exact path but trailing slash
        assertTrue(assertion.matches(req, null, null));
    }

    @Test
    public void testMatches_withDescendants_noMatch() {
        assertion.setPath("/path");
        assertion.setMatchDescendants(true);

        MockHttpServletRequest req = mockRequest(URL.parse("http://www/path2/a/b"));

        assertFalse(assertion.matches(req, null, null));
    }

    @Test
    public void testMatches_urlEncoding() {
        assertion.setPath("/a b");
        MockHttpServletRequest req = mockRequest(URL.parse("http://www/a%20b"));
        assertTrue(assertion.matches(req, null, null));
    }

    @Test
    public void testMatches_withDescendants_commonPrefixNoMatch() {
        assertion.setPath("/a");
        assertion.setMatchDescendants(true);

        MockHttpServletRequest req = mockRequest(URL.parse("http://www/ab"));

        assertFalse(assertion.matches(req, null, null));
    }

    @Test
    public void testUrlConstruct_collection() {
        assertion.setPath("/path");
        URL url = URL.parse("http://www/some/collection/?foo=bar");

        assertEquals("http://www/path/?foo=bar", assertion.processURL(url).toString());
    }

    @Test
    public void testUrlConstruct_other() {
        assertion.setPath("/path");
        URL url = URL.parse("http://www/some/file?foo=bar");

        assertEquals("http://www/path?foo=bar", assertion.processURL(url).toString());
    }

    @Test
    public void testConflicts_equality() {
        assertion.setPath("/path");

        RequestPathAssertion other = new RequestPathAssertion();
        other.setPath("/path");

        // Equal paths should never conflict, since they match the same thing.
        assertFalse(assertion.conflicts(other));
    }

    @Test
    public void testConflicts_descendants() {
        assertion.setPath("/a");
        assertion.setMatchDescendants(true);

        RequestPathAssertion other = new RequestPathAssertion();
        other.setPath("/b");

        assertTrue(assertion.conflicts(other));
        assertTrue(other.conflicts(assertion));
    }

    @Test
    public void testConflicts_descendants2() {
        assertion.setPath("/a");

        RequestPathAssertion other = new RequestPathAssertion();
        other.setPath("/b");
        other.setMatchDescendants(true);

        assertTrue(assertion.conflicts(other));
        assertTrue(other.conflicts(assertion));
    }

    @Test
    public void testConflicts_descendants3() {
        assertion.setPath("/a");
        assertion.setMatchDescendants(true);

        RequestPathAssertion other = new RequestPathAssertion();
        other.setPath("/b");
        other.setMatchDescendants(true);

        assertTrue(assertion.conflicts(other));
        assertTrue(other.conflicts(assertion));
    }

    @Test
    public void testConflicts_descendants4() {
        assertion.setPath("/a/b");
        assertion.setMatchDescendants(true);

        RequestPathAssertion other = new RequestPathAssertion();
        other.setPath("/a/b/c");
        other.setMatchDescendants(true);

        assertFalse(assertion.conflicts(other));
        assertFalse(other.conflicts(assertion));
    }

    @Test
    public void testConflicts_descendants5() {
        assertion.setPath("/a/b/c");
        assertion.setMatchDescendants(true);

        RequestPathAssertion other = new RequestPathAssertion();
        other.setPath("/a/b");
        other.setMatchDescendants(true);

        assertFalse(assertion.conflicts(other));
        assertFalse(other.conflicts(assertion));
    }

    private MockHttpServletRequest mockRequest(URL url) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", url.getPath() + (url.isCollection() ? "/" : ""));
        RequestContext.setRequestContext(rc, req);
        when(rc.getRequestURL()).thenReturn(url);
        return req;
    }

}
