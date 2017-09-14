/* Copyright (c) 2008-2013, University of Oslo, Norway
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import vtk.repository.Path;
import vtk.util.text.TreePrinter.ModelBuilder;

/**
 * Parser for path mapping style configuration files.
 *
 * <p>
 * Parses a line-based configuration file format using a "key = value\n" base syntax,
 * where keys are paths understood specially and hierarchically.
 * <pre>
 * 
 *    / = VALUE1                   # A comment ..
 *    /a[p1:x,p2:y] = VALUE2
 *    /a/b = VALUE3
 *    /a/b/c/ = VALUE4             # exact match hint for path
 *
 *    # another comment
 *    /a/b/c[p1:x]/ = VALUE5       # exact match AND qualifier "p1:x"
 *    /a/b/c/[p1:x] = VALUE5       # alt. syntax for previous exact rule.
 * 
 * </pre> where paths are mapped to a list of qualifiers and a single VALUE.
 * Empty lines, lines not beginning with "/", lines with empty value and
 * "#"-comments are ignored.
 * The input file must use UTF-8 encoding for non-ASCII chars.
 * 
 * <p> 
 * Ending a path with "/" (or specifying "/" after qualifier-list) hints that the
 * particular config entry shall apply only to the specific path and not
 * descendant paths. This makes a difference for
 * {@link #getMatchAncestor(vtk.repository.Path) getMatchAncestor},
 * which ignores exact rules on ancestor paths. The flag is also available
 * for each config entry as {@link Entry#isExact() }.
 * <p>
 * After parsing, the configuration will be organized into a hierarchy
 * corresponding to the paths present, and it can be queried with
 * {@link #get(vtk.repository.Path) get}
 * and {@link #getMatchAncestor(vtk.repository.Path) getMatchAncestor}.
 * <p>
 * The same path may occur in configuration multiple times, but with different
 * set of qualifiers and values. A single path can map to zero or more
 * {@link Entry} instances, each with list of qualifier name-value-pairs,
 * a single VALUE and a flag indicating that exact path matching is desired.
 *
 * <p>A factory must be provided which can create configuration values from strings.
 *
 * <p>Instances of this class are generally immutable, except for values created
 * by user supplied factory, for which there is no guarantee.
 *
 * @param <T> the type of configuration values
 */
public class PathMappingConfig<T> {

    private final Node root = new Node();
    private final Function<String,T> valueFactory;
    private int maxLineSize = 300;
    private int maxLines = 10000;

    /**
     * Construct a configuration instance from an input stream.
     * 
     * @param source the input stream to read configuration lines from.
     * @param valueFactory factory for creating values from strings
     * @throws UncheckedIOException in case of IOException
     * @throws IllegalStateException if failure to create config entries
     */
    public PathMappingConfig(InputStream source, Function<String,T> valueFactory) {
        this.valueFactory = Objects.requireNonNull(valueFactory);
        load(source);
    }

    /**
     * Construct a configuration instance from an input stream.
     * 
     * @param source the input stream to read configuration lines from. The stream
     *               will be closed after parsing.
     * @param valueFactory factory used for creating values
     * @param maxLineSize max size of lines in configuration source.
     * @param maxLines max number of lines read from configuration source.
     *
     * @throws UncheckedIOException in case of IOException
     * @throws IllegalStateException if failure to create config entries
     */
    public PathMappingConfig(InputStream source, Function<String,T> valueFactory, int maxLineSize, int maxLines) {
        if (maxLineSize <= 0) throw new IllegalArgumentException("maxLineSize must be > 0");
        if (maxLines <= 0) throw new IllegalArgumentException("maxLines must be > 0");
        
        this.valueFactory = Objects.requireNonNull(valueFactory);
        this.maxLineSize = maxLineSize;
        this.maxLines = maxLines;
        
        load(source);
    }

    /**
     * Obtain a typical config which provides the config values as strings.
     *
     * <p>Convenience for calling {@link #PathMappingConfig(java.io.InputStream, java.util.function.Function) with
     * {@code Functions.identity()} as value factory, providing all config values simply as strings.
     *
     * @param source the input stream to read configuration lines from. The stream
     *               will be closed after parsing.
     * @return  a new path mapping config
     *
     * @throws IllegalStateException if failure to create config entries from the source
     */
    public static PathMappingConfig<String> strConfig(InputStream source) {
        return new PathMappingConfig(source, Function.identity());
    }

