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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import vtk.repository.Path;
import vtk.web.service.URL;

public class HtmlUtil {
    private static HtmlPageParser parser = new HtmlPageParser();
    
    public static HtmlPage parse(String html) throws Exception {
        Charset encoding = StandardCharsets.UTF_8;
        InputStream in = new ByteArrayInputStream(html.getBytes(encoding));
        return parser.parse(in, encoding.name());
    }
    
    public static HtmlFragment parseFragment(String html) throws Exception {
        return parser.parseFragment(html);
    }

    public static String flatten(String html) {
        try {
            StringBuilder sb = new StringBuilder();
            HtmlFragment fragment = parser.parseFragment(html);
            for (HtmlContent c : fragment.getContent()) {
                sb.append(flatten(c));
            }
            return sb.toString();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String flatten(HtmlContent content) {
        StringBuilder sb = new StringBuilder();
        if (content instanceof HtmlElement) {
            HtmlElement htmlElement = (HtmlElement) content;
            for (HtmlContent child : htmlElement.getChildNodes()) {
                sb.append(flatten(child));
            }
        }
        else if (content instanceof HtmlText) {
            String str = content.getContent();
            sb.append(processHtmlEntities(str));
        }
        return sb.toString();
    }

    public static HtmlFragment linkResolveFilter(String html, URL baseURL, URL requestURL, boolean protocolRelative) {
        HtmlFragment fragment;
        try {
            fragment = parser.parseFragment(html);
        }
        catch (Exception e) {
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
                }
                else if (html.startsWith("&quot;", i)) {
                    buf.append('"');
                    i += 5;
                }
                else if (html.startsWith("&apos;", i)) {
                    buf.append('\'');
                    i += 5;
                }
                else if (html.startsWith("&lt;", i)) {
                    buf.append('<');
                    i += 3;
                }
                else if (html.startsWith("&gt;", i)) {
                    buf.append('>');
                    i += 3;
                }
                else {
                    buf.append('&');
                }
            }
            else {
                buf.append(c);
            }
        }

        return buf.toString();
    }

