/* Copyright (c) 2007, University of Oslo, Norway
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
package org.vortikal.util.repository;

import junit.framework.TestCase;

public class URIUtilTest extends TestCase {
    
    private String[] invalidURIs = new String[] {null, "", "web", "/web//intranett", "/web/"};
    
    public void testGetParentURIIllegalArgument() {
        for (int i = 0; i < invalidURIs.length; i++) {
            checkGetParentURIFail(invalidURIs[i]);
        }
    }
    
    private void checkGetParentURIFail(String uri) {
        try {
            assertEquals("URI '" + uri + "' did not throw exception as expected", null, URIUtil.getParentURI(uri));
            fail();
        } catch (IllegalArgumentException iae) {
        }
    }
    
    public void testGetParentURI() {
        assertEquals(null, URIUtil.getParentURI("/"));
        assertEquals("/", URIUtil.getParentURI("/web"));
        assertEquals("/web", URIUtil.getParentURI("/web/intranett"));
    }
    
    public void testGetResourceNameIllegalArgument() {
        for (int i = 0; i < invalidURIs.length; i++) {
            checkGetResourceNameFail(invalidURIs[i]);
        }
    }
    
    private void checkGetResourceNameFail(String uri) {
        try {
            assertEquals("URI '" + uri + "' did not throw exception as expected", null, URIUtil.getResourceName(uri));
            fail();
        } catch (IllegalArgumentException iae) {
        }
    }
    
    public void testGetResourceName() {
        assertEquals("/", URIUtil.getResourceName("/"));
        assertEquals("web", URIUtil.getResourceName("/web"));
        assertEquals("intranett", URIUtil.getResourceName("/web/intranett"));
    }

    public void testVerifyEscaped() {
        assertFalse(URIUtil.isEscaped("http://www.uio.no/detteÆerikkeencoda"));
        assertFalse(URIUtil.isEscaped("http://www.uio.no/dette%erikkeencoda"));
        assertTrue(URIUtil.isEscaped("http://www.uio.no/dette%20erencoda"));
        assertTrue(URIUtil.isEscaped("http://www.uio.no/?"));

        assertFalse(URIUtil.isEscaped("http://www.uio.no/er dette encoda?"));

    }

    public void testGetAncestorOrSelfAtLevel() {
        assertEquals("/", URIUtil.getAncestorOrSelfAtLevel("/", 0));
        assertEquals("/", URIUtil.getAncestorOrSelfAtLevel("/la/di/da", 0));
        assertEquals("/lala/lala", URIUtil.getAncestorOrSelfAtLevel("/lala/lala/la", 2));
        assertEquals("/lala/lalala", URIUtil.getAncestorOrSelfAtLevel("/lala/lalala", 2));

        try {
            URIUtil.getAncestorOrSelfAtLevel("/", -1);
            fail();
        } catch (IllegalArgumentException e) {
            // Expected
        } catch (Exception e) {
            fail();
        }
        
        try {
            URIUtil.getAncestorOrSelfAtLevel("/la/di/da", 5);
            fail();
        } catch (IllegalArgumentException e) {
            // Expected
        } catch (Exception e) {
            fail();
        }

    }

    public void testIsUrl() {
        assertTrue(URIUtil.isUrl("http://www.uio.no/la"));
        assertTrue(URIUtil.isUrl("://"));
        assertFalse(URIUtil.isUrl(null));
        assertFalse(URIUtil.isUrl("/la/di/da"));
        assertFalse(URIUtil.isUrl("www.uio.no:8080/la/di/da"));
    }
    
}
