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
        while (def.getParentTypeDefinition() != null) {
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
        "/collection",
        "/collection/article-listing",
        "/collection/audio-video-listing",
        "/collection/blog-listing",
        "/collection/employee-listing",
        "/collection/event-listing",
        "/collection/image-listing",
        "/collection/master-listing",
        "/collection/message-listing",
        "/collection/person-listing",
        "/collection/project-listing",
        "/collection/research-group-listing",
        "/file",
        "/file/audio",
        "/file/image",
        "/file/odf",
        "/file/odf/odb",
        "/file/odf/odg",
        "/file/odf/odp",
        "/file/odf/ods",
        "/file/odf/odt",
        "/file/ooxml",
        "/file/ooxml/doc",
        "/file/ooxml/ppt",
        "/file/ooxml/xls",
        "/file/pdf",
        "/file/text",
        "/file/text/apt-resource",
        "/file/text/html",
        "/file/text/html/xhtml10",
        "/file/text/html/xhtml10/xhtml10strict",
        "/file/text/html/xhtml10/xhtml10trans",
        "/file/text/html/xhtml10/xhtml10trans/document",
        "/file/text/html/xhtml10/xhtml10trans/document/article",
        "/file/text/html/xhtml10/xhtml10trans/document/event",
        "/file/text/json-resource",
        "/file/text/json-resource/managed-json-resource",
        "/file/text/json-resource/managed-json-resource/boxes",
        "/file/text/json-resource/managed-json-resource/contact-supervisor",
        "/file/text/json-resource/managed-json-resource/course-description",
        "/file/text/json-resource/managed-json-resource/course-group",
        "/file/text/json-resource/managed-json-resource/course-schedule",
        "/file/text/json-resource/managed-json-resource/exam",
        "/file/text/json-resource/managed-json-resource/external-work-listing",
        "/file/text/json-resource/managed-json-resource/featured-content",
        "/file/text/json-resource/managed-json-resource/frontpage",
        "/file/text/json-resource/managed-json-resource/hvordan-soke",
        "/file/text/json-resource/managed-json-resource/oppbygning",
        "/file/text/json-resource/managed-json-resource/organizational-unit",
        "/file/text/json-resource/managed-json-resource/person",
        "/file/text/json-resource/managed-json-resource/program-document",
        "/file/text/json-resource/managed-json-resource/program-document/program-frontpage",
        "/file/text/json-resource/managed-json-resource/program-document/program-option-frontpage",
        "/file/text/json-resource/managed-json-resource/research-group",
        "/file/text/json-resource/managed-json-resource/samlet",
        "/file/text/json-resource/managed-json-resource/samlet/samlet-program",
        "/file/text/json-resource/managed-json-resource/samlet/samlet-retning",
        "/file/text/json-resource/managed-json-resource/schedule",
        "/file/text/json-resource/managed-json-resource/semester-page",
        "/file/text/json-resource/managed-json-resource/shared-text",
        "/file/text/json-resource/managed-json-resource/structured-document",
        "/file/text/json-resource/managed-json-resource/structured-document/structured-article",
        "/file/text/json-resource/managed-json-resource/structured-document/structured-event",
        "/file/text/json-resource/managed-json-resource/structured-document/web-page",
        "/file/text/json-resource/managed-json-resource/structured-master",
        "/file/text/json-resource/managed-json-resource/structured-message",
        "/file/text/json-resource/managed-json-resource/structured-project",
        "/file/text/json-resource/managed-json-resource/student-exchange-agreement",
        "/file/text/json-resource/managed-json-resource/ub-mapping",
        "/file/text/json-resource/managed-json-resource/unit-publications",
        "/file/text/php",
        "/file/text/xml-resource",
        "/file/text/xml-resource/managed-xml",
        "/file/text/xml-resource/managed-xml/arrangement",
        "/file/text/xml-resource/managed-xml/artikkel",
        "/file/text/xml-resource/managed-xml/artikkelsamling",
        "/file/text/xml-resource/managed-xml/disputas",
        "/file/text/xml-resource/managed-xml/emne",
        "/file/text/xml-resource/managed-xml/emnegruppe",
        "/file/text/xml-resource/managed-xml/muv-artikkel",
        "/file/text/xml-resource/managed-xml/nyhet",
        "/file/text/xml-resource/managed-xml/portal",
        "/file/text/xml-resource/managed-xml/project",
        "/file/text/xml-resource/managed-xml/proveforelesning",
        "/file/text/xml-resource/managed-xml/publikasjon",
        "/file/text/xml-resource/managed-xml/researcher",
        "/file/text/xml-resource/managed-xml/sakskart",
        "/file/text/xml-resource/managed-xml/semester",
        "/file/text/xml-resource/managed-xml/studieprogram",
        "/file/text/xml-resource/managed-xml/studieretning",
        "/file/text/xml-resource/managed-xml/treaty",
        "/file/text/xml-resource/managed-xml/utvekslingsavtale",
        "/file/video"
    };
    
    private static final Map<String, Path> legacyMappings = new HashMap<String, Path>() {{
        for (String s: legacyTypes) {
            Path p = Path.fromString(s);
            put(p.getName(), p);
        }
    }};

}
