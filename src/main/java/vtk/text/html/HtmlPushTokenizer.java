/* Copyright (c) 2015, University of Oslo, Norway
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


public class HtmlPushTokenizer {
    
    public abstract static class Node {
        private String text;
        public Node(String text) {
            this.text = text;
        }
        public String toHtml() {
            return text;
        }
        
        public <T extends Node> boolean is(Class<T> clazz) {
            return clazz.isAssignableFrom(getClass());
        }
        
        public <T extends Node> T as(Class<T> clazz) {
            return clazz.cast(this);
        }
    }
    
    public static final class Attribute extends Node implements InElement {
        private String name, value;
        private Optional<Character> quoteChar;
        
        public String name() { return name; }
        public String value() { return value; }
        public String quotedValue() { 
            if (quoteChar.isPresent()) {
                return quoteChar.get() + value + quoteChar.get();
            }
            return value;
        }
        public String toString() { return "Attribute(" + name + "," + value + ")"; }
        
        public Attribute withName(String name) {
            return create(name, value);
        }
        
        public Attribute withValue(String value) {
            return create(name, value);
        }

        public static Attribute create(String name, String value) {
            if (name == null) throw new NullPointerException("name");
            List<InAttribute> content = new ArrayList<>();
            content.add(new AttrContent(name));
            if (value != null) {
                content.add(new AttrContent("="));
                content.add(new AttrContent("\"" + value + "\""));
            }
            return new Attribute(content);
        }
        
        private Attribute(List<InAttribute> content) {
            super(genContent(content));
            InAttribute first = content.get(0);
            this.name = ((AttrContent) first).toHtml();
            boolean nameOnly = true;
            for (int i = 1; i < content.size(); i++) {
                if (content.get(i) instanceof AttrContent) {
                    nameOnly = false;
                    break;
                }
            }
            if (nameOnly) return;
            
            int eqIdx = -1;
            for (int i = 1; i < content.size(); i++) {
                InAttribute c = content.get(i);
                if (c instanceof AttrContent) {
                    AttrContent a = (AttrContent) c;
                    if (a.isEq()) {
                        eqIdx = i;
                        break;
                    }
                    throw new IllegalArgumentException("=");
                }
            }
            if (eqIdx == -1) throw new IllegalArgumentException("=");
            int valIdx = -1;
            for (int i = eqIdx + 1; i < content.size(); i++) {
                InAttribute c = content.get(i);
                if (c instanceof AttrContent) {
                    valIdx = i;
                    break;
                }
            }
            if (valIdx == -1) throw new IllegalArgumentException("value");
            String value = ((AttrContent) content.get(valIdx)).toHtml();
            
            if (value.startsWith("\"")) {
                if (value.length() < 2 || !value.endsWith("\""))
                    throw new IllegalArgumentException("value: " + value);
                this.value = value.substring(1, value.length() - 1);
                this.quoteChar = Optional.of(value.charAt(0));
            }
            else if (value.startsWith("'")) {
                if (value.length() < 2 || !value.endsWith("'"))
                    throw new IllegalArgumentException("value: " + value);
                this.value = value.substring(1, value.length() - 1);
                this.quoteChar = Optional.of(value.charAt(0));
            }
            else {
                this.value = value;
                this.quoteChar = Optional.empty();
            }
            
        }

        private static String genContent(List<InAttribute> content) {
            if (content.size() == 0) throw new IllegalArgumentException("Empty attribute");
            
            return content.stream().reduce("", (s,  inattr) -> {
                if (inattr instanceof Whitespace) return s + ((Whitespace) inattr).toHtml();
                    else return s + ((AttrContent) inattr).toHtml();
                }, (s1, s2) -> s1 + s2);
        }
    }
    
    private static interface InElement {}
    
    public static final class ElementNode extends Node {
        private String name;
        private boolean closing;
        private boolean selfClosing;
        private List<InElement> content;
        private List<Attribute> attributes;
        
        public String name() { return name; }
        
        public boolean closing() { return closing; }
        
        public boolean selfClosing() { return selfClosing; }
        
        public List<Attribute> attributes() { return attributes; }
        
        public Optional<Attribute> attribute(String name) {
            return attributes.stream().filter(attr -> 
            attr.name.equalsIgnoreCase(name)).findFirst();
        }
        
        public String toString() { return "ElementNode(" + toHtml() + ")"; }
        
        public ElementNode withName(String name) {
            if (!validName(name)) throw new IllegalArgumentException(name);
            return new ElementNode(name, content, selfClosing);
        }
        
        public ElementNode clearAttributes() {
            return new ElementNode(name, Collections.emptyList(), selfClosing);
        }
        
        public ElementNode withAttribute(Attribute attribute) {
            List<InElement> content = new ArrayList<>();
            boolean added = false;
            for (InElement c: this.content) {
                if (c instanceof Whitespace) content.add(c);
                else {
                    Attribute attr = (Attribute) c;
                    if (attr.name().equalsIgnoreCase(attribute.name())) {
                        content.add(attribute);
                        added = true;
                    }
                    else content.add(attr);
                }
            }
            if (!added) {
                content.add(new Whitespace(" "));
                content.add(attribute);
            }
            return new ElementNode(name, filterWhitespace(content), selfClosing);
        }
        
        public ElementNode removeAttribute(String name) {
            List<InElement> content = new ArrayList<>();
            for (InElement c: this.content) {
                if (c instanceof Whitespace) content.add(c);
                else {
                    Attribute attr = (Attribute) c;
                    if (!attr.name().equalsIgnoreCase(name))
                        content.add(attr);
                }
            }
            return new ElementNode(this.name, filterWhitespace(content), selfClosing);
        }
        
        public ElementNode asOpening() {
            if (!closing()) return this;
            return new ElementNode(name.substring(1), content, selfClosing);
        }
        
        public ElementNode asClosing() {
            if (closing()) return this;
            return new ElementNode("/" + name, content, selfClosing);
        }
        
        public ElementNode asSelfClosing() {
            return new ElementNode(name, content, true);
        }
        
        public ElementNode asNonSelfClosing() {
            if (!selfClosing) return this;
            return new ElementNode(name, content, false);
        }
        
        public static ElementNode forName(String name) {
            if (!validName(name)) throw new IllegalArgumentException(name);
            return new ElementNode(name, Collections.emptyList(), false);
        }
        
        private static boolean validName(String name) {
            if (name == null || name.isEmpty()) return false;
            for (char c: name.toCharArray()) {
                if (!validNameChar(c)) {
                    return false;
                }
            }
            return true;
        }
        
        private List<InElement> filterWhitespace(List<InElement> content) {
            List<InElement> result = new ArrayList<>();
            InElement prev = null;
            for (InElement el: content) {
                if (el instanceof Whitespace) {
                    if (prev != null && (prev instanceof Whitespace)) continue;
                }
                result.add(el);
                prev = el;
            }
            return result;
        }
        
        public static boolean validNameChar(char ch) {
            return Character.isLetter(ch) || Character.isDigit(ch) 
                    || ch == '-' || ch == '_' || ch != '.' || ch != ':';
        }
        
        private ElementNode(String name, List<InElement> content, boolean selfClosing) {
            super("<" + name + genContent(content) + (selfClosing ? "/>" : ">"));
            if (name == null) {
                throw new NullPointerException("name");
            }
            char[] array = name.toCharArray();
            for (int i = 0; i < array.length; i++) {
                if (i == 0 && array[i] == '/') continue;
                if (!validNameChar(array[i])) throw new IllegalArgumentException("name");
            }
            if (name.startsWith("/") && selfClosing) {
                throw new IllegalArgumentException("</" + name + "/>");
            }
            this.closing = name.startsWith("/");
            this.name = closing ? name.substring(1) : name;
            this.selfClosing = selfClosing;
            this.content = content;
            List<Attribute> attributes = null;
            for (InElement c: content) {
                if (c instanceof Attribute) {
                    if (attributes == null) attributes = new ArrayList<>();
                    attributes.add((Attribute) c);
                }
            }
            if (attributes == null) this.attributes = Collections.emptyList();
            else this.attributes = Collections.unmodifiableList(attributes);
        }
        
        private static String genContent(List<InElement> content) {
            StringBuilder result = new StringBuilder();
            for (InElement el: content) {
                if (el instanceof Attribute) {
                    result.append(((Attribute) el).toHtml());
                }
                else result.append(((Whitespace) el).toHtml());
            }
            return result.toString();
        }
    }
    
    public static class TextNode extends Node {
        public static TextNode create(String html) {
            return new TextNode(html);
        }
        private TextNode(String text) {
            super(text);
        }
        public String toString() { return "TextNode(" + toHtml() + ")"; }
    }

    private static interface InAttribute {}
    
    private static final class Whitespace extends TextNode implements InElement, InAttribute {
        public Whitespace(String text) {
            super(text);
            for (char c: text.toCharArray())
                if (!Character.isWhitespace(c)) 
                    throw new IllegalArgumentException("Whitespace: " + c);
        }
        public String toString() { return "Whitespace(" + toHtml() + ")"; }
    }
    
    private static final class AttrContent extends TextNode implements InAttribute {
        public AttrContent(String text) {
            super(text);
        }
        public boolean isEq() { return "=".equals(toHtml()); }
        public String toString() { return "AttrContent(" + toHtml() + ")"; }
    }
    
    public static class CommentNode extends Node {
        public CommentNode(String text) {
            super(text);
        }
        public String toString() { return "CommentNode(" + toHtml() + ")"; }
    }
    
    public static class CDataNode extends Node {
        public CDataNode(String text) {
            super(text);
        }
        public String toString() { return "CDataNode(" + toHtml() + ")"; }
    }
    
    public static class Undefined extends Node {
        public Undefined(String text) {
            super(text);
        }
        public String toString() { return "Undefined(" + toHtml() + ")"; }
    }
    
    public static class DoctypeNode extends Node {
        public DoctypeNode(String text) {
            super(text);
        }
        public String toString() { return "DoctypeNode(" + toHtml() + ")"; }
    }
    
    public static interface NodeHandler {
        public boolean node(Node node);
        public void end();
        public void error(String msg);
    }

    private NodeHandler handler;
    
    public HtmlPushTokenizer(NodeHandler handler) {
        this.handler = handler;
    }

    private NodeBuilder builder = null;

    public void push(CharSequence seq) {
        
        if (builder == null) {
            builder = new InitialBuilder(handler);
        }

        for (int i = 0; i < seq.length(); i++) {
            builder = builder.append(seq.charAt(i));
        }
    }
    
    public void eof() {
        if (builder != null) {
            builder.eof();
        }
    }
    
    private static abstract class NodeBuilder {
        private NodeHandler handler;
        public NodeBuilder(NodeHandler handler) {
            this.handler = handler;
        }
        public NodeHandler handler() { return handler; }
        public void emit(Node node) {
            handler.node(node);
        }
        public void error(String msg) {
            handler.error(msg);
        }
        public abstract NodeBuilder append(char ch);
        public abstract void eof();
    }
    
    private static class InitialBuilder extends NodeBuilder {
        public InitialBuilder(NodeHandler handler) {
            super(handler);
        }

        @Override
        public NodeBuilder append(char ch) {
            StringBuilder buf = new StringBuilder();
            buf.append(ch);
            if (ch == '<') return new MarkupBuilder(handler(), buf);
            return new TextBuilder(handler(), buf);
        }
        public String toString() { return "InitialBuilder"; }

        @Override
        public void eof() {
            handler().end();
        }
    }
    
    private static class MarkupBuilder extends NodeBuilder {
        private StringBuilder buf;
        private enum State {
            ANGLE, EXCL
        }
                
        public MarkupBuilder(NodeHandler handler, StringBuilder buf) {
            super(handler);
            this.buf = buf;
        }

        private State state = State.ANGLE;
        
        @Override
        public NodeBuilder append(char ch) {
            buf.append(ch);

            switch (state) {
            case ANGLE:
                if (ch == '!') {
                    state = State.EXCL;
                }
                else if (ch == '?') {
                    
                }
                else if (ch == '/') {
                    return new ElementBuilder(handler(), buf);
                }
                else if (Character.isLetter(ch)) {
                    return new ElementBuilder(handler(), buf);
                }
                return this;
            case EXCL:
                if (ch == '>') {
                    emit(new CommentNode("")); // <!>
                    return new InitialBuilder(handler());
                }
                else if (ch == 'D') {
                    return new DoctypeBuilder(handler(), buf);
                }
                else if (ch == '-') {
                    return new CommentBuilder(handler(), buf);
                }
                else if (ch == '[') {
                    return new CDATABuilder(handler(), buf);
                }
                return this;
            default: 
                return this;
            }
        }
        
        @Override
        public void eof() {
            //error("Unterminated construct: " + buf);
            emit(new Undefined(buf.toString()));
        }

        @Override
        public String toString() { return "MarkupBuilder(" + buf + "," + state + ")"; }
        
    }
    
    private static class CommentBuilder extends NodeBuilder {
        private StringBuilder buf;
        private enum State {
            INIT, IN_COMMENT, END1, END2
        }
        
        public CommentBuilder(NodeHandler handler, StringBuilder buf) {
            super(handler);
            this.buf = buf;
        }
        
        private State state = State.INIT;
        
        @Override
        public NodeBuilder append(char ch) {
            buf.append(ch);
            switch (state) {
            case INIT:
                state = State.IN_COMMENT;
                return this;
            case IN_COMMENT:
                if (ch == '-') {
                    state = State.END1;
                }
                return this;
            case END1:
                if (ch == '-') {
                    state = State.END2;
                } else state = State.IN_COMMENT;
                return this;
            case END2:
                if (ch == '-') {
                    // be lenient:
                    state = State.END1;
                }
                else if (ch == '>') {
                    emit(new CommentNode(buf.toString()));
                    return new InitialBuilder(handler());
                } else state = State.IN_COMMENT;
                return this;
            default:
                return this;
            }
        }
        
        @Override
        public void eof() {
            //error("Unterminated comment: " + buf);
            emit(new Undefined(buf.toString()));
        }
        
        @Override
        public String toString() { return "CommentBuilder(" + buf + ")"; }
    }
    
    private static class CDATABuilder extends NodeBuilder {
        private StringBuilder buf;
        private enum State {
            INIT1,           // <![
            //INIT2,         // <![C
            //INIT3,         // <![CD
            IN_CDATA,        // 
            END1,            //  ]
            END2,            //  ]]
        }
        
        public CDATABuilder(NodeHandler handler, StringBuilder buf) {
            super(handler);
            this.buf = buf;
        }
        
        private State state = State.INIT1;
        
        @Override
        public NodeBuilder append(char ch) {
            buf.append(ch);
            switch (state) {
            case INIT1:
                state = State.IN_CDATA; // automatically
                return this;
            case IN_CDATA:
                if (ch == ']') {
                    state = State.END1;
                }
                return this;
            case END1:
                if (ch == ']') {
                    state = State.END2;
                } else state = State.IN_CDATA;
                return this;
            case END2:
                if (ch == '>') {
                    emit(new CDataNode(buf.toString()));
                    return new InitialBuilder(handler());
                }
                else if (ch == ']') state = State.END1;
                else state = State.IN_CDATA;

                return this;
            default:
                return this;
            }
        }
        
        @Override
        public void eof() {
            //error("Unterminated CDATA: " + buf);
            emit(new Undefined(buf.toString()));
        }
        
        @Override
        //public String toString() { return "CDATABuilder(" + buf + ")"; }
        public String toString() { return "CDATABuilder"; }
    }

    private static class DoctypeBuilder extends NodeBuilder {
        private StringBuilder buf;
        
        public DoctypeBuilder(NodeHandler handler, StringBuilder buf) {
            super(handler);
            this.buf = buf;
        }
        
        @Override
        public NodeBuilder append(char ch) {
            buf.append(ch);
            if (ch == '>') {
                emit(new DoctypeNode(buf.toString()));
                return new InitialBuilder(handler());
            }
            return this;
        }
        
        @Override
        public void eof() {
            //error("Unterminated construct: " + buf);
            emit(new Undefined(buf.toString()));
        }
        
        @Override
        public String toString() { return "DoctypeBuilder(" + buf + ")"; }
    }
    
    
    private static class ElementBuilder extends NodeBuilder {
        private enum State {
            ELEM_NAME, ELEM_WS, ATTR_NAME, ATTR_EQ, ATTR_VALUE_DQUOTED,
            ATTR_VALUE_SQUOTED, ATTR_VALUE_UNQUOTED, SELF_CLOSING, ELEM_UNDEFINED
        }
        
        private StringBuilder buf;
        private String name;
        private AttrBuilder attrBuilder;
        private List<InElement> content = new ArrayList<>();
        
        public ElementBuilder(NodeHandler handler, StringBuilder buf) {
            super(handler);
            this.buf = buf;
            state = State.ELEM_NAME;
        }
        private State state;

        @Override
        public NodeBuilder append(char ch) {
            buf.append(ch);
            switch (state) {
            case ELEM_NAME:
                if (Character.isWhitespace(ch)) {
                    name = buf.substring(1, buf.length() - 1);
                    state = State.ELEM_WS;
                    return this;
                }
                if (ch == '/') {
                    name = buf.substring(1, buf.length() - 1);
                    state = State.SELF_CLOSING;
                    return this;
                }
                if (ch == '>') {
                    name = buf.substring(1, buf.length() - 1);
                    emit(new ElementNode(name, content, false));
                    return new InitialBuilder(handler());
                }
                if (!ElementNode.validNameChar(ch)) {
                    state = State.ELEM_UNDEFINED;
                }
                return this;
                
            case ATTR_NAME:
                if (Character.isWhitespace(ch)) {
                    attrBuilder.whitespace(ch);
                    state = State.ATTR_NAME;
                }
                else if (ch == '=') {
                    attrBuilder.eq(ch);
                    state = State.ATTR_EQ;
                }
                else if (ch == '>') {
                    content.add(attrBuilder.build());
                    attrBuilder = null;
                    emit(new ElementNode(name, content, false));
                    return new InitialBuilder(handler());
                }
                else if (ch == '/') {
                    content.add(attrBuilder.build());
                    attrBuilder = null;
                    state = State.SELF_CLOSING;
                }
                else {
                    attrBuilder = attrBuilder.nameChar(ch);
                }
                return this;
            case ATTR_EQ:
                if (ch == '"') {
                    attrBuilder.valueChar(ch);
                    state = State.ATTR_VALUE_DQUOTED;
                }
                else if (ch == '\'') {
                    attrBuilder.valueChar(ch);
                    state = State.ATTR_VALUE_SQUOTED;
                }
                else if (Character.isLetter(ch)) {
                    attrBuilder.valueChar(ch);
                    state = State.ATTR_VALUE_UNQUOTED;
                }
                else if (Character.isWhitespace(ch)) {
                    attrBuilder.whitespace(ch);
                }
                else {
                    attrBuilder = null; // discard
                    state = State.ELEM_WS;
                }
                return this;
            case ATTR_VALUE_DQUOTED:
                if (ch == '"') {
                    attrBuilder.valueChar(ch);
                    content.add(attrBuilder.build());
                    attrBuilder = null;
                    state = State.ELEM_WS;
                }
                else {
                    attrBuilder.valueChar(ch);
                }
                return this;
            case ATTR_VALUE_SQUOTED:
                if (ch == '\'') {
                    attrBuilder.valueChar(ch);
                    content.add(attrBuilder.build());
                    attrBuilder = null;
                    state = State.ELEM_WS;
                }
                else {
                    attrBuilder.valueChar(ch);
                }
                return this;
            case ATTR_VALUE_UNQUOTED:
                if (Character.isWhitespace(ch)) {
                    content.add(attrBuilder.build());
                    attrBuilder = null;
                    state = State.ELEM_WS;
                }
                else if (ch == '>') {
                    content.add(attrBuilder.build());
                    attrBuilder = null;
                    emit(new ElementNode(name, content, false));
                    return new InitialBuilder(handler());
                }
                else if (ch == '/') {
                    state = State.SELF_CLOSING;
                }
                else {
                    attrBuilder.valueChar(ch);
                }
                return this;
            case ELEM_WS:
                if (Character.isWhitespace(ch)) {
                    return this;
                }
                // add whitespace up to here..
                StringBuilder ws = new StringBuilder();
                for (int i = buf.length() - 2; i >= 0; i--) {
                    if (Character.isWhitespace(buf.charAt(i))) { 
                        ws.append(buf.charAt(i));
                    } else break;
                }
                if (ws.length() != 0) content.add(new Whitespace(ws.toString()));
                    
                if (ch == '>') {
                    emit(new ElementNode(name, content, false));
                    return new InitialBuilder(handler());
                }
                else if (ch == '/') {
                    state = State.SELF_CLOSING;
                }
                else if (Character.isLetter(ch)) {
                    if (attrBuilder == null) attrBuilder = new AttrBuilder();
                    attrBuilder.nameChar(ch);
                    state = State.ATTR_NAME;
                }
                else {
                    state = State.ELEM_UNDEFINED;
                }
                return this;
            case SELF_CLOSING:
                if (ch == '>') {
                    emit(new ElementNode(name, content, true));
                    return new InitialBuilder(handler());
                }
                state = State.ELEM_UNDEFINED;
                return this;
            case ELEM_UNDEFINED:
                if (ch == '>') {
                    emit (new Undefined(buf.toString()));
                    return new InitialBuilder(handler());
                }
                return this;
            default:
                state = State.ELEM_UNDEFINED;
                return this;
            }
        }
        
        @Override
        public void eof() {
            //System.out.println("_state: " + state);
            //error("Unterminated element: " + buf);
            emit(new Undefined(buf.toString()));
        }
        
        @Override
        public String toString() { return "ElementBuilder(" + name + "," + state + ")"; }
    }

    private static class TextBuilder extends NodeBuilder {
        private StringBuilder buf;

        public TextBuilder(NodeHandler handler, StringBuilder buf) {
            super(handler);
            this.buf = buf;
        }

        @Override
        public NodeBuilder append(char ch) {
            if (ch == '<') {
                emit(new TextNode(buf.toString())); // text
                StringBuilder newBuffer = new StringBuilder();
                newBuffer.append(ch);
                return new MarkupBuilder(handler(), newBuffer);
            }
            buf.append(ch);
            return this;
        }
        
        @Override
        public void eof() {
            emit(new TextNode(buf.toString())); // text
        }
        
        @Override
        public String toString() { return "TextBuilder"; }
    }
    
    private static class AttrBuilder {
        private List<InAttribute> content = new ArrayList<>();
        
        public AttrBuilder nameChar(char c) {
            if (content.size() > 1) throw new IllegalStateException("name");
            if (content.isEmpty()) {
                content.add(new AttrContent(""));
            }
            String s = ((AttrContent) content.get(0)).toHtml();
            content.set(0, new AttrContent(s + c));
            return this;
        }
        
        public AttrBuilder eq(char c) {
            if (empty()) throw new IllegalStateException("=");
            content.add(new AttrContent(String.valueOf(c)));
            return this;
        }
        
        public AttrBuilder valueChar(char c) {
            if (empty()) throw new IllegalStateException("value char: " + c);
            InAttribute last = content.get(content.size() - 1);
            if (last instanceof Whitespace) {
                content.add(new AttrContent(String.valueOf(c)));
            }
            else {
                String s = ((AttrContent) last).toHtml();
                if ("=".equals(s)) {
                    content.add(new AttrContent(String.valueOf(c)));
                }
                else content.set(content.size() - 1, new AttrContent(s + c));
            }
            return this;
        }
        
        public AttrBuilder whitespace(char c) {
            if (content.isEmpty()) {
                content.add(new Whitespace(String.valueOf(c)));
            }
            else {
                InAttribute last = content.get(content.size() - 1);
                if (last instanceof Whitespace) {
                    String s = ((Whitespace) last).toHtml();
                    content.set(content.size() - 1, new Whitespace(s));
                }
                else {
                    content.add(new Whitespace(String.valueOf(c)));
                }
            }
            return this;
        }
        
        public Attribute build() {
            return new Attribute(content);
        }

        private boolean empty() {
            for (InAttribute c: content) {
                if (c instanceof AttrContent) return false;
            }
            return true;
        }
        
    }
}
