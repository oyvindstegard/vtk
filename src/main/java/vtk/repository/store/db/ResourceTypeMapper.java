/* Copyright (c) 2015, University of Oslo, Norway
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
package vtk.repository.store.db;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import vtk.repository.Path;
import vtk.repository.ResourceTypeTree;
import vtk.repository.resourcetype.PrimaryResourceTypeDefinition;

public final class ResourceTypeMapper {
    private ResourceTypeTree resourceTypeTree;

    public ResourceTypeMapper(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }
    
    public String resolveResourceType(String input) {
        Path path = input.startsWith("/") ? 
                Path.fromString(input) : legacyMappings.get(input);
        if (path == null) return input;
        
        while (!path.isRoot()) {
            String name = path.getName();
            try {
                resourceTypeTree.getResourceTypeDefinitionByName(name);
                return name;
            } 
            catch (Throwable t) {
                path = path.getParent();
            }
        }
        return input;
    }
    
    public String generateResourceType(String input) {
        List<String> list = new ArrayList<String>();
        PrimaryResourceTypeDefinition def = null;
        try {
            def = (PrimaryResourceTypeDefinition)
                    resourceTypeTree.getResourceTypeDefinitionByName(input);
        } catch (Throwable t) {  }
        while (def != null) {
            list.add(0, def.getName());
            def = def.getParentTypeDefinition();
        }
        if (list.isEmpty()) return input;
        Path path = Path.ROOT;
        for (String s: list) path = path.extend(s);
        return path.toString();
        
    }
    
    // Types snapshot
    private static final String[] legacyTypes = new String[] {
        "/resource/collection",
        "/resource/collection/article-listing",
        "/resource/collection/audio-video-listing",
        "/resource/collection/blog-listing",
        "/resource/collection/employee-listing",
        "/resource/collection/event-listing",
        "/resource/collection/image-listing",
        "/resource/collection/master-listing",
        "/resource/collection/message-listing",
        "/resource/collection/person-listing",
        "/resource/collection/project-listing",
        "/resource/collection/research-group-listing",
        "/resource/file",
        "/resource/file/audio",
        "/resource/file/image",
        "/resource/file/odf",
        "/resource/file/odf/odb",
        "/resource/file/odf/odg",
        "/resource/file/odf/odp",
        "/resource/file/odf/ods",
        "/resource/file/odf/odt",
        "/resource/file/ooxml",
        "/resource/file/ooxml/doc",
        "/resource/file/ooxml/ppt",
        "/resource/file/ooxml/xls",
        "/resource/file/pdf",
        "/resource/file/text",
        "/resource/file/text/apt-resource",
        "/resource/file/text/html",
        "/resource/file/text/html/xhtml10",
        "/resource/file/text/html/xhtml10/xhtml10strict",
        "/resource/file/text/html/xhtml10/xhtml10trans",
        "/resource/file/text/html/xhtml10/xhtml10trans/document",
        "/resource/file/text/html/xhtml10/xhtml10trans/document/article",
        "/resource/file/text/html/xhtml10/xhtml10trans/document/event",
        "/resource/file/text/json-resource",
        "/resource/file/text/json-resource/managed-json-resource",
        "/resource/file/text/json-resource/managed-json-resource/boxes",
        "/resource/file/text/json-resource/managed-json-resource/contact-supervisor",
        "/resource/file/text/json-resource/managed-json-resource/course-description",
        "/resource/file/text/json-resource/managed-json-resource/course-group",
        "/resource/file/text/json-resource/managed-json-resource/course-schedule",
        "/resource/file/text/json-resource/managed-json-resource/exam",
        "/resource/file/text/json-resource/managed-json-resource/external-work-listing",
        "/resource/file/text/json-resource/managed-json-resource/featured-content",
        "/resource/file/text/json-resource/managed-json-resource/frontpage",
        "/resource/file/text/json-resource/managed-json-resource/hvordan-soke",
        "/resource/file/text/json-resource/managed-json-resource/oppbygning",
        "/resource/file/text/json-resource/managed-json-resource/organizational-unit",
        "/resource/file/text/json-resource/managed-json-resource/person",
        "/resource/file/text/json-resource/managed-json-resource/program-document",
        "/resource/file/text/json-resource/managed-json-resource/program-document/program-frontpage",
        "/resource/file/text/json-resource/managed-json-resource/program-document/program-option-frontpage",
        "/resource/file/text/json-resource/managed-json-resource/research-group",
        "/resource/file/text/json-resource/managed-json-resource/samlet",
        "/resource/file/text/json-resource/managed-json-resource/samlet/samlet-program",
        "/resource/file/text/json-resource/managed-json-resource/samlet/samlet-retning",
        "/resource/file/text/json-resource/managed-json-resource/schedule",
        "/resource/file/text/json-resource/managed-json-resource/semester-page",
        "/resource/file/text/json-resource/managed-json-resource/shared-text",
        "/resource/file/text/json-resource/managed-json-resource/structured-document",
        "/resource/file/text/json-resource/managed-json-resource/structured-document/structured-article",
        "/resource/file/text/json-resource/managed-json-resource/structured-document/structured-event",
        "/resource/file/text/json-resource/managed-json-resource/structured-document/web-page",
        "/resource/file/text/json-resource/managed-json-resource/structured-master",
        "/resource/file/text/json-resource/managed-json-resource/structured-message",
        "/resource/file/text/json-resource/managed-json-resource/structured-project",
        "/resource/file/text/json-resource/managed-json-resource/student-exchange-agreement",
        "/resource/file/text/json-resource/managed-json-resource/ub-mapping",
        "/resource/file/text/json-resource/managed-json-resource/unit-publications",
        "/resource/file/text/php",
        "/resource/file/text/xml-resource",
        "/resource/file/text/xml-resource/managed-xml",
        "/resource/file/text/xml-resource/managed-xml/arrangement",
        "/resource/file/text/xml-resource/managed-xml/artikkel",
        "/resource/file/text/xml-resource/managed-xml/artikkelsamling",
        "/resource/file/text/xml-resource/managed-xml/disputas",
        "/resource/file/text/xml-resource/managed-xml/emne",
        "/resource/file/text/xml-resource/managed-xml/emnegruppe",
        "/resource/file/text/xml-resource/managed-xml/muv-artikkel",
        "/resource/file/text/xml-resource/managed-xml/nyhet",
        "/resource/file/text/xml-resource/managed-xml/portal",
        "/resource/file/text/xml-resource/managed-xml/project",
        "/resource/file/text/xml-resource/managed-xml/proveforelesning",
        "/resource/file/text/xml-resource/managed-xml/publikasjon",
        "/resource/file/text/xml-resource/managed-xml/researcher",
        "/resource/file/text/xml-resource/managed-xml/sakskart",
        "/resource/file/text/xml-resource/managed-xml/semester",
        "/resource/file/text/xml-resource/managed-xml/studieprogram",
        "/resource/file/text/xml-resource/managed-xml/studieretning",
        "/resource/file/text/xml-resource/managed-xml/treaty",
        "/resource/file/text/xml-resource/managed-xml/utvekslingsavtale",
        "/resource/file/video"
    };
    
    private static final Map<String, Path> legacyMappings = new HashMap<String, Path>() {{
        for (String s: legacyTypes) {
            Path p = Path.fromString(s);
            put(p.getName(), p);
        }
    }};

}
