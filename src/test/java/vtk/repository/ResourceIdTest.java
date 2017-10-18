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
package vtk.repository;

import static junit.framework.TestCase.*;
import org.junit.Test;

/**
 *
 * @author oyvind
 */
public class ResourceIdTest {

    @Test
    public void fromString() {
        ResourceId id = ResourceId.fromString("vtkframework.org_1000");
        assertEquals("vtkframework.org", id.repositoryId().get());
        assertEquals(1000, id.numericId());
    }
    
    @Test
    public void fromString_2() {
        ResourceId id = ResourceId.fromString("1000");
        assertFalse(id.repositoryId().isPresent());
        assertEquals(1000, id.numericId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidRepositoryId() {
        ResourceId.fromString("_1000");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidRepositoryId_2() {
        ResourceId.fromString("abc1000");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidRepositoryId_3() {
        ResourceId.fromString("__1000");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidNumericId() {
        ResourceId.fromString("-1000");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidNumericId_2() {
        ResourceId.fromString("abc");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidNumericId_3() {
        new ResourceId(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidNumericId_4() {
        ResourceId.fromString("vtkframework.org_1000_");
    }
}
