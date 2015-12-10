/* Copyright (c) 2014, University of Oslo, Norway
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
package vtk.web.search;

import vtk.security.Principal;

public class EditInfo {
    private boolean isEditAuthorized;
    private boolean isEditLocked;
    private Principal lockedBy;
    
    public EditInfo(boolean isEditAuthorized, boolean isEditLocked, Principal lockedBy) {
        this.isEditAuthorized = isEditAuthorized;
        this.isEditLocked = isEditLocked;
        this.lockedBy = lockedBy;
    }
    
    public boolean isEditAuthorized() {
        return isEditAuthorized;
    }
    
    public boolean isEditLocked() {
        return isEditLocked;
    }
    
    public Principal getLockedBy() {
        return lockedBy;
    }

    public String getLockedByNameHref() {
        if (lockedBy != null) {
            String lockedByName = lockedBy.getName();
            String url = lockedBy.getURL();
            if (url != null) {
                lockedByName = "<a href=\"" + lockedBy.getURL() + "\">" + lockedBy.getDescription() + "</a>";
            }
            return lockedByName;
        }
        return "";
    }
}
