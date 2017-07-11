/* Copyright (c) 2009, University of Oslo, Norway
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
package vtk.text.tl;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Context {

    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("[a-zA-Z_]([a-zA-Z0-9\\-_]*[a-zA-Z0-9_])?");
    private Locale locale = Locale.getDefault();
    // Visible from templates and functions:
    private Stack<Map<String, Object>> stack = new Stack<>();
    // For use by "runtime system":
    private Map<String, Object> attributes = new HashMap<>();
    
    private static final String NULL = "null";

    public Context(Locale locale) {
        Map<String, Object> toplevel = new HashMap<>();
        toplevel.put(NULL, null);
        this.stack.push(toplevel);
        this.locale = locale;
    }

    public boolean isDefined(String name) {
        Map<String, Object> ctx;
        int idx = this.stack.size() - 1;
        while (idx >= 0) {
            ctx = this.stack.get(idx--);
            if (ctx.containsKey(name)) {
                return true;
            }
        }
        return false;
    }

    public Object get(String name) {
        Map<String, Object> ctx;
        int idx = this.stack.size() - 1;
        while (idx >= 0) {
            ctx = this.stack.get(idx--);
            if (ctx.containsKey(name)) {
                return ctx.get(name);
            }
        }
        return null;
    }

    // Defines a binding in the context.
    public void define(String name, Object value, boolean global) {
        if (!validateSymbol(name)) {
            throw new IllegalArgumentException("Illegal name: '" + name + "'");
        }
        if (!global) {
            Map<String, Object> ctx = this.stack.peek();
            ctx.put(name, value);
        } else {
            boolean found = false;
            Map<String, Object> ctx;
            int idx = this.stack.size() - 1;
            while (idx >= 0) {
                ctx = this.stack.get(idx--);
                if (ctx.containsKey(name)) {
                    ctx.put(name, value);
                    found = true;
                }
            }
            if (!found) {
                // Define variable in root context
                Map<String, Object> root = this.stack.get(0);
                root.put(name, value);
            }
        }
    }

    public void push() {
        this.stack.push(new HashMap<String, Object>());
    }

    public void pop() {
        this.stack.pop();
    }

    public Locale getLocale() {
        return this.locale;
    }

    public String htmlEscape(String html) {
        StringBuilder result = new StringBuilder();
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
                result.append("&#39;");
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

    @Override
    public String toString() {
        return this.stack.toString();
    }
    
    public boolean hasAttribute(String name) {
        if (this.attributes == null) {
            return false;
        }
        return this.attributes.containsKey(name);
    }
    
    public Object getAttribute(String name) {
        if (this.attributes == null) {
            return null;
        }
        return this.attributes.get(name);
    }
    
    public void setAttribute(String name, Object value) {
        if (this.attributes == null) {
            this.attributes = new HashMap<>();
        }
        this.attributes.put(name, value);
    }
    
    public void removeAttribute(String name) {
        if (this.attributes == null) {
            return;
        }
        this.attributes.remove(name);
    }

    private boolean validateSymbol(String symbol) {
        if (symbol == null) {
            return false;
        }
        if (NULL.equals(symbol)) {
            return false;
        }
        Matcher m = VALID_NAME_PATTERN.matcher(symbol);
        return m.matches();
    }
}
