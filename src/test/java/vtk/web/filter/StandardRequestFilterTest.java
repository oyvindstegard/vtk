/* Copyright (c) 2009, University of Oslo, Norway
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
package vtk.web.filter;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class StandardRequestFilterTest {
    
    @Test
    public void testFilterRequest() throws IOException, ServletException {

        StandardRequestFilter filter = new StandardRequestFilter();
        Map<String, String> replacements = new HashMap<>();
        replacements.put("\\+", "%2B");
        filter.setUrlReplacements(replacements);
        
        TestChain.filter(filter, new MockHttpServletRequest("GET", "/foo/bar"), 
                filtered -> assertEquals("/foo/bar", filtered.getRequestURI()));

        TestChain.filter(filter, new MockHttpServletRequest("GET", "/%20"),
                filtered -> assertEquals("/", filtered.getRequestURI()));

        TestChain.filter(filter, new MockHttpServletRequest("GET", "/foo/bar/file+2.txt"),
                filtered -> assertEquals("/foo/bar/file%2B2.txt", filtered.getRequestURI()));
        
        TestChain.filter(filter, new MockHttpServletRequest("GET", "/foo/bar/i am a file with spaces.txt"),
                filtered -> assertEquals("/foo/bar/i%20am%20a%20file%20with%20spaces.txt", filtered.getRequestURI()));

        TestChain.filter(filter, new MockHttpServletRequest("GET", "/"),
                filtered -> assertEquals("/", filtered.getRequestURI()));

        TestChain.filter(filter, new MockHttpServletRequest("GET", ""),
                filtered -> assertEquals("/", filtered.getRequestURI()));

        try {
            TestChain.filter(filter, new MockHttpServletRequest("OPTIONS", "%"), // Invalid request
                    filtered -> {});
            throw new IllegalStateException("Should not pass");
        }
        catch (IllegalArgumentException e) {
            // Expected
        }
    }

    private static class TestChain implements FilterChain {
        private Consumer<HttpServletRequest> consumer;
        private TestChain(Consumer<HttpServletRequest> consumer) {
            this.consumer = consumer;
        }
        
        public static void filter(StandardRequestFilter filter, HttpServletRequest request, 
                Consumer<HttpServletRequest> consumer) throws IOException, ServletException {
            TestChain ch = new TestChain(consumer);
            filter.doFilter(request, null, ch);
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response)
                throws IOException, ServletException {
            consumer.accept((HttpServletRequest) request);
        }
    }
}
