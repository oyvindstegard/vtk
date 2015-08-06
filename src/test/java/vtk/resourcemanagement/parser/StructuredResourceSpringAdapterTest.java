/* Copyright (c) 2015, University of Oslo, Norway
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

import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import vtk.resourcemanagement.StructuredResourceDescription;
import vtk.resourcemanagement.StructuredResourceManager;


import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class StructuredResourceSpringAdapterTest {
    private StructuredResourceSpringAdapter springAdapter;
    private ResourceLoader resourceLoader;
    private StructuredResourceManager manager;

    @Before
    public void setUp() throws Exception {
        springAdapter = new StructuredResourceSpringAdapter();
        resourceLoader = new DefaultResourceLoader();
        manager = mock(StructuredResourceManager.class);

        springAdapter.setDefaultTypeDefinitionFiles(
                new String[]{"classpath:/vtk/beans/structured-resources-test.vrtx"}
        );
        springAdapter.setResourceLoader(resourceLoader);
        springAdapter.setStructuredResourceManager(manager);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_and_register_same_file_twice() throws Exception {
        springAdapter.afterPropertiesSet();
        doThrow(IllegalArgumentException.class).when(manager)
                .register(any(StructuredResourceDescription.class));
        springAdapter.parseAndRegister(
                resourceLoader.getResource("classpath:/vtk/beans/structured-resources-test.vrtx")
        );
    }
}
