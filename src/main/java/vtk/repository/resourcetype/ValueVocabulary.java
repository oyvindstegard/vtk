/* Copyright (c) 2007, University of Oslo, Norway
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
package vtk.repository.resourcetype;

import java.util.List;

import org.springframework.beans.factory.InitializingBean;
import vtk.repository.Vocabulary;

public class ValueVocabulary implements Vocabulary<Value>, InitializingBean {

    private PropertyType.Type type = PropertyType.Type.STRING;
    private String messageSourceBaseName;
    private Value[] allowedValues;
    private ValueFormatter valueFormatter;
    
    public Value[] getAllowedValues() {
        return this.allowedValues;
    }

    public void setValues(List<Value> allowedValues) {
        this.allowedValues = allowedValues.toArray(new Value[allowedValues.size()]);
    }

    
    public void setMessageSourceBaseName(String messageSourceBaseName) {
        this.messageSourceBaseName = messageSourceBaseName;
    }

    public ValueFormatter getValueFormatter() {
        return this.valueFormatter;
    }

    
    public void setType(PropertyType.Type type) {
        this.type = type;
    }

    public void afterPropertiesSet() throws Exception {
        if (this.messageSourceBaseName != null) {
            this.valueFormatter = new MessageSourceValueFormatter(this.messageSourceBaseName, this.type);
        } 
    }
}
