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
package vtk.web.actions.create;

import vtk.web.actions.UpdateCancelCommand;


public class CreateDocumentCommand extends UpdateCancelCommand {

    private static final long serialVersionUID = 2915071990084672433L;
    private String name = null;
    private String title = null;
    private String sourceURI = null;
    private boolean isIndex = false;
    private boolean isRecommended = false;
    
    
    public CreateDocumentCommand(String submitURL) {
        super(submitURL);
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name)  {
        this.name = name;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title)  {
        this.title = title;
    }
    
    public String getSourceURI() {
        return this.sourceURI;
    }

    public void setSourceURI(String sourceURI)  {
        this.sourceURI = sourceURI;
    }

    /**
     * Gets the value of isIndex
     * 
     * @return the value of isIndex
     */
    public boolean getIsIndex() {
        return this.isIndex;
    }

    /**
     * Sets the value of isIndex
     * 
     * @param isIndex
     *            Value to assign to this.isIndex
     */
    public void setIsIndex(boolean isIndex) {
        this.isIndex = isIndex;
    }

    public boolean getIsRecommended() {
        return this.isRecommended;
    }

    public void setIsRecommended(boolean isRecommended) {
        this.isRecommended = isRecommended;
    }

}
