/* Copyright (c) 2008, University of Oslo, Norway
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
package vtk.text.html;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import vtk.repository.Path;
import vtk.web.service.URL;

public class HtmlUtil {

    private HtmlPageParser htmlParser;
    private Map<String, String> htmlEntityMap = new HashMap<String, String>();

    public HtmlPage parse(InputStream in, String encoding) throws Exception {
        return this.htmlParser.parse(in, encoding);
    }

    public HtmlFragment parseFragment(String html) throws Exception {
        return this.htmlParser.parseFragment(html);
    }

    public String flatten(String html) {
        try {
            StringBuilder sb = new StringBuilder();
            HtmlFragment fragment = this.htmlParser.parseFragment(html);
            for (HtmlContent c : fragment.getContent()) {
                sb.append(flatten(c));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String flatten(HtmlContent content) {
        StringBuilder sb = new StringBuilder();
        if (content instanceof HtmlElement) {
            HtmlElement htmlElement = (HtmlElement) content;
            for (HtmlContent child : htmlElement.getChildNodes()) {
                sb.append(flatten(child));
            }
        } else if (content instanceof HtmlText) {
            String str = content.getContent();
            sb.append(processHtmlEntities(str));
        }
        return sb.toString();
    }

    public HtmlFragment linkResolveFilter(String html, URL baseURL, URL requestURL, boolean protocolRelative) {
        HtmlFragment fragment;
        try {
            fragment = this.htmlParser.parseFragment(html);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        LinkResolveFilter filter = new LinkResolveFilter(baseURL, requestURL, protocolRelative);
        fragment.filter(filter);

        return fragment;
    }

    /**
     * Encodes chars to the basic built in character entities defined by XML:
     * <ul>
     *   <li>' to &amp;apos;
     *   <li>&amp; to &amp;amp;
     *   <li>&quot; to &amp;quot;
     *   <li>&lt; to &amp;lt;
     *   <li>&gt; to &amp;gt;
     * </ul>
     * 
     * @param html string
     * @return string with basic entity ref replacements for affected chars.
     */
    public static String encodeBasicEntities(String html) {
        StringBuilder result = new StringBuilder(html.length());
        for (int i = 0; i < html.length(); i++) {
            char c = html.charAt(i);
            switch (c) {
            case '&':
                result.append("&amp;");
                break;
            case '"':
                result.append("&quot;");
                break;
            case '\'':
                result.append("&apos;");
                break;
            case '<':
                result.append("&lt;");
                break;
            case '>':
                result.append("&gt;");
                break;
            default:
                result.append(c);
                break;
            }
        }
        return result.toString();
    }

    /**
     * Decodes the basic built in entities defined by XML:
     * <ul>
     *   <li>&amp;apos; to '
     *   <li>&amp;amp; to &amp;
     *   <li>&amp;quot; to &quot;
     *   <li>&amp;lt; to &lt;
     *   <li>&amp;gt; to &gt;
     * </ul>
     * 
     * @param html string with entity refs.
     * @return string with basic entity refs resolved to chars.
     */
    public static String decodeBasicEntities(String html) {
        StringBuilder buf = new StringBuilder(html.length());
        for (int i=0; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '&') {
                if (html.startsWith("&amp;", i)) {
                    buf.append('&');
                    i += 4;
                } else if (html.startsWith("&quot;", i)) {
                    buf.append('"');
                    i += 5;
                } else if (html.startsWith("&apos;", i)) {
                    buf.append('\'');
                    i += 5;
                } else if (html.startsWith("&lt;", i)) {
                    buf.append('<');
                    i += 3;
                } else if (html.startsWith("&gt;", i)) {
                    buf.append('>');
                    i += 3;
                } else {
                    buf.append('&');
                }
            } else {
                buf.append(c);
            }
        }

        return buf.toString();
    }
    
    private StringBuilder processHtmlEntities(String content) {
        StringBuilder result = new StringBuilder(content.length());
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '&') {
                int j = i + 1;

                String entity = null;
                while (j < content.length()) {
                    char next = content.charAt(j);
                    boolean validChar = validEntityChar(next);
                    if (!validChar && next == ';' && j > i + 1) {
                        entity = content.substring(i + 1, j);
                        i = j;
                        break;
                    } else if (!validChar) {
                        break;
                    }
                    j++;
                }
                if (entity != null) {
                    String mapped = mapEntity(entity);
                    if (mapped != null) {
                        result.append(mapped);
                    } else {
                        result.append("&").append(entity).append(";");
                    }
                }
            } else {
                result.append(ch);
            }
        }
        return result;
    }
    
    private String mapEntity(String entity) {
        if (entity.startsWith("#")) {
            try {
                entity = entity.startsWith("#x") || entity.startsWith("#X") ?
                        "0" + entity.substring(1) : entity.substring(1);
                int codePoint = Integer.decode(entity);
                char[] ref = Character.toChars(codePoint);
                return new String(ref);
            } catch (Exception e) {
                return null;
            }
        }
        return htmlEntityMap.get(entity);
    }

    private boolean validEntityChar(char c) {
        return c != ';' && ('#' == c || 
                ('a' <= c && 'z' >= c) || 
                ('A' <= c && 'Z' >= c) || 
                ('0' <= c && '9' >= c));
    }

    public void setHtmlParser(HtmlPageParser htmlParser) {
        this.htmlParser = htmlParser;
    }

    public void setHtmlEntityMap(Map<String, String> htmlEntityMap) {
        this.htmlEntityMap = htmlEntityMap;
    }

    private static class LinkResolveFilter implements HtmlPageFilter {
        private URL base;
        private URL requestURL;
        private boolean protocolRelative = false;

        public LinkResolveFilter(URL base, URL requestURL, boolean protocolRelative) {
            this.base = base;
            this.requestURL = requestURL;
            this.protocolRelative = protocolRelative;
        }

        @Override
        public NodeResult filter(HtmlContent node) {
            if (node instanceof HtmlElement) {
                HtmlElement elem = (HtmlElement) node;
                if ("img".equalsIgnoreCase(elem.getName())) {
                    processURL(elem, "src");
                } else if ("a".equalsIgnoreCase(elem.getName())) {
                    processURL(elem, "href");
                }
            }
            return NodeResult.keep;
        }

        private void processURL(HtmlElement elem, String srcAttr) {
            if (elem.getAttribute(srcAttr) == null) {
                return;
            }
            HtmlAttribute attr = elem.getAttribute(srcAttr);
            if (attr == null || !attr.hasValue()) {
                return;
            }
            String val = attr.getValue();

            val = decodeBasicEntities(val);
            if (val.trim().equals("")) {
                return;
            }
            if (URL.isRelativeURL(val)) {
                if (base.getHost().equals(requestURL.getHost())) {
                    try {
                        URL url = this.base.relativeURL(val);
                        val = this.protocolRelative ? 
                                url.protocolRelativeURL() : url.getPathRepresentation();
                        attr.setValue(encodeBasicEntities(val));
                    } catch (Exception e) {
                        return;
                    }
                } else {
                    try {
                        Path path = Path.fromString(val);
                        URL url = new URL(base.getProtocol(), base.getHost(), path);
                        attr.setValue(encodeBasicEntities(url.toString()));
                    } catch (Throwable t) {
                    }
                }
            } else {
                if (base.getHost().equals(requestURL.getHost())) {
                    try {
                        URL url = URL.parse(val);
                        if (url.getHost().equals(this.requestURL.getHost())) {
                            val = this.protocolRelative ? 
                                    url.protocolRelativeURL() : url.getPathRepresentation();
                            attr.setValue(encodeBasicEntities(val));
                        }
                    } catch (Throwable t) {
                    }
                }
            }
        }

        @Override
        public boolean match(HtmlPage page) {
            return true;
        }
    }

}
