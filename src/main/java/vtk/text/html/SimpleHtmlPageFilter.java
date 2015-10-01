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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Simple utility HTML page filter implementation. 
 * This filter can be configured using a set of valid elements, 
 * a set of illegal elements and a flag that determines whether 
 * or not to keep HTML comments.
 * 
 * <p>The filter defines the following behavior:
 * <ul>
 *   <li>Text nodes are kept</li>
 *   <li>Comments are kept unless the <code>keepComments</code> 
 *      JavaBean property is <code>false</code>
 *   <li>Element nodes are processed using the following rules:
 *     <ol>
 *       <li>If the element is contained in the set of illegal elements, 
 *          the element and all its descendants are excluded from the page</li>
 *       <li>If the element is contained in the set of valid elements, the element 
 *          is kept in the page, and the processing continues for its descendants</li>
 *       <li>Otherwise, the element itself is skipped, but the processing continues 
 *          for its descendants</li>
 *     </ol>
 *   </li>
 * </ul>
 * </p>
 */
public class SimpleHtmlPageFilter implements HtmlPageFilter {

    private Set<String> illegalElements = new HashSet<>();
    private Map<String, HtmlElementDescriptor> validElements = new HashMap<>();
    private boolean keepComments = true;
    private boolean checkValidElements = true;
    
    public SimpleHtmlPageFilter() {
    }

    public SimpleHtmlPageFilter(Set<String> illegalElements,
            Set<HtmlElementDescriptor> validElements,
            boolean keepComments) {
        setIllegalElements(illegalElements);
        setValidElements(validElements);
        this.keepComments = keepComments;
    }

    public void setIllegalElements(Set<String> illegalElements) {
        if (illegalElements == null) throw new IllegalArgumentException(
                "Field 'illegalElements' cannot be NULL");
        for (String elem: illegalElements) {
            this.illegalElements.add(elem);
        }
    }
    
    public void setValidElements(Set<HtmlElementDescriptor> validElements) {
        if (validElements == null) throw new IllegalArgumentException(
            "Field 'validElements' cannot be NULL");
        for (HtmlElementDescriptor desc: validElements) {
            this.validElements.put(desc.getName(), desc);
        }
    }
    
    public void setKeepComments(boolean keepComments) {
        this.keepComments = keepComments;
    }
    
    public void setCheckValidElements(boolean checkValidElements) {
        this.checkValidElements = checkValidElements;
    }

    @Override
    public boolean match(HtmlPage page) {
        return true;
    }

    @Override
    public HtmlPageFilter.NodeResult filter(HtmlContent node) {

        if (node instanceof HtmlComment) {
            return keepComments ? NodeResult.keep : NodeResult.exclude;

        } else if (node instanceof HtmlText) {
            return NodeResult.keep;

        } else if (node instanceof HtmlElement) {
            HtmlElement element = (HtmlElement) node;
            String name = element.getName().toLowerCase();

            if (illegalElements.contains(name)) {
                return NodeResult.exclude;
            }
            
            if (!checkValidElements) {
                return NodeResult.keep;
            }

            if (validElements.containsKey(name)) {
                HtmlElementDescriptor desc = validElements.get(name);
                String value = element.getContent();
                if (!desc.isValidAsEmpty() && "".equals(value.trim())) {
                    return NodeResult.exclude;
                }
                
                List<HtmlAttribute> filteredAttributes = new ArrayList<HtmlAttribute>();
                for (String attribute: desc.getAttributes()) {
                    for (HtmlAttribute a: element.getAttributes()) {
                        if (attribute.equals(a.getName().toLowerCase())) {
                            filteredAttributes.add(a);
                        }
                    }
                }
                HtmlAttribute[] newAttributes = filteredAttributes.<HtmlAttribute>toArray(
                        new HtmlAttribute[filteredAttributes.size()]);
                element.setAttributes(newAttributes);
                return NodeResult.keep;
            }
            // Skip node, continue to process children:
            return NodeResult.skip;
        }
        return NodeResult.keep;
    }

}
