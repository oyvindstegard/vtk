/* Copyright (c) 2017, University of Oslo, Norway
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
package vtk.util.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Generic ASCII tree printer.
 */
public class TreePrinter {

    private final Format format;

    public TreePrinter() {
        this.format = new Format(){};
    }

    public TreePrinter(Format format) {
        this.format = format;
    }

    /**
     * Render tree model to string.
     * @param model model to render
     * @return a string with an ASCII tree rendering according to configured format.
     */
    public String render(Model model) {
        StringBuilder buf = new StringBuilder();
        Set<Node> visited = new HashSet<>();
        for (Node root: model.roots()) {
            printNodeTree(root, false, "", buf, visited);
        }
        return buf.toString();
    }

    /**
     * Format for various aspects of the tree rendering.
     */
    public interface Format {

        /**
         * @return Prefix drawn before root nodes.
         */
        default String rootNodeNamePrefix() {
            return "";
        }

        /**
         * @param siblingsFollowing {@code true} if sibling tree nodes follow the node for this prefix
         * @return Prefix drawn before tree node name is formatted
         */
        default String nodeNamePrefix(boolean siblingsFollowing) {
            if (siblingsFollowing) {
                return "*--"; // or "+--"
            } else {
                return "*--"; // or "\\--"
            }
        }

        /**
         * Format a tree node name
         * @param name
         * @return
         */
        default String formatNodeName(String name) {
            return name;
        }

        /**
         * Prefix drawn before an attribute is formatted.
         * @return
         */
        default String attributePrefix() {
            return " ";
        }

        /**
         * Format a node attribute
         * @param attribute
         * @return a string describing the node attribute (single line)
         */
        default String formatAttribute(NamedValue attribute) {
            if (attribute.name().isPresent()) {
                return attribute.name().get() + ": " + attribute.value();
            } else {
                return attribute.value().toString();
            }
        }

        /**
         * @return character used for drawing vertical lines in tree.
         */
        default char verticalTreeLine() {
            return '|';
        }

        /**
         * Max indentation per node level in tree.
         *
         * @return a number &gt;= 1
         */
        default int maxLevelIndentation() {
            return 10;
        }

        /**
         * Space in number of lines before node attributes have been printed.
         */
        default int linesBeforeNodeAttributes() {
            return 0;
        }

        /**
         * Space in number of lines after node attributes have been printed.
         */
        default int linesAfterNodeAttributes() {
            return 1;
        }

    }

    private void printNodeTree(Node node,
            boolean siblingsFollowing, String prefix, StringBuilder b, Set<Node> visited) {
        if (!visited.add(node)) {
            throw new IllegalStateException("Node cycle detected, already visisted node: " + node.name());
        }

        final String nodeNamePrefix;
        if (node.isRoot()) {
            nodeNamePrefix = format.rootNodeNamePrefix();
        } else {
            nodeNamePrefix = format.nodeNamePrefix(siblingsFollowing);
        }
        final String nodeString = nodeNamePrefix + format.formatNodeName(node.name());

        b.append(prefix).append(nodeString).append('\n');

        int indent = Math.min(nodeString.length()-1, format.maxLevelIndentation());
        indent = Math.max(1, indent);

        final String nextLevelPrefix;
        if (siblingsFollowing) {
            nextLevelPrefix = prefix + format.verticalTreeLine() + ws(indent-1);
        } else {
            nextLevelPrefix = prefix + ws(indent);
        }

        for (int i = 0; i < format.linesBeforeNodeAttributes(); i++) {
            if (node.hasChildren()) {
                b.append(nextLevelPrefix);
                b.append(format.verticalTreeLine());
            } else {
                b.append(prefix);
                if (siblingsFollowing) {
                    b.append(format.verticalTreeLine());
                }
            }
            b.append('\n');
        }

        // Render node attributes
        for (NamedValue a : node.attributes()) {
            if (node.hasChildren()) {
                b.append(nextLevelPrefix);
                b.append(format.verticalTreeLine());
                b.append(ws(Math.min(0, format.nodeNamePrefix(siblingsFollowing).length()-1)));
            } else {
                b.append(prefix);
                b.append(ws(format.nodeNamePrefix(siblingsFollowing).length()));
            }
            b.append(format.attributePrefix());
            b.append(format.formatAttribute(a));
            b.append('\n');
        }

        for (int i = 0; i < format.linesAfterNodeAttributes(); i++) {
            if (node.hasChildren()) {
                b.append(nextLevelPrefix);
                b.append(format.verticalTreeLine());
            } else {
                b.append(prefix);
                if (siblingsFollowing) {
                    b.append(format.verticalTreeLine());
                }
            }
            b.append('\n');
        }

        for (Iterator<? extends Node> it = node.children().iterator(); it.hasNext();) {
            Node child = it.next();
            printNodeTree(child, it.hasNext(), nextLevelPrefix, b, visited);
        }
    }

