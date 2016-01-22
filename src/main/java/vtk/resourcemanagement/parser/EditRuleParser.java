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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.antlr.runtime.tree.CommonTree;
import org.apache.commons.lang.LocaleUtils;

import vtk.repository.resource.ResourcetreeLexer;
import vtk.resourcemanagement.EditRule;
import vtk.resourcemanagement.EditRule.EditRuleType;
import vtk.resourcemanagement.StructuredResourceDescription;

@SuppressWarnings("unchecked")
public class EditRuleParser {

    public void parseEditRulesDescriptions(StructuredResourceDescription srd, List<CommonTree> editRuleDescriptions) {
        if (editRuleDescriptions != null) {
            for (CommonTree editRuleDescription : editRuleDescriptions) {
                if (ResourcetreeLexer.GROUP == editRuleDescription.getType()) {
                    handleGroupedEditRuleDescription(srd, editRuleDescription);
                } else {
                    String propName = editRuleDescription.getText();
                    List<CommonTree> editRules = editRuleDescription.getChildren();
                    for (CommonTree editRule : editRules) {
                        switch (editRule.getType()) {
                        case ResourcetreeLexer.BEFORE:
                            srd.addEditRule(new EditRule(propName, EditRuleType.POSITION_BEFORE, editRule.getChild(0)
                                    .getText()));
                            break;
                        case ResourcetreeLexer.AFTER:
                            srd.addEditRule(new EditRule(propName, EditRuleType.POSITION_AFTER, editRule.getChild(0)
                                    .getText()));
                            break;
                        case ResourcetreeLexer.EDITHINT:
                            srd.addEditRule(new EditRule(propName, EditRuleType.EDITHINT, editRule.getText()));
                            break;
                        case ResourcetreeLexer.NOEDIT:

                            srd.addEditRule(new EditRule(propName, EditRuleType.NO_EDIT, null));
                            break;
                        case ResourcetreeLexer.TOOLTIP:
                            handleTooltip(srd, editRuleDescription, propName);
                            break;
                        case ResourcetreeLexer.NAME:
                            String propertyAttributeName = propName + ":" + editRule.getText();
                            srd.addEditRule(new EditRule(propertyAttributeName, EditRuleType.EDITHINT, editRule
                                    .getChild(0).getText(), true));
                            break;
                        default:
                            throw new IllegalStateException(srd.getName() + ": unknown edit rule: " 
                                    + editRuleDescription.getText());
                        }
                    }
                }
            }
        }
    }

    private void handleGroupedEditRuleDescription(StructuredResourceDescription srd, CommonTree groupRuleDescription) {

        CommonTree groupingNameElement = (CommonTree) groupRuleDescription.getChild(0);
        if (ResourcetreeLexer.NAME != groupingNameElement.getType()) {
            throw new IllegalStateException("First element in a grouping definition must be a name. " + "Found: '"
                    + groupingNameElement.getText() + "'");
        }
        String groupingName = groupingNameElement.getText();
        List<String> groupedProps = new ArrayList<String>();
        for (CommonTree prop : (List<CommonTree>) groupingNameElement.getChildren()) {
            groupedProps.add(prop.getText());
        }
        srd.addEditRule(new EditRule(groupingName, EditRuleType.GROUP, groupedProps));

        int siblingIndex = 1;
        CommonTree positioningElement = (CommonTree) groupRuleDescription.getChild(siblingIndex);
        if (positioningElement != null) {
            int groupingType = positioningElement.getType();
            if (ResourcetreeLexer.AFTER == groupingType || ResourcetreeLexer.BEFORE == groupingType) {
                EditRuleType positioningType = ResourcetreeLexer.AFTER == groupingType ? EditRuleType.POSITION_AFTER
                        : EditRuleType.POSITION_BEFORE;
                srd.addEditRule(new EditRule(groupingName, positioningType, positioningElement.getChild(0).getText()));
            }
            siblingIndex++;
        }

        CommonTree oriantationElement = (CommonTree) groupRuleDescription.getChild(siblingIndex);
        if (oriantationElement != null) {
            srd.addEditRule(new EditRule(groupingName, EditRuleType.EDITHINT, oriantationElement.getText()));
        }
    }

    private void handleTooltip(StructuredResourceDescription srd, CommonTree tooltipDescriptions, String propName) {
        Map<Locale, String> tooltipMap = new HashMap<Locale, String>();
        for (CommonTree tooltip : (List<CommonTree>) tooltipDescriptions.getChildren()) {
            for (CommonTree lang : (List<CommonTree>) tooltip.getChildren()) {
                for (CommonTree tip : (List<CommonTree>) lang.getChildren()) {
                    Locale locale = LocaleUtils.toLocale(lang.getText());
                    tooltipMap.put(locale, tip.getText());
                }
            }
        }
        srd.addTooltips(propName, tooltipMap);
    }

}
