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
package vtk.repository.search.preprocessor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.servlet.http.HttpServletRequest;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import vtk.context.BaseContext;
import vtk.repository.Path;
import vtk.repository.store.DefaultPrincipalMetadataDAO;
import vtk.web.RequestContext;
import vtk.web.service.Service;
import vtk.web.service.ServiceImpl;

public class CurrentDepthExpressionEvaluatorTest {

    HttpServletRequest request = new MockHttpServletRequest();
    Service service = new ServiceImpl();

    CurrentDepthExpressionEvaluator evaluator = 
        new CurrentDepthExpressionEvaluator();

    @Test
    public void matches() {
        assertTrue(evaluator.matches("currentDepth"));
        assertTrue(evaluator.matches("currentDepth+2"));
        assertTrue(evaluator.matches("currentDepth-12"));
        
        assertFalse(evaluator.matches(" currentDepth"));
        assertFalse(evaluator.matches("currentDepth+2 "));
        assertFalse(evaluator.matches("currentDepth+ 2"));
        assertFalse(evaluator.matches("currentDepth -12"));
    }
    
    @Test
    public void simpleEvaluation() {
        assertEquals(0, getDepth("/"));
        assertEquals(1, getDepth("/lala"));
        assertEquals(3, getDepth("/la/di/da"));
    }
    
    @Test
    public void relativeEvaluation() {
        assertEquals(0, getDepth("currentDepth-1", "/lala"));
        assertEquals(13, getDepth("currentDepth+10", "/lala/al/la"));
    }

    private int getDepth(String uri) {
        return getDepth("currentDepth", uri);
    }
    
    private int getDepth(String token, String uri) {

        BaseContext.pushContext();

        RequestContext.setRequestContext(
                new RequestContext(request, null, service, null, Path.fromString(uri),
                        null, false, false, true, null, new DefaultPrincipalMetadataDAO()));
        String s = evaluator.evaluate(token);

        BaseContext.popContext();

        return Integer.parseInt(s);
    }
}