    private String ws(int count) {
        StringBuilder ws = new StringBuilder();
        for (int i=0; i<count; i++) {
            ws.append(' ');
        }
        return ws.toString();
    }

    public static ModelBuilder newModelBuilder() {
        return new ModelBuilder();
    }

    public static final class ModelBuilder {

        private final List<NodeImpl> roots = new ArrayList<>();
        private NodeImpl current = null;

        private ModelBuilder() {
        }

        private void checkCurrent() {
            if (current == null) {
                throw new IllegalStateException("No current node, use add(String) to add first root node");
            }
        }

        /**
         * Adds a sibling node of the current node, sets it as current node.
         *
         * <p>If no node has been created yet, this method adds the first root node.
         *
         * <p>If current node is a root node, this adds a new root node.
         * @param name
         * @return
         */
        public ModelBuilder add(String name) {
            if (current == null || !current.parent.isPresent()) {
                NodeImpl newNode = new NodeImpl(name, Optional.empty());
                roots.add(newNode);
                current = newNode;
            } else {
                current = current.parent.get();
                addChild(name);
            }
            return this;
        }

        /**
         * Adds a child of the current node, sets it as current node.
         * @param name
         * @return
         */
        public ModelBuilder addChild(String name) {
            checkCurrent();
            NodeImpl child = new NodeImpl(name, Optional.of(current));
            current.children.add(child);
            current = child;
            return this;
        }

        /**
         * Sets parent of current node as the current node.
         * @return 
         */
        public ModelBuilder toParent() {
            checkCurrent();
            if (!current.parent.isPresent()) {
                throw new IllegalStateException("Current node is a root node");
            }
            current = current.parent.get();
            return this;
        }

        /**
         * Set first root node as current node.
         * @return 
         */
        public ModelBuilder toRoot() {
            checkCurrent();
            current = roots.get(0);
            return this;
        }

        /**
         * Adds an attribute to the current node.
         * @param value
         * @return
         */
        public ModelBuilder addAttribute(Object value) {
            checkCurrent();
            current.attributes.add(new NamedValueImpl(null, value));
            return this;
        }

        /**
         * Adds a named attribute to the current node.
         * @param name
         * @param value
         * @return
         */
        public ModelBuilder addAttribute(String name, Object value) {
            checkCurrent();
            current.attributes.add(new NamedValueImpl(name, value));
            return this;
        }

        public Model getModel() {
            return new ModelImpl(roots);
        }
        
    }


    /**
     * Tree model with multiple roots.
     */
    public interface Model {
        List<? extends Node> roots();
    }

    /**
     * A single tree model node with zero or more children and zero
     * or more attributes.
     */
    public interface Node {
        String name();

        Optional<? extends Node> parent();

        List<? extends Node> children();

        List<? extends NamedValue> attributes();

        default public boolean isRoot() {
            return !parent().isPresent();
        }

        default public boolean hasChildren() {
            return !children().isEmpty();
        }

        default public boolean hasAttributes() {
            return !attributes().isEmpty();
        }

        default public boolean hasSiblings() {
            return parent().isPresent() && parent().get().hasChildren();
        }
    }

    /**
     * A value with an optional name.
     * 
     * <p>Used for tree node attributes.
     */
    public interface NamedValue {

        Optional<String> name();

        Object value();

    }

    private static final class ModelImpl implements Model {
        final List<NodeImpl> rootNodes;

        ModelImpl(List<NodeImpl> rootNodes) {
            this.rootNodes = Collections.unmodifiableList(new ArrayList<>(rootNodes));
        }

        @Override
        public List<NodeImpl> roots() {
            return rootNodes;
        }
    }

    private static final class NodeImpl implements Node {
        final String name;
        final Optional<NodeImpl> parent;
        final List<NamedValueImpl> attributes = new ArrayList<>();
        final List<NodeImpl> children = new ArrayList<>();

        NodeImpl(String name, Optional<NodeImpl> parent) {
            this.name = Objects.requireNonNull(name);
            this.parent = Objects.requireNonNull(parent);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Optional<NodeImpl> parent() {
            return parent;
        }

        @Override
        public List<NodeImpl> children() {
            return children;
        }

        @Override
        public List<NamedValueImpl> attributes() {
            return attributes;
        }

    }

    private static final class NamedValueImpl implements NamedValue {
        final Optional<String> name;
        final Object value;

        private NamedValueImpl(String name, Object value) {
            this.name = Optional.ofNullable(name);
            this.value = value;
        }

        @Override
        public Optional<String> name() {
            return name;
        }

        @Override
        public Object value() {
            return value;
        }
    }

}
