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
package vtk.resourcemanagement.parser;

import java.util.ArrayList;
import java.util.List;

import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.Tree;
import vtk.repository.resource.ResourcetreeLexer;
import vtk.resourcemanagement.StructuredResourceDescription;
import vtk.resourcemanagement.property.BinaryPropertyDescription;
import vtk.resourcemanagement.property.DerivedPropertyDescription;
import vtk.resourcemanagement.property.DerivedPropertyEvaluationDescription;
import vtk.resourcemanagement.property.DerivedPropertyEvaluationDescription.EvaluationElement;
import vtk.resourcemanagement.property.DerivedPropertyEvaluationDescription.Operator;
import vtk.resourcemanagement.property.JSONPropertyAttributeDescription;
import vtk.resourcemanagement.property.JSONPropertyDescription;
import vtk.resourcemanagement.property.PropertyDescription;
import vtk.resourcemanagement.property.SimplePropertyDescription;

public class PropertyDescriptionParser {

    @SuppressWarnings("unchecked")
    public void parsePropertyDescriptions(StructuredResourceDescription srd, List<CommonTree> propertyDescriptions) {
        List<PropertyDescription> props = new ArrayList<PropertyDescription>();
        if (propertyDescriptions != null) {
            for (CommonTree propDesc : propertyDescriptions) {
                int type = propDesc.getChild(0).getType();
                if (type == ResourcetreeLexer.DERIVED) {
                    DerivedPropertyDescription p = new DerivedPropertyDescription();
                    p.setName(propDesc.getText());
                    populateDerivedPropertyDescription(p, propDesc.getChildren());
                    props.add(p);
                } else if (type == ResourcetreeLexer.JSON) {
                    JSONPropertyDescription json = new JSONPropertyDescription();
                    json.setName(propDesc.getText());
                    populateJSONPropertyDescription(json, propDesc.getChildren());
                    props.add(json);
                } else if (type == ResourcetreeLexer.BINARY) {
                    BinaryPropertyDescription bin = new BinaryPropertyDescription();
                    bin.setName(propDesc.getText());
                    populateBinaryPropertyDescription(bin, propDesc.getChildren());
                    props.add(bin);
                } else {
                    SimplePropertyDescription p = new SimplePropertyDescription();
                    p.setName(propDesc.getText());
                    populateSimplePropertyDescription(p, propDesc.getChildren());
                    props.add(p);
                }
            }
            srd.setPropertyDescriptions(props);
        }
    }

    private void populateBinaryPropertyDescription(BinaryPropertyDescription bin, List<CommonTree> propertyDescription) {
        for (CommonTree descEntry : propertyDescription) {
            switch (descEntry.getType()) {
            case ResourcetreeLexer.BINARY:
                bin.setType(descEntry.getText());
                break;
            default:
                throw new IllegalStateException("Unknown token type for simple property description: "
                        + descEntry.getText());
            }
        }
    }

    private void populateSimplePropertyDescription(SimplePropertyDescription p, List<CommonTree> propertyDescription) {

        String propertyType = null;
        for (CommonTree descEntry : propertyDescription) {

            int descType = descEntry.getType();

            switch (descType) {
            case ResourcetreeLexer.PROPTYPE:
                propertyType = descEntry.getText();
                p.setType(propertyType);
                break;
            case ResourcetreeLexer.REQUIRED:
                p.setRequired(true);
                break;
            case ResourcetreeLexer.NOEXTRACT:
                p.setNoExtract(true);
                break;
            case ResourcetreeLexer.OVERRIDES:
                p.setOverrides(descEntry.getChild(0).getText());
                break;
            case ResourcetreeLexer.MULTIPLE:
                p.setMultiple(true);
                break;
            case ResourcetreeLexer.DEFAULTVALUE:
                p.setDefaultValue(descEntry.getChild(0).getText());
                break;
            case ResourcetreeLexer.TRIM:
                if (!ParserConstants.PROPTYPE_STRING.equals(propertyType)) {
                    throw new IllegalArgumentException("Trim is only applicable for properties of type STRING.");
                }
                p.setTrim(true);
                break;
            default:
                throw new IllegalStateException("Unknown token type for simple property description: "
                        + descEntry.getText());
            }
        }

    }

