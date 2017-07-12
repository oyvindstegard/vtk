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
package vtk.web.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.Resource;
import vtk.security.Principal;


/**
 * NOTE: When used on fast link construction,
 * the first child assertion is used!
 * 
 * XXX: Needs typed assertions to deal with or-ing 
 * on link construction
 */
public class OrAssertion implements WebAssertion {

    private List<WebAssertion> assertions = new ArrayList<>();

    public void setAssertions(List<WebAssertion> assertions) {
        this.assertions = assertions;
    }
    
    @Override
    public URL processURL(URL url) {
        return assertions.get(0).processURL(url);
    }

    @Override
    public Optional<URL> processURL(URL url, Resource resource, Principal principal) {
        for (WebAssertion assertion : assertions) {
            // Process URL only for first matching assertion:
            Optional<URL> opt = assertion.processURL(url, resource, principal);
            if (opt.isPresent()) {
                return opt;
            }
        }
        return Optional.empty();
    }
    

    @Override
    public boolean matches(HttpServletRequest request, Resource resource, Principal principal) {
        for (WebAssertion assertion : assertions) {
            if (assertion.matches(request, resource, principal)) {
                return true;
            }
        }
        return false;
    }
    

    @Override
    public boolean conflicts(WebAssertion otherAssertion) {
        for (WebAssertion assertion : assertions) {
            if (!assertion.conflicts(otherAssertion)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return assertions
            .stream()
            .map(x -> x.toString())
            .collect(Collectors.joining(" or "));
    }

}
