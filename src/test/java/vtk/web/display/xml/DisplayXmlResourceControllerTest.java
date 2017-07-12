/* Copyright (c) 2006, University of Oslo, Norway
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
package vtk.web.display.xml;

import static org.junit.Assert.assertEquals;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import vtk.context.BaseContext;
import vtk.repository.Path;
import vtk.repository.Repository;
import vtk.repository.Resource;
import vtk.repository.store.DefaultPrincipalMetadataDAO;
import vtk.security.SecurityContext;
import vtk.web.RequestContext;


public class DisplayXmlResourceControllerTest {

    private String faqSchema = "http://www.uio.no/xsd/uio/faq/v001/faq.xsd";
    private HttpServletRequest request;
    private DisplayXmlResourceController controller;
    private final Path uri = Path.fromString("/hest.xml");
    private final String token = "";
    
    private Mockery context = new JUnit4Mockery();
    private final Resource mockResource = context.mock(Resource.class);
    private final Repository mockRepository = context.mock(Repository.class);

    @Before
    public void setUp() throws Exception {
        this.request = new MockHttpServletRequest();
        this.controller = new DisplayXmlResourceController();
        BaseContext.pushContext();
        SecurityContext securityContext = new SecurityContext(this.token, null);
        SecurityContext.setSecurityContext(securityContext);
        RequestContext requestContext = new RequestContext(
                request, securityContext, null, null, null, uri, null, false, false, true,
                mockRepository, new DefaultPrincipalMetadataDAO());
        RequestContext.setRequestContext(requestContext, request);
    }

    @Test
    public void lastModified() throws Exception {

        long lastModified;
        final Date lastModifiedExpected = new Date();
        
        context.checking(new Expectations() {{ oneOf(mockResource).isCollection(); will(returnValue(false)); }});
        context.checking(new Expectations() {{ oneOf(mockResource).getLastModified(); will(returnValue(lastModifiedExpected)); }});
        
        context.checking(new Expectations() {{ oneOf(mockRepository).retrieve(token, uri, true); will(returnValue(mockResource)); }});
        
        lastModified = this.controller.getLastModified(this.request);
        assertEquals(-1, lastModified);

        this.controller.setHandleLastModified(true);
        lastModified = this.controller.getLastModified(this.request);
        assertEquals(lastModifiedExpected.getTime(), lastModified);

    }
}

