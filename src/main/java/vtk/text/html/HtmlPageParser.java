/* Copyright (c) 2007, University of Oslo, Norway
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.htmlparser.Attribute;
import org.htmlparser.Node;
import org.htmlparser.Parser;
import org.htmlparser.PrototypicalNodeFactory;
import org.htmlparser.Remark;
import org.htmlparser.Tag;
import org.htmlparser.Text;
import org.htmlparser.lexer.Lexer;
import org.htmlparser.lexer.Page;
import org.htmlparser.tags.DoctypeTag;
import org.htmlparser.tags.ProcessingInstructionTag;
import org.htmlparser.util.NodeList;

/**
 * A simple HTML parser that creates {@link HtmlPage} or {@link HtmlFragment} 
 * objects from input.
 */
public class HtmlPageParser {

    private Set<String> emptyTags = DefaultHtmlTagConfiguration.emptyTags();
    
    private String defaultDoctype = HtmlPage.DEFAULT_DOCTYPE;

    public void setDefaultDoctype(String defaultDoctype) {
        this.defaultDoctype = defaultDoctype;
    }
    
    public void setEmptyTags(Set<String> emptyTags) {
        this.emptyTags = emptyTags;
    }
    
    /**
     * Parses HTML from an input stream.
     *
     * @param in the input stream
     * @param encoding the character encoding of the stream
     * @return a the parsed HTML page
     * @exception Exception if an error occurs
     */
    public HtmlPage parse(InputStream in, String encoding) throws Exception {
        return parse(in, encoding, new ArrayList<HtmlNodeFilter>());
    }
    
    /**
     * Parses HTML from an input stream, applying a node filter.
     *
     * @param in the input stream
     * @param encoding the character encoding of the stream
     * @return a the parsed HTML page
     * @exception Exception if an error occurs
     */
    public HtmlPage parse(InputStream in, String encoding, HtmlNodeFilter filter)
        throws Exception {
        List<HtmlNodeFilter> filters = new ArrayList<HtmlNodeFilter>();
        filters.add(filter);
        return parse(in, encoding, filters);
    }
    
    /**
     * Parses HTML from an input stream, applying a list of node
     * filters.
     *
     * @param in the input stream
     * @param encoding the character encoding of the stream
     * @param filters the list of node filters
     * @return a the parsed HTML page
     * @exception Exception if an error occurs
     */
    public HtmlPage parse(InputStream in, String encoding, List<HtmlNodeFilter> filters)
        throws Exception {

        Page page = new Page(in, encoding);
        Lexer lexer = new Lexer(page);

        Parser parser = new Parser(lexer);

        PrototypicalNodeFactory factory = new PrototypicalNodeFactory();
        factory.setTagPrototype(new org.htmlparser.tags.CompositeTag());

        for (String tag: this.emptyTags) {
            factory.registerTag(new EmptyTag(new String[]{tag}));
        }
        parser.setNodeFactory(factory);

        NodeList nodeList = parser.parse(null);

        Node root = findRootNode(nodeList);
        String doctype = findDoctype(nodeList);
        if (doctype == null) {
            doctype = this.defaultDoctype;
        }
        boolean xhtml = HtmlPage.isXhtml(doctype);
        HtmlContent html = buildHtml(root, filters, xhtml);
        if (html == null) {
            throw new HtmlPageParserException("Unable to parse HTML: invalid document");
        }
        HtmlElement rootElement = null;
        if (html instanceof HtmlElement) {
            rootElement = (HtmlElement) html;
        } else {
            rootElement = new HtmlElementImpl("html", xhtml, false);
            rootElement.setChildNodes(new HtmlContent[]{html});
        }
        return new HtmlPage(rootElement, doctype, 
                parser.getEncoding(), 
                Collections.unmodifiableSet(this.emptyTags));
    }
    
    /**
     * Creates a page with the default document type and 
     * the minimal structure: 
     * <code>(html (head (title)) (body))</code>.
     * The content of the title element is supplied in 
     * the <code>title</code> argument.
     */
    public HtmlPage createEmptyPage(String title) {
        String doctype = HtmlPage.DEFAULT_DOCTYPE;
        boolean xhtml = HtmlPage.isXhtml(doctype);
        HtmlElement htmlElem = new HtmlElementImpl("html", xhtml, false);
        HtmlElement headElem = new HtmlElementImpl("head", xhtml, false);
        HtmlElement titleElem = new HtmlElementImpl("title", xhtml, false);
        HtmlElement bodyElem = new HtmlElementImpl("body", xhtml, false);
        titleElem.addContent(new HtmlTextImpl(title));
        headElem.addContent(titleElem);
        htmlElem.addContent(headElem);
        htmlElem.addContent(bodyElem);
        if (xhtml) {
            htmlElem.addAttribute(new HtmlAttributeImpl("xmlns", "http://www.w3.org/1999/xhtml", false));
        }
        return new HtmlPage(htmlElem, doctype, "utf-8", 
                Collections.unmodifiableSet(this.emptyTags));
    }
    
    /**
     * Parses a (possibly incomplete) HTML fragment from a string.
     *
     * @param html the HTML fragment
     * @return the HTML fragment parsed into a list of HTML nodes
     * @exception Exception if an error occurs
     */
    public HtmlFragment parseFragment(String html) throws Exception {
        return parseFragment(html, Collections.<HtmlNodeFilter>emptyList());
    }
    
