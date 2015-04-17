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
package vtk.web.service.manuallyapprove;

import java.io.Serializable;
import java.util.Date;
import org.apache.commons.lang.time.FastDateFormat;

import vtk.web.service.URL;

public class ManuallyApproveResource implements Serializable {

    private static final long serialVersionUID = -8296957318941539258L;

    private final String title;
    private final URL url;
    private final String source;
    private final Date publishDate;
    private boolean approved;

    public ManuallyApproveResource(String title, URL url, String source, Date publishDate, boolean approved) {
        this.title = title;
        this.url = url;
        this.source = source;
        this.publishDate = publishDate;
        this.approved = approved;
    }

    public String getTitle() {
        return title;
    }

    public URL getUrl() {
        return url;
    }

    public String getSource() {
        return source;
    }

    public Date getPublishDate() {
        return publishDate;
    }

    public String getPublishDateAsString() {
        return FastDateFormat.getInstance("yyyy-MM-dd").format(this.publishDate);
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    @Override
    public String toString() {
        return this.url.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ManuallyApproveResource)) {
            return false;
        }
        ManuallyApproveResource otherManuallyApproveResource = (ManuallyApproveResource) other;
        if (this.url.equals(otherManuallyApproveResource.getUrl())) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + (this.title != null ? this.title.hashCode() : 0);
        hash = 37 * hash + (this.url != null ? this.url.hashCode() : 0);
        hash = 37 * hash + (this.source != null ? this.source.hashCode() : 0);
        hash = 37 * hash + (this.approved ? 1 : 0);
        hash = 37 * hash + (this.publishDate != null ? this.publishDate.hashCode() : 0);
        return hash;
    }
}
