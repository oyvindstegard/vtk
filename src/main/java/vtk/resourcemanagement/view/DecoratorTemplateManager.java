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
package vtk.resourcemanagement.view;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import vtk.resourcemanagement.DisplayTemplate;
import vtk.resourcemanagement.StructuredResourceDescription;
import vtk.resourcemanagement.StructuredResourceManager;
import vtk.web.decorating.InputSource;
import vtk.web.decorating.AbstractCachingTemplateManager;

public class DecoratorTemplateManager extends AbstractCachingTemplateManager {

    private StructuredResourceManager resourceManager;
    
    protected InputSource resolve(final String name) {
        StructuredResourceDescription description = this.resourceManager.get(name);
        DisplayTemplate displayTemplate = description.getDisplayTemplate();
        while (displayTemplate == null) {
            String parent = description.getInheritsFrom();
            if (parent == null) {
                throw new IllegalStateException("Unable to resolve template source '" + name + "'");
            }
            description = this.resourceManager.get(parent);
            displayTemplate = description.getDisplayTemplate();
        }
        final DisplayTemplate result = displayTemplate;
        return new InputSource() {
            public String getCharacterEncoding() throws IOException {
                return "utf-8";
            }
            public String getID() {
                return name;
            }

            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(result.getTemplate().getBytes("utf-8"));
            }

            public long getLastModified() throws IOException {
                return result.getLastModified().getTime();
            }
        };
    }

    public void setResourceManager(StructuredResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }
}
