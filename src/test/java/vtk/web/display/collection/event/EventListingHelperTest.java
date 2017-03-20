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
package vtk.web.display.collection.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Before;
import org.junit.Test;
import vtk.testing.mocktypes.MockResourceTypeTree;
import vtk.web.display.collection.event.EventListingHelper.SpecificDateSearchType;

public class EventListingHelperTest {

    private EventListingHelper eventListingHelper;
    private Mockery context = new JUnit4Mockery();
    private HttpServletRequest mockRequest;

    @Before
    public void init() throws Exception {
        this.eventListingHelper = new EventListingHelper();
        this.eventListingHelper.setStartPropDefPointer("resource:start-date");
        this.eventListingHelper.setEndPropDefPointer("resource:end-date");
        this.eventListingHelper.setResourceTypeTree(new MockResourceTypeTree());
        this.mockRequest = this.context.mock(HttpServletRequest.class);
    }

    @Test
    public void testGetSpecificDateSearchType() {
        this.testGetSpecificDateSearchType("2012-09-15", SpecificDateSearchType.Day);
        this.testGetSpecificDateSearchType("2012-09", SpecificDateSearchType.Month);
        this.testGetSpecificDateSearchType("2012", SpecificDateSearchType.Year);
    }

    @Test
    public void testGetSpecificDateSearchTypeInvalid() {
        context.checking(new Expectations() {
            {
                one(mockRequest).getParameter(EventListingHelper.REQUEST_PARAMETER_DATE);
                will(returnValue("invalid request date string"));
            }
        });
        SpecificDateSearchType searchType = this.eventListingHelper.getSpecificDateSearchType(this.mockRequest);
        assertNull("Expected result is not null", searchType);
    }

    private void testGetSpecificDateSearchType(final String requestDateString, final SpecificDateSearchType expected) {
        context.checking(new Expectations() {
            {
                one(mockRequest).getParameter(EventListingHelper.REQUEST_PARAMETER_DATE);
                will(returnValue(requestDateString));
            }
        });
        SpecificDateSearchType searchType = this.eventListingHelper.getSpecificDateSearchType(this.mockRequest);
        assertNotNull("Expected result is null", searchType);
        assertEquals("Wrong search type", expected, searchType);
    }

    @Test
    public void testGetSpecificSearchDate() {
        this.testGetSpecificSearchDate("2012-09-15", "yyyy-MM-dd");
        this.testGetSpecificSearchDate("2012-09", "yyyy-MM");
        this.testGetSpecificSearchDate("2012", "yyyy");
    }

    private void testGetSpecificSearchDate(final String requestDateString, final String pattern) {

        DateTimeFormatter dtf = DateTimeFormat.forPattern(pattern);
        long millis = dtf.parseMillis(requestDateString);
        Date expected = new Date(millis);

        context.checking(new Expectations() {
            {
                one(mockRequest).getParameter(EventListingHelper.REQUEST_PARAMETER_DATE);
                will(returnValue(requestDateString));
            }
        });
        Date date = this.eventListingHelper.getSpecificSearchDate(this.mockRequest);
        assertNotNull("Expected result is null", date);
        assertEquals("Wrong date", expected, date);
    }

}
