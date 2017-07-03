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
package vtk.resourcemanagement;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;

import org.junit.BeforeClass;
import org.junit.Test;

import vtk.resourcemanagement.property.PropertyDescription;

public class StructuredResourceTest {
    // Dummy description, does nothing
    private static StructuredResourceDescription DUMMY_DESC;

    @BeforeClass
    public static void setUp() {
        DUMMY_DESC = new StructuredResourceDescription();
        DUMMY_DESC.setPropertyDescriptions(new ArrayList<PropertyDescription>());
    }

    @Test
    public void testCreateStructuredEvent() throws Exception {
        testCreate("structured-event");
    }

    @Test
    public void testCreatePerson() throws Exception {
        testCreate("person");
    }

    private void testCreate(String resourceTypeName) throws Exception {
        InputStream stream = this.getClass().getResourceAsStream(resourceTypeName.concat(".json"));
        StructuredResource sr = StructuredResource.create(DUMMY_DESC, stream);
        assertNotNull(sr);
        assertTrue(sr.getPropertyNames().size() > 0);
    }

}
