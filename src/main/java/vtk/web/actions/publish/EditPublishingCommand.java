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
package vtk.web.actions.publish;

import vtk.repository.Resource;
import vtk.repository.resourcetype.Value;
import vtk.web.actions.UpdateCancelCommand;

public class EditPublishingCommand extends UpdateCancelCommand {

    private Resource resource;
    private String publishStatus;
    private String publishDate;
    private String unpublishDate;
    private Value publishDateValue;
    private Value unpublishDateValue;

    private String publishDateUpdateAction;
    private String unpublishDateUpdateAction;

    public EditPublishingCommand(String submitURL) {
        super(submitURL);
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public String getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(String publishDate) {
        this.publishDate = publishDate;
    }

    public String getUnpublishDate() {
        return unpublishDate;
    }

    public void setUnpublishDate(String unpublishDate) {
        this.unpublishDate = unpublishDate;
    }

    public Value getPublishDateValue() {
        return publishDateValue;
    }

    public void setPublishDateValue(Value publishDateValue) {
        this.publishDateValue = publishDateValue;
    }

    public Value getUnpublishDateValue() {
        return unpublishDateValue;
    }

    public void setUnpublishDateValue(Value unpublishDateValue) {
        this.unpublishDateValue = unpublishDateValue;
    }

    public String getPublishDateUpdateAction() {
        return publishDateUpdateAction;
    }

    public void setPublishDateUpdateAction(String publishDateUpdateAction) {
        this.publishDateUpdateAction = publishDateUpdateAction;
    }

    public String getUnpublishDateUpdateAction() {
        return unpublishDateUpdateAction;
    }

    public void setUnpublishDateUpdateAction(String unpublishDateUpdateAction) {
        this.unpublishDateUpdateAction = unpublishDateUpdateAction;
    }

    public String getPublishStatus() {
        return publishStatus;
    }

    public void setPublishStatus(String publishStatus) {
        this.publishStatus = publishStatus;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + resource.getURI() + "," 
                + publishStatus + "," + publishDate + "," + unpublishDate 
                + "," + publishDateValue + "," + unpublishDateValue + ","
                + unpublishDateUpdateAction + ")";
    }
    
}
