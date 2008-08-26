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
package org.vortikal.repository.index.consistency;

import org.vortikal.repository.Path;
import org.vortikal.repository.PropertySetImpl;

/**
 * Invalid ACL inconsistency.
 * 
 * @author oyviste
 *
 */
public class InvalidACLInheritedFromInconsistency extends InvalidDataInconsistency {

    private int indexACL = -1;
    private int daoACL = -1;
    
    public InvalidACLInheritedFromInconsistency(Path uri, PropertySetImpl daoPropSet, 
                                                int indexACL, int daoACL) {
        super(uri, daoPropSet);
        this.indexACL = indexACL;
        this.daoACL = daoACL;
    }
    
    public boolean canRepair() {
        return true;
    }
    
    public String getDescription() {
        return "Invalid ACL inherited from inconsistency for index property set at URI '"
          + getUri() + "', indexACL = " + this.indexACL + ", daoACL = " + this.daoACL;
    }

    public String toString() {
        return "InvalidACLInheritedFromInconsistency[URI='" + getUri() + "', indexACL = " 
        + this.indexACL + ", daoACL = " + this.daoACL + "]"; 
    }

}
