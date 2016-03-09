/* Copyright (c) 2004, University of Oslo, Norway
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

import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

/**
 * Structured text parser and generator.
 * 
 * @version $Id: DefaultStructuredText.java,v 1.1 2004/03/29 16:29:02 storset
 *          Exp $
 */
public final class DefaultStructuredText implements StructuredText {

    private String LINE_SEPARATOR = "\n";

    protected String LIST_START = this.LINE_SEPARATOR + "- ";

    protected String LIST_CLOSE = this.LINE_SEPARATOR;

    protected String PARAGRAPH_START = this.LINE_SEPARATOR + this.LINE_SEPARATOR;

    protected String EMPHASIZE_START = "*";

    protected String EMPHASIZE_CLOSE = "*";

    protected String WEIGHT_START = "_";

    protected String WEIGHT_CLOSE = "_";

    protected Map<String, String> tagNames = new HashMap<String, String>();

    private boolean includeNewlines = false;

    private void initTagNames() {
        this.tagNames.put("root", "structuredtext");
        this.tagNames.put("plaintext", "text");
        this.tagNames.put("emphasize", "emphasize");
        this.tagNames.put("weight", "weight");
        this.tagNames.put("link", "link");
        this.tagNames.put("url", "url");
        this.tagNames.put("url-description", "description");
        this.tagNames.put("list", "list");
        this.tagNames.put("listitem", "listitem");
        this.tagNames.put("paragraph", "paragraph");
        this.tagNames.put("newline", "br");
    }

    public Set<String> getTagNames() {
        return this.tagNames.keySet();
    }

    public DefaultStructuredText() {
        initTagNames();
    }

    public void setTextMappings(Map<String, String> customMappings) {
        this.tagNames = customMappings;
    }

    private String lookupTag(String tagName) {
        String tag = this.tagNames.get(tagName);
        if (tag == null) {
            tag = tagName;
        }
        return tag;
    }

    private String reverseLookupTag(String mappedTag) {
        for (String tagName: this.tagNames.keySet()) {
            if ((this.tagNames.get(tagName)).equals(mappedTag)) { return tagName; }
        }
        return mappedTag;
    }

    /**
     * Toggle conversion of single new line characters (to <code>&lt;br&gt;</code>
     * by default) on/off
     * 
     * @param preserve
     */
    public void setIncludeNewlines(boolean preserve) {
        this.includeNewlines = preserve;
    }

    public Element parseStructuredText(String structuredText) {
        String workText = structuredText;

        workText = workText.replaceAll("\r\n", "\n");
        workText = workText.replaceAll("\r", "\n");
        Element root = new Element(lookupTag("root"));

        // split text into blocks, paragraphs, list items, etc.
        root = splitText(workText, root);

        // split list items as well
        for (Object o: root.getChildren(lookupTag("listitem"))) {
            Element child = (Element) o;
            String childText = child.getText();

            child.setText(null);
            splitText(childText, child);
        }

        Element newElement = groupListItems(root);
        return newElement;
    }

    /*
     * Places list items under a common parent ("list") element
     */
    private Element groupListItems(Element element) {
        Element root = new Element(element.getName());
        @SuppressWarnings("unchecked")
        List<Element> children = element.getChildren();
        ArrayList<Element> newChildren = new ArrayList<Element>();
        for (Iterator<Element> i = children.iterator(); i.hasNext();) {
            Element child = (Element) i.next().clone();
            if (!child.getName().equals(lookupTag("listitem"))) {
                newChildren.add(child);
            } else {
                Element listElement = new Element(lookupTag("list"));
                listElement.addContent((Element) child.clone());
                Element followingElement = null;
                while (i.hasNext()) {
                    Element nextElement = (Element) i.next().clone();
                    if (nextElement.getName().equals(lookupTag("listitem"))) {
                        listElement.addContent(nextElement);
                    } else {
                        followingElement = nextElement;
                        break;
                    }
                }
                newChildren.add(listElement);
                if (followingElement != null) {
                    newChildren.add(followingElement);
                }
            }
        }
        root.setContent(newChildren);
        return root;
    }

