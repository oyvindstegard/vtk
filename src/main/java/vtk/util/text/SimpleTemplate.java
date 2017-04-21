/* Copyright (c) 2013, University of Oslo, Norway
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
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Simple string template with variable substitution. 
 * 
 * <p>Configurable prefix and suffix delimiters (e.g. <code>${</code> and
 * <code>}</code>), allowing different styles of template syntax:
 * <ul>
 * <li><code>${foobar}</code>
 * <li><code>%{foobar]</code>
 * <li><code>...</code>
 * </ul> 
 * </p>
 * <p>Variable substitution and output writing is controlled via an implementation supplied by the caller.</p>
 * 
 * <p>Sample usage:
 * <pre>
 *   SimpleTemplate t = SimpleTemplate.compile("My template with a ${variable}.");
 *   t.apply(v -&gt; "value of v", s -&gt; System.out.print(s));
 * </pre>
 * will invoke:
 * <ol><li>Second argument <code>Consumer</code> with value <code>"My template with a "</code>
 * <li>First argument <code>Funtion</code> with value <code>"variable"</code>, returning <code>"piece of text"</code>
 * <li>Second argument <code>Consumer</code> with value <code>"piece of text"</code>
 * </ol>
 *
 * <p>By default, no escape mechanism is enabled. By using parsing flags, escape
 * handling can be enabled. A backslash in template will then prevent
 * interpretation of the next character in input. This can be used to include
 * substitution prefix literally in output or suffix literally in variable. To
 * get a literal backslash in output, use two consecutive backslashes in
 * template.
 * 
 */
public class SimpleTemplate {

    /**
     * The character used for escaping interpretation of delimiter prefix in template.
     */
    public static final char ESC = '\\';
    
    /**
     * Default parsing flag that disables all handling of escaping. This will make
     * outputting a literal string containing the prefix or suffix of variable
     * delimiter impossible (unless output by variable handler itself), since the
     * delimiters will always be interpreted as parts of variable expression,
     * regardless of any preceding escape character. All escape characters
     * that occur in template input will be kept in rendered output.
     */
    public static final int ESC_NO_HANDLING = 0;
    
    /**
     * Parsing flag which enables escape handling and removes one level of
     * escape characters. Escape chars are removed from output even if they
     * form an invalid escape sequence with the next character.
     */
    public static final int ESC_UNESCAPE = 1;
    
    /**
     * Parsing flag which enables escape handling and keeps <em>all</em>
     * escape characters in rendered output as they occur in input. The
     * escape char will still have the effect of escaping the variable delimiters
     * in this mode, but no escape chars will be consumed in the process.
     */
    public static final int ESC_KEEP = 2;
    
    /**
     * Parsing flag which enables escape handling where only escape
     * chars that form invalid or unknown escape sequences with the next character are
     * kept in rendered output (perhaps for further processing at later stage).
     * <p>
     * Escape chars that form valid escape sequences will be consumed in this mode of
     * operation.<br/>
     * Valid escape sequences are:
     * <ol>
     *   <li>An escape character preceding the delimiter prefix when outside variable.
     *   <li>An escape character preceding the delimiter suffix when inside a variable.
     *   <li>An escape character escaping itself.
     * </ol>
     */
    public static final int ESC_KEEP_INVALID = 4;

    /**
     * Applies a compiled template using a supplied variabler resolver and writer.
     * @param resolver resolve a variable expression to a string value, return <code>null</code> for no value present
     * @param writer accept string values which when combined in sequence gives the template transformation result.
     */
    public void apply(Function<String,String> resolver, Consumer<String> writer) {
        for (Node node: nodes) {
            String s = node.text;
            if (node.var) {
                s = resolver.apply(s);
            }
            if (s != null) {
                writer.accept(s);
            }
        }
    }

    /**
     * Compiles a template using <code>${</code> and <code>}</code> as delimiters
     * and no escape handling (no flags).
     * @see #compile(java.lang.String, java.lang.String, java.lang.String, int) 
     */
    public static SimpleTemplate compile(String template) {
        return compile(template, "${", "}");
    }

    /**
     * Compiles a template using <code>${</code> and <code>}</code> as delimiters
     * with extra parse flags.
     * @see #compile(java.lang.String, java.lang.String, java.lang.String, int) 
     */
    public static SimpleTemplate compile(String template, int parseFlags) {
        return compile(template, "${", "}", parseFlags);
    }
    
