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
package vtk.repository.resourcetype;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.jmock.AbstractExpectations.returnValue;
import org.jmock.Expectations;
import org.jmock.auto.Mock;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import vtk.repository.Path;
import vtk.repository.Repository;

/**
 *
 */
public class ConfigurablePathPolicyAssertionTest {

    @Mock
    private Repository repository;

    @Rule
    public JUnitRuleMockery context = new JUnitRuleMockery();

    /**
     * Test of isCloudAllowed method, of class CloudPolicyManager.
     */
    @Test
    public void test() throws Exception {

        final String configFile = "/vrtx/cloud-policy-config.txt";
        final String config = "/ = ALLOW\n/vrtx = deny\n/vrtx/allow = allow";

        context.checking(new Expectations(){{
            oneOf(repository).getInputStream(null, Path.fromString(configFile), true);
            will(returnValue(new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8))));
        }});

        ConfigurablePathPolicyAssertion cm = new ConfigurablePathPolicyAssertion();
        cm.setRepository(repository);
        cm.setConfigurationFile(configFile);
        cm.loadConfiguration();

        assertTrue(cm.test(Path.fromString("/")));
        assertTrue(cm.test(Path.fromString("/foo")));
        assertFalse(cm.test(Path.fromString("/vrtx")));
        assertFalse(cm.test(Path.fromString("/vrtx/foo")));
        assertTrue(cm.test(Path.fromString("/vrtx/allow")));
    }

}