    /**
     * Generates structured text from an element
     */
    public String parseElement(Element element) {
        String text = regenerateText(element);
        return text;
    }

    /**
     * Generate structured text from an XML structure.
     * 
     * @param element
     *            the root <code>Element</code> of the XML structure
     * @return a <code>String</code> containing the structured text
     */
    protected String regenerateText(Element element) {

        StringBuffer buffer = new StringBuffer();

        @SuppressWarnings("unchecked")
        List<Element> children = element.getChildren();
        for (Element child: children) {

            String tagName = reverseLookupTag(child.getName());

            if (tagName.equals("plaintext")) {

                buffer.append(child.getText());

            } else if (tagName.equals("emphasize")) {

                buffer.append(this.EMPHASIZE_START + child.getText()
                        + this.EMPHASIZE_CLOSE);

            } else if (tagName.equals("weight")) {

                buffer.append(this.WEIGHT_START + child.getText() + this.WEIGHT_CLOSE);

            } else if (tagName.equals("link")) {

                String url = child.getChild(lookupTag("url")).getText();
                String description = child.getChild(
                        lookupTag("url-description")).getText();
                buffer.append("\"").append(description).append("\":").append(url);

            } else if (tagName.equals("paragraph")) {

                buffer.append("\n\n");

            } else if (tagName.equals("newline")) {

                buffer.append("\n");

            } else if (tagName.equals("list")) {

                @SuppressWarnings("unchecked")
                List<Element> childChildren = child.getChildren();
                for (Element listItem: childChildren) {

                    if (buffer.length() > 0
                            && buffer.charAt(buffer.length() - 1) != '\n'
                            && buffer.charAt(buffer.length() - 2) != '\n') {
                        buffer.append("\n");
                    }
                    buffer.append("- ").append(regenerateText(listItem));
                }
            }
        }
        return buffer.toString();
    }

    /**
     * Splits structured text into an XML structure.
     * 
     * @param structuredText
     *            the <code>String</code> to be parsed
     * @param rootElement
     *            the parent <code>Element</code> of the generated elements
     * @return the XML structure
     */
    protected Element splitText(String structuredText, Element rootElement) {

        int pos = 0;
        while (pos < structuredText.length()) {
            int nextPos = pos;

            if (listAtPos(structuredText, pos)) {
                nextPos = parseListItem(structuredText, pos, rootElement);
            } else if (paragraphAtPos(structuredText, pos)) {
                nextPos = parseParagraph(structuredText, pos, rootElement);
            } else if (emphasizeAtPos(structuredText, pos)) {
                nextPos = parseEmphasize(structuredText, pos, rootElement);
            } else if (weightAtPos(structuredText, pos)) {
                nextPos = parseWeighted(structuredText, pos, rootElement);
            } else if (linkAtPos(structuredText, pos)) {
                nextPos = parseLink(structuredText, pos, rootElement);
            } else if (newlineAtPos(structuredText, pos)) {
                nextPos = parseNewline(structuredText, pos, rootElement);
            } else {
                nextPos = parsePlainText(structuredText, pos, rootElement);
            }
            pos = nextPos;
        }
        return rootElement;
    }

    protected int parseListItem(String structuredText, int pos,
            Element rootElement) {

        int startPos = (pos == 0) ? pos + "- ".length() : pos
                + this.LIST_START.length();

        int nextPos = structuredText.indexOf(this.LIST_CLOSE, startPos);
        String itemText;
        if (nextPos < 0) {
            nextPos = structuredText.length();
            itemText = structuredText.substring(startPos, nextPos);
        } else {
            itemText = structuredText.substring(startPos, nextPos);
        }

        if (paragraphAtPos(structuredText, pos - this.PARAGRAPH_START.length())
                && this.includeNewlines) {

            // add a newline element before the list
            rootElement.addContent(new Element(lookupTag("newline")));
        }
        Element element = new Element(lookupTag("listitem"));
        element.addContent(itemText);
        rootElement.addContent(element);
        return nextPos;
    }