    private void populateJSONPropertyDescription(JSONPropertyDescription p, List<CommonTree> propertyDescription) {
        for (CommonTree descEntry : propertyDescription) {
            switch (descEntry.getType()) {
            case ResourcetreeLexer.JSON:
                p.setType(descEntry.getText());
                handleJSONAttributes(p, descEntry);
                break;
            case ResourcetreeLexer.MULTIPLE:
                p.setMultiple(true);
                break;
            case ResourcetreeLexer.NOEXTRACT:
                p.setNoExtract(true);
                break;
            case ResourcetreeLexer.INDEXABLEJSON:
                p.setIndexable(true);
                break;
            default:
                throw new IllegalStateException("Unknown token type for derived property description: "
                        + descEntry.getType());
            }
        }

    }

    private void populateDerivedPropertyDescription(DerivedPropertyDescription p, List<CommonTree> propertyDescription) {
        for (CommonTree descEntry : propertyDescription) {
            switch (descEntry.getType()) {
            case ResourcetreeLexer.DERIVED:
                handleDerivedProperty(p, descEntry);
                break;
            case ResourcetreeLexer.OVERRIDES:
                p.setOverrides(descEntry.getChild(0).getText());
                break;
            case ResourcetreeLexer.MULTIPLE:
                p.setMultiple(true);
                break;
            case ResourcetreeLexer.DEFAULTPROP:
                p.setDefaultProperty(descEntry.getChild(0).getText());
                break;
            default:
                throw new IllegalStateException("Unknown token type for derived property description: "
                        + descEntry.getType());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleJSONAttributes(JSONPropertyDescription p, CommonTree descEntry) {
        List<CommonTree> jsonSpecList = descEntry.getChildren();
        if (jsonSpecList != null) {
            for (CommonTree jsonSpec : jsonSpecList) {
                JSONPropertyAttributeDescription attribute = new JSONPropertyAttributeDescription();
                String name = jsonSpec.getText();
                Tree typeTree = jsonSpec.getChild(0);
                attribute.setName(name);
                attribute.setType(typeTree.getText());
                p.addAttribute(attribute);
            }
        }
    }

    private void handleDerivedProperty(DerivedPropertyDescription p, CommonTree descEntry) {

        int index = 0;
        Tree fields = descEntry.getChild(index);

        if (fields == null) {
            return;
        }

        if (fields.getType() == ResourcetreeLexer.MULTIPLE) {
            p.setMultiple(true);
            index++;
            fields = descEntry.getChild(index);
        }

        if (fields == null) {
            return;
        }

        List<String> dependentFields = new ArrayList<String>();
        for (int i = index; i < fields.getChildCount(); i++) {
            dependentFields.add(fields.getChild(i).getText());
        }

        Tree eval = descEntry.getChild(index + 1);

        DerivedPropertyEvaluationDescription evaluationDescription = new DerivedPropertyEvaluationDescription();
        for (int i = 0; i < eval.getChildCount(); i++) {
            Tree evalDesc = eval.getChild(i);
            String value = evalDesc.getText();
            boolean quote = evalDesc.getType() == ResourcetreeLexer.QTEXT;
            Operator operator = null;
            Tree child = evalDesc.getChild(0);
            if (child != null && child.getType() == ResourcetreeLexer.QUESTION) {
                String operatorName = evalDesc.getChild(1).getText();
                operator = DerivedPropertyEvaluationDescription.getOperator(operatorName);
            }
            EvaluationElement evaluationElement = new EvaluationElement(quote, value, operator);
            evaluationDescription.addEvaluationElement(evaluationElement);
        }
        p.setDependentProperties(dependentFields);
        p.setEvaluationDescription(evaluationDescription);
    }
}