    private static StringBuilder processHtmlEntities(String content) {
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
                    }
                    else if (!validChar) {
                        break;
                    }
                    j++;
                }
                if (entity != null) {
                    String mapped = mapEntity(entity);
                    if (mapped != null) {
                        result.append(mapped);
                    }
                    else {
                        result.append("&").append(entity).append(";");
                    }
                }
            }
            else {
                result.append(ch);
            }
        }
        return result;
    }

    private static String mapEntity(String entity) {
        if (entity.startsWith("#")) {
            try {
                entity = entity.startsWith("#x") || entity.startsWith("#X") ?
                        "0" + entity.substring(1) : entity.substring(1);
                        int codePoint = Integer.decode(entity);
                        char[] ref = Character.toChars(codePoint);
                        return new String(ref);
            }
            catch (Exception e) {
                return null;
            }
        }
        return entitiesMap.get(entity);
    }

    private static boolean validEntityChar(char c) {
        return c != ';' && ('#' == c || 
                ('a' <= c && 'z' >= c) || 
                ('A' <= c && 'Z' >= c) || 
                ('0' <= c && '9' >= c));
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
                }
                else if ("a".equalsIgnoreCase(elem.getName())) {
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
                    }
                    catch (Exception e) {
                        return;
                    }
                }
                else {
                    try {
                        Path path = Path.fromString(val);
                        URL url = new URL(base.getProtocol(), base.getHost(), path);
                        attr.setValue(encodeBasicEntities(url.toString()));
                    }
                    catch (Throwable t) {
                    }
                }
            }
            else {
                if (base.getHost().equals(requestURL.getHost())) {
                    try {
                        URL url = URL.parse(val);
                        if (url.getHost().equals(this.requestURL.getHost())) {
                            val = this.protocolRelative ? 
                                    url.protocolRelativeURL() : url.getPathRepresentation();
                                    attr.setValue(encodeBasicEntities(val));
                        }
                    }
                    catch (Throwable t) {
                    }
                }
            }
        }

        @Override
        public boolean match(HtmlPage page) {
            return true;
        }
    }

    private static Map<String, String> entitiesMap = new HashMap<>();
    static {
        Map<String, String> map = new HashMap<>();
        // XML/XHTML:
        map.put("apos", "&#39;");

        // http://www.w3.org/TR/REC-html40/sgml/entities.html
        // Section 24.2: "Character entity references for ISO 8859-1 characters"

        // Latin 1 entities
        map.put("nbsp", "&#160;");
        map.put("iexcl", "¡");
        map.put("cent", "¢");
        map.put("pound", "£");
        map.put("curren", "¤");
        map.put("yen", "¥");
        map.put("brvbar", "¦");
        map.put("sect", "§");
        map.put("uml", "¨");
        map.put("copy", "©");
        map.put("ordf", "ª");
        map.put("laquo", "«");
        map.put("not", "¬");
        map.put("shy", "­");
        map.put("reg", "®");
        map.put("macr", "¯");
        map.put("deg", "°");
        map.put("plusmn", "±");
        map.put("sup2", "²");
        map.put("sup3", "³");
        map.put("acute", "´");
        map.put("micro", "µ");
        map.put("para", "¶");
        map.put("middot", "·");
        map.put("cedil", "¸");
        map.put("sup1", "¹");
        map.put("ordm", "º");
        map.put("raquo", "»");
        map.put("frac14", "¼");
        map.put("frac12", "½");
        map.put("frac34", "¾");
        map.put("iquest", "¿");
        map.put("Agrave", "À");
        map.put("Aacute", "Á");
        map.put("Acirc", "Â");
        map.put("Atilde", "Ã");
        map.put("Auml", "Ä");
        map.put("Aring", "Å");
        map.put("AElig", "Æ");
        map.put("Ccedil", "Ç");
        map.put("Egrave", "È");
        map.put("Eacute", "É");
        map.put("Ecirc", "Ê");
        map.put("Euml", "Ë");
        map.put("Igrave", "Ì");
        map.put("Iacute", "Í");
        map.put("Icirc", "Î");
        map.put("Iuml", "Ï");
        map.put("ETH", "Ð");
        map.put("Ntilde", "Ñ");
        map.put("Ograve", "Ò");
        map.put("Oacute", "Ó");
        map.put("Ocirc", "Ô");
        map.put("Otilde", "Õ");
        map.put("Ouml", "Ö");
        map.put("times", "×");
        map.put("Oslash", "Ø");
        map.put("Ugrave", "Ù");
        map.put("Uacute", "Ú");
        map.put("Ucirc", "Û");
        map.put("Uuml", "Ü");
        map.put("Yacute", "Ý");
        map.put("THORN", "Þ");
        map.put("szlig", "ß");
        map.put("agrave", "à");
        map.put("aacute", "á");
        map.put("acirc", "â");
        map.put("atilde", "ã");
        map.put("auml", "ä");
        map.put("aring", "å");
        map.put("aelig", "æ");
        map.put("ccedil", "ç");
        map.put("egrave", "è");
        map.put("eacute", "é");
        map.put("ecirc", "ê");
        map.put("euml", "ë");
        map.put("igrave", "ì");
        map.put("iacute", "í");
        map.put("icirc", "î");
        map.put("iuml", "ï");
        map.put("eth", "ð");
        map.put("ntilde", "ñ");
        map.put("ograve", "ò");
        map.put("oacute", "ó");
        map.put("ocirc", "ô");
        map.put("otilde", "õ");
        map.put("ouml", "ö");
        map.put("divide", "÷");
        map.put("oslash", "ø");
        map.put("ugrave", "ù");
        map.put("uacute", "ú");
        map.put("ucirc", "û");
        map.put("uuml", "ü");
        map.put("yacute", "ý");
        map.put("thorn", "þ");
        map.put("yuml", "ÿ");

        // Section 24.3: "Character entity references for symbols, 
        // mathematical symbols, and Greek letters"

        // Latin Extended-B
        map.put("fnof", "&#402;");
        // Greek
        map.put("Alpha", "&#913;");
        map.put("Beta", "&#914;");
        map.put("Gamma", "&#915;");
        map.put("Delta", "&#916;");
        map.put("Epsilon", "&#917;");
        map.put("Zeta", "&#918;");
        map.put("Eta", "&#919;");
        map.put("Theta", "&#920;");
        map.put("Iota", "&#921;");
        map.put("Kappa", "&#922;");
        map.put("Lambda", "&#923;");
        map.put("Mu", "&#924;");
        map.put("Nu", "&#925;");
        map.put("Xi", "&#926;");
        map.put("Omicron", "&#927;");
        map.put("Pi", "&#928;");
        map.put("Rho", "&#929;");
        // there is no Sigmaf, and no U+03A2 character either
        map.put("Sigma", "&#931;");
        map.put("Tau", "&#932;");
        map.put("Upsilon", "&#933;");
        map.put("Phi", "&#934;");
        map.put("Chi", "&#935;");
        map.put("Psi", "&#936;");
        map.put("Omega", "&#937;");
        map.put("alpha", "&#945;");
        map.put("beta", "&#946;");
        map.put("gamma", "&#947;");
        map.put("delta", "&#948;");
        map.put("epsilon", "&#949;");
        map.put("zeta", "&#950;");
        map.put("eta", "&#951;");
        map.put("theta", "&#952;");
        map.put("iota", "&#953;");
        map.put("kappa", "&#954;");
        map.put("lambda", "&#955;");
        map.put("mu", "&#956;");
        map.put("nu", "&#957;");
        map.put("xi", "&#958;");
        map.put("omicron", "&#959;");
        map.put("pi", "&#960;");
        map.put("rho", "&#961;");
        map.put("sigmaf", "&#962;");
        map.put("sigma", "&#963;");
        map.put("tau", "&#964;");
        map.put("upsilon", "&#965;");
        map.put("phi", "&#966;");
        map.put("chi", "&#967;");
        map.put("psi", "&#968;");
        map.put("omega", "&#969;");
        map.put("thetasym", "&#977;");
        map.put("upsih", "&#978;");
        map.put("piv", "&#982;");
        // General Punctuation
        map.put("bull", "&#8226;");
        // bullet is NOT the same as bullet operator, U+2219
        map.put("hellip", "&#8230;");
        map.put("prime", "&#8242;");
        map.put("Prime", "&#8243;");
        map.put("oline", "&#8254;");
        map.put("frasl", "&#8260;");
        // Letterlike Symbols
        map.put("weierp", "&#8472;");
        map.put("image", "&#8465;");
        map.put("real", "&#8476;");
        map.put("trade", "&#8482;");
        map.put("alefsym", "&#8501;");
        // alef symbol is NOT the same as hebrew letter alef, 
        // U+05D0 although the same glyph could be used to depict both 
        //  characters

        // Arrows
        map.put("larr", "&#8592;");
        map.put("uarr", "&#8593;");
        map.put("rarr", "&#8594;");
        map.put("darr", "&#8595;");
        map.put("harr", "&#8596;");
        map.put("crarr", "&#8629;");
        map.put("lArr", "&#8656;");
        // ISO 10646 does not say that lArr is the same as the 
        // 'is implied by' arrow but also does not have any other character 
        //  for that function. So ? lArr can be used for 'is implied by' 
        // as ISOtech suggests
        map.put("uArr", "&#8657;");
        map.put("rArr", "&#8658;");
        // ISO 10646 does not say this is the 'implies' character but 
        // does not have another character with this function 
        //  so ? rArr can be used for 'implies' as ISOtech suggests
        map.put("dArr", "&#8659;");
        map.put("hArr", "&#8660;");
        // Mathematical Operators
        map.put("forall", "&#8704;");
        map.put("part", "&#8706;");
        map.put("exist", "&#8707;");
        map.put("empty", "&#8709;");
        map.put("nabla", "&#8711;");
        map.put("isin", "&#8712;");
        map.put("notin", "&#8713;");
        map.put("ni", "&#8715;");
        // should there be a more memorable name than 'ni'?
        map.put("prod", "&#8719;");
        // prod is NOT the same character as U+03A0 'greek 
        // capital letter pi' though the same glyph might be used for both
        map.put("sum", "&#8721;");
        // sum is NOT the same character as U+03A3 'greek capital 
        // letter sigma' though the same glyph might be used for 
        // both
        map.put("minus", "&#8722;");
        map.put("lowast", "&#8727;");
        map.put("radic", "&#8730;");
        map.put("prop", "&#8733;");
        map.put("infin", "&#8734;");
        map.put("ang", "&#8736;");
        map.put("and", "&#8743;");
        map.put("or", "&#8744;");
        map.put("cap", "&#8745;");
        map.put("cup", "&#8746;");
        map.put("int", "&#8747;");
        map.put("there4", "&#8756;");
        map.put("sim", "&#8764;");
        // tilde operator is NOT the same character as the tilde, 
        // U+007E, although the same glyph might be used to represent 
        // both
        map.put("cong", "&#8773;");
        map.put("asymp", "&#8776;");
        map.put("ne", "&#8800;");
        map.put("equiv", "&#8801;");
        map.put("le", "&#8804;");
        map.put("ge", "&#8805;");
        map.put("sub", "&#8834;");
        map.put("sup", "&#8835;");
        // note that nsup, 'not a superset of, U+2283' is not covered by the 
        // Symbol font encoding and is not included. 
        // Should it be, for symmetry? It is in ISOamsn
        map.put("nsub", "&#8836;");
        map.put("sube", "&#8838;");
        map.put("supe", "&#8839;");
        map.put("oplus", "&#8853;");
        map.put("otimes", "&#8855;");
        map.put("perp", "&#8869;");
        map.put("sdot", "&#8901;");
        // dot operator is NOT the same character as U+00B7 middle dot
        // Miscellaneous Technical
        map.put("lceil", "&#8968;");
        map.put("rceil", "&#8969;");
        map.put("lfloor", "&#8970;");
        map.put("rfloor", "&#8971;");
        map.put("lang", "&#9001;");
        // lang is NOT the same character as U+003C 'less than' or 
        // U+2039 'single left-pointing angle quotation mark'
        map.put("rang", "&#9002;");
        // rang is NOT the same character as U+003E 'greater than' 
        // or U+203A 'single right-pointing angle quotation mark'
        // Geometric Shapes
        map.put("loz", "&#9674;");
        // Miscellaneous Symbols
        map.put("spades", "&#9824;");
        // black here seems to mean filled as opposed to hollow
        map.put("clubs", "&#9827;");
        map.put("hearts", "&#9829;");
        map.put("diams", "&#9830;");



        // Section 24.4.1 ("markup-significant and internationalization characters")

        map.put("quot", "&#34;");
        map.put("amp", "&#38;");

        map.put("lt", "&#60;");
        map.put("gt", "&#62;");
        // Latin Extended-A
        map.put("OElig", "&#338;");
        map.put("oelig", "&#339;");
        map.put("Scaron", "&#352;");
        map.put("scaron", "&#353;");
        map.put("Yuml", "&#376;");
        // Spacing Modifier Letters
        map.put("circ", "&#710;");
        map.put("tilde", "&#732;");
        // General Punctuation
        map.put("ensp", "&#8194;");
        map.put("emsp", "&#8195;");
        map.put("thinsp", "&#8201;");
        map.put("zwnj", "&#8204;");
        map.put("zwj", "&#8205;");
        map.put("lrm", "&#8206;");
        map.put("rlm", "&#8207;");
        map.put("ndash", "&#8211;");
        map.put("mdash", "&#8212;");
        map.put("lsquo", "&#8216;");
        map.put("rsquo", "&#8217;");
        map.put("sbquo", "&#8218;");
        map.put("ldquo", "&#8220;");
        map.put("rdquo", "&#8221;");
        map.put("bdquo", "&#8222;");
        map.put("dagger", "&#8224;");
        map.put("Dagger", "&#8225;");
        map.put("permil", "&#8240;");
        map.put("lsaquo", "&#8249;");
        // lsaquo is proposed but not yet ISO standardized
        map.put("rsaquo", "&#8250;");
        // rsaquo is proposed but not yet ISO standardized
        map.put("euro", "&#8364;");
        
        entitiesMap = Collections.unmodifiableMap(map);
    }
}
