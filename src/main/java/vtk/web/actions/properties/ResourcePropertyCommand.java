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
package vtk.web.actions.properties;

import vtk.web.actions.UpdateCancelCommand;

public class ResourcePropertyCommand extends UpdateCancelCommand {
    
    private static final long serialVersionUID = -9031231358523068439L;
    private String namespace;
    private String name;
    private String value;

    public ResourcePropertyCommand(String namespace, String name,
                                   String value, String submitURL) {
        super(submitURL);
        this.namespace = namespace;
        this.name = name;
        this.value = value;
    }



    /**
     * Gets the value of namespace
     *
     * @return the value of namespace
     */
    public String getNamespace() {
        return this.namespace;
    }

    /**
     * Sets the value of namespace
     *
     * @param namespace Value to assign to this.namespace
     */
    public void setNamespace(String namespace)  {
        this.namespace = namespace;
    }

    /**
     * Gets the value of name
     *
     * @return the value of name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the value of name
     *
     * @param name Value to assign to this.name
     */
    public void setName(String name)  {
        this.name = name;
    }

    /**
     * Gets the value of value
     *
     * @return the value of value
     */
    public String getValue() {
        return this.value;
    }

    /**
     * Sets the value of value
     *
     * @param value Value to assign to this.value
     */
    public void setValue(String value)  {
        this.value = value;
    }

}
