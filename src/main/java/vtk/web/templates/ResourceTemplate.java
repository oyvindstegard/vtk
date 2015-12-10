/* Copyright (c) 2008, University of Oslo, Norway
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
package vtk.web.templates;

import vtk.repository.Path;

/**
 * Just a simple bean encapsulating the necessary info about a 
 * resource template.  
 *
 */
public class ResourceTemplate {

    private Path uri;
    private String title;
    private String name;
    private String resourceType;

    public ResourceTemplate(Path uri, String title, String name, String resourceType) {
        this.uri = uri;
        this.title = title;
        this.name = name;
        this.resourceType = resourceType;
    }
    
    public Path getUri() {
        return uri;
    }
    
    public String getTitle() {
        return title;
    }

    public String getName() {
        return name;
    }

    public String getResourceType() {
        return resourceType;
    }
    
    public String toString() {
        StringBuilder builder = new StringBuilder(
                                        ResourceTemplate.class.getSimpleName());
        builder.append("[").append("uri = ").append(this.uri);
        builder.append(", ").append("name = ").append(this.name);
        builder.append(", ").append("title = ").append(this.title);
        builder.append(", ").append("resourceType = ").append(this.resourceType).append("]");
        return builder.toString();
    }
}