    /**
     * Parses a (possibly incomplete) HTML fragment from a string, 
     * applying a list of node filters.
     *
     * @param html the HTML fragment
     * @return the HTML fragment parsed into a list of HTML nodes
     * @param filters the list of node filters
     * @exception Exception if an error occurs
     */
    public HtmlFragment parseFragment(String html, List<HtmlNodeFilter> filters) throws Exception {
        String className = this.getClass().getName();
        StringBuilder s = new StringBuilder();
        s.append("<").append(className).append(">");
        s.append(html);
        s.append("</").append(className).append(">");
        ByteArrayInputStream in = new ByteArrayInputStream(s.toString().getBytes("utf-8"));
        HtmlPage page = parse(in, "utf-8", filters);
        HtmlElement root = page.getRootElement();
        if (!className.equals(root.getName())) {
            throw new HtmlPageParserException("Unable to parse HTML: invalid document");
        }
        List<HtmlContent> content = Arrays.asList(root.getChildNodes());
        return new HtmlFragment(content);
    }

    private Node findRootNode(NodeList nodeList) {
        Node root = null;
        for (int i = 0; i < nodeList.size(); i++) {
            Node node = nodeList.elementAt(i);
            if (node instanceof DoctypeTag) {
                continue;
            } else if (node instanceof ProcessingInstructionTag) {
                continue;
            } else if (node instanceof Tag) {
                root = node;
                break;
            }
        }
        return root;
    }
    
    private String findDoctype(NodeList nodeList) {
        for (int i = 0; i < nodeList.size(); i++) {
            Node node = nodeList.elementAt(i);
            if (node != null && node instanceof DoctypeTag) {
                DoctypeTag doctypeTag = (DoctypeTag) node;
                String text = doctypeTag.getText();
                if (text != null && text.startsWith("!DOCTYPE ")) {
                    text = text.substring("!DOCTYPE ".length());
                }
                return text;
            }
        }
        return null;
    }

    private HtmlContent buildHtml(Node node, List<HtmlNodeFilter> filters, boolean xhtml) {

        HtmlContent content = null;
        
        if (node instanceof Tag) {
            Tag tag = (Tag) node;
            String name = tag.getRawTagName();
            if (name != null && name.endsWith("/")) {
                // Some node names have a trailing slash, remove it:
                name = name.substring(0, name.length() - 1);
            }
            if (name == null) {
                return null;
            }
            boolean empty = tag.isEmptyXmlTag() || (tag instanceof EmptyTag);
            HtmlElementImpl element = new HtmlElementImpl(name, xhtml, empty);

            if (tag.isEndTag()) {
                return null;
            } 

            addAttributes(element, tag);
            
            NodeList children = tag.getChildren();
            if (children != null) {
                for (int i = 0; i < children.size(); i++) {

                    // Handle "flattened" nodes (tags that should be
                    // nested, but aren't):
                    if (isFlattenedNode(children, i)) {

                        Node unflattened = unflattenSiblings(children, i);
                        HtmlContent child = buildHtml(unflattened, filters, xhtml);
                        if (child != null) {
                            element.addContent(child);
                        }
                        // Skip the following text node and end tag:
                        i+=2;

                    } else {
                        
                        HtmlContent child = buildHtml(children.elementAt(i), filters, xhtml);
                        if (child != null) {
                            element.addContent(child);
                        }
                    }
                }
            }
            content = element;
        } else if (node instanceof Text) {
            Text text = (Text) node;
            HtmlTextImpl textNode = new HtmlTextImpl(text.getText());
            content = textNode;
            
        } else if (node instanceof Remark) {
            Remark remark = (Remark) node;
            HtmlCommentImpl comment = new HtmlCommentImpl(
                new HtmlTextImpl(remark.getText()));
            content = comment;
        } 
        // Else unhandled node type:
        
        if (content != null && filters.size() > 0) {
            HtmlContent filteredContent = content;
            for (HtmlNodeFilter filter: filters) {
                if (filteredContent == null) {
                    break;
                }
                filteredContent = filter.filterNode(filteredContent);
            }
            return filteredContent;
        }
        return content;
    }

    private void addAttributes(HtmlElementImpl element, Tag tag) {
        String name = tag.getRawTagName();
        Vector<?> attrs = tag.getAttributesEx();
        for (int i = 0; i < attrs.size(); i++) {
            Attribute attr = (Attribute) attrs.get(i);
            if (attr != null && !attr.isWhitespace()) {
                String attrName = attr.getName();
                if (attrName != null && !name.equals(attrName) && !"/".equals(attrName)) {
                    String attrValue = attr.getValue();
                    boolean single = attr.getQuote() == '\'';
                    element.addAttribute(new HtmlAttributeImpl(attrName, attrValue, single));
                }
            }
        }
    }
    
    private boolean isFlattenedNode(NodeList nodeList, int index) {
        Node childNode = nodeList.elementAt(index);
        if (index <= nodeList.size() - 3) {
            Node firstSibling = nodeList.elementAt(index + 1);
            Node nextSibling = nodeList.elementAt(index + 2);

            return (childNode instanceof Tag
                    && firstSibling != null
                    && nextSibling != null
                    && firstSibling instanceof Text
                    && nextSibling instanceof Tag
                    && ((Tag) nextSibling).isEndTag()
                    && ((Tag) nextSibling).getTagName()
                    .equals(((Tag)childNode).getTagName()));
            
        }
        return false;
    }
    
    private Node unflattenSiblings(NodeList nodeList, int index) {
        Tag node = (Tag) nodeList.elementAt(index);
        Text child = (Text) nodeList.elementAt(index + 1);
        Tag end = (Tag) nodeList.elementAt(index + 2);
        NodeList children = new NodeList();
        children.add(child);
        node.setChildren(children);
        node.setEmptyXmlTag(false);
        node.setEndTag(end);
        return node;
    }
}
