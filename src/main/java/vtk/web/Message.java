/* Copyright (c) 2008, University of Oslo, Norway
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
package vtk.web;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;


public class Message {
    private HttpServletRequest request;
    private String identifier = "message";
    private String titleCode;
    private List<String> messageCodes = new ArrayList<>();

    public Message(HttpServletRequest request, String titleCode) {
        if (titleCode == null || "".equals(titleCode.trim())) {
            throw new IllegalArgumentException("Message code cannot be empty");
        }
        this.request = request;
        this.titleCode = titleCode;
    }

    public String getTitle() {
        org.springframework.web.servlet.support.RequestContext springRequestContext =
            new org.springframework.web.servlet.support.RequestContext(request);
        return springRequestContext.getMessage(this.titleCode, this.titleCode);
    }
    
    public void setIdentifier(String identifier) {
        if (identifier == null || "".equals(identifier.trim())) {
            throw new IllegalArgumentException("Identifier cannot be empty");
        }
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }
    
    public void addMessage(String message) {
        if (message == null || "".equals(message.trim())) {
            throw new IllegalArgumentException("Message cannot be empty");
        }
        this.messageCodes.add(message);
    }

    public List<String> getMessages() {
        org.springframework.web.servlet.support.RequestContext springRequestContext =
            new org.springframework.web.servlet.support.RequestContext(request);
        List<String> result = new ArrayList<>(); 
        for (String msgCode: this.messageCodes) {
            result.add(springRequestContext.getMessage(msgCode, msgCode));
        }
        return result;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("identifier: ").append(this.identifier);
        sb.append(", titleCode: ").append(this.titleCode);
        sb.append(", messageCodes: ").append(this.messageCodes);
        sb.append("}");
        return sb.toString();
    }
}
