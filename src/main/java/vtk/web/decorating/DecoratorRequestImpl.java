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
package vtk.web.decorating;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.Path;
import vtk.text.html.HtmlPage;
import vtk.web.ModelProvider;
import vtk.web.RequestContext;
import vtk.web.decorating.components.DecoratorComponentException;

public class DecoratorRequestImpl implements DecoratorRequest {

    private HtmlPage html;
    private HttpServletRequest servletRequest;
    private Map<String, Object> decoratorParameters;
    private String doctype;
    private Locale locale;
    private static final Pattern PATH_LEVEL_PATTERN = 
        Pattern.compile("(\\$path\\((\\d+)\\))");
    
    public DecoratorRequestImpl(HtmlPage html,
                                HttpServletRequest servletRequest,
                                Map<String, Object> decoratorParameters,
                                String doctype, Locale locale) {
        this.html = html;
        this.servletRequest = servletRequest;
        this.decoratorParameters = decoratorParameters;
        this.doctype = doctype;
        this.locale = locale;
    }
    
    @Override
    public HtmlPage getHtmlPage() {
        return this.html;
    }
    
    @Override
    public HttpServletRequest getServletRequest() {
        return this.servletRequest;
    }

    @Override
    public Map<String, Object> getMvcModel() {
        return ModelProvider.getModel(getServletRequest());
    }
   
    @Override
    public Object getRawParameter(String name) {
        Object value = null;
        if (this.decoratorParameters != null) {
            value = this.decoratorParameters.get(name);
        }
        return value;
    }
    
    @Override
    public String getStringParameter(String name) {
        Object value = getRawParameter(name);
        if (value == null) {
            return null;
        }
        return expandParameter(value.toString());
    }
    
    @Override
    public String getDoctype() {
        return this.doctype;
    }
        

    @Override
    public Locale getLocale() {
        return this.locale;
    }
    
    @Override
    public Iterator<String> getRequestParameterNames() {
        Set<String> s = new HashSet<String>();
        s.addAll(this.decoratorParameters.keySet());
        return s.iterator();
    }
    
    private String expandParameter(String param) {
        // Support for expanding '$path(level)':
        Matcher m = PATH_LEVEL_PATTERN.matcher(param);
        Path currentURI = RequestContext.getRequestContext().getResourceURI();
        StringBuffer sb = new StringBuffer();
        if (m.find()) {
            do {
                String s = m.group(2);
                try {
                    int level = Integer.parseInt(s);
                    if (level < 0 || level > currentURI.getDepth()) {
                        throw new DecoratorComponentException(
                                "Invalid level for current URI: " + currentURI + ": " + level);
                    }
                    String replacement = currentURI.getElements().get(level);
                    m.appendReplacement(sb, replacement);
                }
                catch (NumberFormatException e) {
                    throw new DecoratorComponentException(
                            "Unable to parse integer: " + s);
                }
            } while (m.find());
            
            m.appendTail(sb);
            param = sb.toString();
        }
        return param;
    }
}

