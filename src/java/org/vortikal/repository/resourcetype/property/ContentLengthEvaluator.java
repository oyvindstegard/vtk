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
package org.vortikal.repository.resourcetype.property;

import java.util.Date;

import org.vortikal.repository.Property;
import org.vortikal.repository.PropertySet;
import org.vortikal.repository.resourcetype.Content;
import org.vortikal.repository.resourcetype.ContentModificationPropertyEvaluator;
import org.vortikal.repository.resourcetype.CreatePropertyEvaluator;
import org.vortikal.security.Principal;

/**
 * Evaluate contentLength.
 */
public class ContentLengthEvaluator implements CreatePropertyEvaluator, 
                                       ContentModificationPropertyEvaluator {

    public boolean contentModification(Principal principal, 
                                       Property property, 
                                       PropertySet ancestorPropertySet, 
                                       Content content, Date time) 
        throws PropertyEvaluationException {
        

        if (content == null) {
            // Shouldn't happen, the content-length property evaluator should not be called
            // for collections
            throw new 
                PropertyEvaluationException("Cannot evaluate content-length, content was null");
        }
        
        long length;
        try {
            length = (long) ((byte[]) content.getContentRepresentation(byte[].class)).length;
        } catch (Exception e) {
            throw new PropertyEvaluationException(
                    "Unable to get content length: " + e.getMessage());
        }

        property.setLongValue(length);
        
        return true;
    }

    public boolean create(Principal principal, 
                          Property property, 
                          PropertySet ancestorPropertySet, 
                          boolean isCollection, Date time)
            throws PropertyEvaluationException {
        
        if (isCollection) {
            throw new PropertyEvaluationException("Content length cannot be evaluated for collections.");
        }
        
        property.setLongValue(0);
        
        return true;
    }
    

}
