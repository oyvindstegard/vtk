/* Copyright (c) 2014,2015 University of Oslo, Norway
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
package vtk.resourcemanagement.parser;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.Test;
import vtk.resourcemanagement.StructuredResourceDescription;
import vtk.resourcemanagement.property.PropertyDescription;

public class CourseScheduleResourceTypeDefinitionTest extends StructuredResourceParserTest {

    @Test
    public void testCourseScheduleResourceType() throws IOException {

        StructuredResourceDescription courseSchedule = this.getNodeWithName("course-schedule");
        assertNotNull(courseSchedule);

        String inheritsFrom = courseSchedule.getInheritsFrom();
        assertNull(inheritsFrom);

        // Properties
        List<PropertyDescription> properties = courseSchedule.getPropertyDescriptions();
        assertNotNull(properties);

        PropertyDescription scheduleContentPropDesc = null;
        for (PropertyDescription pd : properties) {
            if (pd.getName().equals("schedule-content")) {
                scheduleContentPropDesc = pd;
                break;
            }
        }
        assertNotNull(scheduleContentPropDesc);

        assertTrue(scheduleContentPropDesc.getType().equals(ParserConstants.PROPTYPE_JSON));

    }

}