    protected int parseParagraph(String structuredText, int pos,
            Element rootElement) {

        rootElement.addContent(new Element(lookupTag("paragraph")));
        // Fix this in case of LINE_SEPARATOR.length() > 1 (e.g. '\r\n').
        int nextPos = pos + this.PARAGRAPH_START.length() - 1;
        if (paragraphAtPos(structuredText, nextPos + 1)) { return nextPos + 1; }
        if (!listAtPos(structuredText, nextPos)) {
            nextPos++;
        }
        return nextPos;
    }

    protected int parseNewline(String structuredText, int pos,
            Element rootElement) {

        rootElement.addContent(new Element(lookupTag("newline")));
        int nextPos = pos + this.LINE_SEPARATOR.length();
        if (listAtPos(structuredText, pos)) {
            nextPos = nextPos - this.LINE_SEPARATOR.length();
        }
        return nextPos;
    }

    protected int parseEmphasize(String structuredText, int pos,
            Element rootElement) {

        int nextPos = structuredText.indexOf(this.EMPHASIZE_CLOSE, pos
                + this.EMPHASIZE_START.length()) + 1;
        String emphasizedText = structuredText.substring(pos
                + this.EMPHASIZE_START.length(), nextPos - 1);

        Element element = new Element(lookupTag("emphasize"));
        element.addContent(emphasizedText);
        rootElement.addContent(element);
        return nextPos;
    }

    protected int parseWeighted(String structuredText, int pos,
            Element rootElement) {

        int nextPos = structuredText.indexOf(this.WEIGHT_CLOSE, pos
                + this.WEIGHT_START.length()) + 1;
        String weightedText = structuredText.substring(pos
                + this.WEIGHT_START.length(), nextPos - 1);

        Element element = new Element(lookupTag("weight"));
        element.addContent(weightedText);
        rootElement.addContent(element);
        return nextPos;
    }

    protected int parseLink(String structuredText, int pos, Element rootElement) {

        String description = structuredText.substring(pos + 1, structuredText
                .indexOf("\":", pos));
        int urlEnd = pos + description.length() + 2;
        while (urlEnd < structuredText.length()
                && structuredText.indexOf(" ", urlEnd) != urlEnd
                && structuredText.indexOf(this.LINE_SEPARATOR, urlEnd) != urlEnd
                && structuredText.indexOf(". ", urlEnd) != urlEnd
                && structuredText.indexOf(", ", urlEnd) != urlEnd
                && structuredText.indexOf("; ", urlEnd) != urlEnd) {
            urlEnd++;
        }
        String url = structuredText.substring(structuredText
                .indexOf("\":", pos) + 2, urlEnd);
        Element link = new Element(lookupTag("link"));
        Element e = new Element(lookupTag("url-description"));
        e.addContent(description);
        link.addContent(e);
        e = new Element(lookupTag("url"));
        e.addContent(url);
        link.addContent(e);
        rootElement.addContent(link);
        return urlEnd;
    }

    protected int parsePlainText(String structuredText, int pos,
            Element rootElement) {

        int nextPos = nextSpecialToken(structuredText, pos);
        if (nextPos > pos) {
            String text = structuredText.substring(pos, nextPos);

            if (!this.includeNewlines) {
                text = stripNewlines(text);
            }
            Element textElement = new Element(lookupTag("plaintext"));
            textElement.addContent(text);
            rootElement.addContent(textElement);
            if (text.trim().length() == 0) {
                // make sure elements containing only whitespace are not
                // treated as empty elements
                Attribute preserveSpace = new Attribute("space", "preserve",
                        Namespace.XML_NAMESPACE);
                textElement.setAttribute(preserveSpace);
            }
        }
        return nextPos;
    }

    /*
     * Strips away leading and trailing newlines
     */
    protected String stripNewlines(String text) {
        String textCopy = text;
        while (textCopy.startsWith("\n")) {
            textCopy = textCopy.substring(1, textCopy.length());
        }

        while (textCopy.endsWith("\n")) {
            textCopy = textCopy.substring(0, textCopy.length() - 1);

        }
        return textCopy;
    }

