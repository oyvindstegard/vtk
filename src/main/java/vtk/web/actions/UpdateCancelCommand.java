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
package vtk.web.actions;

import java.io.Serializable;

/**
 * Utility base class for form command objects that have 'Update' and
 * 'Cancel' actions.
 */
public class UpdateCancelCommand implements Serializable {

    private static final long serialVersionUID = -889547323626097995L;
    private String updateAction = null;
    private String cancelAction = null;
    private String updateViewAction = null;

    private String submitURL;
    private boolean done = false;


    /**
     * Creates a new <code>UpdateCancelCommand</code> instance.
     *
     * @param submitURL the URL of the form submission.
     */
    public UpdateCancelCommand(String submitURL) {
        this.submitURL = submitURL;
    }
    
    /**
     * Creates a new <code>UpdateCancelCommand</code> instance.
     */
    public UpdateCancelCommand(){
    	
    }

    /**
     * Gets the value of saveAction
     *
     * @return the value of saveAction
     */
    public String getUpdateAction() {
        return this.updateAction;
    }

    /**
     * Sets the value of saveAction
     *
     * @param updateAction Value to assign to this.updateAction
     */
    public void setUpdateAction(String updateAction) {
        this.updateAction = updateAction;
    }

    /**
     * Gets the value of cancelAction
     *
     * @return the value of cancelAction
     */
    public String getCancelAction() {
        return this.cancelAction;
    }

    /**
     * Sets the value of cancelAction
     *
     * @param cancelAction Value to assign to this.cancelAction
     */
    public void setCancelAction(String cancelAction) {
        this.cancelAction = cancelAction;
    }
    
    /**
     * Gets the value of updateViewAction
     *
     * @return the value of updateViewAction
     */
    public String getUpdateViewAction() {
        return updateViewAction;
    }

    /**
     * Sets the value of updateViewAction
     *
     * @param updateViewAction Value to assign to this.updateViewAction
     */
    public void setUpdateViewAction(String updateViewAction) {
        this.updateViewAction = updateViewAction;
    }

    /**
     * Gets the value of submitURL
     *
     * @return the value of submitURL
     */
    public String getSubmitURL() {
        return this.submitURL;
    }


    /**
     * Sets the value of submitURL
     *
     * @param submitURL Value to assign to this.submitURL
     */
    public void setSubmitURL(String submitURL)  {
        this.submitURL = submitURL;
    }

    
    /**
     * @return Returns the done.
     */
    public boolean isDone() {
        return this.done;
    }
    

    /**
     * @param done The done to set.
     */
    public void setDone(boolean done) {
        this.done = done;
    }
}
