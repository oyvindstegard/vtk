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
package vtk.repository.content;

import java.io.InputStream;

import net.sf.json.JSONObject;

import vtk.util.io.StreamUtil;

/**
 * Content factory for <code>net.sf.json.JSONObject</code> objects.
 */
public class JSONObjectContentFactory implements ContentFactory {

    private int maxLength = 10000000;
    
    @Override
    public Class<?>[] getRepresentationClasses() {
        return new Class[] {JSONObject.class};
    }
    
    @Override
    public JSONObject getContentRepresentation(Class clazz,  InputStream content) throws Exception {
        if (clazz != JSONObject.class) {
            throw new UnsupportedContentRepresentation("Unsupported representation: " + clazz);
        }
        
        byte[] buffer = StreamUtil.readInputStream(content, this.maxLength + 1);
        if (buffer.length > this.maxLength) {
            throw new Exception("Unable to parse content: maximum size exceeded: " 
                    + this.maxLength);
        }
        String s = new String(buffer, "utf-8");    
        return JSONObject.fromObject(s);
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }


}
