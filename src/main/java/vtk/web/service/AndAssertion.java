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


import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vtk.repository.Resource;
import vtk.security.Principal;


public class AndAssertion implements Assertion {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private Assertion[] assertions;


    public void setAssertions(Assertion[] assertions) {
        this.assertions = assertions;
    }
    
    @Override
    public void processURL(URL url) {
        for (int i = 0; i < this.assertions.length; i++) {
            this.assertions[i].processURL(url);
        }
    }
    
    @Override
    public boolean processURL(URL url, Resource resource, Principal principal, boolean match) {
        for (int i = 0; i < this.assertions.length; i++) {
            if (!this.assertions[i].processURL(url, resource, principal, match)) {
                return false;
            }
        }
        return true;
    }
    

    @Override
    public boolean matches(HttpServletRequest request, Resource resource, Principal principal) {
        for (int i = 0; i < this.assertions.length; i++) {
            if (!this.assertions[i].matches(request, resource, principal)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Assertion " + this.assertions[i]
                                 + " did not match, returning false");
                }
                return false;
            }
        }
        return true;
    }
    

    @Override
    public boolean conflicts(Assertion assertion) {
        for (int i = 0; i < this.assertions.length; i++) {
            if (this.assertions[i].conflicts(assertion)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < this.assertions.length; i++) {
            sb.append(this.assertions[i]);
            if (i < this.assertions.length - 1) sb.append(" and ");
        }
        sb.append(")");
        return sb.toString();
    }

}
