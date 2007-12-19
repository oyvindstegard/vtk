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
package org.vortikal.text.htmlparser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.vortikal.text.html.HtmlComment;
import org.vortikal.text.html.HtmlContent;
import org.vortikal.text.html.HtmlElement;
import org.vortikal.text.html.HtmlPage;
import org.vortikal.text.html.HtmlPageFilter;
import org.vortikal.text.html.HtmlText;
import org.vortikal.text.html.HtmlPageFilter.NodeResult;


public class HtmlPageImpl implements HtmlPage {

    private String doctype;
    private HtmlElement root;
    private boolean xhtml;
    
    public HtmlPageImpl(HtmlElement root, String doctype, boolean xhtml) {
        if (root == null) {
            throw new IllegalArgumentException("Root element cannot be NULL");
        }
        
        this.root = root;
        this.doctype = doctype;
    }

    public HtmlElement getRootElement() {
        return this.root;
    }

    public String getDoctype() {
        return this.doctype;
    }

    public String getStringRepresentation() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE ").append(this.doctype).append(">\n");
        sb.append(this.root.getEnclosedContent());
        return sb.toString();
    }

    public void filter(HtmlPageFilter filter) {
        List<HtmlContent> toplevel = Arrays.asList(this.root.getChildNodes());
        List<HtmlContent> filtered = filterContent(toplevel, filter);
        this.root.setChildNodes(filtered.toArray(new HtmlContent[filtered.size()]));
    }
    
    
    static List<HtmlContent> filterContent(List<HtmlContent> nodeList, HtmlPageFilter filter) {
        List<HtmlContent> resultList = new ArrayList<HtmlContent>();

        for (HtmlContent node: nodeList) {
            NodeResult result = filter.filter(node);

            if (result == NodeResult.keep) {
                if (node instanceof HtmlElement) {
                    HtmlElement element = (HtmlElement) node;
                    List<HtmlContent> children = Arrays.asList(element.getChildNodes());
                    List<HtmlContent> filtered = filterContent(children, filter);
                    element.setChildNodes(filtered.toArray(new HtmlContent[filtered.size()]));
                }
                resultList.add(node);

            } else if (result == NodeResult.skip) {
                if (node instanceof HtmlElement) {
                    HtmlElement element = (HtmlElement) node;
                    List<HtmlContent> children = Arrays.asList(element.getChildNodes());
                    List<HtmlContent> filtered = filterContent(children, filter);
                    resultList.addAll(filtered);
                }
            }
        }
        return resultList;
    }

    public List<HtmlElement> select(String expression) {
        return HtmlSelectUtil.select(this, expression);
    }

    public HtmlElement selectSingleElement(String expression) {
        return HtmlSelectUtil.selectSingleElement(this, expression);
    }

    public HtmlElement createElement(String name) {
        return new HtmlElementImpl(name, this.xhtml, false);
    }

    public HtmlText createTextNode(String content) {
        return new HtmlTextImpl(content);
    }
    public HtmlComment createComment(String comment) {
        return new HtmlCommentImpl(createTextNode(comment));
    }
}