    protected int nextSpecialToken(String text, int pos) {
        int nextPos = pos;
        while (true) {
            if (nextPos == text.length()) break;
            if (listAtPos(text, nextPos) || paragraphAtPos(text, nextPos)
                    || emphasizeAtPos(text, nextPos)
                    || weightAtPos(text, nextPos) || linkAtPos(text, nextPos)
                    || newlineAtPos(text, nextPos)) {
                break;
            }
            nextPos++;
        }
        return nextPos;
    }

    protected boolean listAtPos(String text, int pos) {
        if (pos == 0) { return text.indexOf("- ", pos) == pos; }
        return (text.indexOf(this.LIST_START, pos) == pos);
    }

    protected boolean paragraphAtPos(String text, int pos) {
        return (text.indexOf(this.PARAGRAPH_START, pos) == pos);
    }

    protected boolean newlineAtPos(String text, int pos) {
        if (!this.includeNewlines) { return false; }
        if (paragraphAtPos(text, pos)) { return false; }
        return text.indexOf(this.LINE_SEPARATOR, pos) == pos;
    }

    protected boolean emphasizeAtPos(String text, int pos) {
        if (text.indexOf(this.EMPHASIZE_START, pos) != pos) return false;
        int termPos = text.indexOf(this.EMPHASIZE_CLOSE, pos
                + this.EMPHASIZE_CLOSE.length());
        if (termPos > 0) {
            for (int i = pos; i < termPos; i++) {
                if (listAtPos(text, i) || paragraphAtPos(text, i)
                        || newlineAtPos(text, i) || linkAtPos(text, i)
                        || weightAtPos(text, i)) { return false; }
            }
            return true;
        }
        return false;
    }

    protected boolean weightAtPos(String text, int pos) {
        if (text.indexOf(this.WEIGHT_START, pos) != pos) return false;
        int termPos = text
                .indexOf(this.WEIGHT_CLOSE, pos + this.EMPHASIZE_CLOSE.length());
        if (termPos > 0) {
            for (int i = pos + 1; i < termPos; i++) {
                if (listAtPos(text, i) || paragraphAtPos(text, i)
                        || newlineAtPos(text, i) || linkAtPos(text, i)
                        || emphasizeAtPos(text, i)) { return false; }
            }
            return true;
        }
        return false;
    }

    protected boolean linkAtPos(String text, int pos) {
        // links look like: "description":http://url-of-something
        if (text.indexOf("\"", pos) != pos) return false;

        int testPos = text.indexOf("\":", pos + "\"".length());
        if (testPos > 0 && (text.length() > testPos + "\":".length())) {

            // Check that the " at pos is the closest one to ":
            int intermediatePos = text.indexOf("\"", pos + "\"".length());
            if (intermediatePos != testPos) return false;

            for (int i = pos; i < testPos; i++) {
                if (listAtPos(text, i) || newlineAtPos(text, i)) { return false; }
            }
            if (testPos == text.length() - 1) return false;
            if (text.charAt(testPos + "\":".length()) == ' '
                    || text.indexOf(this.LINE_SEPARATOR, pos + "\":".length()) == pos
                            + "\":".length()) { return false; }
            return true;
        }
        return false;
    }

    public static void dumpXML(Element element, PrintStream out) {
        try {
            Format format = Format.getPrettyFormat();

            XMLOutputter xmlOutputter = new XMLOutputter();
            xmlOutputter.setFormat(format);

            //        	XMLOutputter xmlOutputter = new XMLOutputter(" ", true);
            //            xmlOutputter.setTextNormalize(true);
            String xml = xmlOutputter.outputString(element);
            out.println(xml);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public static void dumpXML(Document doc, PrintStream out) {
        try {
            Format format = Format.getPrettyFormat();

            XMLOutputter xmlOutputter = new XMLOutputter();
            xmlOutputter.setFormat(format);

            //            XMLOutputter xmlOutputter = new XMLOutputter(" ", true);
            //            xmlOutputter.setTextNormalize(true);
            String xml = xmlOutputter.outputString(doc);
            out.println(xml);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

}
