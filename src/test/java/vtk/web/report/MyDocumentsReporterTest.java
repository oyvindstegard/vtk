/* Copyright (c) 2013, University of Oslo, Norway
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
package vtk.web.report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import vtk.repository.Path;
import vtk.repository.store.DefaultPrincipalMetadataDAO;
import vtk.security.Principal.Type;
import vtk.security.PrincipalImpl;
import vtk.security.SecurityContext;
import vtk.web.RequestContext;

public class MyDocumentsReporterTest {

    @Test
    public void createRequest() {
        MockHttpServletRequest request = newRequest("unknown");
        assertEquals("req param", "unknown", request.getParameter(MyDocumentsReporter.REPORT_SUB_TYPE_REQ_ARG));
    }

    @Test
    public void whenAlwaysShouldReturnSubTypeInformationInMap() {
        HttpServletRequest request = newRequest("directacl");
        MyDocumentsReporter myReporter = new MyDocumentsReporter();
        Map<String, Object> map = new HashMap<>();
        myReporter.addToMap(map, request);
        @SuppressWarnings("unchecked")
        ArrayList<MyDocumentsReporter.ReportSubType> subTypes = (ArrayList<MyDocumentsReporter.ReportSubType>)map.get(MyDocumentsReporter.REPORT_SUB_TYPE_MAP_KEY);
        assertNotNull("map should contain subtypes", subTypes);
        assertEquals("isactive #1", false, subTypes.get(0).isActive());
        assertEquals("name #1", "createdby", subTypes.get(0).getName());
        assertEquals("url #1", "http://localhost:9322/?vrtx=admin&mode=report&report-type=my-documents&report-subtype=createdby", subTypes.get(0).getUrl());
        assertEquals("isactive #2", true, subTypes.get(1).isActive());
        assertEquals("name #2", "directacl", subTypes.get(1).getName());
        assertEquals("url #2", "http://localhost:9322/?vrtx=admin&mode=report&report-type=my-documents&report-subtype=directacl", subTypes.get(1).getUrl());
    }

    @Test
    public void generateUrlWhenNoneBeforeThenSetNew() {
        HttpServletRequest request = newRequest(null);
        MyDocumentsReporter myReporter = new MyDocumentsReporter();
        String url = myReporter.generateUrl(request, "directacl");
        assertEquals("http://localhost:9322/?vrtx=admin&mode=report&report-type=my-documents&report-subtype=directacl", url);
    }

    @Test
    public void generateUrlWhenSameThenNoChange() {
        HttpServletRequest request = newRequest("directacl");
        MyDocumentsReporter myReporter = new MyDocumentsReporter();
        String url = myReporter.generateUrl(request, "directacl");
        assertEquals("http://localhost:9322/?vrtx=admin&mode=report&report-type=my-documents&report-subtype=directacl", url);
    }

    @Test
    public void generateUrlWhenNewIsDifferentThenReplaceWithNew() {
        HttpServletRequest request = newRequest("createdby");
        MyDocumentsReporter myReporter = new MyDocumentsReporter();
        String url = myReporter.generateUrl(request, "directacl");
        assertEquals("http://localhost:9322/?vrtx=admin&mode=report&report-type=my-documents&report-subtype=directacl", url);
    }

    @Test
    public void nameOfEnumValue() {
        assertEquals("DirectAcl", MyDocumentsReporter.ReportSubTypeEnum.DirectAcl.name());
    }

    @Test
    public void whenSubTypeNotGivenShouldResultInStandardReport() {
        HttpServletRequest request = newRequest(null);
        MyDocumentsReporter myReporter = new MyDocumentsReporter();
        assertSubType("not-given", MyDocumentsReporter.ReportSubTypeEnum.CreatedBy, myReporter.guessSubType(request));
    }

    @Test
    public void whenSubTypeUnknownShouldResultInStandardReport() {
        HttpServletRequest request = newRequest("xxx");
        MyDocumentsReporter myReporter = new MyDocumentsReporter();
        assertSubType("unknown", MyDocumentsReporter.ReportSubTypeEnum.CreatedBy, myReporter.guessSubType(request));
    }

    @Test
    public void testWhenSubTypeGivenAsCreatedByShouldResultInCreatedBy() {
        HttpServletRequest request = newRequest("createdby");
        MyDocumentsReporter myReporter = new MyDocumentsReporter();
        assertSubType("createdby", MyDocumentsReporter.ReportSubTypeEnum.CreatedBy, myReporter.guessSubType(request));
    }

    @Test
    public void whenSubTypeGivenAsDirectAclShouldResultInDirectAcl() {
        HttpServletRequest request = newRequest("directacl");
        MyDocumentsReporter myReporter = new MyDocumentsReporter();
        assertSubType("directacl", MyDocumentsReporter.ReportSubTypeEnum.DirectAcl, myReporter.guessSubType(request));
    }

    private void assertSubType(String message, MyDocumentsReporter.ReportSubTypeEnum expected, MyDocumentsReporter.ReportSubTypeEnum actual) {
        assertTrue(message, expected == actual);
    }
    

    private MockHttpServletRequest newRequest(String subType) {
        String url = "/?vrtx=admin&mode=report&report-type=my-documents";
        if (subType != null) {
            url += String.format("&%s=%s", MyDocumentsReporter.REPORT_SUB_TYPE_REQ_ARG, subType);
        }
        String userToken = "userToken";
        PrincipalImpl user = new PrincipalImpl("user", Type.USER);
        SecurityContext securityContext = new SecurityContext(userToken, user);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", url);
        SecurityContext.setSecurityContext(securityContext, request);
        RequestContext requestContext = new RequestContext(request, securityContext,
                null, null, null, Path.ROOT, null, false,
                false, true, null, new DefaultPrincipalMetadataDAO());
        RequestContext.setRequestContext(requestContext, request);
        request.setServerName("localhost");
        request.setServerPort(9322);
        request.addParameter("mode", "report");
        request.addParameter("report-type", "my-documents");
        if (subType != null) {
            request.addParameter(MyDocumentsReporter.REPORT_SUB_TYPE_REQ_ARG, subType);
        }
        return request;
    }

}
