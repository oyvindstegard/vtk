/* Copyright (c) 2013,2015 University of Oslo, Norway
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
package vtk.resourcemanagement.parser;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import vtk.resourcemanagement.EditRule;
import vtk.resourcemanagement.ScriptDefinition;
import vtk.resourcemanagement.StructuredResourceDescription;
import vtk.resourcemanagement.property.PropertyDescription;

public class PersonResourceTypeDefinitionTest extends StructuredResourceParserTest {

    private Map<String, ScriptDefinition.ScriptType> expectedScripts;

    @Test
    public void testPersonResourceType() {

        StructuredResourceDescription person = this.getNodeWithName("person");
        assertNotNull(person);

        String inheritsFrom = person.getInheritsFrom();
        assertNull(inheritsFrom);

        // Properties
        List<PropertyDescription> properties = person.getPropertyDescriptions();
        assertNotNull(properties);
        List<String> propertyNames = new ArrayList<String>();
        for (PropertyDescription pd : properties) {
            propertyNames.add(pd.getName());
        }

        List<String> expectedProperties = Arrays.asList("username", "getExternalPersonInfo", "firstName", "surname",
                "position", "phone", "mobile", "fax", "email", "postalAddress", "visitingAddress",
                "externalUserMetaData", "affiliations", "alternativeVisitingAddress", "alternativeCellPhone", "title",
                "room", "availableHours", "picture", "pressPhoto", "content", "getExternalScientificInformation",
                "numberOfPublications", "selectedPublications", "getRelatedProjects", "projects", "getRelatedGroups",
                "groups", "rssFeeds", "tags", "related-content", "hideStudentAffiliation");

        for (String expectedProperty : expectedProperties) {
            assertTrue("Expected property " + expectedProperty + " is missing",
                    propertyNames.contains(expectedProperty));
        }

        for (String propertyName : propertyNames) {
            assertTrue("Didn't expect property " + propertyName, expectedProperties.contains(propertyName));
        }

        // Edit rules
        List<EditRule> editRules = person.getEditRules();
        assertNotNull(editRules);

        // Scripts
        List<ScriptDefinition> scriptDefinitions = person.getScripts();
        assertNotNull(scriptDefinitions);

        expectedScripts = new HashMap<String, ScriptDefinition.ScriptType>();
        expectedScripts.put("getExternalPersonInfo", ScriptDefinition.ScriptType.SHOWHIDE);
        expectedScripts.put("getExternalScientificInformation", ScriptDefinition.ScriptType.SHOWHIDE);
        for (ScriptDefinition sd : scriptDefinitions) {
            ScriptDefinition.ScriptType expectedScriptType = expectedScripts.get(sd.getName());
            assertNotNull(expectedScriptType);
            assertTrue(sd.getType().equals(expectedScriptType));
        }

        // Services
        // XXX Test

        // View components
        // XXX Test

    }

}
