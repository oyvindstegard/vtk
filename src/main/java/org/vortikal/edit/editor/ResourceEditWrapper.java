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
package org.vortikal.edit.editor;

import java.util.HashMap;
import java.util.Map;

import org.vortikal.repository.resourcetype.PropertyTypeDefinition;

public class ResourceEditWrapper extends ResourceWrapper {

    public ResourceEditWrapper(ResourceWrapperManager resourceManager) {
        super(resourceManager);
    }

    private boolean contentChange = false;

    public boolean isContentChange() {
        return contentChange;
    }

    public void setContentChange(boolean contentChange) {
        this.contentChange = contentChange;
    }

    private boolean propChange = false;

    public boolean isPropChange() {
        return propChange;
    }

    public void setPropChange(boolean propChange) {
        this.propChange = propChange;
    }


    /* Errors */

    private Map<PropertyTypeDefinition, String> errors = new HashMap<PropertyTypeDefinition, String>();

    public void reject(PropertyTypeDefinition propDef, String code) {
        this.errors.put(propDef, code);
    }
    
    public String getError(PropertyTypeDefinition propDef) {
        return this.errors.get(propDef);
    }
    
    public boolean hasErrors() {
        return !this.errors.isEmpty();
    }
    
    public Map<PropertyTypeDefinition, String> getErrors() {
        return this.errors;
    }


    private boolean save = false;
    private boolean quit = false;

    public boolean isSave() {
        return this.save;
    }
    
    public void setSave(boolean save) {
        this.save = save;
    }

    public void setQuit(boolean quit) {
        this.quit = quit;
    }

    public boolean isQuit() {
        return quit;
    }

}
