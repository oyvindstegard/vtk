/* Copyright (c) 2005, University of Oslo, Norway
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
package org.vortikal.webdav.header.ifheader;

import org.apache.log4j.BasicConfigurator;
import org.jmock.Mock;
import org.jmock.MockObjectTestCase;
import org.springframework.mock.web.MockHttpServletRequest;
import org.vortikal.repository.Resource;
import org.vortikal.webdav.ifheader.IfMatchHeader;
import org.vortikal.webdav.ifheader.IfNoneMatchHeader;

public class IfMatchTestCase extends MockObjectTestCase {

    private Resource resource;
    
    private final String etag = "\"I am an ETag\"";
    private final String anotherEtag = "\"I am another ETag\"";

    static {
        BasicConfigurator.configure();
    }
    
    protected void setUp() throws Exception {
        super.setUp();
                
        Mock mockResource = mock(Resource.class);
        mockResource.expects(atLeastOnce()).method("getEtag").withNoArguments().will(returnValue(this.etag));
        this.resource = (Resource) mockResource.proxy();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }
    
    public void testCorrectEtag() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("If-Match", this.etag);
        IfMatchHeader ifMatchHeader = new IfMatchHeader(request);
        assertTrue(ifMatchHeader.matches(this.resource));
    }
    
    public void testWrongEtag() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("If-Match", this.anotherEtag);
        IfMatchHeader ifMatchHeader = new IfMatchHeader(request);
        assertFalse(ifMatchHeader.matches(this.resource));
    }
    
    public void testAllEtag() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("If-Match", "*");
        IfNoneMatchHeader ifNoneMatchHeader = new IfNoneMatchHeader(request);
        assertTrue(ifNoneMatchHeader.matches(this.resource));
        this.resource.getEtag(); //to make to mock object happy
    }
    
    public void testNoEtag() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        IfMatchHeader ifMatchHeader = new IfMatchHeader(request);
        assertTrue(ifMatchHeader.matches(this.resource));
        this.resource.getEtag(); //to make to mock object happy
    }


}