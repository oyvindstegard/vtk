/* Copyright (c) 2009,2015 University of Oslo, Norway
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.antlr.runtime.ANTLRInputStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.Tree;
import org.apache.commons.lang.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.repository.resource.ResourcetreeLexer;
import vtk.repository.resource.ResourcetreeParser;
import vtk.resourcemanagement.ComponentDefinition;
import vtk.resourcemanagement.DisplayTemplate;
import vtk.resourcemanagement.StructuredResourceDescription;

@SuppressWarnings("unchecked")
public class StructuredResourceParser {
    private static final Logger logger = LoggerFactory.getLogger(StructuredResourceParser.class);
    private final DefinitionSource src;
    private List<ParsedNode> parsedNodes = new ArrayList<>();

    private PropertyDescriptionParser propertyDescriptionParser = new PropertyDescriptionParser();
    private EditRuleParser editRuleParser = new EditRuleParser();
    private ScriptDefinitionParser scriptDefinitionParser = new ScriptDefinitionParser();
    private ServiceDefinitionParser serviceDefinitionParser = new ServiceDefinitionParser();
    private VocabularyDefinitionParser vocabularyDefinitionParser  = new VocabularyDefinitionParser();
    
    public static interface DefinitionSource {
        public String description();
        public InputStream content() throws IOException;
        public default Optional<DefinitionSource> relative(String rel) throws IOException {
            return Optional.empty();
        }
    }
    
    public StructuredResourceParser(DefinitionSource src) {
        this.src = src;
    }

    public List<ParsedNode> parse() throws Exception {
        ParseUnit parseUnit = createParser(src);
        parseResourceTypeDefinition(parseUnit);

        List<ParsedNode> tmp = new ArrayList<>();
        for (ParsedNode prd : parsedNodes) {
            if (prd.hasParent()) {
                ParsedNode parent = getParent(parsedNodes, prd);
                if (parent != null) {
                    parent.addChild(prd);
                    tmp.add(prd);
                }
            }
        }
        parsedNodes.removeAll(tmp);
        return parsedNodes;
    }

    private void parseResourceTypeDefinition(ParseUnit parseUnit) throws Exception {
        logger.debug("Parse: " + parseUnit.description);

        ResourcetreeParser parser = parseUnit.parser;
        ResourcetreeParser.resources_return resources = parser.resources();
        if (parser.getNumberOfSyntaxErrors() > 0) {

            List<String> messages = parser.getErrorMessages();
            StringBuilder mainMessage = new StringBuilder();
            for (String m : messages) {
                mainMessage
                        .append(parseUnit.description)
                        .append(": ")
                        .append(m);
            }
            throw new IllegalStateException(
                    "Unable to parse resource tree description: " 
                            + mainMessage.toString());
        }

        CommonTree resourceTree = (CommonTree) resources.getTree();
        if (resourceTree == null) return;
        List<CommonTree> children = resourceTree.getChildren();
        if (children.size() == 1) {
            handleResourceTypeDefinition(children.get(0));
        }
        else {
            for (CommonTree child : children) {
                handleResourceTypeDefinition((CommonTree) child.getChild(0));
            }
        }
    }

    private void handleResourceTypeDefinition(CommonTree definition) throws Exception {
        if (ResourcetreeLexer.RESOURCETYPE == definition.getParent().getType()) {
            StructuredResourceDescription srd = createStructuredResourceDescription(definition);
            parsedNodes.add(new ParsedNode(srd));
        }
        else if (ResourcetreeLexer.INCLUDE == definition.getParent().getType()) {
            String includeFileName = definition.getText();
            handleInclude(includeFileName);
        }
    }

    private void handleInclude(String includeFileName) throws Exception {        
        Optional<DefinitionSource> rel = src.relative(includeFileName);
        if (!rel.isPresent()) {
          throw new IllegalArgumentException("Unable to include: " + includeFileName);
        }
        ParseUnit parseUnit = createParser(rel.get());
        parseResourceTypeDefinition(parseUnit);
    }

    private StructuredResourceDescription createStructuredResourceDescription(Tree resource) {
        StructuredResourceDescription resourceDescription = new StructuredResourceDescription();
        resourceDescription.setName(resource.getText());

        List<CommonTree> children = ((CommonTree) resource).getChildren();
        if (hasContent(children)) {
            for (CommonTree descriptionEntry : children) {
                switch (descriptionEntry.getType()) {
                    case ResourcetreeLexer.PARENT:
                        resourceDescription.setInheritsFrom(descriptionEntry.getChild(0).getText());
                        break;
                    case ResourcetreeLexer.PROPERTIES:
                        propertyDescriptionParser.parsePropertyDescriptions(
                                resourceDescription, descriptionEntry.getChildren()
                        );
                        break;
                    case ResourcetreeLexer.EDITRULES:
                        editRuleParser.parseEditRulesDescriptions(
                                resourceDescription, descriptionEntry.getChildren()
                        );
                        break;
                    case ResourcetreeLexer.VIEWCOMPONENTS:
                        handleViewComponents(resourceDescription, descriptionEntry.getChildren());
                        break;
                    case ResourcetreeLexer.VIEW:
                        if (descriptionEntry.getChild(0) != null) {
                            resourceDescription.setDisplayTemplate(
                                    new DisplayTemplate(descriptionEntry.getChild(0).getText())
                            );
                        }
                        break;
                    case ResourcetreeLexer.LOCALIZATION:
                        handleLocalization(resourceDescription, descriptionEntry.getChildren());
                        break;
                    case ResourcetreeLexer.SCRIPTS:
                        scriptDefinitionParser.parseScripts(
                                resourceDescription, descriptionEntry.getChildren()
                        );
                        break;
                    case ResourcetreeLexer.SERVICES:
                        serviceDefinitionParser.parseServices(
                                resourceDescription, descriptionEntry.getChildren()
                        );
                        break;
                    case ResourcetreeLexer.VOCABULARY:
                        vocabularyDefinitionParser.handleVocabulary(
                                resourceDescription, descriptionEntry.getChildren()
                        );
                        break;
                    default:
                        throw new IllegalStateException(
                                resourceDescription.getName() + ": unknown token type: "
                                        + descriptionEntry.getText()
                        );
                }
            }
        }
        return resourceDescription;
    }

    private void handleLocalization(
            StructuredResourceDescription srd,
            List<CommonTree> propertyDescriptions
    ) {
        if (!hasContent(propertyDescriptions)) {
            return;
        }
        
        for (CommonTree propDesc : propertyDescriptions) {
            Map<Locale, Map<Locale, String>> localizationMap = new HashMap<>();
            for (CommonTree lang : (List<CommonTree>) propDesc.getChildren()) {
                Locale locale = LocaleUtils.toLocale(lang.getText());
                HashMap<Locale, String> localizationViewMap = new HashMap<>();
                for (CommonTree label : (List<CommonTree>) lang.getChildren()) {
                    List<CommonTree> view = label.getChildren();
                    if (view == null) {
                        localizationViewMap.put(locale, label.getText());
                        localizationMap.put(locale, localizationViewMap);
                        localizationViewMap = new HashMap<>();
                    }
                    else {
                        Locale localeView = LocaleUtils.toLocale(label.getText());
                        for (CommonTree labelView : view) {
                            localizationViewMap.put(localeView, labelView.getText());
                        }
                    }
                }
                if (!localizationViewMap.isEmpty()) {
                    localizationMap.put(locale, localizationViewMap);
                }
            }
            srd.addLocalization(propDesc.getText(), localizationMap);
        }
    }

    private void handleViewComponents(StructuredResourceDescription srd,
            List<CommonTree> viewComponentDefinitions) {
        if (!hasContent(viewComponentDefinitions)) {
            return;
        }
        for (CommonTree viewComponentDescription : viewComponentDefinitions) {

            if (viewComponentDescription.getChildren().size() >= 1) {
                String name = viewComponentDescription.getText();
                String def = viewComponentDescription.getChild(0).getText();
                ComponentDefinition compDef = new ComponentDefinition(name, def);
                List<String> parameters = new ArrayList<>();
                if (viewComponentDescription.getChildCount() > 1) {
                    for (int i = 1; i < viewComponentDescription.getChildCount(); i++) {
                        String param = viewComponentDescription.getChild(i).getText();
                        parameters.add(param);
                    }
                    compDef.setParameters(parameters);
                }
                srd.addComponentDefinition(compDef);
            }
        }
    }

    private boolean hasContent(List<CommonTree> tree) {
        return tree != null && tree.size() > 0;
    }

    private class ParseUnit {
        final ResourcetreeParser parser;
        final String description;

        public ParseUnit(ResourcetreeParser parser, String description) {
            this.parser = parser;
            this.description = description;
        }
    }

    private ParseUnit createParser(DefinitionSource source) throws IOException {
        InputStream in = source.content();
        ResourcetreeLexer lexer = new ResourcetreeLexer(new ANTLRInputStream(in, "UTF-8"));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ResourcetreeParser parser = new ResourcetreeParser(tokens);
        ParseUnit parseUnit = new ParseUnit(parser, source.description());
        return parseUnit;
    }

    public static class ParsedNode {

        private StructuredResourceDescription srd;
        private List<ParsedNode> children;

        private ParsedNode(StructuredResourceDescription srd) {
            this.srd = srd;
        }

        public StructuredResourceDescription getStructuredResourceDescription() {
            return this.srd;
        }

        public void addChild(ParsedNode prd) {
            if (children == null) {
                children = new ArrayList<>();
            }
            children.add(prd);
        }

        public boolean hasChildren() {
            return children != null && children.size() > 0;
        }

        public List<ParsedNode> getChildren() {
            return this.children;
        }

        public String getName() {
            return this.srd.getName();
        }

        public boolean hasParent() {
            return this.srd.getInheritsFrom() != null;
        }

        public String getParentName() {
            return this.srd.getInheritsFrom();
        }

        @Override
        public String toString() {
            return this.srd.getName() + (this.hasChildren() ? this.children : "");
        }

    }

    private ParsedNode getParent(List<ParsedNode> l, ParsedNode prd) {
        for (ParsedNode p : l) {
            if (p.getName().equals(prd.getParentName())) {
                return p;
            }
            else if (p.hasChildren()) {
                getParent(p.getChildren(), prd);
            }
        }
        return null;
    }

}