    /**
     * Obtain a typical config which provides the config values as strings.
     *
     * <p>Convenience for calling {@link #PathMappingConfig(java.io.InputStream, java.util.function.Function, int, int) }
     * wtih {@code Function.identity()} as value factory, providing all config values as strings.
     *
     * @param source the input stream to read configuration lines from. The stream
     *               will be closed after parsing.
     * @param maxLineSize max size of lines in configuration source.
     * @param maxLines max number of lines read from configuration source.
     * @return  a new path mapping config
     *
     * @throws IllegalStateException if failure to create config entries from the source
     */
    public static PathMappingConfig<String> strConfig(InputStream source, int maxLineSize, int maxLines) {
        return new PathMappingConfig(source, Function.identity(), maxLineSize, maxLines);
    }

    /**
     * Returns all config entries that apply exactly for the given path.
     * @param path the path to look up config for
     * @return  list of config entries which apply exactly to the provided path
     */
    public List<Entry<T>> get(Path path) {
        Node n = root;
        for (String name : path.getElements()) {
            if (name.equals("/")) {
                continue;
            }
            n = n.children.get(name);
            if (n == null) {
                break;
            }
        }
        if (n != null) {
            return n.entries;
        }
        return null;
    }
    
    /**
     * Returns config entries that apply exactly for the given path or
     * the config entries for the closest ancestor path that are not exact match rules.
     * 
     * @param path
     * @return a list of config entries, or <code>null</code> if no entries
     * apply.
     */
    public List<Entry<T>> getMatchAncestor(Path path) {
        Node n = root;
        List<Entry<T>> entries = getApplicableEntries(n, path);
        List<String> nodeNames = path.getElements();
        for (int i=1; i<nodeNames.size();i++) {
            n = n.children.get(nodeNames.get(i));
            if (n == null) {
                break;
            }

            List<Entry<T>> applicable = getApplicableEntries(n, path);
            if (!applicable.isEmpty()) {
                entries = applicable;
            }
        }
        if (entries.isEmpty()) {
            return null;
        }
        
        return entries;
    }
    
    /**
     * Node n must correspond to path p or an ancestor path of p
     * @return list of corresponding config entries, empty list if none.
     */
    private List<Entry<T>> getApplicableEntries(Node n, Path p) {
        List<Entry<T>> entries = new ArrayList<>();
        if (n.entries == null) return entries;
        for (Entry<T> e: n.entries) {
            if (e.exact) {
                if (p.equals(e.path)) {
                    entries.add(e);
                }
            } else {
                // Node represents ancestor or equal path
                entries.add(e);
            }
        }
        return entries;
    }

