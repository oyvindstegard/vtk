/* Copyright (c) 2010, University of Oslo, Norway
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
package org.vortikal.web.actions.trashcan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.vortikal.repository.RecoverableResource;
import org.vortikal.repository.Resource;
import org.vortikal.web.actions.UpdateCancelCommand;

public class TrashCanCommand extends UpdateCancelCommand {

    private String recoverAction;
    private String deletePermanentAction;

    private Resource parentResource;
    private List<TrashCanObject> trashCanObjects = new ArrayList<TrashCanObject>();
    private Map<String, TrashCanSortLink> sortLinks = new HashMap<String, TrashCanSortLink>();

    public String getRecoverAction() {
        return recoverAction;
    }

    public void setRecoverAction(String recoverAction) {
        this.recoverAction = recoverAction;
    }

    public String getDeletePermanentAction() {
        return deletePermanentAction;
    }

    public void setDeletePermanentAction(String deletePermanentAction) {
        this.deletePermanentAction = deletePermanentAction;
    }

    public List<TrashCanObject> getTrashCanObjects() {
        return trashCanObjects;
    }

    public void setTrashCanObjects(List<TrashCanObject> trashCanObjects) {
        this.trashCanObjects = trashCanObjects;
    }

    public TrashCanCommand(String submitURL, Resource parentResource) {
        super(submitURL);
        this.parentResource = parentResource;
    }

    public Resource getParentResource() {
        return parentResource;
    }

    public Map<String, TrashCanSortLink> getSortLinks() {
        return sortLinks;
    }

    public void setSortLinks(Map<String, TrashCanSortLink> sortLinks) {
        this.sortLinks = sortLinks;
    }

    public boolean hasSelectedObjectsForRecovery() {
        if (this.trashCanObjects == null || this.trashCanObjects.size() == 0) {
            return false;
        }
        for (TrashCanObject tco : this.trashCanObjects) {
            if (tco.isSelectedForRecovery()) {
                return true;
            }
        }
        return false;
    }

    public List<RecoverableResource> getSelectedResources() {
        List<RecoverableResource> result = new ArrayList<RecoverableResource>();
        if (this.trashCanObjects != null && this.trashCanObjects.size() > 0) {
            for (TrashCanObject tco : this.trashCanObjects) {
                if (tco.isSelectedForRecovery()) {
                    result.add(tco.getRecoverableResource());
                }
            }
        }
        return result;
    }

    public boolean isValidAction() {
        if (this.getRecoverAction() == null && this.getDeletePermanentAction() == null) {
            return false;
        }
        return true;
    }

}
