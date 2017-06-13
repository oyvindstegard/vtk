/* Copyright (c) 2007, 2008, University of Oslo, Norway
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
package vtk.web.decorating;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vtk.text.html.HtmlUtil;


public class DollarSyntaxComponentParser implements TextualComponentParser {

    private static Logger logger = LoggerFactory.getLogger(DollarSyntaxComponentParser.class);

    private static final Pattern NAMESPACE_REGEX_PATTERN = Pattern.compile("[a-zA-Z]+$");
    private static final Pattern NAME_REGEX_PATTERN = Pattern.compile("[a-zA-Z]+(-[a-zA-Z]+)*\\??$");

    public ComponentInvocation[] parse(Reader reader) throws Exception {
        BufferedReader bufferedReader = new BufferedReader(reader);

        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[1024];

        int n = 0;
        while ((n = bufferedReader.read(buffer)) > 0) {
            sb.append(buffer, 0, n);
        }
        return parseInternal(sb.toString());
    }


    private ComponentInvocation[] parseInternal(String s) throws Exception {

        // Look for occurrences of:
        // ${namespace:name param1=[value] param2=[value]}
        
        // Can use SimpleTemplate for top level parsing here ?

        List<ComponentInvocation> fragmentList = new ArrayList<>();

        int contentIdx = 0;
        while (true) {
            int directiveStart = s.indexOf("${", contentIdx);
            if (directiveStart == -1) {
                break;
            }
            directiveStart += 2;

            int directiveEnd = nextIndexOf(s, '}', '\\', directiveStart);
            if (directiveEnd == -1) {
                break;
            }

            String componentContent = unescapedSubstring(
                    s, '}', '\\', directiveStart, directiveEnd);
            ComponentInvocation c = parseDirective(componentContent);

            if (logger.isDebugEnabled()) {
                logger.debug("Parsed directive '" + componentContent + "' --> " + c);
            }

            if (c == null) {
                addStaticText(fragmentList, s.substring(contentIdx, directiveStart));
                contentIdx = directiveStart;
            }
            else {
                addStaticText(fragmentList, s.substring(contentIdx, directiveStart - 2));
                addDynamicComponent(fragmentList, c);
                contentIdx = directiveEnd + 1;
            }
        }
        String finalChunk = s.substring(contentIdx, s.length());
        addStaticText(fragmentList, finalChunk);

        return fragmentList.toArray(new ComponentInvocation[fragmentList.size()]);
    }

    private ComponentInvocation parseDirective(String s) {
        if (s == null || s.trim().length() <= 1) {
            return null;
        }

        s = s.trim();

        // namespace:name param1=[value] param2=[value]
        String componentRef = parseComponentRef(s);
        if (componentRef == null) {
            if (logger.isInfoEnabled()) {
                logger.info("Unable to parse component ref: '" + s + "'");
            }
            return null;
        }

        String namespace = parseComponentNamespace(componentRef);
        if (namespace == null) {
            if (logger.isInfoEnabled()) {
                logger.info("Unable to parse component namespace from: '" + componentRef + "'");
            }
            return null;
        }
        String name = parseComponentName(componentRef);
        if (name == null) {
            if (logger.isInfoEnabled()) {
                logger.info("Unable to parse component name from: '" + componentRef + "'");
            }
            return null;
        }
        boolean optional = false;
        if (name.endsWith("?")) {
            name = name.substring(0, name.length() - 1);
            optional = true;
        }

        LinkedHashMap<String, Object> parameters = splitParameterList(s);
        if (parameters == null) {
            if (logger.isInfoEnabled()) {
                logger.info("Malformed parameter list in directive: '" + s + "'");
            }
            return null;
        }
        return new ComponentInvocationImpl(namespace, name, new HashMap<>(parameters), optional);
    }



    private String parseComponentRef(String s) {
        // namespace:name
        int delimIdx = s.indexOf(":");
        if (delimIdx == -1) {
            return null;
        }
        int endIdx = nextWhitespaceIdx(s, 0);
        if (endIdx == -1) {
            endIdx = s.length();
        }

        return s.substring(0, endIdx);
    }

    private String parseComponentNamespace(String s) {

        int delimIdx = s.indexOf(":");
        if (delimIdx == -1) {
            return null;
        }

        String namespace = s.substring(0, delimIdx);
        if (!NAMESPACE_REGEX_PATTERN.matcher(namespace).matches()) {
            return null;
        }
        return namespace.trim();
    }

    private String parseComponentName(String s) {

        int delimIdx = s.indexOf(":");
        if (delimIdx == -1) {
            return null;
        }
        String name = s.substring(delimIdx + 1, s.length());
        if (name != null) {
            name = name.trim();
        }
        if (!NAME_REGEX_PATTERN.matcher(name).matches()) {
            return null;
        }
        return name;
    }


    /**
     * Splits a parameter list into a map of <code>(name, value)</code> pairs.
     *
     * @params the raw parameter list: should be in the format:
     * <code>component:ref param1=[value1] param2=[value2] ..</code>
     * @return the parameter map, or <code>null</code> if the
     * parameter list is not well-formed.
     */
    private LinkedHashMap<String, Object> splitParameterList(String s) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        int startIdx = 0;
        while (true) {
            int equalsIdx = s.indexOf("=", startIdx);
            if (equalsIdx == -1) {
                break;
            }

            int nameStartIdx = nextWhitespaceIdx(s, startIdx) + 1;
            if (nameStartIdx == 0 || nameStartIdx > equalsIdx) {
                break;
            }

            int valueStartIdx = s.indexOf("[", equalsIdx) + 1;
            if (valueStartIdx == 0) {
                break;
            }

            int valueEndIdx = nextIndexOf(s, ']', '\\', valueStartIdx);
            if (valueEndIdx == -1) {
                // Malformed parameter list:
                return null;
            }
            String name = s.substring(nameStartIdx, equalsIdx).trim();
            String value = unescapedSubstring(
                    s, ']', '\\', valueStartIdx, valueEndIdx).trim();

            value = HtmlUtil.decodeBasicEntities(value);

            result.put(name, value);
            startIdx = valueEndIdx;
        }
        return result;
    }



    private int nextIndexOf(String string, char character, char escape, int startIndex) {
        for (int i = startIndex; i < string.length(); i++) {
            char current = string.charAt(i);
            if (current == character) {
                if (i > startIndex) {
                    char prev = string.charAt(i - 1);
                    if (prev == escape) {
                        continue;
                    }
                }
                return i;
            }
        }
        return -1;
    }

    private String unescapedSubstring(String string, char special,
            char escape, int startIndex, int endIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < endIndex; i++) {
            char current = string.charAt(i);
            if (current == escape) {
                if (i < endIndex - 1) {
                    char next = string.charAt(i + 1);
                    if (next == special) {
                        continue;
                    }
                }
            }
            sb.append(current);
        }
        return sb.toString();
    }


    private int nextWhitespaceIdx(String s, int startIdx) {
        for (int i = startIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                return i;
            }
        }
        return -1;
    }


    private void addDynamicComponent(List<ComponentInvocation> fragmentList, ComponentInvocation inv) {
        if (logger.isDebugEnabled()) {
            logger.debug("Dynamic component: " + inv);
        }
        fragmentList.add(inv);
    }


    private void addStaticText(List<ComponentInvocation> fragmentList, String s) {
        if (logger.isDebugEnabled()) {
            logger.debug("Static text: '" + s + "'");
        }
        if (fragmentList.size() > 0) {
            ComponentInvocation inv = fragmentList.get(fragmentList.size() - 1);
            if (inv instanceof StaticTextFragment) {
                StaticTextFragment f = (StaticTextFragment) inv;
                f.buffer.append(s);
                return;
            }
        }
        StaticTextFragment f = new StaticTextFragment();
        f.buffer.append(s);
        fragmentList.add(f);
    }


}
