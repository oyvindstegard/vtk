/* Copyright (c) 2009,2016 University of Oslo, Norway
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
package vtk.resourcemanagement;


import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Required;

import vtk.repository.RepositoryContentEvaluationAssertion;
import vtk.repository.Resource;
import vtk.repository.content.JsonParseResult;
import vtk.repository.resourcetype.Content;
import vtk.security.Principal;
import vtk.util.text.Json;

/**
 * XXX Not usable as web service assertion.
 * 
 */
public class JSONObjectSelectAssertion implements RepositoryContentEvaluationAssertion {

//    private Repository repository;
//    private String token;
    private String expression;
    private Set<String> expectedValues = new HashSet<>();

    public JSONObjectSelectAssertion createAssertion(String expression) {
        return createAssertion(expression, (String) null);
    }

    public JSONObjectSelectAssertion createAssertion(String expression, String expectedValue) {
        Set<String> expectedValues = new HashSet<>();
        if (expectedValue != null) {
            expectedValues.add(expectedValue);
        }
        return createAssertion(expression, expectedValues);
    }
    
    public JSONObjectSelectAssertion createAssertion(String expression, Set<String> expectedValues) {
        JSONObjectSelectAssertion assertion = new JSONObjectSelectAssertion();
        assertion.setExpression(expression);
        assertion.setExpectedValues(expectedValues);
        return assertion;
    }
    
    @Override
    public boolean matches(Optional<Resource> resource, Optional<Principal> principal) {
        return matches(resource, principal, Optional.empty());
    }
    
    @Override
    public boolean matches(Optional<Resource> resource, 
            Optional<Principal> principal, Optional<Content> content) {
        if (!content.isPresent()) return false;
        if (!resource.isPresent()) return false;
        if (resource.get().isCollection()) return false;
        
        try {
            JsonParseResult result = content.get().getContentRepresentation(JsonParseResult.class);
            if (result.value.failure().isPresent()) {
                return false;
            }
            Optional<Json.MapContainer> document = result.asObject();
            if (!document.isPresent()) {
                return false;
            }
            Object o = Json.select(document.get(), this.expression);
            if (this.expectedValues == null || this.expectedValues.isEmpty()) {
                return o != null;
            }
            if (o == null) {
                return false;
            }
            return this.expectedValues.contains(o.toString());

        } catch (Exception e) {
            return false;
        }
    }

    @Required
    public void setExpression(String expression) {
        this.expression = expression;
    }
    
    public String getExpression() {
        return this.expression;
    }

    public void setExpectedValues(Set<String> values) {
        if (values == null) {
            return;
        }
        this.expectedValues = Collections.unmodifiableSet(values);
    }
    
    public void setExpectedValue(String value) {
        this.expectedValues = new HashSet<>();
        this.expectedValues.add(value);
    }
    
    public void addExpectedValue(String value) {
        Set<String> expectedValues = new HashSet<>(this.expectedValues);
        expectedValues.add(value);
        this.expectedValues = Collections.unmodifiableSet(expectedValues);
    }
    
    @Override
    public String toString() {
        return "content.json." + this.expression + " in " + this.expectedValues;
    }
}
