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
package vtk.web.servlet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

/**
 * A servlet response wrapper that is aware of the HTTP headers passed
 * through it.
 * 
 * <p>Used for logging which response headers we explicitly set throughout request processing.
 */
public class HeaderAwareResponseWrapper extends StatusAwareResponseWrapper {

    private Map<String, List<Object>> headers = new HashMap<>();
    
    public HeaderAwareResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public void addDateHeader(String name, long value) {
        addHeaderInternal(name, new Date(value), false);
        super.addDateHeader(name, value);
    }
    
    @Override
    public void addHeader(String name, String value) {
        addHeaderInternal(name, value, false);
        super.addHeader(name, value);
    }
    
    @Override
    public void addIntHeader(String name, int value) {
        addHeaderInternal(name, new Integer(value), false);
        super.addIntHeader(name, value);
    }
    
    @Override
    public void setDateHeader(String name, long value) {
        addHeaderInternal(name, new Date(value), true);
        super.setDateHeader(name, value);
    }
    
    @Override
    public void setHeader(String name, String value) {
        addHeaderInternal(name, value, true);
        super.setHeader(name, value);
    }

    private void addHeaderInternal(String name, Object value, boolean overwrite) {
        List<Object> values = this.headers.get(name);
        if (values == null || overwrite) {
            values = new ArrayList<>();
        }
        values.add(value);
        this.headers.put(name, values);
    }

    @Override
    public Collection<String> getHeaderNames() {
        return Collections.unmodifiableCollection(this.headers.keySet());
    }
    
    public List<Object> getHeaderValues(String name) {
        return this.headers.get(name);
    }
    
    @Override
    public void setContentLength(int length) {
        addHeaderInternal("Content-Length", String.valueOf(length), true);
        // Avoid bug in current Resin 4: http://bugs.caucho.com/view.php?id=5807
        // by not calling setHeader in addition to setContentLength.
        super.setContentLength(length);
    }
    
    @Override
    public void setContentType(String contentType) {
        addHeaderInternal("Content-Type", contentType, true);
        super.setHeader("Content-Type", contentType);
        super.setContentType(contentType);
    }
    
    public Object getHeaderValue(String name) {
        List<Object> list = this.headers.get(name);
        if (list == null || list.size() == 0) {
            return null;
        }
        return list.get(0);
    }

    public boolean isSingleValued(String name) {
        if (!this.headers.containsKey(name)) {
            throw new IllegalArgumentException("No header exists for name '" + name + "'");
        }
        List<Object> list = this.headers.get(name);
        return list.size() == 1;
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + getResponse() + "," + headers + ")";
    }
}