    private void load(InputStream is) {

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                if (++lineNum > this.maxLines) {
                    throw new IllegalStateException("File contains too many lines.");
                }
                if (line.length() > this.maxLineSize) {
                    throw new IllegalStateException(
                            "Line number " + lineNum + " is too long");
                }
                line = line.trim();
                line = stripComments(line);
                if ("".equals(line)) {
                    continue;
                }
                if (line.startsWith("/")) {
                    parseLine(line, lineNum, this.root);
                }
            }
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    private void parseLine(String line, int lineNum, Node root) {
        String[] kv = TextUtils.parseKeyValue(line, '=', TextUtils.TRIM 
                                                         | TextUtils.IGNORE_UNESCAPED_SEP_IN_VALUE 
                                                         | TextUtils.IGNORE_INVALID_ESCAPE);
        if (kv[1] == null) return;
        String lhs = kv[0];
        String rhs = kv[1];
        if ("".equals(lhs) || "".equals(rhs)) {
            return;
        }
        try {
            registerNode(lhs, rhs, root);
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Failed to create configuration entry at line %s: %s: %s",
                    lineNum, e.getClass().getSimpleName(), e.getMessage()), e);
        }
    }
    
    private void registerNode(String lhs, String rhs, Node root) {
        boolean exact = false;
        if (lhs.endsWith("/") && !lhs.equals("/")) {
            lhs = lhs.substring(0, lhs.length() - 1);
            exact = true;
        }

        int pathEndPos = lhs.length();
        int leftBracketPos = lhs.indexOf("[");
        int rightBracketPos = -1;
        if (leftBracketPos != -1) {
            rightBracketPos = lhs.lastIndexOf(']');
            if (rightBracketPos == -1 || rightBracketPos < leftBracketPos) {
                return;
            }
            pathEndPos = leftBracketPos;
        }
        String pathStr = lhs.substring(0, pathEndPos);
        // Allow to specify exactly root as '//':
        if (!"//".equals(pathStr)) {
            pathStr = pathStr.replaceAll("/+", "/");
        }
        if (!"/".equals(pathStr) && pathStr.endsWith("/")) {
            exact = true;
            pathStr = pathStr.substring(0, pathStr.length()-1);
        }

        String qualifierStr = leftBracketPos == -1 ? null
                : lhs.substring(leftBracketPos + 1, rightBracketPos).trim();
        List<Qualifier> qualifiers = new ArrayList<>();
        if (qualifierStr != null && !qualifierStr.isEmpty()) {
            String[] splitQualifiers = TextUtils.parseCsv(qualifierStr, ',', TextUtils.TRIM 
                                                                             | TextUtils.DISCARD
                                                                             | TextUtils.IGNORE_INVALID_ESCAPE);
            for (String qualifier : splitQualifiers) {
                String[]  kv = TextUtils.parseKeyValue(qualifier, ':');
                if (kv[0].isEmpty() || kv[1] == null || kv[1].isEmpty()) {
                    return;
                }
                qualifiers.add(new Qualifier(kv[0], kv[1]));
            }
        }

        Path path = Path.fromString(pathStr);
        Node n = root;
        for (String name : path.getElements()) {
            if (name.equals("/")) {
                continue;
            }
            Node child = n.children.get(name);
            if (child == null) {
                child = new Node();
                n.children.put(name, child);
            }
            n = child;
        }
        if (n.entries == null) {
            n.entries = new ArrayList<>();
        }

        T value = valueFactory.apply(rhs);
        n.entries.add(new Entry<>(qualifiers, value, exact, path));
    }

    private class Node {

        private Map<String, Node> children = new LinkedHashMap<>();
        private List<Entry<T>> entries = null;

        @Override
        public String toString() {
            try {
                TreePrinter tp = new TreePrinter();
                return tp.render(getTreeModel());
            } catch (Throwable t) {
                System.err.println("T: " + t);
                return "FUCK";
            }
        }

        private TreePrinter.Model getTreeModel() {
            ModelBuilder builder = TreePrinter.newModelBuilder();
            builder.add("/");
            children.forEach((k,v) -> {
                addChild(v, builder);
            });

            return builder.getModel();
        }

        private ModelBuilder addChild(Node n, ModelBuilder builder) {
            boolean added = false;
            if (n.entries != null) {
                Path p = n.entries.iterator().next().path;
                builder.addChild(p.toString());
                for (Entry<?> e : n.entries) {
                    builder.addAttribute(e);
                }
                added = true;
            }

            n.children.forEach((k,v) -> {
                addChild(v, builder);
            });

            if (added) {
                return builder.toParent();
            } else {
                return builder;
            }
        }

    }

    private String stripComments(String line) {
        if (line.startsWith("#")) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '#') {
                break;
            }
            result.append(c);
        }
        return result.toString();
    }

    @Override
    public String toString() {
        return root.toString();
    }

    /**
     * A config entry represents a single line in the configuration file. Note
     * multiple lines may have the same path mapping, and thus there can be
     * multiple instances of this class for a single path as returned by
     * {@link #get(vtk.repository.Path) }.
     *
     * @param <T> value type of config entries
     */
    public static final class Entry<T> {

        /**
         * List of qualifiers associated with the entry.
         */
        public final List<Qualifier> qualifiers;

        /**
         * Value associated with the entry.
         */
        public final T value;

        /**
         * Tells whether the entry applies exactly to the path.
         */
        public final boolean exact;

        /**
         * The path which this entry is associated with.
         */
        public final Path path;

        private Entry(List<Qualifier> qualifiers, T value, boolean exact, Path path) {
            this.qualifiers = Collections.unmodifiableList(qualifiers);
            this.value = value;
            this.exact = exact;
            this.path = path;
        }

        @Override
        public String toString() {
            return "Entry{" + "qualifiers=" + qualifiers + ", value=" + value + ", exact=" + exact + ", path=" + path + '}';
        }

    }

    /**
     * A qualifier is simply a generic name-value pair of strings.
     */
    public static final class Qualifier {

        /**
         * Qualifier name
         */
        public final String name;

        /**
         * Qualifier value
         */
        public final String value;

        public Qualifier(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            return name + ":" + value;
        }

    }
    
}
