/* Copyright (c) 2005, 2008 University of Oslo, Norway
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
package org.vortikal.web.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.vortikal.repository.Resource;
import org.vortikal.security.Principal;


/**
 * A repository assertion that takes a list of other assertions,
 * applying them in order. If one of the sub-assertions match, this
 * assertion also matches.
 * 
 * XXX: Added andHack boolean property - switch to make assertion 'and' the sub
 * assertions
 * 
 */
public class RepositoryShortcutOrAssertion
  extends AbstractRepositoryAssertion implements InitializingBean {

    private List<RepositoryAssertion> assertions = new ArrayList<RepositoryAssertion>();
    private boolean andHack = false;

    public void afterPropertiesSet()
            throws Exception {
        if (this.assertions.size() == 0) {
            throw new BeanInitializationException("No assertions provided");
        }
    }

    public boolean conflicts(Assertion assertion) {
        return false;
    }
    
    public boolean matches(Resource resource, Principal principal) {
        for (RepositoryAssertion assertion: this.assertions) {
            if (assertion.matches(resource, principal)) {
                if (!this.andHack)
                    return true;
            } else {
                if (this.andHack)
                    return false;
            }
        }
        
        return this.andHack;
    }
    
    public void setAssertions(List<RepositoryAssertion> assertions) {
        this.assertions = assertions;
    }

    public void setAndHack(boolean andHack) {
        this.andHack = andHack;
    }
}
