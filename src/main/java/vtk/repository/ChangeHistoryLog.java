/* Copyright (c) 2004, University of Oslo, Norway
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vtk.security.Principal;

public class ChangeHistoryLog {

    private static Logger logger = LoggerFactory.getLogger(ChangeHistoryLog.class);

    private static Logger securityLogger = LoggerFactory.getLogger(
    		ChangeHistoryLog.class.getName() + ".security"); 
    
    public static void change(String operation, boolean security, Path uri, String description,
            boolean collection, Principal principal) {
        StringBuilder msg = new StringBuilder();
        msg.append(operation);
        msg.append(", uri: ").append(uri);
        msg.append(", desc: ").append(description);
        msg.append(", coll: ").append(collection);
        msg.append(", principal: ").append(principal);
        if (logger.isInfoEnabled()) {
            logger.info(msg.toString());
        }
        if (securityLogger.isInfoEnabled() && security) {
        	securityLogger.info(msg.toString());
        }
    }
}