    /**
     * Compiles a template using custom delimiters and no escape handling (no flags).
     * @see #compile(java.lang.String, java.lang.String, java.lang.String, int) 
     */
    public static SimpleTemplate compile(String template, String delimPrefix,
                                         String delimSuffix) {
        return compile(template, delimPrefix, delimSuffix, ESC_NO_HANDLING);
    }
    
    /**
     * Compiles a template with custom placeholder delimiters. If escape
     * handling is enabled through parsing flags, you may escape placeholder
     * interpretation by preceding
     * <code>delimPrefix</code> with {@link #ESC the escape character} in
     * template string:
     * <code>"foo \\${literal-me}"</code>. You may also escape delimiter suffix
     * inside variable in the same way. If escaping is enabled, use two
     * {@link #ESC escape chars} to get a literal escape char in output.
     * <p>
     * Parsing flags that can be used:
     * <ul>
     * <li>{@link #ESC_NO_HANDLING}  - Escape handling is turned off (default).
     * <li>{@link #ESC_UNESCAPE}     - Turn on escape handling and unescape
     *                                 all escape sequences.
     * <li>{@link #ESC_KEEP}         - Turn on escape handling and keep all escape chars
     *                                 verbatim in output as well.
     * <li>{@link #ESC_KEEP_INVALID} - Turn on escape handling, and keep all escape chars
     *                                 that form invalid or unknown escape sequences in output.
     * </ul>
     * 
     * Nesting placeholders is entirely unsupported.
     */
    public static SimpleTemplate compile(String template, String delimPrefix,
                                         String delimSuffix, int parseFlags) {
        if (template == null) {
            throw new IllegalArgumentException("Argument 'template' is null");
        }
        if (delimPrefix == null || delimPrefix.isEmpty()) {
            throw new IllegalArgumentException("Argument 'delimPrefix' null or empty");
        }
        if (delimSuffix == null || delimSuffix.isEmpty()) {
            throw new IllegalArgumentException("Argument 'delimSuffix' null or empty");
        }
        List<Node> nodes = new ArrayList<Node>();
        StringBuilder token = new StringBuilder();
        boolean esc = false;
        boolean var = false;
        for (int i=0; i<template.length(); i++) {
            char c = template.charAt(i);
            if (c == ESC) {
                if (esc) {
                    token.append(ESC); // Valid escape
                    esc = false;
                } else {
                    if (parseFlags == ESC_NO_HANDLING || hasFlag(ESC_KEEP, parseFlags)) {
                        token.append(ESC);
                    }
                    esc = hasFlag(ESC_KEEP|ESC_KEEP_INVALID|ESC_UNESCAPE, parseFlags);
                }
                continue;
            }
            if (!var && template.startsWith(delimPrefix, i)) {
                if (esc) { // Valid escape
                    esc = false;
                    token.append(c);
                    continue;
                }
                if (token.length() > 0) {
                    nodes.add(textNode(token.toString()));
                    token.setLength(0);
                }
                var = true;
                i += delimPrefix.length()-1;
                continue;
            }
            if (var && template.startsWith(delimSuffix, i)) {
                if (esc) { // Valid escape
                    esc = false;
                    token.append(c);
                    continue;
                }
                if (token.length() > 0) {
                    nodes.add(varNode(token.toString()));
                    token.setLength(0);
                }
                var = false;
                i += delimSuffix.length()-1;
                continue;
            }
            if (esc) {
                if (hasFlag(ESC_KEEP_INVALID, parseFlags) 
                         && !hasFlag(ESC_KEEP, parseFlags)) {
                    token.append(ESC);
                }
                esc = false;
            }
            token.append(c);
        }
        if (token.length() > 0 || var) {
            if (var) {
                // Incomplete placeholder syntax at end of input, output raw
                token.insert(0, delimPrefix);
            }
            nodes.add(textNode(token.toString()));
        }
        
        return new SimpleTemplate(nodes);
    }
    
    private static boolean hasFlag(int flag, int flags) {
        return (flag & flags) != 0;
    }
    
    private List<Node> nodes;
    
    private static Node textNode(String text) {
        return new Node(text, false);
    }
    
    private static Node varNode(String text) {
        return new Node(text, true);
    }
    
    private static class Node {
        boolean var = false;
        String text;
        private Node(String text, boolean var) {
            this.text = text;
            this.var = var;
        }

        @Override
        public String toString() {
            return var ? "var:" + text : "text:\"" + text + "\"";
        }
    }
    
    private SimpleTemplate(List<Node> nodes) {
        this.nodes = nodes;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + nodes.toString();
    }

}
